package com.example

import com.example.data.database.ActivityType
import com.google.android.gms.maps.model.LatLng

/**
 * Data class representing the state of location tracking.
 * This class is exposed as a StateFlow by the LocationTrackingService
 * so that the Compose UI can reactively observe and render updates.
 */
data class LocationTrackingState(
    val isTracking: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val timestamp: Long? = null,
    val errorMessage: String? = null,
    
    // New metrics for Run Tracker
    val distanceMeters: Float = 0f,
    val elapsedTimeSeconds: Long = 0L,
    val caloriesBurned: Int = 0,
    val elevationMeters: Float = 0f,
    val slopePercentage: Float = 0f,
    val activityType: ActivityType = ActivityType.RUNNING,
    val pathPoints: List<LatLng> = emptyList()
)
