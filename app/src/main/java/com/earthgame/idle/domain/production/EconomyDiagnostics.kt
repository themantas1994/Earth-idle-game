package com.earthgame.idle.domain.production

import com.earthgame.idle.domain.engine.EffectiveMultipliers
import com.earthgame.idle.domain.engine.computeProductionRates
import com.earthgame.idle.domain.engine.computeTechProductionRates
import com.earthgame.idle.domain.engine.ownershipLadder
import com.earthgame.idle.domain.formatting.NumberFormatMode
import com.earthgame.idle.domain.formatting.formatNumber
import com.earthgame.idle.domain.model.RESOURCE_LIST
import com.earthgame.idle.domain.model.STARTING_TECH_IDS
import com.earthgame.idle.domain.model.resourceOf
import com.earthgame.idle.domain.technologies.ALL_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.TECH_BY_ID

/**
 * Developer-only readouts of the economy.
 *
 * None of this is shown in the app. It exists because balancing a production
 * graph by reading data files is how a chain quietly ends up impossible, and
 * because a failing balance test is far more useful when it can print the state
 * of the whole economy alongside the assertion that broke.
 *
 * Everything here is a pure function of the definitions and an ownership map,
 * so a test can call it, a future debug screen can call it, and neither needs a
 * running game.
 */
object EconomyDiagnostics {

    private val format = NumberFormatMode.COMPACT

    /** Per-resource production, consumption and net rate for one ownership map. */
    fun resourceReport(
        techOwned: Map<String, Int>,
        multipliers: EffectiveMultipliers = EffectiveMultipliers.IDENTITY,
    ): String {
        val rates = computeProductionRates(techOwned, multipliers)
        return buildString {
            appendLine("RESOURCE            produced/s     consumed/s          net/s")
            for (definition in RESOURCE_LIST) {
                val produced = rates.resourceGrossPerS[definition.id]
                val consumed = rates.resourceConsumedPerS[definition.id]
                val net = rates.resourcePerS[definition.id]
                if (produced.isZero() && consumed.isZero()) continue
                appendLine(
                    definition.shortName.padEnd(18) +
                        formatNumber(produced, format).padStart(14) +
                        formatNumber(consumed, format).padStart(15) +
                        formatNumber(net, format).padStart(15),
                )
            }
        }
    }

    /** Per-building type, inputs, outputs, throughput and utilization. */
    fun buildingReport(
        techOwned: Map<String, Int>,
        multipliers: EffectiveMultipliers = EffectiveMultipliers.IDENTITY,
    ): String {
        val rates = computeProductionRates(techOwned, multipliers)
        return buildString {
            appendLine("BUILDING                       TYPE       OWNED   UTIL  LIMIT      OUTPUT")
            for (building in ProductionGraph.buildings) {
                val owned = techOwned[building.id] ?: 0
                if (owned <= 0) continue
                val flow = rates.consumerFlow(building.id)
                val type = if (building is ConsumerDefinition) "processor" else "producer"
                val utilization = flow?.let { "${it.utilizationPercent}%" } ?: "100%"
                val limit = flow?.limitingResource?.let { resourceOf(it).shortName } ?: "—"
                // A producer's own contribution, not the global rate for a
                // resource half the tree also makes.
                val mine = computeTechProductionRates(building.technology, owned, multipliers, flow?.utilization ?: 1.0)
                val output = building.outputsPerUnit.keys.joinToString(", ") { resource ->
                    val rate = flow?.actualOutputPerS?.get(resource) ?: mine.resourcePerS[resource]
                    "${formatNumber(rate, format)} ${resourceOf(resource).shortName}/s"
                }
                appendLine(
                    building.id.take(30).padEnd(31) +
                        type.padEnd(11) +
                        owned.toString().padStart(5) +
                        utilization.padStart(7) +
                        limit.padStart(7) +
                        "  " + output,
                )
            }
        }
    }

    /** The technology tree: prerequisites, what each node unlocks, and reachability. */
    fun technologyReport(): String {
        val reachable = reachableTechnologies()
        val unlockedBy = ALL_TECHNOLOGIES
            .flatMap { tech -> tech.requires.map { it to tech.id } }
            .groupBy({ it.first }, { it.second })

        return buildString {
            appendLine("TECHNOLOGY                     TIER KIND      REACHABLE  REQUIRES -> UNLOCKS")
            for (tech in ALL_TECHNOLOGIES) {
                appendLine(
                    tech.id.take(30).padEnd(31) +
                        tech.tier.toString().padStart(4) + " " +
                        tech.kind.id.padEnd(10) +
                        (if (tech.id in reachable) "yes" else "NO ").padStart(9) + "  " +
                        tech.requires.joinToString("+").ifEmpty { "—" } +
                        " -> " +
                        unlockedBy[tech.id].orEmpty().joinToString(", ").ifEmpty { "—" },
                )
            }
        }
    }

    /**
     * The full production graph as a Mermaid diagram, for the wiki.
     *
     * Built from [ProductionGraph.edges] rather than re-walking the buildings,
     * so the picture and the validator are reading the same graph.
     */
    fun mermaidDiagram(): String = buildString {
        appendLine("flowchart LR")
        val lines = sortedSetOf<String>()
        for (edge in ProductionGraph.edges) {
            edge.fromResource?.let { lines += "    ${it.id} --> ${edge.buildingId}" }
            edge.toResource?.let { lines += "    ${edge.buildingId} --> ${it.id}" }
        }
        lines.forEach(::appendLine)
    }

    /** The first [count] rungs of the ownership milestone ladder, as a table. */
    fun milestoneReport(count: Int = 40): String = buildString {
        appendLine("RUNG   AT UNITS   STEP   MULTIPLIER")
        var previous = 0
        for ((index, rung) in ownershipLadder(count).withIndex()) {
            appendLine(
                (index + 1).toString().padStart(4) +
                    rung.atUnits.toString().padStart(11) +
                    (rung.atUnits - previous).toString().padStart(7) +
                    ("x" + rung.outputMultiplier).padStart(13),
            )
            previous = rung.atUnits
        }
    }

    /** Everything at once — what a failing balance test prints. */
    fun fullReport(techOwned: Map<String, Int>): String = buildString {
        appendLine("=== RESOURCES ===")
        append(resourceReport(techOwned))
        appendLine()
        appendLine("=== BUILDINGS ===")
        append(buildingReport(techOwned))
        appendLine()
        appendLine("=== PROBLEMS ===")
        val problems = validateEconomy()
        if (problems.isEmpty()) appendLine("none") else problems.forEach { appendLine(it.toString()) }
    }

    /** Every technology a fresh save can eventually reach through its prerequisites. */
    fun reachableTechnologies(): Set<String> {
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
        return reachable
    }

    /**
     * The shortest prerequisite chain from a new game to [techId], as ids.
     * Used by the balance simulation to describe how a player gets somewhere.
     */
    fun unlockPath(techId: String): List<String> {
        val ordered = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        fun visit(id: String) {
            if (!seen.add(id)) return
            TECH_BY_ID[id]?.requires?.forEach(::visit)
            ordered += id
        }

        visit(techId)
        return ordered
    }
}
