package com.earthgame.idle.engine

import com.earthgame.idle.domain.climate.integrateGasConcentration
import com.earthgame.idle.domain.engine.GAME_SECONDS_PER_REAL_SECOND
import com.earthgame.idle.domain.engine.GAME_SECONDS_PER_YEAR
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.REAL_SECONDS_PER_GAME_YEAR
import com.earthgame.idle.domain.engine.gameSecondsFor
import com.earthgame.idle.domain.engine.gameYearsToSeconds
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.GAS_LIST
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.gasOf
import com.earthgame.idle.domain.model.halfLifeFractionRemaining
import com.earthgame.idle.domain.model.naturalRemovalRateConstant
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The atmosphere decays by half-life.
 *
 * The headline property, and the one every other test here is a variation on:
 *
 *     1,000 with a 120-year half-life is 500 after 120 simulated years,
 *     250 after 240, and 125 after 360.
 *
 * That has to hold through the *engine's* integrator, not just through a
 * private copy of `2^(-t/H)` — so most of these assert against
 * `integrateGasConcentration`, the same function the tick calls, with the
 * elapsed span expressed in the real seconds it takes.
 *
 * See `docs/wiki/Atmospheric-Half-Life.md`.
 */
class AtmosphericHalfLifeTest {

    /** Real seconds it takes for [years] of simulated time to pass. */
    private fun realSecondsForYears(years: Double) = years * REAL_SECONDS_PER_GAME_YEAR

    /** A stock of [start] left alone for [years] of simulated time. */
    private fun decayOnly(start: Double, halfLifeYears: Double, years: Double): Double =
        integrateGasConcentration(
            extraConcentration = gd(start),
            productionPerSecond = GameDecimal.ZERO,
            removalRateConstant = ln(2.0) / (halfLifeYears * REAL_SECONDS_PER_GAME_YEAR),
            sinkEfficiency = 1.0,
            extraRemovalPerSecond = GameDecimal.ZERO,
            dtSeconds = realSecondsForYears(years),
        ).toDouble()

    // ------------------------------------------------------- the half-life --

    @Test
    fun `one half-life halves the stock`() {
        assertEquals(500.0, decayOnly(1000.0, 120.0, 120.0), 1e-6)
    }

    @Test
    fun `two half-lives quarter it`() {
        assertEquals(250.0, decayOnly(1000.0, 120.0, 240.0), 1e-6)
    }

    @Test
    fun `three half-lives leave an eighth`() {
        assertEquals(125.0, decayOnly(1000.0, 120.0, 360.0), 1e-6)
    }

    @Test
    fun `half a half-life leaves one over root two`() {
        // 707.106..., which is the continuous curve rather than a once-a-year
        // subtraction: nothing about the law waits for the half-life to elapse.
        assertEquals(1000.0 / sqrt(2.0), decayOnly(1000.0, 120.0, 60.0), 1e-6)
    }

    @Test
    fun `the closed-form law and the integrator are the same curve`() {
        for (gas in GAS_LIST) {
            if (!gas.decaysOnSimulatedClock) continue
            for (multiple in listOf(0.1, 0.5, 1.0, 2.7, 11.0)) {
                val years = gas.halfLifeYears * multiple
                val expected = 1000.0 * halfLifeFractionRemaining(gas.halfLifeYears, gameYearsToSeconds(years))
                val actual = integrateGasConcentration(
                    gd(1000.0),
                    GameDecimal.ZERO,
                    naturalRemovalRateConstant(gas),
                    1.0,
                    GameDecimal.ZERO,
                    realSecondsForYears(years),
                ).toDouble()
                assertEquals("${gas.id} after $years yr", expected, actual, expected * 1e-9 + 1e-12)
            }
        }
    }

