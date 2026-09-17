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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.devzyden.stepcounterbyzyden.R
import com.devzyden.stepcounterbyzyden.data.StepRepository
import com.devzyden.stepcounterbyzyden.data.local.DatabaseProvider
import com.devzyden.stepcounterbyzyden.engine.StepFilterEngine
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TrackingService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var stepRepository: StepRepository

    private var wakeLock: PowerManager.WakeLock? = null

    private val filterEngine = StepFilterEngine()

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

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

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val database = DatabaseProvider.getDatabase(applicationContext)
        stepRepository = StepRepository(database.stepDao())

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ZydenTracker::WakeLockTag"
        )

        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent?.action == "ACTION_REFRESH_LANGUAGE_LIVE") {
            serviceScope.launch {
                val currentMonthlyTotal = stepRepository.getCurrentMonthSteps()

                updateNotification(
                    lastStoredSessionSteps,
                    currentMonthlyTotal
                )
            }
            return START_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                lastStoredSessionSteps,
                0
            )
        )

        serviceScope.launch {
            val currentMonthlyTotal = stepRepository.getCurrentMonthSteps()

            updateNotification(
                lastStoredSessionSteps,
                currentMonthlyTotal
            )
        }

        _isEngineActiveStream.value = true

        registerSensors()
        startLocationUpdates()

        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        serviceScope.launch {
            val currentMonthlyTotal = stepRepository.getCurrentMonthSteps()

            updateNotification(
                lastStoredSessionSteps,
                currentMonthlyTotal
            )
        }
    }

    private fun registerSensors() {
        val stepSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        stepSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_UI
            )
        }

        val accelSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        accelSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000
        )
            .setMinUpdateIntervalMillis(3000)
            .setMaxUpdateDelayMillis(10000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // Location permission may be unavailable.
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

            if (
                initialSteps == -1 ||
                totalMasterSteps < lastStoredSessionSteps
            ) {
                initialSteps = totalMasterSteps
                lastStoredSessionSteps = 0
            }

            val currentRawSteps =
                totalMasterSteps - initialSteps

            val deltaAddition =
                currentRawSteps - lastStoredSessionSteps

            val currentTime = System.currentTimeMillis()

            if (deltaAddition > 0) {
                val isValidStep =
                    filterEngine.verifyStepValidity(
                        currentTime,
                        currentX,
                        currentY,
                        currentZ
                    )

                val isNotInVehicle =
                    filterEngine.verifyUserIsNotInVehicle(
                        lastVerifiedLocation
                    )

                if (isValidStep && isNotInVehicle) {

                    try {
                        wakeLock?.acquire(1000)
                    } catch (e: Exception) {
                        // WakeLock is best-effort only.
                    }

                    lastStoredSessionSteps = currentRawSteps
                    _sessionStepsStream.value = currentRawSteps

                    serviceScope.launch {
                        stepRepository.addValidatedSteps(
                            deltaAddition
                        )

                        val currentMonthlyTotal =
                            stepRepository.getCurrentMonthSteps()

                        updateNotification(
                            lastStoredSessionSteps,
                            currentMonthlyTotal
                        )
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
        // No-op.
    }

    private fun buildNotification(
        sessionSteps: Int,
        monthlySteps: Int
    ): Notification {
        val rawContentFormat =
            getString(R.string.noti_content)

        val formattedContentText =
            String.format(
                rawContentFormat,
                monthlySteps
            )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                getString(R.string.noti_title)
            )
            .setContentText(formattedContentText)
            .setSmallIcon(
                android.R.drawable.ic_menu_compass
            )
            .setOngoing(true)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(formattedContentText)
            )
            .build()
    }

    private fun updateNotification(
        sessionSteps: Int,
        monthlySteps: Int
    ) {
        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                sessionSteps,
                monthlySteps
            )
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Step Tracker Service",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()

        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(
            locationCallback
        )

        if (wakeLock?.isHeld == true) {
            try {
                wakeLock?.release()
            } catch (e: Exception) {
                // Ignore cleanup failure.
            }
        }

        _isEngineActiveStream.value = false
        _sessionStepsStream.value = 0

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
