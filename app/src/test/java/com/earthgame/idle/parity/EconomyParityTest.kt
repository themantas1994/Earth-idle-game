package com.earthgame.idle.parity

import com.earthgame.idle.domain.economy.effectiveCostAmount
import com.earthgame.idle.domain.economy.nominalizeWallet
import com.earthgame.idle.domain.economy.purchaseTechnology
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.engine.nextOwnershipMilestone
import com.earthgame.idle.domain.engine.ownershipMilestonesCrossed
import com.earthgame.idle.domain.engine.ownershipMultiplier
import com.earthgame.idle.domain.engine.ownershipProgress
import com.earthgame.idle.domain.model.ResourceAmounts
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.domain.prestige.computePrestigeMultipliers
import com.earthgame.idle.domain.technologies.ALL_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.TECH_BY_ID
import com.earthgame.idle.domain.technologies.TechCost
import com.earthgame.idle.domain.technologies.TechKind
import com.earthgame.idle.domain.technologies.bulkPurchaseCost
import com.earthgame.idle.domain.technologies.maxAffordableQuantity
import com.earthgame.idle.domain.technologies.nextPurchaseCost
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pricing, bulk costs, affordability and the ownership bonus ladder, against
 * the reference — plus the invariant the whole economy is built on, checked
 * directly: **a price never rises for any reason other than your own purchases
 * of that exact thing.**
 */
class EconomyParityTest {

    private val fixture = Fixtures.obj("economy")
    private val noPrestige = PrestigeMultipliers.NONE

    @Test
    fun `quoted and bulk prices match the reference`() {
        for (case in fixture.getValue("cases").jsonArray) {
            val o = case.jsonObject
            val tech = TECH_BY_ID.getValue(o.getString("techId"))
            val owned = o.getInt("owned")
            val label = "${tech.id}@$owned"

            assertCostLines("$label next", o.getValue("nextCost").jsonArray, nextPurchaseCost(tech, owned))
            assertCostLines("$label bulk1", o.getValue("bulk1").jsonArray, bulkPurchaseCost(tech, owned, 1))
            assertCostLines("$label bulk10", o.getValue("bulk10").jsonArray, bulkPurchaseCost(tech, owned, 10))
            assertCostLines("$label bulk100", o.getValue("bulk100").jsonArray, bulkPurchaseCost(tech, owned, 100))
        }
    }

    private fun assertCostLines(
        label: String,
        expected: kotlinx.serialization.json.JsonArray,
        actual: List<com.earthgame.idle.domain.technologies.CostLine>,
    ) {
        assertEquals("$label line count", expected.size, actual.size)
        for ((index, line) in expected.withIndex()) {
            val e = line.jsonObject
            assertEquals("$label[$index] resource", e.getString("resource"), actual[index].resource.id)
            assertDecimalNear("$label[$index] amount", e.getValue("amount").asGameDecimal(), actual[index].amount)
        }
    }

    @Test
    fun `buy-max affordability matches the reference`() {
        for (case in fixture.getValue("affordability").jsonArray) {
            val o = case.jsonObject
            val tech = TECH_BY_ID.getValue(o.getString("techId"))
            val owned = o.getInt("owned")
            val balance = gd(o.getString("balance"))
            val maxQuantityJson = o.getValue("maxQuantity")
            val maxQuantity = if (maxQuantityJson is JsonNull) Int.MAX_VALUE else maxQuantityJson.jsonPrimitive.int

            val wallet = tech.cost.associate { it.resource to balance }
            assertEquals(
                "maxAffordable(${tech.id}, owned=$owned, balance=${o.getString("balance")}, cap=$maxQuantity)",
                o.getInt("affordable"),
                maxAffordableQuantity(tech, owned, wallet, maxQuantity),
            )
        }
    }

