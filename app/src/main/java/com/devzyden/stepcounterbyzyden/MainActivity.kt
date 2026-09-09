package com.devzyden.stepcounterbyzyden

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.devzyden.stepcounterbyzyden.databinding.ActivityMainBinding
import com.devzyden.stepcounterbyzyden.service.TrackingService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isSystemTrackingActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refreshLocalizedStaticViews()
        evaluateSystemPermissions()
        attachReactiveStreamObservers()
        registerInterfaceListeners()

        // 💡 XIAOMI BACKGROUND FIX: App စဖွင့်ချိန်တွင် ဖုန်းက Xiaomi/POCO/Redmi ဖြစ်နေပါက Dialog ပြခြင်း
        if (Build.MANUFACTURER.lowercase(java.util.Locale.ROOT).contains("xiaomi")) {
            showXiaomiPermissionDialog()
        }
    }

    // 🎯 UX BEST PRACTICE: User အား Settings သွားပြင်ရန် လမ်းညွှန်မည့် လှပသော Dialog တစ်ခုတည်ဆောက်ခြင်း
    private fun showXiaomiPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Xiaomi Optimization Setup")
            .setMessage("Xiaomi China ROM များတွင် Background အလုပ်လုပ်ရန် 'Autostart' ခွင့်ပြုချက်ပေးရန်နှင့် Battery Saver တွင် 'No Restrictions' သို့ ပြောင်းလဲပေးရန် မဖြစ်မနေ လိုအပ်ပါသည်။")
            .setCancelable(false) // User အလွယ်တကူ ကျော်မသွားနိုင်အောင် တားဆီးခြင်း
            .setPositiveButton("1. Autostart ပြင်မည်") { dialog, _ ->
                openXiaomiAutostartSettings() // ဤနေရာတွင် Function အား လှမ်းခေါ်ခြင်း
                dialog.dismiss()

                // Autostart ပြင်ပြီး ပြန်လာလျှင် ဒုတိယအဆင့် Battery ပြင်ဖို့ Dialog ထပ်နှိုးပေးခြင်း
                showXiaomiBatterySaverDialog()
            }
            .show()
    }

    private fun showXiaomiBatterySaverDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Xiaomi Optimization - Step 2")
            .setMessage("နောက်ဆုံးအဆင့်အနေဖြင့် Battery Saver ကို 'No Restrictions' (ကန့်သတ်ချက်မရှိ) သို့ ပြောင်းလဲပေးပါဦး။")
            .setCancelable(false)
            .setPositiveButton("2. Battery Option ပြင်မည်") { dialog, _ ->
                openXiaomiBatterySaverSettings() // ဤနေရာတွင် ဒုတိယ Function အား လှမ်းခေါ်ခြင်း
                dialog.dismiss()
            }
            .show()
    }

    private fun refreshLocalizedStaticViews() {
        binding.tvTitle.text = getString(R.string.app_title)
        binding.tvStepDisplay.text = "0"

        // စာသားအောက်က STEPS လိုမျိုး တခြား static UI label များရှိလျှင်လည်း ဤနေရာတွင် getString ချိတ်ဆက်နိုင်ပါသည်
        if (isSystemTrackingActive) {
            binding.btnToggleService.text = getString(R.string.btn_stop)
        } else {
            binding.btnToggleService.text = getString(R.string.btn_start)
        }
    }

    private fun registerInterfaceListeners() {
        binding.btnToggleService.setOnClickListener {
            val intentToken = Intent(this, TrackingService::class.java)
            if (isSystemTrackingActive) {
                stopService(intentToken)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intentToken)
                } else {
                    startService(intentToken)
                }
            }
        }

        // 💡 UI LIVE REFRESH TRICK: ခေါင်းစဉ်ကို ဖိနှိပ်လိုက်လျှင် ချက်ချင်းမျက်စိရှေ့တင် ပြောင်းလဲစေခြင်း
        binding.tvTitle.setOnLongClickListener {
            val currentLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            if (currentLanguage == "my") {
                changeAppLanguageDynamically("en")
                Toast.makeText(this, "Language: English", Toast.LENGTH_SHORT).show()
            } else {
                changeAppLanguageDynamically("my")
                Toast.makeText(this, "ဘာသာစကား - မြန်မာ", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun changeAppLanguageDynamically(languageTag: String) {
        val localeList = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(localeList) // စနစ်၏ Language picker အား နှိုးဆော်ခြင်း

        // 💡 SERVICE UPDATE TRICK: Service ထဲက Notification ပါ ချက်ချင်းပြောင်းလဲရန် Intent ဖြင့် သတင်းပို့ခြင်း
        if (isSystemTrackingActive) {
            val updateIntent = Intent(this, TrackingService::class.java).apply {
                action = "ACTION_REFRESH_LANGUAGE_LIVE"
            }
            startService(updateIntent)
        }

        // လက်ရှိ Activity ကို ချက်ချင်း Refresh လုပ်ပစ်ခြင်း (ကွင်းဆက်လိုက် ချက်ချင်း ပြောင်းလဲသွားစေသည်)
        refreshLocalizedStaticViews()
    }

    private fun attachReactiveStreamObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    TrackingService.sessionStepsStream.collect { activeSteps ->
                        binding.tvStepDisplay.text = activeSteps.toString()
                    }
                }
                launch {
                    TrackingService.isEngineActiveStream.collect { isActive ->
                        isSystemTrackingActive = isActive
                        if (isActive) {
                            binding.btnToggleService.text = getString(R.string.btn_stop)
                            binding.btnToggleService.setBackgroundColor(Color.parseColor("#DC2626"))
                            binding.tvStepDisplay.setTextColor(Color.parseColor("#16A34A"))
                        } else {
                            binding.btnToggleService.text = getString(R.string.btn_start)
                            binding.btnToggleService.setBackgroundColor(Color.parseColor("#2563EB"))
                            binding.tvStepDisplay.setTextColor(Color.parseColor("#64748B"))
                        }
                    }
                }
            }
        }
    }

    private fun evaluateSystemPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsNeeded.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val unauthorized = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (unauthorized.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, unauthorized.toTypedArray(), 404)
        }
    }

    // 💡 XIAOMI DEEP-LINK 1: Autostart Settings စာမျက်နှာကို တိုက်ရိုက်ပွင့်စေခြင်း
    private fun openXiaomiAutostartSettings() {
        try {
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            startActivity(intent)
            Toast.makeText(this, "Enable Autostart for StepCounter", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Xiaomi Autostart settings not found.", Toast.LENGTH_SHORT).show()
        }
    }

    // 💡 XIAOMI DEEP-LINK 2: App Battery Saver ကို "No Restrictions" စာမျက်နှာသို့ တိုက်ရိုက်ပို့ခြင်း
    private fun openXiaomiBatterySaverSettings() {
        try {
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
                )
            }
            startActivity(intent)
            Toast.makeText(this, "Set Battery Saver to 'No Restrictions'", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // ဖုန်းအချို့တွင် Component ကွဲပြားလျှင် App Details Setting သို့ Fallback လုပ်ပေးခြင်း
            val intent =
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                }
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(code: Int, list: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, list, results)
        if (results.isNotEmpty() && results.any { it == PackageManager.PERMISSION_DENIED }) {
            Toast.makeText(
                this,
                "Permissions required for reliable background tracking.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
