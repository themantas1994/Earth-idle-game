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
import com.earthgame.idle.domain.production.ConsumerDemand
import com.earthgame.idle.domain.production.ConsumerFlow
import com.earthgame.idle.domain.production.accountFlow
import com.earthgame.idle.domain.production.solveUtilizations
import com.earthgame.idle.domain.technologies.ALL_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.TechBranch
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
    /**
     * **Net** resource income: everything produced minus everything processors
     * consume. This is what the wallet actually gains per second, so it is what
     * `simulateStep` integrates and what every affordability countdown reads.
     * Never negative for any resource — see the conservation clamp in
     * `domain/production/ResourceFlow.kt`.
     */
    val resourcePerS: ResourceAmounts,
    /** Gross output, before processors take their share. */
    val resourceGrossPerS: ResourceAmounts = resourcePerS,
    /** What processors consume per second. */
    val resourceConsumedPerS: ResourceAmounts = ResourceAmounts.ZERO,
    /** One entry per owned processor: utilization, bottleneck, throughput. */
    val consumerFlows: List<ConsumerFlow> = emptyList(),
) {
    fun consumerFlow(id: String): ConsumerFlow? = consumerFlows.firstOrNull { it.consumerId == id }

    companion object {
        val ZERO = ProductionRates(GasAmounts.ZERO, GasAmounts.ZERO, ResourceAmounts.ZERO)
    }
}

/**
 * What one building contributes at [owned] units, with every multiplier
 * applied. Split out from [computeProductionRates] so the Production screen can
 * show a building's own contribution rather than the global total for the gases
 * it happens to emit.
 *
 * [utilization] is how hard the building is running: always 1 for a producer,
 * and the processor's solved share of its inputs for a consumer. Output *and*
 * emissions scale with it — a refinery at 40% emits 40% of its rated carbon —
 * which is what keeps the climate model honest about a starved chain.
 */
fun computeTechProductionRates(
    tech: Technology,
    owned: Int,
    multipliers: EffectiveMultipliers,
    utilization: Double = 1.0,
): ProductionRates {
    if (owned <= 0 || !tech.isBuilding) return ProductionRates.ZERO

    val running = utilization.coerceIn(0.0, 1.0)
    val scale = techScale(tech, owned, multipliers) * running
    val gross = GasAmounts.builder()
    val removal = GasAmounts.builder()
    val resources = ResourceAmounts.builder()
    val consumed = ResourceAmounts.builder()

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
    // Intake is scaled by the building's own size — units owned times the
    // ownership milestones they have earned — and by how hard it is running,
    // but never by a production multiplier. See `ResourceFlow.kt`.
    for ((resourceId, perUnit) in tech.effect.inputsPerUnit) {
        consumed[resourceId] = gd(perUnit) * owned * ownershipMultiplier(owned) * running
    }

    val produced = resources.build()
    return ProductionRates(
        gasGrossKgPerS = gross.build(),
        gasRemovalKgPerS = removal.build(),
        resourcePerS = produced,
        resourceGrossPerS = produced,
        resourceConsumedPerS = consumed.build(),
    )
}

/**
 * Runs the whole production pipeline: producer supply, processor demand,
 * bottleneck allocation, consumption, output and emissions.
 *
 * This is the hottest function in the game — it runs on every tick and on every
 * UI refresh — so it walks only the buildings actually owned and accumulates
 * into mutable builders rather than materialising a per-technology result and
 * folding it in. The flow solve itself is skipped entirely when nothing the
 * player owns consumes anything, which is the whole of the early game.
 *
 * See `domain/production/ResourceFlow.kt` for the allocation rule and for why
 * demand is exempt from production multipliers.
 */
