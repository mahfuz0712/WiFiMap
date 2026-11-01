package com.mux.android.wifimap.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.wifimap.AppInfo
import com.mux.android.wifimap.data.Api
import com.mux.android.wifimap.data.AppSettings
import com.mux.android.wifimap.data.Nav
import com.mux.android.wifimap.data.Repo
import com.mux.android.wifimap.data.Scanner
import com.mux.android.wifimap.util.hasPerm
import com.mux.android.wifimap.util.openAppSettings
import kotlinx.coroutines.launch

/** "SYS" tab: settings, rules, permissions, updates, developer info. */
@Composable
fun SystemScreen(tablet: Boolean, onUpdateFound: (UpdateInfo) -> Unit) {
    val uri = LocalUriHandler.current
    val open: (String) -> Unit = { link -> runCatching { uri.openUri(link) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (tablet) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    AppPanel(); Gap(); RulesPanel(); Gap(); PermissionsPanel(); Gap(); UpdatePanel(onUpdateFound)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    DatabasePanel(); Gap(); ScanPanel(); Gap(); AuthorPanel(open); Gap(); MoreAppsPanel(open)
                }
            }
        } else {
            AppPanel(); Gap(); RulesPanel(); Gap(); DatabasePanel(); Gap(); ScanPanel(); Gap(); PermissionsPanel(); Gap()
            UpdatePanel(onUpdateFound); Gap(); AuthorPanel(open); Gap(); MoreAppsPanel(open)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Gap() = Spacer(Modifier.height(8.dp))

@Composable
private fun SettingRow(label: String, hint: String?, value: String, kind: KeyKind = KeyKind.OP, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            HudText(label, color = Hud.colors.text, size = 12.sp)
            if (hint != null) HudText(hint, color = Hud.colors.dim, size = 9.sp)
        }
        HudButton(value, Modifier.width(118.dp).height(38.dp), kind, fontSize = 11.sp, onClick = onClick)
    }
}

@Composable
private fun ToggleRow(label: String, hint: String? = null, value: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(label, hint, if (value) "ON" else "OFF", if (value) KeyKind.GOOD else KeyKind.FN) { onChange(!value) }
}

@Composable
private fun AppPanel() {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "SYSTEM") {
        HudText("◈ ${AppInfo.NAME.uppercase()}", color = c.primary, size = 28.sp, bold = true)
        HudText("VERSION ${appVersion(LocalContext.current)}", color = c.accent, size = 12.sp)
        Spacer(Modifier.height(8.dp))
        HudText(
            "A community Wi-Fi map. See the networks around you, what they are, and passwords that other people shared on purpose - like the ones printed on public QR codes.",
            color = c.text, size = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        SettingRow("COLOR THEME", null, if (AppSettings.amber) "AMBER" else "CYAN", KeyKind.ACCENT) { AppSettings.amber = !AppSettings.amber }
    }
}

@Composable
private fun RulesPanel() {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "WHAT THIS APP IS - AND IS NOT") {
        HudText("✓ A WAY TO FIND PASSWORDS PEOPLE CHOSE TO SHARE: PUBLIC PLACES, CAFES, OFFICES, STAFF-ANNOUNCED WI-FI.", color = c.good, size = 11.sp)
        Spacer(Modifier.height(4.dp))
        HudText("✕ NOT A HACKING TOOL. IT NEVER CRACKS, GUESSES OR READS A PASSWORD. WPS IS SHOWN ONLY AS INFORMATION.", color = c.danger, size = 11.sp)
        Spacer(Modifier.height(4.dp))
        HudText("✕ PERSONAL PHONE HOTSPOTS CAN'T BE SHARED. HOME WI-FI SHOULD NOT BE SHARED WITHOUT THE OWNER'S OK.", color = c.accent, size = 11.sp)
        Spacer(Modifier.height(4.dp))
        HudText("OWNER? TAP REPORT ON ANY LISTED PASSWORD AND CHOOSE \"I AM THE OWNER\" TO HAVE IT REMOVED.", color = c.dim, size = 10.sp)
        Spacer(Modifier.height(6.dp))
        HudText(
            "PRIVACY: THE APP SENDS THE NAME + MAC ADDRESS OF SECURED NETWORKS IN RANGE (NOT OPEN ONES) AND ANY PASSWORD YOU CHOOSE TO SHARE. NO NAME, PHONE NUMBER OR LOCATION IS SENT. YOUR DEVICE GETS A RANDOM ANONYMOUS ID.",
            color = c.dim, size = 9.sp
        )
    }
}

@Composable
private fun DatabasePanel() {
    val c = Hud.colors
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    HudPanel(Modifier.fillMaxWidth(), "COMMUNITY DATABASE") {
        ToggleRow("CHECK THE DATABASE", "TURN OFF TO NEVER SEND NETWORK INFO", AppSettings.lookup) { AppSettings.lookup = it }
        Spacer(Modifier.height(4.dp))
        HudText("SERVER ADDRESS", color = c.dim, size = 10.sp)
        HudField(AppSettings.serverUrl, "https://", Modifier.fillMaxWidth().height(44.dp)) { AppSettings.serverUrl = it.trim() }
        Spacer(Modifier.height(6.dp))
        HudButton("TEST CONNECTION", Modifier.fillMaxWidth().height(44.dp), KeyKind.OP, fontSize = 12.sp) {
            scope.launch {
                status = "CONNECTING... (A SLEEPING SERVER CAN TAKE ~1 MIN)"
                status = try {
                    val s = Api.stats()
                    "ONLINE ✓  ${s.networks} NETWORKS · ${s.shares} SHARED PASSWORDS · ${s.users} PEOPLE"
                } catch (e: Exception) {
                    "OFFLINE: ${e.message ?: "ERROR"}"
                }
            }
        }
        HudButton("RESET MY ANONYMOUS ID", Modifier.fillMaxWidth().height(40.dp), KeyKind.FN, fontSize = 11.sp) {
            AppSettings.token = ""
            status = "NEW ID WILL BE CREATED ON NEXT USE"
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            HudText(status, color = if (status.startsWith("ONLINE")) c.good else c.dim, size = 11.sp)
        }
    }
}

