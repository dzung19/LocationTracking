package com.dzung.runner.locationtracker.service

import android.hardware.SensorManager
import com.dzung.runner.locationtracker.data.database.ActivityType

object FitnessMetricsCalculator {

    /**
     * Look up Metabolic Equivalent of Task (MET) based on speed and activity type.
     */
    fun getMET(speedKmh: Float, activityType: ActivityType): Float {
        // If speed is very low, treat as standing still / resting
        if (speedKmh < 1.0f) return 1.3f // Approximate resting MET

        return if (activityType == ActivityType.WALKING) {
            when {
                speedKmh < 3.2f -> 2.0f
                speedKmh < 4.0f -> 3.0f
                speedKmh < 4.8f -> 3.3f
                speedKmh < 5.6f -> 3.8f
                speedKmh < 6.4f -> 4.3f
                speedKmh < 7.2f -> 5.0f
                else -> 6.0f // brisk walking
            }
        } else { // RUNNING
            when {
                speedKmh < 6.4f -> 5.0f // slow jog
                speedKmh < 8.0f -> 6.0f
                speedKmh < 9.7f -> 8.3f
                speedKmh < 11.3f -> 9.8f
                speedKmh < 12.9f -> 11.0f
                speedKmh < 14.5f -> 11.8f
                speedKmh < 16.1f -> 12.8f
                else -> 14.5f // fast running
            }
        }
    }

    /**
     * Computes calorie burn increment using standard ACSM MET formula:
     * Calories = (MET * weightKg * 3.5 / 200) * timeMinutes
     */
    fun calculateCaloriesDelta(
        speedMps: Float,
        activityType: ActivityType,
        weightKg: Float,
        timeDeltaMs: Long
    ): Float {
        val timeDeltaMinutes = timeDeltaMs / 60000f
        if (timeDeltaMinutes <= 0f) return 0f

        val speedKmh = speedMps * 3.6f
        val met = getMET(speedKmh, activityType)
        val validWeight = if (weightKg > 0f && !weightKg.isNaN()) weightKg else 70f
        val caloriesDelta = (met * validWeight * 3.5f / 200f) * timeDeltaMinutes
        return if (!caloriesDelta.isNaN() && !caloriesDelta.isInfinite() && caloriesDelta > 0f) {
            caloriesDelta
        } else {
            0f
        }
    }

    /**
     * Converts barometric pressure reading (hPa/mbar) to altitude in meters.
     */
    fun getAltitudeFromPressure(pressureHpa: Float): Float {
        if (pressureHpa <= 0f || pressureHpa.isNaN()) return 0f
        val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressureHpa)
        return if (altitude.isNaN() || altitude.isInfinite()) 0f else altitude
    }

    /**
     * Calculates slope percentage: (deltaElevation / deltaDistance) * 100
     */
    fun calculateSlopePercentage(deltaElevation: Float, deltaDistance: Float): Float {
        if (deltaDistance <= 0f || deltaDistance.isNaN() || deltaElevation.isNaN()) return 0f
        val slope = (deltaElevation / deltaDistance) * 100f
        return if (slope.isNaN() || slope.isInfinite()) 0f else slope
    }
}
