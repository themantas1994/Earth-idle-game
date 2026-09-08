package com.earthgame.idle.parity

import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.technologies.ALL_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.GENERATOR_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.RESEARCH_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.TECH_BY_ID
import com.earthgame.idle.domain.technologies.TechBranch
import com.earthgame.idle.domain.technologies.TechKind
import com.earthgame.idle.domain.technologies.Technology
import com.earthgame.idle.domain.technologies.gasProduction
import com.earthgame.idle.domain.technologies.generatorBaseCost
import com.earthgame.idle.domain.technologies.generatorCostGrowth
import com.earthgame.idle.domain.technologies.ladderTier
import com.earthgame.idle.domain.technologies.researchCost
import com.earthgame.idle.domain.technologies.resourceProduction
import com.earthgame.idle.domain.technologies.unlockCost
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole technology table, field by field, against the reference.
 *
 * The Kotlin data files were produced by mechanically translating the
 * TypeScript ones, and this is what makes that safe: every id, name, tier,
 * requirement, cost, growth rate and per-unit output is compared with the
 * values the reference engine actually computed. A dropped requirement or a
 * mistyped tier shows up here rather than as a subtly different game.
 */
class TechnologyParityTest {

    private val fixture = Fixtures.obj("technologies")

    @Test
    fun `the tree has the same technologies in the same order`() {
        assertEquals("technology count", fixture.getInt("count"), ALL_TECHNOLOGIES.size)
        val expectedOrder = fixture.getValue("order").jsonArray.map { it.jsonPrimitive.content }
        assertEquals("tier ordering", expectedOrder, ALL_TECHNOLOGIES.map { it.id })
    }

    @Test
    fun `every technology matches the reference field for field`() {
        for (case in fixture.getValue("technologies").jsonArray) {
            val o = case.jsonObject
            val id = o.getString("id")
            val tech = TECH_BY_ID[id]
            assertNotNull("missing technology $id", tech)
            tech!!

            assertEquals("$id name", o.getString("name"), tech.displayName)
            assertEquals("$id branch", o.getString("branch"), tech.branch.id)
            assertEquals("$id kind", o.getString("kind"), tech.kind.id)
            assertEquals("$id tier", o.getInt("tier"), tech.tier)
            assertEquals("$id icon", o.getString("icon"), tech.icon)
            assertEquals("$id description", o.getString("description"), tech.description)
            assertEquals(
                "$id requires",
                o.getValue("requires").jsonArray.map { it.jsonPrimitive.content },
                tech.requires,
            )
            assertEquals("$id choiceGroup", o.getStringOrNull("choiceGroup"), tech.choiceGroup)

            val expectedMaxOwned = o.getValue("maxOwned")
            if (expectedMaxOwned is JsonNull) {
                assertEquals("$id maxOwned (unlimited)", Technology.UNLIMITED, tech.maxOwned)
            } else {
                assertEquals("$id maxOwned", expectedMaxOwned.jsonPrimitive.int, tech.maxOwned)
            }

            assertDoubleNear("$id costGrowth", o.getDouble("costGrowth"), tech.costGrowth)

            val expectedCosts = o.getValue("cost").jsonArray
            assertEquals("$id cost line count", expectedCosts.size, tech.cost.size)
            for ((index, expected) in expectedCosts.withIndex()) {
                val e = expected.jsonObject
                assertEquals("$id cost[$index] resource", e.getString("resource"), tech.cost[index].resource.id)
                assertDoubleNear("$id cost[$index] amount", e.getDouble("baseAmount"), tech.cost[index].baseAmount)
            }

            assertEffectMatches(id, o.getValue("effect").jsonObject, tech)
        }
    }

    private fun assertEffectMatches(id: String, expected: JsonObject, tech: Technology) {
        val effect = tech.effect

        assertDoubleMap(
            "$id gasProductionPerUnit",
            expected["gasProductionPerUnit"],
            effect.gasProductionPerUnit.mapKeys { it.key.id },
        )
        assertDoubleMap(
            "$id gasRemovalPerUnit",
            expected["gasRemovalPerUnit"],
            effect.gasRemovalPerUnit.mapKeys { it.key.id },
        )
        assertDoubleMap(
            "$id resourceProductionPerUnit",
            expected["resourceProductionPerUnit"],
            effect.resourceProductionPerUnit.mapKeys { it.key.id },
        )

        assertNullableDouble("$id globalProductionMultiplier", expected["globalProductionMultiplier"], effect.globalProductionMultiplier)
        assertNullableDouble("$id researchMultiplier", expected["researchMultiplier"], effect.researchMultiplier)

        val branch = expected["branchProductionMultiplier"]
        if (branch == null || branch is JsonNull) {
            assertEquals("$id branchProductionMultiplier", null, effect.branchProductionMultiplier)
        } else {
            val b = branch.jsonObject
            assertEquals("$id branch multiplier branch", b.getString("branch"), effect.branchProductionMultiplier!!.branch.id)
            assertDoubleNear("$id branch multiplier", b.getDouble("multiplier"), effect.branchProductionMultiplier.multiplier)
        }

        val gas = expected["gasProductionMultiplier"]
        if (gas == null || gas is JsonNull) {
            assertEquals("$id gasProductionMultiplier", null, effect.gasProductionMultiplier)
        } else {
            val g = gas.jsonObject
            assertEquals("$id gas multiplier gas", g.getString("gas"), effect.gasProductionMultiplier!!.gas.id)
            assertDoubleNear("$id gas multiplier", g.getDouble("multiplier"), effect.gasProductionMultiplier.multiplier)
        }

        val resource = expected["resourceProductionMultiplier"]
        if (resource == null || resource is JsonNull) {
            assertEquals("$id resourceProductionMultiplier", null, effect.resourceProductionMultiplier)
        } else {
            val r = resource.jsonObject
            assertEquals("$id resource multiplier resource", r.getString("resource"), effect.resourceProductionMultiplier!!.resource.id)
            assertDoubleNear("$id resource multiplier", r.getDouble("multiplier"), effect.resourceProductionMultiplier.multiplier)
        }
    }

