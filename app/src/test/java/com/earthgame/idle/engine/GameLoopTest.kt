package com.earthgame.idle.engine

import com.earthgame.idle.domain.engine.GameLoop
import com.earthgame.idle.domain.engine.SIMULATION
import com.earthgame.idle.domain.engine.computeDerived
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.ActiveEvent
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.prestige.PRESTIGE_UPGRADE_BY_ID
import com.earthgame.idle.domain.prestige.prestigeUpgradeCost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The session layer above the physics: absences, event rolls, milestone
 * headlines, challenge settlement, purchases and the prestige reset.
 *
 * Random events are the reason [GameLoop] takes an injected [Random]: seeded,
 * the same sequence of events happens every run, which is what makes this
 * testable at all.
 */
class GameLoopTest {

    private val now = 1_700_000_000_000L

    private fun stateWith(
        vararg tech: Pair<String, Int>,
        lastTickAt: Long = now,
    ): GameState = createNewGame(now).let { base ->
        base.copy(techOwned = mapOf("natural_fire" to 1) + tech.toMap(), lastTickAt = lastTickAt)
    }

    private fun advance(state: GameState, atMs: Long, loop: GameLoop = GameLoop(Random(1))) =
        loop.advance(state, atMs, computeDerived(state, atMs))

    @Test
    fun `an ordinary tick advances the simulation`() {
        val state = stateWith("controlled_fire" to 5)
        val result = advance(state, now + 1000)

        assertTrue("energy should accrue", result.state.resources[ResourceId.ENERGY].gt(gd(0.0)))
        assertTrue("CO2 should accrue", result.state.atmosphere[GasId.CO2].gt(gd(0.0)))
        assertEquals("the clock should advance", now + 1000, result.state.lastTickAt)
        assertNull("a one-second tick is not an absence", result.events.offlineProgress)
    }

    @Test
    fun `a gap longer than the threshold is settled as an absence`() {
        val state = stateWith("controlled_fire" to 5)
        val gap = SIMULATION.OFFLINE_GAP_THRESHOLD_MS + 1000
        val result = advance(state, now + gap)

        assertNotNull("a long gap should report offline progress", result.events.offlineProgress)
        assertEquals(
            "the whole gap should be simulated",
            gap / 1000.0,
            result.events.offlineProgress!!.simulatedSeconds,
            1e-9,
        )
        assertEquals("the clock catches up fully", now + gap, result.state.lastTickAt)
    }

    @Test
    fun `a gap just under the threshold is an ordinary tick`() {
        val state = stateWith("controlled_fire" to 5)
        val result = advance(state, now + SIMULATION.OFFLINE_GAP_THRESHOLD_MS - 1)
        assertNull("just under the threshold is not an absence", result.events.offlineProgress)
    }

    @Test
    fun `with offline progress off, an absence advances the clock and nothing else`() {
        val state = stateWith("controlled_fire" to 5).let {
            it.copy(settings = it.settings.copy(offlineProgressEnabled = false))
        }
        val result = advance(state, now + 86_400_000L)

        assertNull("nothing to report", result.events.offlineProgress)
        assertEquals("no resources accrue", gd(0.0), result.state.resources[ResourceId.ENERGY])
        assertEquals(
            "but the clock still catches up, so re-enabling it cannot bank the whole absence",
            now + 86_400_000L,
            result.state.lastTickAt,
        )
    }

    @Test
    fun `time never runs backwards`() {
        val state = stateWith("controlled_fire" to 5)
        val result = advance(state, now - 5000)
        assertEquals("a backwards clock must change nothing", state, result.state)
    }

