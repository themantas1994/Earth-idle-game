package com.earthgame.idle.domain.technologies

import com.earthgame.idle.domain.engine.BALANCE
import kotlin.math.pow

/**
 * Balance curves shared by every technology definition. Each function turns a
 * technology's `tier` into a cost or an output using the constants in
 * `Constants.kt`, so the whole game's pacing is retuned by adjusting a handful
 * of numbers there rather than hundreds of hand-picked ones in the data files.
 *
 * `tier` is a rough overall-progression index — not per-branch — used purely
 * for scaling: technologies unlocked around the same point in a normal
 * playthrough share a similar tier whichever branch they belong to.
 *
 * The `scale` argument on each function is the per-technology fudge factor: a
 * dirtier-than-average generator gets a gas `scale` above 1, a bargain unlock a
 * cost `scale` below 1. Keep those within roughly 0.3–3 or a single technology
 * starts distorting the pacing the curves are built on.
 */

/**
 * Maps a technology's tier onto the position it occupies on the cost and
 * production ladders. The two are the same thing through the broad middle of
 * the tree and diverge above `flattenLadderFromTier`, where the tree narrows to
 * a single chain.
 *
 * Every curve below runs on this rather than on the raw tier, so the two
 * ladders stay in lockstep: compressing costs without compressing output would
 * simply hand the late game back its runaway.
 */
fun ladderTier(tier: Int): Double = ladderTier(tier.toDouble())

fun ladderTier(tier: Double): Double {
    val knee = BALANCE.flattenLadderFromTier
    if (tier <= knee) return tier
    return knee + (tier - knee) * BALANCE.lateTierCompression
}

/** One-time purchase cost (unlocks, multipliers, choices), in a resource's base unit. */
fun unlockCost(tier: Int, scale: Double = 1.0): Double =
    BALANCE.unlockBaseCost * BALANCE.unlockCostGrowthPerTier.pow(ladderTier(tier)) * scale

/** Research cost to unlock a tree node. */
fun researchCost(tier: Int, scale: Double = 1.0): Double =
    BALANCE.researchBaseCost * BALANCE.researchCostGrowthPerTier.pow(ladderTier(tier)) * scale

/** Base cost of a generator's first owned unit. */
fun generatorBaseCost(tier: Int, scale: Double = 1.0): Double =
    BALANCE.generatorBaseCost * BALANCE.generatorCostGrowthPerTier.pow(ladderTier(tier)) * scale

/** Per-unit gas production (kg/s) at 1 owned, before multipliers. */
fun gasProduction(tier: Int, scale: Double = 1.0): Double =
    BALANCE.gasProductionBase * BALANCE.gasProductionGrowthPerTier.pow(ladderTier(tier)) * scale

/** Per-unit resource production (units/s) at 1 owned, before multipliers. */
fun resourceProduction(tier: Int, scale: Double = 1.0): Double =
    BALANCE.resourceProductionBase * BALANCE.productionGrowthPerTier.pow(ladderTier(tier)) * scale

/** Standard per-owned-unit cost growth for generators. Slightly steeper at high tiers. */
fun generatorCostGrowth(tier: Int): Double =
    BALANCE.unitCostGrowth + ladderTier(tier) * BALANCE.unitCostGrowthPerTier