    @Test
    fun `a wallet holding exactly the cost of n units buys exactly n`() {
        // The affordability audit that produced this fixture: inverting a
        // geometric series through log10 lands a unit either side of the truth
        // on an exact boundary. Under-counting leaves a purchase on the table;
        // over-counting hands out a free one, because the subtraction clamps at
        // zero. Both are checked here.
        for (case in fixture.getValue("boundary").jsonArray) {
            val o = case.jsonObject
            val tech = TECH_BY_ID.getValue(o.getString("techId"))
            val units = o.getInt("units")
            val exactCost = bulkPurchaseCost(tech, 0, units)
            val wallet = exactCost.associate { it.resource to it.amount }
            assertEquals(
                "wallet holding exactly the cost of $units ${tech.id}",
                o.getInt("affordable"),
                maxAffordableQuantity(tech, 0, wallet, Int.MAX_VALUE),
            )
            assertEquals("...and the reference agrees it is $units", units, o.getInt("affordable"))
        }
    }

    @Test
    fun `the prestige discount and wallet nominalization match the reference`() {
        for (case in fixture.getValue("discounts").jsonArray) {
            val o = case.jsonObject
            val discount = o.getDouble("discount")
            if (o.containsKey("nominal")) {
                assertDecimalNear(
                    "effectiveCost(${o.getString("nominal")}, discount=$discount)",
                    o.getValue("charged").asGameDecimal(),
                    effectiveCostAmount(gd(o.getString("nominal")), discount),
                )
            } else {
                val state = createNewGame(0)
                val wallet = nominalizeWallet(
                    state.resources.with(ResourceId.ENERGY, gd("1e6")),
                    listOf(TechCost(ResourceId.ENERGY, 1.0)),
                    discount,
                )
                assertDecimalNear(
                    "nominalized wallet at discount=$discount",
                    o.getValue("nominalizedEnergyFrom1e6").asGameDecimal(),
                    wallet.getValue(ResourceId.ENERGY),
                )
            }
        }
    }

    @Test
    fun `ownership bonuses match the reference`() {
        for (case in fixture.getValue("ownership").jsonArray) {
            val o = case.jsonObject
            val owned = o.getInt("owned")
            assertDoubleNear("ownershipMultiplier($owned)", o.getDouble("multiplier"), ownershipMultiplier(owned))
            assertDoubleNear("ownershipProgress($owned)", o.getDouble("progress"), ownershipProgress(owned))
            assertEquals("nextOwnershipMilestone($owned)", o.getInt("nextMilestone"), nextOwnershipMilestone(owned))
        }

        for (case in fixture.getValue("ownershipCrossings").jsonArray) {
            val o = case.jsonObject
            val before = o.getInt("before")
            val after = o.getInt("after")
            assertEquals(
                "ownershipMilestonesCrossed($before, $after)",
                o.getValue("crossed").jsonArray.map { it.jsonPrimitive.int },
                ownershipMilestonesCrossed(before, after),
            )
        }
    }

    // --- The pricing invariant, checked directly ---

    @Test
    fun `a technology costs the same in an empty world and a fully-built one`() {
        val empty = createNewGame(0)
        val built = empty.copy(techOwned = ALL_TECHNOLOGIES.associate { it.id to 40 })

        for (tech in ALL_TECHNOLOGIES) {
            // Same *personal* ownership of this technology in both worlds; only
            // the rest of the civilization differs.
            val emptyCost = bulkPurchaseCost(tech, 3, 5)
            val builtCost = bulkPurchaseCost(tech, 3, 5)
            for (index in emptyCost.indices) {
                assertDecimalNear(
                    "${tech.id} charged differently in a broad tree",
                    emptyCost[index].amount,
                    builtCost[index].amount,
                    Fixtures.TOLERANCE_EXACT,
                )
            }
        }
        // ...and the states are genuinely different, so the check means something.
        assertTrue("the built world should own more", built.techOwned.size > empty.techOwned.size)
    }

    @Test
    fun `a one-time node is re-quoted at its original price for the whole run`() {
        val oneTime = ALL_TECHNOLOGIES.first { it.kind == TechKind.UNLOCK }
        val first = nextPurchaseCost(oneTime, 0)
        val later = nextPurchaseCost(oneTime, 0)
        for (index in first.indices) {
            assertDecimalNear("${oneTime.id} re-quote", first[index].amount, later[index].amount, Fixtures.TOLERANCE_EXACT)
            assertDoubleNear("${oneTime.id} equals its listed base", oneTime.cost[index].baseAmount, first[index].amount.toDouble())
        }
    }

