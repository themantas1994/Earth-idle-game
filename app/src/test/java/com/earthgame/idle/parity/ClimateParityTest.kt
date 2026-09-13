package com.earthgame.idle.parity

import com.earthgame.idle.domain.climate.PlanetaryIndicators
import com.earthgame.idle.domain.climate.agriculturalOutputFactor
import com.earthgame.idle.domain.climate.biodiversityFactor
import com.earthgame.idle.domain.climate.computeForcing
import com.earthgame.idle.domain.climate.computeHabitability
import com.earthgame.idle.domain.climate.computeOceanPh
import com.earthgame.idle.domain.climate.computeSinkEfficiency
import com.earthgame.idle.domain.climate.computeTemperatureAnomaly
import com.earthgame.idle.domain.climate.computeWaterVaporFeedbackConcentration
import com.earthgame.idle.domain.climate.integrateGasConcentration
import com.earthgame.idle.domain.climate.integrateSeaLevelRise
import com.earthgame.idle.domain.climate.oceanAcidityFactor
import com.earthgame.idle.domain.climate.seaLevelFactor
import com.earthgame.idle.domain.climate.temperatureFactor
import com.earthgame.idle.domain.engine.GAME_SECONDS_PER_REAL_SECOND
import com.earthgame.idle.domain.engine.GAME_SECONDS_PER_YEAR
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.REAL_SECONDS_PER_GAME_YEAR
import com.earthgame.idle.domain.engine.gameSecondsFor
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.GasAmounts
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.gasOf
import com.earthgame.idle.domain.model.halfLifeFractionRemaining
import com.earthgame.idle.domain.model.naturalRemovalRateConstant
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The climate model: carbon-cycle integration, radiative forcing, temperature,
 * ocean chemistry, sea level and the five habitability factors, all against
 * values the reference engine produced.
 *
 * The integration fixture is the important one. It sweeps every gas across
 * production rates from 0 to 1e9, both engineered-removal states, three sink
 * efficiencies and six step sizes from a 250 ms tick to a full day — 3,240
 * cases — because this closed-form integration is what makes offline progress
 * and live play agree.
 */
class ClimateParityTest {

    private val fixture = Fixtures.obj("climate")

    @Test
    fun `sink efficiency weakens with warming and holds its floor`() {
        for (case in fixture.getValue("sink").jsonArray) {
            val o = case.jsonObject
            val temp = o.getDouble("tempC")
            assertDoubleNear("computeSinkEfficiency($temp)", o.getDouble("efficiency"), computeSinkEfficiency(temp))
        }
    }

    @Test
    fun `gas half-lives and decay constants match the reference`() {
        for (case in fixture.getValue("removalRates").jsonArray) {
            val o = case.jsonObject
            val gas = gasOf(GasId.entries.first { it.id == o.getString("gas") })
            assertDoubleNear("${gas.id} half-life", o.getDouble("halfLifeYears"), gas.halfLifeYears)
            assertEquals(
                "${gas.id} decaysOnSimulatedClock",
                o.getBoolean("decaysOnSimulatedClock"),
                gas.decaysOnSimulatedClock,
            )
            assertDoubleNear("${gas.id} massPerUnit", o.getDouble("massPerUnit"), gas.massPerUnit)
            assertDoubleNear("${gas.id} k", o.getDouble("k"), naturalRemovalRateConstant(gas))
        }
    }

    @Test
    fun `the half-life law and the integrator agree with the reference`() {
        for (case in fixture.getValue("halfLife").jsonArray) {
            val o = case.jsonObject
            val halfLife = o.getDouble("halfLifeYears")
            val dtGameSeconds = o.getDouble("dtGameSeconds")
            val elapsedYears = o.getDouble("elapsedYears")

            assertDoubleNear(
                "halfLifeFractionRemaining(H=$halfLife, ${elapsedYears}yr)",
                o.getDouble("fractionRemaining"),
                halfLifeFractionRemaining(halfLife, dtGameSeconds),
            )

            val k = ln(2.0) / (halfLife * REAL_SECONDS_PER_GAME_YEAR)
            assertDecimalNear(
                "decay of 1000 (H=$halfLife) over ${elapsedYears}yr",
                o.getValue("integrated").asGameDecimal(),
                integrateGasConcentration(
                    gd(1000.0),
                    GameDecimal.ZERO,
                    k,
                    1.0,
                    GameDecimal.ZERO,
                    elapsedYears * REAL_SECONDS_PER_GAME_YEAR,
                ),
            )
        }
    }

