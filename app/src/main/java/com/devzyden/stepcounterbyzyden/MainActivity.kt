package com.devzyden.stepcounterbyzyden

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isSystemTrackingActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refreshLocalizedStaticViews()

        // 💡 ALL-DEVICE COMPATIBLE: Permissions နှင့် OEM Optimization များကို အဆင့်ဆင့်စစ်ဆေးခြင်း
        evaluateSystemPermissions()
        checkDeviceSpecificOptimizations()

        attachReactiveStreamObservers()
        registerInterfaceListeners()
    }

    private fun refreshLocalizedStaticViews() {
        binding.tvTitle.text = getString(R.string.app_title)
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

        binding.tvTitle.setOnLongClickListener {
            val currentLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            if (currentLanguage == "my") {
                changeAppLanguageDynamically("en")
            } else {
                changeAppLanguageDynamically("my")
            }
            true
        }
    }

    private fun changeAppLanguageDynamically(languageTag: String) {
        val localeList = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(localeList)
        if (isSystemTrackingActive) {
            val updateIntent = Intent(this, TrackingService::class.java).apply {
                action = "ACTION_REFRESH_LANGUAGE_LIVE"
            }
            startService(updateIntent)
        }
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

    // 💡 SENIOR SYSTEM REFACTOR: Android Versions အားလုံးအတွက် လုံခြုံစိတ်ချရသော ခွင့်ပြုချက်တောင်းခံစနစ်
    private fun evaluateSystemPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        // 1. Physical Activity Recognition (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsNeeded.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // 2. Location Tracking (Vehicle motion အတွက် ဗားရှင်းအားလုံး လိုအပ်သည်)
        permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // 3. Post Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // မရရှိသေးသော ခွင့်ပြုချက်များကိုသာ စစ်ထုတ်ခြင်း
        val unauthorizedRequests = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (unauthorizedRequests.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, unauthorizedRequests.toTypedArray(), 404)
        }
    }

    override fun onRequestPermissionsResult(code: Int, list: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, list, results)
        if (code == 404 && results.isNotEmpty()) {
            val hasDeniedPermission = results.any { it == PackageManager.PERMISSION_DENIED }
            if (hasDeniedPermission) {
                Toast.makeText(
                    this,
                    "နောက်ကွယ်မှ တိကျစွာအလုပ်လုပ်ရန် ခွင့်ပြုချက်အားလုံး လိုအပ်ပါသည်။",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // 💡 GLOBAL OEM OPTIMIZATION ENGINE: တရုတ်ဖုန်းလောကတစ်ခုလုံးကို လမ်းညွှန်ပေးမည့် စနစ်
    private fun checkDeviceSpecificOptimizations() {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)

        // OnePlus နှင့် Nothing တို့သည် Stock Android ဆန်သဖြင့် System Battery Optimization သို့ တိုက်ရိုက်လွှတ်နိုင်သည်
        // Xiaomi, Oppo, Vivo, Realme, Huawei တို့သည် သီးသန့် Settings များ လိုအပ်သည်
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains(
                "poco"
            )
        ) {
            showDeviceOptimizationDialog(
                "Xiaomi/Redmi Optimization Setup",
                "နောက်ကွယ်မှ ခြေလှမ်းများ မပြတ်တောက်စေရန် 'Autostart' ခွင့်ပြုချက်ပေးရန်နှင့် Battery Saver တွင် 'No Restrictions' သို့ ပြောင်းလဲပေးရန် လိုအပ်ပါသည်။",
                { openXiaomiAutostartSettings() },
                { openGenericBatteryOptimizationSettings() })
        } else if (manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains(
                "oneplus"
            )
        ) {
            showDeviceOptimizationDialog(
                "Oppo/Realme/OnePlus Setup",
                "နောက်ကွယ်တွင် တိတ်တဆိတ် အမြဲအလုပ်လုပ်နိုင်ရန် 'Allow Background Activity' နှင့် 'Auto-launch' ကို ဖွင့်လှစ်ပေးပါဦး။",
                { openOppoRealmeManagement() },
                { openGenericBatteryOptimizationSettings() })
        } else if (manufacturer.contains("vivo")) {
            showDeviceOptimizationDialog(
                "Vivo Optimization Setup",
                "Vivo ဖုန်းများ၏ Battery Management တွင် 'High Background Power Consumption' ကို ခွင့်ပြုပေးရန် လိုအပ်ပါသည်။",
                { openVivoManagement() },
                { openGenericBatteryOptimizationSettings() })
        }
    }

    private fun showDeviceOptimizationDialog(
        title: String, message: String, onStepOne: () -> Unit, onStepTwo: () -> Unit
    ) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setCancelable(true)
            .setPositiveButton("1. App Launch Setup") { dialog, _ ->
                onStepOne()
                dialog.dismiss()

                // ဒုတိယအဆင့် Battery Saver နေရာသို့ ထပ်မံညွှန်ကြားခြင်း
                AlertDialog.Builder(this).setTitle("Step 2: Battery Optimization")
                    .setMessage("ဒုတိယအဆင့်အနေဖြင့် App အား Battery Unrestricted (ကန့်သတ်ချက်မရှိ) ပြောင်းလဲပေးပါ။")
                    .setPositiveButton("2. Fix Battery") { d2, _ ->
                        onStepTwo()
                        d2.dismiss()
                    }.show()
            }.setNegativeButton("Ignore", null).show()
    }

    // --- OEM Specific Hidden Components Intent Calls ---

    private fun openXiaomiAutostartSettings() {
        try {
            startActivity(Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            })
        } catch (e: Exception) {
            openAppDetailsSettings()
        }
    }

    private fun openOppoRealmeManagement() {
        try {
            startActivity(Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                )
            })
        } catch (e: Exception) {
            try {
                startActivity(Intent().apply {
                    component = ComponentName(
                        "com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"
                    )
                })
            } catch (e2: Exception) {
                openAppDetailsSettings()
            }
        }
    }

    private fun openVivoManagement() {
        try {
            startActivity(Intent().apply {
                component = ComponentName(
                    "com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                )
            })
        } catch (e: Exception) {
            try {
                startActivity(Intent().apply {
                    component = ComponentName(
                        "com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.BgStartUpManager"
                    )
                })
            } catch (e2: Exception) {
                openAppDetailsSettings()
            }
        }
    }

    private fun openGenericBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (e2: Exception) {
                    openAppDetailsSettings()
                }
            }
        } else {
            openAppDetailsSettings()
        }
    }

    private fun openAppDetailsSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }
}