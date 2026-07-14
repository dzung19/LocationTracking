package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WeatherData(
    val temp: Double,
    val description: String,
    val icon: String,
    val humidity: Int,
    val recommendation: String
)

sealed interface WeatherState {
    object Idle : WeatherState
    object Loading : WeatherState
    data class Success(val weather: WeatherData) : WeatherState
    data class Error(val message: String) : WeatherState
}
