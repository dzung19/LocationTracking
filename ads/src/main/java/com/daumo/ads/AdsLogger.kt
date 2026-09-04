package com.daumo.ads

import android.util.Log

object AdsLogger {
    private const val TAG = "ADS_DEBUG"
    private val isDebuggable get() = AdsLoggerConfig.isDebugMode

    fun d(message: String) {
        if (isDebuggable) {
        }
    }

    fun i(message: String) {
        if (isDebuggable) {
            Log.i(TAG, message)
        }
    }

    fun w(message: String) {
        if (isDebuggable) {
            Log.w(TAG, message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        // Always log errors in both debug and release for crash tracking
        if (isDebuggable) {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        } else {
            // In release, log minimal error info for crash reporting
            if (throwable != null) {
                Log.e(TAG, "$message - ${throwable.javaClass.simpleName}: ${throwable.message}")
            } else {
                Log.e(TAG, message)
            }
        }
    }

    fun adLoadStart(adType: String, adUnitId: String) {
        if (isDebuggable) {
            i("🎯 Loading $adType ad")
            i("📱 Ad Unit ID: $adUnitId")
        }
    }

    fun adLoadSuccess(adType: String) {
        if (isDebuggable) {
            i("🎉 $adType ad loaded successfully!")
        }
    }

    fun adLoadFailed(adType: String, errorCode: Int, errorMessage: String) {
        e("❌ $adType ad failed to load! Code: $errorCode, Message: $errorMessage")
    }

    fun adEvent(adType: String, event: String) {
        if (isDebuggable) {
            i("$adType ad $event")
        }
    }

    fun firebaseConfigSuccess() {
        if (isDebuggable) {
            i("🔥 FIREBASE CONFIG FETCH SUCCESS!")
        }
    }

    fun firebaseConfigFailed(error: String) {
        e("❌ Firebase config failed: $error")
    }

    fun initializationSuccess(component: String) {
        if (isDebuggable) {
            i("✅ $component initialized successfully!")
        }
    }

    fun initializationFailed(component: String, error: String) {
        e("❌ $component initialization failed: $error")
    }
}