@Composable
private fun ScanPanel() {
    HudPanel(Modifier.fillMaxWidth(), "SCANNING") {
        ToggleRow("AUTO SCAN", "WHILE THE RADAR TAB IS OPEN", AppSettings.autoScan) { AppSettings.autoScan = it }
        val opts = listOf(15, 30, 60)
        SettingRow("SCAN EVERY", "ANDROID ALLOWS ONLY A FEW SCANS PER 2 MIN", "${AppSettings.scanSeconds} SEC") {
            AppSettings.scanSeconds = opts[(opts.indexOf(AppSettings.scanSeconds).coerceAtLeast(0) + 1) % opts.size]
        }
        HudText("TYPE LABELS ENDING IN ~ (ROUTER~ / HOTSPOT~) ARE ESTIMATES BASED ON THE MAC ADDRESS AND NAME.", color = Hud.colors.dim, size = 9.sp)
    }
}

@Composable
private fun PermissionsPanel() {
    val c = Hud.colors
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Nav.permTick++ }
    val perms = remember { Scanner.requiredPermissions() + Manifest.permission.CAMERA }
    val status = remember(Nav.permTick, Nav.resumeTick) { perms.map { it to hasPerm(ctx, it) } }
    HudPanel(Modifier.fillMaxWidth(), "PERMISSIONS") {
        for ((p, ok) in status) {
            val label = when (p) {
                Manifest.permission.ACCESS_FINE_LOCATION -> "LOCATION (REQUIRED BY ANDROID TO LIST WI-FI)"
                Manifest.permission.NEARBY_WIFI_DEVICES -> "NEARBY WI-FI DEVICES"
                Manifest.permission.CAMERA -> "CAMERA (SCAN WI-FI QR CODES)"
                else -> p.substringAfterLast('.')
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                HudText(label, color = c.text, size = 11.sp, modifier = Modifier.weight(1f))
                HudText(if (ok) "OK" else "MISSING", color = if (ok) c.good else c.danger, size = 11.sp, bold = true)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HudButton("GRANT ALL", Modifier.weight(1f).fillMaxHeight(), KeyKind.ACCENT, enabled = status.any { !it.second }, fontSize = 12.sp) {
                val m = perms.filter { !hasPerm(ctx, it) }
                if (m.isNotEmpty()) launcher.launch(m.toTypedArray())
            }
            HudButton("APP SETTINGS", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 12.sp) { openAppSettings(ctx) }
        }
    }
}

@Composable
private fun UpdatePanel(onUpdateFound: (UpdateInfo) -> Unit) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    HudPanel(Modifier.fillMaxWidth(), "UPDATES") {
        HudButton("CHECK FOR UPDATES", Modifier.fillMaxWidth().height(46.dp), KeyKind.OP, fontSize = 13.sp) {
            scope.launch {
                status = "CHECKING..."
                if (!isOnline(ctx)) {
                    status = "NO INTERNET CONNECTION"
                    return@launch
                }
                val (ok, info) = checkUpdate(ctx)
                status = when {
                    !ok -> "CHECK FAILED"
                    info == null -> "UP TO DATE"
                    else -> "NEW VERSION AVAILABLE: v${info.version}"
                }
                if (info != null) onUpdateFound(info)
            }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HudText(status, color = c.dim, size = 12.sp)
        }
    }
}

@Composable
private fun AuthorPanel(open: (String) -> Unit) {
    HudPanel(Modifier.fillMaxWidth(), "AUTHOR") {
        LinkRow("NAME", AppInfo.AUTHOR, null, open)
        LinkRow("EMAIL", AppInfo.EMAIL, "mailto:${AppInfo.EMAIL}", open)
        LinkRow("GITHUB", "github.com/mahfuz0712", AppInfo.GITHUB, open)
        LinkRow("PORTFOLIO", "portfolio-mahfuz0712.vercel.app", AppInfo.PORTFOLIO, open)
    }
}

@Composable
private fun MoreAppsPanel(open: (String) -> Unit) {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "MORE APPS") {
        HudText("Discover more apps by the developer on zApps Store.", color = c.text, size = 13.sp)
        Spacer(Modifier.height(8.dp))
        HudButton("OPEN zApps STORE ▸", Modifier.fillMaxWidth().height(48.dp), KeyKind.ACCENT, fontSize = 14.sp) { open(AppInfo.MORE_APPS) }
    }
}

@Composable
private fun LinkRow(label: String, value: String, link: String?, open: (String) -> Unit) {
    val c = Hud.colors
    Column(
        Modifier.fillMaxWidth().then(if (link != null) Modifier.clickable { open(link) } else Modifier).padding(vertical = 6.dp)
    ) {
        HudText(label, color = c.dim, size = 10.sp)
        HudText(value, color = if (link != null) c.primary else c.text, size = 16.sp)
    }
}
