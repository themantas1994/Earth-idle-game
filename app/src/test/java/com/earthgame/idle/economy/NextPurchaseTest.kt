package com.earthgame.idle.economy

import com.earthgame.idle.domain.economy.BUY_MAX_QUANTITY
import com.earthgame.idle.domain.economy.getNextMilestone
import com.earthgame.idle.domain.economy.getNextPurchaseCost
import com.earthgame.idle.domain.economy.getUnitsToNextMilestone
import com.earthgame.idle.domain.economy.purchaseTechnology
import com.earthgame.idle.domain.economy.quoteNextPurchase
import com.earthgame.idle.domain.economy.quotedCost
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.domain.technologies.TECH_BY_ID
import com.earthgame.idle.domain.technologies.bulkPurchaseCost
import com.earthgame.idle.presentation.BuyQuantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The **NEXT** buy mode.
 *
 * Two properties matter and everything else follows from them:
 *
 * 1. it buys **exactly** the units between here and the next milestone, and
 * 2. it buys **all of them or none of them**.
 *
 * The second is the one that is easy to get wrong and expensive to get wrong:
 * a button labelled "Next ×10 → 200" that quietly buys four has spent the
 * player's money on nothing they asked for.
 */
class NextPurchaseTest {

    private val now = 1_700_000_000_000L
    private val fire = TECH_BY_ID.getValue("natural_fire")

    private fun state(owned: Int, energy: GameDecimal): GameState =
        createNewGame(now).let {
            it.copy(
                techOwned = mapOf("natural_fire" to owned),
                resources = it.resources.with(ResourceId.ENERGY, energy),
            )
        }

    private fun state(owned: Int, energy: String): GameState = state(owned, gd(energy))

    // --- The quantity -------------------------------------------------------

    @Test
    fun `next targets the milestone and buys precisely the gap`() {
        assertEquals(40, getNextMilestone(37))
        assertEquals(3, getUnitsToNextMilestone(37))

        assertEquals(100, getNextMilestone(96))
        assertEquals(4, getUnitsToNextMilestone(96))

        assertEquals(111, getNextMilestone(101))
        assertEquals(10, getUnitsToNextMilestone(101))
    }

    @Test
    fun `next never overshoots the milestone it is aiming at`() {
        for (owned in 0..600) {
            val target = getNextMilestone(owned)
            assertEquals(
                "buying the quoted quantity must land exactly on the milestone",
                target,
                owned + getUnitsToNextMilestone(owned),
            )
        }
    }

    @Test
    fun `the buy mode resolves the same quantity the helpers do`() {
        for (owned in listOf(0, 3, 37, 96, 100, 111, 199, 200)) {
            assertEquals(getUnitsToNextMilestone(owned), BuyQuantity.NEXT.resolveQuantity(owned))
        }
        assertEquals(1, BuyQuantity.ONE.resolveQuantity(37))
        assertEquals(10, BuyQuantity.TEN.resolveQuantity(37))
        assertEquals(100, BuyQuantity.HUNDRED.resolveQuantity(37))
        assertEquals(BUY_MAX_QUANTITY, BuyQuantity.MAX.resolveQuantity(37))
    }

    // --- The price ----------------------------------------------------------

    @Test
    fun `the next price is the same geometric series every other mode uses`() {
        val owned = 37
        val target = getNextMilestone(owned)
        val viaNext = getNextPurchaseCost(fire, owned, target)
        val viaBulk = bulkPurchaseCost(fire, owned, target - owned)

        assertEquals(viaBulk.size, viaNext.size)
        for (index in viaBulk.indices) {
            assertEquals(viaBulk[index].resource, viaNext[index].resource)
            assertEquals(
                "next and bulk must agree to the digit",
                viaBulk[index].amount.toDouble(),
                viaNext[index].amount.toDouble(),
                0.0,
            )
        }
    }

    @Test
    fun `buying x1 three times costs exactly what next x3 costs`() {
        // The five modes are all views of one price curve, so they must never
        // disagree about what a run of units costs.
        var owned = 37
        var stepwise = GameDecimal.ZERO
        repeat(3) {
            stepwise += quotedCost(fire, owned, 1, 0.0).single().amount
            owned++
        }
        val inOneGo = quotedCost(fire, 37, 3, 0.0).single().amount

        assertEquals(stepwise.toDouble(), inOneGo.toDouble(), inOneGo.toDouble() * 1e-12)
    }

