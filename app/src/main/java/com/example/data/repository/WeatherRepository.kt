package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.data.model.WeatherData
import com.example.data.network.OpenWeatherApi
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeatherRepository(
    private val api: OpenWeatherApi,
    private val context: Context,
    private val moshi: Moshi
) {
    private val prefs = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
    private val adapter = moshi.adapter(WeatherData::class.java)

    suspend fun getWeather(lat: Double, lon: Double): Result<WeatherData> = withContext(Dispatchers.IO) {
        val cacheKey = "weather_data"
        val timeKey = "weather_time"
        val latKey = "weather_lat"
        val lonKey = "weather_lon"

        val cachedJson = prefs.getString(cacheKey, null)
        val cachedTime = prefs.getLong(timeKey, 0L)
        val cachedLat = prefs.getFloat(latKey, 0f).toDouble()
        val cachedLon = prefs.getFloat(lonKey, 0f).toDouble()

        val now = System.currentTimeMillis()
        val cacheDuration = 30 * 60 * 1000 // 30 minutes in milliseconds

        val distance = calculateDistance(lat, lon, cachedLat, cachedLon)

        // Use cache if within 30 minutes AND location is within 1 km
        if (cachedJson != null && (now - cachedTime < cacheDuration) && distance < 1000) {
            try {
                val cachedData = adapter.fromJson(cachedJson)
                if (cachedData != null) {
                    return@withContext Result.success(cachedData)
                }
            } catch (e: Exception) {
                // Fail silently, fetch from API
            }
        }

        // Fetch from API
        try {
            val apiKey = BuildConfig.OPENWEATHER_API_KEY
            if (apiKey.isBlank() || apiKey == "dummy_key") {
                return@withContext Result.failure(Exception("Invalid API key"))
            }

            val response = api.getCurrentWeather(lat, lon, apiKey)
            val temp = response.main.temp
            val humidity = response.main.humidity
            val desc = response.weather.firstOrNull()?.description ?: "clear sky"
            val icon = response.weather.firstOrNull()?.icon ?: "01d"

            val recommendation = when {
                desc.contains("rain", ignoreCase = true) || 
                desc.contains("drizzle", ignoreCase = true) || 
                desc.contains("thunderstorm", ignoreCase = true) -> "Rainy weather ☔. Running indoors is recommended."
                temp > 33 -> "Very hot! 🥵 Hydrate well and avoid peak sun hours."
                temp < 10 -> "Chilly weather 🥶. Dress in layers!"
                desc.contains("snow", ignoreCase = true) -> "Snowy! ❄️ Watch out for slippery roads."
                else -> "Perfect weather for a run! 🏃‍♂️"
            }

            val weatherData = WeatherData(
                temp = temp,
                description = desc,
                icon = icon,
                humidity = humidity,
                recommendation = recommendation
            )

            // Cache it
            val json = adapter.toJson(weatherData)
            prefs.edit()
                .putString(cacheKey, json)
                .putLong(timeKey, now)
                .putFloat(latKey, lat.toFloat())
                .putFloat(lonKey, lon.toFloat())
                .apply()

            Result.success(weatherData)
        } catch (e: Exception) {
            // Fallback to stale cache if API call fails
            if (cachedJson != null) {
                try {
                    val cachedData = adapter.fromJson(cachedJson)
                    if (cachedData != null) {
                        return@withContext Result.success(cachedData)
                    }
                } catch (ex: Exception) {}
            }
            Result.failure(e)
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
