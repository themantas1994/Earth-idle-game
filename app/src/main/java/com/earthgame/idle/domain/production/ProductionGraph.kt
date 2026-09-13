package com.earthgame.idle.domain.production

import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.technologies.ALL_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.TechKind
import com.earthgame.idle.domain.technologies.Technology

/**
 * The economy as a directed graph, assembled once from the technology table.
 *
 * Nothing here is hand-maintained: every producer, consumer and edge is derived
 * from the same [Technology] definitions the rest of the game runs on, so a new
 * building appears in the graph, the validator, the diagnostics dump and the
 * Production screen the moment it is added to a branch file. There is no second
 * catalogue to keep in sync.
 */
object ProductionGraph {

    /** Every building that takes no input: mines, wells, dams, farms, fires. */
    val producers: List<ProducerDefinition> = ALL_TECHNOLOGIES
        .filter { it.kind == TechKind.GENERATOR }
        .map { tech ->
            ProducerDefinition(
                id = tech.id,
                technology = tech,
                outputsPerUnit = tech.effect.resourceProductionPerUnit,
                emissionsPerUnit = tech.effect.gasProductionPerUnit,
                removalPerUnit = tech.effect.gasRemovalPerUnit,
            )
        }

    /** Every building that transforms an input flow into an output flow. */
    val consumers: List<ConsumerDefinition> = ALL_TECHNOLOGIES
        .filter { it.kind == TechKind.CONSUMER }
        .map { tech ->
            ConsumerDefinition(
                id = tech.id,
                technology = tech,
                inputsPerUnit = tech.effect.inputsPerUnit,
                outputsPerUnit = tech.effect.resourceProductionPerUnit,
                emissionsPerUnit = tech.effect.gasProductionPerUnit,
                removalPerUnit = tech.effect.gasRemovalPerUnit,
            )
        }

    val buildings: List<BuildingDefinition> = (producers + consumers).sortedBy { it.tier }

    val producerById: Map<String, ProducerDefinition> = producers.associateBy { it.id }
    val consumerById: Map<String, ConsumerDefinition> = consumers.associateBy { it.id }
    val buildingById: Map<String, BuildingDefinition> = buildings.associateBy { it.id }

    /** Buildings that make [ResourceId], whether by extraction or by processing. */
    val producersOf: Map<ResourceId, List<BuildingDefinition>> =
        ResourceId.entries.associateWith { resource ->
            buildings.filter { (it.outputsPerUnit[resource] ?: 0.0) > 0.0 }
        }

    /** Consumers that eat [ResourceId] as an input. */
    val consumersOf: Map<ResourceId, List<ConsumerDefinition>> =
        ResourceId.entries.associateWith { resource ->
            consumers.filter { (it.inputsPerUnit[resource] ?: 0.0) > 0.0 }
        }

    /** Every input→building→output edge, for the validator and the docs diagram. */
    val edges: List<ProductionEdge> = buildList {
        for (producer in producers) {
            for (output in producer.outputsPerUnit.keys) add(ProductionEdge(null, producer.id, output))
        }
        for (consumer in consumers) {
            for (input in consumer.inputsPerUnit.keys) {
                if (consumer.outputsPerUnit.isEmpty()) {
                    add(ProductionEdge(input, consumer.id, null))
                } else {
                    for (output in consumer.outputsPerUnit.keys) add(ProductionEdge(input, consumer.id, output))
                }
            }
        }
    }

    /**
     * Resources no consumer eats. Legitimate for the ones whose
     * [com.earthgame.idle.domain.model.ResourceDefinition.deadEndReason] says
     * so — Research is spent on the tree, Concrete is spent on buildings — and
     * a bug for anything else. `validateEconomy` draws that line.
     */
    val resourcesWithNoConsumer: List<ResourceId> =
        ResourceId.entries.filter { consumersOf.getValue(it).isEmpty() }

    /** Resources nothing produces. Always a bug: they can never be earned. */
    val resourcesWithNoProducer: List<ResourceId> =
        ResourceId.entries.filter { producersOf.getValue(it).isEmpty() }

    /**
     * Resource→resource dependencies induced by consumers: an edge `a → b` means
     * some consumer turns `a` into `b`. Cycles in this graph are what
     * `findResourceCycles` looks for.
     */
    val resourceDependencies: Map<ResourceId, Set<ResourceId>> =
        ResourceId.entries.associateWith { from ->
            consumersOf.getValue(from).flatMap { it.outputsPerUnit.keys }.toSet()
        }

    /**
     * Every simple cycle in [resourceDependencies], as a list of resources.
     *
     * A cycle is **not automatically an error**. Energy is both an input to the
     * mills and the output of the power stations, so `Energy → Metals →
     * Advanced Materials → Energy` is a real and intended loop in this economy.
     * What makes a loop safe is that every consumer along it is *loss-making in
     * flow terms* — it consumes strictly more resource-seconds than it produces
     * at rated capacity — so going round the loop cannot manufacture resources
     * out of nothing. `validateEconomy` checks exactly that.
     */
    fun findResourceCycles(): List<List<ResourceId>> {
        val cycles = mutableListOf<List<ResourceId>>()
        val path = mutableListOf<ResourceId>()
        val onPath = mutableSetOf<ResourceId>()
        val explored = mutableSetOf<ResourceId>()

        fun walk(node: ResourceId) {
            path += node
            onPath += node
            for (next in resourceDependencies.getValue(node).sortedBy { it.ordinal }) {
                if (next in onPath) {
                    cycles += path.subList(path.indexOf(next), path.size).toList()
                } else if (next !in explored) {
                    walk(next)
                }
            }
            onPath -= node
            path.removeAt(path.lastIndex)
            explored += node
        }

        for (resource in ResourceId.entries) if (resource !in explored) walk(resource)
        return cycles
    }
}
