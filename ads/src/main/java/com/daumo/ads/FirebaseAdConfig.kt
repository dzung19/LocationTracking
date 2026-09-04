package com.daumo.ads

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

import com.google.gson.annotations.SerializedName

data class AdConfigSet(
    @SerializedName("appOpenAdId")
    val appOpenAdId: String = "",
    @SerializedName("bannerAdId")
    val bannerAdId: String = "",
    @SerializedName("interstitialAdId")
    val interstitialAdId: String = "",
    @SerializedName("removeAdsSku")
    val removeAdsSku: String = "remove_ads_sku",
    @SerializedName("priority")
    val priority: Int = 1,
    @SerializedName("isActive")
    val isActive: Boolean = true
)

class FirebaseAdConfig private constructor() {
    private val remoteConfig: FirebaseRemoteConfig? by lazy {
        try {
            FirebaseRemoteConfig.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseRemoteConfig not available: ${e.message}")
            null
        }
    }
    private val TAG = "ADS_DEBUG"

    // Local cache for parsed ad configs
    private var cachedAdConfigs: List<AdConfigSet>? = null
    private var lastConfigFetchTime: Long = 0

    companion object {
        @Volatile
        private var INSTANCE: FirebaseAdConfig? = null

        fun getInstance(): FirebaseAdConfig {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseAdConfig().also { INSTANCE = it }
            }
        }

