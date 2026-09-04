package com.daumo.ads

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import androidx.core.view.contains

class BannerAdManager {
    private var adView: AdView? = null
    private val context: Context
    private val adUnitId: String
    var isAdLoaded: Boolean = false
        private set
    private var adaptiveBannerEnabled = true

    interface BannerAdListener {
        fun onAdLoaded()
        fun onAdFailedToLoad(adError: LoadAdError?)
        fun onAdOpened()
        fun onAdClicked()
        fun onAdClosed()
    }

    private var bannerAdListener: BannerAdListener? = null

    constructor(context: Context) {
        this.context = context
        this.adUnitId = "" // No default test ad ID - must be provided via Firebase
    }

    constructor(context: Context, adUnitId: String) {
        this.context = context
        this.adUnitId = adUnitId
    }

    fun setBannerAdListener(listener: BannerAdListener?) {
        this.bannerAdListener = listener
    }

    fun setAdaptiveBannerEnabled(enabled: Boolean) {
        this.adaptiveBannerEnabled = enabled
    }

    fun loadAd(activity: Activity, adContainer: ViewGroup) {
        AdsLogger.adLoadStart("Banner", adUnitId)
        AdsLogger.d("Container: ${adContainer.javaClass.simpleName}")
        AdsLogger.d("Adaptive Banner Enabled: $adaptiveBannerEnabled")
        
        if (adView != null && adContainer.contains(adView!!)) {
            AdsLogger.w("Destroying existing ad view")
            destroy()
        }

        try {
            adView = AdView(context)
            adView!!.adUnitId = adUnitId
            AdsLogger.d("AdView created with unit ID: $adUnitId")

            val adSize = if (adaptiveBannerEnabled) getAdaptiveAdSize(activity) else AdSize.BANNER
            adView!!.setAdSize(adSize)
            AdsLogger.d("Ad size set: ${adSize.width}x${adSize.height}")

            adContainer.removeAllViews()
            adContainer.addView(adView)
            AdsLogger.d("AdView added to container")

            adView!!.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    AdsLogger.adLoadSuccess("Banner")
                    isAdLoaded = true
                    if (bannerAdListener != null) {
                        bannerAdListener!!.onAdLoaded()
                    }
                    adContainer.visibility = View.VISIBLE
                    AdsLogger.d("Banner container set to VISIBLE")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    AdsLogger.adLoadFailed("Banner", loadAdError.code, loadAdError.message)
                    AdsLogger.d("Error Domain: ${loadAdError.domain}")
                    AdsLogger.d("Response Info: ${loadAdError.responseInfo}")
                    isAdLoaded = false
                    if (bannerAdListener != null) {
                        bannerAdListener!!.onAdFailedToLoad(loadAdError)
                    }
                    adContainer.visibility = View.GONE
                    AdsLogger.w("Banner container set to GONE")
                }

                override fun onAdOpened() {
                    super.onAdOpened()
                    AdsLogger.adEvent("Banner", "opened")
                    if (bannerAdListener != null) {
                        bannerAdListener!!.onAdOpened()
                    }
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    AdsLogger.adEvent("Banner", "clicked")
                    if (bannerAdListener != null) {
                        bannerAdListener!!.onAdClicked()
                    }
                }

                override fun onAdClosed() {
                    super.onAdClosed()
                    AdsLogger.adEvent("Banner", "closed")
                    if (bannerAdListener != null) {
                        bannerAdListener!!.onAdClosed()
                    }
                }
            }

            val adRequest = AdRequest.Builder().build()
            AdsLogger.d("Loading banner ad with request...")
            adView!!.loadAd(adRequest)
            
        } catch (e: Exception) {
            AdsLogger.e("Exception in BannerAdManager.loadAd()", e)
            isAdLoaded = false
            adContainer.visibility = View.GONE
        }
    }
    private fun getAdaptiveAdSize(activity: Activity): AdSize {
        val display = activity.windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)

        val widthPixels = outMetrics.widthPixels.toFloat()
        val density = outMetrics.density

        val adWidth = (widthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
    }

    fun pause() {
        if (adView != null) {
            adView!!.pause()
        }
    }

    fun resume() {
        if (adView != null) {
            adView!!.resume()
        }
    }

    fun destroy() {
        if (adView != null) {
            if (adView!!.parent is ViewGroup) {
                (adView!!.parent as ViewGroup).removeView(adView)
            }
            adView!!.destroy()
            adView = null
        }
        isAdLoaded = false
        bannerAdListener = null
    }

}
