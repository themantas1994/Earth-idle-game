package com.earthgame.idle.storms

import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.engine.GameLoop
import com.earthgame.idle.domain.engine.SIMULATION
import com.earthgame.idle.domain.engine.advanceStormsFor
import com.earthgame.idle.domain.engine.computeDerived
import com.earthgame.idle.domain.engine.computeOfflineProgress
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.engine.simulateStep
import com.earthgame.idle.domain.engine.stormEffectsOf
import com.earthgame.idle.domain.engine.stormRiskOf
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.model.startNewRun
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.domain.save.SaveSerialization
import com.earthgame.idle.domain.storms.StormField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Storms against the rest of the game's lifecycle: the live tick, an absence,
 * a save, a reload, and the end of an Earth.
 *
 * The property that matters most here is the negative one — **a player who
 * leaves under clear skies must be settled exactly as they were before storms
 * existed**. That is what keeps the offline path's reference parity intact, and
 * it is asserted directly below.
 */
class StormLifecycleTest {

    private val now = 1_700_000_000_000L
    private val noPrestige = PrestigeMultipliers.NONE

    /** A planet warm enough to make weather, with an economy running. */
    private fun warmedPlanet(anomalyC: Double = 22.0): GameState {
        val base = createNewGame(now).copy(
            techOwned = mapOf("natural_fire" to 8, "controlled_fire" to 20, "coal_mining" to 25),
            lastTickAt = now,
        )
        // Warm it through the atmosphere rather than by setting the cached
        // temperature directly, so humidity and forcing are the ones the
        // climate model would actually produce.
        val warmed = base.copy(
            atmosphere = base.atmosphere
                .with(GasId.CO2, gd(2_400.0))
                .with(GasId.H2O, gd(anomalyC * 400.0)),
        )
        return simulateStep(warmed, 1.0, noPrestige).state.copy(lastTickAt = now)
    }

    private fun derivedFor(state: GameState) = computeDerived(state, state.lastTickAt)

    // ------------------------------------------------------------- offline --

    @Test
    fun `storms form, move and dissipate entirely while the app is closed`() {
        val before = warmedPlanet()
        assertTrue("the test needs a stormy planet", stormRiskOf(before) > 0.2)
        assertTrue("and one with no storms yet", before.storms.storms.isEmpty())

        val away = computeOfflineProgress(before, now + 6 * 3600_000L, noPrestige, true)

        assertTrue(
            "six hours away should have produced weather",
            away.stormBulletins.isNotEmpty(),
        )
        assertTrue(
            "some of it should have run its course",
            away.stormBulletins.any { it.kind == com.earthgame.idle.domain.storms.StormBulletinKind.DISSIPATED },
        )
        assertEquals(
            "and the summary should say what happened",
            away.state.storms.storms.size,
            away.summary.stormsActive,
        )
        assertTrue(
            "the storm clock advanced with the absence",
            away.state.storms.stepsElapsed > 0L,
        )
    }

    @Test
    fun `an absence under clear skies is settled exactly as it was before storms existed`() {
        // The compatibility guarantee. A player who left with no storms must
        // get bit-for-bit what the pre-storm engine gave them — which is also
        // what the offline parity fixtures encode.
        val clear = createNewGame(now).copy(
            techOwned = mapOf("natural_fire" to 8, "controlled_fire" to 20, "coal_mining" to 15),
            lastTickAt = now,
        )
        assertTrue("the premise", clear.storms.storms.isEmpty())

        val offline = computeOfflineProgress(clear, now + 4 * 3600_000L, noPrestige, true)
        val live = simulateStep(clear, 4.0 * 3600, noPrestige).state

        for (resource in ResourceId.entries) {
            assertEquals(
                "offline and live must agree exactly for $resource",
                live.resources[resource].toExponential(12),
                offline.state.resources[resource].toExponential(12),
            )
        }
        for (gas in GasId.entries) {
            assertEquals(
                "offline and live must agree exactly for $gas",
                live.atmosphere[gas].toExponential(12),
                offline.state.atmosphere[gas].toExponential(12),
            )
        }
    }

    @Test
    fun `storms running when the player leaves do tax the absence`() {
        var stormy = warmedPlanet()
        // Run the weather forward until something is actually on the board.
        var guard = 0
        while (stormy.storms.storms.isEmpty() && guard++ < 400) {
            stormy = stormy.copy(storms = advanceStormsFor(stormy, 60.0).field)
        }
        assertTrue("the test needs a live storm", stormy.storms.storms.isNotEmpty())

        val withWeather = computeOfflineProgress(stormy, now + 600_000L, noPrestige, true)
        val withoutWeather = computeOfflineProgress(
            stormy.copy(storms = StormField.EMPTY),
            now + 600_000L,
            noPrestige,
            true,
        )

        assertTrue(
            "a storm running through an absence must cost something",
            withWeather.state.resources[ResourceId.ENERGY]
                .lt(withoutWeather.state.resources[ResourceId.ENERGY]),
        )
    }

    @Test
    fun `a live session and an absence produce the same storm timeline`() {
        // Storms step on a fixed cadence with a carry, so an hour of 250 ms
        // ticks and one hour-long catch-up run the same steps.
        val start = warmedPlanet()

        val offline = advanceStormsFor(start, 3600.0).field

        var live = start
        repeat(14_400) {
            live = live.copy(storms = advanceStormsFor(live, 0.25).field)
        }

        assertEquals("step index", offline.stepsElapsed, live.storms.stepsElapsed)
        assertEquals(
            "the same storms, at the same places",
            offline.storms.map { it.id to it.latitudeDeg },
            live.storms.storms.map { it.id to it.latitudeDeg },
        )
    }

