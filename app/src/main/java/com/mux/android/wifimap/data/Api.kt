package com.mux.android.wifimap.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Talks to the community database server (see the WiFiMap-Server project). */
object Api {
    class ApiException(val code: Int, message: String) : Exception(message)

    class Stats(val networks: Int, val shares: Int, val users: Int)

    private fun url(path: String) = AppSettings.serverUrl.trim().trimEnd('/') + "/api/v1" + path

    private fun raw(method: String, path: String, body: JSONObject?, auth: Boolean): JSONObject {
        val c = URL(url(path)).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 20_000
            c.readTimeout = 60_000          // free hosting may need a minute to wake up
            c.setRequestProperty("Accept", "application/json")
            if (auth) c.setRequestProperty("Authorization", "Bearer " + AppSettings.token)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try {
                    JSONObject(text).optString("error")
                } catch (e: Exception) {
                    ""
                }
                throw ApiException(code, msg.ifEmpty { "SERVER ERROR $code" })
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            c.disconnect()
        }
    }

    private fun ensureToken() {
        if (AppSettings.token.isBlank()) {
            val r = raw("POST", "/auth/register", JSONObject(), false)
            AppSettings.token = r.getString("token")
        }
    }

    private fun call(method: String, path: String, body: JSONObject? = null): JSONObject {
        ensureToken()
        try {
            return raw(method, path, body, true)
        } catch (e: ApiException) {
            if (e.code != 401) throw e
            AppSettings.token = ""          // token expired / server reset: sign in again once
            ensureToken()
            return raw(method, path, body, true)
        }
    }

    private fun netJson(n: Net) = JSONObject().put("bssid", n.bssid).put("ssid", n.ssid)

    private fun parseCreds(a: JSONArray?): List<CredItem> {
        val out = ArrayList<CredItem>()
        if (a == null) return out
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(
                CredItem(
                    id = o.getString("id"),
                    password = o.getString("password"),
                    sharers = o.optInt("sharers"),
                    up = o.optInt("up"),
                    down = o.optInt("down"),
                    updatedAt = o.optLong("updatedAt"),
                    match = o.optString("match", "exact"),
                    myVote = o.optInt("myVote"),
                    mine = o.optBoolean("mine"),
                    via = o.optString("via", "manual")
                )
            )
        }
        return out
    }

    suspend fun stats(): Stats = withContext(Dispatchers.IO) {
        val o = raw("GET", "/stats", null, false)
        Stats(o.optInt("networks"), o.optInt("shares"), o.optInt("users"))
    }

    /** Which of these access points already have shared passwords? */
    suspend fun lookup(nets: List<Net>): Map<String, Summary> = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        nets.forEach { arr.put(netJson(it)) }
        val res = call("POST", "/wifi/lookup", JSONObject().put("networks", arr)).optJSONObject("results")
        val out = HashMap<String, Summary>()
        if (res != null) {
            for (k in res.keys()) {
                val o = res.getJSONObject(k)
                out[k] = Summary(o.optString("match", "exact"), o.optInt("variants"), o.optInt("sharers"))
            }
        }
        out
    }

    suspend fun detail(n: Net): List<CredItem> = withContext(Dispatchers.IO) {
        parseCreds(call("POST", "/wifi/network", netJson(n)).optJSONArray("credentials"))
    }

    suspend fun share(n: Net, password: String, via: String): List<CredItem> = withContext(Dispatchers.IO) {
        val body = netJson(n)
            .put("password", password)
            .put("security", n.security)
            .put("kind", n.kind)
            .put("band", n.band)
            .put("channel", n.channel)
            .put("brand", n.brand)
            .put("via", via)
            .put("confirm", true)
        parseCreds(call("POST", "/wifi/share", body).optJSONArray("credentials"))
    }

    suspend fun vote(id: String, value: Int) = withContext(Dispatchers.IO) {
        call("POST", "/wifi/credentials/$id/vote", JSONObject().put("value", value))
        Unit
    }

    suspend fun report(id: String, reason: String) = withContext(Dispatchers.IO) {
        call("POST", "/wifi/credentials/$id/report", JSONObject().put("reason", reason))
        Unit
    }

    suspend fun mine(): List<MineItem> = withContext(Dispatchers.IO) {
        val a = call("GET", "/wifi/mine").optJSONArray("items")
        val out = ArrayList<MineItem>()
        if (a != null) {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(
                    MineItem(
                        o.getString("id"), o.optString("ssid"), o.optString("bssid"), o.optString("password"),
                        o.optInt("sharers"), o.optInt("up"), o.optInt("down"), o.optLong("createdAt")
                    )
                )
            }
        }
        out
    }

    suspend fun deleteMine(id: String) = withContext(Dispatchers.IO) {
        call("DELETE", "/wifi/credentials/$id/mine")
        Unit
    }
}
