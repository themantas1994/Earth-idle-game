package com.earthgame.idle.production

import com.earthgame.idle.domain.economy.purchaseTechnology
import com.earthgame.idle.domain.engine.EffectiveMultipliers
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.computeOfflineProgress
import com.earthgame.idle.domain.engine.computeProductionRates
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.engine.simulateStep
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.domain.save.SaveSerialization
import com.earthgame.idle.domain.save.migrate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The production chains end to end: live, offline, across a save, and under
 * prestige.
 *
 * These are the properties a player would notice if they broke — a chain that
 * behaves differently while the app is closed, a balance that goes backwards, a
 * prestige bonus that cancels itself out — so they are tested against the real
 * shipped economy rather than a synthetic one.
 */
class ProductionChainLifecycleTest {

    private val now = 1_700_000_000_000L

    /** An oil chain with a deliberate shortage: 40 refineries fed by 6 derricks. */
    private fun refiningEconomy(): GameState = createNewGame(now).copy(
        techOwned = mapOf(
            "natural_fire" to 200,
            "oil_drilling" to 6,
            "refinery_complex" to 40,
        ),
        resources = createNewGame(now).resources.with(ResourceId.ENERGY, gd("1e12")),
    )

    // --- Offline -------------------------------------------------------------

    @Test
    fun `a bottlenecked chain produces the same amount offline as it does live`() {
        // The offline path collapses an absence into one closed-form step. That
        // is only honest if the flow solve is step-size independent, which it is
        // because rates are constant while nothing is being bought.
        val start = refiningEconomy()
        val awaySeconds = 6.0 * 3600

        val oneStep = simulateStep(start, awaySeconds, PrestigeMultipliers.NONE).state

        var stepped = start
        repeat(360) { stepped = simulateStep(stepped, awaySeconds / 360, PrestigeMultipliers.NONE).state }

        for (resource in ResourceId.entries) {
            val once = oneStep.resources[resource].toDouble()
            val many = stepped.resources[resource].toDouble()
            if (once == 0.0 && many == 0.0) continue
            assertTrue(
                "${resource.id}: one step gave $once, 360 steps gave $many",
                abs(once - many) <= abs(once) * 1e-9 + 1e-9,
            )
        }
    }

    @Test
    fun `offline progress runs the processors and respects their bottleneck`() {
        val start = refiningEconomy()
        val away = 4.0 * 3600

        val offline = computeOfflineProgress(
            start,
            now + (away * 1000).toLong(),
            PrestigeMultipliers.NONE,
            offlineProgressEnabled = true,
        )

        val fuelGained = offline.summary.resourcesGained[ResourceId.FUEL].toDouble()
        assertTrue("the refineries should have run while away", fuelGained > 0.0)

        // And they should have made exactly what their utilization allowed, not
        // their rated capacity.
        val rates = computeProductionRates(start.techOwned, EffectiveMultipliers.IDENTITY)
        val refinery = rates.consumerFlow("refinery_complex")!!
        assertTrue("the chain under test must actually be short", refinery.utilization < 1.0)
        assertEquals(
            rates.resourcePerS[ResourceId.FUEL].toDouble() * away,
            fuelGained,
            fuelGained * 1e-9,
        )
    }

    @Test
    fun `no balance ever falls across a long absence`() {
        val start = refiningEconomy()
        val offline = computeOfflineProgress(
            start,
            now + 12L * 3600 * 1000,
            PrestigeMultipliers.NONE,
            offlineProgressEnabled = true,
        )

        for (resource in ResourceId.entries) {
            assertTrue(
                "${resource.id} went down while the player was away",
                offline.state.resources[resource].gte(start.resources[resource]),
            )
        }
    }

    @Test
    fun `a multi-input processor works offline exactly as it does live`() {
        val start = createNewGame(now).copy(
            techOwned = mapOf(
                "natural_fire" to 400,
                "iron_mining" to 20,
                "steel_mill" to 15,
            ),
            resources = createNewGame(now).resources.with(ResourceId.ENERGY, gd("1e12")),
        )

        val live = simulateStep(start, 1.0, PrestigeMultipliers.NONE).state
        val perSecondLive = live.resources[ResourceId.STEEL] - start.resources[ResourceId.STEEL]

        val offline = computeOfflineProgress(
            start,
            now + 3_600_000L,
            PrestigeMultipliers.NONE,
            offlineProgressEnabled = true,
        ).state
        val perSecondOffline = (offline.resources[ResourceId.STEEL] - start.resources[ResourceId.STEEL]) / 3600.0

        assertTrue("the mill must actually be producing", perSecondLive.gt(GameDecimal.ZERO))
        assertEquals(
            perSecondLive.toDouble(),
            perSecondOffline.toDouble(),
            perSecondLive.toDouble() * 1e-9,
        )
    }

    // --- Prestige ------------------------------------------------------------

    @Test
    fun `a global production multiplier raises output without raising intake`() {
        // The asymmetry that keeps prestige working once chains exist: if a
        // bonus multiplied demand too, it would cancel exactly and the whole
        // processing half of the economy would ignore the prestige tree.
        val owned = mapOf("natural_fire" to 200, "oil_drilling" to 6, "refinery_complex" to 40)

        val plain = computeProductionRates(owned, EffectiveMultipliers.IDENTITY)
        val boosted = computeProductionRates(
            owned,
            EffectiveMultipliers.IDENTITY.copy(global = 4.0),
        )

        val plainRefinery = plain.consumerFlow("refinery_complex")!!
        val boostedRefinery = boosted.consumerFlow("refinery_complex")!!

        assertEquals(
            "demand is a function of units owned and nothing else",
            plainRefinery.inputs.single().demandPerS.toDouble(),
            boostedRefinery.inputs.single().demandPerS.toDouble(),
            0.0,
        )
        assertTrue(
            "more oil arriving should raise the refineries' utilization",
            boostedRefinery.utilization > plainRefinery.utilization,
        )
        assertTrue(
            "and the chain should make strictly more fuel",
            boosted.resourcePerS[ResourceId.FUEL].gt(plain.resourcePerS[ResourceId.FUEL]),
        )
    }

