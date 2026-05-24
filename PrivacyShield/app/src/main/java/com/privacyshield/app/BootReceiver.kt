package com.privacyshield.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") {

            val prefs = context.getSharedPreferences("privacy_shield_prefs", Context.MODE_PRIVATE)
            val wasEnabled = prefs.getBoolean("enabled", false)

            if (wasEnabled && Settings.canDrawOverlays(context)) {
                val serviceIntent = Intent(context, PrivacyService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}