fun computeProductionRates(
    techOwned: Map<String, Int>,
    multipliers: EffectiveMultipliers,
): ProductionRates {
    val gross = GasAmounts.builder()
    val removal = GasAmounts.builder()
    val producerSupply = Array(ResourceId.entries.size) { GameDecimal.ZERO }

    val ownedConsumers = ArrayList<Technology>()
    val ownedConsumerCounts = ArrayList<Int>()

    for (tech in ALL_TECHNOLOGIES) {
        val owned = techOwned[tech.id] ?: 0
        if (owned <= 0 || !tech.isBuilding) continue

        if (tech.isConsumer) {
            ownedConsumers += tech
            ownedConsumerCounts += owned
            continue
        }

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
            producerSupply[resourceId.ordinal] += gd(perUnit) * owned * scale * resourceMultiplier
        }
    }

    if (ownedConsumers.isEmpty()) {
        val supply = ResourceAmounts.build { builder ->
            for (resource in ResourceId.entries) builder[resource] = producerSupply[resource.ordinal]
        }
        return ProductionRates(
            gasGrossKgPerS = gross.build(),
            gasRemovalKgPerS = removal.build(),
            resourcePerS = supply,
            resourceGrossPerS = supply,
            resourceConsumedPerS = ResourceAmounts.ZERO,
            consumerFlows = emptyList(),
        )
    }

    val demands = ArrayList<ConsumerDemand>(ownedConsumers.size)
    val outputScale = DoubleArray(ownedConsumers.size)
    for (index in ownedConsumers.indices) {
        val tech = ownedConsumers[index]
        val owned = ownedConsumerCounts[index]
        val scale = techScale(tech, owned, multipliers)
        outputScale[index] = scale

        // Intake scales with the building's own size — how many units are
        // owned, and the ownership milestones those units have earned — and
        // with nothing else. A milestone makes a processor *bigger*: it pushes
        // more through and eats more to do it, so a chain that was in balance
        // stays in balance as both ends of it deepen. Global, branch, event and
        // prestige multipliers are efficiency, not size, and never touch
        // demand. See `domain/production/ResourceFlow.kt`.
        val sizeScale = ownershipMultiplier(owned)
        val demand = Array(ResourceId.entries.size) { GameDecimal.ZERO }
        for ((resourceId, perUnit) in tech.effect.inputsPerUnit) {
            demand[resourceId.ordinal] = gd(perUnit) * owned * sizeScale
        }
        // Rated output carries the building's own multipliers, so the solver
        // sees the real supply a processor adds to the pool downstream.
        val rated = Array(ResourceId.entries.size) { GameDecimal.ZERO }
        for ((resourceId, perUnit) in tech.effect.resourceProductionPerUnit) {
            val researchBonus = if (resourceId == ResourceId.RESEARCH) multipliers.research else 1.0
            val resourceMultiplier = (multipliers.perResource[resourceId] ?: 1.0) * researchBonus
            rated[resourceId.ordinal] = gd(perUnit) * owned * scale * resourceMultiplier
        }

        demands += ConsumerDemand(
            consumerId = tech.id,
            owned = owned,
            demandPerS = demand,
            ratedOutputPerS = rated,
            inputResources = tech.effect.inputsPerUnit.keys.sortedBy { it.ordinal },
        )
    }

    val allocation = solveUtilizations(producerSupply, demands)
    val flow = accountFlow(producerSupply, demands, allocation, outputScale)

    // Emissions follow utilization: a processor that cannot get its inputs
    // does not burn what it never received.
    for (index in ownedConsumers.indices) {
        val tech = ownedConsumers[index]
        val owned = ownedConsumerCounts[index]
        val running = allocation.utilization[index]
        if (running <= 0.0) continue
        val scale = outputScale[index] * running

        for ((gasId, perUnit) in tech.effect.gasProductionPerUnit) {
            val gasMultiplier = (multipliers.perGas[gasId] ?: 1.0) * multipliers.allGas
            gross.add(gasId, gd(perUnit) * owned * scale * gasMultiplier)
        }
        for ((gasId, perUnit) in tech.effect.gasRemovalPerUnit) {
            removal.add(gasId, gd(perUnit) * owned * scale)
        }
    }

    return ProductionRates(
        gasGrossKgPerS = gross.build(),
        gasRemovalKgPerS = removal.build(),
        resourcePerS = flow.netPerS,
        resourceGrossPerS = flow.supplyPerS,
        resourceConsumedPerS = flow.consumptionPerS,
        consumerFlows = flow.consumers,
    )
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
 * Advances the whole simulation by [dtSeconds] of **real** time.
 *
 * This is the single source of truth for how gases, resources, temperature and
 * habitability evolve, used identically for live 250 ms ticks and for offline
 * catch-up, so both paths are guaranteed consistent. The gas integration is
 * closed-form (see `integrateGasConcentration`), which is what makes one call
 * with `dt = 8 hours` produce the same numbers as 115,200 calls with
 * `dt = 0.25 s`.
 *
 * It is also the only thing that ages the planet: every real second simulated
 * here advances [GameState.gameAgeSeconds] by [GAME_SECONDS_PER_REAL_SECOND],
 * and that simulated age is the clock the atmosphere's half-lives decay on.
 * Because this function is the sole path, the offline catch-up gets the
 * ageing — and the decay — for free and cannot drift from a live session.
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
    val gameSecondsElapsed = gameSecondsFor(dtSeconds)

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
        gameAgeSeconds = state.gameAgeSeconds + gameSecondsElapsed,
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
            totalSimulatedSeconds = state.lifetimeStats.totalSimulatedSeconds + gameSecondsElapsed,
            totalGasProducedKg = lifetimeGasTotals.build(),
            highestTemperatureC = max(state.lifetimeStats.highestTemperatureC, newTemp),
            highestCo2Ppm = max(state.lifetimeStats.highestCo2Ppm, co2Ppm),
        ),
        collapsed = state.collapsed || habitability.fraction <= 0.0001,
    )

    return SimulationStepResult(newState, rates)
}
