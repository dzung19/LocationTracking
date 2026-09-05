package com.dzung.runner.locationtracker

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.daumo.ads.DynamicAdsManager
import com.dzung.runner.locationtracker.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LocationTrackingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidContext(this@LocationTrackingApp)
            modules(appModule)
        }
        if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isMainProcess()
            } else {
                TODO("VERSION.SDK_INT < P")
            }
        ) {
            try {
                // Check if ads should be disabled
                val adsDisabled = try {
                    BuildConfig.ADS_DISABLED
                } catch (e: Exception) {
                    Log.e("RingtoneApplication", "Error reading BuildConfig.ADS_DISABLED", e)
                    false // Default to false if we can't determine the value
                }

                DynamicAdsManager.initialize(this, adsDisabled)
            } catch (e: Exception) {
                Log.e("DynamicAdsManager", "init error", e)
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.P)
    private fun isMainProcess(): Boolean {
        return applicationInfo.packageName == getProcessName()
    }
}