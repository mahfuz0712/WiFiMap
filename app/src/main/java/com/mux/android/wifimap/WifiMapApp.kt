package com.mux.android.wifimap

import android.app.Application
import com.mux.android.wifimap.data.AppSettings
import com.mux.android.wifimap.data.Oui

class WifiMapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        Oui.preload(this)
    }
}