    @Test
    fun `event rolls are reproducible for a given seed`() {
        val state = stateWith("controlled_fire" to 30, "coal_mining" to 20)

        fun runSeeded(seed: Int): List<String?> {
            val loop = GameLoop(Random(seed))
            var current = state
            val fired = mutableListOf<String?>()
            for (step in 1..400) {
                val at = now + step * 1000L
                val result = loop.advance(current, at, computeDerived(current, at))
                current = result.state
                result.events.startedEventId?.let { fired += it }
            }
            return fired
        }

        assertEquals("the same seed must give the same events", runSeeded(42), runSeeded(42))
        assertTrue("...and some events should actually fire", runSeeded(42).isNotEmpty())
        assertNotEquals("...while a different seed diverges", runSeeded(42), runSeeded(7))
    }

    @Test
    fun `random events pause during a challenge`() {
        // A challenge run should be about the restriction, not about whether a
        // lucky banner showed up.
        val state = stateWith("controlled_fire" to 30).let {
            it.copy(challenges = it.challenges.copy(activeId = "ice_age"))
        }
        val loop = GameLoop(Random(1))
        var current = state
        var eventsFired = 0
        for (step in 1..500) {
            val at = now + step * 1000L
            val result = loop.advance(current, at, computeDerived(current, at))
            current = result.state
            if (result.events.startedEventId != null) eventsFired++
        }
        assertEquals("no events during a challenge", 0, eventsFired)
    }

    @Test
    fun `no more than three events run at once`() {
        val state = stateWith("controlled_fire" to 50, "coal_mining" to 40)
        val loop = GameLoop(Random(3))
        var current = state
        var maxConcurrent = 0
        for (step in 1..2000) {
            val at = now + step * 1000L
            val result = loop.advance(current, at, computeDerived(current, at))
            current = result.state
            maxConcurrent = maxOf(maxConcurrent, current.activeEvents.size)
        }
        assertTrue("saw at most ${GameLoop.MAX_CONCURRENT_EVENTS}, was $maxConcurrent", maxConcurrent <= GameLoop.MAX_CONCURRENT_EVENTS)
    }

    @Test
    fun `expired events are pruned as the clock passes them`() {
        val state = stateWith("controlled_fire" to 5).copy(
            activeEvents = listOf(ActiveEvent("e", "good_harvest", now, now + 1000)),
        )
        val result = advance(state, now + 2000)
        assertTrue("the expired event should be gone", result.state.activeEvents.isEmpty())
    }

    @Test
    fun `milestone headlines fire once per run and land in the feed`() {
        val state = stateWith("controlled_fire" to 1)
        val first = advance(state, now + 1000)
        assertTrue("Controlled Fire should make the news", "first_smoke" in first.events.firedMilestones)
        assertEquals("and appear in the feed", listOf("first_smoke"), first.state.newsFeed.map { it.milestoneId })

        val second = advance(first.state, now + 2000)
        assertFalse("it must not fire twice", "first_smoke" in second.events.firedMilestones)
        assertEquals("the feed must not duplicate", 1, second.state.newsFeed.size)
    }

    @Test
    fun `achievements unlock once and stay unlocked`() {
        val state = stateWith("controlled_fire" to 1)
        val first = advance(state, now + 1000)
        assertTrue("First Spark should unlock", "first_spark" in first.events.newAchievements)
        assertTrue("and be recorded", first.state.achievementsUnlocked["first_spark"] == true)

        val second = advance(first.state, now + 2000)
        assertFalse("it must not re-unlock", "first_spark" in second.events.newAchievements)
    }

    @Test
    fun `a challenge whose restriction is broken fails immediately`() {
        // Ice Age fails above +2 °C.
        // The temperature is recomputed from the atmosphere every step, so the
        // planet has to actually be that warm rather than merely say it is.
        val state = stateWith("coal_mining" to 100).let { base ->
            base.copy(
                atmosphere = base.atmosphere.with(GasId.CO2, gd(1000.0)),
                challenges = base.challenges.copy(activeId = "ice_age"),
            )
        }

        val result = advance(state, now + 1000)
        assertEquals("the challenge should fail", "ice_age", result.events.challengeFailed)
        assertNull("and be cleared", result.state.challenges.activeId)
        assertFalse("without being marked complete", result.state.challenges.completed["ice_age"] == true)
    }

