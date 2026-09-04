package com.dzung.runner.locationtracker.data.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class WeatherData(
    val temp: Double,
    val description: String,
    val icon: String,
    val humidity: Int,
    val recommendation: String
)

@Keep
sealed interface WeatherState {
    @Keep object Idle : WeatherState
    @Keep object Loading : WeatherState
    @Keep data class Success(val weather: WeatherData) : WeatherState
    @Keep data class Error(val message: String) : WeatherState
}
