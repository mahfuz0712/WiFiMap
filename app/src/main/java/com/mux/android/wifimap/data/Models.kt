package com.mux.android.wifimap.data

/** One Wi-Fi access point seen in a scan. */
data class Net(
    val bssid: String,            // aa:bb:cc:dd:ee:ff (lower case)
    val ssid: String,             // "" = hidden
    val caps: String,
    val level: Int,               // dBm
    val freq: Int,
    val width: Int,               // MHz, 0 = unknown
    val security: String,         // OPEN, OWE, WEP, WPA, WPA2, WPA3, WPA/WPA2, WPA2/WPA3, ENTERPRISE
    val wps: Boolean,
    val band: String,             // 2.4G, 5G, 6G
    val channel: Int,
    val standard: String,         // "Wi-Fi 5" or ""
    val kind: String,             // ROUTER or HOTSPOT (estimated)
    val brand: String,            // vendor name from the MAC address, "" = unknown
    val connected: Boolean
) {
    val hidden: Boolean get() = ssid.isEmpty()
    val title: String get() = if (ssid.isEmpty()) "<HIDDEN NETWORK>" else ssid

    /** Networks that use a shared password (WPA/WEP personal). */
    val needsPassword: Boolean get() = security == "WEP" || security.startsWith("WPA")

    /** Personal phone hotspots and enterprise logins must never be shared. */
    val canShare: Boolean get() = needsPassword && security != "ENTERPRISE" && kind != "HOTSPOT" && ssid.isNotEmpty()

    val signalPercent: Int get() = (2 * (level + 100)).coerceIn(0, 100)
    val bars: Int
        get() = when {
            level >= -55 -> 4
            level >= -67 -> 3
            level >= -75 -> 2
            level >= -85 -> 1
            else -> 0
        }
}

/** Short database answer for the list. */
data class Summary(val match: String, val variants: Int, val sharers: Int)

data class CredItem(
    val id: String,
    val password: String,
    val sharers: Int,
    val up: Int,
    val down: Int,
    val updatedAt: Long,
    val match: String,           // "exact" = this access point, "device" = same router, other band / SSID slot
    val myVote: Int,
    val mine: Boolean,
    val via: String              // "qr" or "manual"
)

data class MineItem(
    val id: String,
    val ssid: String,
    val bssid: String,
    val password: String,
    val sharers: Int,
    val up: Int,
    val down: Int,
    val createdAt: Long
)

data class ShareDraft(val net: Net, val password: String = "", val via: String = "manual")
