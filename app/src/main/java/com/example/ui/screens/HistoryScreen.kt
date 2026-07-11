package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HistoryViewModel
import com.example.data.database.ActivityType
import com.example.data.database.LocationPoint
import com.example.data.database.RunSession
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val runSessions by viewModel.runSessions.collectAsStateWithLifecycle()
    val sessionPoints by viewModel.sessionPoints.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity History", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (runSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "No History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Activities Recorded Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your completed runs and walks will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(runSessions, key = { it.id }) { session ->
                    // Trigger loading of points for this session to display canvas preview
                    LaunchedEffect(session.id) {
                        viewModel.loadPointsForSession(session.id)
                    }

                    val points = sessionPoints[session.id] ?: emptyList()
                    HistoryCard(
                        session = session,
                        points = points,
                        onClick = { onNavigateToDetail(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryCard(
    session: RunSession,
    points: List<LocationPoint>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("EEE, MMM dd, yyyy • hh:mm a", Locale.getDefault()) }
    val dateStr = remember(session.startTimeInMillis) { dateFormat.format(Date(session.startTimeInMillis)) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Header: Activity Type & Date
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (session.activityType == ActivityType.RUNNING) {
                            Icons.Default.DirectionsRun
                        } else {
                            Icons.Default.DirectionsWalk
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (session.activityType == ActivityType.RUNNING) "Running" else "Walking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Canvas Polyline Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                if (points.isNotEmpty()) {
                    RouteCanvasPreview(
                        points = points,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No route data available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Metrics Summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Distance
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Distance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.2f km".format(session.totalDistanceMeters / 1000f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Time
                val elapsedSeconds = if (session.endTimeInMillis != null) {
                    (session.endTimeInMillis - session.startTimeInMillis) / 1000
                } else {
                    0L
                }
                val h = elapsedSeconds / 3600
                val m = (elapsedSeconds % 3600) / 60
                val s = elapsedSeconds % 60
                val timeStr = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Duration",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Pace
                val avgPaceStr = if (session.totalDistanceMeters > 0) {
                    val paceSecondsPerKm = (elapsedSeconds / (session.totalDistanceMeters / 1000f)).toInt()
                    val paceMins = paceSecondsPerKm / 60
                    val paceSecs = paceSecondsPerKm % 60
                    "%d:%02d /km".format(paceMins, paceSecs)
                } else {
                    "-:--"
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Avg Pace",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = avgPaceStr,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Calories
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Calories",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${session.totalCalories} kcal",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun RouteCanvasPreview(
    points: List<LocationPoint>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }

        val latRange = maxLat - minLat
        val lngRange = maxLng - minLng

        val maxRange = maxOf(latRange, lngRange)
        val finalLatRange = if (maxRange == 0.0) 1.0 else maxRange
        val finalLngRange = if (maxRange == 0.0) 1.0 else maxRange

        // Aspect ratio fits logic
        val width = size.width
        val height = size.height

        // Find route centroid to shift to center
        val routeWidth = (lngRange / finalLngRange * width).toFloat()
        val routeHeight = (latRange / finalLatRange * height).toFloat()
        val offsetX = (width - routeWidth) / 2f
        val offsetY = (height - routeHeight) / 2f

        val path = Path()
        points.forEachIndexed { index, point ->
            val rawX = ((point.longitude - minLng) / finalLngRange * width).toFloat()
            val rawY = (height - ((point.latitude - minLat) / finalLatRange * height)).toFloat()

            // Scale down and add offset padding
            val padding = 16f
            val x = offsetX + padding + rawX * (width - 2 * padding - offsetX * 2) / width
            val y = offsetY + padding + rawY * (height - 2 * padding - offsetY * 2) / height

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(
                width = 8f,
                join = StrokeJoin.Round,
                cap = StrokeCap.Round
            )
        )
    }
}
