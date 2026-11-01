package com.mux.android.wifimap.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.Settings as AndroidSettings
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mux.android.wifimap.data.AppSettings
import com.mux.android.wifimap.data.Nav
import com.mux.android.wifimap.data.Net
import com.mux.android.wifimap.data.Problem
import com.mux.android.wifimap.data.Repo
import com.mux.android.wifimap.data.Scanner
import com.mux.android.wifimap.data.ShareDraft
import com.mux.android.wifimap.data.Summary
import com.mux.android.wifimap.data.parseWifiQr
import com.mux.android.wifimap.util.openAppSettings
import com.mux.android.wifimap.util.openSettingsAction
import com.mux.android.wifimap.util.toast

private fun secColor(n: Net, c: HudPalette) = when (n.security) {
    "OPEN" -> c.accent
    "WPA3", "WPA2/WPA3" -> c.good
    "WEP" -> c.danger
    else -> c.primary
}

@Composable
fun RadarScreen(tablet: Boolean) {
    val ctx = LocalContext.current
    val c = Hud.colors
    val problem = remember(Nav.permTick, Nav.resumeTick, Scanner.lastScan) { Scanner.problem(ctx) }
    var filter by remember { mutableStateOf("ALL") }
    var query by remember { mutableStateOf("") }
    val summaries = Repo.summaries

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Nav.permTick++
    }
    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { res ->
        val text = res.contents ?: return@rememberLauncherForActivityResult
        val q = parseWifiQr(text)
        if (q == null) {
            toast(ctx, "NOT A WI-FI QR CODE")
        } else {
            val net = Scanner.nets.filter { it.ssid == q.ssid }.maxByOrNull { it.level }
            when {
                net == null -> toast(ctx, "\"${q.ssid}\" IS NOT IN RANGE - GO NEAR THE ROUTER AND SCAN AGAIN")
                q.password.isEmpty() -> toast(ctx, "THIS QR CODE HAS NO PASSWORD")
                !net.canShare -> toast(ctx, "THIS NETWORK CAN'T BE SHARED")
                else -> Nav.share = ShareDraft(net, q.password, "qr")
            }
        }
    }

    val list = remember(Scanner.nets, summaries, filter, query) {
        val q = query.trim().lowercase()
        Scanner.nets
            .filter {
                when (filter) {
                    "SHARED" -> summaries.containsKey(it.bssid)
                    "SECURED" -> it.needsPassword
                    "OPEN" -> it.security == "OPEN"
                    "2.4G" -> it.band == "2.4G"
                    "5G" -> it.band == "5G" || it.band == "6G"
                    "ROUTER" -> it.kind == "ROUTER"
                    "HOTSPOT" -> it.kind == "HOTSPOT"
                    else -> true
                }
            }
            .filter {
                q.isEmpty() || it.ssid.lowercase().contains(q) || it.bssid.contains(q) || it.brand.lowercase().contains(q)
            }
            .sortedWith(
                compareByDescending<Net> { it.connected }
                    .thenByDescending { summaries.containsKey(it.bssid) }
                    .thenByDescending { it.level }
            )
    }
    val sharedCount = Scanner.nets.count { summaries.containsKey(it.bssid) }

    Column(Modifier.fillMaxSize()) {
        HudPanel(Modifier.fillMaxWidth(), "RADAR") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    HudText(
                        "${Scanner.nets.size} NETWORKS  ·  $sharedCount WITH PASSWORDS",
                        color = c.primary, size = 14.sp, bold = true
                    )
                    HudText(
                        when {
                            Scanner.scanning -> "SCANNING..."
                            Repo.checking -> "CHECKING COMMUNITY DATABASE..."
                            Repo.serverOk == false -> "DATABASE OFFLINE: ${Repo.lastError}"
                            !AppSettings.lookup -> "DATABASE LOOKUP IS OFF (SYS)"
                            Scanner.connectedSsid.isNotEmpty() -> "CONNECTED: ${Scanner.connectedSsid}"
                            else -> "READY"
                        },
                        color = if (Repo.serverOk == false) c.danger else c.dim, size = 10.sp, maxLines = 2
                    )
                }
                HudButton("SCAN", Modifier.width(70.dp).height(42.dp), KeyKind.ACCENT, enabled = problem == null && !Scanner.scanning, fontSize = 12.sp) {
                    Scanner.scan(ctx)
                    com.mux.android.wifimap.data.Repo.invalidateAll()
                }
                HudButton("QR", Modifier.width(56.dp).height(42.dp), KeyKind.OP, fontSize = 13.sp) {
                    val o = ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Point at the Wi-Fi QR code")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                    qrLauncher.launch(o)
                }
            }
            if (Scanner.note.isNotEmpty()) HudText(Scanner.note, color = c.accent, size = 10.sp)
        }
        Spacer(Modifier.height(6.dp))

        when (problem) {
            Problem.NO_PERMISSION -> {
                HudPanel(Modifier.fillMaxWidth(), "PERMISSION REQUIRED") {
                    HudText(
                        "Android only shows nearby Wi-Fi networks to apps that have the location permission. Your location is never sent anywhere.",
                        color = c.text, size = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    HudButton("GRANT ACCESS", Modifier.fillMaxWidth().height(46.dp), KeyKind.ACCENT, fontSize = 13.sp) {
                        permLauncher.launch(Scanner.requiredPermissions().toTypedArray())
                    }
                    HudButton("APP SETTINGS", Modifier.fillMaxWidth().height(40.dp), KeyKind.FN, fontSize = 12.sp) { openAppSettings(ctx) }
                }
                return@Column
            }
            Problem.LOCATION_OFF -> {
                HudPanel(Modifier.fillMaxWidth(), "LOCATION IS OFF") {
                    HudText("Turn on Location in Android quick settings. Android needs it to list Wi-Fi networks.", color = c.text, size = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    HudButton("OPEN LOCATION SETTINGS", Modifier.fillMaxWidth().height(46.dp), KeyKind.ACCENT, fontSize = 12.sp) {
                        openSettingsAction(ctx, AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS)
                    }
                }
                return@Column
            }
            Problem.WIFI_OFF -> {
                HudPanel(Modifier.fillMaxWidth(), "WI-FI IS OFF") {
                    HudText("Turn on Wi-Fi to see the networks around you.", color = c.text, size = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    HudButton("OPEN WI-FI SETTINGS", Modifier.fillMaxWidth().height(46.dp), KeyKind.ACCENT, fontSize = 12.sp) {
                        openSettingsAction(ctx, AndroidSettings.ACTION_WIFI_SETTINGS)
                    }
                }
                return@Column
            }
            null -> {}
        }

        ChipRow(
            listOf(
                "ALL" to "ALL", "SHARED" to "◆ SHARED", "SECURED" to "SECURED", "OPEN" to "OPEN",
                "2.4G" to "2.4 GHZ", "5G" to "5 GHZ", "ROUTER" to "ROUTER", "HOTSPOT" to "HOTSPOT"
            ),
            filter
        ) { filter = it }
        Spacer(Modifier.height(4.dp))
        HudField(query, "FILTER BY NAME / BSSID / BRAND", Modifier.fillMaxWidth().height(40.dp)) { query = it }
        Spacer(Modifier.height(4.dp))

        if (list.isEmpty()) EmptyNote(if (Scanner.nets.isEmpty()) "NO NETWORKS FOUND YET.\nTAP SCAN." else "NOTHING MATCHES THIS FILTER")
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(list, key = { it.bssid }) { n ->
                NetRow(n, summaries[n.bssid]) { Nav.detail = n }
            }
        }
    }
}

