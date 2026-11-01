package com.mux.android.wifimap.data

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi

/**
 * Joins a network with a (community) password. Android decides how:
 *  - 11+  : system dialog "Save this network?"
 *  - 10   : network suggestion (approve the notification)
 *  - 9-   : classic add + enable
 */
object Connect {
    fun supported(net: Net): Boolean = net.security != "WEP" && net.security != "ENTERPRISE" && net.ssid.isNotEmpty()

    /** [launch] is used on Android 11+ to open the system dialog. Returns a short message for the user. */
    fun connect(ctx: Context, net: Net, password: String, launch: (Intent) -> Unit): String {
        if (!supported(net)) return "THIS NETWORK TYPE CAN'T BE JOINED FROM THE APP - COPY THE PASSWORD"
        return try {
            when {
                Build.VERSION.SDK_INT >= 30 -> {
                    launch(addNetworksIntent(net, password))
                    "CONFIRM IN THE ANDROID DIALOG"
                }
                Build.VERSION.SDK_INT >= 29 -> suggest(ctx, net, password)
                else -> legacy(ctx, net, password)
            }
        } catch (e: Exception) {
            "COULD NOT CONNECT - COPY THE PASSWORD AND JOIN FROM WI-FI SETTINGS"
        }
    }

    @RequiresApi(29)
    private fun suggestion(net: Net, pw: String): WifiNetworkSuggestion {
        val b = WifiNetworkSuggestion.Builder().setSsid(net.ssid)
        if (net.security == "WPA3") b.setWpa3Passphrase(pw) else b.setWpa2Passphrase(pw)
        return b.build()
    }

    @RequiresApi(30)
    private fun addNetworksIntent(net: Net, pw: String): Intent {
        val list = arrayListOf(suggestion(net, pw))
        return Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, list)
    }

    @RequiresApi(29)
    private fun suggest(ctx: Context, net: Net, pw: String): String {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val status = wm.addNetworkSuggestions(listOf(suggestion(net, pw)))
        return if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ||
            status == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE
        ) "NETWORK ADDED - APPROVE THE ANDROID NOTIFICATION TO CONNECT"
        else "ANDROID REFUSED THE NETWORK - COPY THE PASSWORD INSTEAD"
    }

    @Suppress("DEPRECATION")
    private fun legacy(ctx: Context, net: Net, pw: String): String {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val conf = WifiConfiguration()
        conf.SSID = "\"" + net.ssid + "\""
        val hex64 = pw.length == 64 && pw.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        conf.preSharedKey = if (hex64) pw else "\"" + pw + "\""
        conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
        val id = wm.addNetwork(conf)
        if (id == -1) return "ANDROID REFUSED THE NETWORK - COPY THE PASSWORD INSTEAD"
        wm.disconnect()
        wm.enableNetwork(id, true)
        wm.reconnect()
        return "CONNECTING..."
    }
}
