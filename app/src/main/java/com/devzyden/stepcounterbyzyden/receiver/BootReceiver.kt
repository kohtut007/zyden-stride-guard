package com.devzyden.stepcounterbyzyden.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.devzyden.stepcounterbyzyden.service.TrackingService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val preferences =
            context.getSharedPreferences(
                "zyden_app_preferences",
                Context.MODE_PRIVATE
            )

        val trackingEnabled =
            preferences.getBoolean("tracking_enabled", false)

        if (!trackingEnabled) {
            return
        }

        val serviceIntent =
            Intent(context, TrackingService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
