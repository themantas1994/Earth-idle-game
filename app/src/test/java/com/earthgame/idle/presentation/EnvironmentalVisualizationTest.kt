package com.earthgame.idle.presentation

import com.earthgame.idle.domain.climate.relativeHumidity
import com.earthgame.idle.domain.climate.windAt
import com.earthgame.idle.domain.climate.windStrength
import com.earthgame.idle.domain.engine.CLIMATE
import com.earthgame.idle.domain.engine.GAME_SECONDS_PER_REAL_SECOND
import com.earthgame.idle.domain.engine.advanceStormsFor
import com.earthgame.idle.domain.engine.computeDerived
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.engine.simulateStep
import com.earthgame.idle.domain.model.ActiveEvent
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.presentation.visualization.EnvironmentOverlay
import com.earthgame.idle.presentation.visualization.TEMPERATURE_VISUAL_CEILING_C
import com.earthgame.idle.presentation.visualization.VISUAL_ROTATION_SIMULATED_DAYS
import com.earthgame.idle.presentation.visualization.environmentalVisualizationOf
import com.earthgame.idle.presentation.visualization.rotationPhaseOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The one-way valve between the simulation and the globe.
 *
 * The renderer needs a GPU; this does not. Everything about *what the globe is
 * told to draw* is decided by `environmentalVisualizationOf`, which is a pure
 * function — so whether the globe is showing the right planet is a question
 * that can be answered without looking at it.
 */
class EnvironmentalVisualizationTest {

    private val now = 1_700_000_000_000L
    private val noPrestige = PrestigeMultipliers.NONE

    private fun planet(co2Ppm: Double = 0.0, waterVaporPpm: Double = 0.0): GameState {
        val base = createNewGame(now).copy(
            techOwned = mapOf("natural_fire" to 6, "coal_mining" to 14),
            lastTickAt = now,
        )
        val seeded = base.copy(
            atmosphere = base.atmosphere
                .with(GasId.CO2, gd(co2Ppm))
                .with(GasId.H2O, gd(waterVaporPpm)),
        )
        return simulateStep(seeded, 1.0, noPrestige).state.copy(lastTickAt = now)
    }

    private fun snapshotOf(state: GameState) =
        environmentalVisualizationOf(state, computeDerived(state, state.lastTickAt), state.lastTickAt)

    // ------------------------------------------------- values come from state --

    @Test
    fun `every value the globe draws comes from the simulation`() {
        val state = planet(co2Ppm = 900.0, waterVaporPpm = 3_000.0)
        val snapshot = snapshotOf(state)

        assertEquals(
            "temperature is the simulation's own anomaly",
            state.temperatureAnomalyC,
            snapshot.temperatureAnomalyC,
            0.0,
        )
        assertEquals(
            "humidity is read off the climate model's water vapour",
            relativeHumidity(state.atmosphere).toFloat(),
            snapshot.humidity,
            1e-6f,
        )
        assertEquals(
            "wind is the environment model's own figure",
            windStrength(state.temperatureAnomalyC, state.forcing.total).toFloat(),
            snapshot.windStrength,
            1e-6f,
        )
        assertEquals(
            "habitability is the simulation's",
            state.habitability.fraction.toFloat(),
            snapshot.habitability,
            1e-6f,
        )
        assertEquals(
            "and the planet's age is its own",
            state.gameAgeSeconds,
            snapshot.planetAgeSeconds,
            0.0,
        )
    }

    @Test
    fun `a pristine planet reads as pristine`() {
        val snapshot = snapshotOf(createNewGame(now))

        assertEquals("no warming to show", 0f, snapshot.temperatureScale, 0f)
        assertEquals("no greenhouse blanket", 0f, snapshot.atmosphericOpacity, 0f)
        assertEquals("full habitability", 1f, snapshot.habitability, 1e-6f)
        assertTrue("no storms", snapshot.storms.isEmpty())
        assertTrue("no events", snapshot.events.isEmpty())
        assertEquals("and no storm risk at all", 0f, snapshot.stormRisk, 0f)
    }

    @Test
    fun `warming drives the temperature and atmosphere overlays`() {
        val cool = snapshotOf(planet(co2Ppm = 40.0))
        val hot = snapshotOf(planet(co2Ppm = 4_000.0))

        assertTrue("a hotter planet reads hotter", hot.temperatureScale > cool.temperatureScale)
        assertTrue(
            "and its atmosphere reads thicker",
            hot.atmosphericOpacity > cool.atmosphericOpacity,
        )
        assertTrue("both stay in range", cool.temperatureScale in 0f..1f && hot.temperatureScale in 0f..1f)
    }

