package com.earthgame.idle.domain.production

import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.PRODUCTION
import com.earthgame.idle.domain.model.ResourceAmounts
import com.earthgame.idle.domain.model.ResourceId
import kotlin.math.min

/**
 * The production pipeline: who supplies what, who demands what, and what
 * everybody therefore actually runs at.
 *
 * ## What a consumer is allowed to do
 *
 * A processor runs on **flow, not on stockpiles**. An oil refinery cracks the
 * oil arriving this second; it cannot dip into the barrels you are saving to
 * buy the next derrick. That single rule buys four properties the game needs:
 *
 * 1. **No negative balances, ever.** Consumption is capped at supply before any
 *    balance is touched, so a resource can stop growing but can never go down.
 * 2. **Offline is exact, not approximated.** Rates are constant across an
 *    absence (nothing is bought while you are away), so one closed-form step
 *    over twelve hours gives bit-identical results to 172,800 live ticks. See
 *    `docs/wiki/Offline-Progression.md`.
 * 3. **Order independence.** The answer is a fixed point of the whole system,
 *    not the result of walking a list, so re-ordering two definitions in a data
 *    file cannot change a single number.
 * 4. **A readable bottleneck.** "73%, limited by Oil" is a statement about
 *    rates, which is the thing the player can actually go and fix.
 *
 * ## The pipeline, in order
 *
 * 1. **Producer supply.** Every input-free building's output, with global,
 *    branch and ownership multipliers applied.
 * 2. **Consumer demand.** `owned × inputsPerUnit`, and *nothing else* — see
 *    "Multipliers" below.
 * 3. **Allocation.** Supply is shared out by max-min fairness (below), giving
 *    each consumer a utilization in `[0, 1]`.
 * 4. **Consumption and output.** Each consumer eats `utilization × demand` and
 *    makes `utilization × rated output`, with its multipliers applied to the
 *    output only.
 * 5. **Emissions.** Scaled by the same utilization: a refinery running at 40%
 *    emits 40% of its rated carbon. Producers always run at 100%.
 * 6. **Conservation clamp.** A final hard cap of consumption at supply, so the
 *    net rate of every resource is provably `>= 0` whatever the definitions do.
 * 7. **Net.** `net = supply − consumption`, which is what the wallet gains.
 *
 * ## Multipliers: size versus efficiency
 *
 * Two different things multiply a building, and only one of them touches what
 * it eats.
 *
 * **Size** is the building itself: how many units are owned, times the
 * ownership milestones those units have earned. A processor that has doubled
 * through a milestone is a bigger factory — it pushes twice as much through and
 * takes twice as much in. Demand scales with size, which is what keeps a chain
 * in balance as both ends of it deepen: ten mines feeding ten mills stay in the
 * same proportion at a hundred of each.
 *
 * **Efficiency** is everything else: prestige, events, branch bonuses, The
 * Wheel. Those multiply **output only**.
 *
 * That asymmetry is deliberate. If an efficiency bonus multiplied both sides it
 * would be a no-op across the whole processing half of the economy — a ×10 on a
 * refinery's fuel output and a ×10 on its oil demand cancel exactly, and the
 * player's prestige tree would silently stop working the moment they unlocked a
 * chain. Instead a prestige bonus reads on the card the way it should: same
 * intake, more output, and a bottleneck that eases.
 *
 * ## The allocation rule
 *
 * When several processors want the same resource, supply is split by **max-min
 * fairness**: find the scarcest resource, cap everyone who needs it at the
 * ratio it can support, subtract what they take, and repeat with what is left.
 * Nobody is starved because they happen to be defined later in a file, and a
 * processor already limited by a *different* input releases the share of this
 * one it was never going to use.
 *
 * Because processors feed processors — a power station's Energy runs a steel
 * mill — supply itself depends on the answer. So the allocation is iterated to
 * a fixed point. Starting from "everything runs flat out", each round can only
 * lower utilization, so the sequence is monotone, bounded and converges; in
 * practice two or three rounds settle it, and the loop stops as soon as nothing
 * moves. The iteration cap makes the cost bounded no matter what the data does.
 */

