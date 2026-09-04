package com.daumo.ads

data class AdsConfig(
    val appOpenAdUnitId: String?,
    val defaultBannerAdUnitId: String,
    val defaultInterstitialAdUnitId: String,
    val removeAdsSku: String
)
