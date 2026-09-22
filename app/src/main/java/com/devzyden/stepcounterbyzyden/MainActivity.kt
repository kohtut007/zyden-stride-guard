package com.devzyden.stepcounterbyzyden

import android.Manifest
import android.content.res.ColorStateList
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
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
import com.devzyden.stepcounterbyzyden.data.StepRepository
import com.devzyden.stepcounterbyzyden.data.local.DatabaseProvider
import com.devzyden.stepcounterbyzyden.databinding.ActivityMainBinding
import com.devzyden.stepcounterbyzyden.service.TrackingService
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isSystemTrackingActive = false

    private lateinit var stepRepository: StepRepository

    private val optimizationPreferences by lazy {
        getSharedPreferences("zyden_app_preferences", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = DatabaseProvider.getDatabase(applicationContext)
        stepRepository = StepRepository(database.stepDao())

        refreshLocalizedStaticViews()

        evaluateSystemPermissions()
        checkDeviceSpecificOptimizations()

        attachReactiveStreamObservers()
        registerInterfaceListeners()
        recoverTrackingIfNeeded()
    }
    // 1. refreshLocalizedStaticViews() နေရာတွင် Metric Label များကိုပါ Localization ချိတ်ဆက်ခြင်း
    private fun refreshLocalizedStaticViews() {
        binding.tvTitle.text = getString(R.string.app_title)

        // UI စာသားများကို ဘာသာစကားအလိုက် Live သတ်မှတ်ပေးခြင်း
        // (အကယ်၍ XML ထဲတွင် ရိုးရိုးရေးထားသော Label များအား Dynamic ချိန်းချင်ပါက သုံးနိုင်သည်)
        if (isSystemTrackingActive) {
            binding.btnToggleService.text = getString(R.string.btn_stop)
        } else {
            binding.btnToggleService.text = getString(R.string.btn_start)
        }
    }

    private fun recoverTrackingIfNeeded() {
        val trackingEnabled = optimizationPreferences.getBoolean("tracking_enabled", false)
        if (!trackingEnabled) return

        if (TrackingService.isEngineActiveStream.value || isSystemTrackingActive) return
        if (!hasRequiredTrackingPermissions()) return

        val intent = Intent(this, TrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun hasRequiredTrackingPermissions(): Boolean {
        val hasActivityRecognition =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED

        val hasLocation =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        return hasActivityRecognition && hasLocation
    }

    private fun registerInterfaceListeners() {
        binding.btnToggleService.setOnClickListener {
            val intentToken = Intent(this, TrackingService::class.java)
            if (isSystemTrackingActive) {
                optimizationPreferences.edit()
                    .putBoolean("tracking_enabled", false)
                    .remove("tracking_session_id")
                    .apply()
                stopService(intentToken)
            } else if (!hasRequiredTrackingPermissions()) {
                evaluateSystemPermissions()
                return@setOnClickListener
            } else {
                val trackingSessionId = UUID.randomUUID().toString()
                optimizationPreferences.edit()
                    .putBoolean("tracking_enabled", true)
                    .putString("tracking_session_id", trackingSessionId)
                    .apply()
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

    // 2. attachReactiveStreamObservers() နေရာကို ဤတွက်ချက်မှုစနစ်အသစ်ဖြင့် လဲလှယ်လိုက်ပါ
    private fun attachReactiveStreamObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // 💡 NEW INFRASTRUCTURE: ခြေလှမ်းတက်လာသည်နှင့် ကီလိုမီတာနှင့် ကယ်လိုရီအား စက္ကန့်မလပ် Live တွက်ချက်ပြသခြင်း
                launch {
                    stepRepository.observeCurrentMonthSteps().collect { activeSteps ->
                        binding.tvStepDisplay.text = activeSteps.toString()

                        val computedDistanceKm = activeSteps * 0.000762
                        val computedCaloriesKcal = (activeSteps * 0.04).toInt()

                        binding.tvDistanceDisplay.text =
                            String.format(java.util.Locale.US, "%.2f", computedDistanceKm)

                        binding.tvCaloriesDisplay.text =
                            computedCaloriesKcal.toString()
                    }
                }

                launch {
                    TrackingService.isEngineActiveStream.collect { isActive ->
                        isSystemTrackingActive = isActive
                        if (isActive) {
                            binding.btnToggleService.text = getString(R.string.btn_stop)
                            binding.btnToggleService.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.status_stop))
                        } else {
                            binding.btnToggleService.text = getString(R.string.btn_start)
                            binding.btnToggleService.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.button_primary))
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
        // 2. Location Tracking: ask only when neither precise nor approximate location is granted.
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

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
        if (optimizationPreferences.getBoolean("oem_optimization_setup_dismissed", false)) {
            return
        }

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
                        optimizationPreferences.edit()
                            .putBoolean("oem_optimization_setup_dismissed", true)
                            .apply()
                        onStepTwo()
                        d2.dismiss()
                    }.show()
            }.setNegativeButton("Ignore") { dialog, _ ->
                optimizationPreferences.edit()
                    .putBoolean("oem_optimization_setup_dismissed", true)
                    .apply()
                dialog.dismiss()
            }.show()
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