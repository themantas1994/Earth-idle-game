package com.earthgame.idle.storms

import com.earthgame.idle.domain.climate.relativeHumidity
import com.earthgame.idle.domain.climate.windStrength
import com.earthgame.idle.domain.engine.CLIMATE
import com.earthgame.idle.domain.engine.STORMS
import com.earthgame.idle.domain.storms.Storm
import com.earthgame.idle.domain.storms.StormBulletinKind
import com.earthgame.idle.domain.storms.StormClimate
import com.earthgame.idle.domain.storms.StormField
import com.earthgame.idle.domain.storms.StormSeverity
import com.earthgame.idle.domain.storms.StormType
import com.earthgame.idle.domain.storms.advanceStorms
import com.earthgame.idle.domain.storms.deriveStormSeed
import com.earthgame.idle.domain.storms.eligibleStormTypes
import com.earthgame.idle.domain.storms.intensityProfile
import com.earthgame.idle.domain.storms.stormFormationChancePerStep
import com.earthgame.idle.domain.storms.stormFormationPressure
import com.earthgame.idle.domain.storms.wrapLongitude
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The storm simulation, tested without a GPU, a device or a renderer.
 *
 * That is the point of the architecture as much as it is the point of these
 * tests: storms are domain code, the renderer only draws what they say, and so
 * every behaviour the player can see — when storms form, where they go, how
 * strong they get, when they die — is answerable here on the JVM.
 */
class StormSimulationTest {

    private val seed = deriveStormSeed(1_700_000_000_000L, 0)

    /** The climate a planet at [anomalyC] of warming actually has, per the climate model. */
    private fun climateAt(anomalyC: Double): StormClimate {
        // Water vapour is the climate model's own feedback term, so the
        // humidity here is the humidity the real simulation would produce.
        val waterVaporPpm = anomalyC.coerceAtLeast(0.0) * CLIMATE.h2oFeedbackPpmPerDegree
        return StormClimate(
            temperatureAnomalyC = anomalyC,
            humidity = relativeHumidity(waterVaporPpm),
            windStrength = windStrength(anomalyC, anomalyC),
        )
    }

    private fun run(
        climate: StormClimate,
        seconds: Double,
        field: StormField = StormField.EMPTY,
    ) = advanceStorms(field, seed, climate, seconds)

    // --------------------------------------------------------- formation --

    @Test
    fun `a cool planet cannot make a storm however long it runs`() {
        val calm = climateAt(0.2)
        assertEquals("no formation pressure below the floor", 0.0, stormFormationPressure(calm), 0.0)

        // A full day of simulated time, which is far longer than any run's
        // opening, and still nothing.
        val result = run(calm, 24 * 3600.0)
        assertTrue("a cool planet must stay clear", result.field.storms.isEmpty())
        assertTrue("and produce no bulletins", result.bulletins.isEmpty())
    }

    @Test
    fun `formation pressure rises with warming and saturates`() {
        val samples = listOf(1.0, 3.0, 8.0, 20.0, 60.0, 400.0).map { stormFormationPressure(climateAt(it)) }
        for (index in 1 until samples.size) {
            assertTrue(
                "pressure must not fall as the planet warms: $samples",
                samples[index] >= samples[index - 1] - 1e-12,
            )
        }
        assertTrue("a mildly warmed planet is nearly calm, was ${samples.first()}", samples.first() < 0.1)
        assertTrue("an extreme planet saturates, was ${samples.last()}", samples.last() > 0.98)
        assertTrue("and never exceeds one", samples.all { it <= 1.0 })
    }

