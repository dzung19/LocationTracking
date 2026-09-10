package com.dzung.runner.locationtracker.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.dzung.runner.locationtracker.HistoryViewModel
import com.dzung.runner.locationtracker.LocationViewModel
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Keep
@Serializable
sealed interface ScreenKey : NavKey

@Keep
@Serializable
object TrackerKey : ScreenKey

@Keep
@Serializable
object HistoryKey : ScreenKey

@Keep
@Serializable
data class DetailKey(val sessionId: Long) : ScreenKey

@Composable
fun LocationTrackerApp(
    viewModel: LocationViewModel,
    modifier: Modifier = Modifier,
    onStartService: () -> Unit
) {
    val context = LocalContext.current
    val isServiceBound by viewModel.isServiceBound.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()

    val isAppReady = isServiceBound && userPreferences != null

    val backStack = rememberNavBackStack(TrackerKey)
    val currentKey = backStack.lastOrNull()
    val showBottomBar = isAppReady && (currentKey is TrackerKey || currentKey is HistoryKey)

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
        Crossfade(
            targetState = isAppReady,
            animationSpec = tween(durationMillis = 400),
            label = "splash_crossfade"
        ) { ready ->
            if (!ready) {
                SplashScreen()
            } else {
                NavDisplay(
                    backStack = backStack,
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        }
                    },
                    entryProvider = entryProvider {
                        entry<TrackerKey> {
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

                            MainTrackerScreen(
                                viewModel = viewModel,
                                state = trackingState,
                                userPreferences = userPreferences!!,
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

                        entry<HistoryKey> {
                            val historyViewModel: HistoryViewModel = koinViewModel()
                            HistoryScreen(
                                viewModel = historyViewModel,
                                onNavigateToDetail = { sessionId ->
                                    backStack.add(DetailKey(sessionId))
                                },
                                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                            )
                        }

                        entry<DetailKey> { key ->
                            val historyViewModel: HistoryViewModel = koinViewModel()
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
    }
}
