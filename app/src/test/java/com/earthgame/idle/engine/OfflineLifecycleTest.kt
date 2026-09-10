package com.earthgame.idle.engine

import com.earthgame.idle.domain.engine.GameLoop
import com.earthgame.idle.domain.engine.SIMULATION
import com.earthgame.idle.domain.engine.computeDerived
import com.earthgame.idle.domain.engine.computeOfflineProgress
import com.earthgame.idle.domain.engine.offlineCapSeconds
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.domain.prestige.computePrestigeMultipliers
import com.earthgame.idle.domain.save.SaveSerialization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Offline progression across the lifecycle events and clock anomalies a real
 * device produces, rather than the well-behaved absences `OfflineParityTest`
 * compares against the reference.
 *
 * Every case here is something a phone genuinely does: locking the screen,
 * having the process killed and cold-started, crossing a daylight-saving
 * boundary, being handed a save whose timestamp is missing or in the future.
 */
class OfflineLifecycleTest {

    private val now = 1_700_000_000_000L
    private val loop = GameLoop(Random(7))
    private val noPrestige = PrestigeMultipliers.NONE

    private fun playing(): GameState = createNewGame(now).let {
        it.copy(techOwned = it.techOwned + ("controlled_fire" to 12))
    }

    private fun advance(state: GameState, atMs: Long) =
        loop.advance(state, atMs, computeDerived(state, atMs))

    // ------------------------------------------------- absence granularity --

    @Test
    fun `a few seconds away is an ordinary tick, not an absence`() {
        val result = advance(playing(), now + SIMULATION.OFFLINE_GAP_THRESHOLD_MS - 1)

        assertEquals("no welcome-back modal for a screen blink", null, result.events.offlineProgress)
        assertTrue(result.state.resources[ResourceId.ENERGY].gt(playing().resources[ResourceId.ENERGY]))
    }

    @Test
    fun `crossing the gap threshold switches to the offline path`() {
        val at = now + SIMULATION.OFFLINE_GAP_THRESHOLD_MS + 1
        val result = advance(playing(), at)

        assertNotNull("past the threshold the absence is settled and reported", result.events.offlineProgress)
        assertEquals(at, result.state.lastTickAt)
    }

    @Test
    fun `an absence with offline progress switched off is settled without a modal`() {
        // The only way an absence reaches the offline path and reports nothing:
        // the gap threshold (20 s) is already above the reporting floor (5 s),
        // so every real absence is worth telling the player about.
        val opted = playing().copy(settings = playing().settings.copy(offlineProgressEnabled = false))

        val result = advance(opted, now + 8 * 3600 * 1000L)

        assertEquals(null, result.events.offlineProgress)
    }

    @Test
    fun `a real absence is reported with a summary the UI can show`() {
        val result = advance(playing(), now + 4 * 3600 * 1000L)
        val offline = result.events.offlineProgress

        assertNotNull(offline)
        assertEquals(4.0 * 3600, offline!!.awaySeconds, 1e-6)
        assertEquals(4.0 * 3600, offline.simulatedSeconds, 1e-6)
        assertFalse(offline.cappedByLimit)
        assertTrue(offline.summary.resourcesGained[ResourceId.ENERGY].gt(com.earthgame.idle.domain.engine.GameDecimal.ZERO))
    }

    // ---------------------------------------------------------- the cap ---

    @Test
    fun `a week away banks the cap and says it was capped`() {
        val result = advance(playing(), now + 7 * 24 * 3600 * 1000L)
        val offline = result.events.offlineProgress!!

        assertTrue(offline.cappedByLimit)
        assertEquals(SIMULATION.BASE_OFFLINE_CAP_SECONDS, offline.simulatedSeconds, 1e-6)
        assertEquals("the clock still catches all the way up", now + 7 * 24 * 3600 * 1000L, result.state.lastTickAt)
    }

    @Test
    fun `two absences in a row each bank their own capped share`() {
        // The cap is per absence, not per day: coming back twice banks twice.
        val first = advance(playing(), now + 24 * 3600 * 1000L).state
        val energyAfterFirst = first.resources[ResourceId.ENERGY]
        val second = advance(first, first.lastTickAt + 24 * 3600 * 1000L).state

        assertTrue(second.resources[ResourceId.ENERGY].gt(energyAfterFirst))
    }

    @Test
    fun `the cap upgrades extend the absence that can be banked`() {
        val upgraded = computePrestigeMultipliers(
            mapOf("extended_endurance" to 3, "automated_industry" to 1),
        )

        assertEquals(
            "three doublings plus one more",
            SIMULATION.BASE_OFFLINE_CAP_SECONDS * 16,
            offlineCapSeconds(upgraded),
            1e-6,
        )
        assertEquals(SIMULATION.BASE_OFFLINE_CAP_SECONDS, offlineCapSeconds(noPrestige), 1e-6)
    }

    // ------------------------------------------------------- clock anomalies --