    @Test
    fun `the simulated clock matches the reference`() {
        val clock = fixture.getValue("clock").jsonObject
        assertDoubleNear("GAME_SECONDS_PER_YEAR", clock.getDouble("gameSecondsPerYear"), GAME_SECONDS_PER_YEAR)
        assertDoubleNear(
            "GAME_SECONDS_PER_REAL_SECOND",
            clock.getDouble("gameSecondsPerRealSecond"),
            GAME_SECONDS_PER_REAL_SECOND,
        )
        assertDoubleNear(
            "REAL_SECONDS_PER_GAME_YEAR",
            clock.getDouble("realSecondsPerGameYear"),
            REAL_SECONDS_PER_GAME_YEAR,
        )
        for (case in clock.getValue("gameSecondsFor").jsonArray) {
            val o = case.jsonObject
            val real = o.getDouble("realSeconds")
            assertDoubleNear("gameSecondsFor($real)", o.getDouble("gameSeconds"), gameSecondsFor(real))
        }
    }

    @Test
    fun `analytic gas integration matches the reference across every step size`() {
        var checked = 0
        for (case in fixture.getValue("integration").jsonArray) {
            val o = case.jsonObject
            val gas = gasOf(GasId.entries.first { it.id == o.getString("gas") })
            val result = integrateGasConcentration(
                gd(o.getString("start")),
                gd(o.getString("production")),
                naturalRemovalRateConstant(gas),
                o.getDouble("sinkEfficiency"),
                gd(o.getString("removal")),
                o.getDouble("dtSeconds"),
            )
            assertDecimalNear(
                "integrate(${gas.id}, start=${o.getString("start")}, prod=${o.getString("production")}, " +
                    "removal=${o.getString("removal")}, eff=${o.getDouble("sinkEfficiency")}, dt=${o.getDouble("dtSeconds")})",
                o.getValue("result").asGameDecimal(),
                result,
            )
            checked++
        }
        // 6 gases x 3 starting concentrations x 5 production rates x 2 removal
        // states x 3 sink efficiencies x 6 step sizes.
        assertEquals("the full integration sweep", 3240, checked)
    }

    @Test
    fun `radiative forcing matches the reference per gas and in total`() {
        for (case in fixture.getValue("forcing").jsonArray) {
            val o = case.jsonObject
            val co2Extra = o.getDouble("co2Extra")
            val ch4Extra = o.getDouble("ch4Extra")
            val atmosphere = GasAmounts.build { builder ->
                builder[GasId.CO2] = gd(co2Extra)
                builder[GasId.CH4] = gd(ch4Extra)
                builder[GasId.N2O] = gd(ch4Extra / 10)
                builder[GasId.O3] = gd(co2Extra / 20)
                builder[GasId.FLUORINATED] = gd(co2Extra * 3)
                builder[GasId.H2O] = gd(co2Extra)
            }
            val forcing = computeForcing(atmosphere)
            val expectedPerGas = o.getValue("perGas").jsonObject
            for ((key, value) in expectedPerGas) {
                val gasId = GasId.entries.first { it.id == key }
                assertDoubleNear("forcing[$key] at co2=$co2Extra ch4=$ch4Extra", value.jsonPrimitive.double, forcing.perGas[gasId])
            }
            assertDoubleNear("total forcing at co2=$co2Extra ch4=$ch4Extra", o.getDouble("total"), forcing.total)
        }
    }

    @Test
    fun `ozone depletion below its baseline produces negative forcing`() {
        // CFCs remove ozone, which is the one case where a gas's contribution
        // goes the other way — and where the log-based forcing has to be
        // clamped rather than producing NaN.
        val o = fixture.getValue("negativeOzone").jsonObject
        val atmosphere = GasAmounts.ZERO.with(GasId.O3, gd(o.getDouble("o3Extra")))
        val forcing = computeForcing(atmosphere)
        assertDoubleNear("depleted ozone forcing", o.getValue("perGas").jsonObject.getDouble("o3"), forcing.perGas[GasId.O3])
        assertDoubleNear("total with depleted ozone", o.getDouble("total"), forcing.total)
        assertTrue("ozone depletion should cool, not warm", forcing.perGas[GasId.O3] < 0)
    }

