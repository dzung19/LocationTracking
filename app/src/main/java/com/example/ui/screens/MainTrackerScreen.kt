package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Refresh
import com.example.data.model.WeatherState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.LocationTrackingState
import com.example.LocationViewModel
import com.example.data.database.ActivityType
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch

@Composable
fun MainTrackerScreen(
    viewModel: LocationViewModel,
    state: LocationTrackingState,
    onStartService: () -> Unit,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val LAT_KEY = doublePreferencesKey("last_lat")
    val LON_KEY = doublePreferencesKey("last_lon")

    val locationPrefs by context.dataStore.data.collectAsStateWithLifecycle(initialValue = null)

    if (locationPrefs == null) {
        // Wait for DataStore to load the initial cached location
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val savedLat = locationPrefs?.get(LAT_KEY) ?: 10.762622
    val savedLon = locationPrefs?.get(LON_KEY) ?: 106.660172
    val userWeight = locationPrefs?.get(WEIGHT_KEY) ?: 70f
    var showWeightDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()

    // Fetch weather immediately using best available coordinates (GPS if active, fallback to last saved location)
    val currentLat = state.latitude ?: savedLat
    val currentLon = state.longitude ?: savedLon

    LaunchedEffect(currentLat, currentLon) {
        viewModel.fetchWeather(currentLat, currentLon)
    }
    
    if (showWeightDialog) {
        var weightInput by remember { mutableStateOf(userWeight.toString()) }
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            icon = { Icon(Icons.Default.MonitorWeight, contentDescription = null) },
            title = { Text("Body Weight (kg)") },
            text = {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Weight in kg") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newWeight = weightInput.toFloatOrNull()
                    if (newWeight != null && newWeight > 0) {
                        coroutineScope.launch {
                            context.dataStore.edit { prefs ->
                                prefs[WEIGHT_KEY] = newWeight
                            }
                        }
                        showWeightDialog = false
                    }
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val defaultLocation = LatLng(savedLat, savedLon)
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 16f)
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
            context.dataStore.edit { prefs ->
                prefs[LAT_KEY] = state.latitude
                prefs[LON_KEY] = state.longitude
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
                val paceStr = if (state.distanceMeters > 0) {
                    val paceSecondsPerKm = (state.elapsedTimeSeconds / (state.distanceMeters / 1000f)).toInt()
                    val mins = paceSecondsPerKm / 60
                    val secs = paceSecondsPerKm % 60
                    "%d:%02d".format(mins, secs)
                } else {
                    "-:--"
                }

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
                        val target = currentLatLng ?: defaultLocation
                        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(target, 16f))
                    },
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .testTag("recenter_button"),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.MyLocation, "Recenter Map")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            ) {
                if (!hasLocationPermission) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning Icon",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Location Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please grant permission to track your activity and view your current location.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Permission")
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(
                                    text = "Activity Tracker",
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
                            Spacer(modifier = Modifier.width(12.dp))
                            IconButton(onClick = { showWeightDialog = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        val badgeColor = if (state.isTracking) Color(0xFF4CAF50) else Color(0xFFF44336)
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

                    // Dashboard Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailItem("DISTANCE", "%.2f km".format(state.distanceMeters / 1000f), Modifier.weight(1f))
                        
                        val paceStr = if (state.distanceMeters > 0) {
                            val paceSecondsPerKm = (state.elapsedTimeSeconds / (state.distanceMeters / 1000f)).toInt()
                            val mins = paceSecondsPerKm / 60
                            val secs = paceSecondsPerKm % 60
                            "%d:%02d /km".format(mins, secs)
                        } else {
                            "-:-- /km"
                        }
                        DetailItem("PACE", paceStr, Modifier.weight(1f))
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val h = state.elapsedTimeSeconds / 3600
                        val m = (state.elapsedTimeSeconds % 3600) / 60
                        val s = state.elapsedTimeSeconds % 60
                        val timeStr = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
                        
                        DetailItem("TIME", timeStr, Modifier.weight(1f))
                        DetailItem("CALORIES", "${state.caloriesBurned} kcal", Modifier.weight(1f))
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailItem("ELEVATION", "%.1f m".format(state.elevationMeters), Modifier.weight(1f))
                        val sign = if (state.slopePercentage > 0) "+" else ""
                        DetailItem("SLOPE", "$sign%.1f %%".format(state.slopePercentage), Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!state.isTracking) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            val isWalking = state.activityType == ActivityType.WALKING
                            Button(
                                onClick = { viewModel.setActivityType(ActivityType.WALKING) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isWalking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isWalking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) { Text("Walk 🚶‍♂️") }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                    
                            val isRunning = state.activityType == ActivityType.RUNNING
                            Button(
                                onClick = { viewModel.setActivityType(ActivityType.RUNNING) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isRunning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) { Text("Run 🏃‍♂️") }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                onStartService()
                                viewModel.startTracking()
                            },
                            enabled = !state.isTracking,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, "Start", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { viewModel.stopTracking() },
                            enabled = state.isTracking,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Stop, "Stop", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop", fontWeight = FontWeight.SemiBold)
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
                    Text("Detecting location...", style = MaterialTheme.typography.bodySmall)
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
                        text = "Checking weather...",
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
                            text = "Weather unavailable",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onRetry, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
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
