package com.dzung.runner.locationtracker.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daumo.ads.BillingConstants
import com.dzung.runner.locationtracker.R
import com.dzung.runner.locationtracker.billing.PremiumManager
import kotlinx.coroutines.launch

/**
 * Recognizable, high-converting athletic Paywall Bottom Sheet.
 * Displays 3 product tiers:
 * 1. Runner PRO Bundle (Hero Card: Best Value)
 * 2. Remove Ads
 * 3. Clean Route Stickers (Remove Watermark)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumPaywallBottomSheet(
    premiumManager: PremiumManager,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    initialTargetSku: String = BillingConstants.SKU_PREMIUM_BUNDLE
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isAdsRemoved by premiumManager.isAdsRemoved.collectAsStateWithLifecycle()
    val isWatermarkRemoved by premiumManager.isWatermarkRemoved.collectAsStateWithLifecycle()
    val isProUser by premiumManager.isProUser.collectAsStateWithLifecycle()
    val productPrices by premiumManager.productPrices.collectAsStateWithLifecycle()
    val purchasedSkus by premiumManager.purchasedSkus.collectAsStateWithLifecycle()

    var selectedSku by remember { mutableStateOf(initialTargetSku) }
    var isRestoring by remember { mutableStateOf(false) }

    // Fallback prices if Google Play store connection is loading
    val bundlePrice = productPrices[BillingConstants.SKU_PREMIUM_BUNDLE] ?: "$1.49"
    val adsPrice = productPrices[BillingConstants.SKU_REMOVE_ADS]
        ?: productPrices[BillingConstants.SKU_REMOVE_ADS_LEGACY]
        ?: "$0.99"
    val watermarkPrice = productPrices[BillingConstants.SKU_REMOVE_WATERMARK] ?: "$0.99"

    val restoreSuccessMsg = stringResource(R.string.restore_success)
    val restoreEmptyMsg = stringResource(R.string.restore_empty)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar with Close button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                onDismissRequest()
                            }
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Paywall Header
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFC5200), Color(0xFFFFB300))
                        )
                    )
                    .shadow(6.dp, CircleShape)
            ) {
                Text(
                    text = "👑",
                    fontSize = 28.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.paywall_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = stringResource(R.string.paywall_subtitle),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ==========================================
            // TIER 1: RUNNER PRO BUNDLE (HERO CARD)
            // ==========================================
            val isBundleOwned = isProUser || purchasedSkus.contains(BillingConstants.SKU_PREMIUM_BUNDLE)
            PaywallBundleHeroCard(
                title = stringResource(R.string.tier_bundle_title),
                badgeText = stringResource(R.string.tier_bundle_badge),
                price = bundlePrice,
                isSelected = selectedSku == BillingConstants.SKU_PREMIUM_BUNDLE,
                isOwned = isBundleOwned,
                features = listOf(
                    stringResource(R.string.feature_no_ads),
                    stringResource(R.string.feature_no_watermark),
                    stringResource(R.string.feature_vip_streak),
                    stringResource(R.string.feature_lifetime)
                ),
                onClick = {
                    if (!isBundleOwned) {
                        selectedSku = BillingConstants.SKU_PREMIUM_BUNDLE
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ==========================================
            // TIER 2: REMOVE ADS
            // ==========================================
            val isAdsOwned = isAdsRemoved || isProUser
            PaywallStandardCard(
                icon = "🚫",
                title = stringResource(R.string.tier_ads_title),
                description = stringResource(R.string.tier_ads_desc),
                price = adsPrice,
                isSelected = selectedSku == BillingConstants.SKU_REMOVE_ADS,
                isOwned = isAdsOwned,
                onClick = {
                    if (!isAdsOwned) {
                        selectedSku = BillingConstants.SKU_REMOVE_ADS
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ==========================================
            // TIER 3: REMOVE WATERMARK
            // ==========================================
            val isWatermarkOwned = isWatermarkRemoved || isProUser
            PaywallStandardCard(
                icon = "🎨",
                title = stringResource(R.string.tier_watermark_title),
                description = stringResource(R.string.tier_watermark_desc),
                price = watermarkPrice,
                isSelected = selectedSku == BillingConstants.SKU_REMOVE_WATERMARK,
                isOwned = isWatermarkOwned,
                onClick = {
                    if (!isWatermarkOwned) {
                        selectedSku = BillingConstants.SKU_REMOVE_WATERMARK
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // ACTION BUTTON (CTA)
            // ==========================================
            val isSelectedOwned = when (selectedSku) {
                BillingConstants.SKU_PREMIUM_BUNDLE -> isBundleOwned
                BillingConstants.SKU_REMOVE_ADS -> isAdsOwned
                BillingConstants.SKU_REMOVE_WATERMARK -> isWatermarkOwned
                else -> false
            }

            val selectedTitle = when (selectedSku) {
                BillingConstants.SKU_PREMIUM_BUNDLE -> stringResource(R.string.tier_bundle_title)
                BillingConstants.SKU_REMOVE_ADS -> stringResource(R.string.tier_ads_title)
                BillingConstants.SKU_REMOVE_WATERMARK -> stringResource(R.string.tier_watermark_title)
                else -> ""
            }

            val selectedPrice = when (selectedSku) {
                BillingConstants.SKU_PREMIUM_BUNDLE -> bundlePrice
                BillingConstants.SKU_REMOVE_ADS -> adsPrice
                BillingConstants.SKU_REMOVE_WATERMARK -> watermarkPrice
                else -> ""
            }

            Button(
                onClick = {
                    if (activity != null && !isSelectedOwned) {
                        premiumManager.launchPurchase(activity, selectedSku)
                    }
                },
                enabled = !isSelectedOwned,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFC5200),
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .shadow(if (!isSelectedOwned) 4.dp else 0.dp, RoundedCornerShape(14.dp))
            ) {
                if (isSelectedOwned) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_continue_owned),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_continue_format, selectedTitle, selectedPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Restore Purchases Text Button
            TextButton(
                onClick = {
                    isRestoring = true
                    premiumManager.restorePurchases { hasPurchases ->
                        isRestoring = false
                        Toast.makeText(
                            context,
                            if (hasPurchases) restoreSuccessMsg else restoreEmptyMsg,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                enabled = !isRestoring,
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isRestoring) "..." else stringResource(R.string.restore_purchases),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Legal & Compliance Notes
            Text(
                text = stringResource(R.string.paywall_terms),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

/**
 * Hero Card for the Runner PRO Bundle.
 * Features a radiant gradient border, floating "BEST VALUE" badge, and detailed feature checkmarks.
 */
