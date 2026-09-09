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

        // UI စတင်ပွင့်ချိန်တွင် လက်ရှိရွေးချယ်ထားသော ဘာသာစကားအတိုင်း UI စာသားများကို သတ်မှတ်ပေးခြင်း
        refreshLocalizedStaticViews()

        evaluateSystemPermissions()
        attachReactiveStreamObservers()
        registerInterfaceListeners()
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

    override fun onRequestPermissionsResult(code: Int, list: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, list, results)
        if (results.isNotEmpty() && results.any { it == PackageManager.PERMISSION_DENIED }) {
            Toast.makeText(this, "Permissions required for reliable background tracking.", Toast.LENGTH_LONG).show()
        }
    }
}
