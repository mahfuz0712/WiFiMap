package com.mux.android.wifimap.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mux.android.wifimap.AppInfo
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Small global UI state. */
object Nav {
    var tab by mutableIntStateOf(0)
    var resumeTick by mutableIntStateOf(0)
    var permTick by mutableIntStateOf(0)
    var detail by mutableStateOf<Net?>(null)          // open network page
    var share by mutableStateOf<ShareDraft?>(null)    // open "share password" dialog
    var dataTick by mutableIntStateOf(0)              // bumped after a share / vote / delete so pages reload
}

/** Every property is Compose state AND persisted in SharedPreferences. */
object AppSettings {
    private var sp: SharedPreferences? = null
    private val registry = ArrayList<Pref<*>>()

    fun init(ctx: Context) {
        if (sp != null) return
        sp = ctx.applicationContext.getSharedPreferences("wifimap", Context.MODE_PRIVATE)
        registry.forEach { it.load() }
    }

    class Pref<T : Any>(private val key: String, private val def: T) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(def)

        init {
            registry.add(this)
        }

        @Suppress("UNCHECKED_CAST")
        fun load() {
            val s = sp ?: return
            val v: Any = when (def) {
                is Boolean -> s.getBoolean(key, def)
                is Int -> s.getInt(key, def)
                else -> s.getString(key, def as String) ?: def
            }
            state.value = v as T
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            val e = sp?.edit() ?: return
            when (value) {
                is Boolean -> e.putBoolean(key, value)
                is Int -> e.putInt(key, value)
                else -> e.putString(key, value as String)
            }
            e.apply()
        }
    }

    var amber by Pref("amber", false)
    var introSeen by Pref("introSeen", false)
    var askedPerms by Pref("askedPerms", false)

    var serverUrl by Pref("serverUrl", AppInfo.API_BASE)
    var token by Pref("token", "")
    var lookup by Pref("lookup", true)           // send BSSIDs of secured networks to the community database
    var autoScan by Pref("autoScan", true)
    var scanSeconds by Pref("scanSeconds", 30)
}
