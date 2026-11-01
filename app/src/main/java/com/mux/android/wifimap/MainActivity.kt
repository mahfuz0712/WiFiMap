package com.mux.android.wifimap

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mux.android.wifimap.data.AppSettings
import com.mux.android.wifimap.data.Nav
import com.mux.android.wifimap.data.Scanner
import com.mux.android.wifimap.ui.WifiMapRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    Nav.share != null -> Nav.share = null
                    Nav.detail != null -> Nav.detail = null
                    Nav.tab != 0 -> Nav.tab = 0
                    else -> finish()
                }
            }
        })
        setContent { WifiMapRoot() }
    }

    override fun onStart() {
        super.onStart()
        Scanner.register(this)
    }

    override fun onStop() {
        Scanner.unregister(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        Nav.resumeTick++
    }
}
