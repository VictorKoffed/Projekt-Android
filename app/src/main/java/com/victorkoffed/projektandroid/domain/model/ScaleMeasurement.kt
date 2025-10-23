package com.victorkoffed.projektandroid.domain.model

/**
 * Representerar en enskild mätning från vågen.
 *
 * @param weightGrams Vikten i gram.
 * @param flowRateGramsPerSecond Flödeshastigheten i gram/sekund.
 */
data class ScaleMeasurement(
    val weightGrams: Float,
    val flowRateGramsPerSecond: Float // <-- UPPDATERAD RAD
)