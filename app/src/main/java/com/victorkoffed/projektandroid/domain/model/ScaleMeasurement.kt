package com.victorkoffed.projektandroid.domain.model

/**
 * Representerar en enskild mätning i realtid från vågen under en bryggning.
 *
 * @property weightGrams Den totala ackumulerade vikten i gram.
 * @property flowRateGramsPerSecond Den aktuella flödeshastigheten i gram per sekund.
 * @property timeMillis Tiden i millisekunder som rapporterats direkt från vågens interna timer (om tillgänglig).
 * @property batteryPercent Den rapporterade batterinivån i procent (om tillgänglig).
 */
data class ScaleMeasurement(
    val weightGrams: Float,
    val flowRateGramsPerSecond: Float,
    val timeMillis: Long? = null, // Tiden från vågen (nullable)
    val batteryPercent: Int? = null
)