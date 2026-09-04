package com.daumo.ads

import androidx.annotation.Keep

@Keep
data class AdsConfig(
    val appOpenAdUnitId: String?,
    val defaultBannerAdUnitId: String,
    val defaultInterstitialAdUnitId: String,
    val removeAdsSku: String
)