/** Per-resource claim on one consumer's intake, in units per second. */
data class InputFlow(
    val resource: ResourceId,
    /** What this consumer would eat at 100% capacity. */
    val demandPerS: GameDecimal,
    /**
     * What the whole economy has of this resource for processors to share, per
     * second: gross supply times the claim cap. Economy-wide rather than
     * per-consumer, because that is the number the player can act on — "the
     * refineries want 165 Oil/s and the planet produces 120".
     */
    val availablePerS: GameDecimal,
    /** What this consumer actually eats at its achieved utilization. */
    val consumedPerS: GameDecimal,
) {
    /** True when the economy cannot cover this consumer's demand on its own. */
    val isShort: Boolean get() = availablePerS.lt(demandPerS)
}

/** Everything the UI and the diagnostics need to explain one processor. */
data class ConsumerFlow(
    val consumerId: String,
    val owned: Int,
    /** 0..1. 1 means every input is fully supplied. */
    val utilization: Double,
    /** Output per second if every input were fully supplied. */
    val theoreticalOutputPerS: ResourceAmounts,
    /** Output per second at the achieved utilization — what actually arrives. */
    val actualOutputPerS: ResourceAmounts,
    val inputs: List<InputFlow>,
    /**
     * The input that is holding this processor back, or null when it is at full
     * capacity. Reported by the allocator rather than re-derived, so it always
     * names the resource that actually settled this consumer's share.
     */
    val limitingResource: ResourceId?,
) {
    val isRunning: Boolean get() = owned > 0 && utilization > 0.0

    /** 0..100, for display. */
    val utilizationPercent: Int get() = (utilization * 100).toInt().coerceIn(0, 100)
}

/**
 * The whole economy's flow for one instant, as rates per second. Everything is
 * a rate: nothing here touches a balance.
 */
data class ResourceFlowResult(
    /** Gross output of every building, producers and processors alike. */
    val supplyPerS: ResourceAmounts,
    /** Everything processors eat. */
    val consumptionPerS: ResourceAmounts,
    /** `supply − consumption`. Never negative for any resource. */
    val netPerS: ResourceAmounts,
    /** One entry per owned processor, in graph order. */
    val consumers: List<ConsumerFlow>,
    /** How many fixed-point rounds the allocation took. Diagnostics only. */
    val solverRounds: Int,
) {
    fun consumerFlow(id: String): ConsumerFlow? = consumers.firstOrNull { it.consumerId == id }

    companion object {
        val ZERO = ResourceFlowResult(
            supplyPerS = ResourceAmounts.ZERO,
            consumptionPerS = ResourceAmounts.ZERO,
            netPerS = ResourceAmounts.ZERO,
            consumers = emptyList(),
            solverRounds = 0,
        )
    }
}

/**
 * What one consumer wants and what it makes, per second, at 100% capacity.
 * Assembled by the caller (which owns the multiplier rules) and handed to
 * [solveResourceFlow], which is pure arithmetic over it.
 */
class ConsumerDemand(
    val consumerId: String,
    val owned: Int,
    /** Indexed by [ResourceId.ordinal]; zero where there is no demand. */
    val demandPerS: Array<GameDecimal>,
    /** Indexed by [ResourceId.ordinal]; output at full capacity. */
    val ratedOutputPerS: Array<GameDecimal>,
    /** Which resources this consumer actually needs, for fast iteration. */
    val inputResources: List<ResourceId>,
)

private val RESOURCE_COUNT = ResourceId.entries.size

/**
 * Solves the whole economy's utilizations.
 *
 * [producerSupplyPerS] is the input-free half of the economy, already
 * multiplied. Returns a utilization in `[0,1]` per entry of [demands], in the
 * same order, plus how many rounds it took.
 */
internal class Allocation(
    val utilization: DoubleArray,
    /** Ordinal of the resource that settled each consumer, or -1 when unlimited. */
    val limitedBy: IntArray,
    /** Per-resource supply processors were allowed to claim, after the cap. */
    val availablePerS: Array<GameDecimal>,
    val rounds: Int,
)

