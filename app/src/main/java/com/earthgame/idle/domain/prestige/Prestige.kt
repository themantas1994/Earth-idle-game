package com.earthgame.idle.domain.prestige

import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.PRESTIGE
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.GasAmounts
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * What one prestige upgrade level does. Challenge rewards use the same shape,
 * folded in at a flat level of 1 — a challenge is either completed once or not.
 */
data class PrestigeUpgradeEffect(
    /** Multiplies all resource and gas production, stacking per level. */
    val globalProductionMultiplier: Double? = null,
    /** Multiplies Research income specifically, stacking per level. */
    val researchMultiplier: Double? = null,
    /** Multiplies production of one specific gas, stacking per level. */
    val gasProductionMultiplier: Pair<GasId, Double>? = null,
    /** Multiplies ALL greenhouse-gas production at once (distinct from resource currencies). */
    val allGasProductionMultiplier: Double? = null,
    /** Fractional discount on every technology cost, stacking additively across levels, capped at 0.9. */
    val techCostDiscount: Double? = null,
    /** Multiplies the base offline-progress cap, stacking per level. */
    val offlineCapMultiplier: Double? = null,
    /** Technologies auto-granted (owned = 1) at the start of every future run. */
    val grantsStartingTechIds: List<String> = emptyList(),
    /** Extra owned units of a generator granted at the start of every future run, per level. */
    val startingGenerators: Map<String, Int> = emptyMap(),
    /** Flat resources granted at the start of every future run. */
    val startingResources: Map<ResourceId, Double> = emptyMap(),
)

data class PrestigeUpgrade(
    val id: String,
    val displayName: String,
    val description: String,
    val icon: String,
    /** In Earth Points. */
    val baseCost: Double,
    /** Per level; 1 for one-time upgrades. */
    val costGrowth: Double,
    val maxLevel: Int,
    val effect: PrestigeUpgradeEffect,
) {
    val isRepeatable: Boolean get() = maxLevel > 1

    companion object {
        /** No ceiling. Cost growth makes anything past a few dozen levels unaffordable anyway. */
        const val UNLIMITED_LEVELS = Int.MAX_VALUE
    }
}

