package com.dzung.runner.locationtracker

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.Keep
import androidx.core.app.ActivityCompat
import com.dzung.runner.locationtracker.data.database.ActivityType
import com.dzung.runner.locationtracker.data.database.LocationPoint
import com.dzung.runner.locationtracker.data.database.RunSession
import com.dzung.runner.locationtracker.data.database.RunDao
import com.dzung.runner.locationtracker.data.repository.UserPreferencesRepository
import com.dzung.runner.locationtracker.service.FitnessMetricsCalculator
import com.dzung.runner.locationtracker.service.GhostRunnerManager
import com.dzung.runner.locationtracker.service.TrackingNotificationHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * A Bound and Started Service responsible for tracking user location using FusedLocationProviderClient.
 * Coordinates GPS updates, active metrics, sensor tracking, and Room database persistence.
 */
@Keep
class LocationTrackingService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "LocationTrackingService"
        const val ACTION_START_TRACKING = "com.dzung.runner.locationtracker.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.dzung.runner.locationtracker.ACTION_STOP_TRACKING"
    }

    private val binder = LocalBinder()
    private val runDao: RunDao by inject()
    private val userPreferencesRepository: UserPreferencesRepository by inject()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val _trackingState = MutableStateFlow(LocationTrackingState())
    val trackingState: StateFlow<LocationTrackingState> = _trackingState.asStateFlow()

    private lateinit var notificationManager: NotificationManager
    private lateinit var sensorManager: SensorManager
    private var pressureSensor: Sensor? = null

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught exception in LocationTrackingService coroutine scope", throwable)
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (ignored: Exception) {}
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)

    private var timerJob: Job? = null
    private var currentSessionId: Long? = null
    private var previousLocation: Location? = null
    private var accumulatedDistance: Float = 0f
    private var accumulatedCalories: Float = 0f
    private var startTimeMillis: Long = 0L
    private var currentWeightKg: Float = 70f
    private var movingTimeSeconds: Long = 0L
    private var smoothedSpeedMps: Float = 0f
    private var isCurrentlyMoving: Boolean = false
    private var lastMovingTimestampMs: Long = 0L

    @Volatile
    private var isStartingTracking: Boolean = false

    private val ghostRunnerManager = GhostRunnerManager()

    // Barometer tracking
    private var currentElevation: Float = 0f
    private var lastSlopeDistance: Float = 0f
    private var lastSlopeElevation: Float = 0f
    private var currentSlopePercentage: Float = 0f

    @Keep
    inner class LocalBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Initializing service resources")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        TrackingNotificationHelper.createNotificationChannel(this)
        setupLocationCallback()
        fetchInitialLocation()

        serviceScope.launch {
            try {
                userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                    currentWeightKg = if (prefs.weight > 0f && !prefs.weight.isNaN()) prefs.weight else 70f
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting user preferences in service", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        fetchInitialLocation()
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean = true

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> startLocationUpdates()
            ACTION_STOP_TRACKING -> stopLocationUpdates()
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        try {
            if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
                val pressure = event.values?.getOrNull(0) ?: return
                val altitude = FitnessMetricsCalculator.getAltitudeFromPressure(pressure)
                if (altitude <= 0f) return

                if (lastSlopeElevation == 0f) {
                    lastSlopeElevation = altitude
                }
                currentElevation = altitude
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing sensor change", e)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun setActivityType(type: ActivityType) {
        if (!_trackingState.value.isTracking) {
            _trackingState.update { it.copy(activityType = type) }
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                try {
                    val location = result.lastLocation ?: return
                    if (location.latitude.isNaN() || location.longitude.isNaN()) return

                    // Accuracy gate: ignore poor accuracy fixes (> 25m) for distance/speed
                    if (location.hasAccuracy() && location.accuracy > 25.0f) {
                        _trackingState.update {
                            it.copy(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracy = location.accuracy
                            )
                        }
                        return
                    }

                    // First fix initialization
                    val prevLoc = previousLocation
                    if (prevLoc == null) {
                        previousLocation = location
                        ghostRunnerManager.alignGhostRoute(location.latitude, location.longitude)?.let { pathLatLngs ->
                            _trackingState.update { it.copy(
                                ghostPathPoints = pathLatLngs,
                                ghostLatitude = pathLatLngs.firstOrNull()?.latitude,
                                ghostLongitude = pathLatLngs.firstOrNull()?.longitude
                            ) }
                        }
                        val latLng = LatLng(location.latitude, location.longitude)
                        _trackingState.update { currentState ->
                            currentState.copy(
                                isTracking = true,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracy = location.accuracy,
                                timestamp = location.time,
                                pathPoints = if (currentState.pathPoints.isEmpty()) listOf(latLng) else currentState.pathPoints,
                                errorMessage = null
                            )
                        }
                        return
                    }

                    // Distance and time delta relative to anchor (previousLocation)
                    val distFromAnchor = prevLoc.distanceTo(location)
                    if (distFromAnchor.isNaN() || distFromAnchor.isInfinite() || distFromAnchor < 0f) return
                    val timeDeltaMs = maxOf(0L, location.time - prevLoc.time)
                    val timeDeltaSeconds = timeDeltaMs / 1000f

                    // Determine instantaneous speed (hardware Doppler speed preferred)
                    val rawSpeedMps = if (location.hasSpeed()) {
                        location.speed
                    } else if (timeDeltaSeconds > 0f) {
                        distFromAnchor / timeDeltaSeconds
                    } else {
                        0f
                    }
                    val safeRawSpeed = if (rawSpeedMps.isNaN() || rawSpeedMps.isInfinite() || rawSpeedMps < 0f) 0f else rawSpeedMps

                    // Movement detection deadband
                    val isMovingCondition = (safeRawSpeed >= 0.3f && distFromAnchor >= 2.0f) || (safeRawSpeed >= 0.8f && distFromAnchor >= 1.5f)
                    val latLng = LatLng(location.latitude, location.longitude)

                    if (isMovingCondition) {
                        isCurrentlyMoving = true
                        lastMovingTimestampMs = System.currentTimeMillis()

                        accumulatedDistance += distFromAnchor
                        previousLocation = location

                        // Exponential moving average for smooth speed calculation
                        smoothedSpeedMps = if (smoothedSpeedMps <= 0.1f) {
                            safeRawSpeed
                        } else {
                            0.35f * safeRawSpeed + 0.65f * smoothedSpeedMps
                        }

                        // Calories using MET formula
                        val caloriesDelta = FitnessMetricsCalculator.calculateCaloriesDelta(
                            smoothedSpeedMps,
                            _trackingState.value.activityType,
                            currentWeightKg,
                            timeDeltaMs
                        )
                        if (caloriesDelta > 0f) {
                            accumulatedCalories += caloriesDelta
                        }

                        // Barometer slope calculation
                        val deltaDistanceForSlope = accumulatedDistance - lastSlopeDistance
                        if (deltaDistanceForSlope > 10f && currentElevation != 0f && !currentElevation.isNaN()) {
                            currentSlopePercentage = FitnessMetricsCalculator.calculateSlopePercentage(
                                currentElevation - lastSlopeElevation,
                                deltaDistanceForSlope
                            )
                            lastSlopeDistance = accumulatedDistance
                            lastSlopeElevation = currentElevation
                        }

                        // Safely insert point to DB only on real movement
                        currentSessionId?.let { sid ->
                            serviceScope.launch {
                                try {
                                    val point = LocationPoint(
                                        sessionId = sid,
                                        latitude = location.latitude,
                                        longitude = location.longitude,
                                        timestamp = location.time
                                    )
                                    runDao.insertLocationPoint(point)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error inserting location point to database", e)
                                }
                            }
                        }
                    } else {
                        if (lastMovingTimestampMs == 0L || (System.currentTimeMillis() - lastMovingTimestampMs) > 4000L) {
                            isCurrentlyMoving = false
                            smoothedSpeedMps = 0f
                        }
                    }

                    // Calculate instantaneous pace (null when stationary)
                    val currentPaceSeconds: Int? = if (isCurrentlyMoving && smoothedSpeedMps >= 0.3f) {
                        (1000f / smoothedSpeedMps).toInt().coerceIn(120, 1800)
                    } else {
                        null
                    }

                    val safeCalories = if (accumulatedCalories.isNaN() || accumulatedCalories.isInfinite()) 0 else accumulatedCalories.toInt()
                    val safeDistance = if (accumulatedDistance.isNaN() || accumulatedDistance.isInfinite()) 0f else accumulatedDistance

                    _trackingState.update { currentState ->
                        val newPath = if (isCurrentlyMoving) currentState.pathPoints + latLng else currentState.pathPoints
                        currentState.copy(
                            isTracking = true,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy,
                            timestamp = location.time,
                            distanceMeters = safeDistance,
                            caloriesBurned = safeCalories,
                            elevationMeters = if (currentElevation.isNaN() || currentElevation.isInfinite()) 0f else currentElevation,
                            slopePercentage = currentSlopePercentage,
                            pathPoints = newPath,
                            currentSpeedMps = if (isCurrentlyMoving) smoothedSpeedMps else 0f,
                            currentPaceSecondsPerKm = currentPaceSeconds,
                            isMoving = isCurrentlyMoving,
                            movingTimeSeconds = movingTimeSeconds,
                            errorMessage = null
                        )
                    }

                    serviceScope.launch {
                        try {
                            userPreferencesRepository.saveLocation(location.latitude, location.longitude)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving location to DataStore", e)
                        }
                    }

                    TrackingNotificationHelper.updateNotificationContent(this@LocationTrackingService, notificationManager, _trackingState.value)
                } catch (t: Throwable) {
                    Log.e(TAG, "Unhandled error in onLocationResult callback", t)
                    try {
                        FirebaseCrashlytics.getInstance().recordException(t)
                    } catch (ignored: Exception) {}
                }
            }
        }
    }

    fun fetchInitialLocation() {
        val hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null && !_trackingState.value.isTracking) {
                        if (!loc.latitude.isNaN() && !loc.longitude.isNaN()) {
                            _trackingState.update {
                                it.copy(latitude = loc.latitude, longitude = loc.longitude)
                            }
                            serviceScope.launch {
                                try {
                                    userPreferencesRepository.saveLocation(loc.latitude, loc.longitude)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error saving initial location", e)
                                }
                            }
                        }
                    }
                }.addOnFailureListener { e ->
                    Log.w(TAG, "Failed to get last known location", e)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Location permission missing or error fetching initial location", e)
            }
        }
    }

    fun startLocationUpdates() {
        if (_trackingState.value.isTracking || isStartingTracking) return
        isStartingTracking = true

        val hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            isStartingTracking = false
            _trackingState.update { it.copy(errorMessage = "Location permission is not granted.") }
            try {
                val notification = TrackingNotificationHelper.buildStatusNotification(
                    this,
                    _trackingState.value.activityType,
                    "Location permission required"
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(TrackingNotificationHelper.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(TrackingNotificationHelper.NOTIFICATION_ID, notification)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling start without permission", e)
            }
            stopSelf()
            return
        }

        try {
            val notification = TrackingNotificationHelper.buildStatusNotification(
                this,
                _trackingState.value.activityType,
                "Starting location tracking..."
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(TrackingNotificationHelper.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(TrackingNotificationHelper.NOTIFICATION_ID, notification)
            }

            val priority = if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val locationRequest = LocationRequest.Builder(priority, 4000L).apply {
                setMinUpdateIntervalMillis(2000L)
                setWaitForAccurateLocation(false)
            }.build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Reset state
            accumulatedDistance = 0f
            accumulatedCalories = 0f
            movingTimeSeconds = 0L
            smoothedSpeedMps = 0f
            isCurrentlyMoving = false
            lastMovingTimestampMs = 0L
            previousLocation = null
            startTimeMillis = System.currentTimeMillis()

            currentElevation = 0f
            lastSlopeDistance = 0f
            lastSlopeElevation = 0f
            currentSlopePercentage = 0f

            pressureSensor?.let {
                try {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error registering pressure sensor", e)
                }
            }

            _trackingState.update {
                it.copy(
                    isTracking = true,
                    errorMessage = null,
                    distanceMeters = 0f,
                    elapsedTimeSeconds = 0L,
                    movingTimeSeconds = 0L,
                    currentSpeedMps = 0f,
                    currentPaceSecondsPerKm = null,
                    isMoving = false,
                    caloriesBurned = 0,
                    elevationMeters = 0f,
                    slopePercentage = 0f,
                    pathPoints = emptyList()
                )
            }

            val currentActivityType = _trackingState.value.activityType
            serviceScope.launch {
                try {
                    val session = RunSession(
                        startTimeInMillis = startTimeMillis,
                        activityType = currentActivityType
                    )
                    currentSessionId = runDao.insertRunSession(session)
                } catch (e: Exception) {
                    Log.e(TAG, "Database error inserting run session", e)
                }
            }

            startTimer()
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error starting location updates or foreground service", e)
            try {
                FirebaseCrashlytics.getInstance().recordException(e)
            } catch (ignored: Exception) {}
            _trackingState.update { it.copy(isTracking = false, errorMessage = "Failed to start tracking: ${e.message}") }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (ignored: Exception) {}
            stopSelf()
        } finally {
            isStartingTracking = false
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                try {
                    delay(1000L)
                    val elapsedSeconds = if (startTimeMillis > 0L) {
                        maxOf(0L, (System.currentTimeMillis() - startTimeMillis) / 1000)
                    } else 0L

                    if (isCurrentlyMoving) {
                        if (lastMovingTimestampMs > 0L && (System.currentTimeMillis() - lastMovingTimestampMs) > 4000L) {
                            isCurrentlyMoving = false
                            smoothedSpeedMps = 0f
                        } else {
                            movingTimeSeconds++
                        }
                    }

                    val currentPace = if (isCurrentlyMoving && smoothedSpeedMps >= 0.3f) {
                        (1000f / smoothedSpeedMps).toInt().coerceIn(120, 1800)
                    } else {
                        null
                    }

                    if (ghostRunnerManager.hasGhost()) {
                        val ghostState = ghostRunnerManager.interpolateGhost(elapsedSeconds)
                        _trackingState.update { state ->
                            state.copy(
                                elapsedTimeSeconds = elapsedSeconds,
                                movingTimeSeconds = movingTimeSeconds,
                                currentSpeedMps = if (isCurrentlyMoving) smoothedSpeedMps else 0f,
                                currentPaceSecondsPerKm = currentPace,
                                isMoving = isCurrentlyMoving,
                                ghostLatitude = ghostState.latitude,
                                ghostLongitude = ghostState.longitude,
                                ghostDistanceMeters = ghostState.distanceMeters
                            )
                        }
                    } else {
                        _trackingState.update { state ->
                            state.copy(
                                elapsedTimeSeconds = elapsedSeconds,
                                movingTimeSeconds = movingTimeSeconds,
                                currentSpeedMps = if (isCurrentlyMoving) smoothedSpeedMps else 0f,
                                currentPaceSecondsPerKm = currentPace,
                                isMoving = isCurrentlyMoving
                            )
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in tracking timer loop", e)
                }
            }
        }
    }

    fun stopLocationUpdates() {
        if (!_trackingState.value.isTracking) return

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering sensor listener", e)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground status", e)
        }

        timerJob?.cancel()

        val finalState = _trackingState.value
        val sessionEndTime = if (lastMovingTimestampMs > startTimeMillis) {
            lastMovingTimestampMs
        } else {
            System.currentTimeMillis()
        }
        val finalElapsedSeconds = if (startTimeMillis > 0L && sessionEndTime > startTimeMillis) {
            (sessionEndTime - startTimeMillis) / 1000L
        } else {
            finalState.elapsedTimeSeconds
        }

        serviceScope.launch {
            try {
                currentSessionId?.let { sid ->
                    runDao.getRunSession(sid)?.let { session ->
                        val updatedSession = session.copy(
                            endTimeInMillis = sessionEndTime,
                            totalDistanceMeters = finalState.distanceMeters,
                            totalCalories = finalState.caloriesBurned
                        )
                        runDao.updateRunSession(updatedSession)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Database error updating run session", e)
            } finally {
                currentSessionId = null
            }

            withContext(Dispatchers.Main) {
                _trackingState.update {
                    it.copy(
                        isTracking = false,
                        elapsedTimeSeconds = finalElapsedSeconds,
                        errorMessage = "Tracking stopped by user"
                    )
                }
                stopSelf()
            }
        }
    }

    fun setGhostSession(sessionId: Long?) {
        if (sessionId == null) {
            ghostRunnerManager.clear()
            _trackingState.update { it.copy(
                selectedGhostSessionId = null,
                ghostLatitude = null,
                ghostLongitude = null,
                ghostDistanceMeters = 0f,
                ghostPathPoints = emptyList()
            ) }
            return
        }

        serviceScope.launch {
            val pathLatLngs = ghostRunnerManager.loadGhostSession(sessionId, runDao)
            _trackingState.update { it.copy(
                selectedGhostSessionId = sessionId,
                ghostPathPoints = pathLatLngs,
                ghostLatitude = pathLatLngs.firstOrNull()?.latitude,
                ghostLongitude = pathLatLngs.firstOrNull()?.longitude,
                ghostDistanceMeters = 0f
            ) }
        }
    }

    override fun onDestroy() {
        try {
            timerJob?.cancel()
            fusedLocationClient.removeLocationUpdates(locationCallback)
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error during service onDestroy cleanup", e)
        }
        super.onDestroy()
    }
}
