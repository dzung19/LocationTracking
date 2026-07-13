package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "run_sessions")
data class RunSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeInMillis: Long,
    val endTimeInMillis: Long? = null,
    val totalDistanceMeters: Float = 0f,
    val totalCalories: Int = 0,
    val activityType: ActivityType = ActivityType.RUNNING
)

data class RunStats(
    val totalDistanceMeters: Float = 0f,
    val totalCalories: Int = 0
)
