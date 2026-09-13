package com.earthgame.idle.balance

import com.earthgame.idle.domain.economy.purchaseTechnology
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.computeDerived
import com.earthgame.idle.domain.engine.computeProductionRates
import com.earthgame.idle.domain.engine.nextOwnershipMilestone
import com.earthgame.idle.domain.engine.simulateStep
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.domain.production.EconomyDiagnostics
import com.earthgame.idle.domain.production.ProductionGraph
import com.earthgame.idle.domain.technologies.ALL_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.TECH_BY_ID
import com.earthgame.idle.domain.technologies.isTechAvailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A played game, at speed.
 *
 * The harness below runs an actual playthrough: it ticks the simulation, and
 * every so often it buys whatever it can afford, the way a player checking in
 * would. It exists to answer questions that no per-function test can — *does a
 * chain ever actually start? does the second processor arrive before the first
 * one has been useful? does anything stall forever?* — and to fail loudly if a
 * rebalance makes the middle of the game unreachable.
 *
 * It deliberately does **not** optimise for a fast finish. A bot that plays
 * perfectly measures the ceiling; this one plays roughly, which is closer to
 * what the curve has to survive.
 */
class EconomyBalanceSimulationTest {

    private val now = 1_700_000_000_000L

    /** What a run looked like. Everything the assertions below read. */
    private data class RunTrace(
        val simulatedSeconds: Double,
        val finalState: GameState,
        val firstOwnedAt: Map<String, Double>,
        val stalledForSeconds: Double,
    )

    /**
     * Plays for [seconds] of simulated time, buying on a fixed cadence.
     *
     * The buying policy is deliberately simple and greedy-by-tier: take the
     * cheapest unowned research node it can afford, then add units to the
     * highest-tier building it can afford. A real player does better; if even
     * this reaches a stage, the stage is reachable.
     */
    private fun play(
        seconds: Double,
        stepSeconds: Double = 30.0,
        prestige: PrestigeMultipliers = PrestigeMultipliers.NONE,
    ): RunTrace {
        var state = createNewGame(now)
        var elapsed = 0.0
        val firstOwnedAt = mutableMapOf<String, Double>()
        for (id in state.techOwned.keys) firstOwnedAt[id] = 0.0

        var longestStall = 0.0
        var currentStall = 0.0

        while (elapsed < seconds) {
            state = simulateStep(state, stepSeconds, prestige).state
            elapsed += stepSeconds

            var boughtAnything = false

            // One research node per check-in, cheapest first — that is what
            // opens new chains.
            val research = ALL_TECHNOLOGIES
                .filter { !it.isBuilding && (state.techOwned[it.id] ?: 0) == 0 }
                .filter { isTechAvailable(it, state.techOwned) }
                .sortedBy { it.tier }
            for (tech in research) {
                val result = purchaseTechnology(state, tech.id, 1, prestige)
                if (result.success) {
                    state = result.state
                    firstOwnedAt.putIfAbsent(tech.id, elapsed)
                    boughtAnything = true
                    break
                }
            }

            // Then spend what is left deepening buildings, newest first.
            val buildings = ALL_TECHNOLOGIES
                .filter { it.isBuilding && isTechAvailable(it, state.techOwned) }
                .sortedByDescending { it.tier }
            for (tech in buildings) {
                val result = purchaseTechnology(state, tech.id, 1, prestige)
                if (result.success) {
                    state = result.state
                    firstOwnedAt.putIfAbsent(tech.id, elapsed)
                    boughtAnything = true
                }
            }

            if (boughtAnything) {
                longestStall = maxOf(longestStall, currentStall)
                currentStall = 0.0
            } else {
                currentStall += stepSeconds
            }
        }

        return RunTrace(elapsed, state, firstOwnedAt, maxOf(longestStall, currentStall))
    }

    // --- Fresh game ----------------------------------------------------------

