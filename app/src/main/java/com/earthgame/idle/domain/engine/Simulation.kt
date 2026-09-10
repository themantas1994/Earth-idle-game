package com.earthgame.idle.domain.engine

import com.earthgame.idle.domain.climate.agriculturalOutputFactor
import com.earthgame.idle.domain.climate.biodiversityFactor
import com.earthgame.idle.domain.climate.computeForcing
import com.earthgame.idle.domain.climate.computeHabitability
import com.earthgame.idle.domain.climate.computeOceanPh
import com.earthgame.idle.domain.climate.computeSinkEfficiency
import com.earthgame.idle.domain.climate.computeTemperatureAnomaly
import com.earthgame.idle.domain.climate.computeWaterVaporFeedbackConcentration
import com.earthgame.idle.domain.climate.gasDisplayConcentration
import com.earthgame.idle.domain.climate.integrateGasConcentration
import com.earthgame.idle.domain.climate.integrateSeaLevelRise
import com.earthgame.idle.domain.climate.PlanetaryIndicators
import com.earthgame.idle.domain.model.GAS_LIST
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.GasAmounts
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.RESOURCE_LIST
import com.earthgame.idle.domain.model.ResourceAmounts
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.naturalRemovalRateConstant
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.domain.technologies.ALL_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.TechBranch
import com.earthgame.idle.domain.technologies.TechKind
import com.earthgame.idle.domain.technologies.Technology
import kotlin.math.max

/** Aggregated, ready-to-apply multiplier bundle for one simulation step. */
data class EffectiveMultipliers(
    val global: Double,
    val perBranch: Map<TechBranch, Double>,
    val perGas: Map<GasId, Double>,
    val perResource: Map<ResourceId, Double>,
    val research: Double,
    val allGas: Double,
) {
    companion object {
        val IDENTITY = EffectiveMultipliers(1.0, emptyMap(), emptyMap(), emptyMap(), 1.0, 1.0)
    }
}

/**
 * A transient multiplier layer — random events, challenge modifiers — folded in
 * alongside prestige and technology effects.
 */
data class MultiplierContribution(
    val global: Double? = null,
    val research: Double? = null,
    val allGas: Double? = null,
    val perGas: Map<GasId, Double> = emptyMap(),
    val perBranch: Map<TechBranch, Double> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = global == null && research == null && allGas == null && perGas.isEmpty() && perBranch.isEmpty()

    companion object {
        val NONE = MultiplierContribution()
    }
}

/**
 * Folds every owned multiplier/choice technology, every owned prestige upgrade
 * and any transient contributions into one bundle, so per-tick production maths
 * is a single lookup rather than re-walking the tech tree.
 */
fun computeEffectiveMultipliers(
    techOwned: Map<String, Int>,
    prestige: PrestigeMultipliers,
    extra: List<MultiplierContribution> = emptyList(),
): EffectiveMultipliers {
    var global = prestige.global
    var research = prestige.research
    var allGas = prestige.allGas
    val perGas = prestige.perGas.toMutableMap()
    val perBranch = mutableMapOf<TechBranch, Double>()
    val perResource = mutableMapOf<ResourceId, Double>()

    for (contribution in extra) {
        contribution.global?.let { global *= it }
        contribution.research?.let { research *= it }
        contribution.allGas?.let { allGas *= it }
        for ((gasId, multiplier) in contribution.perGas) {
            perGas[gasId] = (perGas[gasId] ?: 1.0) * multiplier
        }
        for ((branch, multiplier) in contribution.perBranch) {
            perBranch[branch] = (perBranch[branch] ?: 1.0) * multiplier
        }
    }

    for (tech in ALL_TECHNOLOGIES) {
        if ((techOwned[tech.id] ?: 0) <= 0) continue
        val effect = tech.effect
        effect.globalProductionMultiplier?.let { global *= it }
        effect.researchMultiplier?.let { research *= it }
        effect.branchProductionMultiplier?.let { (branch, multiplier) ->
            perBranch[branch] = (perBranch[branch] ?: 1.0) * multiplier
        }
        effect.gasProductionMultiplier?.let { (gas, multiplier) ->
            perGas[gas] = (perGas[gas] ?: 1.0) * multiplier
        }
        effect.resourceProductionMultiplier?.let { (resource, multiplier) ->
            perResource[resource] = (perResource[resource] ?: 1.0) * multiplier
        }
    }

    return EffectiveMultipliers(global, perBranch, perGas, perResource, research, allGas)
}

/**
 * Everything that scales one building's output: the global and per-branch
 * multipliers it shares with the rest of the civilization, times the ownership
 * bonus it has earned on its own.
 */
