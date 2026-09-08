package com.earthgame.idle.domain.technologies

import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.ResourceId
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * The whole technology tree, assembled from the per-branch data files and
 * sorted by tier — the order the UI walks and the order the "what should I buy
 * next" hint scans.
 */
val ALL_TECHNOLOGIES: List<Technology> = (
    PRIMITIVE_TECHS +
        AGRICULTURE_TECHS +
        INDUSTRY_TECHS +
        ELECTRICITY_TECHS +
        FOSSIL_FUEL_TECHS +
        TRANSPORTATION_TECHS +
        CONSTRUCTION_TECHS +
        CHEMISTRY_TECHS +
        GLOBALIZATION_TECHS +
        DIGITAL_TECHS +
        ENDGAME_TECHS
    ).sortedBy { it.tier }

val TECH_BY_ID: Map<String, Technology> = ALL_TECHNOLOGIES.associateBy { it.id }

/** Every generator, in tier order. The Production screen's entire catalogue. */
val GENERATOR_TECHNOLOGIES: List<Technology> = ALL_TECHNOLOGIES.filter { it.kind == TechKind.GENERATOR }

/**
 * Everything the Technology screen sells: the research tree of one-time
 * unlocks, permanent multipliers and branching choices. Disjoint from
 * [GENERATOR_TECHNOLOGIES], so neither screen ever shows the other's cards.
 */
val RESEARCH_TECHNOLOGIES: List<Technology> = ALL_TECHNOLOGIES.filter { it.kind != TechKind.GENERATOR }

/** Choice-group membership, precomputed so availability checks are not O(tree). */
private val TECHS_BY_CHOICE_GROUP: Map<String, List<Technology>> =
    ALL_TECHNOLOGIES.filter { it.choiceGroup != null }.groupBy { it.choiceGroup!! }

fun getTechnology(id: String): Technology =
    TECH_BY_ID[id] ?: throw IllegalArgumentException("Unknown technology id: $id")

/** Whether every prerequisite of [tech] is owned, given a map of owned counts. */
fun requirementsMet(tech: Technology, ownedCounts: Map<String, Int>): Boolean =
    tech.requires.all { (ownedCounts[it] ?: 0) > 0 }

/**
 * Whether [tech] should be visible and purchasable: requirements met, not
 * maxed, and no rival choice already taken.
 */
fun isTechAvailable(tech: Technology, ownedCounts: Map<String, Int>): Boolean {
    if (!requirementsMet(tech, ownedCounts)) return false
    if ((ownedCounts[tech.id] ?: 0) >= tech.maxOwned) return false
    val group = tech.choiceGroup
    if (group != null) {
        val rivalTaken = TECHS_BY_CHOICE_GROUP[group].orEmpty()
            .any { it.id != tech.id && (ownedCounts[it.id] ?: 0) > 0 }
        if (rivalTaken) return false
    }
    return true
}

/** Whether a rival in [tech]'s choice group has been taken, closing this option off for the run. */
fun isRivalChoiceTaken(tech: Technology, ownedCounts: Map<String, Int>): Boolean {
    val group = tech.choiceGroup ?: return false
    return TECHS_BY_CHOICE_GROUP[group].orEmpty()
        .any { it.id != tech.id && (ownedCounts[it.id] ?: 0) > 0 }
}

/** One line item of a quoted price. */
data class CostLine(val resource: ResourceId, val amount: GameDecimal)

/**
 * Cost of the next unit of [tech] given how many are already owned. Generators
 * scale geometrically with `costGrowth`; one-time purchases always cost their
 * base amount, however large the tree around them grows.
 */
fun nextPurchaseCost(tech: Technology, owned: Int): List<CostLine> {
    val growth = if (tech.isOneTime) 1.0 else tech.costGrowth
    return tech.cost.map { CostLine(it.resource, gd(it.baseAmount) * gd(growth).pow(owned)) }
}

