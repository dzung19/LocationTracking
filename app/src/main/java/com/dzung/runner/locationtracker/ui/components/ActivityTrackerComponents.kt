package com.dzung.runner.locationtracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dzung.runner.locationtracker.R
import com.dzung.runner.locationtracker.data.database.ActivityType

/**
 * Pulsing live radar dot indicating active GPS telemetry.
 */
@Composable
fun PulsingLiveDot(
    modifier: Modifier = Modifier,
    pulseColor: Color = Color(0xFF00E676)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier.size(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size(10.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(pulseColor.copy(alpha = alpha))
        )
        // Core dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(pulseColor)
        )
    }
}

/**
 * Modern Segmented Capsule Control to toggle between Walk and Run.
 */
@Composable
fun SegmentedActivitySwitch(
    currentType: ActivityType,
    onTypeSelected: (ActivityType) -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = currentType == ActivityType.RUNNING

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Walk Option
            ActivityOptionPill(
                title = stringResource(R.string.walk),
                icon = Icons.Default.DirectionsWalk,
                isSelected = !isRunning,
                onClick = { onTypeSelected(ActivityType.WALKING) },
                modifier = Modifier.weight(1f)
            )

            // Run Option
            ActivityOptionPill(
                title = stringResource(R.string.run),
                icon = Icons.Default.DirectionsRun,
                isSelected = isRunning,
                onClick = { onTypeSelected(ActivityType.RUNNING) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActivityOptionPill(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 250),
        label = "pill_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 250),
        label = "pill_fg"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = contentColor
        )
    }
}

/**
 * Prominent Hero Stat display for Total Distance.
 */
@Composable
fun HeroStatDisplay(
    distanceMeters: Float,
    modifier: Modifier = Modifier
) {
    val distanceKm = distanceMeters / 1000f
    val formattedDistance = "%.2f".format(distanceKm)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = formattedDistance,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 52.sp,
                    lineHeight = 56.sp
                ),
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "KM",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Text(
            text = stringResource(R.string.distance_label),
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 1.5.sp
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Clean, color-coded metric tile for duration, pace, calories, elevation, etc.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.8.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Real-time Ghost Duel Card showing distance gap and lead/lag state.
 */
@Composable
fun GhostDuelCard(
    ghostDistanceDiff: Float,
    sessionName: String,
    onClearGhost: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLeading = ghostDistanceDiff >= 0
    val duelColor = if (isLeading) Color(0xFF00C853) else Color(0xFFFF9800)
    val diffAbs = kotlin.math.abs(ghostDistanceDiff)
    val gapText = if (isLeading) {
        "+%.1f m ahead".format(diffAbs)
    } else {
        "-%.1f m behind".format(diffAbs)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(duelColor.copy(alpha = 0.12f))
            .border(1.dp, duelColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = duelColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isLeading) "🏆 LEADING GHOST" else "⚠️ BEHIND GHOST",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = duelColor
                    )
                    Text(
                        text = "$gapText • $sessionName",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onClearGhost,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear Ghost",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Compact 1-line stats bar displayed when the tracker is minimized/collapsed.
 */
@Composable
fun CollapsedMiniHud(
    isTracking: Boolean,
    distanceMeters: Float,
    elapsedTimeSeconds: Long,
    activityType: ActivityType,
    modifier: Modifier = Modifier,
    currentPaceSecondsPerKm: Int? = null
) {
    val distKm = distanceMeters / 1000f
    val paceStr = currentPaceSecondsPerKm?.let { paceSec ->
        val mins = paceSec / 60
        val secs = paceSec % 60
        "%d:%02d/km".format(mins, secs)
    } ?: "-:--"

    val h = elapsedTimeSeconds / 3600
    val m = (elapsedTimeSeconds % 3600) / 60
    val s = elapsedTimeSeconds % 60
    val timeStr = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (isTracking) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PulsingLiveDot()
                Text(
                    text = "%.2f km".format(distKm),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = paceStr,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00C853)
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val icon = if (activityType == ActivityType.RUNNING) Icons.Default.DirectionsRun else Icons.Default.DirectionsWalk
                val label = if (activityType == ActivityType.RUNNING) stringResource(R.string.run) else stringResource(R.string.walk)
                
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = stringResource(R.string.system_idle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
