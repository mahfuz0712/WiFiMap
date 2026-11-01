package com.mux.android.wifimap.data

import android.content.Context

/**
 * Vendor lookup from the MAC address (IEEE OUI list, bundled in assets/oui.txt).
 * The first 3 bytes of a BSSID tell which company made the radio / router.
 */
object Oui {
    @Volatile
    private var map: HashMap<String, String>? = null
    @Volatile
    private var loading = false

    fun preload(ctx: Context) {
        if (map != null || loading) return
        loading = true
        val app = ctx.applicationContext
        Thread {
            val m = HashMap<String, String>(50000)
            try {
                app.assets.open("oui.txt").bufferedReader().useLines { lines ->
                    for (l in lines) {
                        if (l.length > 7 && l[6] == '|') m[l.substring(0, 6)] = l.substring(7)
                    }
                }
            } catch (e: Exception) {
            }
            map = m
            loading = false
        }.start()
    }

    /** Phone hotspots use a "locally administered" MAC (bit 0x02 of the first byte set). */
    fun isLocallyAdministered(bssid: String): Boolean {
        val first = bssid.take(2).toIntOrNull(16) ?: return false
        return first and 0x02 != 0
    }

    private val noise = Regex(
        "(?i)[ ,]+(co\\.?|ltd\\.?|limited|inc\\.?|corp\\.?|corporation|llc|gmbh|technologies|technology|" +
            "electronics|communications|communication|systems|international|industrial|company|holdings|group|pte|bhd|sdn)$"
    )

    /** Company name for the BSSID, "" when unknown. */
    fun vendor(bssid: String): String {
        val m = map ?: return ""
        val hex = bssid.replace(":", "").uppercase()
        if (hex.length < 6) return ""
        // clear the "locally administered" bit: the real maker's prefix is hiding behind it
        val first = hex.substring(0, 2).toInt(16) and 0x02.inv()
        val key = "%02X".format(first) + hex.substring(2, 6)
        var name = m[key] ?: return ""
        while (true) {
            val n = name.replace(noise, "")
            if (n == name) break
            name = n
        }
        return name.trim().take(26)
    }

    private val routerKeys = listOf(
        "tp-link", "tp link", "d-link", "netgear", "tenda", "mikrotik", "routerboard", "ubiquiti", "cisco", "linksys",
        "mercusys", "netis", "totolink", "ruijie", "aruba", "ruckus", "cambium", "fiberhome", "zyxel", "belkin",
        "buffalo", "draytek", "engenius", "edimax", "comtrend", "sagemcom", "technicolor", "arris", "actiontec",
        "askey", "sercomm", "juniper", "extreme networks", "meraki", "fortinet", "peplink", "teltonika", "cudy",
        "keenetic", "h3c", "gl technologies", "gl.inet", "tplink", "dasan", "calix", "adtran", "casa systems"
    )

    private val phoneKeys = listOf(
        "samsung", "apple", "xiaomi", "oppo", "vivo", "realme", "oneplus", "google", "motorola", "transsion",
        "tecno", "infinix", "itel", "honor", "lg electronics", "sony", "htc", "nokia", "meizu", "fairphone",
        "hmd", "nothing", "bbk", "lenovo mobile", "zte"
    )

    /** "ROUTER", "PHONE" or "" */
    fun brandType(vendor: String): String {
        val v = vendor.lowercase()
        if (v.isEmpty()) return ""
        if (routerKeys.any { v.contains(it) }) return "ROUTER"
        if (phoneKeys.any { v.contains(it) }) return "PHONE"
        return ""
    }
}
