package com.dzung.runner.locationtracker.util

/**
 * A rolling window pace calculator that maintains a temporal queue of recent movement samples
 * to filter out instantaneous GPS jitter and provide a smooth, athletic-grade pace.
 */
class RollingPaceCalculator(
    private val windowDurationMs: Long = 12_000L, // 12 seconds rolling window
    private val minWindowMs: Long = 4_000L,       // Minimum 4s required before producing pace
    private val minDeltaMeters: Float = 5.0f,     // Minimum 5m movement required
    private val minPaceSeconds: Int = 150,        // 2:30 /km (world-class sprint threshold)
    private val maxPaceSeconds: Int = 1500        // 25:00 /km (slow walk threshold)
) {
    private data class Sample(val timestampMs: Long, val accumulatedDistanceMeters: Float)

    private val samples = ArrayDeque<Sample>()

    /**
     * Records a new movement sample with timestamp and current total accumulated distance.
     */
    @Synchronized
    fun addSample(timestampMs: Long, accumulatedDistanceMeters: Float) {
        if (accumulatedDistanceMeters.isNaN() || accumulatedDistanceMeters.isInfinite() || accumulatedDistanceMeters < 0f) return
        samples.addLast(Sample(timestampMs, accumulatedDistanceMeters))
        trimOldSamples(timestampMs)
    }

    /**
     * Calculates the pace in seconds per kilometer across the rolling window.
     * Returns null if stationary, insufficient movement, or below minimum walking speed.
     */
    @Synchronized
    fun calculatePace(currentTimeMs: Long): Int? {
        trimOldSamples(currentTimeMs)

        if (samples.size < 2) return null

        val oldest = samples.first()
        val newest = samples.last()

        val timeSpanMs = newest.timestampMs - oldest.timestampMs
        val deltaDistance = newest.accumulatedDistanceMeters - oldest.accumulatedDistanceMeters

        // Ensure we have enough time and distance in the window to produce a reliable value
        if (timeSpanMs < minWindowMs || deltaDistance < minDeltaMeters) {
            return null
        }

        val timeSpanSec = timeSpanMs / 1000f
        val speedMps = deltaDistance / timeSpanSec

        // Cutoff for stationary or imperceptible movement (< 0.5 m/s or ~33 min/km)
        if (speedMps < 0.5f) {
            return null
        }

        val paceSecPerKm = (1000f / speedMps).toInt()
        return paceSecPerKm.coerceIn(minPaceSeconds, maxPaceSeconds)
    }

    /**
     * Resets the rolling window (e.g. on pause or stationary state).
     */
    @Synchronized
    fun reset() {
        samples.clear()
    }

    private fun trimOldSamples(currentTimeMs: Long) {
        val cutoff = currentTimeMs - windowDurationMs
        while (samples.isNotEmpty() && samples.first().timestampMs < cutoff) {
            samples.removeFirst()
        }
    }
}