@Composable
private fun PaywallBundleHeroCard(
    title: String,
    badgeText: String,
    price: String,
    isSelected: Boolean,
    isOwned: Boolean,
    features: List<String>,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isOwned -> Color(0xFF4CAF50)
            isSelected -> Color(0xFFFC5200)
            else -> Color(0xFFFFB300).copy(alpha = 0.5f)
        },
        label = "HeroBorder"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected || isOwned) 2.5.dp else 1.dp,
        label = "HeroBorderWidth"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Floating Best Value Badge & Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOwned) Color(0xFF4CAF50) else Color(0xFFFC5200),
                    contentColor = Color.White
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isOwned) stringResource(R.string.already_owned) else badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Text(
                    text = price,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Title & Radio Check
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected || isOwned) Color(0xFFFC5200) else Color.Transparent
                        )
                        .border(
                            2.dp,
                            if (isSelected || isOwned) Color(0xFFFC5200) else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected || isOwned) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(6.dp))

            // Feature Checklist
            features.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 1.5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Standard Tier Card for individual items (Remove Ads, Remove Watermark).
 */
@Composable
private fun PaywallStandardCard(
    icon: String,
    title: String,
    description: String,
    price: String,
    isSelected: Boolean,
    isOwned: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isOwned -> Color(0xFF4CAF50)
            isSelected -> Color(0xFFFC5200)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        label = "StandardBorder"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected || isOwned) 2.dp else 1.dp,
        label = "StandardBorderWidth"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Description
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isOwned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF4CAF50),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.already_owned),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Price & Radio Indicator
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = price,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected || isOwned) Color(0xFFFC5200) else Color.Transparent
                        )
                        .border(
                            2.dp,
                            if (isSelected || isOwned) Color(0xFFFC5200) else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected || isOwned) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
