package com.daumo.ads

import android.app.Activity
import android.content.Context
import android.util.Log.*
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import java.util.concurrent.ConcurrentHashMap

class BillingManager(
    private val context: Context,
    private val inAppProductIds: List<String> = BillingConstants.ALL_INAPP_SKUS,
    private val subscriptionIds: List<String> = emptyList(),
    private val onUserPurchasedRemoveAds: () -> Unit = {},
    private val onBillingSetupFailed: (() -> Unit)? = null,
    private val onPurchaseFailed: ((billingResult: BillingResult) -> Unit)? = null
) {
    private lateinit var billingClient: BillingClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isUserPremium = MutableStateFlow(false)
    val isUserPremium = _isUserPremium.asStateFlow()

    private val _isAdsRemoved = MutableStateFlow(false)
    val isAdsRemoved = _isAdsRemoved.asStateFlow()

    private val _isWatermarkRemoved = MutableStateFlow(false)
    val isWatermarkRemoved = _isWatermarkRemoved.asStateFlow()

    private val _purchasedProducts = MutableStateFlow<Set<String>>(emptySet())
    val purchasedProducts = _purchasedProducts.asStateFlow()

    private val _productDetailsMap = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetailsMap = _productDetailsMap.asStateFlow()

    private val _productPrices = MutableStateFlow<Map<String, String>>(emptyMap())
    val productPrices = _productPrices.asStateFlow()

    private val rawProductDetailsMap = ConcurrentHashMap<String, ProductDetails>()

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                i(TAG, "User cancelled the purchase flow.")
            } else {
                e(TAG, "Purchase error. Response Code: ${billingResult.responseCode}, Debug message: ${billingResult.debugMessage}")
                billingResult.responseCode.let { subCode ->
                    e(TAG, "Purchase error Sub Response Code: $subCode")
                }
                onPurchaseFailed?.invoke(billingResult)
            }
        }

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .enablePrepaidPlans() // Required for subscription support
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(pendingPurchasesParams)
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    i(TAG, "Billing Client Setup Finished successfully.")
                    scope.launch {
                        queryProductDetails()
                        querySubscriptionProductDetails()
                        queryExistingPurchases()
                    }
                } else {
                    e(TAG, "Billing Client Setup Failed. Error: ${billingResult.debugMessage}, Response Code: ${billingResult.responseCode}")
                    onBillingSetupFailed?.invoke()
                }
            }

            override fun onBillingServiceDisconnected() {
                w(TAG, "Billing Service Disconnected. Auto-reconnection will occur on demand.")
            }
        })
    }

    suspend fun queryProductDetails() {
        if (!billingClient.isReady) {
            e(TAG, "queryProductDetails: BillingClient is not ready.")
            return
        }

        // Query all registered INAPP products (Remove Ads, Remove Watermark, Pro Bundle)
        val inAppProductList = inAppProductIds.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(inAppProductList)
            .build()

        i(TAG, "Querying INAPP products: $inAppProductIds")
        val result = billingClient.queryProductDetails(inAppParams)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val prices = mutableMapOf<String, String>()
            result.productDetailsList?.forEach { details ->
                rawProductDetailsMap[details.productId] = details
                details.oneTimePurchaseOfferDetails?.formattedPrice?.let { p ->
                    prices[details.productId] = p
                }
            }
            _productDetailsMap.value = rawProductDetailsMap.toMap()
            if (prices.isNotEmpty()) {
                _productPrices.value = prices
            }
            i(TAG, "INAPP query OK. Found ${result.productDetailsList?.size} products: ${result.productDetailsList?.map { it.productId }}")
        } else {
            e(TAG, "INAPP query FAILED. Response Code: ${result.billingResult.responseCode}, Debug Message: ${result.billingResult.debugMessage}")
        }
    }

    private suspend fun querySubscriptionProductDetails() {
        if (subscriptionIds.isNotEmpty()) {
            val subsProductList = subscriptionIds.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }

            val subsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(subsProductList)
                .build()

            i(TAG, "Querying SUBS products: $subscriptionIds")
            val subsResult = billingClient.queryProductDetails(subsParams)
            if (subsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                subsResult.productDetailsList?.forEach { details ->
                    rawProductDetailsMap[details.productId] = details
                    i(TAG, "SUBS found: ${details.productId}, offers: ${details.subscriptionOfferDetails?.size ?: 0}")
                }
                _productDetailsMap.value = rawProductDetailsMap.toMap()
                i(TAG, "SUBS query OK. Found ${subsResult.productDetailsList?.size} products: ${subsResult.productDetailsList?.map { it.productId }}")
            } else {
                e(TAG, "SUBS query FAILED. Response Code: ${subsResult.billingResult.responseCode}, Debug Message: ${subsResult.billingResult.debugMessage}")
            }
        }
    }

    suspend fun queryExistingPurchases() {
        if (!billingClient.isReady) {
            e(TAG, "queryExistingPurchases: BillingClient is not ready.")
            return
        }

        val purchasedSet = mutableSetOf<String>()

        // Query INAPP purchases
        val inAppParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val inAppResult = billingClient.queryPurchasesAsync(inAppParams)
        if (inAppResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            inAppResult.purchasesList.forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    purchasedSet.addAll(purchase.products)
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase.purchaseToken, purchase.products)
                    }
                }
            }
            i(TAG, "INAPP purchases found: $purchasedSet")
        } else {
            e(TAG, "Error querying INAPP purchases: ${inAppResult.billingResult.debugMessage}")
        }

        // Query SUBS purchases
        if (subscriptionIds.isNotEmpty()) {
            val subsParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            val subsResult = billingClient.queryPurchasesAsync(subsParams)
            if (subsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                subsResult.purchasesList.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        purchasedSet.addAll(purchase.products)
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase.purchaseToken, purchase.products)
                        }
                    }
                }
                i(TAG, "SUBS purchases found: ${subsResult.purchasesList.flatMap { it.products }}")
            } else {
                e(TAG, "Error querying SUBS purchases: ${subsResult.billingResult.debugMessage}")
            }
        }

        updateEntitlements(purchasedSet)
    }

    fun restorePurchases(onComplete: (hasPurchases: Boolean) -> Unit) {
        scope.launch {
            queryExistingPurchases()
            withContext(Dispatchers.Main) {
                onComplete(_purchasedProducts.value.isNotEmpty())
            }
        }
    }

    fun queryPurchasesAsync() {
        scope.launch {
            queryExistingPurchases()
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String = BillingConstants.SKU_REMOVE_ADS) {
        if (!billingClient.isReady) {
            e(TAG, "launchPurchaseFlow: BillingClient is not ready.")
            onPurchaseFailed?.invoke(BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE).build())
            return
        }

        val productDetails = rawProductDetailsMap[productId]
        if (productDetails == null) {
            e(TAG, "launchPurchaseFlow: Product details for $productId not available. Available: ${rawProductDetailsMap.keys}. Querying again...")
            onPurchaseFailed?.invoke(BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE).build())
            return
        }

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        // Subscriptions REQUIRE an offerToken
        if (productDetails.productType == BillingClient.ProductType.SUBS) {
            val offerToken = productDetails.subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken

            if (offerToken == null) {
                e(TAG, "launchPurchaseFlow: No offer token found for subscription $productId")
                onPurchaseFailed?.invoke(BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE).build())
                return
            }

            productDetailsParamsBuilder.setOfferToken(offerToken)
            i(TAG, "Using offerToken for subscription $productId")
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        val launchResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            e(TAG, "Failed to launch billing flow. Error: ${launchResult.debugMessage}, Response Code: ${launchResult.responseCode}")
            onPurchaseFailed?.invoke(launchResult)
        } else {
            i(TAG, "Billing flow launched successfully for $productId.")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase.purchaseToken, purchase.products)
            } else {
                val newSet = _purchasedProducts.value + purchase.products
                updateEntitlements(newSet)
                i(TAG, "Purchase for ${purchase.products} already acknowledged.")
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            i(TAG, "Purchase is PENDING for: ${purchase.products.joinToString()}. Inform user.")
        } else if (purchase.purchaseState == Purchase.PurchaseState.UNSPECIFIED_STATE) {
            e(TAG, "Purchase state is UNSPECIFIED for: ${purchase.products.joinToString()}")
        }
    }

    private fun acknowledgePurchase(purchaseToken: String, products: List<String>) {
        if (!billingClient.isReady) {
            e(TAG, "acknowledgePurchase: BillingClient is not ready.")
            return
        }
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val newSet = _purchasedProducts.value + products
                updateEntitlements(newSet)
                i(TAG, "Purchase acknowledged successfully for $products.")
            } else {
                e(TAG, "Failed to acknowledge purchase. Error: ${billingResult.debugMessage}, Response Code: ${billingResult.responseCode}")
            }
        }
    }

    private fun updateEntitlements(purchasedSet: Set<String>) {
        val hadAdsRemoved = _isAdsRemoved.value
        val hasAdsRemoved = purchasedSet.contains(BillingConstants.SKU_REMOVE_ADS) ||
                purchasedSet.contains(BillingConstants.SKU_REMOVE_ADS_LEGACY) ||
                purchasedSet.contains(BillingConstants.SKU_PREMIUM_BUNDLE) ||
                purchasedSet.contains(BillingConstants.SKU_TEST_PURCHASED)

        val hasWatermarkRemoved = purchasedSet.contains(BillingConstants.SKU_REMOVE_WATERMARK) ||
                purchasedSet.contains(BillingConstants.SKU_PREMIUM_BUNDLE) ||
                purchasedSet.contains(BillingConstants.SKU_TEST_PURCHASED)

        val hasProBundle = purchasedSet.contains(BillingConstants.SKU_PREMIUM_BUNDLE) ||
                purchasedSet.contains(BillingConstants.SKU_TEST_PURCHASED) ||
                (hasAdsRemoved && hasWatermarkRemoved)

        _purchasedProducts.value = purchasedSet
        _isAdsRemoved.value = hasAdsRemoved
        _isWatermarkRemoved.value = hasWatermarkRemoved
        _isUserPremium.value = hasProBundle

        if (hasAdsRemoved && !hadAdsRemoved) {
            onUserPurchasedRemoveAds()
        }
        i(TAG, "Entitlements updated: adsRemoved=$hasAdsRemoved, watermarkRemoved=$hasWatermarkRemoved, isPremium=$hasProBundle, products=$purchasedSet")
    }

    fun destroy() {
        if (::billingClient.isInitialized && billingClient.isReady) {
            billingClient.endConnection()
        }
        d(TAG, "BillingManager destroyed.")
    }

    companion object {
        private const val TAG = "BillingManager"
    }
}
