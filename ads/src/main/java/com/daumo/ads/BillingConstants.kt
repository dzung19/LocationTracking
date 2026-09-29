package com.daumo.ads

import androidx.annotation.Keep

@Keep
object BillingConstants {
    const val SKU_REMOVE_ADS = "remove_ads"
    const val SKU_REMOVE_WATERMARK = "remove_watermark"
    const val SKU_PREMIUM_BUNDLE = "premium_bundle"

    // Legacy and Google Play test SKUs for backwards compatibility and emulator testing
    const val SKU_REMOVE_ADS_LEGACY = "remove_ads_sku"
    const val SKU_TEST_PURCHASED = "android.test.purchased"

    val ALL_INAPP_SKUS = listOf(
        SKU_REMOVE_ADS,
        SKU_REMOVE_WATERMARK,
        SKU_PREMIUM_BUNDLE,
        SKU_REMOVE_ADS_LEGACY,
        SKU_TEST_PURCHASED
    )
}