    @Test
    fun `the temperature scale saturates rather than running off the end`() {
        // The endgame reaches six-figure temperatures. The overlay stops
        // getting redder; the number beside it keeps climbing.
        val absurd = snapshotOf(planet(co2Ppm = 1e7))
        assertEquals("fully saturated", 1f, absurd.temperatureScale, 0f)
        assertTrue(
            "while the real anomaly is far past the visual ceiling",
            absurd.temperatureAnomalyC > TEMPERATURE_VISUAL_CEILING_C,
        )
    }

    @Test
    fun `humidity and cloud cover follow the simulated water vapour`() {
        val dry = snapshotOf(planet(co2Ppm = 400.0, waterVaporPpm = 0.0))
        val wet = snapshotOf(planet(co2Ppm = 400.0, waterVaporPpm = 8_000.0))

        assertTrue("more water vapour reads as more humid", wet.humidity > dry.humidity)
        assertTrue("and as more cloud", wet.cloudCoverage > dry.cloudCoverage)
        assertTrue("both bounded", wet.humidity <= 1f && wet.cloudCoverage <= 1f)
    }

    @Test
    fun `the gas breakdown is the forcing breakdown, strongest first`() {
        val state = planet(co2Ppm = 1_200.0, waterVaporPpm = 2_000.0)
        val snapshot = snapshotOf(state)

        assertTrue("some gases should be contributing", snapshot.gasContributions.isNotEmpty())
        for (index in 1 until snapshot.gasContributions.size) {
            assertTrue(
                "contributions must be ordered strongest first",
                snapshot.gasContributions[index].forcingWm2 <=
                    snapshot.gasContributions[index - 1].forcingWm2,
            )
        }
        val total = snapshot.gasContributions.sumOf { it.share.toDouble() }
        assertEquals("shares must add up", 1.0, total, 1e-6)
        for (contribution in snapshot.gasContributions) {
            assertEquals(
                "each share is that gas's own forcing",
                state.forcing.perGas[contribution.gasId],
                contribution.forcingWm2,
                1e-12,
            )
        }
    }

    // ---------------------------------------------------------------- storms --

    @Test
    fun `storms reach the globe at the position the simulation gave them`() {
        var state = planet(co2Ppm = 3_000.0, waterVaporPpm = 9_000.0)
        var guard = 0
        while (state.storms.storms.isEmpty() && guard++ < 400) {
            state = state.copy(storms = advanceStormsFor(state, 60.0).field)
        }
        assertTrue("the test needs a storm", state.storms.storms.isNotEmpty())

        val snapshot = snapshotOf(state)
        assertEquals("every storm is drawn", state.storms.storms.size, snapshot.storms.size)

        for (storm in state.storms.storms) {
            val drawn = snapshot.storms.first { it.id == storm.id }
            assertEquals("latitude", storm.latitudeDeg.toFloat(), drawn.latitudeDeg, 1e-6f)
            assertEquals("longitude", storm.longitudeDeg.toFloat(), drawn.longitudeDeg, 1e-6f)
            assertEquals("intensity", storm.intensity.toFloat(), drawn.intensity, 1e-6f)
            assertEquals("footprint", storm.radiusDeg.toFloat(), drawn.radiusDeg, 1e-6f)
            assertEquals("name", storm.displayName, drawn.displayName)
            assertEquals("severity in words", storm.severity.displayName, drawn.severityLabel)
        }
    }

    @Test
    fun `a storm carries its gameplay effect in text, not only in colour`() {
        var state = planet(co2Ppm = 3_000.0, waterVaporPpm = 9_000.0)
        var guard = 0
        // Run on until a storm has actually organised, so it has an effect worth
        // describing rather than being a minute old.
        while (guard++ < 800) {
            state = state.copy(storms = advanceStormsFor(state, 30.0).field)
            if (state.storms.storms.any { it.intensity > 0.3 }) break
        }
        val strong = state.storms.storms.firstOrNull { it.intensity > 0.3 }
        assertTrue("the test needs an organised storm", strong != null)

        val drawn = snapshotOf(state).storms.first { it.id == strong!!.id }
        assertTrue("its penalty is stated as a number", drawn.globalPenalty > 0.0)
        assertTrue(
            "and spoken aloud for a screen reader: ${drawn.accessibleSummary}",
            drawn.accessibleSummary.contains("%") && drawn.accessibleSummary.contains(drawn.displayName),
        )
    }

    // ---------------------------------------------------------------- events --

