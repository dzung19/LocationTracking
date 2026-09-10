package com.dzung.runner.locationtracker

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.daumo.ads.DynamicAdsManager
import com.dzung.runner.locationtracker.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LocationTrackingApp : Application() {
    override fun onCreate() {
        super.onCreate()

        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        } catch (e: Exception) {
            Log.e("LocationTrackingApp", "Error enabling Firebase Crashlytics", e)
        }
        
        startKoin {
            androidContext(this@LocationTrackingApp)
            modules(appModule)
        }

        if (isMainProcess()) {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    // Check if ads should be disabled
                    val adsDisabled = try {
                        BuildConfig.ADS_DISABLED
                    } catch (e: Exception) {
                        Log.e("LocationTrackingApp", "Error reading BuildConfig.ADS_DISABLED", e)
                        false // Default to false if we can't determine the value
                    }

                    DynamicAdsManager.initialize(this@LocationTrackingApp, adsDisabled)
                } catch (e: Exception) {
                    Log.e("DynamicAdsManager", "init error", e)
                }
            }
        }
    }

    private fun isMainProcess(): Boolean {
        return applicationInfo.packageName == getProcessName()
    }
}