    @Test
    fun `temperature anomaly matches the reference including the super-linear tail`() {
        for (case in fixture.getValue("temperature").jsonArray) {
            val o = case.jsonObject
            val forcing = o.getDouble("forcing")
            assertDoubleNear("computeTemperatureAnomaly($forcing)", o.getDouble("anomaly"), computeTemperatureAnomaly(forcing))
        }
    }

    @Test
    fun `water vapor feedback matches the reference`() {
        for (case in fixture.getValue("waterVapor").jsonArray) {
            val o = case.jsonObject
            val temp = o.getDouble("prevTempC")
            assertDoubleNear("waterVapor($temp)", o.getDouble("ppm"), computeWaterVaporFeedbackConcentration(temp))
        }
    }

    @Test
    fun `every habitability factor and the composite match the reference`() {
        for (case in fixture.getValue("habitability").jsonArray) {
            val o = case.jsonObject
            val temp = o.getDouble("tempC")
            val co2Extra = o.getDouble("co2Extra")
            val sea = o.getDouble("seaLevelM")
            val label = "temp=$temp co2=$co2Extra sea=$sea"

            val ph = computeOceanPh(co2Extra)
            assertDoubleNear("$label oceanPh", o.getDouble("oceanPh"), ph)
            assertDoubleNear("$label temperatureFactor", o.getDouble("temperatureFactor"), temperatureFactor(temp))
            assertDoubleNear("$label oceanAcidityFactor", o.getDouble("oceanAcidityFactor"), oceanAcidityFactor(ph))
            assertDoubleNear("$label seaLevelFactor", o.getDouble("seaLevelFactor"), seaLevelFactor(sea))
            assertDoubleNear("$label agricultureFactor", o.getDouble("agricultureFactor"), agriculturalOutputFactor(temp))
            assertDoubleNear("$label biodiversityFactor", o.getDouble("biodiversityFactor"), biodiversityFactor(temp))

            val result = computeHabitability(
                PlanetaryIndicators(temp, ph, sea, agriculturalOutputFactor(temp), biodiversityFactor(temp)),
            )
            assertDoubleNear("$label habitability", o.getDouble("fraction"), result.fraction)

            val expectedFactors = o.getValue("factors").jsonObject
            assertDoubleNear("$label factors.temperature", expectedFactors.getDouble("temperature"), result.factors.temperature)
            assertDoubleNear("$label factors.oceanAcidity", expectedFactors.getDouble("oceanAcidity"), result.factors.oceanAcidity)
            assertDoubleNear("$label factors.seaLevel", expectedFactors.getDouble("seaLevel"), result.factors.seaLevel)
            assertDoubleNear("$label factors.agriculture", expectedFactors.getDouble("agriculture"), result.factors.agriculture)
            assertDoubleNear("$label factors.biodiversity", expectedFactors.getDouble("biodiversity"), result.factors.biodiversity)
        }
    }

    @Test
    fun `sea level integration matches the reference`() {
        for (case in fixture.getValue("seaLevel").jsonArray) {
            val o = case.jsonObject
            val result = integrateSeaLevelRise(o.getDouble("current"), o.getDouble("tempC"), o.getDouble("dtSeconds"))
            assertDoubleNear(
                "seaLevel(${o.getDouble("current")}, ${o.getDouble("tempC")}, ${o.getDouble("dtSeconds")})",
                o.getDouble("result"),
                result,
            )
        }
    }

    // --- Properties the fixture cannot express ---

    @Test
    fun `habitability is multiplicative, so one collapsed factor ends the run`() {
        val healthy = computeHabitability(PlanetaryIndicators(0.0, 8.1, 0.0, 1.0, 1.0))
        assertEquals(1.0, healthy.fraction, 1e-12)

        // Temperature past its collapse threshold zeroes the whole thing even
        // though every other factor is pristine.
        val cooked = computeHabitability(PlanetaryIndicators(60.0, 8.1, 0.0, 1.0, 1.0))
        assertEquals(0.0, cooked.fraction, 0.0)
    }

    @Test
    fun `engineered removal exceeding production never drives a gas negative`() {
        val result = integrateGasConcentration(gd(1.0), gd(0.0), 1e-9, 1.0, gd(1e6), 3600.0)
        assertTrue("concentration should clamp at zero, was $result", result.gte(com.earthgame.idle.domain.engine.GameDecimal.ZERO))
    }
}
