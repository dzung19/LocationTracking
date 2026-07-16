package com.example

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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.data.database.ActivityType
import com.example.data.database.AppDatabase
import com.example.data.database.LocationPoint
import com.example.data.database.RunSession
import com.example.data.database.RunDao
import com.example.ui.screens.WEIGHT_KEY
import com.example.ui.screens.dataStore
import com.google.android.gms.location.FusedLocationProviderClient
import org.koin.android.ext.android.inject
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A Bound and Started Service responsible for tracking user location using FusedLocationProviderClient.
 * It elevates itself to a Foreground Service while tracking is active to prevent the system
 * from reclaiming its resources, and exposes location updates in real-time through a Kotlin StateFlow.
 */
class LocationTrackingService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "LocationTrackingService"
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 101

        const val ACTION_START_TRACKING = "com.example.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.ACTION_STOP_TRACKING"
    }

    private val binder = LocalBinder()
    private val runDao: RunDao by inject()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val _trackingState = MutableStateFlow(LocationTrackingState())
    val trackingState: StateFlow<LocationTrackingState> = _trackingState.asStateFlow()

    private lateinit var notificationManager: NotificationManager
    
    private lateinit var sensorManager: SensorManager
    private var pressureSensor: Sensor? = null

    // Run Tracker specifics
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var timerJob: Job? = null
    private var currentSessionId: Long? = null
    private var previousLocation: Location? = null
    private var accumulatedDistance: Float = 0f
    private var accumulatedCalories: Float = 0f
    private var startTimeMillis: Long = 0L
    private var currentWeightKg: Float = 70f
    
    // Ghost Runner data
    private var ghostPoints = listOf<GhostPoint>()
    
    // Barometer tracking
    private var currentElevation: Float = 0f
    private var lastSlopeDistance: Float = 0f
    private var lastSlopeElevation: Float = 0f
    private var currentSlopePercentage: Float = 0f

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
        
        // Observe weight changes from DataStore
        serviceScope.launch {
            applicationContext.dataStore.data.collect { prefs ->
                currentWeightKg = prefs[WEIGHT_KEY] ?: 70f
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
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
        if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
            val pressure = event.values[0]
            val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
            
            if (lastSlopeElevation == 0f) {
                lastSlopeElevation = altitude
            }
            currentElevation = altitude
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
                val location = result.lastLocation ?: return
                
                // Calculate distance and time delta
                var distanceDelta = 0f
                var timeDeltaMs = 0L
                previousLocation?.let { prevLoc ->
                    distanceDelta = prevLoc.distanceTo(location)
                    timeDeltaMs = location.time - prevLoc.time
                    accumulatedDistance += distanceDelta
                }
                previousLocation = location

                val latLng = LatLng(location.latitude, location.longitude)
                
                // Calculate Calories using MET formula
                val timeDeltaMinutes = timeDeltaMs / 60000f
                val speedMps = if (location.hasSpeed()) {
                    location.speed
                } else if (timeDeltaMs > 0) {
                    distanceDelta / (timeDeltaMs / 1000f)
                } else {
                    0f
                }
                val speedKmh = speedMps * 3.6f
                val currentActivityType = _trackingState.value.activityType
                val met = getMET(speedKmh, currentActivityType)
                
                // Formula: Calories/min = (MET * Weight(kg) * 3.5) / 200
                val weightKg = currentWeightKg
                val caloriesDelta = (met * weightKg * 3.5f / 200f) * timeDeltaMinutes
                accumulatedCalories += caloriesDelta

                // Slope calculation
                val deltaDistanceForSlope = accumulatedDistance - lastSlopeDistance
                if (deltaDistanceForSlope > 10f && currentElevation != 0f) { // Update slope every 10 meters to smooth noise
                    val deltaElevation = currentElevation - lastSlopeElevation
                    currentSlopePercentage = (deltaElevation / deltaDistanceForSlope) * 100f
                    
                    lastSlopeDistance = accumulatedDistance
                    lastSlopeElevation = currentElevation
                }

                _trackingState.update { currentState ->
                    val newPath = currentState.pathPoints + latLng
                    currentState.copy(
                        isTracking = true,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        timestamp = location.time,
                        distanceMeters = accumulatedDistance,
                        caloriesBurned = accumulatedCalories.toInt(),
                        elevationMeters = currentElevation,
                        slopePercentage = currentSlopePercentage,
                        pathPoints = newPath,
                        errorMessage = null
                    )
                }

                // Insert point to DB
                currentSessionId?.let { sid ->
                    serviceScope.launch {
                        val point = LocationPoint(
                            sessionId = sid,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = location.time
                        )
                        runDao.insertLocationPoint(point)
                    }
                }

                updateNotificationContent(location)
            }
        }
    }

    fun startLocationUpdates() {
        if (_trackingState.value.isTracking) return

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            _trackingState.update { it.copy(errorMessage = "Location permission is not granted.") }
            return
        }

        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L).apply {
                setMinUpdateIntervalMillis(2000L)
                setWaitForAccurateLocation(false)
            }.build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            val notification = buildStatusNotification("Starting location tracking...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Reset state
            accumulatedDistance = 0f
            accumulatedCalories = 0f
            previousLocation = null
            startTimeMillis = System.currentTimeMillis()
            
            currentElevation = 0f
            lastSlopeDistance = 0f
            lastSlopeElevation = 0f
            currentSlopePercentage = 0f
            
            pressureSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }

            _trackingState.update {
                it.copy(
                    isTracking = true,
                    errorMessage = null,
                    distanceMeters = 0f,
                    elapsedTimeSeconds = 0L,
                    caloriesBurned = 0,
                    elevationMeters = 0f,
                    slopePercentage = 0f,
                    pathPoints = emptyList()
                )
            }
            
            // Start Timer and DB Session
            val currentActivityType = _trackingState.value.activityType
            serviceScope.launch {
                val session = RunSession(
                    startTimeInMillis = startTimeMillis,
                    activityType = currentActivityType
                )
                currentSessionId = runDao.insertRunSession(session)
            }

            startTimer()
            
        } catch (e: SecurityException) {
            _trackingState.update { it.copy(errorMessage = "SecurityException: ${e.message}") }
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
            while (true) {
                delay(1000L)
                val elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000
                
                if (ghostPoints.isNotEmpty()) {
                    val ghostState = interpolateGhost(elapsedSeconds, ghostPoints)
                    _trackingState.update { state ->
                        state.copy(
                            elapsedTimeSeconds = elapsedSeconds,
                            ghostLatitude = ghostState.latitude,
                            ghostLongitude = ghostState.longitude,
                            ghostDistanceMeters = ghostState.distanceMeters
                        )
                    }
                } else {
                    _trackingState.update { state ->
                        state.copy(
                            elapsedTimeSeconds = elapsedSeconds
                        )
                    }
                }
            }
        }
    }

    fun stopLocationUpdates() {
        if (!_trackingState.value.isTracking) return

        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        timerJob?.cancel()

        val finalState = _trackingState.value
        serviceScope.launch {
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
            currentSessionId = null
        }

        _trackingState.update {
            it.copy(
                isTracking = false,
                errorMessage = "Tracking stopped by user"
            )
        }
        
        stopSelf()
    }

    private fun updateNotificationContent(location: Location) {
        val state = _trackingState.value
        val distanceKm = state.distanceMeters / 1000f
        val text = "Dist: %.2f km | Time: %d s".format(distanceKm, state.elapsedTimeSeconds)
        val notification = buildStatusNotification(text)
        notificationManager.notify(NOTIFICATION_ID, notification)
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
            val points = runDao.getLocationPointsForSessionOnce(sessionId)
            val sorted = points.sortedBy { it.timestamp }
            val ghostStartTime = sorted.firstOrNull()?.timestamp ?: 0L
            val pointsList = mutableListOf<GhostPoint>()
            var dist = 0f
            var prevPoint: LocationPoint? = null
            sorted.forEach { pt ->
                prevPoint?.let { prev ->
                    val results = FloatArray(1)
                    Location.distanceBetween(prev.latitude, prev.longitude, pt.latitude, pt.longitude, results)
                    dist += results[0]
                }
                val elapsed = (pt.timestamp - ghostStartTime) / 1000
                pointsList.add(GhostPoint(LatLng(pt.latitude, pt.longitude), elapsed, dist))
                prevPoint = pt
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

    override fun onDestroy() {
        timerJob?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
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
