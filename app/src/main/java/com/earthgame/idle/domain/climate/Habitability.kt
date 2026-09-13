package com.earthgame.idle.domain.climate

import com.earthgame.idle.domain.engine.GAME_SECONDS_PER_YEAR
import com.earthgame.idle.domain.engine.HABITABILITY
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Habitability is deliberately not a linear function of temperature alone. It
 * combines five independent 0..1 "goodness" factors, each mapped from a
 * different simulated planetary indicator, and *multiplies* them together.
 * Multiplying rather than averaging means any single factor collapsing to zero
 * drives habitability to zero even if the others are fine — the flavour of "one
 * broken system can end civilization" without a fully coupled climate model.
 *
 * `habitability <= 0` is the reset trigger.
 */
data class PlanetaryIndicators(
    val temperatureAnomalyC: Double,
    val oceanPh: Double,
    val seaLevelRiseMeters: Double,
    val agriculturalOutputFraction: Double,
    val biodiversityFraction: Double,
)

data class HabitabilityFactors(
    val temperature: Double,
    val oceanAcidity: Double,
    val seaLevel: Double,
    val agriculture: Double,
    val biodiversity: Double,
) {
    companion object {
        val PRISTINE = HabitabilityFactors(1.0, 1.0, 1.0, 1.0, 1.0)
    }
}

data class HabitabilityResult(val fraction: Double, val factors: HabitabilityFactors) {
    companion object {
        val PRISTINE = HabitabilityResult(1.0, HabitabilityFactors.PRISTINE)
    }
}

private fun clamp01(x: Double): Double = min(1.0, max(0.0, x))

fun temperatureFactor(tempAnomalyC: Double): Double {
    val t = max(0.0, tempAnomalyC) / HABITABILITY.tempCollapseThreshold
    return clamp01(1.0 - t * t)
}

fun computeOceanPh(co2ExtraPpm: Double): Double =
    HABITABILITY.baselinePh - HABITABILITY.phDriftPerCo2Ppm * max(0.0, co2ExtraPpm)

fun oceanAcidityFactor(ph: Double): Double {
    val range = HABITABILITY.baselinePh - HABITABILITY.phCollapseFloor
    return clamp01((ph - HABITABILITY.phCollapseFloor) / range)
}

fun seaLevelFactor(seaLevelRiseMeters: Double): Double =
    clamp01(1.0 - seaLevelRiseMeters / HABITABILITY.seaLevelCollapseMeters)

/**
 * Sea level rise accumulates with sustained warming exposure over time (the
 * integral of temperature).
 *
 * Deliberately still on the **real** clock. `seaLevelPerDegreeYear` is not a
 * physical rate: it was pre-scaled by about 10^5 precisely so that a run's
 * worth of real seconds produces a meaningful rise, which is the same scaling
 * the simulated calendar now states explicitly. Running it on simulated years
 * *and* leaving the constant alone would apply that scaling twice and drown
 * the planet in minutes; rescaling the constant to compensate would be a
 * change with no behavioural effect. So the year here is a real one, and the
 * only shared thing is the length of a year.
 */
fun integrateSeaLevelRise(currentMeters: Double, tempAnomalyC: Double, dtSeconds: Double): Double {
    val dtYears = dtSeconds / GAME_SECONDS_PER_YEAR
    val rise = HABITABILITY.seaLevelPerDegreeYear * max(0.0, tempAnomalyC) * dtYears
    return currentMeters + rise
}

fun agriculturalOutputFactor(tempAnomalyC: Double): Double {
    val knee = HABITABILITY.agricultureKneeDegrees
    if (tempAnomalyC <= knee) return 1.0 - 0.05 * (tempAnomalyC / knee)
    val excess = tempAnomalyC - knee
    return clamp01(0.95 * exp(-excess / 8.0))
}

fun biodiversityFactor(tempAnomalyC: Double): Double {
    val knee = HABITABILITY.biodiversityKneeDegrees
    if (tempAnomalyC <= knee) return 1.0 - 0.03 * (tempAnomalyC / knee)
    val excess = tempAnomalyC - knee
    return clamp01(0.97 * exp(-excess / 6.0))
}

fun computeHabitability(indicators: PlanetaryIndicators): HabitabilityResult {
    val factors = HabitabilityFactors(
        temperature = temperatureFactor(indicators.temperatureAnomalyC),
        oceanAcidity = oceanAcidityFactor(indicators.oceanPh),
        seaLevel = seaLevelFactor(indicators.seaLevelRiseMeters),
        agriculture = clamp01(indicators.agriculturalOutputFraction),
        biodiversity = clamp01(indicators.biodiversityFraction),
    )

    val fraction = clamp01(
        factors.temperature * factors.oceanAcidity * factors.seaLevel *
            factors.agriculture * factors.biodiversity,
    )

    return HabitabilityResult(fraction, factors)
}
