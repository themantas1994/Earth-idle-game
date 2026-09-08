package com.earthgame.idle.domain.economy

import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.ResourceAmounts
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.domain.technologies.CostLine
import com.earthgame.idle.domain.technologies.TECH_BY_ID
import com.earthgame.idle.domain.technologies.Technology
import com.earthgame.idle.domain.technologies.bulkPurchaseCost
import com.earthgame.idle.domain.technologies.isTechAvailable
import com.earthgame.idle.domain.technologies.maxAffordableQuantity
import kotlin.math.max
import kotlin.math.min

/**
 * Pricing rule of the whole game, and the one invariant this file exists to
 * protect:
 *
 *   **A price never rises for any reason other than your own purchases of that
 *   exact thing.**
 *
 * Concretely, the amount charged for a technology is a pure function of
 * `(technology, how many of that technology you already own)`, scaled down —
 * never up — by the prestige discount. Nothing about the rest of the
 * civilization enters into it. So:
 *
 * - a one-time unlock, multiplier or choice is quoted once and costs exactly
 *   that forever, however large the tree around it grows;
 * - a generator's *first* unit always costs its listed base price, and only the
 *   units you have personally bought of it make the next one dearer.
 *
 * The reference implementation once had a civilization-complexity surcharge
 * that multiplied every price by the number of distinct technologies owned. It
 * paced the middle of the game, but it did so by quietly re-pricing things the
 * player had already been shown — every new frontier made every *other*
 * frontier more expensive, so the reward for expanding was a bigger bill. It is
 * gone, and pacing comes entirely from the tier curves in `Constants.kt`, which
 * are baked into each technology's listed price up front and never move.
 * `EconomyParityTest` locks the invariant in.
 */

/** The price actually charged for [nominal] after any prestige discount. Only ever <= [nominal]. */
fun effectiveCostAmount(nominal: GameDecimal, discount: Double): GameDecimal =
    if (discount <= 0) nominal else nominal * (1.0 - min(0.9, discount))

/**
 * Restates a wallet in the "nominal" units the cost curves are written in, so
 * the closed-form affordability maths needs no knowledge of the prestige
 * discount: divide what the player holds by exactly the factor that will
 * multiply the price.
 *
 * The UI has to answer the same question the engine does, and running both off
 * this keeps a Buy button from ever offering a purchase [purchaseTechnology]
 * would then refuse.
 */
fun nominalizeWallet(
    resources: ResourceAmounts,
    costs: List<com.earthgame.idle.domain.technologies.TechCost>,
    discount: Double,
): Map<ResourceId, GameDecimal> {
    val factor = 1.0 - min(0.9, max(0.0, discount))
    val scaled = mutableMapOf<ResourceId, GameDecimal>()
    for (cost in costs) {
        val balance = resources[cost.resource]
        scaled[cost.resource] = if (factor >= 1.0) balance else balance / factor
    }
    return scaled
}

data class PurchaseResult(
    val state: GameState,
    val purchasedQuantity: Int,
    val success: Boolean,
) {
    companion object {
        fun failed(state: GameState) = PurchaseResult(state, 0, false)
    }
}

/** "Buy max" — capped by what a run can realistically own rather than by an unbounded loop. */
const val BUY_MAX_QUANTITY = Int.MAX_VALUE

/**
 * Buys up to [requestedQuantity] more units of a technology. Fails atomically:
 * either the affordable quantity (capped at what was requested) is purchased
 * and paid for in one step, or nothing changes.
 */
fun purchaseTechnology(
    state: GameState,
    techId: String,
    requestedQuantity: Int,
    prestige: PrestigeMultipliers,
    disabledTechIds: Set<String> = emptySet(),
): PurchaseResult {
    val tech = TECH_BY_ID[techId] ?: return PurchaseResult.failed(state)
    if (techId in disabledTechIds) return PurchaseResult.failed(state)
    if (!isTechAvailable(tech, state.techOwned)) return PurchaseResult.failed(state)

    val owned = state.techOwned[techId] ?: 0
    val roomLeft = tech.maxOwned - owned
    if (roomLeft <= 0) return PurchaseResult.failed(state)

    val scaledAvailable = nominalizeWallet(state.resources, tech.cost, prestige.techCostDiscount)
    val cappedQuantity = min(requestedQuantity, roomLeft)
    val quantity = maxAffordableQuantity(tech, owned, scaledAvailable, cappedQuantity)
    if (quantity <= 0) return PurchaseResult.failed(state)

    val totalCost = bulkPurchaseCost(tech, owned, quantity)
    val resources = state.resources.toBuilder()
    for (line in totalCost) {
        val charged = effectiveCostAmount(line.amount, prestige.techCostDiscount)
        resources[line.resource] = (resources[line.resource] - charged).clampMin(GameDecimal.ZERO)
    }

    return PurchaseResult(
        state = state.copy(
            resources = resources.build(),
            techOwned = state.techOwned + (techId to owned + quantity),
        ),
        purchasedQuantity = quantity,
        success = true,
    )
}

/** Grants a technology for free (owned = max(current, 1)) — the prestige "starting tech" bonus. */
fun grantTechnology(state: GameState, techId: String): GameState {
    val owned = state.techOwned[techId] ?: 0
    if (owned > 0) return state
    return state.copy(techOwned = state.techOwned + (techId to 1))
}

/** The price the player is shown for a given buy quantity, discount included. */
fun quotedCost(tech: Technology, owned: Int, quantity: Int, discount: Double): List<CostLine> =
    bulkPurchaseCost(tech, owned, quantity).map { CostLine(it.resource, effectiveCostAmount(it.amount, discount)) }

/**
 * Seconds until the player can afford [cost] at their current income.
 *
 * A price being saved toward is far more motivating with a clock on it, and
 * because prices in this game never move the clock is a promise rather than an
 * estimate: wait that long and the thing is yours at exactly the number on the
 * button. Null means nothing is producing the resource at all, which the UI
 * renders as "no income" rather than as an infinite countdown.
 */
fun secondsUntilAffordable(
    cost: List<CostLine>,
    resources: ResourceAmounts,
    perSecond: ResourceAmounts,
): Double? {
    var worst = 0.0
    for (line in cost) {
        val held = resources[line.resource]
        val shortfall = line.amount - held
        if (shortfall.lte(GameDecimal.ZERO)) continue
        val rate = perSecond[line.resource]
        if (rate.lte(GameDecimal.ZERO)) return null
        worst = max(worst, (shortfall / rate).toDouble())
    }
    return worst
}
