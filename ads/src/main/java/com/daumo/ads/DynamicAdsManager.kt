package com.daumo.ads

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DynamicAdsManager private constructor(
    private val application: Application,
    private val adsDisabled: Boolean = false
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val firebaseAdConfig = FirebaseAdConfig.getInstance()
    private val manifestUpdater = ManifestUpdater.getInstance(application)
    private val TAG = "ADS_DEBUG"

    private val _currentAdConfig = MutableStateFlow<AdConfigSet?>(null)
    val currentAdConfig: StateFlow<AdConfigSet?> = _currentAdConfig.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private var monetizationManager: MonetizationManager? = null

    companion object {
        @Volatile
        private var INSTANCE: DynamicAdsManager? = null

        @JvmStatic
        fun initialize(application: Application, adsDisabled: Boolean) {
            synchronized(this) {
                if (INSTANCE == null) {
                    INSTANCE = DynamicAdsManager(application, adsDisabled)
                    INSTANCE?.initialize()
                }
            }
        }

        @JvmStatic
        fun getInstance(): DynamicAdsManager {
            return INSTANCE ?: throw IllegalStateException("DynamicAdsManager must be initialized first")
        }
    }

    /**
     * Check if the app is running in debug mode
     */
    private fun isDebugBuild(): Boolean {
        return try {
            val buildConfigClass = Class.forName("${application.packageName}.BuildConfig")
            val field = buildConfigClass.getField("DEBUG")
            field.getBoolean(null)
        } catch (e: Exception) {
            try {
                (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            } catch (e2: Exception) {
                Log.w(TAG, "Could not determine debug mode, assuming release mode", e2)
                false
            }
        }
    }

    /**
     * Check if ads should be disabled completely (e.g., due to Admob limitations)
     * This can be controlled by a build config field or other mechanism
     */
    private fun areAdsDisabled(): Boolean {
        return try {
            val buildConfigClass = Class.forName("${application.packageName}.BuildConfig")
            val field = buildConfigClass.getField("ADS_DISABLED")
            field.getBoolean(null)
        } catch (e: Exception) {
            false
        }
    }

    private fun getFallbackConfigFromBuildConfig(): AdConfigSet? {
        return try {
            val buildConfigClass = Class.forName("${application.packageName}.BuildConfig")
            val appOpen = buildConfigClass.getField("APP_OPEN_AD_ID").get(null) as? String
            val banner = buildConfigClass.getField("BANNER_AD_ID").get(null) as? String
            val interstitial = buildConfigClass.getField("INTERSTITIAL_AD_ID").get(null) as? String
            val sku = (buildConfigClass.getField("REMOVE_ADS_SKU").get(null) as? String) ?: "remove_ads_sku"

            if (!banner.isNullOrBlank() && !interstitial.isNullOrBlank()) {
                AdConfigSet(
                    appOpenAdId = appOpen ?: "",
                    bannerAdId = banner,
                    interstitialAdId = interstitial,
                    removeAdsSku = sku,
                    priority = 999,
                    isActive = true
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "No fallback ad IDs available in BuildConfig", e)
            null
        }
    }

    fun initialize() {
        scope.launch {
            try {
                val isAdsActuallyDisabled = adsDisabled || firebaseAdConfig.isAdsDisabled()

                if (isAdsActuallyDisabled) {
                    // Ads are disabled, don't initialize monetization manager or Firebase config
                    Log.w(TAG, "⚠️  ADS DISABLED: Not initializing ad system due to Admob limitations")
                    Log.i(TAG, "🚫 AD SYSTEM DISABLED - No ads will be shown")
                    Log.i(TAG, "📋 Skipping Firebase Remote Config initialization")
                    _isInitialized.value = true
                    return@launch
                }

                // Check if we're in debug mode
                val isDebugMode = isDebugBuild()

                if (isDebugMode) {
                    // Use test ad IDs in debug mode
                    Log.i(TAG, "🧪 DEBUG MODE: Using test ad IDs")
                    val testAdsConfig = AdsConfig(
                        appOpenAdUnitId = "ca-app-pub-3940256099942544/9257395921", // Test App Open Ad ID
                        defaultBannerAdUnitId = "ca-app-pub-3940256099942544/6300978111", // Test Banner Ad ID
                        defaultInterstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712", // Test Interstitial Ad ID
                        removeAdsSku = "android.test.purchased" // Test SKU
                    )

                    Log.i(TAG, "🚀 Initializing MonetizationManager with TEST config...")
                    MonetizationManager.initialize(application, testAdsConfig)
                    monetizationManager = MonetizationManager.getInstance()

                    Log.i(TAG, "✅ MonetizationManager initialized with TEST ads successfully!")
                    Log.i(TAG, "🧪 Test App Open Ad ID: ${testAdsConfig.appOpenAdUnitId}")
                    Log.i(TAG, "🧪 Test Banner Ad ID: ${testAdsConfig.defaultBannerAdUnitId}")
                    Log.i(TAG, "🧪 Test Interstitial Ad ID: ${testAdsConfig.defaultInterstitialAdUnitId}")

                    Log.i(TAG, "🎉 TEST ADS SYSTEM READY - Using AdMob test ads")
                } else {
                    // Initialize Firebase Remote Config
                    val firebaseInitialized = firebaseAdConfig.initialize(adsDisabled)
                    if (!firebaseInitialized) {
                        Log.w(TAG, "Firebase Remote Config initialization failed, checking fallback configs")
                    }

                    // Get the best available ad config from Firebase Remote Config or BuildConfig fallback
                    val bestConfig = firebaseAdConfig.getBestAdConfig() ?: getFallbackConfigFromBuildConfig()

                    if (bestConfig != null) {
                        _currentAdConfig.value = bestConfig

                        // Log config info
                        Log.i(TAG, "📱 App Open Ad ID: ${bestConfig.appOpenAdId}")
                        Log.i(TAG, "📱 Banner Ad ID: ${bestConfig.bannerAdId}")
                        Log.i(TAG, "📱 Interstitial Ad ID: ${bestConfig.interstitialAdId}")
                        Log.i(TAG, "🎯 Priority: ${bestConfig.priority}")
                        Log.i(TAG, "💡 Status: ${if (bestConfig.isActive) "ACTIVE" else "INACTIVE"}")

                        // Initialize MonetizationManager with the resolved config
                        val adsConfig = AdsConfig(
                            appOpenAdUnitId = bestConfig.appOpenAdId,
                            defaultBannerAdUnitId = bestConfig.bannerAdId,
                            defaultInterstitialAdUnitId = bestConfig.interstitialAdId,
                            removeAdsSku = bestConfig.removeAdsSku
                        )

                        Log.i(TAG, "🚀 Initializing MonetizationManager with dynamic config...")
                        MonetizationManager.initialize(application, adsConfig)
                        monetizationManager = MonetizationManager.getInstance()

                        Log.i(TAG, "✅ MonetizationManager initialized successfully!")
                        Log.i(TAG, "🎉 ADS SYSTEM READY")
                    } else {
                        Log.w(TAG, "⚠️ No active ad configs found from Firebase Remote Config or BuildConfig")
                    }
                }

                _isInitialized.value = true

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize DynamicAdsManager", e)
                _isInitialized.value = true // Mark as initialized even if failed to avoid infinite loops
            }
        }
    }

    suspend fun switchToNextAdConfig(): Boolean {
        // If ads are disabled, we can't switch configs
        val isAdsActuallyDisabled = adsDisabled || firebaseAdConfig.isAdsDisabled()
        if (isAdsActuallyDisabled) {
            Log.w(TAG, "⚠️  ADS DISABLED: Cannot switch ad configs when ads are disabled")
            return false
        }

        // In debug mode, we don't switch ad configs since we're using fixed test ad IDs
        if (isDebugBuild()) {
            Log.w(TAG, "⚠️  DEBUG MODE: Cannot switch ad configs in debug mode (using fixed test ads)")
            return false
        }

        return try {
            val currentConfig = _currentAdConfig.value
            if (currentConfig != null) {
                // Mark current config as failed
                firebaseAdConfig.markAdConfigAsFailed(currentConfig.appOpenAdId)
            }

            // Get next best config
            val nextConfig = firebaseAdConfig.getBestAdConfig()
            if (nextConfig != null && nextConfig.appOpenAdId != currentConfig?.appOpenAdId) {
                _currentAdConfig.value = nextConfig

                // Reinitialize MonetizationManager with new config
                val adsConfig = AdsConfig(
                    appOpenAdUnitId = nextConfig.appOpenAdId,
                    defaultBannerAdUnitId = nextConfig.bannerAdId,
                    defaultInterstitialAdUnitId = nextConfig.interstitialAdId,
                    removeAdsSku = nextConfig.removeAdsSku
                )

                // Release old manager and initialize new one
                monetizationManager?.release()
                MonetizationManager.initialize(application, adsConfig)
                monetizationManager = MonetizationManager.getInstance()

                true
            } else {
                Log.w(TAG, "No alternative ad config available")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch ad config", e)
            false
        }
    }

    suspend fun refreshConfigs(): Boolean {
        // If ads are disabled, we can't refresh configs
        val isAdsActuallyDisabled = adsDisabled || firebaseAdConfig.isAdsDisabled()
        if (isAdsActuallyDisabled) {
            Log.w(TAG, "⚠️  ADS DISABLED: Cannot refresh configs when ads are disabled")
            return false
        }

        // In debug mode, we don't refresh configs since we're using fixed test ad IDs
        if (isDebugBuild()) {
            Log.w(TAG, "⚠️  DEBUG MODE: Cannot refresh configs in debug mode (using fixed test ads)")
            return false
        }

        return try {
            Log.d(TAG, "Refreshing ad configs...")
            val refreshed = firebaseAdConfig.initialize(adsDisabled)
            if (refreshed) {
                val bestConfig = firebaseAdConfig.getBestAdConfig()
                if (bestConfig != null && bestConfig.appOpenAdId != _currentAdConfig.value?.appOpenAdId) {
                    _currentAdConfig.value = bestConfig
                    Log.d(TAG, "Refreshed to new config: ${bestConfig.appOpenAdId}")
                }
            }
            refreshed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh configs", e)
            false
        }
    }

    suspend fun resetFailedConfigs() {
        // If ads are disabled, we don't need to reset failed configs
        val isAdsActuallyDisabled = adsDisabled || firebaseAdConfig.isAdsDisabled()
        if (isAdsActuallyDisabled) {
            Log.w(TAG, "⚠️  ADS DISABLED: No need to reset failed configs when ads are disabled")
            return
        }

        try {
            firebaseAdConfig.resetFailedAdConfigs()
            Log.d(TAG, "Reset failed ad configs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset failed configs", e)
        }
    }

    fun getMonetizationManager(): MonetizationManager? {
        // If ads are disabled, return null
        val isAdsActuallyDisabled = adsDisabled || firebaseAdConfig.isAdsDisabled()
        if (isAdsActuallyDisabled) {
            Log.w(TAG, "⚠️  ADS DISABLED: MonetizationManager not available when ads are disabled")
            return null
        }
        return monetizationManager
    }

    fun getCurrentConfig(): AdConfigSet? {
        // If ads are disabled, return null
        val isAdsActuallyDisabled = adsDisabled || firebaseAdConfig.isAdsDisabled()
        if (isAdsActuallyDisabled) {
            Log.w(TAG, "⚠️  ADS DISABLED: No current config when ads are disabled")
            return null
        }
        return _currentAdConfig.value
    }

    fun getAllAvailableConfigs(): List<AdConfigSet> {
        // If ads are disabled, return empty list
        val isAdsActuallyDisabled = adsDisabled || firebaseAdConfig.isAdsDisabled()
        if (isAdsActuallyDisabled) {
            Log.w(TAG, "⚠️  ADS DISABLED: No available configs when ads are disabled")
            return emptyList()
        }
        return firebaseAdConfig.getActiveAdConfigs()
    }

    fun isConfigHealthy(): Boolean {
        // If ads are disabled, return false
        val isAdsActuallyDisabled = adsDisabled || firebaseAdConfig.isAdsDisabled()
        if (isAdsActuallyDisabled) {
            Log.w(TAG, "⚠️  ADS DISABLED: Config not healthy when ads are disabled")
            return false
        }
        val config = _currentAdConfig.value
        return config != null && config.isActive
    }
}
