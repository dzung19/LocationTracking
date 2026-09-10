package com.dzung.runner.locationtracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.locationDataStore: DataStore<Preferences> by preferencesDataStore(name = "LocationPrefs")

data class UserPreferences(
    val latitude: Double,
    val longitude: Double,
    val weight: Float,
    val hasSavedLocation: Boolean
)

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_LATITUDE = doublePreferencesKey("last_lat")
        val KEY_LONGITUDE = doublePreferencesKey("last_lon")
        val KEY_WEIGHT = floatPreferencesKey("user_weight")

        const val DEFAULT_LATITUDE = 10.762622
        const val DEFAULT_LONGITUDE = 106.660172
        const val DEFAULT_WEIGHT = 70f
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.locationDataStore.data.map { prefs ->
        val hasLat = prefs.contains(KEY_LATITUDE)
        val hasLon = prefs.contains(KEY_LONGITUDE)
        UserPreferences(
            latitude = prefs[KEY_LATITUDE] ?: DEFAULT_LATITUDE,
            longitude = prefs[KEY_LONGITUDE] ?: DEFAULT_LONGITUDE,
            weight = prefs[KEY_WEIGHT] ?: DEFAULT_WEIGHT,
            hasSavedLocation = hasLat && hasLon
        )
    }

    suspend fun saveLocation(latitude: Double, longitude: Double) {
        context.locationDataStore.edit { prefs ->
            prefs[KEY_LATITUDE] = latitude
            prefs[KEY_LONGITUDE] = longitude
        }
    }

    suspend fun saveWeight(weight: Float) {
        context.locationDataStore.edit { prefs ->
            prefs[KEY_WEIGHT] = weight
        }
    }
}