val PRESTIGE_UPGRADES: List<PrestigeUpgrade> = listOf(
    PrestigeUpgrade(
        id = "atmospheric_momentum",
        displayName = "Atmospheric Momentum",
        description = "Each civilization remembers a little more than the last. +10% production per level.",
        icon = "🌬️",
        baseCost = 100.0,
        costGrowth = 2.2,
        maxLevel = PrestigeUpgrade.UNLIMITED_LEVELS,
        effect = PrestigeUpgradeEffect(globalProductionMultiplier = 1.1),
    ),
    PrestigeUpgrade(
        id = "industrial_memory",
        displayName = "Industrial Memory",
        description = "Start every future Earth already knowing the Steam Engine.",
        icon = "⚙️",
        baseCost = 1_000.0,
        costGrowth = 1.0,
        maxLevel = 1,
        effect = PrestigeUpgradeEffect(grantsStartingTechIds = listOf("steam_engine")),
    ),
    PrestigeUpgrade(
        id = "rapid_research",
        displayName = "Rapid Research",
        description = "Institutional knowledge survives the reset. +50% Research per level.",
        icon = "📚",
        baseCost = 10_000.0,
        costGrowth = 3.0,
        maxLevel = 5,
        effect = PrestigeUpgradeEffect(researchMultiplier = 1.5),
    ),
    PrestigeUpgrade(
        id = "institutional_memory",
        displayName = "Institutional Memory",
        description = "Every civilization inherits the last one's blueprints. Every building you own produces +40% more, per level.",
        icon = "🏛️",
        baseCost = 150_000.0,
        costGrowth = 4.0,
        maxLevel = 6,
        effect = PrestigeUpgradeEffect(globalProductionMultiplier = 1.4),
    ),
    PrestigeUpgrade(
        id = "civilizational_acceleration",
        displayName = "Civilizational Acceleration",
        description = "Blueprints from past civilizations make every technology 20% cheaper per level.",
        icon = "📐",
        baseCost = 1_000_000.0,
        costGrowth = 4.0,
        maxLevel = 4,
        effect = PrestigeUpgradeEffect(techCostDiscount = 0.2),
    ),
    PrestigeUpgrade(
        id = "anthropocene_mastery",
        displayName = "Anthropocene Mastery",
        description = "You've done this before. ×10 all greenhouse-gas production.",
        icon = "🌋",
        baseCost = 25_000_000.0,
        costGrowth = 1.0,
        maxLevel = 1,
        effect = PrestigeUpgradeEffect(allGasProductionMultiplier = 10.0),
    ),
    PrestigeUpgrade(
        id = "eternal_flame",
        displayName = "Eternal Flame",
        description = "An ember carried between worlds. Every future Earth starts with its fires already burning: +2 Natural Fire per level.",
        icon = "🔥",
        baseCost = 50.0,
        costGrowth = 1.8,
        maxLevel = 10,
        effect = PrestigeUpgradeEffect(startingGenerators = mapOf("natural_fire" to 2)),
    ),
    PrestigeUpgrade(
        id = "extended_endurance",
        displayName = "Extended Endurance",
        description = "Your civilizations keep running longer without you watching. +100% offline progress cap per level.",
        icon = "⏳",
        baseCost = 5_000.0,
        costGrowth = 3.5,
        maxLevel = 3,
        effect = PrestigeUpgradeEffect(offlineCapMultiplier = 2.0),
    ),
    PrestigeUpgrade(
        id = "head_start",
        displayName = "Head Start",
        description = "Begin each Earth with a stockpile of Energy, skipping the slowest opening minutes.",
        icon = "🚩",
        baseCost = 20_000.0,
        costGrowth = 1.0,
        maxLevel = 1,
        effect = PrestigeUpgradeEffect(startingResources = mapOf(ResourceId.ENERGY to 1000.0)),
    ),
    PrestigeUpgrade(
        id = "automated_industry",
        displayName = "Automated Industry",
        description = "Robots keep the lines running while you sleep. +100% offline progress cap, and every future Earth starts with 25 Natural Fires already lit.",
        icon = "🤖",
        baseCost = 200_000.0,
        costGrowth = 1.0,
        maxLevel = 1,
        effect = PrestigeUpgradeEffect(
            offlineCapMultiplier = 2.0,
            startingGenerators = mapOf("natural_fire" to 25),
        ),
    ),
    PrestigeUpgrade(
        id = "deep_foundations",
        displayName = "Deep Foundations",
        description = "Start every future Earth with a Research head start, so the tree opens immediately instead of after the first hour.",
        icon = "🧱",
        baseCost = 40_000.0,
        costGrowth = 1.0,
        maxLevel = 1,
        effect = PrestigeUpgradeEffect(
            startingResources = mapOf(ResourceId.RESEARCH to 5_000.0, ResourceId.ENERGY to 25_000.0),
        ),
    ),
)

val PRESTIGE_UPGRADE_BY_ID: Map<String, PrestigeUpgrade> = PRESTIGE_UPGRADES.associateBy { it.id }

fun prestigeUpgradeCost(upgrade: PrestigeUpgrade, currentLevel: Int): GameDecimal =
    gd(upgrade.baseCost) * gd(upgrade.costGrowth).pow(currentLevel)

/**
 * Everything owned prestige upgrades and completed challenges do, aggregated
 * once so the per-tick production maths is a single lookup.
 */
data class PrestigeMultipliers(
    val global: Double = 1.0,
    val research: Double = 1.0,
    val allGas: Double = 1.0,
    val perGas: Map<GasId, Double> = emptyMap(),
    val techCostDiscount: Double = 0.0,
    val offlineCapMultiplier: Double = 1.0,
    val startingTechIds: List<String> = emptyList(),
    val startingGenerators: Map<String, Int> = emptyMap(),
    val startingResources: Map<ResourceId, Double> = emptyMap(),
) {
    companion object {
        val NONE = PrestigeMultipliers()
    }
}

private class PrestigeAccumulator {
    var global = 1.0
    var research = 1.0
    var allGas = 1.0
    val perGas = mutableMapOf<GasId, Double>()
    var techCostDiscount = 0.0
    var offlineCapMultiplier = 1.0
    val startingTechIds = mutableListOf<String>()
    val startingGenerators = mutableMapOf<String, Int>()
    val startingResources = mutableMapOf<ResourceId, Double>()

