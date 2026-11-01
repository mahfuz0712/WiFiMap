package com.mux.android.wifimap.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mux.android.wifimap.data.Api
import com.mux.android.wifimap.data.AppSettings
import com.mux.android.wifimap.data.Connect
import com.mux.android.wifimap.data.CredItem
import com.mux.android.wifimap.data.Nav
import com.mux.android.wifimap.data.Net
import com.mux.android.wifimap.data.Repo
import com.mux.android.wifimap.data.ShareDraft
import com.mux.android.wifimap.data.parseWifiQr
import com.mux.android.wifimap.util.copyText
import com.mux.android.wifimap.util.toast
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun DetailScreen(net: Net) {
    val ctx = LocalContext.current
    val c = Hud.colors
    val scope = rememberCoroutineScope()
    // newest scan data for this access point (signal changes), falls back to the tapped snapshot
    val live = com.mux.android.wifimap.data.Scanner.nets.firstOrNull { it.bssid == net.bssid } ?: net

    var creds by remember(net.bssid) { mutableStateOf<List<CredItem>?>(null) }
    var error by remember(net.bssid) { mutableStateOf("") }
    var reportFor by remember { mutableStateOf<CredItem?>(null) }
    var sure by remember { mutableStateOf("") }
    val connectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    LaunchedEffect(net.bssid, Nav.dataTick, AppSettings.lookup) {
        error = ""
        if (!live.needsPassword || !AppSettings.lookup) return@LaunchedEffect
        try {
            creds = Api.detail(live)
        } catch (e: Exception) {
            error = e.message ?: "SERVER UNREACHABLE"
        }
    }

    reportFor?.let { r ->
        HudDialog("REPORT THIS PASSWORD", onDismiss = { reportFor = null }) {
            HudText("WHY?", color = c.dim, size = 11.sp)
            Spacer(Modifier.height(6.dp))
            val reasons = listOf(
                Triple("wrong", "IT DOES NOT WORK", KeyKind.OP),
                Triple("private", "PRIVATE NETWORK - SHOULD NOT BE PUBLIC", KeyKind.ACCENT),
                Triple("owner", "I AM THE OWNER - PLEASE REMOVE", KeyKind.DANGER),
                Triple("abuse", "SPAM / ABUSE", KeyKind.FN)
            )
            for ((key, label, kind) in reasons) {
                HudButton(label, Modifier.fillMaxWidth().height(46.dp), kind, fontSize = 11.sp) {
                    scope.launch {
                        try {
                            Api.report(r.id, key)
                            toast(ctx, "REPORT SENT - THANK YOU")
                            Nav.dataTick++
                        } catch (e: Exception) {
                            toast(ctx, e.message ?: "FAILED")
                        }
                    }
                    reportFor = null
                }
            }
            HudButton("CANCEL", Modifier.fillMaxWidth().height(42.dp), KeyKind.FN, fontSize = 12.sp) { reportFor = null }
        }
    }

    Overlay("NETWORK", onClose = { Nav.detail = null }) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            // ---- title
            HudText(live.title, color = if (live.hidden) c.dim else c.primary, size = 30.sp, bold = true)
            HudText(live.bssid.uppercase(), color = c.dim, size = 13.sp)
            if (live.connected) HudText("● YOU ARE CONNECTED TO THIS ACCESS POINT", color = c.good, size = 11.sp, bold = true)
            Spacer(Modifier.height(8.dp))

            // ---- specs
            HudPanel(Modifier.fillMaxWidth(), "SPECS") {
                Spec("SECURITY", live.security, if (live.security == "OPEN") c.accent else c.text)
                if (live.needsPassword) Spec("WPS", if (live.wps) "ON  (WEAKER: PIN LOGIN POSSIBLE)" else "OFF", if (live.wps) c.accent else c.good)
                Spec("BAND", live.band + "  ·  " + live.freq + " MHZ", c.text)
                Spec("CHANNEL", live.channel.toString() + if (live.width > 0) "  ·  ${live.width} MHZ WIDE" else "", c.text)
                if (live.standard.isNotEmpty()) Spec("STANDARD", live.standard.uppercase(), c.text)
                Spec("SIGNAL", "${live.level} dBm  ·  ${live.signalPercent}%", c.text)
                Spec("TYPE", if (live.kind == "HOTSPOT") "PHONE / PERSONAL HOTSPOT (ESTIMATE)" else "ROUTER / ACCESS POINT (ESTIMATE)", if (live.kind == "HOTSPOT") c.accent else c.text)
                Spec("BRAND", live.brand.ifEmpty { "UNKNOWN" }.uppercase(), c.text)
            }
            Spacer(Modifier.height(8.dp))

            // ---- community passwords
            HudPanel(Modifier.fillMaxWidth(), "PASSWORDS SHARED BY THE COMMUNITY") {
                when {
                    live.security == "OPEN" || live.security == "OWE" ->
                        HudText("OPEN NETWORK. NO PASSWORD NEEDED.", color = c.accent, size = 13.sp, bold = true)
                    live.security == "ENTERPRISE" ->
                        HudText("This network needs a personal login (company / school). It has no shared password.", color = c.dim, size = 12.sp)
                    !AppSettings.lookup -> {
                        HudText("Database lookup is turned off.", color = c.dim, size = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        HudButton("TURN ON", Modifier.fillMaxWidth().height(42.dp), KeyKind.ACCENT, fontSize = 12.sp) { AppSettings.lookup = true }
                    }
                    error.isNotEmpty() -> {
                        HudText("DATABASE OFFLINE: $error", color = c.danger, size = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        HudButton("RETRY", Modifier.fillMaxWidth().height(42.dp), KeyKind.OP, fontSize = 12.sp) { Nav.dataTick++ }
                    }
                    creds == null -> HudText("LOADING...", color = c.dim, size = 12.sp)
                    creds!!.isEmpty() -> {
                        HudText("NOBODY HAS SHARED A PASSWORD FOR THIS WI-FI YET.", color = c.dim, size = 12.sp)
                        HudText("Know it from a sign, a QR code or the staff? Be the first to share it.", color = c.dim, size = 10.sp)
                    }
                    else -> {
                        for (cr in creds!!) {
                            CredCard(
                                cr = cr, sure = sure == cr.id,
                                onConnect = {
                                    toast(ctx, Connect.connect(ctx, live, cr.password) { connectLauncher.launch(it) })
                                },
                                onCopy = { copyText(ctx, cr.password, "wifi password") },
                                onVote = { v ->
                                    scope.launch {
                                        try {
                                            Api.vote(cr.id, if (cr.myVote == v) 0 else v)
                                            Nav.dataTick++
                                        } catch (e: Exception) {
                                            toast(ctx, e.message ?: "FAILED")
                                        }
                                    }
                                },
                                onReport = { reportFor = cr },
                                onDelete = {
                                    if (sure != cr.id) {
                                        sure = cr.id
                                    } else {
                                        sure = ""
                                        scope.launch {
                                            try {
                                                Api.deleteMine(cr.id)
                                                Repo.invalidate(live.bssid)
                                                Nav.dataTick++
                                            } catch (e: Exception) {
                                                toast(ctx, e.message ?: "FAILED")
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // ---- share
            HudPanel(Modifier.fillMaxWidth(), "SHARE") {
                when {
                    live.kind == "HOTSPOT" && live.needsPassword ->
                        HudText("This looks like a personal phone hotspot. Personal hotspots cannot be shared.", color = c.accent, size = 12.sp)
                    !live.canShare ->
                        HudText("Nothing to share for this network.", color = c.dim, size = 12.sp)
                    else -> {
                        HudText("Do you know the password because it is public (sign, QR code, staff told customers) or because you have permission?", color = c.text, size = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        HudButton("SHARE A PASSWORD", Modifier.fillMaxWidth().height(48.dp), KeyKind.GOOD, fontSize = 13.sp) {
                            Nav.share = ShareDraft(live)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                HudText(
                    "This is NOT a hacking tool. Passwords only come from people who chose to share them. Owner and do not want your Wi-Fi listed? Tap REPORT on the password and choose \"I AM THE OWNER\".",
                    color = c.dim, size = 9.sp
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Spec(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        HudText(label, color = Hud.colors.dim, size = 11.sp, modifier = Modifier.width(92.dp))
        HudText(value, color = color, size = 12.sp, bold = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CredCard(
    cr: CredItem,
    sure: Boolean,
    onConnect: () -> Unit,
    onCopy: () -> Unit,
    onVote: (Int) -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit
) {
    val c = Hud.colors
    HudItem(Modifier.fillMaxWidth()) {
        HudText(cr.password, color = c.primary, size = 22.sp, bold = true)
        Spacer(Modifier.height(2.dp))
        Row {
            Tag("${cr.sharers} ${if (cr.sharers == 1) "PERSON" else "PEOPLE"} SHARED THIS", c.good)
            if (cr.via == "qr") Tag("FROM QR CODE", c.accent)
        }
        Row {
            if (cr.match == "device") Tag("SAME ROUTER, OTHER BAND / SLOT", c.accent) else Tag("THIS ACCESS POINT", c.primary)
        }
        HudText(
            "▲ ${cr.up} WORKED   ▼ ${cr.down} FAILED   ·   ${dateFmt.format(Date(cr.updatedAt))}",
            color = c.dim, size = 10.sp
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HudButton("CONNECT", Modifier.weight(1.3f).fillMaxHeight(), KeyKind.GOOD, fontSize = 11.sp, onClick = onConnect)
            HudButton("COPY", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 11.sp, onClick = onCopy)
        }
        Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HudButton("✓ WORKED", Modifier.weight(1f).fillMaxHeight(), KeyKind.GOOD, selected = cr.myVote == 1, fontSize = 10.sp) { onVote(1) }
            HudButton("✕ FAILED", Modifier.weight(1f).fillMaxHeight(), KeyKind.DANGER, selected = cr.myVote == -1, fontSize = 10.sp) { onVote(-1) }
            HudButton("REPORT", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 10.sp, onClick = onReport)
        }
        if (cr.mine) {
            HudButton(
                if (sure) "TAP AGAIN TO REMOVE MY SHARE" else "REMOVE MY SHARE",
                Modifier.fillMaxWidth().height(40.dp), KeyKind.DANGER, selected = sure, fontSize = 10.sp, onClick = onDelete
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// share dialog
// -------------------------------------------------------------------------------------------------

@Composable
fun ShareDialog(d: ShareDraft) {
    val ctx = LocalContext.current
    val c = Hud.colors
    val scope = rememberCoroutineScope()
    var pw by remember(d) { mutableStateOf(d.password) }
    var via by remember(d) { mutableStateOf(d.via) }
    var confirmed by remember(d) { mutableStateOf(false) }
    var busy by remember(d) { mutableStateOf(false) }
    var error by remember(d) { mutableStateOf("") }

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { res ->
        val text = res.contents ?: return@rememberLauncherForActivityResult
        val q = parseWifiQr(text)
        when {
            q == null -> toast(ctx, "NOT A WI-FI QR CODE")
            q.ssid != d.net.ssid -> toast(ctx, "THIS QR CODE IS FOR \"${q.ssid}\", NOT \"${d.net.ssid}\"")
            q.password.isEmpty() -> toast(ctx, "THIS QR CODE HAS NO PASSWORD")
            else -> {
                pw = q.password
                via = "qr"
            }
        }
    }

    val minLen = if (d.net.security == "WEP") 5 else 8
    val validLen = pw.length in minLen..64
    val canSend = validLen && confirmed && !busy

    HudDialog("SHARE PASSWORD", onDismiss = { if (!busy) Nav.share = null }) {
        HudText(d.net.title, color = c.primary, size = 20.sp, bold = true, maxLines = 1)
        HudText(d.net.bssid.uppercase() + "  ·  " + d.net.security, color = c.dim, size = 10.sp)
        Spacer(Modifier.height(10.dp))
        HudField(pw, "WI-FI PASSWORD", Modifier.fillMaxWidth().height(46.dp)) {
            pw = it
            via = "manual"
            error = ""
        }
        if (pw.isNotEmpty() && !validLen) HudText("PASSWORD MUST BE $minLen TO 64 CHARACTERS", color = c.accent, size = 10.sp)
        Spacer(Modifier.height(6.dp))
        HudButton("SCAN WI-FI QR CODE", Modifier.fillMaxWidth().height(40.dp), KeyKind.OP, fontSize = 11.sp) {
            qrLauncher.launch(
                ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("Point at the Wi-Fi QR code").setBeepEnabled(false).setOrientationLocked(false)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HudText(
                "I CONFIRM THIS PASSWORD IS PUBLIC (SIGN / QR / TOLD TO CUSTOMERS) OR I HAVE PERMISSION TO SHARE IT.",
                color = c.text, size = 10.sp, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            HudButton(if (confirmed) "YES" else "NO", Modifier.width(60.dp).height(40.dp), if (confirmed) KeyKind.GOOD else KeyKind.FN, fontSize = 12.sp) {
                confirmed = !confirmed
            }
        }
        HudText("NEVER SHARE A HOME / PRIVATE WI-FI OR A PERSONAL HOTSPOT.", color = c.accent, size = 9.sp)
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            HudText(error, color = c.danger, size = 11.sp, bold = true)
        }
        DialogButtons(
            Triple("CANCEL", KeyKind.FN) { if (!busy) Nav.share = null },
            Triple(if (busy) "SENDING..." else "SHARE", if (canSend) KeyKind.GOOD else KeyKind.FN) {
                if (canSend) {
                    busy = true
                    scope.launch {
                        try {
                            Api.share(d.net, pw, via)
                            Repo.invalidate(d.net.bssid)
                            toast(ctx, "THANK YOU - PASSWORD SHARED")
                            Nav.dataTick++
                            Nav.share = null
                            Repo.refresh(com.mux.android.wifimap.data.Scanner.nets, force = true)
                        } catch (e: Exception) {
                            error = e.message ?: "SERVER UNREACHABLE"
                        }
                        busy = false
                    }
                }
            }
        )
    }
}
