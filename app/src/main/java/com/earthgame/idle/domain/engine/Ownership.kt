package com.earthgame.idle.domain.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Ownership milestones: the reward for going *deep* on one building.
 *
 * Every building's price climbs with each unit you own of it, which on its own
 * makes buying the eleventh copy of something strictly worse than buying the
 * first copy of something newer. That is correct pacing but miserable feedback:
 * the number goes up, the bar does not, and nothing ever *happens*.
 *
 * So every building carries its own progress track, and crossing a milestone
 * multiplies that building's entire output permanently, for the rest of the
 * run.
 *
 * ## The ladder
 *
 * Milestones used to land on every tenth unit forever. They now **spread out as
 * you go deeper**, which is what gives the NEXT buy mode something meaningful
 * to aim at late in a run and stops a thousand-unit building from being a
 * conveyor belt of identical rewards.
 *
 * The ladder is built from two numbers in [OWNERSHIP_BONUS]:
 *
 * ```
 * step(owned) = baseStep + stepGrowthPerBlock * (owned / blockUnits)      // integer division
 * ```
 *
 * with one extra rule: **a milestone never steps across a block boundary.**
 * The last milestone of a block is the boundary itself. That is what keeps the
 * round numbers round — 100, 200, 300 are always milestones — and it is why the
 * spacing table below is exact rather than approximate.
 *
 * | Units owned | Step | Milestones in the block |
 * | :-- | --: | :-- |
 * | 0–100 | 10 | 10, 20, 30, … 90, 100 |
 * | 100–200 | 11 | 111, 122, 133, … 199, 200 |
 * | 200–300 | 12 | 212, 224, 236, … 296, 300 |
 * | 300–400 | 13 | 313, 326, … 299+, 400 |
 * | 900–1000 | 19 | 919, 938, … 1000 |
 *
 * The growth is deliberately **linear in the block index, not exponential**.
 * Ten blocks in, the interval has not quite doubled; a hundred blocks in — a
 * depth no run reaches — it is 110. A player who is 3,000 units deep still gets
 * a reward every 40 units, which is a few minutes of an idle game rather than
 * an afternoon. Pure exponential spacing would have made the late game silent.
 *
 * The rewards themselves are data, not a formula baked into the engine: see
 * [milestoneAt] and [OWNERSHIP_BONUS.multiplier]. Every milestone currently
 * pays the same ×2 the old fixed ladder did, so the power curve of the early
 * game — the part players actually replay — is unchanged; going very deep is
 * slightly weaker than it was, which is the intended effect of sparser
 * milestones and is what keeps the ladder honest against the price curve.
 *
 * `BalanceInvariantsTest` guards the relationship between step size, reward and
 * price growth directly; `MilestoneLadderTest` pins the ladder itself.
 */

/** One rung of a building's ownership ladder. */
data class OwnershipMilestoneDefinition(
    /** Units owned at which this milestone lands. */
    val atUnits: Int,
    /** Output multiplier this rung grants, on top of every rung below it. */
    val outputMultiplier: Double,
)

/**
 * Spacing between milestones for a building holding [owned] units, before the
 * block-boundary rule. Grows by [OWNERSHIP_BONUS.stepGrowthPerBlock] with every
 * [OWNERSHIP_BONUS.blockUnits] owned.
 */
fun milestoneStep(owned: Int): Int {
    val block = max(0, owned) / OWNERSHIP_BONUS.blockUnits
    return OWNERSHIP_BONUS.baseStep + OWNERSHIP_BONUS.stepGrowthPerBlock * block
}

/**
 * The next unit count at which a milestone lands, strictly greater than
 * [owned]. The target the NEXT buy mode aims at.
 */
fun nextOwnershipMilestone(owned: Int): Int {
    val count = max(0, owned)
    val blockUnits = OWNERSHIP_BONUS.blockUnits
    val blockStart = (count / blockUnits) * blockUnits
    val step = milestoneStep(count)
    val within = count - blockStart
    val candidate = blockStart + ((within / step) + 1) * step
    return min(candidate, blockStart + blockUnits)
}

/**
 * The previous milestone at or below [owned] — the bottom of the progress bar.
 * Zero when no milestone has been reached yet.
 */
fun previousOwnershipMilestone(owned: Int): Int {
    val count = max(0, owned)
    val blockUnits = OWNERSHIP_BONUS.blockUnits
    if (count < OWNERSHIP_BONUS.baseStep) return 0
    val blockStart = (count / blockUnits) * blockUnits
    // Exactly on a boundary, the boundary itself is the milestone just reached.
    if (count == blockStart) return blockStart
    val step = milestoneStep(count)
    val within = count - blockStart
    return blockStart + (within / step) * step
}

