package com.earthgame.idle.domain.events

import com.earthgame.idle.domain.climate.AtmosphereState
import com.earthgame.idle.domain.engine.MultiplierContribution
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.ActiveEvent
import com.earthgame.idle.domain.model.GAS_LIST
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.technologies.TechBranch

data class RandomEventDefinition(
    val id: String,
    val displayName: String,
    val description: String,
    val icon: String,
    /** Random events only start rolling once the civilization reaches roughly this progress level. */
    val minCivLevel: Int,
    val durationSeconds: Int,
    /** Relative selection weight among currently-eligible events. */
    val weight: Int,
    val isNegative: Boolean = false,
    val effect: MultiplierContribution,
    /** A one-time atmospheric mass injection applied the instant the event triggers, in kg. */
    val instantGasBurstKg: Map<GasId, Double> = emptyMap(),
)

/**
 * Short-lived multiplier swings rolled by chance, roughly every few minutes.
 *
 * The set skews hard toward the positive on purpose. A negative event in an
 * idle game is a tax on being away — you were not there, you could not react,
 * and all it did was quietly make the bar fill slower. The two that remain
 * are short, mild, and exist so that a green banner means something; every
 * other roll is a gift. Weights and durations are tuned so the *positive*
 * ones are the ones long enough to notice and act on.
 */
val RANDOM_EVENTS: List<RandomEventDefinition> = listOf(
    RandomEventDefinition(
        id = "good_harvest",
        displayName = "Bumper Harvest",
        description = "A season goes exactly right. Everything you run produces triple for a while.",
        icon = "🌻",
        minCivLevel = 1,
        durationSeconds = 90,
        weight = 4,
        effect = MultiplierContribution(global = 3.0),
    ),
    RandomEventDefinition(
        id = "master_craftsman",
        displayName = "Master Craftsman",
        description = "Someone works out a better way to do it, and tells everyone. Research floods in.",
        icon = "🔨",
        minCivLevel = 3,
        durationSeconds = 90,
        weight = 4,
        effect = MultiplierContribution(research = 8.0),
    ),
    RandomEventDefinition(
        id = "volcanic_eruption",
        displayName = "Volcanic Eruption",
        description = "A major eruption injects a pulse of CO₂ into the atmosphere.",
        icon = "🌋",
        minCivLevel = 0,
        durationSeconds = 30,
        weight = 3,
        effect = MultiplierContribution.NONE,
        instantGasBurstKg = mapOf(GasId.CO2 to 5e10),
    ),
    RandomEventDefinition(
        id = "wildfire",
        displayName = "Wildfire",
        description = "Vast wildfires release a large burst of carbon dioxide.",
        icon = "🔥",
        minCivLevel = 3,
        durationSeconds = 30,
        weight = 3,
        effect = MultiplierContribution.NONE,
        instantGasBurstKg = mapOf(GasId.CO2 to 2e11),
    ),
    RandomEventDefinition(
        id = "el_nino",
        displayName = "El Niño",
        description = "Shifting ocean currents temporarily amplify warming feedback loops.",
        icon = "🌊",
        minCivLevel = 5,
        durationSeconds = 120,
        weight = 3,
        effect = MultiplierContribution(allGas = 2.0),
    ),
    RandomEventDefinition(
        id = "methane_release",
        displayName = "Methane Release",
        description = "Thawing permafrost vents a burst of trapped methane.",
        icon = "🧊",
        minCivLevel = 8,
        durationSeconds = 120,
        weight = 3,
        effect = MultiplierContribution(perGas = mapOf(GasId.CH4 to 5.0)),
    ),
    RandomEventDefinition(
        id = "industrial_boom",
        displayName = "Industrial Boom",
        description = "A surge of investment sends every factory's output soaring.",
        icon = "📈",
        minCivLevel = 6,
        durationSeconds = 120,
        weight = 4,
        effect = MultiplierContribution(global = 8.0),
    ),
    RandomEventDefinition(
        id = "economic_crash",
        displayName = "Economic Crash",
        description = "Markets collapse. Industrial output slows to a crawl.",
        icon = "📉",
        minCivLevel = 6,
        durationSeconds = 30,
        weight = 1,
        isNegative = true,
        effect = MultiplierContribution(global = 0.7),
    ),
    RandomEventDefinition(
        id = "green_revolution",
        displayName = "Green Revolution",
        description = "New techniques send agricultural output through the roof.",
        icon = "🌾",
        minCivLevel = 7,
        durationSeconds = 120,
        weight = 3,
        effect = MultiplierContribution(perBranch = mapOf(TechBranch.AGRICULTURE to 15.0)),
    ),
    RandomEventDefinition(
        id = "technological_breakthrough",
        displayName = "Technological Breakthrough",
        description = "A sudden insight accelerates research dramatically.",
        icon = "💡",
        minCivLevel = 10,
        durationSeconds = 120,
        weight = 4,
        effect = MultiplierContribution(research = 30.0),
    ),
    RandomEventDefinition(
        id = "supply_chain_shock",
        displayName = "Supply Chain Shock",
        description = "Global logistics seize up, throttling every branch at once.",
        icon = "🚧",
        minCivLevel = 18,
        durationSeconds = 30,
        weight = 1,
        isNegative = true,
        effect = MultiplierContribution(global = 0.75),
    ),
    RandomEventDefinition(
        id = "agi_breakthrough",
        displayName = "AGI Breakthrough",
        description = "Machines start optimizing the optimizers. Research goes vertical.",
        icon = "🤖",
        minCivLevel = 26,
        durationSeconds = 150,
        weight = 4,
        effect = MultiplierContribution(research = 50.0, global = 4.0),
    ),
    RandomEventDefinition(
        id = "stellar_flare",
        displayName = "Stellar Flare",
        description = "Your Dyson swarm catches a solar flare at full charge — a brief, absurd surge of everything.",
        icon = "☀️",
        minCivLevel = 40,
        durationSeconds = 150,
        weight = 4,
        effect = MultiplierContribution(global = 20.0, allGas = 3.0),
    ),
)

