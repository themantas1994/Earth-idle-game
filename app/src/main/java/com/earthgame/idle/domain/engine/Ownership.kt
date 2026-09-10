package com.earthgame.idle.domain.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Ownership bonuses: the reward for going *deep* on one generator.
 *
 * Every generator's price climbs with each unit you own of it, which on its own
 * makes buying the eleventh copy of something strictly worse than buying the
 * first copy of something newer. That is correct pacing but miserable feedback:
 * the number goes up, the bar does not, and nothing ever *happens*.
 *
 * So every generator carries its own progress track. Every tenth copy doubles
 * that building's entire output, permanently, for the rest of the run — and the
 * card immediately starts counting toward the next one.
 *
 * The spacing is what keeps it honest. Across one ten-unit span a generator's
 * unit price grows by `unitCostGrowth^10` — about ×4 at the shipped value —
 * against a single ×2 from the bonus, so the value of each further copy still
 * falls and broadening into new technology still wins in the long run. Depth is
 * a satisfying detour, not a replacement for the tech tree.
 * `BalanceInvariantsTest` guards the relationship directly.
 */

/** How many ownership thresholds [owned] units have crossed. */
fun ownershipMilestonesReached(owned: Int): Int = max(0, owned) / OWNERSHIP_BONUS.everyUnits

/** Production multiplier a generator has earned purely from how many of it are owned. */
fun ownershipMultiplier(owned: Int): Double {
    val reached = ownershipMilestonesReached(owned)
    return if (reached <= 0) 1.0 else OWNERSHIP_BONUS.multiplier.pow(reached)
}

/** Unit count at which the next doubling lands, for the "next bonus at ×N" readout. */
fun nextOwnershipMilestone(owned: Int): Int =
    (ownershipMilestonesReached(owned) + 1) * OWNERSHIP_BONUS.everyUnits

/** Where [owned] sits between the previous threshold and the next, in [0,1] — the card's progress bar. */
fun ownershipProgress(owned: Int): Double =
    min(1.0, max(0.0, (max(0, owned) % OWNERSHIP_BONUS.everyUnits).toDouble() / OWNERSHIP_BONUS.everyUnits))

/**
 * Thresholds crossed by going from [before] to [after] units, in order.
 * Computed arithmetically rather than by walking the units, since a single
 * "buy max" can land thousands of copies at once.
 */
fun ownershipMilestonesCrossed(before: Int, after: Int): List<Int> {
    if (after <= before) return emptyList()
    val step = OWNERSHIP_BONUS.everyUnits
    val from = ownershipMilestonesReached(before) + 1
    val to = ownershipMilestonesReached(after)
    if (to < from) return emptyList()
    return (from..to).map { it * step }
}
