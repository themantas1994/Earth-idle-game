package com.earthgame.idle.domain.production

import com.earthgame.idle.domain.model.RESOURCE_LIST
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.STARTING_TECH_IDS
import com.earthgame.idle.domain.model.resourceOf
import com.earthgame.idle.domain.technologies.ALL_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.TECH_BY_ID
import com.earthgame.idle.domain.technologies.TechKind
import com.earthgame.idle.domain.technologies.Technology

/**
 * Structural validation of the whole economy graph.
 *
 * The technology table is data, and data files are exactly where a production
 * chain quietly breaks: a processor whose input nothing produces, a technology
 * that unlocks a mill before the mine that feeds it, a loop that mints
 * resources out of nothing. None of those crash — they just make a game that
 * cannot be played, and they do it hours into a run.
 *
 * So the graph is checked, and the check is loud. `EconomyValidationTest` fails
 * the build on any problem, and `EarthApplication` asserts it on startup in
 * debug builds, so a broken chain never reaches a device.
 *
 * Every rule below exists because it describes something a player could hit.
 */
enum class EconomyProblemSeverity { ERROR, WARNING }

data class EconomyProblem(
    val rule: String,
    val subject: String,
    val message: String,
    val severity: EconomyProblemSeverity = EconomyProblemSeverity.ERROR,
) {
    override fun toString(): String = "[$severity] $rule: $subject — $message"
}

/**
 * Runs every rule and returns everything wrong with the economy, worst first.
 * An empty list means the graph is playable.
 */
fun validateEconomy(): List<EconomyProblem> {
    val problems = mutableListOf<EconomyProblem>()

    problems += checkIdentity()
    problems += checkProducers()
    problems += checkConsumers()
    problems += checkResources()
    problems += checkTechnologyGraph()
    problems += checkEconomicOrdering()
    problems += checkCycles()
    problems += checkStartingEconomy()

    return problems.sortedBy { it.severity.ordinal }
}

/** Only the problems that make the game unplayable. */
fun economyErrors(): List<EconomyProblem> =
    validateEconomy().filter { it.severity == EconomyProblemSeverity.ERROR }

// --- Rules -----------------------------------------------------------------

private fun checkIdentity(): List<EconomyProblem> {
    val problems = mutableListOf<EconomyProblem>()

    val duplicateTechIds = ALL_TECHNOLOGIES.groupBy { it.id }.filterValues { it.size > 1 }.keys
    for (id in duplicateTechIds) {
        problems += EconomyProblem("duplicate-technology-id", id, "defined ${ALL_TECHNOLOGIES.count { it.id == id }} times")
    }

    val duplicateResourceIds = RESOURCE_LIST.groupBy { it.id.id }.filterValues { it.size > 1 }.keys
    for (id in duplicateResourceIds) {
        problems += EconomyProblem("duplicate-resource-id", id, "two resources share the persisted id")
    }

    val duplicateBuildingIds = ProductionGraph.buildings.groupBy { it.id }.filterValues { it.size > 1 }.keys
    for (id in duplicateBuildingIds) {
        problems += EconomyProblem("duplicate-building-id", id, "a building is both a producer and a consumer")
    }

    return problems
}

private fun checkProducers(): List<EconomyProblem> {
    val problems = mutableListOf<EconomyProblem>()
    for (producer in ProductionGraph.producers) {
        if (producer.technology.effect.inputsPerUnit.isNotEmpty()) {
            problems += EconomyProblem(
                "producer-with-inputs", producer.id,
                "a producer must take no input; declare it as a CONSUMER instead",
            )
        }
        val makesNothing = producer.outputsPerUnit.isEmpty() &&
            producer.emissionsPerUnit.isEmpty() &&
            producer.removalPerUnit.isEmpty()
        if (makesNothing) {
            problems += EconomyProblem(
                "producer-without-output", producer.id,
                "produces no resource, no emission and no removal — it would be a pure cost",
            )
        }
        for ((resource, rate) in producer.outputsPerUnit) {
            if (rate <= 0.0) {
                problems += EconomyProblem(
                    "non-positive-output", producer.id,
                    "output of ${resourceOf(resource).displayName} is $rate",
                )
            }
        }
    }
    return problems
}

