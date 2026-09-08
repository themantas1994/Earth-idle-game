package com.earthgame.idle.parity

import com.earthgame.idle.domain.achievements.ACHIEVEMENTS
import com.earthgame.idle.domain.challenges.CHALLENGES
import com.earthgame.idle.domain.challenges.CHALLENGE_BY_ID
import com.earthgame.idle.domain.challenges.disabledTechIdsForChallenge
import com.earthgame.idle.domain.events.RANDOM_EVENTS
import com.earthgame.idle.domain.milestones.MILESTONES
import com.earthgame.idle.domain.model.GAS_LIST
import com.earthgame.idle.domain.model.RESOURCE_LIST
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
 * Content parity: the achievement, challenge, event, milestone, gas and
 * resource tables.
 *
 * The brief for this port was explicit that no content may be dropped, so this
 * compares the tables entry by entry — ids, names, descriptions, thresholds,
 * weights and, for challenges, the *computed* set of technologies each one
 * locks out.
 */
class ContentParityTest {

    private val fixture = Fixtures.obj("content")

    @Test
    fun `all 32 achievements are present with the same text`() {
        val expected = fixture.getValue("achievements").jsonArray
        assertEquals("achievement count", expected.size, ACHIEVEMENTS.size)
        assertEquals("achievement count is 32", 32, ACHIEVEMENTS.size)

        for ((index, case) in expected.withIndex()) {
            val o = case.jsonObject
            val achievement = ACHIEVEMENTS[index]
            assertEquals("achievement[$index] id", o.getString("id"), achievement.id)
            assertEquals("${achievement.id} name", o.getString("name"), achievement.displayName)
            assertEquals("${achievement.id} description", o.getString("description"), achievement.description)
            assertEquals("${achievement.id} icon", o.getString("icon"), achievement.icon)
        }
    }

    @Test
    fun `all 8 challenges match, including the technologies they lock out`() {
        val expected = fixture.getValue("challenges").jsonArray
        assertEquals("challenge count", expected.size, CHALLENGES.size)
        assertEquals("challenge count is 8", 8, CHALLENGES.size)

        for ((index, case) in expected.withIndex()) {
            val o = case.jsonObject
            val challenge = CHALLENGES[index]
            assertEquals("challenge[$index] id", o.getString("id"), challenge.id)
            assertEquals("${challenge.id} name", o.getString("name"), challenge.displayName)
            assertEquals("${challenge.id} description", o.getString("description"), challenge.description)
            assertEquals("${challenge.id} icon", o.getString("icon"), challenge.icon)
            assertEquals("${challenge.id} goal", o.getString("goalDescription"), challenge.goal.description)
            assertEquals("${challenge.id} reward text", o.getString("rewardDescription"), challenge.rewardDescription)

            val restriction = o.getValue("restriction").jsonObject
            val maxTier = restriction.getValue("maxTechTier")
            assertEquals(
                "${challenge.id} maxTechTier",
                if (maxTier is JsonNull) null else maxTier.jsonPrimitive.int,
                challenge.restriction.maxTechTier,
            )
            val maxTemp = restriction.getValue("maxTemperatureC")
            if (maxTemp is JsonNull) {
                assertEquals("${challenge.id} maxTemperatureC", null, challenge.restriction.maxTemperatureC)
            } else {
                assertDoubleNear("${challenge.id} maxTemperatureC", maxTemp.jsonPrimitive.double, challenge.restriction.maxTemperatureC!!)
            }

            // The computed lockout set, which is what actually gates purchases.
            assertEquals(
                "${challenge.id} disabled technologies",
                o.getValue("disabledTechIds").jsonArray.map { it.jsonPrimitive.content }.sorted(),
                disabledTechIdsForChallenge(challenge).sorted(),
            )
        }
    }

