package com.victorkoffed.projektandroid.domain.model

/**
 * Representerar en enskild mätning i realtid från vågen under en bryggning.
 *
 * @property weightGrams Den totala ackumulerade vikten i gram.
 * @property flowRateGramsPerSecond Den aktuella flödeshastigheten i gram per sekund.
 */
data class ScaleMeasurement(
    val weightGrams: Float,
    val flowRateGramsPerSecond: Float
)