package com.dzungphung.aimodel.econimical.smartspend.ui.components

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.daumo.ads.DynamicAdsManager
import com.daumo.ads.InterstitialAdManager
import kotlinx.coroutines.delay

@Composable
fun showInterstitialAd(
    showAd: Boolean,
    onAdClosed: () -> Unit
) {
    val context = LocalContext.current
    
    LaunchedEffect(showAd) {
        if (!showAd) return@LaunchedEffect
        
        try {
            Log.d("InterstitialAd", "Starting interstitial ad process")
            // Add a small delay to ensure the DynamicAdsManager is initialized
            delay(100)
            
            val activity = context as? Activity
            if (activity != null) {
                Log.d("InterstitialAd", "Activity found, attempting to get monetization manager")
                // Try to get the monetization manager with a retry mechanism
                var attempts = 0
                while (attempts < 5) {
                    Log.d("InterstitialAd", "Attempt ${attempts + 1} to get monetization manager")
                    val monetizationManager = try {
                        DynamicAdsManager.getInstance().getMonetizationManager()
                    } catch (e: Exception) {
                        Log.e("InterstitialAd", "Error getting monetization manager", e)
                        null
                    }
                    
                    if (monetizationManager != null) {
                        Log.d("InterstitialAd", "Monetization manager found, checking if ad is loaded")
                        val isAdLoaded = monetizationManager.isInterstitialAdLoaded()
                        Log.d("InterstitialAd", "Interstitial ad loaded status: $isAdLoaded")
                        if (isAdLoaded) {
                            Log.d("InterstitialAd", "Interstitial ad is loaded, showing ad")
                            // Show the interstitial ad with proper callback for when it's closed
                            monetizationManager.showInterstitialAd(
                                activity = activity,
                                onAdShownOrNotAvailable = { shown ->
                                    Log.d("InterstitialAd", "onAdShownOrNotAvailable called with shown=$shown")
                                    if (shown) {
                                        Log.d("InterstitialAd", "Interstitial ad was shown, calling onAdClosed()")
                                        onAdClosed()
                                    } else {
                                        Log.d("InterstitialAd", "Interstitial ad not shown, executing action directly")
                                        onAdClosed()
                                    }
                                }
                            )
                        } else {
                            Log.d("InterstitialAd", "Interstitial ad not loaded, loading and executing action directly")
                            // Load the interstitial ad for next time
                            monetizationManager.loadInterstitialAd(
                                context = context,
                                listener = object : InterstitialAdManager.OnAdClosedListener {
                                    override fun onAdClosed() {
                                        Log.d("InterstitialAd", "Interstitial ad closed after loading, executing action")
                                        onAdClosed()
                                    }
                                }
                            )
                            // Execute the action directly since ad is not available yet
                            onAdClosed()
                        }
                        break
                    } else {
                        Log.d("InterstitialAd", "Monetization manager not available, retrying...")
                        attempts++
                        delay(200)
                    }
                }
                if (attempts >= 5) {
                    Log.e("InterstitialAd", "Failed to get monetization manager after 5 attempts, executing action directly")
                    onAdClosed()
                }
            } else {
                Log.e("InterstitialAd", "Context is not an Activity, executing action directly")
                onAdClosed()
            }
        } catch (e: Exception) {
            Log.e("InterstitialAd", "Error showing interstitial ad", e)
            onAdClosed()
        }
    }
}
