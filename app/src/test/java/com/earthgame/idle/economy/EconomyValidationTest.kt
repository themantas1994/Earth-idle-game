package com.earthgame.idle.economy

import com.earthgame.idle.domain.model.RESOURCE_LIST
import com.earthgame.idle.domain.model.ResourceClass
import com.earthgame.idle.domain.production.EconomyProblemSeverity
import com.earthgame.idle.domain.production.ProductionGraph
import com.earthgame.idle.domain.production.validateEconomy
import com.earthgame.idle.domain.technologies.ALL_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.CONSUMER_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.GENERATOR_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.TECH_BY_ID
import com.earthgame.idle.domain.technologies.TechBranch
import com.earthgame.idle.domain.technologies.TechKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The economy graph, checked as a graph.
 *
 * This is the test that fails the build when a data file describes a chain a
 * player cannot actually run: a mill with no mine, a technology that unlocks
 * before its own feedstock, a loop that mints resources. Every rule is
 * implemented in `domain/production/EconomyValidation.kt`; this asserts the
 * shipped economy passes all of them.
 */
class EconomyValidationTest {

    @Test
    fun `the shipped economy graph has no problems at all`() {
        val problems = validateEconomy()
        assertEquals(
            "economy problems:\n" + problems.joinToString("\n"),
            emptyList<String>(),
            problems.map { it.toString() },
        )
    }

    @Test
    fun `nothing is even a warning`() {
        val warnings = validateEconomy().filter { it.severity == EconomyProblemSeverity.WARNING }
        assertTrue("unexpected warnings:\n" + warnings.joinToString("\n"), warnings.isEmpty())
    }

    @Test
    fun `every producer and every consumer is exactly one of the two`() {
        val producerIds = GENERATOR_TECHNOLOGIES.map { it.id }.toSet()
        val consumerIds = CONSUMER_TECHNOLOGIES.map { it.id }.toSet()
        assertTrue("a building cannot be both", (producerIds intersect consumerIds).isEmpty())

        for (tech in GENERATOR_TECHNOLOGIES) {
            assertTrue("${tech.id}: a producer must declare no inputs", tech.effect.inputsPerUnit.isEmpty())
        }
        for (tech in CONSUMER_TECHNOLOGIES) {
            assertTrue("${tech.id}: a consumer must declare inputs", tech.effect.inputsPerUnit.isNotEmpty())
        }
    }