@Composable
private fun NetRow(n: Net, s: Summary?, onClick: () -> Unit) {
    val c = Hud.colors
    HudItem(Modifier.fillMaxWidth(), selected = n.connected, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HudText(n.title, color = if (n.hidden) c.dim else c.primary, size = 20.sp, bold = true, maxLines = 1)
                Row {
                    Tag(n.security, secColor(n, c))
                    if (n.needsPassword) Tag(if (n.wps) "WPS ON" else "WPS OFF", if (n.wps) c.accent else c.dim)
                    Tag("CH ${n.channel}", c.text)
                    Tag(n.band, c.text)
                }
                Row {
                    Tag(if (n.kind == "HOTSPOT") "HOTSPOT~" else "ROUTER~", if (n.kind == "HOTSPOT") c.accent else c.good)
                    if (n.brand.isNotEmpty()) Tag(n.brand.uppercase(), c.primary)
                    if (n.standard.isNotEmpty()) Tag(n.standard.uppercase(), c.dim)
                    if (n.width > 0) Tag("${n.width}MHZ", c.dim)
                }
                Spacer(Modifier.height(3.dp))
                HudText(n.bssid.uppercase(), color = c.dim, size = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                HudText(
                    "█".repeat(n.bars) + "░".repeat(4 - n.bars),
                    color = if (n.bars >= 3) c.good else if (n.bars == 2) c.accent else c.danger, size = 14.sp, maxLines = 1
                )
                HudText("${n.level} dBm", color = c.dim, size = 10.sp)
                if (n.connected) HudText("CONNECTED", color = c.good, size = 9.sp, bold = true)
            }
        }
        // what the community database knows
        Spacer(Modifier.height(4.dp))
        when {
            s != null && s.variants > 0 -> HudText(
                "◆ ${s.variants} PASSWORD${if (s.variants > 1) "S" else ""} SHARED BY ${s.sharers} ${if (s.sharers == 1) "PERSON" else "PEOPLE"}" +
                    if (s.match == "device") "  (SAME ROUTER)" else "",
                color = c.good, size = 12.sp, bold = true
            )
            n.security == "OPEN" -> HudText("OPEN NETWORK - NO PASSWORD NEEDED", color = c.accent, size = 11.sp)
            n.needsPassword -> HudText(
                if (AppSettings.lookup) "NO PASSWORD SHARED YET" else "DATABASE LOOKUP OFF",
                color = c.dim, size = 11.sp
            )
            else -> HudText(n.security, color = c.dim, size = 11.sp)
        }
    }
}
