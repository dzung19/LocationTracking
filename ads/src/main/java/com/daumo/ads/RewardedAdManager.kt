package com.daumo.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class RewardedAdManager(context: Context, val adUnitId: String) {
    private var mRewardedAd: RewardedAd? = null
    private var isLoadingAd = false
    private val context: Context = context.applicationContext

    fun interface OnAdClosedListener {
        fun onAdClosed(rewardEarned: Boolean)
    }

    private var adClosedListener: OnAdClosedListener? = null
    private var userEarnedReward = false

    val isAdLoaded: Boolean
        get() = mRewardedAd != null

    companion object {
        private const val TAG = "RewardedAdManager"
    }

    fun setOnAdClosedListener(listener: OnAdClosedListener?) {
        this.adClosedListener = listener
    }

    fun loadAd() {
        if (isLoadingAd || mRewardedAd != null || adUnitId.isBlank()) {
            return
        }

        isLoadingAd = true
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded successfully.")
                    mRewardedAd = rewardedAd
                    isLoadingAd = false
                    setupFullScreenContentCallback()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                    mRewardedAd = null
                    isLoadingAd = false
                }
            }
        )
    }

    private fun setupFullScreenContentCallback() {
        mRewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                Log.d(TAG, "Rewarded ad was clicked.")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed fullscreen content.")
                mRewardedAd = null
                adClosedListener?.onAdClosed(userEarnedReward)
                userEarnedReward = false
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Rewarded ad failed to show fullscreen content: ${adError.message}")
                mRewardedAd = null
                adClosedListener?.onAdClosed(false)
                userEarnedReward = false
            }

            override fun onAdImpression() {
                Log.d(TAG, "Rewarded ad recorded an impression.")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded ad showed fullscreen content.")
            }
        }
    }

    fun showAd(
        activity: Activity,
        onUserEarnedReward: () -> Unit,
        specificListener: OnAdClosedListener? = null
    ) {
        if (specificListener != null) {
            this.adClosedListener = specificListener
        }
        userEarnedReward = false

        if (mRewardedAd != null) {
            mRewardedAd!!.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.type} = ${rewardItem.amount}")
                userEarnedReward = true
                onUserEarnedReward()
            }
        } else {
            Log.w(TAG, "Rewarded ad is not ready yet.")
            adClosedListener?.onAdClosed(false)
            loadAd()
        }
    }

    fun destroy() {
        mRewardedAd = null
        adClosedListener = null
        isLoadingAd = false
    }
}
