package com.earthgame.idle.domain.climate

import com.earthgame.idle.domain.engine.CARBON_CYCLE
import com.earthgame.idle.domain.engine.CLIMATE
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.model.GAS_LIST
import com.earthgame.idle.domain.model.GasAmounts
import com.earthgame.idle.domain.model.GasDoubles
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.gasOf
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Atmospheric state tracks each gas as the concentration *above* its
 * pre-industrial baseline, in the gas's native unit (ppm/ppb/ppt/DU). The
 * baseline is assumed to be a natural steady state — equal natural emission and
 * removal flux — and is not simulated: only the anthropogenic perturbation is.
 * That keeps the numbers meaningful (extra == 0 at game start) without having
 * to model pre-industrial natural gas cycles.
 */
typealias AtmosphereState = GasAmounts

fun createInitialAtmosphere(): AtmosphereState = GasAmounts.ZERO

/**
 * Natural and engineered sinks weaken as the planet warms — permafrost thaw,
 * ocean stratification reducing mixing, forest dieback — floored so removal
 * never fully stops.
 */
fun computeSinkEfficiency(tempAnomalyC: Double): Double {
    val efficiency = 1.0 / (1.0 + CARBON_CYCLE.warmingSinkPenalty * max(0.0, tempAnomalyC))
    return max(CARBON_CYCLE.minSinkEfficiency, efficiency)
}

/**
 * Advances one gas's concentration by [dtSeconds] using the exact analytic
 * solution to `dE/dt = production - k·E` — a first-order decay toward the
 * equilibrium `production/k`.
 *
 * This being closed-form rather than a fixed small-step Euler integration is
 * not just an optimisation: it means a 250 ms live tick and an eight-hour
 * offline catch-up produce numerically identical results for the same elapsed
 * time, which is what lets offline progress be a single calculation instead of
 * a stepped simulation loop. `SimulationParityTest` checks that property
 * directly.
 *
 * @param extraConcentration current concentration above baseline, in the gas's native unit
 * @param productionPerSecond emission rate, in the gas's native unit per second
 * @param removalRateConstant natural per-second removal fraction (1 / lifetime)
 * @param sinkEfficiency multiplier in (0,1] representing weakened sinks under warming
 * @param extraRemovalPerSecond engineered removal (e.g. Direct Air Capture), native unit/s
 */
fun integrateGasConcentration(
    extraConcentration: GameDecimal,
    productionPerSecond: GameDecimal,
    removalRateConstant: Double,
    sinkEfficiency: Double,
    extraRemovalPerSecond: GameDecimal,
    dtSeconds: Double,
): GameDecimal {
    val k = removalRateConstant * sinkEfficiency
    if (dtSeconds <= 0) return extraConcentration

    // Engineered removal acts as a flat subtraction from the net production term.
    val netProduction = productionPerSecond - extraRemovalPerSecond

    if (k <= 0) {
        // No natural decay (shouldn't happen given the sink floor, but stay safe): linear growth.
        return (extraConcentration + netProduction * dtSeconds).clampMin(GameDecimal.ZERO)
    }

    val decayFactor = exp(-k * dtSeconds)
    val equilibrium = netProduction / k
    val result = extraConcentration * decayFactor + equilibrium * (1.0 - decayFactor)
    return result.clampMin(GameDecimal.ZERO)
}

/** Each gas's radiative forcing contribution (W/m²), and the total. */
data class ForcingBreakdown(val perGas: GasDoubles, val total: Double) {
    companion object {
        val ZERO = ForcingBreakdown(GasDoubles.ZERO, 0.0)
    }
}

fun computeForcing(atmosphere: AtmosphereState): ForcingBreakdown {
    val builder = GasDoubles.builder()
    var total = 0.0
    for (gas in GAS_LIST) {
        val concentration = gas.baseline + atmosphere[gas.id].toDouble()
        val contribution = gas.forcing(concentration, gas.baseline)
        builder[gas.id] = contribution
        total += contribution
    }
    return ForcingBreakdown(builder.build(), total)
}

/**
 * Water vapor is modeled as a feedback rather than a direct emission: its
 * equilibrium concentration rises with the *other* gases' warming. The caller
 * feeds the previous temperature anomaly in, which closes the loop one
 * simulation step behind — stable, and it avoids solving forcing and
 * temperature simultaneously.
 */
fun computeWaterVaporFeedbackConcentration(previousTempAnomalyC: Double): Double =
    max(0.0, previousTempAnomalyC) * CLIMATE.h2oFeedbackPpmPerDegree

/**
 * Converts total radiative forcing into a temperature anomaly: linear in the
 * climate-sensitivity term, as in real simplified energy-balance models, with a
 * mild super-linear kicker so extreme lategame forcing escalates rather than
 * flattening out.
 */
fun computeTemperatureAnomaly(totalForcingWm2: Double): Double {
    val forcing = max(0.0, totalForcingWm2)
    val linear = CLIMATE.climateSensitivity * forcing
    val superLinear = CLIMATE.superLinearCoefficient * forcing.pow(CLIMATE.superLinearExponent)
    return linear + superLinear
}

/** Absolute concentration for display: baseline plus the anthropogenic extra. */
fun gasDisplayConcentration(gasId: GasId, atmosphere: AtmosphereState): Double =
    gasOf(gasId).baseline + atmosphere[gasId].toDouble()