    @Test
    fun `each category needs the conditions its definition states`() {
        assertEquals(
            "a barely-warmed planet makes tropical storms and nothing else",
            listOf(StormType.TROPICAL_STORM),
            eligibleStormTypes(climateAt(1.0)),
        )
        assertTrue(
            "hurricanes need real warming",
            StormType.HURRICANE in eligibleStormTypes(climateAt(3.0)),
        )
        assertTrue(
            "superstorms are an extreme-planet phenomenon",
            StormType.SUPERSTORM !in eligibleStormTypes(climateAt(6.0)),
        )
        assertTrue(
            "...and appear once it is extreme",
            StormType.SUPERSTORM in eligibleStormTypes(climateAt(12.0)),
        )
    }

    @Test
    fun `a warm planet forms storms, and never more than the cap`() {
        val hot = climateAt(30.0)
        assertTrue("a hot planet should be forming storms", stormFormationChancePerStep(hot) > 0.0)

        var field = StormField.EMPTY
        var formed = 0
        var peakConcurrent = 0
        var minutesWithWeather = 0

        // Sampled across the window rather than at one arbitrary instant: a
        // snapshot can legitimately catch the sky between storms even on a
        // planet that is making them constantly.
        repeat(240) {
            val result = advanceStorms(field, seed, hot, 60.0)
            field = result.field
            formed += result.bulletins.count { it.kind == StormBulletinKind.FORMED }
            peakConcurrent = maxOf(peakConcurrent, field.storms.size)
            if (field.storms.isNotEmpty()) minutesWithWeather++
        }

        assertTrue("a hot planet should be making storms, made $formed", formed > 20)
        assertTrue(
            "and should be stormy most of the time, was $minutesWithWeather of 240 minutes",
            minutesWithWeather > 120,
        )
        assertTrue(
            "the active cap must hold, peaked at $peakConcurrent",
            peakConcurrent <= STORMS.MAX_ACTIVE,
        )
    }

    // ------------------------------------------------------ determinism --

    @Test
    fun `the same seed and climate produce exactly the same weather`() {
        val climate = climateAt(14.0)
        val first = run(climate, 4 * 3600.0)
        val second = run(climate, 4 * 3600.0)

        assertEquals("identical inputs must give identical storms", first.field, second.field)
        assertEquals(
            "and identical bulletins",
            first.bulletins.map { it.kind to it.storm.id },
            second.bulletins.map { it.kind to it.storm.id },
        )
    }

    @Test
    fun `a different Earth gets different weather`() {
        val climate = climateAt(14.0)
        val earthOne = advanceStorms(StormField.EMPTY, deriveStormSeed(1L, 0), climate, 6 * 3600.0)
        val earthTwo = advanceStorms(StormField.EMPTY, deriveStormSeed(1L, 1), climate, 6 * 3600.0)

        assertNotEquals(
            "two Earths must not share a forecast",
            earthOne.field.storms.map { it.formedAtStep },
            earthTwo.field.storms.map { it.formedAtStep },
        )
    }

    @Test
    fun `one long step and many short steps produce the same timeline`() {
        // The property the fixed-step carry accumulator exists to provide, and
        // the reason a twelve-hour absence and a live session agree.
        val climate = climateAt(18.0)
        val oneShot = run(climate, 3600.0).field

        var stepped = StormField.EMPTY
        repeat(14_400) { stepped = advanceStorms(stepped, seed, climate, 0.25).field }

        assertEquals("step index", oneShot.stepsElapsed, stepped.stepsElapsed)
        assertEquals("storms", oneShot.storms, stepped.storms)
        assertEquals("carry", oneShot.carrySeconds, stepped.carrySeconds, 1e-9)
    }

    @Test
    fun `time shorter than a step is banked rather than lost`() {
        val climate = climateAt(18.0)
        val partial = advanceStorms(StormField.EMPTY, seed, climate, STORMS.STEP_SECONDS / 2)
        assertEquals("nothing stepped yet", 0L, partial.field.stepsElapsed)
        assertEquals("but the time is kept", STORMS.STEP_SECONDS / 2, partial.field.carrySeconds, 1e-9)

        val completed = advanceStorms(partial.field, seed, climate, STORMS.STEP_SECONDS / 2)
        assertEquals("the two halves make a step", 1L, completed.field.stepsElapsed)
    }

