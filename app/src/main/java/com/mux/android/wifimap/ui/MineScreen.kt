package com.mux.android.wifimap.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.wifimap.data.Api
import com.mux.android.wifimap.data.MineItem
import com.mux.android.wifimap.data.Nav
import com.mux.android.wifimap.data.Repo
import com.mux.android.wifimap.util.copyText
import com.mux.android.wifimap.util.toast
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

/** Passwords this device has shared. */
@Composable
fun MineScreen() {
    val ctx = LocalContext.current
    val c = Hud.colors
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<MineItem>?>(null) }
    var error by remember { mutableStateOf("") }
    var sure by remember { mutableStateOf("") }

    LaunchedEffect(Nav.dataTick, Nav.resumeTick) {
        error = ""
        try {
            items = Api.mine()
        } catch (e: Exception) {
            error = e.message ?: "SERVER UNREACHABLE"
        }
    }

    Column(Modifier.fillMaxSize()) {
        HudPanel(Modifier.fillMaxWidth(), "MY CONTRIBUTIONS") {
            HudText("${items?.size ?: 0} PASSWORDS SHARED BY YOU", color = c.primary, size = 14.sp, bold = true)
            HudText("Thank you for helping others get online. You can remove anything you shared.", color = c.dim, size = 10.sp)
        }
        Spacer(Modifier.height(6.dp))
        if (error.isNotEmpty()) {
            HudText("DATABASE OFFLINE: $error", color = c.danger, size = 12.sp)
            HudButton("RETRY", Modifier.fillMaxWidth().height(42.dp), KeyKind.OP, fontSize = 12.sp) { Nav.dataTick++ }
        } else if (items != null && items!!.isEmpty()) {
            EmptyNote("YOU HAVEN'T SHARED ANYTHING YET.\nOPEN A NETWORK IN SCAN AND TAP SHARE.")
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(items ?: emptyList(), key = { it.id }) { m ->
                HudItem(Modifier.fillMaxWidth()) {
                    HudText(m.ssid, color = c.primary, size = 18.sp, bold = true, maxLines = 1)
                    HudText(m.bssid.uppercase(), color = c.dim, size = 10.sp)
                    HudText(m.password, color = c.text, size = 15.sp, bold = true)
                    Row {
                        Tag("${m.sharers} SHARED", c.good)
                        Tag("▲ ${m.up}", c.good)
                        Tag("▼ ${m.down}", if (m.down > 0) c.danger else c.dim)
                    }
                    HudText(fmt.format(Date(m.createdAt)), color = c.dim, size = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        HudButton("COPY", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 11.sp) { copyText(ctx, m.password, "wifi password") }
                        HudButton(
                            if (sure == m.id) "SURE?" else "REMOVE", Modifier.weight(1f).fillMaxHeight(), KeyKind.DANGER,
                            selected = sure == m.id, fontSize = 11.sp
                        ) {
                            if (sure != m.id) {
                                sure = m.id
                            } else {
                                sure = ""
                                scope.launch {
                                    try {
                                        Api.deleteMine(m.id)
                                        Repo.invalidateAll()
                                        Nav.dataTick++
                                    } catch (e: Exception) {
                                        toast(ctx, e.message ?: "FAILED")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