private fun checkConsumers(): List<EconomyProblem> {
    val problems = mutableListOf<EconomyProblem>()
    for (consumer in ProductionGraph.consumers) {
        if (consumer.inputsPerUnit.isEmpty()) {
            problems += EconomyProblem(
                "consumer-without-input", consumer.id,
                "a consumer with no input is a producer; declare it as a GENERATOR instead",
            )
        }
        for ((resource, rate) in consumer.inputsPerUnit) {
            if (rate <= 0.0) {
                problems += EconomyProblem(
                    "non-positive-input", consumer.id,
                    "input of ${resourceOf(resource).displayName} is $rate",
                )
            }
            if (ProductionGraph.producersOf.getValue(resource).none { it.id != consumer.id }) {
                problems += EconomyProblem(
                    "input-has-no-supplier", consumer.id,
                    "needs ${resourceOf(resource).displayName}, which nothing else produces",
                )
            }
        }

        val producesNothing = consumer.outputsPerUnit.isEmpty() && consumer.removalPerUnit.isEmpty()
        if (producesNothing) {
            problems += EconomyProblem(
                "consumer-without-useful-output", consumer.id,
                "consumes resources and produces neither a resource nor a gas removal",
            )
        }

        for (resource in consumer.outputsPerUnit.keys) {
            if (resource in consumer.inputsPerUnit) {
                problems += EconomyProblem(
                    "self-feeding-consumer", consumer.id,
                    "both consumes and produces ${resourceOf(resource).displayName}, which is free output",
                )
            }
        }
    }
    return problems
}

private fun checkResources(): List<EconomyProblem> {
    val problems = mutableListOf<EconomyProblem>()

    for (resource in ProductionGraph.resourcesWithNoProducer) {
        problems += EconomyProblem(
            "unreachable-resource", resource.id,
            "nothing in the game produces it, so it can never be earned",
        )
    }

    for (resource in ProductionGraph.resourcesWithNoConsumer) {
        val definition = resourceOf(resource)
        val spentOn = ALL_TECHNOLOGIES.any { tech -> tech.cost.any { it.resource == resource } }
        if (definition.deadEndReason != null) continue
        if (!spentOn) {
            problems += EconomyProblem(
                "dead-end-resource", resource.id,
                "no processor consumes it and no technology costs it — it does nothing",
            )
        }
    }

    return problems
}

private fun checkTechnologyGraph(): List<EconomyProblem> {
    val problems = mutableListOf<EconomyProblem>()

    for (tech in ALL_TECHNOLOGIES) {
        for (requirement in tech.requires) {
            if (TECH_BY_ID[requirement] == null) {
                problems += EconomyProblem(
                    "requirement-not-found", tech.id,
                    "requires \"$requirement\", which is not a technology",
                )
            }
        }
        for (line in tech.cost) {
            if (ProductionGraph.producersOf.getValue(line.resource).isEmpty()) {
                problems += EconomyProblem(
                    "cost-in-unproducible-resource", tech.id,
                    "costs ${resourceOf(line.resource).displayName}, which nothing produces",
                )
            }
        }
        if (tech.kind == TechKind.CONSUMER && tech.maxOwned != Technology.UNLIMITED) {
            problems += EconomyProblem(
                "bounded-consumer", tech.id,
                "processors are bought repeatedly and must be unbounded",
            )
        }
    }

    // Reachability from the starting technology set.
    val reachable = STARTING_TECH_IDS.toMutableSet()
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
    for (tech in ALL_TECHNOLOGIES) {
        if (tech.id !in reachable) {
            problems += EconomyProblem(
                "unreachable-technology", tech.id,
                "no chain of prerequisites leads to it from a new game",
            )
        }
    }

    return problems
}

/**
 * The rule that keeps the tree honest about the economy: **a processor must not
 * be unlockable before something that supplies each of its inputs is.**
 *
 * Checked by walking the prerequisite graph rather than by comparing tiers, so
 * it catches the real failure — a mill the player can buy and then watch sit at
 * 0% forever because the ore it eats is four branches away.
 */
private fun checkEconomicOrdering(): List<EconomyProblem> {
    val problems = mutableListOf<EconomyProblem>()

    /** Every technology that must already be owned before [tech] can be bought. */
    fun ancestorsOf(tech: Technology): Set<String> {
        val seen = mutableSetOf<String>()
        val stack = ArrayDeque(tech.requires)
        while (stack.isNotEmpty()) {
            val id = stack.removeFirst()
            if (!seen.add(id)) continue
            TECH_BY_ID[id]?.requires?.forEach(stack::addLast)
        }
        return seen
    }

    for (consumer in ProductionGraph.consumers) {
        val unlockedFirst = ancestorsOf(consumer.technology) + STARTING_TECH_IDS
        for (input in consumer.inputsPerUnit.keys) {
            val suppliers = ProductionGraph.producersOf.getValue(input).filter { it.id != consumer.id }
            val availableSupplier = suppliers.any { supplier ->
                supplier.id in unlockedFirst ||
                    // A supplier the player can already reach: it is unlocked by
                    // something this consumer's own chain has necessarily passed.
                    supplier.technology.requires.all { it in unlockedFirst }
            }
            if (!availableSupplier) {
                problems += EconomyProblem(
                    "consumer-unlocks-before-its-supply", consumer.id,
                    "needs ${resourceOf(input).displayName}, but none of its suppliers " +
                        "(${suppliers.joinToString { it.id }}) is reachable by the time it unlocks",
                )
            }
        }
    }

    return problems
}