    @Test
    fun `zero and negative time change nothing`() {
        val field = run(climateAt(20.0), 1800.0).field
        assertEquals(field, advanceStorms(field, seed, climateAt(20.0), 0.0).field)
        assertEquals(field, advanceStorms(field, seed, climateAt(20.0), -30.0).field)
    }

    // ---------------------------------------------- intensity and movement --

    @Test
    fun `intensity rises to a plateau and falls back to nothing`() {
        assertTrue("a storm starts weak", intensityProfile(0.0) < 0.2)
        assertEquals("and holds at its peak mid-life", 1.0, intensityProfile(0.45), 1e-9)
        assertEquals("and is gone at the end", 0.0, intensityProfile(1.0), 1e-9)

        // Monotone up, then monotone down — no flicker anywhere in between.
        var previous = intensityProfile(0.0)
        for (step in 1..28) {
            val value = intensityProfile(step / 100.0)
            assertTrue("the spin-up must not dip", value >= previous - 1e-12)
            previous = value
        }
        previous = intensityProfile(0.62)
        for (step in 63..100) {
            val value = intensityProfile(step / 100.0)
            assertTrue("the wind-down must not rise", value <= previous + 1e-12)
            previous = value
        }
    }

    @Test
    fun `a storm ages, moves, peaks and dissipates`() {
        val climate = climateAt(25.0)
        var field = StormField.EMPTY
        // Run until something forms.
        var guard = 0
        while (field.storms.isEmpty() && guard++ < 200) {
            field = advanceStorms(field, seed, climate, 60.0).field
        }
        assertTrue("the test needs a storm to follow", field.storms.isNotEmpty())

        val tracked = field.storms.first()
        var current: Storm? = tracked
        var maxIntensity = tracked.intensity
        var moved = false
        var dissipated = false

        repeat(400) {
            val result = advanceStorms(field, seed, climate, STORMS.STEP_SECONDS)
            field = result.field
            val next = field.storms.firstOrNull { it.id == tracked.id }
            if (next != null) {
                maxIntensity = maxOf(maxIntensity, next.intensity)
                val previous = current!!
                if (previous.latitudeDeg != next.latitudeDeg || previous.longitudeDeg != next.longitudeDeg) {
                    moved = true
                }
                current = next
            } else if (current != null) {
                dissipated = true
                current = null
            }
        }

        assertTrue("a storm must move", moved)
        assertTrue("a storm must reach a real intensity, peaked at $maxIntensity", maxIntensity > 0.15)
        assertTrue("a storm must eventually dissipate", dissipated)
    }

    @Test
    fun `a storm stays on the globe`() {
        val climate = climateAt(45.0)
        var field = StormField.EMPTY
        repeat(300) { field = advanceStorms(field, seed, climate, 30.0).field }
        assertTrue("something should be running", field.storms.isNotEmpty())

        for (storm in field.storms) {
            assertTrue(
                "latitude in range: ${storm.latitudeDeg}",
                abs(storm.latitudeDeg) < STORMS.DISSIPATION_LATITUDE,
            )
            assertTrue(
                "longitude wrapped: ${storm.longitudeDeg}",
                storm.longitudeDeg >= -180.0 && storm.longitudeDeg <= 180.0,
            )
            assertTrue("intensity in range: ${storm.intensity}", storm.intensity in 0.0..1.0)
            assertTrue("life remaining is positive", storm.remainingSeconds >= 0.0)
        }
    }

    @Test
    fun `longitude wraps rather than running off the map`() {
        assertEquals(-175.0, wrapLongitude(185.0), 1e-9)
        assertEquals(175.0, wrapLongitude(-185.0), 1e-9)
        assertEquals(0.0, wrapLongitude(360.0), 1e-9)
    }

