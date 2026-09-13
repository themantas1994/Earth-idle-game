package com.earthgame.idle.storms

import com.earthgame.idle.domain.climate.relativeHumidity
import com.earthgame.idle.domain.climate.windStrength
import com.earthgame.idle.domain.engine.CLIMATE
import com.earthgame.idle.domain.engine.STORMS
import com.earthgame.idle.domain.storms.StormBulletinKind
import com.earthgame.idle.domain.storms.StormClimate
import com.earthgame.idle.domain.storms.StormField
import com.earthgame.idle.domain.storms.StormType
import com.earthgame.idle.domain.storms.advanceStorms
import com.earthgame.idle.domain.storms.computeStormEffects
import com.earthgame.idle.domain.storms.deriveStormSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Long-run balance: what storms actually do to a planet, at each stage of a
 * run, measured rather than asserted from the tuning constants.
 *
 * Each case simulates hours of game time across several seeds and reports the
 * storm rate, the average concurrent count, the share of the time the sky is
 * busy, and — the number that matters — the mean production penalty a player
 * would actually feel. The assertions are the design's boundaries:
 *
 * - The early game is **clear**. A player who has just lit their first fire is
 *   never taxed by weather.
 * - Pressure rises with warming, monotonically, at every stage.
 * - Even an extreme planet's mean drag stays a drag rather than a wall, so
 *   storms create pressure and never make progress impossible.
 *
 * If a tuning change breaks one of these, the failure message prints the whole
 * table, so what moved is visible without re-deriving it by hand.
 */
class StormBalanceTest {

    private data class Measurement(
        val label: String,
        val anomalyC: Double,
        val stormsPerHour: Double,
        val meanConcurrent: Double,
        val busyShare: Double,
        val meanGlobalPenalty: Double,
        val peakGlobalPenalty: Double,
        val meanIntensity: Double,
        val categories: Map<StormType, Int>,
    ) {
        override fun toString(): String = buildString {
            append(label.padEnd(18))
            append("+%-8.1f".format(anomalyC))
            append("storms/h %-7.2f".format(stormsPerHour))
            append("concurrent %-6.2f".format(meanConcurrent))
            append("busy %-6.0f%%".format(busyShare * 100))
            append("drag mean %-6.1f%%".format(meanGlobalPenalty * 100))
            append("peak %-6.1f%%".format(peakGlobalPenalty * 100))
            append("  ")
            append(categories.entries.sortedBy { it.key.ordinal }.joinToString(" ") { "${it.key.id}=${it.value}" })
        }
    }

    /** The climate the climate model would produce at [anomalyC] of warming. */
    private fun climateAt(anomalyC: Double) = StormClimate(
        temperatureAnomalyC = anomalyC,
        humidity = relativeHumidity(anomalyC.coerceAtLeast(0.0) * CLIMATE.h2oFeedbackPpmPerDegree),
        windStrength = windStrength(anomalyC, anomalyC),
    )

    /**
     * Runs [hours] of weather at [anomalyC], across several Earths, sampling
     * the sky once a minute.
     */
    private fun measure(label: String, anomalyC: Double, hours: Int = 8, earths: Int = 4): Measurement {
        val climate = climateAt(anomalyC)
        val minutes = hours * 60

        var formed = 0
        var concurrentTotal = 0
        var busySamples = 0
        var penaltyTotal = 0.0
        var peakPenalty = 0.0
        var intensityTotal = 0.0
        var intensitySamples = 0
        val categories = mutableMapOf<StormType, Int>()

        for (earth in 0 until earths) {
            val seed = deriveStormSeed(1_700_000_000_000L + earth * 97_003L, earth)
            var field = StormField.EMPTY
            repeat(minutes) {
                val result = advanceStorms(field, seed, climate, 60.0)
                field = result.field
                for (bulletin in result.bulletins) {
                    if (bulletin.kind == StormBulletinKind.FORMED) {
                        formed++
                        categories.merge(bulletin.storm.type, 1, Int::plus)
                    }
                }
                concurrentTotal += field.storms.size
                if (field.storms.isNotEmpty()) busySamples++
                for (storm in field.storms) {
                    intensityTotal += storm.intensity
                    intensitySamples++
                }
                val penalty = computeStormEffects(field.storms).globalPenalty
                penaltyTotal += penalty
                peakPenalty = maxOf(peakPenalty, penalty)
            }
        }

        val samples = (minutes * earths).toDouble()
        return Measurement(
            label = label,
            anomalyC = anomalyC,
            stormsPerHour = formed / (hours.toDouble() * earths),
            meanConcurrent = concurrentTotal / samples,
            busyShare = busySamples / samples,
            meanGlobalPenalty = penaltyTotal / samples,
            peakGlobalPenalty = peakPenalty,
            meanIntensity = if (intensitySamples == 0) 0.0 else intensityTotal / intensitySamples,
            categories = categories,
        )
    }

