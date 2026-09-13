package com.earthgame.idle.engine

import com.earthgame.idle.domain.engine.GAME_SECONDS_PER_REAL_SECOND
import com.earthgame.idle.domain.engine.computeOfflineProgress
import com.earthgame.idle.domain.engine.gameSecondsFor
import com.earthgame.idle.domain.engine.gameSecondsToYears
import com.earthgame.idle.domain.engine.gameYearsToSeconds
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.engine.simulateStep
import com.earthgame.idle.domain.formatting.NumberFormatMode
import com.earthgame.idle.domain.formatting.formatGameAge
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.model.startNewRun
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.domain.save.SaveSerialization
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Earth's simulated age: what advances it, what does not, and what
 * survives a reset.
 *
 * The distinction under test throughout is between *simulated* time — the
 * planet's own age, which the half-lives decay against — and wall-clock time.
 * They are related by one constant and nothing else, and the age must only ever
 * come from time the world was actually simulated for.
 */
class GameAgeTest {

    private val now = 1_700_000_000_000L
    private val noPrestige = PrestigeMultipliers.NONE

    private fun playing() = createNewGame(now).copy(
        techOwned = mapOf("natural_fire" to 6, "controlled_fire" to 14),
    )

    // ------------------------------------------------------------ advancing --

    @Test
    fun `a new Earth starts at age zero`() {
        assertEquals(0.0, createNewGame(now).gameAgeSeconds, 0.0)
        assertEquals(0.0, createNewGame(now).lifetimeStats.totalSimulatedSeconds, 0.0)
    }

    @Test
    fun `one real second of simulation ages the Earth by one simulated day`() {
        val after = simulateStep(createNewGame(now), 1.0, noPrestige).state
        assertEquals(GAME_SECONDS_PER_REAL_SECOND, after.gameAgeSeconds, 0.0)
    }

    @Test
    fun `age accumulates across ticks and matches one long step`() {
        val base = playing()
        val oneShot = simulateStep(base, 3600.0, noPrestige).state

        var stepped = base
        repeat(14_400) { stepped = simulateStep(stepped, 0.25, noPrestige).state }

        assertEquals(gameSecondsFor(3600.0), oneShot.gameAgeSeconds, 1e-6)
        assertEquals(oneShot.gameAgeSeconds, stepped.gameAgeSeconds, 1e-3)
    }

    @Test
    fun `a zero or negative step does not age the planet`() {
        val base = playing()
        assertEquals(0.0, simulateStep(base, 0.0, noPrestige).state.gameAgeSeconds, 0.0)
        assertEquals(0.0, simulateStep(base, -600.0, noPrestige).state.gameAgeSeconds, 0.0)
    }

    @Test
    fun `the lifetime total tracks the Earth's age while a run lasts`() {
        val after = simulateStep(playing(), 900.0, noPrestige).state
        assertEquals(after.gameAgeSeconds, after.lifetimeStats.totalSimulatedSeconds, 0.0)
    }

    // -------------------------------------------------------------- offline --

    @Test
    fun `offline progression ages the Earth`() {
        val base = playing().copy(lastTickAt = now)
        val result = computeOfflineProgress(base, now + 3_600_000, noPrestige, true)
        assertEquals(gameSecondsFor(3600.0), result.state.gameAgeSeconds, 1e-6)
    }

    @Test
    fun `an absence past the cap ages the Earth by the cap, not the absence`() {
        val base = playing().copy(lastTickAt = now)
        val week = 7 * 24 * 3600
        val result = computeOfflineProgress(base, now + week * 1000L, noPrestige, true)

        assertTrue("the absence must have been capped", result.cappedByLimit)
        assertEquals(gameSecondsFor(result.simulatedSeconds), result.state.gameAgeSeconds, 1e-6)
        assertTrue(
            "the planet must not age by the whole absence",
            result.state.gameAgeSeconds < gameSecondsFor(week.toDouble()),
        )
    }

    @Test
    fun `time the app merely existed for does not age the Earth`() {
        // Offline progress off: the player was away, the world was not
        // simulated, so the planet did not age — and its atmosphere did not
        // decay either.
        val base = playing().copy(lastTickAt = now)
        val result = computeOfflineProgress(base, now + 86_400_000L, noPrestige, false)
        assertEquals(0.0, result.state.gameAgeSeconds, 0.0)
    }

    @Test
    fun `offline and live play age the Earth and its atmosphere identically`() {
        val base = playing().copy(lastTickAt = now)
        val spanSeconds = 4 * 3600

        var live = base
        repeat(spanSeconds * 4) { live = simulateStep(live, 0.25, noPrestige).state }

        val offline = computeOfflineProgress(base, now + spanSeconds * 1000L, noPrestige, true).state

        assertEquals(live.gameAgeSeconds, offline.gameAgeSeconds, 1e-3)
        for (gas in GasId.entries) {
            // H2O is the documented exception: a feedback one step behind, not
            // an accumulating stock. Everything the player builds up must match.
            if (gas == GasId.H2O) continue
            val a = live.atmosphere[gas].toDouble()
            val b = offline.atmosphere[gas].toDouble()
            assertTrue("$gas: live $a vs offline $b", abs(a - b) <= maxOf(a, b) * 1e-9 + 1e-12)
        }
    }

