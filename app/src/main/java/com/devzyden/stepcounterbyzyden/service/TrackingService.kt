package com.devzyden.stepcounterbyzyden.service

import android.app.*
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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.devzyden.stepcounterbyzyden.R
import com.devzyden.stepcounterbyzyden.data.PreferenceManager
import com.devzyden.stepcounterbyzyden.engine.StepFilterEngine
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TrackingService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var prefs: PreferenceManager
    private var wakeLock: PowerManager.WakeLock? = null
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

        // Deep Sleep ကာကွယ်ရန် WakeLock ကို စတင်ပြင်ဆင်ခြင်း
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZydenTracker::WakeLockTag")

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentMonthlyTotal = prefs.fetchMonthlySteps()

        if (intent?.action == "ACTION_REFRESH_LANGUAGE_LIVE") {
            updateNotification(lastStoredSessionSteps, currentMonthlyTotal)
            return START_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(lastStoredSessionSteps, currentMonthlyTotal))
        _isEngineActiveStream.value = true

        registerSensors()
        startLocationUpdates()

        // 💡 SELF-HEALING SAFETY: AlarmManager ဖြင့် ၁၅ မိနစ်တစ်ခါ နောက်ကွယ်မှ အတင်း Double-Check နှိုးခိုင်းခြင်း
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(this, TrackingService::class.java)
        val pendingIntent = PendingIntent.getService(
            this, 0, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + (15 * 60 * 1000),
            (15 * 60 * 1000).toLong(),
            pendingIntent
        )

        return START_STICKY
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
        // GPS Settings Option Pack
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(3000)
            .setMaxUpdateDelayMillis(10000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) { }
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

            // 💡 HARDWARE REBOOT PROTECTION SAFE-GUARD:
            // အကယ်၍ ဖုန်း Restart ကျသွားပြီး master count က လက်ရှိမှတ်ထားတာထက် ငယ်သွားပါက Baseline အား ပြန်ညှိခြင်း
            if (initialSteps == -1 || totalMasterSteps < lastStoredSessionSteps) {
                initialSteps = totalMasterSteps
                lastStoredSessionSteps = 0
            }

            val currentRawSteps = totalMasterSteps - initialSteps
            val deltaAddition = currentRawSteps - lastStoredSessionSteps
            val currentTime = System.currentTimeMillis()

            if (deltaAddition > 0) {
                // G-Force Anti-Cheat နှင့် GPS Vehicle Filter စစ်ဆေးခြင်း
                if (filterEngine.verifyStepValidity(currentTime, currentX, currentY, currentZ) &&
                    filterEngine.verifyUserIsNotInVehicle(lastVerifiedLocation)) {

                    // 💡 WAKELOCK ACTIVATION: CPU အား စက္ကန့်ပိုင်းမျှ နှိုးပြီး Storage ထဲ ဒေတာ သေချာသိမ်းဆည်းခြင်း
                    try { wakeLock?.acquire(1000) } catch (e: Exception) {}

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
            val channel = NotificationChannel(CHANNEL_ID, "Step Tracker Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (e: Exception) {}
        }
        _isEngineActiveStream.value = false
        _sessionStepsStream.value = 0
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