    @Test
    fun `a challenge whose goal is met completes and grants its reward`() {
        // Low Carbon wants 1e12 Energy.
        val state = stateWith("controlled_fire" to 5).let {
            it.copy(
                resources = it.resources.with(ResourceId.ENERGY, gd("1e13")),
                challenges = it.challenges.copy(activeId = "low_carbon"),
            )
        }
        val result = advance(state, now + 1000)
        assertEquals("the challenge should complete", "low_carbon", result.events.challengeCompleted)
        assertTrue("and be recorded", result.state.challenges.completed["low_carbon"] == true)
        assertNull("and be cleared", result.state.challenges.activeId)

        // Its permanent reward now applies to every future run.
        val derived = computeDerived(result.state, now)
        assertEquals("+25% research", 1.25, derived.prestige.research, 1e-12)
    }

    @Test
    fun `purchases are counted and report ownership thresholds`() {
        val loop = GameLoop(Random(1))
        val state = stateWith().copy(resources = createNewGame(now).resources.with(ResourceId.ENERGY, gd("1e6")))
        val result = loop.purchase(state, "natural_fire", 25, computeDerived(state, now))

        assertEquals("25 more fires", 26, result.state.techOwned["natural_fire"])
        assertEquals("counted", 25, result.state.lifetimeStats.totalTechnologiesPurchased)
        assertNotNull("a bulk buy crosses thresholds", result.events.ownershipMilestone)
        assertEquals(
            "and names the highest crossed",
            20,
            result.events.ownershipMilestone!!.atUnits,
        )
        assertEquals("with the generator's new total multiplier", 4.0, result.events.ownershipMilestone.multiplier, 0.0)
    }

    @Test
    fun `a refused purchase changes nothing`() {
        val loop = GameLoop(Random(1))
        val state = stateWith()
        val result = loop.purchase(state, "stellar_energy", 1, computeDerived(state, now))
        assertTrue("the same instance should come back", result.state === state)
        assertNull(result.events.ownershipMilestone)
    }

    @Test
    fun `prestige upgrades cost what they quote and never go past their cap`() {
        val loop = GameLoop(Random(1))
        val upgrade = PRESTIGE_UPGRADE_BY_ID.getValue("rapid_research")
        var state = stateWith().let {
            it.copy(prestige = it.prestige.copy(earthPoints = gd("1e12")))
        }

        repeat(upgrade.maxLevel) { level ->
            val before = state.prestige.earthPoints
            val cost = prestigeUpgradeCost(upgrade, level)
            state = loop.buyPrestigeUpgrade(state, upgrade.id)
            assertEquals("level after buy ${level + 1}", level + 1, state.prestige.upgradesOwned[upgrade.id])

            // The charge is recovered by differencing two values eight orders
            // of magnitude apart, which costs the last digit or two of the
            // larger one: the error scales with the *balance*, not the cost. A
            // player holding 1e12 points cannot be short-changed by more than
            // about 1e-4 of a point, which is the precision the type promises.
            val charged = before - state.prestige.earthPoints
            assertEquals(
                "should charge the quoted cost for level $level",
                cost.toDouble(),
                charged.toDouble(),
                maxOf(cost.toDouble() * 1e-9, before.toDouble() * 1e-14),
            )
        }

        val atCap = state.prestige.upgradesOwned[upgrade.id]
        state = loop.buyPrestigeUpgrade(state, upgrade.id)
        assertEquals("a maxed upgrade cannot be bought again", atCap, state.prestige.upgradesOwned[upgrade.id])
    }

    @Test
    fun `an unaffordable prestige upgrade is refused`() {
        val loop = GameLoop(Random(1))
        val state = stateWith()
        assertTrue("no points, no purchase", loop.buyPrestigeUpgrade(state, "anthropocene_mastery") === state)
    }