    @Test
    fun `every gas keeps the half-life value it shipped with`() {
        // The task that introduced this decay explicitly did not change the
        // numbers, only what they mean and the clock they run on. If one of
        // these moves, that was a balance change and wants saying out loud.
        assertEquals(120.0, gasOf(GasId.CO2).halfLifeYears, 0.0)
        assertEquals(12.0, gasOf(GasId.CH4).halfLifeYears, 0.0)
        assertEquals(114.0, gasOf(GasId.N2O).halfLifeYears, 0.0)
        assertEquals(0.03, gasOf(GasId.H2O).halfLifeYears, 0.0)
        assertEquals(0.06, gasOf(GasId.O3).halfLifeYears, 0.0)
        assertEquals(3200.0, gasOf(GasId.FLUORINATED).halfLifeYears, 0.0)
    }

    @Test
    fun `decay constants are lambda equals ln two over the half-life`() {
        for (gas in GAS_LIST) {
            if (!gas.decaysOnSimulatedClock) continue
            val expected = ln(2.0) / (gas.halfLifeYears * REAL_SECONDS_PER_GAME_YEAR)
            assertEquals("${gas.id} lambda", expected, naturalRemovalRateConstant(gas), expected * 1e-12)
        }
    }

    @Test
    fun `water vapour is a feedback and keeps its own relaxation rate`() {
        // Documented exception: H2O is not a stock with a half-life, and its
        // rate is deliberately untouched by the simulated clock. Asserting it
        // here means a future change to the clock cannot silently move it.
        val h2o = gasOf(GasId.H2O)
        assertFalse("H2O must not decay on the simulated clock", h2o.decaysOnSimulatedClock)
        assertEquals(
            1.0 / (h2o.halfLifeYears * GAME_SECONDS_PER_YEAR),
            naturalRemovalRateConstant(h2o),
            1e-18,
        )
    }

    // ------------------------------------------------- production vs decay --

    @Test
    fun `with no production a gas declines`() {
        val k = naturalRemovalRateConstant(gasOf(GasId.CO2))
        val after = integrateGasConcentration(
            gd(1000.0), GameDecimal.ZERO, k, 1.0, GameDecimal.ZERO, realSecondsForYears(30.0),
        ).toDouble()
        // A quarter of CO2's half-life, so 2^(-0.25) of the stock survives.
        assertEquals(1000.0 * halfLifeFractionRemaining(120.0, gameYearsToSeconds(30.0)), after, 1e-6)
        assertTrue("expected decline, got $after", after < 1000.0)
        assertTrue("expected a partial decline, got $after", after > 800.0)
    }

    @Test
    fun `production above the decay rate accumulates`() {
        val k = naturalRemovalRateConstant(gasOf(GasId.CO2))
        val after = integrateGasConcentration(
            gd(1000.0), gd(1000.0 * k * 4), k, 1.0, GameDecimal.ZERO, realSecondsForYears(30.0),
        ).toDouble()
        assertTrue("expected accumulation, got $after", after > 1000.0)
    }

    @Test
    fun `production that exactly balances decay holds a steady state`() {
        val k = naturalRemovalRateConstant(gasOf(GasId.CO2))
        // Five centuries at the equilibrium rate should not move the stock.
        val after = integrateGasConcentration(
            gd(1000.0), gd(1000.0 * k), k, 1.0, GameDecimal.ZERO, realSecondsForYears(500.0),
        ).toDouble()
        assertEquals(1000.0, after, 1e-6)
    }

    @Test
    fun `a gas approaches production over lambda from below`() {
        val k = naturalRemovalRateConstant(gasOf(GasId.CH4))
        val production = 0.5
        val equilibrium = production / k
        val after = integrateGasConcentration(
            gd(0.0), gd(production), k, 1.0, GameDecimal.ZERO, realSecondsForYears(12.0 * 20),
        ).toDouble()
        assertEquals("20 half-lives should be within a rounding of equilibrium", equilibrium, after, equilibrium * 1e-5)
    }

    // ------------------------------------------ step size and consistency --

