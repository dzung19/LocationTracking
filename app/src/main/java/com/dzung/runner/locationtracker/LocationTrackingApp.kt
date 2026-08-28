package com.dzung.runner.locationtracker

import android.app.Application
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
    }
}