package com.dzung.runner.locationtracker.data.network

import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@Keep
interface OpenWeatherApi {
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String = "metric"
    ): OpenWeatherResponse
}

@Keep
@JsonClass(generateAdapter = true)
data class OpenWeatherResponse(
    @Json(name = "main") val main: MainInfo,
    @Json(name = "weather") val weather: List<WeatherInfo>
)

@Keep
@JsonClass(generateAdapter = true)
data class MainInfo(
    @Json(name = "temp") val temp: Double,
    @Json(name = "humidity") val humidity: Int
)

@Keep
@JsonClass(generateAdapter = true)
data class WeatherInfo(
    @Json(name = "description") val description: String,
    @Json(name = "icon") val icon: String
)
