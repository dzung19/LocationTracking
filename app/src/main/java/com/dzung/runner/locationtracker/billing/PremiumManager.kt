package com.dzung.runner.locationtracker.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.daumo.ads.BillingConstants
import com.daumo.ads.BillingManager
import com.daumo.ads.DynamicAdsManager
import com.dzung.runner.locationtracker.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class PremiumManager(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val TAG = "PremiumManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _purchasedSkus = MutableStateFlow<Set<String>>(emptySet())
    val purchasedSkus: StateFlow<Set<String>> = _purchasedSkus.asStateFlow()

    private val _productPrices = MutableStateFlow<Map<String, String>>(emptyMap())
    val productPrices: StateFlow<Map<String, String>> = _productPrices.asStateFlow()

    val isAdsRemoved: StateFlow<Boolean> = _purchasedSkus.map { skus ->
        skus.contains(BillingConstants.SKU_REMOVE_ADS) ||
                skus.contains(BillingConstants.SKU_REMOVE_ADS_LEGACY) ||
                skus.contains(BillingConstants.SKU_PREMIUM_BUNDLE) ||
                skus.contains(BillingConstants.SKU_TEST_PURCHASED)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val isWatermarkRemoved: StateFlow<Boolean> = _purchasedSkus.map { skus ->
        skus.contains(BillingConstants.SKU_REMOVE_WATERMARK) ||
                skus.contains(BillingConstants.SKU_PREMIUM_BUNDLE) ||
                skus.contains(BillingConstants.SKU_TEST_PURCHASED)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val isProUser: StateFlow<Boolean> = _purchasedSkus.map { skus ->
        skus.contains(BillingConstants.SKU_PREMIUM_BUNDLE) ||
                skus.contains(BillingConstants.SKU_TEST_PURCHASED) ||
                (skus.contains(BillingConstants.SKU_REMOVE_ADS) && skus.contains(BillingConstants.SKU_REMOVE_WATERMARK))
    }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        // 1. First hydrate from local DataStore cache for instant offline responsiveness
        scope.launch {
            userPreferencesRepository.cachedPurchasesFlow.collectLatest { cached ->
                if (cached.isNotEmpty() && _purchasedSkus.value.isEmpty()) {
                    _purchasedSkus.value = cached
                    Log.d(TAG, "Hydrated entitlements from local cache: $cached")
                }
            }
        }

        // 2. Bind with BillingManager when ready
        scope.launch {
            bindBillingManager()
        }
    }

    private suspend fun bindBillingManager() {
        var attempts = 0
        var billing: BillingManager? = null
        while (attempts < 10) {
            billing = try {
                DynamicAdsManager.getInstance().getMonetizationManager()?.billing
            } catch (e: Exception) {
                null
            }
            if (billing != null) break
            delay(300.milliseconds)
            attempts++
        }

        if (billing != null) {
            val activeBilling = billing
            // Collect remote purchases and sync with DataStore
            scope.launch {
                activeBilling.purchasedProducts.collectLatest { remotePurchases ->
                    val merged = _purchasedSkus.value + remotePurchases
                    _purchasedSkus.value = merged
                    userPreferencesRepository.saveCachedPurchases(merged)
                    Log.d(TAG, "Synced remote purchases from BillingClient: $merged")
                }
            }

            // Collect localized prices from BillingManager
            scope.launch {
                activeBilling.productPrices.collectLatest { priceMap ->
                    if (priceMap.isNotEmpty()) {
                        _productPrices.value = priceMap
                        Log.d(TAG, "Loaded localized prices: $priceMap")
                    }
                }
            }
        } else {
            Log.w(TAG, "BillingManager could not be bound after attempts.")
        }
    }

    fun launchPurchase(activity: Activity, sku: String) {
        val monetization = try {
            DynamicAdsManager.getInstance().getMonetizationManager()
        } catch (e: Exception) {
            null
        }

        if (monetization != null) {
            monetization.launchPurchaseFlow(activity, sku)
        } else {
            Log.e(TAG, "Cannot launch purchase: MonetizationManager not available")
        }
    }

    fun restorePurchases(onResult: (Boolean) -> Unit) {
        val monetization = try {
            DynamicAdsManager.getInstance().getMonetizationManager()
        } catch (e: Exception) {
            null
        }

        val billing = monetization?.billing
        if (billing != null) {
            billing.restorePurchases { hasPurchases ->
                val allPurchased = _purchasedSkus.value + billing.purchasedProducts.value
                _purchasedSkus.value = allPurchased
                scope.launch {
                    userPreferencesRepository.saveCachedPurchases(allPurchased)
                }
                onResult(hasPurchases)
            }
        } else {
            onResult(false)
        }
    }

    fun isProductPurchased(sku: String): Boolean {
        if (isProUser.value) return true
        if (sku == BillingConstants.SKU_REMOVE_ADS && isAdsRemoved.value) return true
        if (sku == BillingConstants.SKU_REMOVE_WATERMARK && isWatermarkRemoved.value) return true
        return _purchasedSkus.value.contains(sku)
    }
}
