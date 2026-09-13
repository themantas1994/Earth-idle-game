package com.earthgame.idle.domain.economy

import com.earthgame.idle.domain.engine.nextOwnershipMilestone
import com.earthgame.idle.domain.engine.unitsToNextOwnershipMilestone
import com.earthgame.idle.domain.model.ResourceAmounts
import com.earthgame.idle.domain.technologies.CostLine
import com.earthgame.idle.domain.technologies.Technology
import com.earthgame.idle.domain.technologies.bulkPurchaseCost
import com.earthgame.idle.domain.technologies.maxAffordableQuantity

/**
 * The **NEXT** buy mode: buy exactly enough units to land on this building's
 * next ownership milestone, and not one unit more.
 *
 * The other modes answer "how many do I want?". NEXT answers the question a
 * player of an idle game is actually asking — *"what do I need to do to make
 * something happen?"* — and it answers it with a number, a target and an exact
 * price, so nobody has to do arithmetic against a progressively-spaced ladder
 * in their head.
 *
 * Its one rule, and the reason it is not just another quantity: **it is
 * all-or-nothing.** Owning 190 with the milestone at 200, and money for four,
 * the button does not quietly buy four — that would spend the money and deliver
 * nothing the player asked for. It stays disabled and says how many more units
 * are needed. Buying four is what Max is for.
 */

/** The ownership count at which this building's next milestone lands. */
fun getNextMilestone(currentOwned: Int): Int = nextOwnershipMilestone(currentOwned)

/** How many units still have to be bought to reach it. Always at least 1. */
fun getUnitsToNextMilestone(currentOwned: Int): Int = unitsToNextOwnershipMilestone(currentOwned)

/**
 * The exact, undiscounted price of getting from [currentOwned] to
 * [nextMilestone] — the geometric-series sum over precisely those units, using
 * the same closed form every other buy mode uses, so no two modes can ever
 * disagree about what a unit costs.
 */
fun getNextPurchaseCost(tech: Technology, currentOwned: Int, nextMilestone: Int): List<CostLine> {
    val quantity = (nextMilestone - currentOwned).coerceAtLeast(0)
    if (quantity == 0) return emptyList()
    return bulkPurchaseCost(tech, currentOwned, quantity)
}

/** Everything the NEXT button needs to render itself honestly. */
data class NextPurchaseQuote(
    /** Units this purchase would buy. Never partial. */
    val quantity: Int,
    /** Ownership count it lands on. */
    val targetOwned: Int,
    /** The price, prestige discount included — exactly what will be charged. */
    val cost: List<CostLine>,
    /** Whether the whole quantity is affordable right now. */
    val affordable: Boolean,
    /**
     * Units the player can afford of the [quantity] needed, when they cannot
     * afford all of it. Drives the "need 6 more" readout; never a quantity that
     * gets bought.
     */
    val affordableNow: Int,
) {
    /** Units still unaffordable. Zero when [affordable]. */
    val shortfallUnits: Int get() = (quantity - affordableNow).coerceAtLeast(0)
}

/**
 * Quotes the NEXT purchase for one building against the player's wallet.
 *
 * Affordability is decided with [maxAffordableQuantity] over the
 * prestige-nominalized wallet — the same call `purchaseTechnology` makes — so
 * an enabled NEXT button can never be refused by the engine, and a disabled one
 * is never hiding a purchase that would have worked.
 */
fun quoteNextPurchase(
    tech: Technology,
    owned: Int,
    resources: ResourceAmounts,
    discount: Double,
): NextPurchaseQuote {
    val target = getNextMilestone(owned)
    val quantity = (target - owned).coerceAtLeast(1)
    val cost = quotedCost(tech, owned, quantity, discount)

    val wallet = nominalizeWallet(resources, tech.cost, discount)
    val affordableNow = maxAffordableQuantity(tech, owned, wallet, quantity)

    return NextPurchaseQuote(
        quantity = quantity,
        targetOwned = target,
        cost = cost,
        affordable = affordableNow >= quantity,
        affordableNow = affordableNow,
    )
}
