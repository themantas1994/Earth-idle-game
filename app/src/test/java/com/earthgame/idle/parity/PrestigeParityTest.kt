package com.earthgame.idle.parity

import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.engine.offlineCapSeconds
import com.earthgame.idle.domain.model.GasAmounts
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.prestige.PRESTIGE_UPGRADES
import com.earthgame.idle.domain.prestige.PRESTIGE_UPGRADE_BY_ID
import com.earthgame.idle.domain.prestige.PrestigeGainParams
import com.earthgame.idle.domain.prestige.PrestigeUpgrade
import com.earthgame.idle.domain.prestige.calculatePrestigeGain
import com.earthgame.idle.domain.prestige.computePrestigeMultipliers
import com.earthgame.idle.domain.prestige.prestigeUpgradeCost
import com.earthgame.idle.domain.prestige.speedMultiplier
import com.earthgame.idle.domain.challenges.computeChallengeRewardEffects
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prestige layer: upgrade definitions and cost ladders, the aggregated
 * multiplier bundle for many ownership combinations (including everything at
 * max level, where the discount cap has to hold), the speed term, and the
 * Earth Points payout across gas totals from zero to 1e120.
 */
class PrestigeParityTest {

    private val fixture = Fixtures.obj("prestige")

    @Test
    fun `the upgrade table matches the reference`() {
        val expected = fixture.getValue("upgrades").jsonArray
        assertEquals("upgrade count", expected.size, PRESTIGE_UPGRADES.size)

        for (case in expected) {
            val o = case.jsonObject
            val id = o.getString("id")
            val upgrade = PRESTIGE_UPGRADE_BY_ID[id] ?: error("missing prestige upgrade $id")

            assertEquals("$id name", o.getString("name"), upgrade.displayName)
            assertEquals("$id description", o.getString("description"), upgrade.description)
            assertEquals("$id icon", o.getString("icon"), upgrade.icon)
            assertDoubleNear("$id baseCost", o.getDouble("baseCost"), upgrade.baseCost)
            assertDoubleNear("$id costGrowth", o.getDouble("costGrowth"), upgrade.costGrowth)

            val maxLevel = o.getValue("maxLevel")
            if (maxLevel is JsonNull) {
                assertEquals("$id maxLevel (unlimited)", PrestigeUpgrade.UNLIMITED_LEVELS, upgrade.maxLevel)
            } else {
                assertEquals("$id maxLevel", maxLevel.jsonPrimitive.int, upgrade.maxLevel)
            }

            val costs = o.getValue("costs").jsonArray
            for ((level, expectedCost) in costs.withIndex()) {
                assertDecimalNear(
                    "$id cost at level $level",
                    expectedCost.asGameDecimal(),
                    prestigeUpgradeCost(upgrade, level),
                )
            }
        }
    }

    @Test
    fun `aggregated multipliers match the reference for every ownership combination`() {
        for (case in fixture.getValue("bundles").jsonArray) {
            val o = case.jsonObject
            val owned = o.getValue("upgradesOwned").jsonObject.mapValues { it.value.jsonPrimitive.int }
            val completedChallenges = o.getValue("completedChallenges").jsonArray
                .associate { it.jsonPrimitive.content to true }
            val label = "upgrades=$owned challenges=${completedChallenges.keys}"

            val bundle = computePrestigeMultipliers(owned, computeChallengeRewardEffects(completedChallenges))

            assertDoubleNear("$label global", o.getDouble("global"), bundle.global)
            assertDoubleNear("$label research", o.getDouble("research"), bundle.research)
            assertDoubleNear("$label allGas", o.getDouble("allGas"), bundle.allGas)
            assertDoubleNear("$label techCostDiscount", o.getDouble("techCostDiscount"), bundle.techCostDiscount)
            assertDoubleNear("$label offlineCapMultiplier", o.getDouble("offlineCapMultiplier"), bundle.offlineCapMultiplier)
            assertDoubleNear("$label offlineCapSeconds", o.getDouble("offlineCapSeconds"), offlineCapSeconds(bundle))

            val expectedPerGas = o.getValue("perGas").jsonObject
            assertEquals("$label perGas keys", expectedPerGas.keys, bundle.perGas.keys.map { it.id }.toSet())
            for ((key, value) in expectedPerGas) {
                val gasId = GasId.entries.first { it.id == key }
                assertDoubleNear("$label perGas[$key]", value.jsonPrimitive.double, bundle.perGas.getValue(gasId))
            }

            assertEquals(
                "$label startingTechIds",
                o.getValue("startingTechIds").jsonArray.map { it.jsonPrimitive.content },
                bundle.startingTechIds,
            )
            assertEquals(
                "$label startingGenerators",
                o.getValue("startingGenerators").jsonObject.mapValues { it.value.jsonPrimitive.int },
                bundle.startingGenerators,
            )
            val expectedResources = o.getValue("startingResources").jsonObject
            assertEquals(
                "$label startingResources keys",
                expectedResources.keys,
                bundle.startingResources.keys.map { it.id }.toSet(),
            )
            for ((key, value) in expectedResources) {
                val resourceId = ResourceId.entries.first { it.id == key }
                assertDoubleNear("$label startingResources[$key]", value.jsonPrimitive.double, bundle.startingResources.getValue(resourceId))
            }
        }
    }

    @Test
    fun `the speed term matches the reference and stays inside its bounds`() {
        for (case in fixture.getValue("speeds").jsonArray) {
            val o = case.jsonObject
            val seconds = o.getDouble("runSeconds")
            val multiplier = speedMultiplier(seconds)
            assertDoubleNear("speedMultiplier($seconds)", o.getDouble("multiplier"), multiplier)
            assertTrue("speed multiplier out of bounds at $seconds", multiplier in 0.25..64.0)
        }
    }

    @Test
    fun `Earth Points payouts match the reference`() {
        for (case in fixture.getValue("gains").jsonArray) {
            val o = case.jsonObject
            val totals = GasAmounts.ZERO.with(GasId.CO2, gd(o.getString("gasKg")))
            val gain = calculatePrestigeGain(
                PrestigeGainParams(
                    totalGasProducedKg = totals,
                    peakForcingWm2 = o.getDouble("peakForcing"),
                    civLevel = o.getInt("civLevel"),
                    runDurationSeconds = o.getDouble("runSeconds"),
                ),
            )
            assertDecimalNear(
                "prestige gain (gas=${o.getString("gasKg")}, forcing=${o.getDouble("peakForcing")}, " +
                    "civ=${o.getInt("civLevel")}, run=${o.getDouble("runSeconds")})",
                o.getValue("gain").asGameDecimal(),
                gain,
            )
        }
    }

    @Test
    fun `a corrupted clock cannot mint points`() {
        // A run of zero or negative length is treated as the reference pace
        // rather than as infinitely fast.
        assertEquals(1.0, speedMultiplier(0.0), 0.0)
        assertEquals(1.0, speedMultiplier(-500.0), 0.0)
        assertEquals(1.0, speedMultiplier(Double.NaN), 0.0)
        assertEquals(1.0, speedMultiplier(Double.POSITIVE_INFINITY), 0.0)
    }

    @Test
    fun `the technology discount is capped however many upgrades stack`() {
        val everything = PRESTIGE_UPGRADES.associate {
            it.id to if (it.maxLevel == PrestigeUpgrade.UNLIMITED_LEVELS) 100 else it.maxLevel
        }
        val bundle = computePrestigeMultipliers(everything)
        assertTrue("discount must never exceed 90%", bundle.techCostDiscount <= 0.9)
    }
}
