package com.dzung.runner.locationtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.content.Intent
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import com.dzung.runner.locationtracker.ui.components.RouteShareBottomSheet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.dzung.runner.locationtracker.R
import com.dzung.runner.locationtracker.HistoryViewModel
import com.dzung.runner.locationtracker.components.BannerAd
import com.dzung.runner.locationtracker.data.database.ActivityType
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    sessionId: Long,
    viewModel: HistoryViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val runSessions by viewModel.runSessions.collectAsStateWithLifecycle()
    val sessionPoints by viewModel.sessionPoints.collectAsStateWithLifecycle()

    val session = remember(runSessions) {
        runSessions.find { it.id == sessionId }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareBottomSheet by remember { mutableStateOf(false) }

    // Trigger loading of points for this session
    LaunchedEffect(sessionId) {
        viewModel.loadPointsForSession(sessionId)
    }

    val points = sessionPoints[sessionId] ?: emptyList()
    val pathPoints = remember(points) {
        points.map { LatLng(it.latitude, it.longitude) }
    }

    val cameraPositionState = rememberCameraPositionState()

    // Fit route in bounds when loaded
    LaunchedEffect(pathPoints) {
        if (pathPoints.size >= 2) {
            try {
                val builder = LatLngBounds.builder()
                pathPoints.forEach { builder.include(it) }
                val bounds = builder.build()
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLngBounds(bounds, 100)
                )
            } catch (e: Exception) {
                // Fallback in case of layout dimensions or empty bounds
                if (pathPoints.isNotEmpty()) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(pathPoints[0], 16f)
                }
            }
        } else if (pathPoints.size == 1) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(pathPoints[0], 16f)
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.delete_activity),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.delete_activity_confirm),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteSession(sessionId)
                        onBackClick()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.delete_activity))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(48.dp)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                    Text(
                        text = stringResource(R.string.activity_details),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                    session?.let { currentSession ->
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_activity),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = { showShareBottomSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.share_route_sheet_title),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            BannerAd(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                adKey = "detail_banner"
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (session == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.session_not_found), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Interactive Map showing the full route
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            myLocationButtonEnabled = false
                        )
                    ) {
                        if (pathPoints.size >= 2) {
                            Polyline(
                                points = pathPoints,
                                color = MaterialTheme.colorScheme.primary,
                                width = 12f
                            )
                            MarkerComposable(
                                state = rememberUpdatedMarkerState(position = pathPoints.first()),
                                anchor = Offset(0.5f, 0.5f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF4CAF50),
                                    contentColor = Color.White,
                                    tonalElevation = 4.dp,
                                    shadowElevation = 4.dp
                                ) {
                                    Text(
                                        text = stringResource(R.string.start_point),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            MarkerComposable(
                                state = rememberUpdatedMarkerState(position = pathPoints.last()),
                                anchor = Offset(0.5f, 0.5f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF44336),
                                    contentColor = Color.White,
                                    tonalElevation = 4.dp,
                                    shadowElevation = 4.dp
                                ) {
                                    Text(
                                        text = stringResource(R.string.end_point),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        } else if (pathPoints.size == 1) {
                            MarkerComposable(
                                state = rememberUpdatedMarkerState(position = pathPoints.first()),
                                anchor = Offset(0.5f, 0.5f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White,
                                    tonalElevation = 4.dp,
                                    shadowElevation = 4.dp
                                ) {
                                    Text(
                                        text = stringResource(R.string.start_point),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (pathPoints.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                tonalElevation = 4.dp,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = if (session.totalDistanceMeters <= 0f) {
                                        stringResource(R.string.stationary_activity)
                                    } else {
                                        stringResource(R.string.no_route_data)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }

                // Info Panel
                val dateFormat = remember { SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()) }
                val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
                val dateStr = dateFormat.format(Date(session.startTimeInMillis))
                val timeStr = timeFormat.format(Date(session.startTimeInMillis))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Title / Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (session.activityType == ActivityType.RUNNING) {
                                    Icons.Default.DirectionsRun
                                } else {
                                    Icons.Default.DirectionsWalk
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (session.activityType == ActivityType.RUNNING) stringResource(R.string.running_workout) else stringResource(R.string.walking_workout),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.session_date_time, dateStr, timeStr),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(16.dp))

                        // Stats grid
                        val elapsedSeconds = if (session.endTimeInMillis != null) {
                            (session.endTimeInMillis - session.startTimeInMillis) / 1000
                        } else {
                            0L
                        }
                        val h = elapsedSeconds / 3600
                        val m = (elapsedSeconds % 3600) / 60
                        val s = elapsedSeconds % 60
                        val durationStr = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)

                        val avgPaceStr = if (session.totalDistanceMeters > 0) {
                            val paceSecondsPerKm = (elapsedSeconds / (session.totalDistanceMeters / 1000f)).toInt()
                            val paceMins = paceSecondsPerKm / 60
                            val paceSecs = paceSecondsPerKm % 60
                            "%d:%02d /km".format(paceMins, paceSecs)
                        } else {
                            "-:--"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailItem(stringResource(R.string.distance_label), "%.2f km".format(session.totalDistanceMeters / 1000f), Modifier.weight(1f))
                            DetailItem(stringResource(R.string.duration_label), durationStr, Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailItem(stringResource(R.string.avg_pace_label), avgPaceStr, Modifier.weight(1f))
                            DetailItem(stringResource(R.string.calories_label), "${session.totalCalories} kcal", Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { showShareBottomSheet = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFC5200), // Strava athletic orange
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.share_route_sheet_title),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showShareBottomSheet && session != null) {
        RouteShareBottomSheet(
            points = points,
            session = session,
            onDismissRequest = { showShareBottomSheet = false }
        )
    }
}