internal fun solveUtilizations(
    producerSupplyPerS: Array<GameDecimal>,
    demands: List<ConsumerDemand>,
): Allocation {
    val n = demands.size
    if (n == 0) {
        return Allocation(
            DoubleArray(0),
            IntArray(0),
            Array(RESOURCE_COUNT) { producerSupplyPerS[it] * PRODUCTION.maxSupplyClaimFraction },
            0,
        )
    }

    var utilization = DoubleArray(n) { 1.0 }
    var limitedBy = IntArray(n) { -1 }
    var available = Array(RESOURCE_COUNT) { GameDecimal.ZERO }
    var rounds = 0

    repeat(PRODUCTION.maxSolverRounds) {
        rounds++
        val supply = Array(RESOURCE_COUNT) { producerSupplyPerS[it] }
        for (i in demands.indices) {
            val consumer = demands[i]
            val util = utilization[i]
            if (util <= 0.0) continue
            for (r in 0 until RESOURCE_COUNT) {
                val rated = consumer.ratedOutputPerS[r]
                if (!rated.isZero()) supply[r] = supply[r] + rated * util
            }
        }

        available = Array(RESOURCE_COUNT) { supply[it] * PRODUCTION.maxSupplyClaimFraction }
        val round = allocateMaxMinFair(available, demands)

        var moved = false
        for (i in 0 until n) {
            if (kotlin.math.abs(round.utilization[i] - utilization[i]) > PRODUCTION.solverConvergenceEpsilon) {
                moved = true
            }
        }
        utilization = round.utilization
        limitedBy = round.limitedBy
        if (!moved) return Allocation(utilization, limitedBy, available, rounds)
    }
    return Allocation(utilization, limitedBy, available, rounds)
}

private class RoundResult(val utilization: DoubleArray, val limitedBy: IntArray)

/**
 * Max-min fair share of [available] between [demands].
 *
 * Repeatedly finds the scarcest resource, caps every still-unfixed consumer
 * that needs it at the ratio that resource can support, subtracts what those
 * consumers then take from *all* of their inputs, and goes round again with the
 * remainder. Resources are scanned in enum order and consumers in list order,
 * but neither affects the result: ties produce the same ratio whichever is
 * picked first.
 */
private fun allocateMaxMinFair(
    available: Array<GameDecimal>,
    demands: List<ConsumerDemand>,
): RoundResult {
    val n = demands.size
    val utilization = DoubleArray(n) { 1.0 }
    val limitedBy = IntArray(n) { -1 }
    val settled = BooleanArray(n)
    val remaining = Array(RESOURCE_COUNT) { available[it] }
    var unsettled = n

    // Each round settles at least the consumers of one resource, so the loop
    // cannot run more times than there are consumers.
    var guard = 0
    while (unsettled > 0 && guard <= n) {
        guard++

        var scarcestRatio = Double.POSITIVE_INFINITY
        var scarcestResource = -1
        for (r in 0 until RESOURCE_COUNT) {
            var claim = GameDecimal.ZERO
            for (i in 0 until n) {
                if (settled[i]) continue
                val want = demands[i].demandPerS[r]
                if (!want.isZero()) claim += want
            }
            if (claim.isZero()) continue
            val ratio = (remaining[r] / claim).toDouble()
            if (ratio < scarcestRatio) {
                scarcestRatio = ratio
                scarcestResource = r
            }
        }

        // Nothing is oversubscribed: everyone left runs flat out.
        if (scarcestResource < 0 || scarcestRatio >= 1.0) break

        val cap = if (scarcestRatio.isNaN() || scarcestRatio < 0.0) 0.0 else min(1.0, scarcestRatio)
        var settledThisRound = 0
        for (i in 0 until n) {
            if (settled[i]) continue
            if (demands[i].demandPerS[scarcestResource].isZero()) continue
            utilization[i] = cap
            limitedBy[i] = scarcestResource
            settled[i] = true
            unsettled--
            settledThisRound++
            if (cap > 0.0) {
                for (resource in demands[i].inputResources) {
                    val r = resource.ordinal
                    remaining[r] = (remaining[r] - demands[i].demandPerS[r] * cap).clampMin(GameDecimal.ZERO)
                }
            }
        }
        // Defensive: a resource with a claim but no claimant cannot happen, but
        // an empty round would spin the loop.
        if (settledThisRound == 0) break
    }

    // Whoever is left was never the scarce one; they consume what they asked for.
    for (i in 0 until n) {
        if (settled[i]) continue
        for (resource in demands[i].inputResources) {
            val r = resource.ordinal
            remaining[r] = (remaining[r] - demands[i].demandPerS[r]).clampMin(GameDecimal.ZERO)
        }
    }

    return RoundResult(utilization, limitedBy)
}

