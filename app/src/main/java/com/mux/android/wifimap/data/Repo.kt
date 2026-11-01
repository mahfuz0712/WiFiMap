package com.mux.android.wifimap.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Keeps the community-database answers for the networks in range. */
object Repo {
    var summaries by mutableStateOf<Map<String, Summary>>(emptyMap())
        private set
    var serverOk by mutableStateOf<Boolean?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var lastError by mutableStateOf("")
        private set

    private val fetchedAt = HashMap<String, Long>()

    /** Only secured networks are sent; open networks need no password so they are never looked up. */
    suspend fun refresh(nets: List<Net>, force: Boolean = false) {
        if (!AppSettings.lookup) return
        val now = System.currentTimeMillis()
        val todo = nets.filter {
            it.needsPassword && it.ssid.isNotEmpty() && (force || now - (fetchedAt[it.bssid] ?: 0L) > 120_000L)
        }
        if (todo.isEmpty()) return
        checking = true
        try {
            val merged = HashMap(summaries)
            for (chunk in todo.chunked(50)) {
                val res = Api.lookup(chunk)
                for (n in chunk) {
                    fetchedAt[n.bssid] = now
                    val s = res[n.bssid]
                    if (s != null && s.variants > 0) merged[n.bssid] = s else merged.remove(n.bssid)
                }
            }
            summaries = merged
            serverOk = true
            lastError = ""
        } catch (e: Exception) {
            serverOk = false
            lastError = e.message ?: "ERROR"
        }
        checking = false
    }

    fun invalidate(bssid: String) {
        fetchedAt.remove(bssid)
    }

    fun invalidateAll() {
        fetchedAt.clear()
    }
}
