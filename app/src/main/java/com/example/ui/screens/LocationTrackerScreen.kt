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
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.entryProvider
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MonitorWeight

val Context.dataStore by preferencesDataStore(name = "LocationPrefs")
val WEIGHT_KEY = floatPreferencesKey("user_weight")

@Serializable
sealed interface ScreenKey : NavKey

@Serializable
object TrackerKey : ScreenKey

@Serializable
object HistoryKey : ScreenKey

@Serializable
data class DetailKey(val sessionId: Long) : ScreenKey
@Composable
fun LocationTrackerApp(
    viewModel: LocationViewModel,
    modifier: Modifier = Modifier,
    onStartService: () -> Unit
) {
    val backStack = rememberNavBackStack<NavKey>(TrackerKey)
    val historyViewModel: HistoryViewModel = koinViewModel()
    
    val currentKey = backStack.lastOrNull()
    val showBottomBar = currentKey is TrackerKey || currentKey is HistoryKey

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentKey is TrackerKey,
                        onClick = {
                            if (currentKey !is TrackerKey) {
                                backStack.clear()
                                backStack.add(TrackerKey)
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
                        selected = currentKey is HistoryKey,
                        onClick = {
                            if (currentKey !is HistoryKey) {
                                backStack.clear()
                                backStack.add(TrackerKey)
                                backStack.add(HistoryKey)
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
        NavDisplay(
            backStack = backStack,
            onBack = {
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                }
            },
            entryProvider = entryProvider {
                entry<TrackerKey> {
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

                entry<HistoryKey> {
                    HistoryScreen(
                        viewModel = historyViewModel,
                        onNavigateToDetail = { sessionId ->
                            backStack.add(DetailKey(sessionId))
                        },
                        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                    )
                }

                entry<DetailKey> { key ->
                    RunDetailScreen(
                        sessionId = key.sessionId,
                        viewModel = historyViewModel,
                        onBackClick = {
                            if (backStack.size > 1) {
                                backStack.removeLastOrNull()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