/**
 * How many milestones [owned] units have crossed.
 *
 * Counted a block at a time rather than by walking units, since a single buy
 * can land hundreds of copies. Blocks whose step has grown past
 * [OWNERSHIP_BONUS.blockUnits] contain exactly one milestone — the boundary —
 * so past that point the count is arithmetic and an absurd ownership figure
 * cannot spin the loop.
 */
fun ownershipMilestonesReached(owned: Int): Int {
    val count = max(0, owned)
    if (count < OWNERSHIP_BONUS.baseStep) return 0

    val blockUnits = OWNERSHIP_BONUS.blockUnits
    val fullBlocks = count / blockUnits
    val within = count - fullBlocks * blockUnits

    var total = 0L
    var block = 0
    while (block < fullBlocks) {
        val step = OWNERSHIP_BONUS.baseStep + OWNERSHIP_BONUS.stepGrowthPerBlock * block
        if (step >= blockUnits) {
            // Every remaining block holds exactly its boundary milestone.
            total += (fullBlocks - block).toLong()
            break
        }
        total += milestonesInBlock(step, blockUnits).toLong()
        block++
    }

    if (within > 0) {
        val step = OWNERSHIP_BONUS.baseStep + OWNERSHIP_BONUS.stepGrowthPerBlock * fullBlocks
        total += (within / step).toLong()
    }

    return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Milestones inside one complete block: every whole [step] strictly below the
 * block's width, plus the boundary itself.
 */
private fun milestonesInBlock(step: Int, blockUnits: Int): Int =
    (blockUnits - 1) / step + 1

/**
 * The [index]-th milestone of the ladder, 1-based, as data.
 *
 * Kept as a definition rather than an `if (count % 10 == 0)` so a future
 * rebalance can make a particular rung worth more than its neighbours without
 * touching the engine.
 */
fun milestoneAt(index: Int): OwnershipMilestoneDefinition? {
    if (index <= 0) return null
    var units = 0
    repeat(index) { units = nextOwnershipMilestone(units) }
    return OwnershipMilestoneDefinition(units, OWNERSHIP_BONUS.multiplier)
}

/** The ladder's first [count] rungs, for documentation, diagnostics and tests. */
fun ownershipLadder(count: Int): List<OwnershipMilestoneDefinition> {
    val rungs = ArrayList<OwnershipMilestoneDefinition>(max(0, count))
    var units = 0
    repeat(max(0, count)) {
        units = nextOwnershipMilestone(units)
        rungs += OwnershipMilestoneDefinition(units, OWNERSHIP_BONUS.multiplier)
    }
    return rungs
}

/** Production multiplier a building has earned purely from how many of it are owned. */
fun ownershipMultiplier(owned: Int): Double {
    val reached = ownershipMilestonesReached(owned)
    return if (reached <= 0) 1.0 else OWNERSHIP_BONUS.multiplier.pow(reached)
}

/** Where [owned] sits between the previous milestone and the next, in [0,1] — the card's progress bar. */
fun ownershipProgress(owned: Int): Double {
    val count = max(0, owned)
    val previous = previousOwnershipMilestone(count)
    val next = nextOwnershipMilestone(count)
    val span = next - previous
    if (span <= 0) return 0.0
    return min(1.0, max(0.0, (count - previous).toDouble() / span))
}

/**
 * Thresholds crossed by going from [before] to [after] units, in order.
 *
 * Computed from the milestone *counts* at each end rather than by walking the
 * units, since a single "buy max" can land thousands of copies at once.
 */
fun ownershipMilestonesCrossed(before: Int, after: Int): List<Int> {
    if (after <= before) return emptyList()
    val from = ownershipMilestonesReached(before)
    val to = ownershipMilestonesReached(after)
    if (to <= from) return emptyList()

    val crossed = ArrayList<Int>(to - from)
    var units = previousOwnershipMilestone(max(0, before))
    // Walk the ladder from the last milestone already held up to the new count.
    while (crossed.size < to - from) {
        units = nextOwnershipMilestone(units)
        crossed += units
    }
    return crossed
}

/** Units still to buy before the next milestone lands. Always >= 1. */
fun unitsToNextOwnershipMilestone(owned: Int): Int = nextOwnershipMilestone(owned) - max(0, owned)
