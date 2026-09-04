package com.daumo.ads

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import java.util.Date

class AppOpenAdManager : ActivityLifecycleCallbacks, LifecycleObserver {
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false

    private val myApplication: Application
    private var currentActivity: Activity? = null

    private val adUnitId: String
    private var loadTime: Long = 0
    var enabled: Boolean = true
        private set

    interface OnAdClosedListener {
        fun onAdClosed()
    }

    private var onAdClosedListener: OnAdClosedListener? = null

    constructor(application: Application) {
        this.myApplication = application
        this.adUnitId = "" // No default test ad ID - must be provided via Firebase

        this.myApplication.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    constructor(application: Application, adUnitId: String) {
        this.myApplication = application
        this.adUnitId = adUnitId

        this.myApplication.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun setOnAdClosedListener(listener: OnAdClosedListener?) {
        this.onAdClosedListener = listener
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onMoveToForeground() {
        // Show the ad (if available) when the app moves to foreground.
        if (currentActivity != null && !isAdActivity(currentActivity)) {
            showAdIfAvailable(currentActivity!!)
        } else {
            Log.i(TAG, "onMoveToForeground: Activity is not ready to show ad.")
        }
    }
    fun loadAd() {
        if (isLoadingAd || this.isAdAvailable) {
            return
        }

        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            myApplication,
            adUnitId,
            request,
            object : AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = (Date()).time
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    appOpenAd = null
                }
            })
    }

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = (Date()).time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return (dateDifference < (numMilliSecondsPerHour * numHours))
    }

    private val isAdAvailable: Boolean
        get() =
            appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)

    fun showAdIfAvailable(activity: Activity) {
        if (isShowingAd) {
            return
        }

        if (!this.isAdAvailable) {
            if (onAdClosedListener != null) {
                onAdClosedListener!!.onAdClosed()
            }
            loadAd()
            return
        }

        if (isAdActivity(activity)) {
            if (onAdClosedListener != null) {
                onAdClosedListener!!.onAdClosed()
            }
            return
        }

        appOpenAd!!.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                if (onAdClosedListener != null) {
                    onAdClosedListener!!.onAdClosed()
                }
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {

                appOpenAd = null
                isShowingAd = false
                if (onAdClosedListener != null) {
                    onAdClosedListener!!.onAdClosed()
                }
                loadAd()
            }

            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
            }
        }
        appOpenAd!!.show(activity)
    }

    private fun isAdActivity(activity: Activity?): Boolean {
        if (activity == null) return false
        val activityName = activity.javaClass.getName()
        return activityName == "com.google.android.gms.ads.AdActivity"
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityStarted(activity: Activity) {
        if (!isShowingAd) {
            currentActivity = activity
        }
    }

    override fun onActivityStopped(activity: Activity) {
    }

    fun enable() {
        if (enabled) return
        this.enabled = true
        Log.i(TAG, "App Open Ads have been enabled.")
    }

    fun release() {
        this.myApplication.unregisterActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        appOpenAd = null
        currentActivity = null
        isLoadingAd = false
        isShowingAd = false
        onAdClosedListener = null
    }

    fun loadAdOnAppOpen() {
        if (!enabled) {
            return
        }
        if (isLoadingAd || isAdAvailable) {
            return
        }
        Log.i(TAG, "loadAdOnAppOpen: Triggering ad load.")
        loadAd()
    }

    fun disable() {
        if (!enabled) return
        this.enabled = false
    }

    companion object {
        private const val TAG = "AppOpenAdManager"
    }
}