        // Empty fallback configs - will use build config values
        private val FALLBACK_CONFIGS = emptyList<AdConfigSet>()
    }

    suspend fun initialize(adsDisabled: Boolean = false): Boolean {
        // If ads are disabled, don't initialize Firebase Remote Config
        if (adsDisabled) {
            return false
        }

        val rc = remoteConfig ?: return false

        return try {
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0) // Fetch latest config immediately
                .build()

            rc.setConfigSettingsAsync(configSettings)
            Log.d(TAG, "Firebase Remote Config settings applied")

            // Set default values
            setDefaultValues()
            Log.d(TAG, "Default values set")

            // Fetch and activate remote config atomically
            val activated = rc.fetchAndActivate().await()
            Log.d(TAG, "Firebase Remote Config fetchAndActivate: $activated")

            val configsJson = rc.getString("ad_configs")
            Log.d(TAG, "Firebase ad_configs: $configsJson")
            if (hasConfigsChanged(configsJson)) {
                clearCache()
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize Firebase Remote Config", e)
            Log.e(TAG, "Firebase initialization error: ${e.message}")
            false
        }
    }

    private fun setDefaultValues() {
        val rc = remoteConfig ?: return
        val defaults = mutableMapOf<String, Any>()

        // No default configs - only use Firebase remote config
        defaults["ad_configs"] = "[]"
        defaults["failed_ad_ids"] = "[]"
        defaults["config_update_time"] = System.currentTimeMillis().toString()

        rc.setDefaultsAsync(defaults)
    }

    fun getActiveAdConfigs(): List<AdConfigSet> {
        Log.d(TAG, "🔄 getActiveAdConfigs() called")

        // Check if we have cached configs that are still valid
        cachedAdConfigs?.let { cached ->
            Log.d(TAG, "📦 Returning cached ad configs (${cached.size} items)")
            return cached
        }

        val rc = remoteConfig ?: return FALLBACK_CONFIGS

        return try {
            val configsJson = rc.getString("ad_configs")
            val failedAdIdsJson = rc.getString("failed_ad_ids")

            val configs = parseAdConfigs(configsJson)
            val failedAdIds = parseFailedAdIds(failedAdIdsJson)

            val activeConfigs = configs.filter { it.isActive && it.appOpenAdId !in failedAdIds }

            // Cache the active configs
            cachedAdConfigs = activeConfigs
            lastConfigFetchTime = System.currentTimeMillis()

            // If no active configs from Firebase, use fallback configs
            if (activeConfigs.isEmpty()) {
                Log.w(TAG, "⚠️ No active configs from Firebase, using fallback configs")
                return FALLBACK_CONFIGS
            }

            val sortedConfigs = activeConfigs.sortedBy { it.priority }
            sortedConfigs
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active ad configs", e)
            Log.w(TAG, "⚠️ Error getting configs, using fallback configs")
            FALLBACK_CONFIGS
        }
    }

    fun getBestAdConfig(): AdConfigSet? {
        val activeConfigs = getActiveAdConfigs()
        return activeConfigs.firstOrNull()
    }

    suspend fun markAdConfigAsFailed(adUnitId: String) {
        val rc = remoteConfig ?: return
        try {
            val failedAdIdsJson = rc.getString("failed_ad_ids")
            val failedAdIds = parseFailedAdIds(failedAdIdsJson).toMutableSet()

            failedAdIds.add(adUnitId)

            val updatedFailedIdsJson = failedAdIds.joinToString(",", "[", "]") { "\"$it\"" }

            val defaults = mutableMapOf<String, Any>()
            defaults["failed_ad_ids"] = updatedFailedIdsJson
            rc.setDefaultsAsync(defaults)

            // Clear cache when failed ad IDs are updated
            clearCache()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark ad config as failed", e)
        }
    }

    suspend fun resetFailedAdConfigs() {
        val rc = remoteConfig ?: return
        try {
            val defaults = mutableMapOf<String, Any>()
            defaults["failed_ad_ids"] = "[]"
            defaults["config_update_time"] = System.currentTimeMillis().toString()
            rc.setDefaultsAsync(defaults)

            // Clear cache when failed ad IDs are reset
            clearCache()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset failed ad configs", e)
        }
    }

    /**
     * Check if ads are disabled via Firebase Remote Config
     */
    fun isAdsDisabled(): Boolean {
        val rc = remoteConfig ?: return false
        return try {
            rc.getBoolean("ads_disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting ads_disabled value from Firebase", e)
            false
        }
    }

    /**
     * Check if the Firebase configs have changed compared to the cached data
     */
    private fun hasConfigsChanged(newConfigsJson: String): Boolean {
        // If we don't have cached configs, treat as changed
        val cachedConfigs = cachedAdConfigs
        if (cachedConfigs == null) {
            return true
        }

        // Parse the new configs to compare
        val newConfigs = try {
            parseAdConfigs(newConfigsJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing new configs for comparison", e)
            // If we can't parse new configs, treat as changed to be safe
            return true
        }

        // Compare the configs
        val hasChanged = cachedConfigs != newConfigs
        if (hasChanged) {
        } else {
        }
        return hasChanged
    }

    /**
     * Clear the local cache of parsed ad configs
     */
    private fun clearCache() {
        Log.d(TAG, "🧹 Clearing local cache")
        cachedAdConfigs = null
        lastConfigFetchTime = 0
    }

    private fun parseAdConfigs(json: String): List<AdConfigSet> {
        if (json.isBlank() || json.trim() == "[]") {
            return emptyList()
        }
        return try {
            val gson = Gson()
            val cleanJson = extractAdConfigsJson(json) ?: json.trim()

            val listType = object : TypeToken<List<AdConfigSet>>() {}.type
            val directList: List<AdConfigSet>? = try {
                gson.fromJson(cleanJson, listType)
            } catch (e: Exception) {
                null
            }

            if (!directList.isNullOrEmpty()) {
                return directList.filter { it.isActive }.sortedBy { it.priority }
            }

            // Fallback to Map parsing
            parseFirebaseConfig(cleanJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ad configs", e)
            emptyList()
        }
    }

    private fun parseFirebaseConfig(json: String): List<AdConfigSet> {
        return try {
            val gson = Gson()
            val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
            val configList: List<Map<String, Any>> = gson.fromJson(json, listType)

            val configs = configList.mapNotNull { configMap ->
                try {
                    val appOpenAdId = configMap["appOpenAdId"] as? String ?: ""
                    val bannerAdId = configMap["bannerAdId"] as? String ?: ""
                    val interstitialAdId = configMap["interstitialAdId"] as? String ?: ""
                    val removeAdsSku = configMap["removeAdsSku"] as? String ?: "remove_ads_sku"
                    val priority = (configMap["priority"] as? Double)?.toInt() ?: (configMap["priority"] as? Int) ?: 1
                    val isActive = configMap["isActive"] as? Boolean ?: true

                    if (bannerAdId.isEmpty() && interstitialAdId.isEmpty()) {
                        null
                    } else {
                        AdConfigSet(
                            appOpenAdId = appOpenAdId,
                            bannerAdId = bannerAdId,
                            interstitialAdId = interstitialAdId,
                            removeAdsSku = removeAdsSku,
                            priority = priority,
                            isActive = isActive
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Failed to parse individual config: $e")
                    null
                }
            }

            configs.sortedBy { it.priority }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Firebase config JSON with Gson", e)
            emptyList()
        }
    }

    private fun extractAdConfigsJson(json: String): String? {
        return try {
            // First try to extract from Firebase Remote Config structure
            val adConfigsValuePattern = """"ad_configs":\s*\{[^}]*"value":\s*"\[([^\]]+)\]"""".toRegex()
            val match = adConfigsValuePattern.find(json)

            if (match != null) {
                // Found the ad configs array content, reconstruct the full array
                val arrayContent = match.groupValues[1]
                return "[$arrayContent]"
            }

            // If not found in Firebase structure, check if it's already a direct array
            if (json.trim().startsWith("[") && json.trim().endsWith("]")) {
                return json
            }

            // Try to find any array that contains ad config fields
            val arrayPattern = """\[[^]]*"applicationId":[^]]*\]""".toRegex()
            val arrayMatch = arrayPattern.find(json)
            if (arrayMatch != null) {
                return arrayMatch.value
            }

            Log.w(TAG, "Could not find ad configs array in JSON")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting ad configs JSON", e)
            null
        }
    }

    private fun parseFailedAdIds(json: String): Set<String> {
        return try {
            // Simple JSON parsing for failed ad IDs
            emptySet() // For now, return empty set
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing failed ad IDs", e)
            emptySet()
        }
    }
}
