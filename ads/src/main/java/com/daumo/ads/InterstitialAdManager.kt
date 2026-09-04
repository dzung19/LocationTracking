package com.daumo.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class InterstitialAdManager(context: Context, val adUnitId: String) {
    private var mInterstitialAd: InterstitialAd? = null
    private var isLoadingAd = false
    private val context: Context = context.applicationContext
    fun interface OnAdClosedListener {
        fun onAdClosed()
    }

    private var adClosedListener: OnAdClosedListener? = null

    fun setOnAdClosedListener(listener: OnAdClosedListener?) {
        this.adClosedListener = listener
    }

    fun loadAd() {
        if (isLoadingAd || mInterstitialAd != null) {
            return
        }

        isLoadingAd = true
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    isLoadingAd = false
                    setupFullScreenContentCallback()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    mInterstitialAd = null
                    isLoadingAd = false
                }
            }
        )
    }

    private fun setupFullScreenContentCallback() {
        if (mInterstitialAd == null) {
            return
        }

        mInterstitialAd!!.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                Log.d(TAG, "Ad was clicked.")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad dismissed fullscreen content.")
                mInterstitialAd = null
                if (adClosedListener != null) {
                    adClosedListener!!.onAdClosed()
                }
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Ad failed to show fullscreen content: " + adError.message)
                mInterstitialAd = null
                if (adClosedListener != null) {
                    adClosedListener!!.onAdClosed()
                }
            }

            override fun onAdImpression() {
                Log.d(TAG, "Ad recorded an impression.")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad showed fullscreen content.")
            }
        }
    }

    @JvmOverloads
    fun showAd(activity: Activity, specificListener: OnAdClosedListener? = null) {
        if (specificListener != null) {
            this.adClosedListener = specificListener
        }

        if (mInterstitialAd != null) {
            mInterstitialAd!!.show(activity)
        } else {
            if (this.adClosedListener != null) {
                this.adClosedListener!!.onAdClosed()
            }
            loadAd()
        }
    }

    val isAdLoaded: Boolean
        get() = mInterstitialAd != null

    fun destroy() {
        if (mInterstitialAd != null) {
            mInterstitialAd!!.fullScreenContentCallback = null
            mInterstitialAd = null
        }
        adClosedListener = null
    }

    companion object {
        private const val TAG = "InterstitialAdManager"
    }
}
