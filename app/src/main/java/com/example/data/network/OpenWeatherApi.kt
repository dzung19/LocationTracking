package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherApi {
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): OpenWeatherResponse
}

@JsonClass(generateAdapter = true)
data class OpenWeatherResponse(
    @Json(name = "main") val main: MainInfo,
    @Json(name = "weather") val weather: List<WeatherInfo>
)

@JsonClass(generateAdapter = true)
data class MainInfo(
    @Json(name = "temp") val temp: Double,
    @Json(name = "humidity") val humidity: Int
)

@JsonClass(generateAdapter = true)
data class WeatherInfo(
    @Json(name = "description") val description: String,
    @Json(name = "icon") val icon: String
)