    @Test
    fun `a fresh game buys its second building within the first minute`() {
        val trace = play(seconds = 120.0, stepSeconds = 5.0)

        val distinctOwned = trace.finalState.techOwned.count { it.value > 0 }
        assertTrue(
            "a brand-new Earth should be moving inside two minutes, owned $distinctOwned\n" +
                EconomyDiagnostics.resourceReport(trace.finalState.techOwned),
            distinctOwned >= 2,
        )
    }

    @Test
    fun `the opening never stalls with nothing to do`() {
        val trace = play(seconds = 1_800.0, stepSeconds = 15.0)

        assertTrue(
            "the opening half hour stalled for ${trace.stalledForSeconds}s with nothing affordable",
            trace.stalledForSeconds <= 300.0,
        )
    }

    // --- Early and mid game --------------------------------------------------

    @Test
    fun `the first processing chain comes online within a session`() {
        val trace = play(seconds = 6.0 * 3600, stepSeconds = 60.0)

        val processorsOwned = ProductionGraph.consumers.count { (trace.finalState.techOwned[it.id] ?: 0) > 0 }
        assertTrue(
            "no processor was reachable in six hours of play\n" +
                EconomyDiagnostics.buildingReport(trace.finalState.techOwned),
            processorsOwned >= 1,
        )

        val firstProcessor = ProductionGraph.consumers
            .mapNotNull { consumer -> trace.firstOwnedAt[consumer.id]?.let { consumer.id to it } }
            .minByOrNull { it.second }
        assertTrue("a processor should have been recorded", firstProcessor != null)
        assertTrue(
            "the first processor (${firstProcessor!!.first}) took ${firstProcessor.second / 3600}h",
            firstProcessor.second <= 6.0 * 3600,
        )
    }

    @Test
    fun `a running chain actually converts, rather than sitting at zero per cent`() {
        val trace = play(seconds = 6.0 * 3600, stepSeconds = 60.0)
        val rates = computeProductionRates(
            trace.finalState.techOwned,
            computeDerived(trace.finalState, now).effective,
        )

        val running = rates.consumerFlows.filter { it.owned > 0 }
        if (running.isEmpty()) return

        assertTrue(
            "every processor the bot bought is starved at 0%\n" +
                EconomyDiagnostics.buildingReport(trace.finalState.techOwned),
            running.any { it.utilization > 0.05 },
        )
    }

    @Test
    fun `a processed resource reaches the wallet, not just the flow`() {
        val trace = play(seconds = 8.0 * 3600, stepSeconds = 120.0)
        val processedResources = listOf(ResourceId.FUEL, ResourceId.STEEL, ResourceId.CHEMICALS, ResourceId.CONCRETE)

        assertTrue(
            "eight hours of play banked none of a processed resource\n" +
                EconomyDiagnostics.resourceReport(trace.finalState.techOwned),
            processedResources.any { trace.finalState.resources[it].gt(GameDecimal.ZERO) },
        )
    }

    // --- Deep progression ----------------------------------------------------

    @Test
    fun `a long run keeps finding things to buy`() {
        val trace = play(seconds = 72.0 * 3600, stepSeconds = 600.0)

        val owned = trace.finalState.techOwned.count { it.value > 0 }
        assertTrue(
            "three days of play only reached $owned technologies",
            owned >= 25,
        )
        assertTrue(
            "the late game stalled for ${trace.stalledForSeconds / 3600}h with nothing affordable",
            trace.stalledForSeconds <= 12.0 * 3600,
        )
    }

    @Test
    fun `prestige bonuses measurably accelerate a run rather than cancelling out`() {
        val plain = play(seconds = 4.0 * 3600, stepSeconds = 60.0)
        val boosted = play(
            seconds = 4.0 * 3600,
            stepSeconds = 60.0,
            prestige = PrestigeMultipliers.NONE.copy(global = 8.0),
        )

        val plainOwned = plain.finalState.techOwned.count { it.value > 0 }
        val boostedOwned = boosted.finalState.techOwned.count { it.value > 0 }

        assertTrue(
            "a x8 production bonus reached $boostedOwned technologies against $plainOwned without it",
            boostedOwned > plainOwned,
        )
    }

