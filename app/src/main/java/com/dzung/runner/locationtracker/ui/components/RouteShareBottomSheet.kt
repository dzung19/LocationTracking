package com.dzung.runner.locationtracker.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dzung.runner.locationtracker.R
import com.dzung.runner.locationtracker.data.database.LocationPoint
import com.dzung.runner.locationtracker.data.database.RunSession
import com.dzung.runner.locationtracker.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom Sheet allowing runners to customize and export Strava-style transparent
 * route stickers or overlay routes directly on their running photos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteShareBottomSheet(
    points: List<LocationPoint>,
    session: RunSession,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedRatio by remember { mutableStateOf(RouteAspectRatio.SQUARE_1_1) }
    var selectedTheme by remember { mutableStateOf(RouteTheme.STRAVA_ORANGE) }
    var selectedStyle by remember { mutableStateOf(RouteOverlayStyle.ROUTE_AND_STATS) }
    var backgroundPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var backgroundPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            backgroundPhotoUri = uri
            coroutineScope.launch(Dispatchers.IO) {
                backgroundPhotoBitmap = RouteShareManager.decodeSampledBitmapFromUri(context, uri)
            }
        }
    }

    // Generate/refresh preview whenever configuration changes
    LaunchedEffect(points, session, selectedRatio, selectedTheme, selectedStyle, backgroundPhotoBitmap) {
        if (points.size < 2) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val config = RouteStickerConfig(
                aspectRatio = selectedRatio,
                theme = selectedTheme,
                style = selectedStyle,
                showBrandWatermark = true
            )
            previewBitmap = if (backgroundPhotoBitmap != null) {
                RouteBitmapGenerator.generateCompositedBitmap(
                    backgroundPhoto = backgroundPhotoBitmap!!,
                    points = points,
                    session = session,
                    config = config
                )
            } else {
                RouteBitmapGenerator.generateTransparentRouteBitmap(
                    points = points,
                    session = session,
                    config = config
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.share_route_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (points.size < 2) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.insufficient_route_points),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                // Live Interactive Preview Box
                val previewHeight = if (selectedRatio == RouteAspectRatio.SQUARE_1_1) 280.dp else 360.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Checkered transparent canvas pattern if no background photo
                    if (backgroundPhotoBitmap == null) {
                        CheckerboardPattern(modifier = Modifier.fillMaxSize())
                    }

                    // Rendered Preview Bitmap
                    previewBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Route Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } ?: CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Customization Controls
                // 1. Format / Aspect Ratio
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.aspect_ratio),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(80.dp)
                    )
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedRatio == RouteAspectRatio.SQUARE_1_1,
                            onClick = { selectedRatio = RouteAspectRatio.SQUARE_1_1 },
                            label = { Text(stringResource(R.string.ratio_square)) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.CropSquare, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        FilterChip(
                            selected = selectedRatio == RouteAspectRatio.STORY_9_16,
                            onClick = { selectedRatio = RouteAspectRatio.STORY_9_16 },
                            label = { Text(stringResource(R.string.ratio_story)) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Color Palettes (Strava Orange, Clean White, Neon Green, Stealth Dark)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Color",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(80.dp)
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThemeColorItem(
                            theme = RouteTheme.STRAVA_ORANGE,
                            isSelected = selectedTheme == RouteTheme.STRAVA_ORANGE,
                            color = Color(0xFFFC5200),
                            label = stringResource(R.string.theme_strava_orange),
                            onClick = { selectedTheme = RouteTheme.STRAVA_ORANGE }
                        )
                        ThemeColorItem(
                            theme = RouteTheme.CLEAN_WHITE,
                            isSelected = selectedTheme == RouteTheme.CLEAN_WHITE,
                            color = Color.White,
                            label = stringResource(R.string.theme_pure_white),
                            onClick = { selectedTheme = RouteTheme.CLEAN_WHITE }
                        )
                        ThemeColorItem(
                            theme = RouteTheme.ELECTRIC_NEON,
                            isSelected = selectedTheme == RouteTheme.ELECTRIC_NEON,
                            color = Color(0xFF00E676),
                            label = stringResource(R.string.theme_neon_green),
                            onClick = { selectedTheme = RouteTheme.ELECTRIC_NEON }
                        )
                        ThemeColorItem(
                            theme = RouteTheme.STEALTH_DARK,
                            isSelected = selectedTheme == RouteTheme.STEALTH_DARK,
                            color = Color(0xFF1C1C1E),
                            label = stringResource(R.string.theme_stealth_black),
                            onClick = { selectedTheme = RouteTheme.STEALTH_DARK }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Style: Route & Stats vs Route Only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Style",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(80.dp)
                    )
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedStyle == RouteOverlayStyle.ROUTE_AND_STATS,
                            onClick = { selectedStyle = RouteOverlayStyle.ROUTE_AND_STATS },
                            label = { Text(stringResource(R.string.style_route_and_stats)) }
                        )
                        FilterChip(
                            selected = selectedStyle == RouteOverlayStyle.ROUTE_ONLY,
                            onClick = { selectedStyle = RouteOverlayStyle.ROUTE_ONLY },
                            label = { Text(stringResource(R.string.style_route_only)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Background Photo Option (Add Photo / Remove Photo)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (backgroundPhotoBitmap != null) {
                                stringResource(R.string.change_background_photo)
                            } else {
                                stringResource(R.string.pick_background_photo)
                            }
                        )
                    }

                    if (backgroundPhotoBitmap != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                backgroundPhotoUri = null
                                backgroundPhotoBitmap = null
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.remove_background_photo),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                // Button 1: If user picked background photo, allow sharing composited photo
                AnimatedVisibility(visible = backgroundPhotoBitmap != null) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isGenerating = true
                                val config = RouteStickerConfig(selectedRatio, selectedTheme, selectedStyle, true)
                                val finalBmp = withContext(Dispatchers.Default) {
                                    RouteBitmapGenerator.generateCompositedBitmap(
                                        backgroundPhoto = backgroundPhotoBitmap!!,
                                        points = points,
                                        session = session,
                                        config = config
                                    )
                                }
                                val uri = withContext(Dispatchers.IO) {
                                    RouteShareManager.saveBitmapToCache(context, finalBmp)
                                }
                                isGenerating = false
                                if (uri != null) {
                                    RouteShareManager.shareImageUri(
                                        context,
                                        uri,
                                        context.getString(R.string.share_photo_with_route)
                                    )
                                }
                            }
                        },
                        enabled = !isGenerating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFC5200), // Strava athletic orange
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.share_photo_with_route),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Button 2: Share Transparent PNG Sticker
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isGenerating = true
                            val config = RouteStickerConfig(selectedRatio, selectedTheme, selectedStyle, true)
                            val finalBmp = withContext(Dispatchers.Default) {
                                RouteBitmapGenerator.generateTransparentRouteBitmap(
                                    points = points,
                                    session = session,
                                    config = config
                                )
                            }
                            val uri = withContext(Dispatchers.IO) {
                                RouteShareManager.saveBitmapToCache(context, finalBmp)
                            }
                            isGenerating = false
                            if (uri != null) {
                                RouteShareManager.shareImageUri(
                                    context,
                                    uri,
                                    context.getString(R.string.share_transparent_sticker)
                                )
                            }
                        }
                    },
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (backgroundPhotoBitmap == null) {
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFC5200),
                            contentColor = Color.White
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    }
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.share_transparent_sticker),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Button 3: Save to Device Gallery
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            isGenerating = true
                            val config = RouteStickerConfig(selectedRatio, selectedTheme, selectedStyle, true)
                            val finalBmp = withContext(Dispatchers.Default) {
                                if (backgroundPhotoBitmap != null) {
                                    RouteBitmapGenerator.generateCompositedBitmap(
                                        backgroundPhoto = backgroundPhotoBitmap!!,
                                        points = points,
                                        session = session,
                                        config = config
                                    )
                                } else {
                                    RouteBitmapGenerator.generateTransparentRouteBitmap(
                                        points = points,
                                        session = session,
                                        config = config
                                    )
                                }
                            }
                            val success = withContext(Dispatchers.IO) {
                                RouteShareManager.saveBitmapToGallery(context, finalBmp)
                            }
                            isGenerating = false
                            Toast.makeText(
                                context,
                                if (success) context.getString(R.string.saved_to_gallery_success) else context.getString(R.string.saved_to_gallery_error),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.save_to_gallery))
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Visual color bubble selector for themes.
 */
@Composable
private fun ThemeColorItem(
    theme: RouteTheme,
    isSelected: Boolean,
    color: Color,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (theme == RouteTheme.CLEAN_WHITE) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Renders a subtle checkerboard pattern in Canvas to indicate transparency.
 */
@Composable
private fun CheckerboardPattern(
    modifier: Modifier = Modifier,
    tileSizeDp: Float = 14f
) {
    Canvas(modifier = modifier) {
        val tileSize = tileSizeDp * density
        val cols = (size.width / tileSize).toInt() + 1
        val rows = (size.height / tileSize).toInt() + 1

        val lightColor = Color(0xFFF0F0F0)
        val darkColor = Color(0xFFE2E2E2)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val color = if ((r + c) % 2 == 0) lightColor else darkColor
                drawRect(
                    color = color,
                    topLeft = Offset(c * tileSize, r * tileSize),
                    size = Size(tileSize, tileSize)
                )
            }
        }
    }
}
