package com.dzung.runner.locationtracker.service

import android.location.Location
import android.util.Log
import com.dzung.runner.locationtracker.data.database.LocationPoint
import com.dzung.runner.locationtracker.data.database.RunDao
import com.google.android.gms.maps.model.LatLng

data class GhostPoint(
    val latLng: LatLng,
    val elapsedSeconds: Long,
    val accumulatedDistanceMeters: Float
)

data class InterpolatedGhostState(
    val latitude: Double?,
    val longitude: Double?,
    val distanceMeters: Float
)

class GhostRunnerManager {
    companion object {
        private const val TAG = "GhostRunnerManager"
    }

    private var ghostPoints: List<GhostPoint> = emptyList()

    fun hasGhost(): Boolean = ghostPoints.isNotEmpty()

    fun clear() {
        ghostPoints = emptyList()
    }

    suspend fun loadGhostSession(sessionId: Long, runDao: RunDao): List<LatLng> {
        return try {
            val points = runDao.getLocationPointsForSessionOnce(sessionId)
            val sorted = points.sortedBy { it.timestamp }
            val ghostStartTime = sorted.firstOrNull()?.timestamp ?: 0L
            val pointsList = mutableListOf<GhostPoint>()
            var dist = 0f
            var prevPoint: LocationPoint? = null

            sorted.forEach { pt ->
                if (!pt.latitude.isNaN() && !pt.longitude.isNaN()) {
                    prevPoint?.let { prev ->
                        val results = FloatArray(1)
                        Location.distanceBetween(prev.latitude, prev.longitude, pt.latitude, pt.longitude, results)
                        val stepDist = results[0]
                        if (!stepDist.isNaN() && stepDist >= 0f) {
                            dist += stepDist
                        }
                    }
                    val elapsed = (pt.timestamp - ghostStartTime) / 1000
                    pointsList.add(GhostPoint(LatLng(pt.latitude, pt.longitude), elapsed, dist))
                    prevPoint = pt
                }
            }
            ghostPoints = pointsList
            pointsList.map { it.latLng }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ghost session points", e)
            emptyList()
        }
    }

    fun interpolateGhost(elapsedSeconds: Long): InterpolatedGhostState {
        if (ghostPoints.isEmpty()) {
            return InterpolatedGhostState(null, null, 0f)
        }

        if (elapsedSeconds <= ghostPoints.first().elapsedSeconds) {
            val first = ghostPoints.first()
            return InterpolatedGhostState(first.latLng.latitude, first.latLng.longitude, first.accumulatedDistanceMeters)
        }

        if (elapsedSeconds >= ghostPoints.last().elapsedSeconds) {
            val last = ghostPoints.last()
            return InterpolatedGhostState(last.latLng.latitude, last.latLng.longitude, last.accumulatedDistanceMeters)
        }

        for (i in 0 until ghostPoints.size - 1) {
            val p1 = ghostPoints[i]
            val p2 = ghostPoints[i + 1]
            if (elapsedSeconds >= p1.elapsedSeconds && elapsedSeconds <= p2.elapsedSeconds) {
                val t1 = p1.elapsedSeconds
                val t2 = p2.elapsedSeconds
                val duration = t2 - t1
                val fraction = if (duration > 0) (elapsedSeconds - t1).toFloat() / duration else 0f

                val lat = p1.latLng.latitude + fraction * (p2.latLng.latitude - p1.latLng.latitude)
                val lon = p1.latLng.longitude + fraction * (p2.latLng.longitude - p1.latLng.longitude)
                val dist = p1.accumulatedDistanceMeters + fraction * (p2.accumulatedDistanceMeters - p1.accumulatedDistanceMeters)

                return InterpolatedGhostState(lat, lon, dist)
            }
        }

        val last = ghostPoints.last()
        return InterpolatedGhostState(last.latLng.latitude, last.latLng.longitude, last.accumulatedDistanceMeters)
    }

    fun alignGhostRoute(userStartLat: Double, userStartLon: Double): List<LatLng>? {
        try {
            if (userStartLat.isNaN() || userStartLon.isNaN()) return null
            val firstGhost = ghostPoints.firstOrNull() ?: return null
            if (firstGhost.latLng.latitude.isNaN() || firstGhost.latLng.longitude.isNaN()) return null

            val results = FloatArray(1)
            Location.distanceBetween(
                userStartLat, userStartLon,
                firstGhost.latLng.latitude, firstGhost.latLng.longitude,
                results
            )
            val distance = results[0]
            if (distance.isNaN()) return null

            // If the start points are more than 150m apart, offset the whole path to align with user start
            if (distance > 150f) {
                val latOffset = userStartLat - firstGhost.latLng.latitude
                val lonOffset = userStartLon - firstGhost.latLng.longitude

                ghostPoints = ghostPoints.mapNotNull { pt ->
                    val newLat = pt.latLng.latitude + latOffset
                    val newLon = pt.latLng.longitude + lonOffset
                    if (newLat.isNaN() || newLon.isNaN()) null
                    else pt.copy(latLng = LatLng(newLat, newLon))
                }

                return ghostPoints.map { it.latLng }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error aligning ghost route", e)
        }
        return null
    }
}