    @Test
    fun `one long step equals many short ones`() {
        val k = naturalRemovalRateConstant(gasOf(GasId.CO2))
        val production = gd(1e-4)
        val span = realSecondsForYears(120.0)

        val oneShot = integrateGasConcentration(gd(1000.0), production, k, 1.0, GameDecimal.ZERO, span)

        var stepped = gd(1000.0)
        val steps = 5000
        repeat(steps) {
            stepped = integrateGasConcentration(stepped, production, k, 1.0, GameDecimal.ZERO, span / steps)
        }

        val a = oneShot.toDouble()
        val b = stepped.toDouble()
        assertTrue("one step $a vs $steps steps $b", abs(a - b) <= a * 1e-9)
    }

    // --------------------------------------------------------- robustness --

    @Test
    fun `extreme spans and magnitudes stay finite and non-negative`() {
        val spans = listOf(0.0, 1e-9, 1.0, 1e6, 1e12, 1e30)
        val halfLives = listOf(0.03, 0.06, 12.0, 120.0, 3200.0)
        for (halfLife in halfLives) {
            for (years in spans) {
                val value = decayOnly(1e30, halfLife, years)
                assertFalse("NaN at H=$halfLife, $years yr", value.isNaN())
                assertTrue("infinite at H=$halfLife, $years yr", value.isFinite())
                assertTrue("negative at H=$halfLife, $years yr", value >= 0.0)
            }
        }
    }

    @Test
    fun `engineered removal beyond production never drives a gas negative`() {
        val k = naturalRemovalRateConstant(gasOf(GasId.CO2))
        val after = integrateGasConcentration(gd(10.0), gd(1.0), k, 1.0, gd(1000.0), 3600.0)
        assertTrue("must clamp at zero, got ${after.toDouble()}", after.toDouble() >= 0.0)
    }

    @Test
    fun `a zero or negative step changes nothing`() {
        val k = naturalRemovalRateConstant(gasOf(GasId.CO2))
        for (dt in listOf(0.0, -1.0, -1e9)) {
            val after = integrateGasConcentration(gd(1000.0), gd(1.0), k, 1.0, GameDecimal.ZERO, dt)
            assertEquals("dt=$dt", 1000.0, after.toDouble(), 0.0)
        }
    }

    @Test
    fun `the fraction-remaining law is bounded and degenerate-safe`() {
        assertEquals(1.0, halfLifeFractionRemaining(120.0, 0.0), 0.0)
        assertEquals(1.0, halfLifeFractionRemaining(120.0, -1.0), 0.0)
        assertEquals(0.0, halfLifeFractionRemaining(0.0, 1.0), 0.0)
        val huge = halfLifeFractionRemaining(0.03, gameYearsToSeconds(1e9))
        assertTrue("an absurd span must floor at zero, got $huge", huge >= 0.0 && huge < 1e-12)
    }

    // --------------------------------------------------------- the clock --

    @Test
    fun `the simulated clock is one simulated day per real second`() {
        assertEquals(86_400.0, GAME_SECONDS_PER_REAL_SECOND, 0.0)
        assertEquals(365.25, REAL_SECONDS_PER_GAME_YEAR, 1e-12)
        assertEquals(365.25 * 24 * 3600, GAME_SECONDS_PER_YEAR, 0.0)
        assertEquals(86_400.0, gameSecondsFor(1.0), 0.0)
        assertEquals("a negative span never rewinds the clock", 0.0, gameSecondsFor(-5.0), 0.0)
    }

    @Test
    fun `CO2 halves over the offline cap of real time`() {
        // Not a coincidence worth losing: a 120-year half-life is 12.17 real
        // hours, so a full night's banked absence roughly halves an untended
        // atmosphere. This is the number that makes the mechanic felt rather
        // than theoretical, and it falls straight out of the clock.
        val halfLifeRealSeconds = 120.0 * REAL_SECONDS_PER_GAME_YEAR
        assertEquals(43_830.0, halfLifeRealSeconds, 1.0)
    }
}