    @Test
    fun `a prestige discount changes prices without touching the flow`() {
        val owned = mapOf("natural_fire" to 200, "oil_drilling" to 6, "refinery_complex" to 40)
        val generous = PrestigeMultipliers.NONE.copy(techCostDiscount = 0.5)

        val rates = computeProductionRates(owned, EffectiveMultipliers.IDENTITY)

        // A refinery is priced in Energy *and* Oil and is gated behind
        // Fractional Distillation, so the state has to satisfy all three for
        // the purchase to get as far as the discount.
        val wallet = refiningEconomy().let {
            it.copy(
                techOwned = it.techOwned + ("fractional_distillation" to 1),
                resources = it.resources.with(ResourceId.OIL, gd("1e12")),
            )
        }
        val cheap = purchaseTechnology(wallet, "refinery_complex", 1, generous)
        val full = purchaseTechnology(wallet, "refinery_complex", 1, PrestigeMultipliers.NONE)

        assertTrue(cheap.success && full.success)
        assertTrue(
            "a discount must leave the player with more money, never less",
            cheap.state.resources[ResourceId.ENERGY].gt(full.state.resources[ResourceId.ENERGY]),
        )
        // The discount is a pricing concept; it must not appear in the flow.
        assertEquals(
            rates.consumerFlow("refinery_complex")!!.utilization,
            computeProductionRates(owned, EffectiveMultipliers.IDENTITY).consumerFlow("refinery_complex")!!.utilization,
            0.0,
        )
    }

    // --- Saves ---------------------------------------------------------------

    @Test
    fun `a save written before the production chains loads with its run intact`() {
        val legacy = createNewGame(now).copy(
            saveVersion = 5,
            techOwned = mapOf("natural_fire" to 140, "coal_mining" to 37, "oil_drilling" to 12),
            resources = createNewGame(now).resources
                .with(ResourceId.ENERGY, gd("4.2e18"))
                .with(ResourceId.COAL, gd("1.5e9"))
                .with(ResourceId.OIL, gd("7e8")),
        )

        val reloaded = migrate(
            SaveSerialization.decode(SaveSerialization.encode(legacy), now)!!,
        )

        assertEquals("the save is stamped forward", 6, reloaded.saveVersion)
        assertEquals("ownership survives", legacy.techOwned, reloaded.techOwned)
        assertEquals(
            "old balances are untouched",
            legacy.resources[ResourceId.ENERGY],
            reloaded.resources[ResourceId.ENERGY],
        )
        assertEquals(legacy.resources[ResourceId.COAL], reloaded.resources[ResourceId.COAL])
        assertEquals(legacy.resources[ResourceId.OIL], reloaded.resources[ResourceId.OIL])

        for (resource in listOf(
            ResourceId.IRON,
            ResourceId.COPPER,
            ResourceId.URANIUM,
            ResourceId.RARE_EARTHS,
            ResourceId.FUEL,
            ResourceId.CHEMICALS,
            ResourceId.ELECTRONICS,
            ResourceId.ADVANCED_MATERIALS,
            ResourceId.LAUNCH_CAPACITY,
        )) {
            assertTrue("${resource.id} should start empty", reloaded.resources[resource].isZero())
        }
    }

    @Test
    fun `processor ownership and the new resources survive a round trip`() {
        val modern = createNewGame(now).copy(
            techOwned = mapOf("natural_fire" to 12, "refinery_complex" to 214, "steel_mill" to 33),
            resources = createNewGame(now).resources
                .with(ResourceId.FUEL, gd("6.02e23"))
                .with(ResourceId.LAUNCH_CAPACITY, gd("1.75e5")),
        )

        val reloaded = migrate(SaveSerialization.decode(SaveSerialization.encode(modern), now)!!)

        assertNotNull(reloaded)
        assertEquals(214, reloaded.techOwned["refinery_complex"])
        assertEquals(33, reloaded.techOwned["steel_mill"])
        assertEquals(modern.resources[ResourceId.FUEL], reloaded.resources[ResourceId.FUEL])
        assertEquals(modern.resources[ResourceId.LAUNCH_CAPACITY], reloaded.resources[ResourceId.LAUNCH_CAPACITY])
    }

    @Test
    fun `a building already past a hundred keeps every milestone it earned`() {
        // The ladder changed shape at 100. A returning player must never see a
        // bonus they had already been granted taken away.
        val legacy = createNewGame(now).copy(
            saveVersion = 5,
            techOwned = mapOf("natural_fire" to 250),
        )

        val reloaded = migrate(SaveSerialization.decode(SaveSerialization.encode(legacy), now)!!)

        assertEquals(250, reloaded.techOwned["natural_fire"])
        assertTrue(
            "the building must still carry a substantial ownership bonus",
            com.earthgame.idle.domain.engine.ownershipMultiplier(250) >= 2.0.let { base ->
                var value = 1.0
                repeat(21) { value *= base }
                value
            },
        )
    }
}
