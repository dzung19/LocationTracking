package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HistoryViewModel
import com.example.data.database.ActivityType
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
    val runSessions by viewModel.runSessions.collectAsStateWithLifecycle()
    val sessionPoints by viewModel.sessionPoints.collectAsStateWithLifecycle()

    val session = remember(runSessions) {
        runSessions.find { it.id == sessionId }
    }

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
        if (pathPoints.isNotEmpty()) {
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
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
                Text("Session not found", style = MaterialTheme.typography.bodyLarge)
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
                        if (pathPoints.isNotEmpty()) {
                            Polyline(
                                points = pathPoints,
                                color = MaterialTheme.colorScheme.primary,
                                width = 12f
                            )
                            Marker(
                                state = rememberMarkerState(position = pathPoints.first()),
                                title = "Start Point"
                            )
                            Marker(
                                state = rememberMarkerState(position = pathPoints.last()),
                                title = "End Point"
                            )
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
                                    text = if (session.activityType == ActivityType.RUNNING) "Running Workout" else "Walking Workout",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$dateStr at $timeStr",
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
                            DetailItem("DISTANCE", "%.2f km".format(session.totalDistanceMeters / 1000f), Modifier.weight(1f))
                            DetailItem("DURATION", durationStr, Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailItem("AVG PACE", avgPaceStr, Modifier.weight(1f))
                            DetailItem("CALORIES", "${session.totalCalories} kcal", Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
