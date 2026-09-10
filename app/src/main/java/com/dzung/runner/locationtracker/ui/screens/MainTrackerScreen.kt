package com.dzung.runner.locationtracker.ui.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import com.dzung.runner.locationtracker.ui.components.CollapsedMiniHud
import com.dzung.runner.locationtracker.ui.components.GhostDuelCard
import com.dzung.runner.locationtracker.ui.components.HeroStatDisplay
import com.dzung.runner.locationtracker.ui.components.MetricTile
import com.dzung.runner.locationtracker.ui.components.PulsingLiveDot
import com.dzung.runner.locationtracker.ui.components.SegmentedActivitySwitch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.dzung.runner.locationtracker.data.model.WeatherState
import androidx.compose.ui.res.stringResource
import com.dzung.runner.locationtracker.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dzung.runner.locationtracker.LocationTrackingState
import com.dzung.runner.locationtracker.LocationViewModel
import com.dzung.runner.locationtracker.data.database.ActivityType
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.dzung.runner.locationtracker.data.repository.UserPreferences
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch

@Composable
fun MainTrackerScreen(
    viewModel: LocationViewModel,
    state: LocationTrackingState,
    userPreferences: UserPreferences,
    onStartService: () -> Unit,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val userWeight = userPreferences.weight
    var showWeightDialog by remember { mutableStateOf(false) }
    var showGhostDialog by remember { mutableStateOf(false) }
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle(emptyList())
    val coroutineScope = rememberCoroutineScope()

    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()

    var isTrackerExpanded by remember { mutableStateOf(!state.isTracking) }

    LaunchedEffect(state.isTracking) {
        isTrackerExpanded = !state.isTracking
    }

    // Fetch weather immediately using best available coordinates (GPS if active, fallback to last saved location)
    val currentLat = state.latitude ?: userPreferences.latitude
    val currentLon = state.longitude ?: userPreferences.longitude

    LaunchedEffect(currentLat, currentLon) {
        viewModel.fetchWeather(currentLat, currentLon)
    }
    
    if (showWeightDialog) {
        var weightInput by remember { mutableStateOf(userWeight.toString()) }
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            icon = { Icon(Icons.Default.MonitorWeight, contentDescription = null) },
            title = { Text(stringResource(R.string.body_weight_title)) },
            text = {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(stringResource(R.string.weight_label)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newWeight = weightInput.toFloatOrNull()
                    if (newWeight != null && newWeight > 0) {
                        viewModel.saveWeight(newWeight)
                        showWeightDialog = false
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showGhostDialog) {
        val dateFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { showGhostDialog = false },
            title = { Text(stringResource(R.string.select_ghost)) },
            text = {
                if (allSessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.no_ghost_available),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    val grouped = remember(allSessions) {
                        allSessions.groupBy { session ->
                            val date = Date(session.startTimeInMillis)
                            val fmt = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())
                            fmt.format(date)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        grouped.forEach { (dateHeader, sessionsInDay) ->
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    Text(
                                        text = "  $dateHeader  ",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                            items(sessionsInDay, key = { it.id }) { session ->
                                val dateStr = dateFormat.format(Date(session.startTimeInMillis))
                                val durationSeconds = if (session.endTimeInMillis != null) {
                                    (session.endTimeInMillis - session.startTimeInMillis) / 1000
                                } else {
                                    0L
                                }
                                val mins = durationSeconds / 60
                                val secs = durationSeconds % 60
                                val durationStr = "%02d:%02d".format(mins, secs)
                                
                                val label = if (session.activityType == ActivityType.RUNNING) {
                                    stringResource(R.string.running_activity)
                                } else {
                                    stringResource(R.string.walking_activity)
                                }
                                
                                val isSelected = state.selectedGhostSessionId == session.id

                                Card(
                                    onClick = {
                                        viewModel.setGhostSession(session.id)
                                        showGhostDialog = false
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "$label - %.2f km".format(session.totalDistanceMeters / 1000f),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "$dateStr • $durationStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGhostDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val defaultLocation = LatLng(userPreferences.latitude, userPreferences.longitude)
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 16f)
    }

    // If coordinates were newly loaded/saved and GPS is not active yet, animate camera to saved location
    LaunchedEffect(userPreferences.hasSavedLocation, userPreferences.latitude, userPreferences.longitude) {
        if (userPreferences.hasSavedLocation && state.latitude == null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(userPreferences.latitude, userPreferences.longitude),
                    16f
                )
            )
        }
    }

    val currentLatLng = remember(state.latitude, state.longitude) {
        if (state.latitude != null && state.longitude != null) {
            LatLng(state.latitude, state.longitude)
        } else {
            null
        }
    }

    // Save newly tracked coordinates to DataStore
    LaunchedEffect(state.latitude, state.longitude) {
        if (state.latitude != null && state.longitude != null) {
            viewModel.saveLocation(state.latitude, state.longitude)
        }
    }

    // Locate user immediately on app open and persist coordinates to DataStore
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        val target = LatLng(loc.latitude, loc.longitude)
                        if (state.latitude == null) {
                            coroutineScope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 16f))
                            }
                        }
                        viewModel.saveLocation(loc.latitude, loc.longitude)
                    }
                }

                val cancellationTokenSource = CancellationTokenSource()
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { loc ->
                    if (loc != null) {
                        val target = LatLng(loc.latitude, loc.longitude)
                        if (state.latitude == null) {
                            coroutineScope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 16f))
                            }
                        }
                        viewModel.saveLocation(loc.latitude, loc.longitude)
                    }
                }
            } catch (e: SecurityException) {
                // Ignore if permission revoked
            }
        }
    }

    LaunchedEffect(currentLatLng) {
        currentLatLng?.let { latLng ->
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(latLng, 16f)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false
            ),
            contentPadding = contentPadding
        ) {
            currentLatLng?.let { latLng ->
                val paceStr = state.currentPaceSecondsPerKm?.let { paceSec ->
                    val mins = paceSec / 60
                    val secs = paceSec % 60
                    "%d:%02d".format(mins, secs)
                } ?: "-:--"

                MarkerComposable(
                    state = rememberMarkerState(position = latLng),
                    anchor = Offset(0.5f, 0.9f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.wrapContentSize()
                    ) {
                        // Floating Pace Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            tonalElevation = 4.dp,
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                text = paceStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(2.dp))

                        // Avatar containing active activity icon
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            tonalElevation = 4.dp,
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = if (state.activityType == ActivityType.RUNNING) {
                                        Icons.Default.DirectionsRun
                                    } else {
                                        Icons.Default.DirectionsWalk
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Surface(
                            shape = androidx.compose.foundation.shape.GenericShape { size, _ ->
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width / 2f, size.height)
                                close()
                            },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(width = 8.dp, height = 6.dp)
                                .offset(y = (-2).dp)
                        ) {}
                    }
                }
            }

            // Draw Breadcrumb Trail (Polyline)
            if (state.pathPoints.isNotEmpty()) {
                Polyline(
                    points = state.pathPoints,
                    color = Color.Blue,
                    width = 12f
                )
            }

            // Draw ghost route polyline if loaded
            if (state.ghostPathPoints.isNotEmpty()) {
                Polyline(
                    points = state.ghostPathPoints,
                    color = Color(0xFFFF9800).copy(alpha = 0.45f), // Semi-transparent Amber/Orange
                    width = 8f
                )
            }

            // Draw ghost runner marker if active and coordinates exist
            if (state.isTracking && state.ghostLatitude != null && state.ghostLongitude != null) {
                MarkerComposable(
                    state = rememberMarkerState(
                        position = LatLng(state.ghostLatitude, state.ghostLongitude)
                    ),
                    anchor = Offset(0.5f, 0.5f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFF9800).copy(alpha = 0.85f), // Semi-transparent Orange
                        contentColor = Color.White,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("👻", fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        // Floating Weather Advisor at Top-Left (Overlay over map)
        if (hasLocationPermission) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = contentPadding.calculateTopPadding() + 16.dp, start = 16.dp)
            ) {
                WeatherWidget(
                    weatherState = weatherState,
                    onRetry = {
                        if (state.latitude != null && state.longitude != null) {
                            viewModel.fetchWeather(state.latitude, state.longitude)
                        }
                    }
                )
            }
        }

        // Top-Right Floating Actions: Share, Like & Ghost Comparison Widget
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = contentPadding.calculateTopPadding() + 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Share and Like / Rate App Pill
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { shareApp(context) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share_app),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { rateApp(context) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = stringResource(R.string.like_app),
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Floating Ghost Comparison Card at Top-Right
            if (state.isTracking && state.selectedGhostSessionId != null) {
                GhostComparisonWidget(
                    userDistance = state.distanceMeters,
                    ghostDistance = state.ghostDistanceMeters
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = contentPadding.calculateBottomPadding())
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (hasLocationPermission) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            val target = currentLatLng ?: defaultLocation
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 16f))
                            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                            try {
                                fusedClient.lastLocation.addOnSuccessListener { loc ->
                                    if (loc != null) {
                                        val newTarget = LatLng(loc.latitude, loc.longitude)
                                        coroutineScope.launch {
                                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(newTarget, 16f))
                                        }
                                        viewModel.saveLocation(loc.latitude, loc.longitude)
                                    }
                                }
                            } catch (e: SecurityException) {}
                        }
                    },
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .testTag("recenter_button"),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.MyLocation, stringResource(R.string.recenter_map))
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(durationMillis = 300)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            ) {
                if (!hasLocationPermission) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.loc_permission_req),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.loc_permission_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(stringResource(R.string.grant_permission), fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        // Collapsed Mini-HUD (shown when minimized)
                        AnimatedVisibility(
                            visible = !isTrackerExpanded,
                            enter = fadeIn(animationSpec = tween(250)) + expandVertically(animationSpec = tween(300)),
                            exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(300))
                        ) {
                            CollapsedMiniHud(
                                isTracking = state.isTracking,
                                distanceMeters = state.distanceMeters,
                                elapsedTimeSeconds = state.elapsedTimeSeconds,
                                currentPaceSecondsPerKm = state.currentPaceSecondsPerKm,
                                activityType = state.activityType,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        // Expanded Athletic Dashboard (shown when expanded)
                        AnimatedVisibility(
                            visible = isTrackerExpanded,
                            enter = fadeIn(animationSpec = tween(250)) + expandVertically(animationSpec = tween(300)),
                            exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(300))
                        ) {
                            Column {
                                // Header: Status + Title + Settings
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (state.isTracking) {
                                            PulsingLiveDot()
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF9E9E9E))
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = if (state.isTracking) {
                                                    if (state.activityType == ActivityType.RUNNING) stringResource(R.string.running_workout)
                                                    else stringResource(R.string.walking_workout)
                                                } else {
                                                    stringResource(R.string.activity_tracker)
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (state.isTracking) stringResource(R.string.fg_updates_active) else stringResource(R.string.system_idle),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val badgeColor = if (state.isTracking) Color(0xFF00E676) else Color(0xFF9E9E9E)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(badgeColor.copy(alpha = 0.12f))
                                                .border(1.dp, badgeColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (state.isTracking) stringResource(R.string.tracking_active) else stringResource(R.string.tracking_stopped),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                                color = badgeColor
                                            )
                                        }

                                        IconButton(
                                            onClick = { showWeightDialog = true },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = stringResource(R.string.settings),
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Hero Stat: Big Distance Number
                                HeroStatDisplay(distanceMeters = state.distanceMeters)

                                Spacer(modifier = Modifier.height(14.dp))

                                // Metric Tiles Grid 2x2
                                val paceStr = state.currentPaceSecondsPerKm?.let { paceSec ->
                                    val mins = paceSec / 60
                                    val secs = paceSec % 60
                                    "%d:%02d /km".format(mins, secs)
                                } ?: "-:-- /km"

                                val h = state.elapsedTimeSeconds / 3600
                                val m = (state.elapsedTimeSeconds % 3600) / 60
                                val s = state.elapsedTimeSeconds % 60
                                val timeStr = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MetricTile(
                                        label = stringResource(R.string.duration_label),
                                        value = timeStr,
                                        icon = Icons.Default.Timer,
                                        accentColor = Color(0xFFFFA000),
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricTile(
                                        label = stringResource(R.string.pace_label),
                                        value = paceStr,
                                        icon = Icons.Default.Speed,
                                        accentColor = Color(0xFF00C853),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MetricTile(
                                        label = stringResource(R.string.calories_label),
                                        value = "${state.caloriesBurned} kcal",
                                        icon = Icons.Default.LocalFireDepartment,
                                        accentColor = Color(0xFFFF5722),
                                        modifier = Modifier.weight(1f)
                                    )
                                    val sign = if (state.slopePercentage > 0) "+" else ""
                                    val elevSlope = "%.0fm • $sign%.1f%%".format(state.elevationMeters, state.slopePercentage)
                                    MetricTile(
                                        label = stringResource(R.string.elevation_label),
                                        value = elevSlope,
                                        icon = Icons.Default.Terrain,
                                        accentColor = Color(0xFF7C4DFF),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Ghost Runner Duel
                                if (state.selectedGhostSessionId != null) {
                                    val selectedSession = allSessions.find { it.id == state.selectedGhostSessionId }
                                    val ghostDistanceDiff = state.ghostDistanceMeters
                                    val distKm = (selectedSession?.totalDistanceMeters ?: 0f) / 1000f
                                    val sessionName = "PB %.2f km".format(distKm)

                                    Spacer(modifier = Modifier.height(12.dp))
                                    GhostDuelCard(
                                        ghostDistanceDiff = ghostDistanceDiff,
                                        sessionName = sessionName,
                                        onClearGhost = { viewModel.setGhostSession(null) }
                                    )
                                } else if (!state.isTracking) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { showGhostDialog = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp)
                                    ) {
                                        Icon(Icons.Default.DirectionsRun, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.select_ghost), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                // Activity Selector (Walk / Run)
                                if (!state.isTracking) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    SegmentedActivitySwitch(
                                        currentType = state.activityType,
                                        onTypeSelected = { viewModel.setActivityType(it) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }

                        // Bottom action buttons row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!state.isTracking) {
                                Button(
                                    onClick = {
                                        onStartService()
                                        viewModel.startTracking()
                                        isTrackerExpanded = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, stringResource(R.string.start), modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.start).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        viewModel.stopTracking()
                                        isTrackerExpanded = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                                ) {
                                    Icon(Icons.Default.Stop, stringResource(R.string.stop), modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.stop).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = { isTrackerExpanded = !isTrackerExpanded },
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(
                                    imageVector = if (isTrackerExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = if (isTrackerExpanded) stringResource(R.string.collapse_tracker) else stringResource(R.string.expand_tracker),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherWidget(
    weatherState: WeatherState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .widthIn(max = 280.dp)
            .wrapContentHeight()
    ) {
        when (weatherState) {
            is WeatherState.Idle -> {
                Box(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.detecting_location), style = MaterialTheme.typography.bodySmall)
                }
            }
            is WeatherState.Loading -> {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.checking_weather),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is WeatherState.Error -> {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.weather_unavailable),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onRetry, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.retry),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            is WeatherState.Success -> {
                val weather = weatherState.weather
                val emoji = when {
                    weather.icon.startsWith("01") -> "☀️"
                    weather.icon.startsWith("02") -> "⛅"
                    weather.icon.startsWith("03") || weather.icon.startsWith("04") -> "☁️"
                    weather.icon.startsWith("09") || weather.icon.startsWith("10") -> "🌧️"
                    weather.icon.startsWith("11") -> "⛈️"
                    weather.icon.startsWith("13") -> "❄️"
                    weather.icon.startsWith("50") -> "🌫️"
                    else -> "🌤️"
                }

                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Column {
                            Text(
                                text = "${weather.temp.toInt()}°C",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = weather.description.replaceFirstChar { it.titlecase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = weather.recommendation,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun GhostComparisonWidget(
    userDistance: Float,
    ghostDistance: Float,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .widthIn(max = 200.dp)
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.ghost_comparison_header),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            val diff = userDistance - ghostDistance
            val diffColor = if (diff >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
            val diffText = if (diff >= 0) {
                stringResource(R.string.ahead_by, diff)
            } else {
                stringResource(R.string.behind_by, -diff)
            }
            
            Text(
                text = diffText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = diffColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = if (diff >= 0) "🏆 Leading" else "⚠️ Lagging",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (diff >= 0) Color(0xFF4CAF50) else Color(0xFFFF9800)
            )
        }
    }
}

/**
 * Triggers Android share sheet with app link and promo message.
 */
private fun shareApp(context: Context) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        val text = context.getString(R.string.share_app_text, context.packageName)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, context.getString(R.string.share_app_title))
    context.startActivity(chooser)
}

/**
 * Opens Google Play Store page for user rating and review.
 */
private fun rateApp(context: Context) {
    val packageName = context.packageName
    val marketUri = Uri.parse("market://details?id=$packageName")
    val goToMarket = Intent(Intent.ACTION_VIEW, marketUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    }
    try {
        context.startActivity(goToMarket)
    } catch (e: ActivityNotFoundException) {
        val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
}