/**
 * Turns solved utilizations into the full accounting: supply, consumption, net
 * and a per-consumer explanation.
 *
 * The conservation clamp in step 6 of the pipeline lives here: consumption of
 * every resource is capped at that resource's supply before the net is taken,
 * so `netPerS` is non-negative for every resource by construction, whatever the
 * solver returned.
 */
internal fun accountFlow(
    producerSupplyPerS: Array<GameDecimal>,
    demands: List<ConsumerDemand>,
    allocation: Allocation,
    /** Per-consumer output multiplier — global × branch × ownership. Output only. */
    outputScale: DoubleArray,
): ResourceFlowResult {
    val utilization = allocation.utilization
    val supply = Array(RESOURCE_COUNT) { producerSupplyPerS[it] }
    val consumption = Array(RESOURCE_COUNT) { GameDecimal.ZERO }

    for (i in demands.indices) {
        val util = utilization[i]
        if (util <= 0.0) continue
        val consumer = demands[i]
        for (r in 0 until RESOURCE_COUNT) {
            val rated = consumer.ratedOutputPerS[r]
            if (!rated.isZero()) supply[r] = supply[r] + rated * util
        }
        for (resource in consumer.inputResources) {
            val r = resource.ordinal
            consumption[r] = consumption[r] + consumer.demandPerS[r] * util
        }
    }

    // Conservation clamp. Consumption can never exceed supply, so no balance
    // can ever fall.
    for (r in 0 until RESOURCE_COUNT) {
        if (consumption[r].gt(supply[r])) consumption[r] = supply[r]
    }

    val supplyAmounts = ResourceAmounts.build { builder ->
        for (r in 0 until RESOURCE_COUNT) builder[ResourceId.entries[r]] = supply[r]
    }
    val consumptionAmounts = ResourceAmounts.build { builder ->
        for (r in 0 until RESOURCE_COUNT) builder[ResourceId.entries[r]] = consumption[r]
    }
    val netAmounts = ResourceAmounts.build { builder ->
        for (r in 0 until RESOURCE_COUNT) {
            builder[ResourceId.entries[r]] = (supply[r] - consumption[r]).clampMin(GameDecimal.ZERO)
        }
    }

    val flows = ArrayList<ConsumerFlow>(demands.size)
    for (i in demands.indices) {
        val consumer = demands[i]
        val util = utilization[i]
        val scale = outputScale.getOrElse(i) { 1.0 }

        val theoretical = ResourceAmounts.build { builder ->
            for (r in 0 until RESOURCE_COUNT) {
                val rated = consumer.ratedOutputPerS[r]
                if (!rated.isZero()) builder[ResourceId.entries[r]] = rated * scale
            }
        }
        val actual = ResourceAmounts.build { builder ->
            for (r in 0 until RESOURCE_COUNT) {
                val rated = consumer.ratedOutputPerS[r]
                if (!rated.isZero()) builder[ResourceId.entries[r]] = rated * scale * util
            }
        }

        val inputs = consumer.inputResources.map { resource ->
            val r = resource.ordinal
            InputFlow(
                resource = resource,
                demandPerS = consumer.demandPerS[r],
                availablePerS = allocation.availablePerS[r],
                consumedPerS = consumer.demandPerS[r] * util,
            )
        }

        val limiting = allocation.limitedBy.getOrElse(i) { -1 }
        flows += ConsumerFlow(
            consumerId = consumer.consumerId,
            owned = consumer.owned,
            utilization = util,
            theoreticalOutputPerS = theoretical,
            actualOutputPerS = actual,
            inputs = inputs,
            limitingResource = if (util >= 1.0 || limiting < 0) null else ResourceId.entries[limiting],
        )
    }

    return ResourceFlowResult(
        supplyPerS = supplyAmounts,
        consumptionPerS = consumptionAmounts,
        netPerS = netAmounts,
        consumers = flows,
        solverRounds = allocation.rounds,
    )
}
