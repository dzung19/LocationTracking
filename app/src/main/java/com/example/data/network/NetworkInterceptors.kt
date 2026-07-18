package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

// --- Exceptions ---
class NoConnectivityException(message: String) : IOException(message)

open class ApiException(message: String, cause: Throwable? = null) : IOException(message, cause)
class NetworkException(message: String, cause: Throwable?) : ApiException(message, cause)
class UnauthorizedException(message: String) : ApiException(message)
class ForbiddenException(message: String) : ApiException(message)
class NotFoundException(message: String) : ApiException(message)
class ServerException(message: String) : ApiException(message)

// --- Interceptors ---

/**
 * Checks for device internet connectivity before executing the HTTP request.
 * Throws [NoConnectivityException] if the device is offline.
 */
class NetworkConnectionInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!isInternetAvailable(context)) {
            throw NoConnectivityException("No internet connection available")
        }
        return chain.proceed(chain.request())
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * Automatically appends the static OpenWeather API key query parameter ('appid')
 * to every outgoing network request.
 */
class WeatherApiKeyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val apiKey = BuildConfig.OPENWEATHER_API_KEY
        
        if (apiKey.isBlank() || apiKey == "dummy_key") {
            throw IOException("Invalid OpenWeather API key configuration")
        }
        
        val newUrl = originalRequest.url.newBuilder()
            .addQueryParameter("appid", apiKey)
            .build()
            
        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()
            
        return chain.proceed(newRequest)
    }
}

/**
 * Catches lower-level raw network errors (timeouts, etc.) and server error response codes,
 * mapping them into clean, structured [ApiException] subclasses.
 */
class ErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response: Response
        try {
            response = chain.proceed(chain.request())
        } catch (e: NoConnectivityException) {
            throw e
        } catch (e: IOException) {
            throw NetworkException("Network communication failure: ${e.message}", e)
        }

        if (!response.isSuccessful) {
            when (response.code) {
                401 -> throw UnauthorizedException("Unauthorized: Invalid API credentials")
                403 -> throw ForbiddenException("Forbidden: Access denied")
                404 -> throw NotFoundException("Resource not found on server")
                in 500..599 -> throw ServerException("Server error encountered (HTTP ${response.code})")
                else -> throw ApiException("Network API error (HTTP ${response.code})")
            }
        }
        return response
    }
}
