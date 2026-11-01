package com.mux.android.wifimap.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.mux.android.wifimap.util.hasPerm

enum class Problem { NO_PERMISSION, LOCATION_OFF, WIFI_OFF }

/** Wraps WifiManager scanning. Results are published as Compose state. */
object Scanner {
    var nets by mutableStateOf<List<Net>>(emptyList())
        private set
    var scanning by mutableStateOf(false)
        private set
    var lastScan by mutableLongStateOf(0L)
        private set
    var connectedSsid by mutableStateOf("")
        private set
    var note by mutableStateOf("")
        private set

    private var receiver: BroadcastReceiver? = null
    private val main = Handler(Looper.getMainLooper())

    private fun wm(ctx: Context): WifiManager =
        ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun requiredPermissions(): List<String> {
        val l = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        return l
    }

    private fun locationOn(ctx: Context): Boolean {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
            else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            true
        }
    }

    fun problem(ctx: Context): Problem? {
        if (requiredPermissions().any { !hasPerm(ctx, it) }) return Problem.NO_PERMISSION
        if (!locationOn(ctx)) return Problem.LOCATION_OFF
        return try {
            if (!wm(ctx).isWifiEnabled) Problem.WIFI_OFF else null
        } catch (e: Exception) {
            null
        }
    }

    fun register(ctx: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                scanning = false
                publish(c)
            }
        }
        receiver = r
        ContextCompat.registerReceiver(
            ctx.applicationContext, r, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_EXPORTED
        )
        publish(ctx)
    }

    fun unregister(ctx: Context) {
        val r = receiver ?: return
        try {
            ctx.applicationContext.unregisterReceiver(r)
        } catch (e: Exception) {
        }
        receiver = null
    }

    @Suppress("DEPRECATION")
    fun scan(ctx: Context) {
        if (problem(ctx) != null) return
        try {
            val ok = wm(ctx).startScan()
            if (ok) {
                scanning = true
                note = ""
                main.postDelayed({ scanning = false }, 10_000)
            } else {
                // Android limits apps to a few scans every 2 minutes - show what the system already has
                note = "ANDROID LIMITS SCAN SPEED - SHOWING LATEST RESULTS"
                publish(ctx)
            }
        } catch (e: SecurityException) {
            note = "LOCATION PERMISSION REQUIRED"
        } catch (e: Exception) {
            note = "SCAN FAILED"
        }
    }

    @Suppress("DEPRECATION")
    fun publish(ctx: Context) {
        if (problem(ctx) != null) return
        try {
            val m = wm(ctx)
            var connBssid = ""
            var connSsid = ""
            try {
                val info = m.connectionInfo
                if (info != null && info.networkId != -1) {
                    connBssid = (info.bssid ?: "").lowercase()
                    connSsid = (info.ssid ?: "").trim('"')
                    if (connSsid == "<unknown ssid>") connSsid = ""
                }
            } catch (e: Exception) {
            }
            val seen = HashMap<String, Net>()
            for (r in m.scanResults) {
                val n = WifiParser.toNet(r, connBssid) ?: continue
                val old = seen[n.bssid]
                if (old == null || n.level > old.level) seen[n.bssid] = n
            }
            nets = seen.values.toList()
            connectedSsid = connSsid
            lastScan = System.currentTimeMillis()
        } catch (e: SecurityException) {
            note = "LOCATION PERMISSION REQUIRED"
        } catch (e: Exception) {
        }
    }
}
