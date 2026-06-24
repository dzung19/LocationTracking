package com.example

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
    val errorMessage: String? = null
)
