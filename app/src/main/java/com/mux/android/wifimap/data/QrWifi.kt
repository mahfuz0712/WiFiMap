package com.mux.android.wifimap.data

/** Content of a Wi-Fi QR code:  WIFI:T:WPA;S:MyNetwork;P:secret;;  */
data class QrWifi(val ssid: String, val password: String, val type: String)

fun parseWifiQr(text: String): QrWifi? {
    val t = text.trim()
    if (!t.startsWith("WIFI:", ignoreCase = true)) return null
    val body = t.substring(5)
    val fields = HashMap<String, String>()
    val key = StringBuilder()
    val value = StringBuilder()
    var inValue = false
    var i = 0
    while (i < body.length) {
        val ch = body[i]
        when {
            ch == '\\' && i + 1 < body.length -> {
                (if (inValue) value else key).append(body[i + 1])
                i++
            }
            ch == ':' && !inValue -> inValue = true
            ch == ';' -> {
                if (key.isNotEmpty()) fields[key.toString().uppercase()] = value.toString()
                key.clear()
                value.clear()
                inValue = false
            }
            else -> (if (inValue) value else key).append(ch)
        }
        i++
    }
    if (key.isNotEmpty()) fields[key.toString().uppercase()] = value.toString()
    val ssid = fields["S"] ?: return null
    return QrWifi(ssid, fields["P"] ?: "", (fields["T"] ?: "").uppercase())
}
