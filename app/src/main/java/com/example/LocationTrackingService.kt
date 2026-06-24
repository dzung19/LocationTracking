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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    // Binder returned to clients connecting to the Service
    private val binder = LocalBinder()

    // Location provider client from Google Play Services
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Callback that handles incoming location results
    private lateinit var locationCallback: LocationCallback

    // Holds the reactive state of location tracking
    private val _trackingState = MutableStateFlow(LocationTrackingState())
    val trackingState: StateFlow<LocationTrackingState> = _trackingState.asStateFlow()

    // Notification manager to update the persistent status notification
    private lateinit var notificationManager: NotificationManager

    /**
     * Local Binder implementation returning a reference to the service instance.
     * This avoids complex IPC since the Service and Activity run in the same process.
     */
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
        Log.d(TAG, "onBind: Client connected to service")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind: Client disconnected from service")
        // Return true to allow onRebind to be called if future clients connect
        return true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Received intent with action = ${intent?.action}")
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                startLocationUpdates()
            }
            ACTION_STOP_TRACKING -> {
                stopLocationUpdates()
            }
        }
        // STICKY ensures that if the OS kills the service, it gets recreated (restarted)
        return START_STICKY
    }

    /**
     * Configures the location callback to update our StateFlow and the foreground notification
     * whenever play services delivers a new location reading.
     */
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val location = result.lastLocation ?: return
                Log.d(TAG, "onLocationResult: New coordinates - Lat: ${location.latitude}, Lon: ${location.longitude}")

                // Update reactive flow state
                _trackingState.update { currentState ->
                    currentState.copy(
                        isTracking = true,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        timestamp = location.time,
                        errorMessage = null
                    )
                }

                // Dynamically refresh the notification content to display the current position
                updateNotificationContent(location)
            }
        }
    }

    /**
     * Public command to request high-accuracy, real-time location updates.
     */
    fun startLocationUpdates() {
        if (_trackingState.value.isTracking) {
            Log.d(TAG, "startLocationUpdates: Already tracking")
            return
        }

        // Validate fine location permission before scheduling play services updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "startLocationUpdates: Cannot start, permission denied")
            _trackingState.update { it.copy(errorMessage = "Location permission is not granted.") }
            return
        }

        try {
            // Build the modern LocationRequest using the LocationRequest.Builder
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L).apply {
                setMinUpdateIntervalMillis(2000L) // Set the fastest rate for updates
                setWaitForAccurateLocation(false)
            }.build()

            // Request updates on the main thread loop
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Transition the service to foreground execution to protect it from background termination
            val notification = buildStatusNotification("Starting location tracking...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            _trackingState.update {
                it.copy(
                    isTracking = true,
                    errorMessage = null
                )
            }
            Log.d(TAG, "startLocationUpdates: Successfully registered location updates")
        } catch (e: SecurityException) {
            Log.e(TAG, "startLocationUpdates: SecurityException: ${e.message}")
            _trackingState.update { it.copy(errorMessage = "SecurityException: ${e.message}") }
        }
    }

    /**
     * Public command to suspend location tracking and transition back to a background service.
     */
    fun stopLocationUpdates() {
        if (!_trackingState.value.isTracking) {
            Log.d(TAG, "stopLocationUpdates: Not currently tracking")
            return
        }

        Log.d(TAG, "stopLocationUpdates: Stopping location updates")
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Stop foreground execution and remove the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        _trackingState.update {
            it.copy(
                isTracking = false,
                errorMessage = "Tracking stopped by user"
            )
        }
        
        // Stop the service if there are no bound clients anymore
        stopSelf()
    }

    /**
     * Exposes whether tracking is currently active.
     */
    fun isTracking(): Boolean {
        return _trackingState.value.isTracking
    }

    /**
     * Public accessor to grab the latest location snapshot synchronously.
     */
    fun getLatestLocation(): Location? {
        val state = _trackingState.value
        if (state.latitude == null || state.longitude == null) return null
        return Location("FUSED").apply {
            latitude = state.latitude
            longitude = state.longitude
            accuracy = state.accuracy ?: 0f
            time = state.timestamp ?: 0L
        }
    }

    /**
     * Refreshes the content of the foreground persistent notification with new coordinates.
     */
    private fun updateNotificationContent(location: Location) {
        val text = "Latitude: %.5f, Longitude: %.5f (±%.1fm)".format(
            location.latitude,
            location.longitude,
            location.accuracy
        )
        val notification = buildStatusNotification(text)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Construct the status notification including interactive content and action buttons.
     */
    private fun buildStatusNotification(contentText: String): Notification {
        // Stop Action Intent when clicked from notification
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification Click Intent (brings user to MainActivity)
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val activityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Tracker Active")
            .setContentText(contentText)
            // Use Android built-in compass drawable as small icon
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(activityPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Tracking",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    /**
     * Prepares the Notification Channel needed for Android 8.0+ (Oreo).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel displaying active GPS location tracking updates"
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Destroying service and cleaning up callbacks")
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }
}