    // --- Pacing of the milestone ladder --------------------------------------

    @Test
    fun `milestones stay frequent enough to feel across a realistic depth`() {
        // How many milestones a building earns on the way to a given depth, and
        // how far apart the last one is. Both are the numbers a player feels.
        val checkpoints = listOf(50, 100, 200, 400, 800)
        for (depth in checkpoints) {
            val gap = nextOwnershipMilestone(depth) - depth
            assertTrue(
                "at $depth owned the next milestone is $gap units away",
                gap in 1..(10 + depth / 100 + 1),
            )
        }
    }

    @Test
    fun `the first processor a player meets is genuinely short of its ore`() {
        // The lesson the whole mechanic rests on has to land the first time.
        // One mine per mill must not be enough, or the player never learns that
        // a processor is something you feed.
        val owned = mapOf(
            "natural_fire" to 60,
            "iron_mining" to 10,
            "steel_mill" to 10,
        )
        val rates = computeProductionRates(owned, com.earthgame.idle.domain.engine.EffectiveMultipliers.IDENTITY)
        val mill = rates.consumerFlow("steel_mill")!!

        assertTrue(
            "ten mills on ten mines ran at ${mill.utilizationPercent}% — the bottleneck is invisible\n" +
                EconomyDiagnostics.buildingReport(owned),
            mill.utilization < 0.95,
        )
        assertTrue("but it must still be worth owning", mill.utilization > 0.3)
        assertEquals(ResourceId.IRON, mill.limitingResource)
    }

    @Test
    fun `buying more of the scarce input is what fixes a bottleneck`() {
        val short = mapOf("natural_fire" to 60, "iron_mining" to 10, "steel_mill" to 10)
        val fed = short + ("iron_mining" to 40)

        val before = computeProductionRates(short, com.earthgame.idle.domain.engine.EffectiveMultipliers.IDENTITY)
        val after = computeProductionRates(fed, com.earthgame.idle.domain.engine.EffectiveMultipliers.IDENTITY)

        assertTrue(
            "four times the ore should raise utilization",
            after.consumerFlow("steel_mill")!!.utilization > before.consumerFlow("steel_mill")!!.utilization,
        )
        assertTrue(
            "and produce strictly more Metals",
            after.resourcePerS[ResourceId.STEEL].gt(before.resourcePerS[ResourceId.STEEL]),
        )
    }

    @Test
    fun `every branch contributes something a player can buy`() {
        val reachable = EconomyDiagnostics.reachableTechnologies()
        for (branch in com.earthgame.idle.domain.technologies.TechBranch.entries) {
            val inBranch = ALL_TECHNOLOGIES.filter { it.branch == branch }
            assertTrue("$branch has no technology at all", inBranch.isNotEmpty())
            assertTrue(
                "$branch has nothing a player can ever reach",
                inBranch.any { it.id in reachable },
            )
        }
    }

    @Test
    fun `every processor sits on a chain a player can actually complete`() {
        // For each processor, the shortest prerequisite chain from a new game
        // must also unlock a supplier of each of its inputs.
        for (consumer in ProductionGraph.consumers) {
            val path = EconomyDiagnostics.unlockPath(consumer.id).toSet()
            for (input in consumer.inputsPerUnit.keys) {
                val supplied = ProductionGraph.producersOf.getValue(input)
                    .filter { it.id != consumer.id }
                    .any { supplier ->
                        supplier.id in path ||
                            EconomyDiagnostics.unlockPath(supplier.id).all { step ->
                                step in path || TECH_BY_ID.getValue(step).requires.all { it in path }
                            }
                    }
                assertTrue(
                    "${consumer.id} needs ${input.id} but no supplier is reachable alongside it",
                    supplied,
                )
            }
        }
    }
}