    private fun assertDoubleMap(message: String, expected: kotlinx.serialization.json.JsonElement?, actual: Map<String, Double>) {
        if (expected == null || expected is JsonNull) {
            assertTrue("$message should be empty but was $actual", actual.isEmpty())
            return
        }
        val expectedMap = expected.jsonObject
        assertEquals("$message keys", expectedMap.keys, actual.keys)
        for ((key, value) in expectedMap) {
            assertDoubleNear("$message[$key]", value.jsonPrimitive.double, actual.getValue(key))
        }
    }

    private fun assertNullableDouble(message: String, expected: kotlinx.serialization.json.JsonElement?, actual: Double?) {
        if (expected == null || expected is JsonNull) {
            assertEquals(message, null, actual)
        } else {
            assertNotNull("$message should be set", actual)
            assertDoubleNear(message, expected.jsonPrimitive.double, actual!!)
        }
    }

    @Test
    fun `the tier scaling curves match the reference`() {
        for (case in fixture.getValue("scaling").jsonArray) {
            val o = case.jsonObject
            val tier = o.getInt("tier")
            assertDoubleNear("ladderTier($tier)", o.getDouble("ladderTier"), ladderTier(tier))
            assertDoubleNear("unlockCost($tier)", o.getDouble("unlockCost"), unlockCost(tier))
            assertDoubleNear("researchCost($tier)", o.getDouble("researchCost"), researchCost(tier))
            assertDoubleNear("generatorBaseCost($tier)", o.getDouble("generatorBaseCost"), generatorBaseCost(tier))
            assertDoubleNear("gasProduction($tier)", o.getDouble("gasProduction"), gasProduction(tier))
            assertDoubleNear("resourceProduction($tier)", o.getDouble("resourceProduction"), resourceProduction(tier))
            assertDoubleNear("generatorCostGrowth($tier)", o.getDouble("generatorCostGrowth"), generatorCostGrowth(tier))
        }
    }

    // --- Graph integrity, checked directly rather than through a fixture ---

    @Test
    fun `every requirement names a technology that exists`() {
        for (tech in ALL_TECHNOLOGIES) {
            for (requirement in tech.requires) {
                assertNotNull("${tech.id} requires unknown technology $requirement", TECH_BY_ID[requirement])
            }
        }
    }

    @Test
    fun `the dependency graph has no cycles`() {
        val visiting = mutableSetOf<String>()
        val done = mutableSetOf<String>()

        fun visit(id: String, path: List<String>) {
            if (id in done) return
            assertFalse("dependency cycle: ${(path + id).joinToString(" -> ")}", id in visiting)
            visiting += id
            for (requirement in TECH_BY_ID.getValue(id).requires) visit(requirement, path + id)
            visiting -= id
            done += id
        }

        for (tech in ALL_TECHNOLOGIES) visit(tech.id, emptyList())
    }

    @Test
    fun `every technology is reachable from the starting fire`() {
        // Anything unreachable is content no player can ever see.
        val reachable = mutableSetOf("natural_fire")
        var changed = true
        while (changed) {
            changed = false
            for (tech in ALL_TECHNOLOGIES) {
                if (tech.id in reachable) continue
                if (tech.requires.all { it in reachable }) {
                    reachable += tech.id
                    changed = true
                }
            }
        }
        val unreachable = ALL_TECHNOLOGIES.map { it.id }.filterNot { it in reachable }
        assertEquals("unreachable technologies", emptyList<String>(), unreachable)
    }

    @Test
    fun `the two shopping screens are disjoint and cover the whole tree`() {
        assertEquals(
            "every technology belongs to exactly one screen",
            ALL_TECHNOLOGIES.size,
            GENERATOR_TECHNOLOGIES.size + RESEARCH_TECHNOLOGIES.size,
        )
        assertTrue(
            "the research screen must never sell a generator",
            RESEARCH_TECHNOLOGIES.none { it.kind == TechKind.GENERATOR },
        )
        assertEquals("generator count", 68, GENERATOR_TECHNOLOGIES.size)
        assertEquals("research node count", 31, RESEARCH_TECHNOLOGIES.size)
    }

    @Test
    fun `every one-time node is genuinely one-time and every generator is unbounded`() {
        for (tech in ALL_TECHNOLOGIES) {
            if (tech.kind == TechKind.GENERATOR) {
                assertEquals("${tech.id} should be unbounded", Technology.UNLIMITED, tech.maxOwned)
            } else {
                assertEquals("${tech.id} should be one-time", 1, tech.maxOwned)
            }
        }
    }

    @Test
    fun `all eleven branches are populated`() {
        val branches = ALL_TECHNOLOGIES.map { it.branch }.toSet()
        assertEquals("every branch has technology", TechBranch.entries.toSet(), branches)
    }

    @Test
    fun `gas and resource references all resolve`() {
        for (tech in ALL_TECHNOLOGIES) {
            for (gas in tech.effect.gasProductionPerUnit.keys + tech.effect.gasRemovalPerUnit.keys) {
                assertTrue("${tech.id} unknown gas $gas", gas in GasId.entries)
            }
            for (resource in tech.effect.resourceProductionPerUnit.keys + tech.cost.map { it.resource }) {
                assertTrue("${tech.id} unknown resource $resource", resource in ResourceId.entries)
            }
        }
    }
}