    @Test
    fun `a clock moved backwards does not rewind the world`() {
        val playing = advance(playing(), now + 3600 * 1000L).state
        val energy = playing.resources[ResourceId.ENERGY]

        // Daylight saving, a manual change, an NTP correction.
        val rewound = advance(playing, playing.lastTickAt - 3600 * 1000L).state

        assertEquals("no resource may be un-produced", energy, rewound.resources[ResourceId.ENERGY])
        assertEquals("but the tick re-anchors so the game keeps running", playing.lastTickAt - 3600 * 1000L, rewound.lastTickAt)
    }

    @Test
    fun `a save whose timestamp is in the future settles to an ordinary tick`() {
        val fromTheFuture = playing().copy(lastTickAt = now + 10 * 24 * 3600 * 1000L)

        val settled = advance(fromTheFuture, now)

        assertEquals(null, settled.events.offlineProgress)
        assertEquals(now, settled.state.lastTickAt)
    }

    @Test
    fun `a save with no timestamp at all falls back to the load clock`() {
        val json = """
            {"saveVersion":3,"runNumber":0,"resources":{},"atmosphere":{},
             "techOwned":{"natural_fire":1},"prestige":{}}
        """.trimIndent()

        val loaded = SaveSerialization.deserialize(json, now)!!

        assertEquals(now, loaded.lastTickAt)
        assertEquals("so the first tick banks nothing", null, advance(loaded, now).events.offlineProgress)
    }

    @Test
    fun `an absurd elapsed time cannot overflow the simulation`() {
        // Year 2100 against a save written today. The cap makes this ordinary,
        // but the arithmetic still has to survive the raw elapsed value.
        val result = advance(playing(), now + 4_000_000_000_000L)

        assertTrue(result.state.temperatureAnomalyC.isFinite())
        assertTrue(result.state.resources[ResourceId.ENERGY].isFinite())
        assertTrue(result.state.habitability.fraction in 0.0..1.0)
        assertEquals(SIMULATION.BASE_OFFLINE_CAP_SECONDS, result.events.offlineProgress!!.simulatedSeconds, 1e-6)
    }

    // ------------------------------------------- process death and restart --

    @Test
    fun `a cold start after process death is the same as a backgrounded resume`() {
        val played = advance(playing(), now + 30_000).state

        // Killed by the OS: whatever was last written is all that survives.
        val revived = SaveSerialization.deserialize(SaveSerialization.serialize(played), now)!!

        val fromRevived = advance(revived, played.lastTickAt + 6 * 3600 * 1000L).state
        val fromMemory = advance(played, played.lastTickAt + 6 * 3600 * 1000L).state

        assertEquals(fromMemory.resources, fromRevived.resources)
        assertEquals(fromMemory.atmosphere, fromRevived.atmosphere)
        assertEquals(fromMemory.temperatureAnomalyC, fromRevived.temperatureAnomalyC, 1e-9)
    }

    @Test
    fun `a first launch has nothing to catch up on`() {
        val fresh = createNewGame(now)

        val result = advance(fresh, now)

        assertEquals(fresh, result.state)
        assertEquals(null, result.events.offlineProgress)
    }

    @Test
    fun `disabling offline progress advances the clock and nothing else`() {
        val opted = playing().copy(settings = playing().settings.copy(offlineProgressEnabled = false))

        val result = advance(opted, now + 8 * 3600 * 1000L)

        assertEquals(opted.resources, result.state.resources)
        assertEquals(opted.atmosphere, result.state.atmosphere)
        assertEquals("but the absence is not bankable later", now + 8 * 3600 * 1000L, result.state.lastTickAt)
    }

    @Test
    fun `an active challenge survives an absence`() {
        val inChallenge = loop.startChallenge(playing(), "ice_age", now)

        val result = advance(inChallenge, now + 2 * 3600 * 1000L)

        assertEquals("ice_age", result.state.challenges.activeId)
    }

    @Test
    fun `offline progress rolls no random events`() {
        // Events are multiplier swings the player could react to. Awarding a
        // handful for a night asleep would be noise at best, and the offline
        // path deliberately does not roll them.
        val result = advance(playing(), now + 12 * 3600 * 1000L)

        assertEquals(null, result.events.startedEventId)
        assertTrue(result.state.activeEvents.isEmpty())
    }

    @Test
    fun `expired events are pruned by the absence that outlasted them`() {
        val withEvent = playing().copy(
            activeEvents = listOf(
                com.earthgame.idle.domain.model.ActiveEvent("good_harvest-1", "good_harvest", now, now + 90_000),
            ),
        )

        val result = advance(withEvent, now + 3600 * 1000L)

        assertTrue("an event cannot outlive its window", result.state.activeEvents.isEmpty())
    }

    @Test
    fun `the offline summary reports what actually changed`() {
        val offline = computeOfflineProgress(playing(), now + 6 * 3600 * 1000L, noPrestige, true)

        assertEquals(
            offline.state.resources[ResourceId.ENERGY].toDouble(),
            (playing().resources[ResourceId.ENERGY] + offline.summary.resourcesGained[ResourceId.ENERGY]).toDouble(),
            1e-6,
        )
        assertTrue(offline.summary.temperatureAfterC >= offline.summary.temperatureBeforeC)
    }
}