private fun techScale(tech: Technology, owned: Int, multipliers: EffectiveMultipliers): Double =
    multipliers.global * (multipliers.perBranch[tech.branch] ?: 1.0) * ownershipMultiplier(owned)

data class ProductionRates(
    /** Before removal — "how much civilization is emitting". */
    val gasGrossKgPerS: GasAmounts,
    /** Engineered removal: carbon capture, reforestation, terraforming engines. */
    val gasRemovalKgPerS: GasAmounts,
    val resourcePerS: ResourceAmounts,
) {
    companion object {
        val ZERO = ProductionRates(GasAmounts.ZERO, GasAmounts.ZERO, ResourceAmounts.ZERO)
    }
}

/**
 * What one technology contributes at [owned] units, with every multiplier
 * applied. Split out from [computeProductionRates] so the Production screen can
 * show a building's own contribution rather than the global total for the gases
 * it happens to emit.
 */
fun computeTechProductionRates(
    tech: Technology,
    owned: Int,
    multipliers: EffectiveMultipliers,
): ProductionRates {
    if (owned <= 0 || tech.kind != TechKind.GENERATOR) return ProductionRates.ZERO

    val scale = techScale(tech, owned, multipliers)
    val gross = GasAmounts.builder()
    val removal = GasAmounts.builder()
    val resources = ResourceAmounts.builder()

    for ((gasId, perUnit) in tech.effect.gasProductionPerUnit) {
        val gasMultiplier = (multipliers.perGas[gasId] ?: 1.0) * multipliers.allGas
        gross[gasId] = gd(perUnit) * owned * scale * gasMultiplier
    }
    for ((gasId, perUnit) in tech.effect.gasRemovalPerUnit) {
        removal[gasId] = gd(perUnit) * owned * scale
    }
    for ((resourceId, perUnit) in tech.effect.resourceProductionPerUnit) {
        val researchBonus = if (resourceId == ResourceId.RESEARCH) multipliers.research else 1.0
        val resourceMultiplier = (multipliers.perResource[resourceId] ?: 1.0) * researchBonus
        resources[resourceId] = gd(perUnit) * owned * scale * resourceMultiplier
    }

    return ProductionRates(gross.build(), removal.build(), resources.build())
}

/**
 * Sums every owned generator's per-unit output into total production rates.
 *
 * This is the hottest function in the game — it runs on every tick and on every
 * UI refresh — so it accumulates into mutable builders and walks only the
 * technologies actually owned, rather than materialising a per-technology
 * result and folding it in.
 */
fun computeProductionRates(
    techOwned: Map<String, Int>,
    multipliers: EffectiveMultipliers,
): ProductionRates {
    val gross = GasAmounts.builder()
    val removal = GasAmounts.builder()
    val resources = ResourceAmounts.builder()

    for (tech in ALL_TECHNOLOGIES) {
        val owned = techOwned[tech.id] ?: 0
        if (owned <= 0 || tech.kind != TechKind.GENERATOR) continue

        val scale = techScale(tech, owned, multipliers)

        for ((gasId, perUnit) in tech.effect.gasProductionPerUnit) {
            val gasMultiplier = (multipliers.perGas[gasId] ?: 1.0) * multipliers.allGas
            gross.add(gasId, gd(perUnit) * owned * scale * gasMultiplier)
        }
        for ((gasId, perUnit) in tech.effect.gasRemovalPerUnit) {
            removal.add(gasId, gd(perUnit) * owned * scale)
        }
        for ((resourceId, perUnit) in tech.effect.resourceProductionPerUnit) {
            val researchBonus = if (resourceId == ResourceId.RESEARCH) multipliers.research else 1.0
            val resourceMultiplier = (multipliers.perResource[resourceId] ?: 1.0) * researchBonus
            resources.add(resourceId, gd(perUnit) * owned * scale * resourceMultiplier)
        }
    }

    return ProductionRates(gross.build(), removal.build(), resources.build())
}

/** Overall civilization progress score: the sum of (tier + 1) across every distinct owned technology. */
fun computeCivLevel(techOwned: Map<String, Int>): Int {
    var level = 0
    for (tech in ALL_TECHNOLOGIES) {
        if ((techOwned[tech.id] ?: 0) > 0) level += tech.tier + 1
    }
    return level
}

data class SimulationStepResult(val state: GameState, val productionRates: ProductionRates)

/**
 * Advances the whole simulation by [dtSeconds] of in-game time.
 *
 * This is the single source of truth for how gases, resources, temperature and
 * habitability evolve, used identically for live 250 ms ticks and for offline
 * catch-up, so both paths are guaranteed consistent. The gas integration is
 * closed-form (see `integrateGasConcentration`), which is what makes one call
 * with `dt = 8 hours` produce the same numbers as 115,200 calls with
 * `dt = 0.25 s`.
 */
