package com.devzyden.stepcounterbyzyden.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.devzyden.stepcounterbyzyden.service.TrackingService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // ဖုန်း Reboot ကျသွားလျှင် ဖြစ်စေ၊ အခြား System event ဖြစ်စေ Service အား အလိုအလျောက် နောက်ကွယ်မှ ပြန်နှိုးခြင်း
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val serviceIntent = Intent(context, TrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