    // ------------------------------------------------------ save and reload --

    @Test
    fun `a storm survives a save and continues from where it was`() {
        var stormy = warmedPlanet()
        var guard = 0
        while (stormy.storms.storms.isEmpty() && guard++ < 400) {
            stormy = stormy.copy(storms = advanceStormsFor(stormy, 60.0).field)
        }
        assertTrue("the test needs a live storm", stormy.storms.storms.isNotEmpty())

        val revived = SaveSerialization.deserialize(SaveSerialization.serialize(stormy), now)
        assertNotNull("the save must load", revived)
        assertEquals("the storms come back unchanged", stormy.storms, revived!!.storms)
        assertEquals("and so does the seed", stormy.stormSeed, revived.stormSeed)

        // ...and the two continue identically, which is the part that matters:
        // a reloaded Earth must get the same weather as the one that was saved.
        assertEquals(
            advanceStormsFor(stormy, 1800.0).field,
            advanceStormsFor(revived, 1800.0).field,
        )
    }

    // ------------------------------------------------------------ prestige --

    @Test
    fun `a new Earth gets clear skies and its own seed`() {
        var stormy = warmedPlanet()
        var guard = 0
        while (stormy.storms.storms.isEmpty() && guard++ < 400) {
            stormy = stormy.copy(storms = advanceStormsFor(stormy, 60.0).field)
        }
        assertTrue("the test needs a live storm to lose", stormy.storms.storms.isNotEmpty())

        val fresh = startNewRun(stormy, now + 1_000)

        assertTrue("no storm may cross to the next Earth", fresh.storms.storms.isEmpty())
        assertEquals("and the storm clock restarts", 0L, fresh.storms.stepsElapsed)
        assertEquals("with no banked time", 0.0, fresh.storms.carrySeconds, 0.0)
        assertTrue("under a new seed", fresh.stormSeed != stormy.stormSeed)
        assertTrue("which is never zero", fresh.stormSeed != 0L)
        assertTrue(
            "and no penalty carries with them",
            stormEffectsOf(fresh).isNeutral,
        )
    }

    @Test
    fun `a reset through the game loop also clears the weather`() {
        var stormy = warmedPlanet()
        var guard = 0
        while (stormy.storms.storms.isEmpty() && guard++ < 400) {
            stormy = stormy.copy(storms = advanceStormsFor(stormy, 60.0).field)
        }
        val collapsed = stormy.copy(collapsed = true)
        val loop = GameLoop(kotlin.random.Random(7))

        val reset = loop.resetEarth(collapsed, now + 5_000, derivedFor(collapsed))

        assertTrue("the new Earth starts clear", reset.state.storms.storms.isEmpty())
    }

    // ---------------------------------------------------------- live ticks --

    @Test
    fun `the live tick advances the weather and prices it into production`() {
        val loop = GameLoop(kotlin.random.Random(11))
        var state = warmedPlanet()
        var derived: DerivedState = derivedFor(state)

        // Ten minutes of ordinary ticks, well under the offline threshold.
        var clock = state.lastTickAt
        repeat(2_400) {
            clock += 250L
            val result = loop.advance(state, clock, derived)
            state = result.state
            derived = computeDerived(state, clock)
        }

        assertTrue("the storm clock ran", state.storms.stepsElapsed > 0L)
        assertTrue(
            "a ten-minute window on a hot planet should have made weather",
            state.storms.stepsElapsed >= 100L,
        )

        if (state.storms.storms.isNotEmpty()) {
            assertTrue(
                "live storms must show up in the production multipliers",
                derived.effective.global < 1.0,
            )
        }
    }

    @Test
    fun `a gap past the threshold is settled through the offline path, weather included`() {
        val loop = GameLoop(kotlin.random.Random(3))
        val state = warmedPlanet()
        val later = state.lastTickAt + SIMULATION.OFFLINE_GAP_THRESHOLD_MS + 3 * 3600_000L

        val result = loop.advance(state, later, derivedFor(state))

        assertNotNull("an absence must be reported", result.events.offlineProgress)
        assertTrue(
            "and its weather reported with it",
            result.events.stormBulletins.isNotEmpty(),
        )
        assertTrue(
            "the storm clock advanced",
            result.state.storms.stepsElapsed > state.storms.stepsElapsed,
        )
    }

    @Test
    fun `major storm headlines reach the news feed and minor ones do not`() {
        val loop = GameLoop(kotlin.random.Random(5))
        val state = warmedPlanet(anomalyC = 40.0)
        val later = state.lastTickAt + SIMULATION.OFFLINE_GAP_THRESHOLD_MS + 6 * 3600_000L

        val result = loop.advance(state, later, derivedFor(state))
        val stormHeadlines = result.state.newsFeed.filter { it.milestoneId.startsWith("storm:") }

        assertTrue("a stormy absence should make headlines", stormHeadlines.isNotEmpty())
        assertTrue(
            "every storm headline carries its own text rather than a lookup id",
            stormHeadlines.all { it.bulletin != null },
        )
        assertTrue(
            "and the feed is not drowned in them",
            stormHeadlines.size <= result.events.stormBulletins.count { it.isMajor },
        )
    }
}