    @Test
    fun `storms scale with the climate across a whole run`() {
        val stages = listOf(
            measure("early game", 0.3),
            measure("first warming", 1.5),
            measure("moderate", 4.0),
            measure("high", 10.0),
            measure("severe", 25.0),
            measure("extreme", 120.0),
        )
        val table = stages.joinToString("\n")
        println("Storm balance across a run:\n$table")

        // --- the early game is clear ---
        assertEquals(
            "an unwarmed planet must never make a storm\n$table",
            0.0,
            stages[0].stormsPerHour,
            0.0,
        )
        assertEquals(
            "and must never cost the player anything\n$table",
            0.0,
            stages[0].meanGlobalPenalty,
            0.0,
        )
        assertTrue(
            "the first hint of warming is still nearly calm\n$table",
            stages[1].busyShare < 0.25,
        )

        // --- the curve only ever goes up ---
        for (index in 1 until stages.size) {
            assertTrue(
                "storm frequency must not fall as the planet warms\n$table",
                stages[index].stormsPerHour >= stages[index - 1].stormsPerHour - 1e-9,
            )
            assertTrue(
                "nor must the drag\n$table",
                stages[index].meanGlobalPenalty >= stages[index - 1].meanGlobalPenalty - 1e-9,
            )
        }

        // --- the middle of the run is where weather starts mattering ---
        assertTrue(
            "a moderately warmed planet should see occasional storms\n$table",
            stages[2].stormsPerHour in 1.0..12.0,
        )
        assertTrue(
            "a badly warmed one should be stormy most of the time\n$table",
            stages[4].busyShare > 0.6,
        )

        // --- and the end of it never becomes a wall ---
        val extreme = stages.last()
        assertTrue(
            "even an extreme planet keeps most of its production\n$table",
            extreme.meanGlobalPenalty < 0.20,
        )
        assertTrue(
            "and its worst moment is survivable\n$table",
            extreme.peakGlobalPenalty <= STORMS.MAX_GLOBAL_PENALTY + 1e-9,
        )
        assertTrue(
            "but weather is a real cost by then\n$table",
            extreme.meanGlobalPenalty > 0.04,
        )
        assertTrue(
            "and the sky never exceeds the active cap\n$table",
            extreme.meanConcurrent <= STORMS.MAX_ACTIVE.toDouble(),
        )
    }

    @Test
    fun `each category appears only once the planet can make it`() {
        val moderate = measure("moderate", 4.0, hours = 12)
        val extreme = measure("extreme", 120.0, hours = 12)

        assertTrue(
            "a moderately warm planet must not produce superstorms: ${moderate.categories}",
            StormType.SUPERSTORM !in moderate.categories,
        )
        assertTrue(
            "an extreme one must produce every category: ${extreme.categories}",
            StormType.entries.all { it in extreme.categories },
        )
        assertTrue(
            "and superstorms must stay the rarest of them: ${extreme.categories}",
            extreme.categories.getValue(StormType.SUPERSTORM) <
                extreme.categories.getValue(StormType.TROPICAL_STORM),
        )
    }

    @Test
    fun `a stormy planet is never permanently crippled`() {
        // The strongest statement the balance makes: run the worst climate in
        // the game for a long time and check the *worst minute* of it, not the
        // average. Production is slowed; it is never stopped, and it always
        // comes back.
        val climate = climateAt(500.0)
        var field = StormField.EMPTY
        val seed = deriveStormSeed(42L, 0)

        var worst = 0.0
        var clearMinutes = 0
        repeat(12 * 60) {
            field = advanceStorms(field, seed, climate, 60.0).field
            val penalty = computeStormEffects(field.storms).globalPenalty
            worst = maxOf(worst, penalty)
            if (field.storms.isEmpty()) clearMinutes++
        }

        assertTrue("the worst minute must stay under the cap, was $worst", worst <= STORMS.MAX_GLOBAL_PENALTY)
        assertTrue("and leave most production running, was $worst", worst < 0.32)
        assertTrue(
            "even the worst planet in the game gets clear spells, had $clearMinutes minutes",
            clearMinutes > 0,
        )
    }
}