    @Test
    fun `resetting a living Earth is refused`() {
        val loop = GameLoop(Random(1))
        val state = stateWith("coal_mining" to 10)
        assertFalse("the planet is fine", state.collapsed)
        val result = loop.resetEarth(state, now, computeDerived(state, now))
        assertTrue("nothing should change", result.state === state)
    }

    @Test
    fun `resetting a dead Earth banks points and preserves exactly what should carry`() {
        val loop = GameLoop(Random(1))
        val collapsed = stateWith("coal_mining" to 40, "controlled_fire" to 20).let { base ->
            base.copy(
                collapsed = true,
                runNumber = 3,
                runStartedAt = now - 200_000_000,
                resources = base.resources.with(ResourceId.ENERGY, gd("1e20")),
                atmosphere = base.atmosphere.with(GasId.CO2, gd("5000")),
                seaLevelRiseMeters = 40.0,
                temperatureAnomalyC = 30.0,
                runStats = base.runStats.copy(
                    totalGasProducedKg = base.runStats.totalGasProducedKg.with(GasId.CO2, gd("1e18")),
                    peakForcingWm2 = 90.0,
                ),
                prestige = base.prestige.copy(
                    earthPoints = gd("500"),
                    upgradesOwned = mapOf("eternal_flame" to 2),
                ),
                achievementsUnlocked = mapOf("hot" to true),
                challenges = base.challenges.copy(completed = mapOf("low_carbon" to true)),
                settings = base.settings.copy(vibrationEnabled = false),
                tutorial = base.tutorial.copy(completed = true),
            )
        }

        val result = loop.resetEarth(collapsed, now, computeDerived(collapsed, now))
        val fresh = result.state

        // Carried across.
        assertTrue("points should be banked", fresh.prestige.earthPoints.gt(gd("500")))
        assertEquals("upgrades survive", mapOf("eternal_flame" to 2), fresh.prestige.upgradesOwned)
        assertTrue("achievements survive", fresh.achievementsUnlocked["hot"] == true)
        assertTrue("challenge completions survive", fresh.challenges.completed["low_carbon"] == true)
        assertFalse("settings survive", fresh.settings.vibrationEnabled)
        assertTrue("tutorial progress survives", fresh.tutorial.completed)
        assertEquals("reset counted", 1, fresh.lifetimeStats.totalResets)

        // Reset.
        assertEquals("the Earth number advances", 4, fresh.runNumber)
        assertFalse("the new planet is alive", fresh.collapsed)
        assertEquals("atmosphere cleared", gd(0.0), fresh.atmosphere[GasId.CO2])
        assertEquals("sea level cleared", 0.0, fresh.seaLevelRiseMeters, 0.0)
        assertEquals("temperature cleared", 0.0, fresh.temperatureAnomalyC, 0.0)
        assertTrue("news feed cleared", fresh.newsFeed.isEmpty())
        assertTrue("milestones re-armed", fresh.milestonesTriggered.isEmpty())
        assertEquals("no technology carries over except the starting bonuses", 1 + 2 * 2, fresh.techOwned["natural_fire"])
        assertNull("coal mining is gone", fresh.techOwned["coal_mining"])
    }

    @Test
    fun `starting a challenge resets the Earth and arms the restriction`() {
        val loop = GameLoop(Random(1))
        val state = stateWith("coal_mining" to 40).let {
            it.copy(resources = it.resources.with(ResourceId.ENERGY, gd("1e12")))
        }
        val next = loop.startChallenge(state, "primitive", now)

        assertEquals("the challenge is active", "primitive", next.challenges.activeId)
        assertEquals("on a fresh Earth", gd(0.0), next.resources[ResourceId.ENERGY])
        assertNull("with no carried technology", next.techOwned["coal_mining"])

        val derived = computeDerived(next, now)
        assertTrue(
            "and its restriction locks out later tiers",
            "steam_engine" in derived.disabledTechIds,
        )
    }
}
