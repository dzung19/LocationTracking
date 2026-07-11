package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.LocationTrackingState
import com.example.LocationViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LocationTrackerApp(
    viewModel: LocationViewModel,
    modifier: Modifier = Modifier,
    onStartService: () -> Unit
) {
    val context = LocalContext.current
    val isServiceBound by viewModel.isServiceBound.collectAsStateWithLifecycle()
    val trackingState by viewModel.trackingState.collectAsStateWithLifecycle()

    // Observe permission states reactively
    var hasLocationPermission by remember {
        mutableStateOf(context.checkLocationPermissions())
    }

    // Prepare launcher for fine, coarse and notifications permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = fineGranted || coarseGranted
    }

    // Launch permission request if not already granted
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            val req = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
            permissionLauncher.launch(req)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!isServiceBound) {
            // State 1: Bound Service is still connecting/binding
            ServiceConnectingScreen()
        } else if (!hasLocationPermission) {
            // State 2: Permission is denied
            PermissionDeniedScreen(onRequestPermission = {
                val req = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.toTypedArray()
                permissionLauncher.launch(req)
            })
        } else {
            // State 3: Permissions granted and service is bound!
            MainTrackerScreen(
                viewModel = viewModel,
                state = trackingState,
                onStartService = onStartService
            )
        }
    }
}

@Composable
fun ServiceConnectingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Connecting to Service...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Establishing binder pipeline to securely track GPS coordinates",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PermissionDeniedScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Warning Icon",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Location Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "This application uses the Fused Location Provider and Google Maps SDK to display and track your coordinates in real-time. Please grant location permissions to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("permission_grant_button")
        ) {
            Text("Grant Location Permission", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun MainTrackerScreen(
    viewModel: LocationViewModel,
    state: LocationTrackingState,
    onStartService: () -> Unit
) {
    val context = LocalContext.current

    // Default coordinate (Vietnam central area)
    val defaultLocation = LatLng(10.762622, 106.660172)
    
    // Manage Camera Position State
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 12f)
    }

    // Collect latest coordinate from flow state
    val currentLatLng = remember(state.latitude, state.longitude) {
        if (state.latitude != null && state.longitude != null) {
            LatLng(state.latitude, state.longitude)
        } else {
            null
        }
    }

    // Auto-animate map camera to the latest coordinates whenever they update
    LaunchedEffect(currentLatLng) {
        currentLatLng?.let { latLng ->
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(latLng, 16f)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Google Map Rendering
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = context.checkLocationPermissions()
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false, // We provide our own floating button
                zoomControlsEnabled = false
            )
        ) {
            // Render Marker if coordinates exist
            currentLatLng?.let { latLng ->
                Marker(
                    state = rememberMarkerState(position = latLng),
                    title = "My Current Location",
                    snippet = "Accuracy: ${state.accuracy ?: 0f}m"
                )
            }
        }

        // Container for bottom UI elements to prevent overlap
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            // 2. Custom FAB to snap map camera back to user's location
            FloatingActionButton(
                onClick = {
                    currentLatLng?.let { latLng ->
                        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    }
                },
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .testTag("recenter_button"),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Recenter Map"
                )
            }

            // 3. Float Panel Card overlaid on top of Google Map
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Header with tracking badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "GPS Tracking Hub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (state.isTracking) "Foreground updates active" else "System idle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Status Indicator Badge
                        val badgeColor = if (state.isTracking) Color(0xFF4CAF50) else Color(0xFFF44336)
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 0.8f,
                            targetValue = 1.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse_anim"
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                                    .then(
                                        if (state.isTracking) {
                                            Modifier.background(badgeColor.copy(alpha = 0.5f)) // Visual pulsing
                                        } else Modifier
                                    )
                            )
                            Text(
                                text = if (state.isTracking) "TRACKING" else "STOPPED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = badgeColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Grid with Lat, Lon, Accuracy details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailItem(
                            label = "LATITUDE",
                            value = state.latitude?.let { "%.5f".format(it) } ?: "N/A",
                            modifier = Modifier.weight(1f)
                        )
                        DetailItem(
                            label = "LONGITUDE",
                            value = state.longitude?.let { "%.5f".format(it) } ?: "N/A",
                            modifier = Modifier.weight(1f)
                        )
                        DetailItem(
                            label = "ACCURACY",
                            value = state.accuracy?.let { "±%.1fm".format(it) } ?: "N/A",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Render latest timestamp or error messages
                    if (state.errorMessage != null) {
                        Text(
                            text = state.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else if (state.timestamp != null) {
                        val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                        val formattedTime = remember(state.timestamp) { sdf.format(Date(state.timestamp)) }
                        Text(
                            text = "Last Update: $formattedTime",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Control Action Buttons (Start / Stop Tracking)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Start Tracking Button
                        Button(
                            onClick = {
                                // 1. Force foreground service execution
                                onStartService()
                                // 2. Trigger updates from ViewModel
                                viewModel.startTracking()
                            },
                            enabled = !state.isTracking,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("start_tracking_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start Icon",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start", fontWeight = FontWeight.SemiBold)
                        }

                        // Stop Tracking Button
                        Button(
                            onClick = {
                                viewModel.stopTracking()
                            },
                            enabled = state.isTracking,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("stop_tracking_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Icon",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Extension helper to evaluate if ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION are granted.
 */
fun Context.checkLocationPermissions(): Boolean {
    return ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
}