    @Test
    fun `the quote carries the discount the engine will actually charge`() {
        val discounted = quoteNextPurchase(fire, 5, state(5, "1e9").resources, 0.5)
        val full = quoteNextPurchase(fire, 5, state(5, "1e9").resources, 0.0)

        assertEquals(full.quantity, discounted.quantity)
        assertEquals(
            full.cost.single().amount.toDouble() * 0.5,
            discounted.cost.single().amount.toDouble(),
            full.cost.single().amount.toDouble() * 1e-9,
        )
    }

    // --- All or nothing -----------------------------------------------------

    @Test
    fun `next is affordable only when the whole milestone is`() {
        // Owning 190, the next rung of the ladder is 199 — the last one of the
        // second hundred before the 200 boundary.
        assertEquals(199, getNextMilestone(190))
        val exact = quotedCost(fire, 190, 9, 0.0).single().amount
        val enough = quoteNextPurchase(fire, 190, state(190, exact).resources, 0.0)

        assertEquals(199, enough.targetOwned)
        assertEquals(9, enough.quantity)
        assertTrue("a wallet holding exactly the price must be enough", enough.affordable)
        assertEquals(0, enough.shortfallUnits)
    }

    @Test
    fun `one unit short leaves next disabled and says how far short`() {
        val forEight = quotedCost(fire, 190, 8, 0.0).single().amount
        val short = quoteNextPurchase(fire, 190, state(190, forEight).resources, 0.0)

        assertFalse("a wallet that cannot cover the ninth unit must not buy", short.affordable)
        assertEquals(9, short.quantity)
        assertEquals("it should still say what Max would do", 8, short.affordableNow)
        assertEquals(1, short.shortfallUnits)
    }

    @Test
    fun `a partial next purchase changes nothing at all`() {
        // The whole point: four units toward a milestone nine away is money
        // spent on none of the reward the button promised.
        val fourUnits = quotedCost(fire, 190, 4, 0.0).single().amount
        val before = state(190, fourUnits)

        val result = purchaseTechnology(
            before,
            "natural_fire",
            getUnitsToNextMilestone(190),
            PrestigeMultipliers.NONE,
            requireFullQuantity = true,
        )

        assertFalse("the purchase must fail", result.success)
        assertEquals(0, result.purchasedQuantity)
        assertEquals("the state must be untouched", before, result.state)
    }

    @Test
    fun `max in the same position happily buys the partial amount`() {
        val fourUnits = quotedCost(fire, 190, 4, 0.0).single().amount
        val before = state(190, fourUnits)

        val result = purchaseTechnology(before, "natural_fire", BUY_MAX_QUANTITY, PrestigeMultipliers.NONE)

        assertTrue("Max is the mode that buys what it can", result.success)
        assertEquals(4, result.purchasedQuantity)
    }

    @Test
    fun `an affordable next purchase lands exactly on the milestone`() {
        val price = quotedCost(fire, 37, 3, 0.0).single().amount
        val before = state(37, price * 2.0)

        val result = purchaseTechnology(
            before,
            "natural_fire",
            getUnitsToNextMilestone(37),
            PrestigeMultipliers.NONE,
            requireFullQuantity = true,
        )

        assertTrue(result.success)
        assertEquals(3, result.purchasedQuantity)
        assertEquals(40, result.state.techOwned.getValue("natural_fire"))
    }

    @Test
    fun `the quote never offers a purchase the engine would refuse`() {
        // The UI and the engine must answer affordability with the same
        // function, or a live button can fail on tap.
        for (owned in listOf(0, 9, 37, 96, 100, 150, 199)) {
            for (magnitude in listOf("0", "1", "1e2", "1e4", "1e9", "1e30")) {
                val current = state(owned, magnitude)
                val quote = quoteNextPurchase(fire, owned, current.resources, 0.0)
                val result = purchaseTechnology(
                    current,
                    "natural_fire",
                    quote.quantity,
                    PrestigeMultipliers.NONE,
                    requireFullQuantity = true,
                )
                assertEquals(
                    "quote and engine disagreed at owned=$owned, wallet=$magnitude",
                    quote.affordable,
                    result.success,
                )
                if (result.success) {
                    assertEquals(quote.quantity, result.purchasedQuantity)
                    assertEquals(quote.targetOwned, result.state.techOwned.getValue("natural_fire"))
                }
            }
        }
    }

    @Test
    fun `next works on a processor exactly as it does on a producer`() {
        val refinery = TECH_BY_ID.getValue("refinery_complex")
        val wallet = createNewGame(now).resources
            .with(ResourceId.ENERGY, gd("1e30"))
            .with(ResourceId.OIL, gd("1e30"))

        val quote = quoteNextPurchase(refinery, 96, wallet, 0.0)

        assertEquals(100, quote.targetOwned)
        assertEquals(4, quote.quantity)
        assertTrue(quote.affordable)
    }
}