/**
 * Loops in the resource graph, and the property that makes them safe.
 *
 * This economy has real loops — Energy runs the mills, the mills make the
 * Metals the reactors are built out of, and the reactors make Energy — and
 * forbidding them outright would forbid a power station that runs on something
 * industry produced. What matters is not that a loop exists but whether it can
 * **sustain itself**, because a loop that needs nothing from outside is a loop
 * that can be spun for free resources.
 *
 * So the rule is anchoring: **every loop must consume at least one resource the
 * loop does not produce.** Every processor along a safe loop is therefore tied,
 * somewhere, to a mine or a well that a player had to buy and that emits when
 * it runs. The Energy → Metals → Advanced Materials → Energy loop passes
 * because the mill needs Iron Ore and the materials plant needs Chemicals,
 * neither of which the loop makes.
 *
 * A second guarantee sits underneath this one, in the solver rather than the
 * data: utilization is capped at 1, so total supply can never exceed the sum of
 * what the buildings the player actually bought are rated for, whatever shape
 * the graph has. `ResourceFlowTest.a loop cannot amplify supply` asserts it
 * directly.
 */
private fun checkCycles(): List<EconomyProblem> {
    val problems = mutableListOf<EconomyProblem>()

    for (cycle in ProductionGraph.findResourceCycles()) {
        val selfContained = selfSustainingRealization(cycle)
        if (selfContained != null) {
            problems += EconomyProblem(
                "self-sustaining-production-loop",
                cycle.joinToString(" -> ") { it.id },
                "every step of this loop can be run by a processor whose inputs all come from " +
                    "inside the loop (${selfContained.joinToString()}), so it needs no extraction " +
                    "to keep going — see docs/wiki/Economy-and-Production.md",
            )
        }
    }

    return problems
}

/**
 * The processors that would let [cycle] run on nothing but itself, or null when
 * at least one step of the loop always needs something from outside.
 *
 * A loop only spins for free if *every* one of its steps has a processor that
 * can be fed entirely from within the loop. One step that needs ore, or fuel,
 * or anything a mine has to supply, anchors the whole loop to real extraction —
 * and to the emissions that come with it.
 */
private fun selfSustainingRealization(cycle: List<ResourceId>): List<String>? {
    val inCycle = cycle.toSet()
    val realization = mutableListOf<String>()

    for (index in cycle.indices) {
        val from = cycle[index]
        val to = cycle[(index + 1) % cycle.size]
        val selfContained = ProductionGraph.consumersOf.getValue(from).firstOrNull { consumer ->
            consumer.outputsPerUnit.containsKey(to) && consumer.inputsPerUnit.keys.all { it in inCycle }
        } ?: return null
        realization += selfContained.id
    }

    return realization
}

/** A brand-new game has to be able to start: something must produce without needing anything. */
private fun checkStartingEconomy(): List<EconomyProblem> {
    val problems = mutableListOf<EconomyProblem>()

    val startingProducers = STARTING_TECH_IDS.mapNotNull { ProductionGraph.producerById[it] }
    if (startingProducers.isEmpty()) {
        problems += EconomyProblem(
            "impossible-starting-economy", "new game",
            "a new game owns no input-free producer, so no resource can ever be earned",
        )
        return problems
    }

    val startingOutputs = startingProducers.flatMap { it.outputsPerUnit.keys }.toSet()
    val firstPurchases = ALL_TECHNOLOGIES.filter { tech ->
        tech.requires.all { it in STARTING_TECH_IDS } || tech.id in STARTING_TECH_IDS
    }
    val affordableOpening = firstPurchases.any { tech ->
        tech.cost.isNotEmpty() && tech.cost.all { it.resource in startingOutputs }
    }
    if (!affordableOpening) {
        problems += EconomyProblem(
            "impossible-starting-economy", "new game",
            "nothing buyable at the start is priced in a resource the starting producers make",
        )
    }

    return problems
}
