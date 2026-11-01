package com.mux.android.wifimap.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.core.content.ContextCompat

fun hasPerm(ctx: Context, p: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

fun toast(ctx: Context, msg: String) {
    Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show()
}

fun copyText(ctx: Context, text: String, label: String = "text") {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    toast(ctx, "COPIED")
}

fun openAppSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
    }
}

fun openSettingsAction(ctx: Context, action: String) {
    try {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        toast(ctx, "OPEN ANDROID SETTINGS MANUALLY")
    }
}