val RANDOM_EVENT_BY_ID: Map<String, RandomEventDefinition> = RANDOM_EVENTS.associateBy { it.id }

/**
 * Weighted pick among the events currently eligible. The caller supplies the
 * random draw in [0,1) rather than this drawing its own, which is what makes
 * event selection reproducible in tests and identical to the reference
 * implementation for the same seed.
 */
fun rollRandomEvent(civLevel: Int, activeEventDefIds: Set<String>, random: Double): RandomEventDefinition? {
    val eligible = RANDOM_EVENTS.filter { it.minCivLevel <= civLevel && it.id !in activeEventDefIds }
    if (eligible.isEmpty()) return null
    val totalWeight = eligible.sumOf { it.weight }
    var threshold = random * totalWeight
    for (event in eligible) {
        threshold -= event.weight
        if (threshold <= 0) return event
    }
    return eligible.last()
}

/** Sums the still-active (non-expired) events into one multiplier contribution for the current tick. */
fun computeActiveEventMultipliers(activeEvents: List<ActiveEvent>, nowMs: Long): MultiplierContribution {
    if (activeEvents.isEmpty()) return MultiplierContribution.NONE

    var global: Double? = null
    var research: Double? = null
    var allGas: Double? = null
    val perGas = mutableMapOf<GasId, Double>()
    val perBranch = mutableMapOf<TechBranch, Double>()

    for (active in activeEvents) {
        if (active.endsAt <= nowMs) continue
        val definition = RANDOM_EVENT_BY_ID[active.eventDefId] ?: continue
        val effect = definition.effect
        effect.global?.let { global = (global ?: 1.0) * it }
        effect.research?.let { research = (research ?: 1.0) * it }
        effect.allGas?.let { allGas = (allGas ?: 1.0) * it }
        for ((gasId, multiplier) in effect.perGas) {
            perGas[gasId] = (perGas[gasId] ?: 1.0) * multiplier
        }
        for ((branch, multiplier) in effect.perBranch) {
            perBranch[branch] = (perBranch[branch] ?: 1.0) * multiplier
        }
    }

    return MultiplierContribution(global, research, allGas, perGas.toMap(), perBranch.toMap())
}

fun removeExpiredEvents(activeEvents: List<ActiveEvent>, nowMs: Long): List<ActiveEvent> =
    if (activeEvents.none { it.endsAt <= nowMs }) activeEvents else activeEvents.filter { it.endsAt > nowMs }

/** Applies an event's one-time atmospheric burst directly to the atmosphere state. */
fun applyInstantGasBurst(atmosphere: AtmosphereState, definition: RandomEventDefinition): AtmosphereState {
    if (definition.instantGasBurstKg.isEmpty()) return atmosphere
    val builder = atmosphere.toBuilder()
    for (gas in GAS_LIST) {
        val burstKg = definition.instantGasBurstKg[gas.id] ?: continue
        builder.add(gas.id, gd(burstKg) / gas.massPerUnit)
    }
    return builder.build()
}