    @Test
    fun `every resource a consumer eats has at least one producer of its own`() {
        for (consumer in ProductionGraph.consumers) {
            for (input in consumer.inputsPerUnit.keys) {
                val suppliers = ProductionGraph.producersOf.getValue(input).filter { it.id != consumer.id }
                assertTrue(
                    "${consumer.id} eats ${input.id}, which nothing else makes",
                    suppliers.isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun `every resource is either consumed, spent, or documented as a dead end`() {
        for (definition in RESOURCE_LIST) {
            if (definition.deadEndReason != null) continue
            val eaten = ProductionGraph.consumersOf.getValue(definition.id).isNotEmpty()
            val spent = ALL_TECHNOLOGIES.any { tech -> tech.cost.any { it.resource == definition.id } }
            assertTrue(
                "${definition.id.id} is produced but never used — give it a consumer or a deadEndReason",
                eaten || spent,
            )
        }
    }

    @Test
    fun `every production loop is anchored to something outside itself`() {
        // The economy deliberately contains loops — power runs the mills, the
        // mills make the metal the reactors are built from, the reactors make
        // power. What keeps that from being free resources is that at least one
        // step of every loop needs something the loop cannot make, which ties
        // the whole thing back to a mine somebody had to buy and that emits when
        // it runs.
        for (cycle in ProductionGraph.findResourceCycles()) {
            val inCycle = cycle.toSet()
            val anchoredStep = cycle.indices.any { index ->
                val from = cycle[index]
                val to = cycle[(index + 1) % cycle.size]
                ProductionGraph.consumersOf.getValue(from)
                    .filter { it.outputsPerUnit.containsKey(to) }
                    .all { consumer -> consumer.inputsPerUnit.keys.any { it !in inCycle } }
            }
            assertTrue(
                "the loop ${cycle.joinToString(" -> ") { it.id }} can run entirely on itself",
                anchoredStep,
            )
        }
    }

    @Test
    fun `no processor consumes and produces the same resource`() {
        for (consumer in ProductionGraph.consumers) {
            val both = consumer.inputsPerUnit.keys intersect consumer.outputsPerUnit.keys
            assertTrue("${consumer.id} both eats and makes ${both.joinToString()}", both.isEmpty())
        }
    }

    @Test
    fun `a processor never unlocks before something that can feed it`() {
        fun ancestorsOf(id: String): Set<String> {
            val seen = mutableSetOf<String>()
            val stack = ArrayDeque(TECH_BY_ID.getValue(id).requires)
            while (stack.isNotEmpty()) {
                val next = stack.removeFirst()
                if (!seen.add(next)) continue
                TECH_BY_ID[next]?.requires?.forEach(stack::addLast)
            }
            return seen
        }

        for (consumer in ProductionGraph.consumers) {
            val before = ancestorsOf(consumer.id)
            for (input in consumer.inputsPerUnit.keys) {
                val reachable = ProductionGraph.producersOf.getValue(input)
                    .filter { it.id != consumer.id }
                    .any { it.id in before || it.technology.requires.all { req -> req in before } }
                assertTrue(
                    "${consumer.id} can be bought before anything that produces ${input.id}",
                    reachable,
                )
            }
        }
    }

    @Test
    fun `every technology is reachable from a brand-new game`() {
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
        assertEquals(
            "unreachable technologies",
            emptyList<String>(),
            ALL_TECHNOLOGIES.map { it.id }.filterNot { it in reachable },
        )
    }

    @Test
    fun `a technology never requires something from a higher tier than itself`() {
        // Tier drives cost and output scaling, so a cheap node gated behind an
        // expensive one is free content the player walks past.
        //
        // Two inversions predate the production-chain economy and are pinned by
        // the reference-parity fixtures, so they cannot be corrected without
        // breaking parity with the original engine. They are listed here rather
        // than silently tolerated, and nothing new may join them.
        val grandfathered = setOf(
            "concrete requires cement_production",
            "industrial_chemistry requires chemical_industry",
        )

        val violations = ALL_TECHNOLOGIES.flatMap { tech ->
            tech.requires.mapNotNull { TECH_BY_ID[it] }
                .filter { it.tier > tech.tier }
                .map { "${tech.id} (tier ${tech.tier}) requires ${it.id} (tier ${it.tier})" }
        }.filterNot { violation -> grandfathered.any { violation.startsWith(it.substringBefore(" requires")) && violation.contains(it.substringAfter("requires ")) } }

        assertEquals("tier inversions", emptyList<String>(), violations)
    }

    @Test
    fun `the counts the documentation quotes are the counts the tree has`() {
        // docs/wiki/Technology-System.md prints these, and a wiki that disagrees
        // with the code is worse than no wiki.
        val byKind = ALL_TECHNOLOGIES.groupingBy { it.kind }.eachCount()
        assertEquals("total technologies", 157, ALL_TECHNOLOGIES.size)
        assertEquals("producers", 78, byKind[TechKind.GENERATOR])
        assertEquals("processors", 26, byKind[TechKind.CONSUMER])
        assertEquals("multipliers", 28, byKind[TechKind.MULTIPLIER])
        assertEquals("unlocks", 23, byKind[TechKind.UNLOCK])
        assertEquals("choices", 2, byKind[TechKind.CHOICE])
        assertEquals("branches", 16, TechBranch.entries.size)
        assertEquals("resources", 15, RESOURCE_LIST.size)
    }

    @Test
    fun `every resource class is populated`() {
        for (resourceClass in ResourceClass.entries) {
            assertTrue(
                "no resource is classified as $resourceClass",
                RESOURCE_LIST.any { it.resourceClass == resourceClass },
            )
        }
    }
}
