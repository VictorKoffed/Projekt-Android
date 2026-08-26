package com.victorkoffed.projektandroid.domain.model

/**
 * Represents a single real-time telemetry data point from the smart scale during an active brew session.
 *
 * @property timeMillis The elapsed time reported directly by the scale's internal hardware timer,
 * ensuring precise synchronization independent of the mobile device's clock latency or state.
 */
data class ScaleMeasurement(
    val weightGrams: Float,
    val flowRateGramsPerSecond: Float,
    val timeMillis: Long? = null,
    val batteryPercent: Int? = null
)