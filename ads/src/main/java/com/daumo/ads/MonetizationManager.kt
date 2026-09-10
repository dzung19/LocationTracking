package com.daumo.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class MonetizationManager private constructor(
    private val application: Application,
    private val config: AdsConfig
) : DefaultLifecycleObserver {
    private val monetizationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var appOpenAdManager: AppOpenAdManager? = null
    private val bannerAdManagers =
        mutableMapOf<String, BannerAdManager>()
    private val interstitialAdManagers = mutableMapOf<String, InterstitialAdManager>()

    private lateinit var billingManager: BillingManager

    private var _isUserPremium = false
    val isUserPremium: Boolean
        get() = _isUserPremium

    private val TAG = "ADS_DEBUG"


    init {
        Log.i(TAG, "🏗️ MonetizationManager constructor called")
        Log.i(TAG, "🔧 Config appOpenAdId: ${config.appOpenAdUnitId}")
        Log.i(TAG, "🔧 Config bannerAdId: ${config.defaultBannerAdUnitId}")
        
        // Initialize MobileAds
        Log.i(TAG, "🚀 Initializing MobileAds...")
        
        MobileAds.initialize(application) { initializationStatus ->
            val adapterStatus = initializationStatus.adapterStatusMap
            adapterStatus.forEach { (adapterClass, status) ->
                Log.d(TAG, "Adapter status: $adapterClass = ${status.description}")
            }
            Log.i(TAG, "✅ MobileAds initialized with manifest fallback")
        }

        // Initialize components after MobileAds initialization
        initializeComponents()
        
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }
    
    
    private fun initializeComponents() {
        appOpenAdManager = config.appOpenAdUnitId?.takeIf { it.isNotBlank() }?.let { AppOpenAdManager(application, it) }
        
        // Preload App Open Ad if ad unit ID exists
        if (!config.appOpenAdUnitId.isNullOrBlank() && !_isUserPremium) {
            appOpenAdManager?.loadAd()
        }

        billingManager = BillingManager(
            application,
            onUserPurchasedRemoveAds = {
            },
            onBillingSetupFailed = {
            },
            onPurchaseFailed = { billingResult ->
            }
        )

        monetizationScope.launch {
            billingManager.isUserPremium.collectLatest { isPremium ->
                if (_isUserPremium != isPremium) {
                    _isUserPremium = isPremium
                    if (isPremium) {
                        destroyAllAds()
                    } else {
                        enableAdsIfNeeded()
                    }
                }
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        if (owner is ProcessLifecycleOwner) {
            release()
            ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        }
    }

    fun loadAppOpenAd() {
        Log.i(TAG, "🎯 loadAppOpenAd() called")
        Log.i(TAG, "👤 User Premium: $isUserPremium")
        Log.i(TAG, "🔧 App Open Ad Manager: ${appOpenAdManager != null}")
        Log.i(TAG, "🔧 Ad Unit ID: ${config.appOpenAdUnitId}")
        
        if (isUserPremium) {
            Log.w(TAG, "⚠️  User is premium, skipping app open ad")
            return
        }
        appOpenAdManager?.loadAd()
    }

    fun loadBannerAd(
        activity: Activity,
        container: ViewGroup,
        adKey: String = "default_banner",
        listener: BannerAdManager.BannerAdListener? = null
    ) {
        Log.i(TAG, "🎯 loadBannerAd() called with adKey: $adKey")
        Log.i(TAG, "📱 Activity: ${activity.localClassName}")
        Log.i(TAG, "📦 Container: ${container.javaClass.simpleName}")
        Log.i(TAG, "🔧 Ad Unit ID: ${config.defaultBannerAdUnitId}")
        Log.i(TAG, "👤 User Premium: $isUserPremium")
        
        loadBannerAd(
            activity = activity,
            container = container,
            adKey = adKey,
            adUnitId = config.defaultBannerAdUnitId,
            listener = listener
        )
    }

    fun loadBannerAd(
        activity: Activity,
        container: ViewGroup,
        adKey: String,
        adUnitId: String,
        listener: BannerAdManager.BannerAdListener? = null
    ) {
        if (isUserPremium) {
            container.removeAllViews()
            container.visibility = ViewGroup.GONE
            return
        }

        var bannerManager = bannerAdManagers[adKey]
        if (bannerManager == null) {
            bannerManager = BannerAdManager(activity, adUnitId)
            bannerAdManagers[adKey] = bannerManager
        }
        listener?.let { bannerManager.setBannerAdListener(it) }
        bannerManager.loadAd(activity, container)
    }


    fun pauseBannerAd(adKey: String = "default_banner") {
        bannerAdManagers[adKey]?.pause()
    }

    fun resumeBannerAd(adKey: String = "default_banner") {
        bannerAdManagers[adKey]?.resume()
    }

    fun destroyBannerAd(adKey: String = "default_banner") {
        bannerAdManagers.remove(adKey)?.destroy()
    }

    fun loadInterstitialAd(
        context: Context,
        listener: InterstitialAdManager.OnAdClosedListener? = null
    ) {
        Log.i(TAG, "🎯 loadInterstitialAd() called")
        Log.i(TAG, "📱 Context: ${context.javaClass.simpleName}")
        Log.i(TAG, "🔧 Ad Unit ID: ${config.defaultInterstitialAdUnitId}")
        Log.i(TAG, "👤 User Premium: $isUserPremium")
        
        loadInterstitialAd(
            context = context,
            adKey = "default_interstitial",
            adUnitId = config.defaultInterstitialAdUnitId,
            listener = listener
        )
    }

    fun loadInterstitialAd(
        context: Context,
        adKey: String = "default_interstitial",
        adUnitId: String,
        listener: InterstitialAdManager.OnAdClosedListener? = null
    ) {
        if (isUserPremium) {
            interstitialAdManagers.remove(adKey)?.destroy()
            return
        }

        var interstitialManager = interstitialAdManagers[adKey]

        if (interstitialManager == null || interstitialManager.adUnitId != adUnitId) {
            interstitialManager?.destroy()
            interstitialManager = InterstitialAdManager(context.applicationContext, adUnitId)
            interstitialAdManagers[adKey] = interstitialManager
        }

        listener?.let { interstitialManager.setOnAdClosedListener(it) }

        if (!interstitialManager.isAdLoaded) {
            interstitialManager.loadAd()
        }
    }

    fun showInterstitialAd(
        activity: Activity,
        adKey: String = "default_interstitial",
        onAdShownOrNotAvailable: (shown: Boolean) -> Unit
    ) {
        if (isUserPremium) {
            onAdShownOrNotAvailable(false)
            return
        }

        val interstitialManager = interstitialAdManagers[adKey]
        if (interstitialManager != null && interstitialManager.isAdLoaded) {
            interstitialManager.showAd(activity) {
                onAdShownOrNotAvailable(true)
            }
        } else {
            onAdShownOrNotAvailable(false)
            interstitialManager?.loadAd() ?: loadInterstitialAd(
                activity,
                adKey,
                config.defaultInterstitialAdUnitId
            )
        }
    }

    fun isInterstitialAdLoaded(adKey: String = "default_interstitial"): Boolean {
        return if (isUserPremium) false else interstitialAdManagers[adKey]?.isAdLoaded ?: false
    }

    fun launchRemoveAdsPurchaseFlow(activity: Activity) {
        if (isUserPremium) {
            return
        }
        billingManager.launchPurchaseFlow(activity)
    }

    private fun destroyAllAds() {
        appOpenAdManager?.disable()

        bannerAdManagers.keys.toList()
            .forEach { key ->
                bannerAdManagers.remove(key)?.destroy()
            }
        bannerAdManagers.clear()

        interstitialAdManagers.keys.toList().forEach { key ->
            interstitialAdManagers.remove(key)?.destroy()
        }
        interstitialAdManagers.clear()
    }

    private fun enableAdsIfNeeded() {
        appOpenAdManager?.enable()
    }

    fun release() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        appOpenAdManager?.release()

        bannerAdManagers.values.forEach { it.destroy() }
        bannerAdManagers.clear()

        interstitialAdManagers.values.forEach {
            it.destroy()
        }
        interstitialAdManagers.clear()

        billingManager.destroy()

        INSTANCE = null
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        if (!isUserPremium) {
            appOpenAdManager?.enable()
        } else {
            appOpenAdManager?.disable()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: MonetizationManager? = null

        fun initialize(application: Application, config: AdsConfig) {
            synchronized(this) {
                if (INSTANCE == null) {
                    INSTANCE = MonetizationManager(application, config)
                }
            }
        }


        fun getInstance(): MonetizationManager {
            return INSTANCE
                ?: throw IllegalStateException("MonetizationManager must be initialized in the Application class before use.")
        }
    }
}
