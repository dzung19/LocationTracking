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
import com.example.HistoryViewModel
import com.example.data.database.ActivityType
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import org.koin.androidx.compose.koinViewModel
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.History

val Context.dataStore by preferencesDataStore(name = "LocationPrefs")

@Composable
fun LocationTrackerApp(
    viewModel: LocationViewModel,
    modifier: Modifier = Modifier,
    onStartService: () -> Unit
) {
    val navController = rememberNavController()
    val historyViewModel: HistoryViewModel = koinViewModel()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != null && !currentRoute.startsWith("detail")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentRoute == "tracker",
                        onClick = {
                            if (currentRoute != "tracker") {
                                navController.navigate("tracker") {
                                    popUpTo("tracker") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Map, contentDescription = "Tracker") },
                        label = { Text("Tracker") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = currentRoute == "history",
                        onClick = {
                            if (currentRoute != "history") {
                                navController.navigate("history") {
                                    popUpTo("tracker") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("History") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "tracker",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("tracker") {
                val context = LocalContext.current
                val isServiceBound by viewModel.isServiceBound.collectAsStateWithLifecycle()
                val trackingState by viewModel.trackingState.collectAsStateWithLifecycle()

                var hasLocationPermission by remember {
                    mutableStateOf(context.checkLocationPermissions())
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    hasLocationPermission = fineGranted || coarseGranted
                }

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

                Box(modifier = Modifier.fillMaxSize()) {
                    if (!isServiceBound) {
                        ServiceConnectingScreen()
                    } else {
                        MainTrackerScreen(
                            viewModel = viewModel,
                            state = trackingState,
                            onStartService = onStartService,
                            hasLocationPermission = hasLocationPermission,
                            onRequestPermission = {
                                val req = mutableListOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ).apply {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }.toTypedArray()
                                permissionLauncher.launch(req)
                            },
                            contentPadding = innerPadding
                        )
                    }
                }
            }

            composable("history") {
                HistoryScreen(
                    viewModel = historyViewModel,
                    onNavigateToDetail = { sessionId ->
                        navController.navigate("detail/$sessionId")
                    },
                    modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                )
            }

            composable(
                route = "detail/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
                RunDetailScreen(
                    sessionId = sessionId,
                    viewModel = historyViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
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
    }
}


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
                Marker(
                    state = rememberMarkerState(position = latLng),
                    title = "Current Location",
                    snippet = "Accuracy: ${state.accuracy ?: 0f}m"
                )
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

fun Context.checkLocationPermissions(): Boolean {
    return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
