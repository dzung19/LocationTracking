package com.dzung.runner.locationtracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dzung.runner.locationtracker.MainActivity
import com.dzung.runner.locationtracker.LocationTrackingService
import com.dzung.runner.locationtracker.LocationTrackingState
import com.dzung.runner.locationtracker.data.database.ActivityType

object TrackingNotificationHelper {
    private const val TAG = "TrackingNotificationHelper"
    const val CHANNEL_ID = "location_tracking_channel"
    const val NOTIFICATION_ID = 101

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel displaying active GPS location tracking updates"
                enableLights(false)
                enableVibration(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun buildStatusNotification(
        context: Context,
        activityType: ActivityType,
        contentText: String
    ): Notification {
        val stopIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val activityPendingIntent = PendingIntent.getActivity(
            context,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (activityType == ActivityType.RUNNING) "Running..." else "Walking...")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(activityPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    fun updateNotificationContent(
        context: Context,
        notificationManager: NotificationManager,
        state: LocationTrackingState
    ) {
        try {
            val distanceKm = state.distanceMeters / 1000f
            val displayPace = state.currentPaceSecondsPerKm ?: state.averagePaceSecondsPerKm
            val paceDisplay = displayPace?.let { paceSec ->
                "%d:%02d/km".format(paceSec / 60, paceSec % 60)
            } ?: "--:--"
            val text = "Dist: %.2f km | Pace: %s | Time: %d s".format(
                distanceKm,
                paceDisplay,
                state.elapsedTimeSeconds
            )
            val notification = buildStatusNotification(context, state.activityType, text)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification content", e)
        }
    }
}
