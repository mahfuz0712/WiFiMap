package com.mux.android.wifimap.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.wifimap.data.AppSettings
import com.mux.android.wifimap.data.Nav
import com.mux.android.wifimap.data.Repo
import com.mux.android.wifimap.data.Scanner
import kotlinx.coroutines.delay

enum class Tab(val label: String, val title: String) {
    SCAN("SCAN", "RADAR"),
    MINE("MINE", "MY SHARES"),
    SYS("SYS", "SYSTEM")
}

@Composable
fun WifiMapRoot() {
    val ctx = LocalContext.current
    HudTheme(if (AppSettings.amber) AmberPalette else CyanPalette) {
        val tablet = LocalConfiguration.current.screenWidthDp >= 600
        var update by remember { mutableStateOf<UpdateInfo?>(null) }
        LaunchedEffect(Unit) { if (isOnline(ctx)) update = checkUpdate(ctx).second }

        // ask for the scan permissions once, on first launch
        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            Nav.permTick++
        }
        LaunchedEffect(Unit) {
            if (!AppSettings.askedPerms) {
                AppSettings.askedPerms = true
                val m = Scanner.requiredPermissions().filter { !com.mux.android.wifimap.util.hasPerm(ctx, it) }
                if (m.isNotEmpty()) permLauncher.launch(m.toTypedArray())
            }
        }

        // show what Android already has, then keep scanning while the radar is on screen
        LaunchedEffect(Nav.permTick, Nav.resumeTick) {
            Scanner.register(ctx)
            Scanner.publish(ctx)
        }
        val canScan = remember(Nav.permTick, Nav.resumeTick) { Scanner.problem(ctx) == null }
        LaunchedEffect(canScan, AppSettings.autoScan, AppSettings.scanSeconds, Nav.tab) {
            if (canScan && AppSettings.autoScan && Nav.tab == Tab.SCAN.ordinal) {
                while (true) {
                    Scanner.scan(ctx)
                    delay(AppSettings.scanSeconds * 1000L)
                }
            }
        }
        // every new scan: ask the community database about the secured networks
        LaunchedEffect(Scanner.nets, AppSettings.lookup) {
            delay(400)
            Repo.refresh(Scanner.nets)
        }

        val current = Tab.values()[Nav.tab]
        val screen: @Composable () -> Unit = {
            when (current) {
                Tab.SCAN -> RadarScreen(tablet)
                Tab.MINE -> MineScreen()
                Tab.SYS -> SystemScreen(tablet) { update = it }
            }
        }

        HudBackground(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
                    if (tablet) {
                        Row(Modifier.fillMaxSize()) {
                            Column(Modifier.width(92.dp).fillMaxHeight()) {
                                for (t in Tab.values()) {
                                    HudButton(t.label, Modifier.fillMaxWidth().height(60.dp), KeyKind.OP, selected = t == current, fontSize = 14.sp) {
                                        Nav.tab = t.ordinal
                                    }
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                Header(current.title, Modifier.fillMaxWidth().height(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.weight(1f).fillMaxWidth()) { screen() }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Header(current.title, Modifier.fillMaxWidth().height(34.dp))
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.weight(1f).fillMaxWidth()) { screen() }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth().height(46.dp)) {
                                for (t in Tab.values()) {
                                    HudButton(t.label, Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, selected = t == current, fontSize = 12.sp) {
                                        Nav.tab = t.ordinal
                                    }
                                }
                            }
                        }
                    }
                }
                // network page on top of everything
                Nav.detail?.let { DetailScreen(it) }
            }
        }

        Nav.share?.let { ShareDialog(it) }
        if (!AppSettings.introSeen) IntroDialog()
        update?.let { u ->
            UpdateDialog(
                u, appVersion(ctx),
                onUpdate = { openUrl(ctx, u.apkUrl); update = null },
                onCancel = { update = null }
            )
        }
    }
}

@Composable
private fun Header(title: String, modifier: Modifier) {
    val c = Hud.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        HudText("◈ WI-FI MAP", color = c.primary, size = 16.sp, bold = true)
        HudText(title, color = c.dim, size = 11.sp)
    }
}

@Composable
private fun IntroDialog() {
    val c = Hud.colors
    HudDialog("WELCOME", onDismiss = { AppSettings.introSeen = true }) {
        HudText("◈ WI-FI MAP", color = c.primary, size = 22.sp, bold = true)
        Spacer(Modifier.height(8.dp))
        HudText("Find out which Wi-Fi networks are around you and what passwords other people have shared for them.", color = c.text, size = 12.sp)
        Spacer(Modifier.height(6.dp))
        HudText("• Many public places print the password on a sign or QR code. Share it once and everyone nearby can use it without asking staff.", color = c.text, size = 11.sp)
        HudText("• This is NOT a hacking tool. Passwords are never cracked - only shared by people on purpose.", color = c.good, size = 11.sp)
        HudText("• Only share public passwords or ones you have permission to share. Never personal hotspots.", color = c.accent, size = 11.sp)
        HudText("• To check the database the app sends the name + MAC address of secured networks near you. Turn this off any time in SYS.", color = c.dim, size = 10.sp)
        DialogButtons(Triple("I UNDERSTAND", KeyKind.GOOD) { AppSettings.introSeen = true })
    }
}
