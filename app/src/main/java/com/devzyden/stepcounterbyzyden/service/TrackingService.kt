package com.devzyden.stepcounterbyzyden.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.devzyden.stepcounterbyzyden.R
import com.devzyden.stepcounterbyzyden.data.PreferenceManager
import com.devzyden.stepcounterbyzyden.engine.StepFilterEngine
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TrackingService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var prefs: PreferenceManager
    private val filterEngine = StepFilterEngine()

    private var lastVerifiedLocation: Location? = null
    private var initialSteps = -1
    private var lastStoredSessionSteps = 0

    private var currentX = 0f
    private var currentY = 0f
    private var currentZ = 0f

    companion object {
        private val _sessionStepsStream = MutableStateFlow(0)
        val sessionStepsStream: StateFlow<Int> = _sessionStepsStream

        private val _isEngineActiveStream = MutableStateFlow(false)
        val isEngineActiveStream: StateFlow<Boolean> = _isEngineActiveStream

        private const val CHANNEL_ID = "step_counter_channel"
        private const val NOTIFICATION_ID = 101
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        prefs = PreferenceManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentMonthlyTotal = prefs.fetchMonthlySteps()

        if (intent?.action == "ACTION_REFRESH_LANGUAGE_LIVE") {
            updateNotification(lastStoredSessionSteps, currentMonthlyTotal)
            // Service သေသွားခဲ့လျှင် OS က ချက်ချင်းပြန်မနှိုးပေးနိုင်တဲ့ အခြေအနေမျိုး (ဥပမာ Deep Doze Mode) ရှိက
            // AlarmManager ကို သုံးပြီး ၁၅ မိနစ်တစ်ခါ Service ကို အတင်းနောက်ကွယ်ကနေ Double-Check ပြန်နှိုးခိုင်းထားခြင်း
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val alarmIntent = Intent(this, TrackingService::class.java)
            val pendingIntent = android.app.PendingIntent.getService(
                this, 0, alarmIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            // ၁၅ မိနစ်တစ်ခါ ပုံမှန်နှိုးဆော်ရန် သတ်မှတ်ခြင်း
            alarmManager.setInexactRepeating(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + (15 * 60 * 1000),
                (15 * 60 * 1000).toLong(),
                pendingIntent
            )

            return START_STICKY
        }

        // 💡 XIAOMI PERSISTENCE TRICK: Service ကို Foreground အဖြစ် ပြင်းပြင်းထန်ထန် သတ်မှတ်ထားခြင်း
        startForeground(
            NOTIFICATION_ID,
            buildNotification(lastStoredSessionSteps, currentMonthlyTotal)
        )
        _isEngineActiveStream.value = true

        registerSensors()
        startLocationUpdates()

        return START_STICKY // System က သတ်လျှင်တောင် OS က ချက်ချင်း ပြန်နှိုးပေးမည်
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val currentMonthlyTotal = prefs.fetchMonthlySteps()
        updateNotification(lastStoredSessionSteps, currentMonthlyTotal)
    }

    private fun registerSensors() {
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun startLocationUpdates() {
        // 💡 ZERO BATTERY DRAIN: HIGH_ACCURACY စနစ်သုံးသော်လည်း Interval ကို အဆင်ပြေအောင် ညှိပြီး
        // MaxWaitTime ကို ပါသုံးထားခြင်းက ဘက်ထရီ စားသုံးမှုကို သိသိသာသာ လျှော့ချပေးသည်
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(3000)
            .setMaxUpdateDelayMillis(10000) // Location updates များကို စုပြီးမှ ပို့ရန် (Battery Saver)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            lastVerifiedLocation = result.lastLocation
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            currentX = event.values[0]
            currentY = event.values[1]
            currentZ = event.values[2]
        }
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalMasterSteps = event.values[0].toInt()
            if (initialSteps == -1) {
                initialSteps = totalMasterSteps
            }
            val currentRawSteps = totalMasterSteps - initialSteps
            val deltaAddition = currentRawSteps - lastStoredSessionSteps
            val currentTime = System.currentTimeMillis()

            if (deltaAddition > 0) {
                // Anti-Cheat (G-Force) နှင့် Enhanced Vehicle Filter ကို ဖြတ်သန်းစစ်ဆေးခြင်း
                if (filterEngine.verifyStepValidity(currentTime, currentX, currentY, currentZ) &&
                    filterEngine.verifyUserIsNotInVehicle(lastVerifiedLocation)
                ) {

                    lastStoredSessionSteps = currentRawSteps
                    _sessionStepsStream.value = currentRawSteps

                    prefs.saveValidatedSteps(deltaAddition)
                    val updatedMonthlyTotal = prefs.fetchMonthlySteps()

                    updateNotification(currentRawSteps, updatedMonthlyTotal)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun buildNotification(sessionSteps: Int, monthlySteps: Int): Notification {
        val rawContentFormat = getString(R.string.noti_content)
        val formattedContentText = String.format(rawContentFormat, monthlySteps)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.noti_title))
            .setContentText(formattedContentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(formattedContentText))
            .build()
    }

    private fun updateNotification(sessionSteps: Int, monthlySteps: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(sessionSteps, monthlySteps))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Step Tracker Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _isEngineActiveStream.value = false
        _sessionStepsStream.value = 0
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