/** Total cost of buying [quantity] more units in one purchase — the sum of a geometric series. */
fun bulkPurchaseCost(tech: Technology, owned: Int, quantity: Int): List<CostLine> {
    if (tech.isOneTime) return nextPurchaseCost(tech, owned)
    val growth = tech.costGrowth
    return tech.cost.map { cost ->
        val base = gd(cost.baseAmount) * gd(growth).pow(owned)
        // base * (growth^quantity - 1) / (growth - 1)
        val total = if (abs(growth - 1.0) < 1e-9) {
            base * quantity
        } else {
            base * (gd(growth).pow(quantity) - GameDecimal.ONE) / (growth - 1.0)
        }
        CostLine(cost.resource, total)
    }
}

/**
 * How many more units of [tech] can be afforded with [available], capped by
 * [maxQuantity]. Uses the closed-form inverse of the geometric-series sum, so
 * it works for huge quantities without looping.
 */
fun maxAffordableQuantity(
    tech: Technology,
    owned: Int,
    available: Map<ResourceId, GameDecimal>,
    maxQuantity: Int,
): Int {
    if (maxQuantity <= 0) return 0
    if (tech.isOneTime) {
        val cost = nextPurchaseCost(tech, owned)
        val canAfford = cost.all { (available[it.resource] ?: GameDecimal.ZERO).gte(it.amount) }
        return if (canAfford) 1 else 0
    }

    var limit = maxQuantity
    for (cost in tech.cost) {
        val balance = available[cost.resource] ?: GameDecimal.ZERO
        if (balance.isZero()) return 0
        val base = gd(cost.baseAmount) * gd(tech.costGrowth).pow(owned)
        val growth = tech.costGrowth

        val affordableForThisResource: Int
        if (abs(growth - 1.0) < 1e-9) {
            affordableForThisResource = clampToInt(floor((balance / base).toDouble()))
        } else {
            // Solve n from: balance >= base * (growth^n - 1) / (growth - 1)
            //            => growth^n <= balance*(growth-1)/base + 1
            val rhs = balance * (growth - 1.0) / base + GameDecimal.ONE
            affordableForThisResource = if (rhs.lte(GameDecimal.ZERO)) {
                0
            } else {
                clampToInt(floor(rhs.log10() / log10(growth)))
            }
        }
        limit = min(limit, max(0, affordableForThisResource))
    }

    limit = max(0, min(limit, maxQuantity))

    // The logarithm above is evaluated in floating point, so on a boundary
    // (balance exactly equal to the cost of n units) it can land one unit
    // either side of the true answer. Over-counting is the dangerous
    // direction: the caller would charge more than the player has and the
    // subtraction clamps at zero, handing out a free purchase. Settle the last
    // unit against the exact geometric-series cost instead of trusting the
    // float.
    fun affordable(n: Int): Boolean =
        n <= 0 || bulkPurchaseCost(tech, owned, n)
            .all { (available[it.resource] ?: GameDecimal.ZERO).gte(it.amount) }

    // The float error is bounded to a unit or two, so cap the correction
    // rather than risk a long loop if a pathological cost curve is ever added.
    val maxCorrection = 4
    var i = 0
    while (i < maxCorrection && limit > 0 && !affordable(limit)) {
        limit--
        i++
    }
    i = 0
    while (i < maxCorrection && limit < maxQuantity && affordable(limit + 1)) {
        limit++
        i++
    }

    return if (affordable(limit)) limit else 0
}

/**
 * A quantity that overflows Int is not a real purchase — per-unit costs grow
 * ~15% a unit, so anything past a few thousand is already unreachable — but it
 * must not wrap to a negative and hand out free units.
 */
private fun clampToInt(value: Double): Int = when {
    value.isNaN() -> 0
    value >= Int.MAX_VALUE.toDouble() -> Int.MAX_VALUE
    value <= 0.0 -> 0
    else -> value.toInt()
}