    // ------------------------------------------------------------- prestige --

    @Test
    fun `a reset returns the Earth to age zero but keeps the lifetime total`() {
        val aged = simulateStep(playing(), 7200.0, noPrestige).state
        assertTrue("the run must have aged first", aged.gameAgeSeconds > 0.0)

        val fresh = startNewRun(aged, now + 7_200_000)
        assertEquals("a new Earth starts at age zero", 0.0, fresh.gameAgeSeconds, 0.0)
        assertEquals(
            "the lifetime simulated total survives the reset",
            aged.lifetimeStats.totalSimulatedSeconds,
            fresh.lifetimeStats.totalSimulatedSeconds,
            0.0,
        )
        assertTrue("and it is not zero", fresh.lifetimeStats.totalSimulatedSeconds > 0.0)
    }

    @Test
    fun `the lifetime total keeps growing across Earths`() {
        val first = simulateStep(playing(), 3600.0, noPrestige).state
        val second = simulateStep(startNewRun(first, now), 3600.0, noPrestige).state
        assertEquals(
            gameSecondsFor(7200.0),
            second.lifetimeStats.totalSimulatedSeconds,
            1e-6,
        )
        assertEquals(gameSecondsFor(3600.0), second.gameAgeSeconds, 1e-6)
    }

    // ---------------------------------------------------------- persistence --

    @Test
    fun `the age round-trips through a save`() {
        val aged = simulateStep(playing(), 12_345.0, noPrestige).state
        val json = SaveSerialization.serialize(aged)
        val loaded = SaveSerialization.deserialize(json, now)
        assertNotNull(loaded)
        assertEquals(aged.gameAgeSeconds, loaded!!.gameAgeSeconds, 1e-9)
        assertEquals(
            aged.lifetimeStats.totalSimulatedSeconds,
            loaded.lifetimeStats.totalSimulatedSeconds,
            1e-9,
        )
    }

    @Test
    fun `a very large age survives the round trip and the year conversion`() {
        // A million simulated years is roughly a year of continuous play. It
        // has to stay a number rather than becoming an infinity or losing the
        // conversion.
        val huge = gameYearsToSeconds(1e6)
        val state = playing().copy(gameAgeSeconds = huge)
        val loaded = SaveSerialization.deserialize(SaveSerialization.serialize(state), now)!!
        assertEquals(huge, loaded.gameAgeSeconds, 1.0)
        assertEquals(1e6, gameSecondsToYears(loaded.gameAgeSeconds), 1e-3)
    }

    @Test
    fun `a damaged or negative age decodes as zero rather than rewinding time`() {
        val json = SaveSerialization.serialize(playing())
            .replace("\"gameAgeSeconds\":0.0", "\"gameAgeSeconds\":-500.0")
        val loaded = SaveSerialization.deserialize(json, now)!!
        assertEquals(0.0, loaded.gameAgeSeconds, 0.0)
    }

    // ------------------------------------------------------------ formatting --

    @Test
    fun `an age reads as years, months and days`() {
        assertEquals("0d", formatGameAge(0.0))
        assertEquals("1d", formatGameAge(86_400.0))
        // A month is a twelfth of a Julian year, 30.4375 days.
        assertEquals("30d", formatGameAge(30 * 86_400.0))
        assertEquals("1m 0d", formatGameAge(gameYearsToSeconds(1.0 / 12)))
        assertEquals("1y 0m 0d", formatGameAge(gameYearsToSeconds(1.0)))
        assertEquals("12y 4m 0d", formatGameAge(gameYearsToSeconds(12.0) + gameYearsToSeconds(4.0 / 12)))
    }

    @Test
    fun `an enormous age falls back to the player's number format`() {
        val out = formatGameAge(gameYearsToSeconds(1e9), NumberFormatMode.SCIENTIFIC)
        assertTrue("expected a scientific year count, got $out", out.endsWith("y") && out.contains("e"))
    }

    @Test
    fun `a broken age still formats`() {
        assertEquals("0d", formatGameAge(Double.NaN))
        assertEquals("∞", formatGameAge(Double.POSITIVE_INFINITY))
        assertEquals("0d", formatGameAge(-1.0))
    }

    @Test
    fun `the age is simulated time, not play time`() {
        // The two are different numbers on the same state, and the Statistics
        // screen shows both. If they ever coincide the clock has been lost.
        val after = simulateStep(playing().copy(resources = playing().resources.with(
            com.earthgame.idle.domain.model.ResourceId.ENERGY, gd(0.0),
        )), 600.0, noPrestige).state
        assertEquals(600.0, after.lifetimeStats.totalPlayTimeSeconds, 1e-9)
        assertEquals(600.0 * GAME_SECONDS_PER_REAL_SECOND, after.gameAgeSeconds, 1e-6)
    }
}
