package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Insert
    suspend fun insertRunSession(runSession: RunSession): Long

    @Update
    suspend fun updateRunSession(runSession: RunSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationPoint(locationPoint: LocationPoint)

    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getLocationPointsForSession(sessionId: Long): Flow<List<LocationPoint>>

    @Query("SELECT * FROM run_sessions WHERE id = :sessionId")
    suspend fun getRunSession(sessionId: Long): RunSession?

    @Query("SELECT * FROM run_sessions ORDER BY startTimeInMillis DESC")
    fun getAllRunSessions(): Flow<List<RunSession>>

    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getLocationPointsForSessionOnce(sessionId: Long): List<LocationPoint>

    @Query("""
        SELECT COALESCE(SUM(totalDistanceMeters), 0.0) as totalDistanceMeters, COALESCE(SUM(totalCalories), 0) as totalCalories 
        FROM run_sessions 
        WHERE startTimeInMillis >= :fromTimeMillis AND startTimeInMillis <= :toTimeMillis
    """)
    fun getStatsInRange(fromTimeMillis: Long, toTimeMillis: Long): Flow<RunStats>
}
