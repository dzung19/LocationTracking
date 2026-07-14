package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.WeatherState
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LocationViewModel(private val weatherRepository: WeatherRepository) : ViewModel() {

    private var activeService: LocationTrackingService? = null
    private var serviceCollectorJob: Job? = null

    // Mirrors the tracking state from the service to the UI
    private val _trackingState = MutableStateFlow(LocationTrackingState())
    val trackingState: StateFlow<LocationTrackingState> = _trackingState.asStateFlow()

    // Tracks if the service is currently bound and available
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    // Weather state for UI representation
    private val _weatherState = MutableStateFlow<WeatherState>(WeatherState.Idle)
    val weatherState: StateFlow<WeatherState> = _weatherState.asStateFlow()

    /**
     * Fetches current weather for given latitude and longitude with repository caching.
     */
    fun fetchWeather(lat: Double, lon: Double) {
        if (_weatherState.value is WeatherState.Loading) return
        _weatherState.value = WeatherState.Loading
        viewModelScope.launch {
            weatherRepository.getWeather(lat, lon)
                .onSuccess { _weatherState.value = WeatherState.Success(it) }
                .onFailure { _weatherState.value = WeatherState.Error(it.message ?: "Unknown error") }
        }
    }

    /**
     * Called by the Activity when the LocationTrackingService is connected or disconnected.
     */
    fun setService(service: LocationTrackingService?) {
        activeService = service
        _isServiceBound.value = service != null
        
        // Cancel any existing collector job to avoid memory leaks or duplicate collections
        serviceCollectorJob?.cancel()
        
        if (service != null) {
            // Start collecting state updates from the service
            serviceCollectorJob = viewModelScope.launch {
                service.trackingState.collect { state ->
                    _trackingState.value = state
                }
            }
        } else {
            // If service is disconnected unexpectedly, mark tracking as false safely
            _trackingState.update { it.copy(isTracking = false) }
        }
    }

    /**
     * Delegates the start tracking command to the active service.
     */
    fun startTracking() {
        activeService?.startLocationUpdates()
    }

    /**
     * Delegates the stop tracking command to the active service.
     */
    fun stopTracking() {
        activeService?.stopLocationUpdates()
    }

    /**
     * Delegates setting the activity type to the active service.
     */
    fun setActivityType(type: com.example.data.database.ActivityType) {
        activeService?.setActivityType(type)
    }
}