    @Test
    fun `an active event gets a stable place on the globe`() {
        val state = planet(co2Ppm = 500.0).copy(
            activeEvents = listOf(
                ActiveEvent(id = "wildfire-123", eventDefId = "wildfire", startedAt = now, endsAt = now + 30_000),
            ),
        )
        val first = snapshotOf(state)
        val second = snapshotOf(state)

        assertEquals("the event is drawn", 1, first.events.size)
        assertEquals(
            "and stays put between frames",
            first.events.first().latitudeDeg,
            second.events.first().latitudeDeg,
            0f,
        )
        assertTrue(
            "somewhere the projection can actually draw",
            abs(first.events.first().latitudeDeg) <= 58f &&
                abs(first.events.first().longitudeDeg) <= 180f,
        )
        assertTrue(
            "and it says what it is",
            first.events.first().accessibleSummary.contains("Wildfire"),
        )
    }

    @Test
    fun `two events do not land on the same spot`() {
        val state = planet(co2Ppm = 500.0).copy(
            activeEvents = listOf(
                ActiveEvent("wildfire-1", "wildfire", now, now + 30_000),
                ActiveEvent("el_nino-2", "el_nino", now, now + 30_000),
            ),
        )
        val events = snapshotOf(state).events
        assertEquals(2, events.size)
        assertNotEquals(
            "two events must be distinguishable on the globe",
            events[0].latitudeDeg to events[0].longitudeDeg,
            events[1].latitudeDeg to events[1].longitudeDeg,
        )
    }

    // ------------------------------------------------------------- rotation --

    @Test
    fun `the globe turns on the simulated clock, never the device clock`() {
        // A brand new Earth is at the start of its turn.
        assertEquals(0f, rotationPhaseOf(0.0), 0f)

        // Half a visual rotation's worth of *simulated* time is half a turn.
        val secondsPerTurn = VISUAL_ROTATION_SIMULATED_DAYS * GAME_SECONDS_PER_REAL_SECOND
        assertEquals(0.5f, rotationPhaseOf(secondsPerTurn / 2), 1e-5f)

        // And it wraps rather than growing without bound.
        assertEquals(0.25f, rotationPhaseOf(secondsPerTurn * 12.25), 1e-4f)
        assertTrue("always in range", (0..200).all { rotationPhaseOf(it * 1e7) in 0f..1f })
    }

    @Test
    fun `a paused simulation produces a still globe`() {
        // Two snapshots of the same state must be identical — including the
        // rotation. Nothing here reads a wall clock, so a game that is not
        // ticking does not drift on by itself.
        val state = planet(co2Ppm = 800.0)
        assertEquals(snapshotOf(state), snapshotOf(state))
    }

    // ---------------------------------------------------------- the overlays --

    @Test
    fun `every overlay states its scale in words`() {
        for (overlay in EnvironmentOverlay.entries) {
            assertTrue("${overlay.id} needs a low label", overlay.legendLow.isNotBlank())
            assertTrue("${overlay.id} needs a high label", overlay.legendHigh.isNotBlank())
            assertTrue("${overlay.id} needs a description", overlay.description.isNotBlank())
        }
    }

    @Test
    fun `storms stay visible under every overlay that is not the wind`() {
        for (overlay in EnvironmentOverlay.entries) {
            assertEquals(
                "${overlay.id} storm visibility",
                overlay != EnvironmentOverlay.WIND,
                overlay.showsStorms,
            )
        }
    }

    // ----------------------------------------------------------- the wind --

    @Test
    fun `the wind field is the three-cell pattern the storms are steered by`() {
        val strength = 1.0
        val tropics = windAt(12.0, strength)
        val midLatitudes = windAt(45.0, strength)

        assertTrue("the tropics blow east to west", tropics.eastward < 0)
        assertTrue("the mid-latitudes blow west to east", midLatitudes.eastward > 0)
        assertEquals(
            "and the pattern is the same in both hemispheres",
            windAt(45.0, strength).eastward,
            windAt(-45.0, strength).eastward,
            1e-12,
        )
        assertTrue("a still planet has still air", windAt(20.0, 0.0).speed == 0.0)
    }

    @Test
    fun `humidity is a reading of water vapour and nothing else`() {
        assertEquals(
            "no vapour is the base humidity",
            com.earthgame.idle.domain.engine.ENVIRONMENT.BASE_HUMIDITY,
            relativeHumidity(0.0),
            1e-12,
        )
        assertTrue("more vapour is more humid", relativeHumidity(4_000.0) > relativeHumidity(1_000.0))
        assertTrue("and it saturates rather than exceeding one", relativeHumidity(1e9) <= 1.0)

        // The number the globe shows for a given warming is the one the
        // climate model's own feedback term produces.
        val anomaly = 6.0
        assertEquals(
            relativeHumidity(anomaly * CLIMATE.h2oFeedbackPpmPerDegree),
            relativeHumidity(anomaly * CLIMATE.h2oFeedbackPpmPerDegree),
            0.0,
        )
    }
}
