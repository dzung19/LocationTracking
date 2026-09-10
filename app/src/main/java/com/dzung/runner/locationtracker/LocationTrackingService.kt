package com.dzung.runner.locationtracker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import androidx.core.app.NotificationCompat
import com.dzung.runner.locationtracker.data.database.ActivityType
import com.dzung.runner.locationtracker.data.database.AppDatabase
import com.dzung.runner.locationtracker.data.database.LocationPoint
import com.dzung.runner.locationtracker.data.database.RunSession
import com.dzung.runner.locationtracker.data.database.RunDao
import com.dzung.runner.locationtracker.data.repository.UserPreferencesRepository
import com.google.android.gms.location.FusedLocationProviderClient
import org.koin.android.ext.android.inject
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

/**
 * A Bound and Started Service responsible for tracking user location using FusedLocationProviderClient.
 * It elevates itself to a Foreground Service while tracking is active to prevent the system
 * from reclaiming its resources, and exposes location updates in real-time through a Kotlin StateFlow.
 */
@Keep
class LocationTrackingService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "LocationTrackingService"
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 101

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

    // Handler to catch and log any unhandled background coroutine exceptions, preventing app crash
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
    
    // Guard against simultaneous or re-entrant start calls
    @Volatile
    private var isStartingTracking: Boolean = false

    // Ghost Runner data
    private var ghostPoints = listOf<GhostPoint>()
    
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
        
        createNotificationChannel()
        setupLocationCallback()
        fetchInitialLocation()
        
        // Observe weight changes from DataStore safely
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

    override fun onUnbind(intent: Intent?): Boolean {
        return true
    }

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
                if (pressure <= 0f || pressure.isNaN()) return
                
                val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
                if (altitude.isNaN() || altitude.isInfinite()) return
                
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

    /**
     * Called by ViewModel before starting to set the activity type
     */
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
                        alignGhostRoute(location.latitude, location.longitude)
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

                    // Movement detection:
                    // 1) User has moved >= 2.0m from anchor with speed >= 0.3 m/s (~1.0 km/h)
                    // 2) OR speed >= 0.8 m/s with displacement >= 1.5m (faster pace with short fix interval)
                    val isMovingCondition = (safeRawSpeed >= 0.3f && distFromAnchor >= 2.0f) || (safeRawSpeed >= 0.8f && distFromAnchor >= 1.5f)

                    val latLng = LatLng(location.latitude, location.longitude)

                    if (isMovingCondition) {
                        isCurrentlyMoving = true
                        lastMovingTimestampMs = System.currentTimeMillis()

                        // Accumulate true distance and advance anchor
                        accumulatedDistance += distFromAnchor
                        previousLocation = location

                        // Exponential moving average for smooth pace calculation
                        smoothedSpeedMps = if (smoothedSpeedMps <= 0.1f) {
                            safeRawSpeed
                        } else {
                            0.35f * safeRawSpeed + 0.65f * smoothedSpeedMps
                        }

                        // Calculate Calories using MET formula during movement
                        val timeDeltaMinutes = timeDeltaMs / 60000f
                        val speedKmh = smoothedSpeedMps * 3.6f
                        val currentActivityType = _trackingState.value.activityType
                        val met = getMET(speedKmh, currentActivityType)
                        val weightKg = if (currentWeightKg > 0f && !currentWeightKg.isNaN()) currentWeightKg else 70f
                        val caloriesDelta = (met * weightKg * 3.5f / 200f) * timeDeltaMinutes
                        if (!caloriesDelta.isNaN() && !caloriesDelta.isInfinite() && caloriesDelta > 0f) {
                            accumulatedCalories += caloriesDelta
                        }

                        // Slope calculation
                        val deltaDistanceForSlope = accumulatedDistance - lastSlopeDistance
                        if (deltaDistanceForSlope > 10f && currentElevation != 0f && !currentElevation.isNaN()) {
                            val deltaElevation = currentElevation - lastSlopeElevation
                            val slope = (deltaElevation / deltaDistanceForSlope) * 100f
                            currentSlopePercentage = if (slope.isNaN() || slope.isInfinite()) 0f else slope

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
                        // User is stationary or GPS jitter within anchor deadband
                        // Anchor (previousLocation) remains fixed so small jitters never integrate!
                        if (lastMovingTimestampMs == 0L || (System.currentTimeMillis() - lastMovingTimestampMs) > 4000L) {
                            isCurrentlyMoving = false
                            smoothedSpeedMps = 0f
                        }
                    }

                    // Calculate instantaneous pace (seconds per km). Null when stationary/not moving.
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

                    // Safely persist current location to DataStore
                    serviceScope.launch {
                        try {
                            userPreferencesRepository.saveLocation(location.latitude, location.longitude)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving location to DataStore", e)
                        }
                    }

                    updateNotificationContent(location)
                } catch (t: Throwable) {
                    Log.e(TAG, "Unhandled error in onLocationResult callback", t)
                    try {
                        FirebaseCrashlytics.getInstance().recordException(t)
                    } catch (ignored: Exception) {}
                }
            }
        }
    }

    /**
     * One-time location query on startup/bind to initialize coordinates before user presses Start
     */
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
            // To prevent ForegroundServiceDidNotStartInTimeException on Android 8+ when called via startForegroundService,
            // post a brief notification, remove it, and immediately stopSelf.
            try {
                val notification = buildStatusNotification("Location permission required")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
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
            val notification = buildStatusNotification("Starting location tracking...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
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
            
            // Start Timer and DB Session
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
            // Catches ForegroundServiceStartNotAllowedException, SecurityException, IllegalStateException, etc.
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

    private fun getMET(speedKmh: Float, activityType: ActivityType): Float {
        // If speed is very low, treat as standing still / resting
        if (speedKmh < 1.0f) return 1.3f // Approximate resting MET
        
        return if (activityType == ActivityType.WALKING) {
            when {
                speedKmh < 3.2f -> 2.0f
                speedKmh < 4.0f -> 3.0f
                speedKmh < 4.8f -> 3.3f
                speedKmh < 5.6f -> 3.8f
                speedKmh < 6.4f -> 4.3f
                speedKmh < 7.2f -> 5.0f
                else -> 6.0f // brisk walking
            }
        } else { // RUNNING
            when {
                speedKmh < 6.4f -> 5.0f // slow jog
                speedKmh < 8.0f -> 6.0f
                speedKmh < 9.7f -> 8.3f
                speedKmh < 11.3f -> 9.8f
                speedKmh < 12.9f -> 11.0f
                speedKmh < 14.5f -> 11.8f
                speedKmh < 16.1f -> 12.8f
                else -> 14.5f // fast running
            }
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

                    // Check timeout for stationary transition
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
                    
                    if (ghostPoints.isNotEmpty()) {
                        val ghostState = interpolateGhost(elapsedSeconds, ghostPoints)
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
        serviceScope.launch {
            try {
                currentSessionId?.let { sid ->
                    runDao.getRunSession(sid)?.let { session ->
                        val updatedSession = session.copy(
                            endTimeInMillis = System.currentTimeMillis(),
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

            // Shift state updates and stopSelf to Main dispatcher only after the DB write has succeeded
            withContext(Dispatchers.Main) {
                _trackingState.update {
                    it.copy(
                        isTracking = false,
                        errorMessage = "Tracking stopped by user"
                    )
                }
                stopSelf()
            }
        }
    }

    private fun updateNotificationContent(location: Location) {
        try {
            val state = _trackingState.value
            val distanceKm = state.distanceMeters / 1000f
            val paceDisplay = state.currentPaceSecondsPerKm?.let { paceSec ->
                "%d:%02d/km".format(paceSec / 60, paceSec % 60)
            } ?: "--:--"
            val text = "Dist: %.2f km | Pace: %s | Time: %d s".format(distanceKm, paceDisplay, state.elapsedTimeSeconds)
            val notification = buildStatusNotification(text)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification content", e)
        }
    }

    private fun buildStatusNotification(contentText: String): Notification {
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val activityIntent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val activityPendingIntent = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (_trackingState.value.activityType == ActivityType.RUNNING) "Running..." else "Walking...")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(activityPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Location Tracking Service", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Channel displaying active GPS location tracking updates"
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun setGhostSession(sessionId: Long?) {
        if (sessionId == null) {
            ghostPoints = emptyList()
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
            try {
                val points = runDao.getLocationPointsForSessionOnce(sessionId)
                val sorted = points.sortedBy { it.timestamp }
                val ghostStartTime = sorted.firstOrNull()?.timestamp ?: 0L
                val pointsList = mutableListOf<GhostPoint>()
                var dist = 0f
                var prevPoint: LocationPoint? = null
                sorted.forEach { pt ->
                    if (!pt.latitude.isNaN() && !pt.longitude.isNaN()) {
                        prevPoint?.let { prev ->
                            val results = FloatArray(1)
                            Location.distanceBetween(prev.latitude, prev.longitude, pt.latitude, pt.longitude, results)
                            val stepDist = results[0]
                            if (!stepDist.isNaN() && stepDist >= 0f) {
                                dist += stepDist
                            }
                        }
                        val elapsed = (pt.timestamp - ghostStartTime) / 1000
                        pointsList.add(GhostPoint(LatLng(pt.latitude, pt.longitude), elapsed, dist))
                        prevPoint = pt
                    }
                }
                ghostPoints = pointsList
                
                val pathLatLngs = pointsList.map { it.latLng }
                _trackingState.update { it.copy(
                    selectedGhostSessionId = sessionId,
                    ghostPathPoints = pathLatLngs,
                    ghostLatitude = pathLatLngs.firstOrNull()?.latitude,
                    ghostLongitude = pathLatLngs.firstOrNull()?.longitude,
                    ghostDistanceMeters = 0f
                ) }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading ghost session points", e)
            }
        }
    }

    private fun interpolateGhost(elapsedSeconds: Long, ghostPoints: List<GhostPoint>): InterpolatedGhostState {
        if (ghostPoints.isEmpty()) {
            return InterpolatedGhostState(null, null, 0f)
        }
        
        if (elapsedSeconds <= ghostPoints.first().elapsedSeconds) {
            val first = ghostPoints.first()
            return InterpolatedGhostState(first.latLng.latitude, first.latLng.longitude, first.accumulatedDistanceMeters)
        }
        
        if (elapsedSeconds >= ghostPoints.last().elapsedSeconds) {
            val last = ghostPoints.last()
            return InterpolatedGhostState(last.latLng.latitude, last.latLng.longitude, last.accumulatedDistanceMeters)
        }
        
        for (i in 0 until ghostPoints.size - 1) {
            val p1 = ghostPoints[i]
            val p2 = ghostPoints[i + 1]
            if (elapsedSeconds >= p1.elapsedSeconds && elapsedSeconds <= p2.elapsedSeconds) {
                val t1 = p1.elapsedSeconds
                val t2 = p2.elapsedSeconds
                val duration = t2 - t1
                val fraction = if (duration > 0) (elapsedSeconds - t1).toFloat() / duration else 0f
                
                val lat = p1.latLng.latitude + fraction * (p2.latLng.latitude - p1.latLng.latitude)
                val lon = p1.latLng.longitude + fraction * (p2.latLng.longitude - p1.latLng.longitude)
                val dist = p1.accumulatedDistanceMeters + fraction * (p2.accumulatedDistanceMeters - p1.accumulatedDistanceMeters)
                
                return InterpolatedGhostState(lat, lon, dist)
            }
        }
        
        val last = ghostPoints.last()
        return InterpolatedGhostState(last.latLng.latitude, last.latLng.longitude, last.accumulatedDistanceMeters)
    }

    private fun alignGhostRoute(userStartLat: Double, userStartLon: Double) {
        try {
            if (userStartLat.isNaN() || userStartLon.isNaN()) return
            val firstGhost = ghostPoints.firstOrNull() ?: return
            if (firstGhost.latLng.latitude.isNaN() || firstGhost.latLng.longitude.isNaN()) return
            
            val results = FloatArray(1)
            Location.distanceBetween(
                userStartLat, userStartLon,
                firstGhost.latLng.latitude, firstGhost.latLng.longitude,
                results
            )
            val distance = results[0]
            if (distance.isNaN()) return
            
            // If the start points are more than 150m apart, offset the whole path to align with user start
            if (distance > 150f) {
                val latOffset = userStartLat - firstGhost.latLng.latitude
                val lonOffset = userStartLon - firstGhost.latLng.longitude
                
                ghostPoints = ghostPoints.mapNotNull { pt ->
                    val newLat = pt.latLng.latitude + latOffset
                    val newLon = pt.latLng.longitude + lonOffset
                    if (newLat.isNaN() || newLon.isNaN()) null
                    else pt.copy(latLng = LatLng(newLat, newLon))
                }
                
                val pathLatLngs = ghostPoints.map { it.latLng }
                _trackingState.update { it.copy(
                    ghostPathPoints = pathLatLngs,
                    ghostLatitude = pathLatLngs.firstOrNull()?.latitude,
                    ghostLongitude = pathLatLngs.firstOrNull()?.longitude
                ) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error aligning ghost route", e)
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

data class GhostPoint(
    val latLng: LatLng,
    val elapsedSeconds: Long,
    val accumulatedDistanceMeters: Float
)

data class InterpolatedGhostState(
    val latitude: Double?,
    val longitude: Double?,
    val distanceMeters: Float
)