    @Test
    fun `all 13 random events match, including weights and durations`() {
        val expected = fixture.getValue("events").jsonArray
        assertEquals("event count", expected.size, RANDOM_EVENTS.size)
        assertEquals("event count is 13", 13, RANDOM_EVENTS.size)

        for ((index, case) in expected.withIndex()) {
            val o = case.jsonObject
            val event = RANDOM_EVENTS[index]
            assertEquals("event[$index] id", o.getString("id"), event.id)
            assertEquals("${event.id} name", o.getString("name"), event.displayName)
            assertEquals("${event.id} description", o.getString("description"), event.description)
            assertEquals("${event.id} icon", o.getString("icon"), event.icon)
            assertEquals("${event.id} minCivLevel", o.getInt("minCivLevel"), event.minCivLevel)
            assertEquals("${event.id} durationSeconds", o.getInt("durationSeconds"), event.durationSeconds)
            assertEquals("${event.id} weight", o.getInt("weight"), event.weight)
            assertEquals("${event.id} isNegative", o.getBoolean("isNegative"), event.isNegative)

            val burst = o.getValue("instantGasBurstKg")
            if (burst is JsonNull) {
                assertTrue("${event.id} should have no burst", event.instantGasBurstKg.isEmpty())
            } else {
                val expectedBurst = burst.jsonObject
                assertEquals("${event.id} burst keys", expectedBurst.keys, event.instantGasBurstKg.keys.map { it.id }.toSet())
                for ((key, value) in expectedBurst) {
                    val gas = event.instantGasBurstKg.entries.first { it.key.id == key }
                    assertDoubleNear("${event.id} burst[$key]", value.jsonPrimitive.double, gas.value)
                }
            }
        }
    }

    @Test
    fun `all 39 milestone headlines are present with the same copy`() {
        val expected = fixture.getValue("milestones").jsonArray
        assertEquals("milestone count", expected.size, MILESTONES.size)
        assertEquals("milestone count is 39", 39, MILESTONES.size)

        for ((index, case) in expected.withIndex()) {
            val o = case.jsonObject
            val milestone = MILESTONES[index]
            assertEquals("milestone[$index] id", o.getString("id"), milestone.id)
            assertEquals("${milestone.id} headline", o.getString("headline"), milestone.headline)
            assertEquals("${milestone.id} body", o.getString("body"), milestone.body)
            assertEquals("${milestone.id} source", o.getString("source"), milestone.source)
            assertEquals("${milestone.id} icon", o.getString("icon"), milestone.icon)
            assertEquals("${milestone.id} category", o.getString("category"), milestone.category.name.lowercase())
        }
    }

    @Test
    fun `the gas registry matches the reference`() {
        val expected = fixture.getValue("gases").jsonArray
        assertEquals("gas count", expected.size, GAS_LIST.size)
        for ((index, case) in expected.withIndex()) {
            val o = case.jsonObject
            val gas = GAS_LIST[index]
            assertEquals("gas[$index] id", o.getString("id"), gas.id.id)
            assertEquals("${gas.id} name", o.getString("name"), gas.displayName)
            assertEquals("${gas.id} formula", o.getString("formula"), gas.formula)
            assertEquals("${gas.id} unit", o.getString("unit"), gas.unit.label)
            assertDoubleNear("${gas.id} baseline", o.getDouble("baseline"), gas.baseline)
            assertDoubleNear("${gas.id} massPerUnit", o.getDouble("massPerUnit"), gas.massPerUnit)
            assertDoubleNear("${gas.id} lifetimeYears", o.getDouble("lifetimeYears"), gas.lifetimeYears)
            assertEquals("${gas.id} directlyEmitted", o.getBoolean("directlyEmitted"), gas.directlyEmitted)
        }
    }

    @Test
    fun `the resource registry matches the reference`() {
        val expected = fixture.getValue("resources").jsonArray
        assertEquals("resource count", expected.size, RESOURCE_LIST.size)
        for ((index, case) in expected.withIndex()) {
            val o = case.jsonObject
            val resource = RESOURCE_LIST[index]
            assertEquals("resource[$index] id", o.getString("id"), resource.id.id)
            assertEquals("${resource.id} name", o.getString("name"), resource.displayName)
            assertEquals("${resource.id} shortName", o.getString("shortName"), resource.shortName)
            assertEquals("${resource.id} description", o.getString("description"), resource.description)
        }
    }

    @Test
    fun `every challenge reward is reachable through the prestige effect shape`() {
        // Challenge rewards are folded in alongside prestige upgrades, so a
        // reward using a field the aggregator ignores would silently do nothing.
        for (challenge in CHALLENGES) {
            val reward = challenge.reward
            val hasEffect = reward.globalProductionMultiplier != null ||
                reward.researchMultiplier != null ||
                reward.gasProductionMultiplier != null ||
                reward.allGasProductionMultiplier != null ||
                reward.techCostDiscount != null ||
                reward.offlineCapMultiplier != null ||
                reward.grantsStartingTechIds.isNotEmpty() ||
                reward.startingGenerators.isNotEmpty() ||
                reward.startingResources.isNotEmpty()
            assertTrue("${challenge.id} reward has no effect at all", hasEffect)
            assertTrue("${challenge.id} not registered", CHALLENGE_BY_ID.containsKey(challenge.id))
        }
    }
}
