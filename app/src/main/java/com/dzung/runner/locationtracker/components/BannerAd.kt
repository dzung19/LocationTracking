package com.dzung.runner.locationtracker.components

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.daumo.ads.BannerAdManager
import com.daumo.ads.DynamicAdsManager
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.delay

@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    adKey: String = "default_banner",
    reserveSpace: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // State to track if banner is loaded or failed
    var isBannerLoaded by remember { mutableStateOf(false) }
    var isBannerFailed by remember { mutableStateOf(false) }
    
    // Create a reference to the ad container
    val adContainer = remember { 
        FrameLayout(context).apply {
            visibility = View.GONE
        }
    }
    
    // Check if user is premium to avoid reserving space unnecessarily
    val initialMonetizationManager = remember {
        try {
            DynamicAdsManager.getInstance().getMonetizationManager()
        } catch (e: Exception) {
            null
        }
    }
    val isPremium = initialMonetizationManager?.isUserPremium == true
    
    // Target height: reserve 56.dp upfront so content above it never jumps/flings
    val targetHeight = when {
        isPremium -> 0.dp
        isBannerLoaded -> 56.dp
        isBannerFailed -> 0.dp
        reserveSpace -> 56.dp
        else -> 0.dp
    }
    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "BannerHeight"
    )
    
    // Load the banner ad when the composable is first composed
    LaunchedEffect(adKey) {
        if (isPremium) {
            isBannerFailed = true
            return@LaunchedEffect
        }
        try {
            Log.d("BannerAd", "Starting banner ad initialization for $adKey")
            val activity = context as? Activity
            if (activity != null) {
                var attempts = 0
                while (attempts < 5) {
                    val monetizationManager = try {
                        DynamicAdsManager.getInstance().getMonetizationManager()
                    } catch (e: Exception) {
                        null
                    }
                    
                    if (monetizationManager != null) {
                        if (monetizationManager.isUserPremium) {
                            isBannerFailed = true
                            break
                        }
                        monetizationManager.loadBannerAd(
                            activity = activity,
                            container = adContainer,
                            adKey = adKey,
                            listener = object : BannerAdManager.BannerAdListener {
                                override fun onAdLoaded() {
                                    Log.d("BannerAd", "Banner ad loaded successfully")
                                    isBannerLoaded = true
                                    isBannerFailed = false
                                    adContainer.visibility = View.VISIBLE
                                }

                                override fun onAdFailedToLoad(adError: LoadAdError?) {
                                    Log.d("BannerAd", "Banner ad failed to load: ${adError?.message}")
                                    isBannerLoaded = false
                                    isBannerFailed = true
                                    adContainer.visibility = View.GONE
                                }

                                override fun onAdOpened() {
                                    Log.d("BannerAd", "Banner ad opened")
                                }

                                override fun onAdClicked() {
                                    Log.d("BannerAd", "Banner ad clicked")
                                }

                                override fun onAdClosed() {
                                    Log.d("BannerAd", "Banner ad closed")
                                }
                            }
                        )
                        Log.d("BannerAd", "Banner ad load requested for $adKey")
                        break
                    } else {
                        Log.d("BannerAd", "Monetization manager not available, retrying...")
                        attempts++
                        delay(150)
                    }
                }
                if (attempts >= 5) {
                    Log.e("BannerAd", "Failed to get monetization manager after 5 attempts")
                    isBannerLoaded = false
                    isBannerFailed = true
                    adContainer.visibility = View.GONE
                }
            } else {
                Log.e("BannerAd", "Context is not an Activity")
                isBannerLoaded = false
                isBannerFailed = true
                adContainer.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e("BannerAd", "Error loading banner ad", e)
            isBannerLoaded = false
            isBannerFailed = true
            adContainer.visibility = View.GONE
        }
    }
    
    // Handle lifecycle events
    DisposableEffect(lifecycleOwner, adKey) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                try {
                    DynamicAdsManager.getInstance().getMonetizationManager()?.resumeBannerAd(adKey)
                } catch (ignored: Exception) {}
            }
            
            override fun onPause(owner: LifecycleOwner) {
                try {
                    DynamicAdsManager.getInstance().getMonetizationManager()?.pauseBannerAd(adKey)
                } catch (ignored: Exception) {}
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            try {
                lifecycleOwner.lifecycle.removeObserver(observer)
                DynamicAdsManager.getInstance().getMonetizationManager()?.destroyBannerAd(adKey)
            } catch (ignored: Exception) {}
        }
    }
    
    // Create the banner ad container with fixed/animated height to prevent layout shift
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(animatedHeight)
            .padding(bottom = if (animatedHeight > 0.dp) 4.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { adContainer },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
