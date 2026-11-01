package com.mux.android.wifimap.data

import android.net.wifi.ScanResult
import android.os.Build

/** Turns Android's raw ScanResult into the facts shown in the list. */
object WifiParser {
    fun security(caps: String): String {
        val c = caps.uppercase()
        val eap = c.contains("EAP")
        val wpa3 = c.contains("SAE") || c.contains("WPA3")
        val owe = c.contains("OWE")
        val wpa2 = c.contains("WPA2") || c.contains("RSN")
        val wpa = c.contains("WPA-") || (c.contains("WPA") && !wpa2 && !wpa3)
        val wep = c.contains("WEP")
        return when {
            eap -> "ENTERPRISE"
            wpa3 && wpa2 -> "WPA2/WPA3"
            wpa3 -> "WPA3"
            owe -> "OWE"
            wpa2 && wpa -> "WPA/WPA2"
            wpa2 -> "WPA2"
            wpa -> "WPA"
            wep -> "WEP"
            else -> "OPEN"
        }
    }

    fun band(freq: Int): String = when (freq) {
        in 2400..2500 -> "2.4G"
        in 4900..5899 -> "5G"
        in 5925..7125 -> "6G"
        else -> "?"
    }

    fun channel(freq: Int): Int = when (freq) {
        2484 -> 14
        in 2400..2500 -> (freq - 2407) / 5
        in 4900..5899 -> (freq - 5000) / 5
        in 5925..7125 -> (freq - 5950) / 5
        else -> 0
    }

    private val phoneSsid = Regex(
        "(?i)(android ?ap|iphone|ipad|galaxy|samsung|redmi|xiaomi|poco|\\bmi ?\\d|oppo|vivo|realme|oneplus|pixel|" +
            "moto ?[a-z]|infinix|tecno|itel|honor|nokia ?\\d|hotspot|my ?phone|\\bs\\d{2}\\b|\\bnote ?\\d)"
    )

    /** Router or phone hotspot? There is no flag for it, so this is an estimate from MAC + name. */
    fun kind(bssid: String, ssid: String, wps: Boolean, vendor: String): String {
        var score = 0
        val locAdmin = Oui.isLocallyAdministered(bssid)
        when (Oui.brandType(vendor)) {
            "PHONE" -> score += if (locAdmin) 3 else 1
            "ROUTER" -> score -= 3
        }
        if (phoneSsid.containsMatchIn(ssid)) score += 3
        if (locAdmin) score += 1
        if (!wps) score += 1
        return if (score >= 3) "HOTSPOT" else "ROUTER"
    }

    @Suppress("DEPRECATION")
    fun toNet(r: ScanResult, connectedBssid: String): Net? {
        val bssid = (r.BSSID ?: return null).lowercase()
        val ssid = (r.SSID ?: "").trim()
        val caps = r.capabilities ?: ""
        val wps = caps.uppercase().contains("WPS")
        val freq = r.frequency
        val width = if (Build.VERSION.SDK_INT >= 23) {
            when (r.channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> 20
                ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
                else -> 0
            }
        } else 0
        val standard = if (Build.VERSION.SDK_INT >= 30) {
            when (r.wifiStandard) {
                4 -> "Wi-Fi 4"
                5 -> "Wi-Fi 5"
                6 -> "Wi-Fi 6"
                8 -> "Wi-Fi 7"
                else -> ""
            }
        } else ""
        val vendor = Oui.vendor(bssid)
        return Net(
            bssid = bssid,
            ssid = ssid,
            caps = caps,
            level = r.level,
            freq = freq,
            width = width,
            security = security(caps),
            wps = wps,
            band = band(freq),
            channel = channel(freq),
            standard = standard,
            kind = kind(bssid, ssid, wps, vendor),
            brand = vendor,
            connected = bssid == connectedBssid
        )
    }
}