    @Test
    fun `no two live storms share a name`() {
        var field = StormField.EMPTY
        val climate = climateAt(60.0)
        repeat(200) {
            field = advanceStorms(field, seed, climate, 60.0).field
            val names = field.storms.map { it.name }
            assertEquals("names must be unique among live storms: $names", names.size, names.toSet().size)
        }
    }

    // --------------------------------------------------------- bulletins --

    @Test
    fun `every storm is announced when it forms and again when it dies`() {
        val climate = climateAt(30.0)
        val result = run(climate, 6 * 3600.0)

        val formed = result.bulletins.filter { it.kind == StormBulletinKind.FORMED }.map { it.storm.id }
        val dissipated = result.bulletins.filter { it.kind == StormBulletinKind.DISSIPATED }.map { it.storm.id }

        assertTrue("six hours of a hot planet should produce storms", formed.isNotEmpty())
        assertTrue(
            "every storm that is gone was announced as gone",
            dissipated.toSet().all { it in formed.toSet() },
        )
        assertEquals(
            "the ones still running are exactly the ones not yet dissipated",
            (formed.toSet() - dissipated.toSet()).size,
            result.field.storms.size,
        )
    }

    @Test
    fun `only serious developments are flagged as major`() {
        val minor = Storm(
            id = "s1",
            type = StormType.TROPICAL_STORM,
            name = "Wren",
            latitudeDeg = 12.0,
            longitudeDeg = 40.0,
            intensity = 0.2,
            targetIntensity = 0.3,
            peakIntensity = 0.2,
            ageSeconds = 20.0,
            lifetimeSeconds = 300.0,
            headingDeg = 300.0,
            speedDegPerSecond = 0.02,
            formedAtStep = 3,
        )
        assertTrue(
            "a tropical storm forming is not worth a banner",
            !com.earthgame.idle.domain.storms.StormBulletin(StormBulletinKind.FORMED, minor).isMajor,
        )
        assertTrue(
            "a hurricane forming is",
            com.earthgame.idle.domain.storms.StormBulletin(
                StormBulletinKind.FORMED,
                minor.copy(type = StormType.HURRICANE),
            ).isMajor,
        )
    }

    @Test
    fun `severity labels track intensity and are spelled out`() {
        assertEquals(StormSeverity.FORMING, StormSeverity.of(0.1))
        assertEquals(StormSeverity.MODERATE, StormSeverity.of(0.35))
        assertEquals(StormSeverity.SEVERE, StormSeverity.of(0.6))
        assertEquals(StormSeverity.EXTREME, StormSeverity.of(0.9))
        // Colour is never the only carrier of a gameplay effect.
        assertTrue(StormSeverity.entries.all { it.displayName.isNotBlank() })
    }

    @Test
    fun `a storm describes itself in words for a screen reader`() {
        val storm = Storm(
            id = "s2",
            type = StormType.HURRICANE,
            name = "Iris",
            latitudeDeg = 18.4,
            longitudeDeg = -62.9,
            intensity = 0.62,
            targetIntensity = 0.7,
            peakIntensity = 0.62,
            ageSeconds = 100.0,
            lifetimeSeconds = 500.0,
            headingDeg = 315.0,
            speedDegPerSecond = 0.02,
            formedAtStep = 9,
        )
        val spoken = storm.accessibleSummary
        assertTrue(spoken, spoken.contains("Hurricane Iris"))
        assertTrue(spoken, spoken.contains("severe"))
        assertTrue(spoken, spoken.contains("18°N"))
        assertTrue(spoken, spoken.contains("62°W"))
    }

    @Test
    fun `a seed is never zero, which would collapse the mixing`() {
        assertNotEquals(0L, deriveStormSeed(0L, 0))
        assertNotEquals(0L, deriveStormSeed(1_700_000_000_000L, 12))
        assertNotEquals(
            "consecutive Earths must not share a seed",
            deriveStormSeed(1_700_000_000_000L, 3),
            deriveStormSeed(1_700_000_000_000L, 4),
        )
    }
}
