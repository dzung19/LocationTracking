package com.dzung.runner.locationtracker.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

val Context.locationDataStore: DataStore<Preferences> by preferencesDataStore(name = "LocationPrefs")

data class UserPreferences(
    val latitude: Double,
    val longitude: Double,
    val weight: Float,
    val hasSavedLocation: Boolean,
    val markerIcon: String = UserPreferencesRepository.DEFAULT_MARKER_ICON
)

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private const val TAG = "UserPreferencesRepo"
        val KEY_LATITUDE = doublePreferencesKey("last_lat")
        val KEY_LONGITUDE = doublePreferencesKey("last_lon")
        val KEY_WEIGHT = floatPreferencesKey("user_weight")
        val KEY_MARKER_ICON = stringPreferencesKey("marker_icon")

        const val DEFAULT_LATITUDE = 10.762622
        const val DEFAULT_LONGITUDE = 106.660172
        const val DEFAULT_WEIGHT = 70f
        const val DEFAULT_MARKER_ICON = "DEFAULT"
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.locationDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading location preferences from DataStore", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val hasLat = prefs.contains(KEY_LATITUDE)
            val hasLon = prefs.contains(KEY_LONGITUDE)
            UserPreferences(
                latitude = prefs[KEY_LATITUDE] ?: DEFAULT_LATITUDE,
                longitude = prefs[KEY_LONGITUDE] ?: DEFAULT_LONGITUDE,
                weight = prefs[KEY_WEIGHT] ?: DEFAULT_WEIGHT,
                hasSavedLocation = hasLat && hasLon,
                markerIcon = prefs[KEY_MARKER_ICON] ?: DEFAULT_MARKER_ICON
            )
        }

    suspend fun saveLocation(latitude: Double, longitude: Double) {
        try {
            context.locationDataStore.edit { prefs ->
                prefs[KEY_LATITUDE] = latitude
                prefs[KEY_LONGITUDE] = longitude
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving location coordinates to DataStore", e)
        }
    }

    suspend fun saveWeight(weight: Float) {
        try {
            context.locationDataStore.edit { prefs ->
                prefs[KEY_WEIGHT] = weight
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user weight to DataStore", e)
        }
    }

    suspend fun saveMarkerIcon(markerIcon: String) {
        try {
            context.locationDataStore.edit { prefs ->
                prefs[KEY_MARKER_ICON] = markerIcon
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving marker icon to DataStore", e)
        }
    }
}