fun simulateStep(
    state: GameState,
    dtSeconds: Double,
    prestige: PrestigeMultipliers,
    extraMultipliers: List<MultiplierContribution> = emptyList(),
): SimulationStepResult {
    val multipliers = computeEffectiveMultipliers(state.techOwned, prestige, extraMultipliers)

    if (dtSeconds <= 0) {
        return SimulationStepResult(state, computeProductionRates(state.techOwned, multipliers))
    }

    val rates = computeProductionRates(state.techOwned, multipliers)
    val sinkEfficiency = computeSinkEfficiency(state.temperatureAnomalyC)

    val atmosphere = state.atmosphere.toBuilder()
    val runGasTotals = state.runStats.totalGasProducedKg.toBuilder()
    val lifetimeGasTotals = state.lifetimeStats.totalGasProducedKg.toBuilder()

    for (gas in GAS_LIST) {
        val k = naturalRemovalRateConstant(gas)
        val grossKgPerS = rates.gasGrossKgPerS[gas.id]
        var productionRate = grossKgPerS / gas.massPerUnit
        val removalRate = rates.gasRemovalKgPerS[gas.id] / gas.massPerUnit

        if (gas.id == GasId.H2O) {
            // Water vapor is feedback, not accumulation: drive it toward an
            // equilibrium set by the *previous* temperature anomaly, using its
            // own (very short) lifetime as the relaxation rate.
            val equilibrium = computeWaterVaporFeedbackConcentration(state.temperatureAnomalyC)
            productionRate = gd(equilibrium * k)
        }

        atmosphere[gas.id] = integrateGasConcentration(
            state.atmosphere[gas.id],
            productionRate,
            k,
            sinkEfficiency,
            removalRate,
            dtSeconds,
        )

        if (gas.directlyEmitted) {
            val producedThisStep = grossKgPerS * dtSeconds
            runGasTotals.add(gas.id, producedThisStep)
            lifetimeGasTotals.add(gas.id, producedThisStep)
        }
    }

    val newAtmosphere = atmosphere.build()

    val resources = state.resources.toBuilder()
    for (resource in RESOURCE_LIST) {
        resources.add(resource.id, rates.resourcePerS[resource.id] * dtSeconds)
    }

    val forcing = computeForcing(newAtmosphere)
    val newTemp = computeTemperatureAnomaly(forcing.total)
    val avgTempForSeaLevel = (state.temperatureAnomalyC + newTemp) / 2.0
    val newSeaLevel = integrateSeaLevelRise(state.seaLevelRiseMeters, avgTempForSeaLevel, dtSeconds)
    val co2Ppm = gasDisplayConcentration(GasId.CO2, newAtmosphere)
    val oceanPh = computeOceanPh(newAtmosphere[GasId.CO2].toDouble())

    val habitability = computeHabitability(
        PlanetaryIndicators(
            temperatureAnomalyC = newTemp,
            oceanPh = oceanPh,
            seaLevelRiseMeters = newSeaLevel,
            agriculturalOutputFraction = agriculturalOutputFactor(newTemp),
            biodiversityFraction = biodiversityFactor(newTemp),
        ),
    )

    val totalGrossRate = rates.gasGrossKgPerS.sum()

    val newState = state.copy(
        atmosphere = newAtmosphere,
        resources = resources.build(),
        seaLevelRiseMeters = newSeaLevel,
        previousTemperatureAnomalyC = state.temperatureAnomalyC,
        temperatureAnomalyC = newTemp,
        forcing = forcing,
        habitability = habitability,
        oceanPh = oceanPh,
        runStats = state.runStats.copy(
            totalGasProducedKg = runGasTotals.build(),
            peakForcingWm2 = max(state.runStats.peakForcingWm2, forcing.total),
            peakTemperatureC = max(state.runStats.peakTemperatureC, newTemp),
            peakCo2Ppm = max(state.runStats.peakCo2Ppm, co2Ppm),
            peakGasProductionRateKgPerS = state.runStats.peakGasProductionRateKgPerS.max(totalGrossRate),
        ),
        lifetimeStats = state.lifetimeStats.copy(
            totalPlayTimeSeconds = state.lifetimeStats.totalPlayTimeSeconds + dtSeconds,
            totalGasProducedKg = lifetimeGasTotals.build(),
            highestTemperatureC = max(state.lifetimeStats.highestTemperatureC, newTemp),
            highestCo2Ppm = max(state.lifetimeStats.highestCo2Ppm, co2Ppm),
        ),
        collapsed = state.collapsed || habitability.fraction <= 0.0001,
    )

    return SimulationStepResult(newState, rates)
}