    fun apply(e: PrestigeUpgradeEffect, level: Int) {
        e.globalProductionMultiplier?.let { global *= it.pow(level) }
        e.researchMultiplier?.let { research *= it.pow(level) }
        e.allGasProductionMultiplier?.let { allGas *= it.pow(level) }
        e.gasProductionMultiplier?.let { (gas, multiplier) ->
            perGas[gas] = (perGas[gas] ?: 1.0) * multiplier.pow(level)
        }
        e.techCostDiscount?.let { techCostDiscount = min(0.9, techCostDiscount + it * level) }
        e.offlineCapMultiplier?.let { offlineCapMultiplier *= it.pow(level) }
        startingTechIds += e.grantsStartingTechIds
        for ((techId, count) in e.startingGenerators) {
            startingGenerators[techId] = (startingGenerators[techId] ?: 0) + count * level
        }
        for ((resource, amount) in e.startingResources) {
            startingResources[resource] = (startingResources[resource] ?: 0.0) + amount
        }
    }

    fun build() = PrestigeMultipliers(
        global = global,
        research = research,
        allGas = allGas,
        perGas = perGas.toMap(),
        techCostDiscount = techCostDiscount,
        offlineCapMultiplier = offlineCapMultiplier,
        startingTechIds = startingTechIds.toList(),
        startingGenerators = startingGenerators.toMap(),
        startingResources = startingResources.toMap(),
    )
}

/**
 * Aggregates every owned prestige upgrade at every level, plus any permanent
 * challenge rewards, into one multiplier bundle.
 */
fun computePrestigeMultipliers(
    upgradesOwned: Map<String, Int>,
    challengeRewardEffects: List<PrestigeUpgradeEffect> = emptyList(),
): PrestigeMultipliers {
    val accumulator = PrestigeAccumulator()

    for (upgrade in PRESTIGE_UPGRADES) {
        val level = upgradesOwned[upgrade.id] ?: 0
        if (level <= 0) continue
        accumulator.apply(upgrade.effect, level)
    }

    for (reward in challengeRewardEffects) {
        accumulator.apply(reward, 1)
    }

    return accumulator.build()
}

data class PrestigeGainParams(
    val totalGasProducedKg: GasAmounts,
    val peakForcingWm2: Double,
    val civLevel: Int,
    val runDurationSeconds: Double,
)

/**
 * How much faster than the reference pace this run was, as a multiplier on the
 * payout. This is the term that makes each Earth worth more than the last:
 * prestige upgrades buy speed, and speed is what is scored.
 *
 * A run of zero (or nonsensical) length is treated as the reference pace rather
 * than as infinitely fast, so a corrupted clock cannot mint points.
 */
fun speedMultiplier(runDurationSeconds: Double): Double {
    if (!runDurationSeconds.isFinite() || runDurationSeconds <= 0) return 1.0
    val ratio = PRESTIGE.referenceRunSeconds / runDurationSeconds
    val scaled = ratio.pow(PRESTIGE.speedExponent)
    return min(PRESTIGE.maxSpeedMultiplier, max(PRESTIGE.minSpeedMultiplier, scaled))
}

/**
 * Converts one run's outcome into Earth Points: how much gas the civilization
 * produced, scaled by how hard it pushed the atmosphere, how far up the tech
 * tree it got, and how quickly it managed all three.
 */
fun calculatePrestigeGain(params: PrestigeGainParams): GameDecimal {
    val totalGasKg = params.totalGasProducedKg.sum()
    val gasScore = (totalGasKg / PRESTIGE.baseDivisor).clampMin(GameDecimal.ZERO)
    val gasComponent = gasScore.pow(PRESTIGE.exponent) * PRESTIGE.gasWeight

    val forcingMultiplier = 1.0 + PRESTIGE.forcingWeight * sqrt(max(0.0, params.peakForcingWm2))
    val civMultiplier = 1.0 + PRESTIGE.civLevelWeight * sqrt(max(0.0, params.civLevel.toDouble()))

    return gasComponent * forcingMultiplier * civMultiplier * speedMultiplier(params.runDurationSeconds)
}
