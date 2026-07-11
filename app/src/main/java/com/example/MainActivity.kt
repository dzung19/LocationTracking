package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.ui.screens.LocationTrackerApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    // Instantiate ViewModel scoped to this Activity using Koin
    private val locationViewModel: LocationViewModel by viewModel()
    
    private var isServiceBound = false

    // Service Connection handling binder communication
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected: Connected to LocationTrackingService")
            val binder = service as? LocationTrackingService.LocalBinder
            val trackingService = binder?.getService()
            locationViewModel.setService(trackingService)
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected: Disconnected from LocationTrackingService")
            locationViewModel.setService(null)
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                LocationTrackerApp(
                    viewModel = locationViewModel,
                    modifier = Modifier.fillMaxSize(),
                    onStartService = ::startForegroundTrackingService
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Binding to LocationTrackingService")
        val intent = Intent(this, LocationTrackingService::class.java)
        // Bind to service using BIND_AUTO_CREATE so the system spins it up if needed
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isServiceBound) {
            Log.d(TAG, "onStop: Unbinding from LocationTrackingService")
            unbindService(serviceConnection)
            isServiceBound = false
            locationViewModel.setService(null)
        }
    }

    /**
     * Promotes our service to a persistent 'Started' foreground state before binding triggers.
     * This is crucial to ensure that tracking survives when the activity is stopped/paused.
     */
    private fun startForegroundTrackingService() {
        Log.d(TAG, "startForegroundTrackingService: Invoking startForegroundService")
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START_TRACKING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
