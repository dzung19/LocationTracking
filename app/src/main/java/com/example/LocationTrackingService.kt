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
class LocationTrackingService : Service() {

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

    // Run Tracker specifics
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var timerJob: Job? = null
    private var currentSessionId: Long? = null
    private var previousLocation: Location? = null
    private var accumulatedDistance: Float = 0f
    private var startTimeMillis: Long = 0L

    inner class LocalBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Initializing service resources")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        setupLocationCallback()
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
                
                // Calculate distance
                var distanceDelta = 0f
                previousLocation?.let { prevLoc ->
                    distanceDelta = prevLoc.distanceTo(location)
                    accumulatedDistance += distanceDelta
                }
                previousLocation = location

                val latLng = LatLng(location.latitude, location.longitude)

                _trackingState.update { currentState ->
                    val newPath = currentState.pathPoints + latLng
                    currentState.copy(
                        isTracking = true,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        timestamp = location.time,
                        distanceMeters = accumulatedDistance,
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
            previousLocation = null
            startTimeMillis = System.currentTimeMillis()
            _trackingState.update {
                it.copy(
                    isTracking = true,
                    errorMessage = null,
                    distanceMeters = 0f,
                    elapsedTimeSeconds = 0L,
                    caloriesBurned = 0,
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

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (true) {
                delay(1000L)
                val elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000
                
                _trackingState.update { state ->
                    // Calorie calculation: weight * distance(km) * multiplier
                    val multiplier = if (state.activityType == ActivityType.RUNNING) 1.036f else 0.73f
                    val distanceKm = state.distanceMeters / 1000f
                    val calories = (70f * distanceKm * multiplier).toInt()
                    
                    state.copy(
                        elapsedTimeSeconds = elapsedSeconds,
                        caloriesBurned = calories
                    )
                }
            }
        }
    }

    fun stopLocationUpdates() {
        if (!_trackingState.value.isTracking) return

        fusedLocationClient.removeLocationUpdates(locationCallback)

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

    override fun onDestroy() {
        timerJob?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }
}