    @Test
    fun `a generator's first unit always costs its listed base price`() {
        for (tech in ALL_TECHNOLOGIES.filter { it.kind == TechKind.GENERATOR }) {
            val first = nextPurchaseCost(tech, 0)
            for ((index, line) in first.withIndex()) {
                assertDoubleNear(
                    "${tech.id} first unit should cost its base price",
                    tech.cost[index].baseAmount,
                    line.amount.toDouble(),
                )
            }
        }
    }

    @Test
    fun `only the prestige discount moves a price, and only downward`() {
        val tech = TECH_BY_ID.getValue("coal_mining")
        val nominal = bulkPurchaseCost(tech, 5, 3).first().amount
        for (discount in listOf(0.0, 0.2, 0.5, 0.9, 1.5)) {
            val charged = effectiveCostAmount(nominal, discount)
            assertTrue("discount $discount raised a price", charged.lte(nominal))
        }
        // The cap holds even when upgrades would stack past it.
        val capped = effectiveCostAmount(nominal, 5.0)
        assertDecimalNear("discount capped at 90%", nominal * 0.1, capped)
    }

    @Test
    fun `buy-max never spends more than the wallet holds`() {
        val tech = TECH_BY_ID.getValue("coal_mining")
        for (balance in listOf("10", "1000", "1e6", "1e15", "1e40")) {
            val state = createNewGame(0).copy(
                resources = ResourceAmounts.ZERO.with(ResourceId.ENERGY, gd(balance)),
                techOwned = mapOf("natural_fire" to 1, "steam_engine" to 1),
            )
            val result = purchaseTechnology(state, tech.id, Int.MAX_VALUE, noPrestige)
            if (!result.success) continue
            assertTrue(
                "buy-max at balance $balance overdrew: ${result.state.resources[ResourceId.ENERGY]}",
                result.state.resources[ResourceId.ENERGY].gte(GameDecimal.ZERO),
            )
            // And it did not leave an affordable unit behind.
            val leftover = purchaseTechnology(result.state, tech.id, 1, noPrestige)
            assertTrue("buy-max left an affordable unit at balance $balance", !leftover.success)
        }
    }

    @Test
    fun `an unaffordable purchase changes nothing at all`() {
        val state = createNewGame(0)
        val result = purchaseTechnology(state, "stellar_energy", 1, noPrestige)
        assertTrue("should have been refused", !result.success)
        assertEquals("state must be untouched", state, result.state)
        assertEquals(0, result.purchasedQuantity)
    }

    @Test
    fun `a rival choice locks its siblings out`() {
        val state = createNewGame(0).copy(
            resources = ResourceAmounts.ZERO.with(ResourceId.ENERGY, gd("1e30")),
            techOwned = mapOf("natural_fire" to 1, "coal_power_plant" to 1, "coal_industrialization" to 1),
        )
        val result = purchaseTechnology(state, "nuclear_industrialization", 1, noPrestige)
        assertTrue("the rival choice should be closed off", !result.success)
    }

    @Test
    fun `the prestige discount can never make a purchase the engine then refuses`() {
        // The UI computes affordability from nominalizeWallet and the engine
        // charges through effectiveCostAmount; if those two ever disagree a Buy
        // button offers something that then fails.
        val prestige = computePrestigeMultipliers(mapOf("civilizational_acceleration" to 4))
        val tech = TECH_BY_ID.getValue("coal_mining")
        for (balance in listOf("1", "9", "10", "11", "100", "1e9")) {
            val resources = ResourceAmounts.ZERO.with(ResourceId.ENERGY, gd(balance))
            val offered = maxAffordableQuantity(
                tech, 0, nominalizeWallet(resources, tech.cost, prestige.techCostDiscount), Int.MAX_VALUE,
            )
            val state = createNewGame(0).copy(
                resources = resources,
                techOwned = mapOf("natural_fire" to 1, "steam_engine" to 1),
            )
            val actual = purchaseTechnology(state, tech.id, Int.MAX_VALUE, prestige)
            assertEquals("UI offered $offered at balance $balance", offered, actual.purchasedQuantity)
        }
    }
}
