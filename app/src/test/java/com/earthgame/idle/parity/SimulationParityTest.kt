package com.earthgame.idle.parity

import com.earthgame.idle.domain.achievements.AchievementContext
import com.earthgame.idle.domain.achievements.checkAchievements
import com.earthgame.idle.domain.challenges.computeChallengeRewardEffects
import com.earthgame.idle.domain.economy.grantTechnology
import com.earthgame.idle.domain.economy.purchaseTechnology
import com.earthgame.idle.domain.engine.computeCivLevel
import com.earthgame.idle.domain.engine.computeEffectiveMultipliers
import com.earthgame.idle.domain.engine.computeProductionRates
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.engine.simulateStep
import com.earthgame.idle.domain.milestones.checkMilestones
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.model.GAS_LIST
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.NEWS_FEED_LIMIT
import com.earthgame.idle.domain.model.NewsItem
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.model.startNewRun
import com.earthgame.idle.domain.prestige.PrestigeGainParams
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.domain.prestige.calculatePrestigeGain
import com.earthgame.idle.domain.prestige.computePrestigeMultipliers
import com.earthgame.idle.domain.technologies.TechBranch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gameplay-equivalence test the migration brief asks for.
 *
 * It replays a fixed 95-step script — start a fresh game, advance known
 * amounts of time, buy known technologies, grant known resources, prestige,
 * and play on — and compares *every* simulation value against what the
 * reference engine produced at the same point: resources, per-gas atmosphere,
 * temperature, per-gas forcing, all five habitability factors, ocean pH, sea
 * level, run and lifetime statistics, the multiplier bundle, production rates,
 * civilization level, the news feed and the achievement set.
 *
 * The script runs a planet all the way from a single campfire to collapse at
 * +600,000 °C, so it exercises the extreme-magnitude paths as well as the
 * opening minutes.
 */
class SimulationParityTest {

    private val noPrestige = PrestigeMultipliers.NONE

    @Test
    fun `a full scripted playthrough matches the reference at every step`() {
        val fixture = Fixtures.obj("simulation")
        val startMs = fixture.getValue("startMs").jsonPrimitive.long
        val script = fixture.getValue("script").jsonArray
        val steps = fixture.getValue("steps").jsonArray

        var state = createNewGame(startMs)
        var stepIndex = 0
        assertSnapshot(steps[stepIndex++].jsonObject, state, noPrestige)

        for (action in script) {
            val a = action.jsonObject
            when (a.getString("kind")) {
                "advance" -> {
                    state = simulateStep(state, a.getDouble("seconds"), noPrestige).state
                    state = applyMilestonesAndAchievements(state)
                }

                "buy" -> {
                    val result = purchaseTechnology(state, a.getString("techId"), a.getInt("quantity"), noPrestige)
                    state = if (result.success) {
                        result.state.copy(
                            lifetimeStats = result.state.lifetimeStats.copy(
                                totalTechnologiesPurchased = result.state.lifetimeStats.totalTechnologiesPurchased +
                                    result.purchasedQuantity,
                            ),
                        )
                    } else {
                        result.state
                    }
                    val expected = steps[stepIndex].jsonObject
                    assertEquals(
                        "${expected.getString("label")} purchase success",
                        expected.getBoolean("purchaseSucceeded"),
                        result.success,
                    )
                    assertEquals(
                        "${expected.getString("label")} purchased quantity",
                        expected.getInt("purchasedQuantity"),
                        result.purchasedQuantity,
                    )
                }

                "grantTech" -> state = grantTechnology(state, a.getString("techId"))

                "grantCheat" -> {
                    val resource = ResourceId.entries.first { it.id == a.getString("resource") }
                    state = state.copy(
                        resources = state.resources.with(resource, state.resources[resource] + gd(a.getString("amount"))),
                    )
                }

                else -> error("unknown scripted action ${a.getString("kind")}")
            }
            assertSnapshot(steps[stepIndex++].jsonObject, state, noPrestige)
        }

        // --- Prestige: score the run, reset, apply starting bonuses ---
        val runDurationSeconds = 259_200.0
        val earned = calculatePrestigeGain(
            PrestigeGainParams(
                totalGasProducedKg = state.runStats.totalGasProducedKg,
                peakForcingWm2 = state.runStats.peakForcingWm2,
                civLevel = computeCivLevel(state.techOwned),
                runDurationSeconds = runDurationSeconds,
            ),
        )

        var fresh = startNewRun(state, startMs + (runDurationSeconds * 1000).toLong())
        fresh = fresh.copy(
            prestige = fresh.prestige.copy(
                earthPoints = fresh.prestige.earthPoints + earned,
                upgradesOwned = mapOf("eternal_flame" to 3, "head_start" to 1, "atmospheric_momentum" to 2),
            ),
            lifetimeStats = fresh.lifetimeStats.copy(
                totalResets = fresh.lifetimeStats.totalResets + 1,
                fastestResetSeconds = runDurationSeconds,
                longestRunSeconds = runDurationSeconds,
                totalEarthPointsEarned = fresh.lifetimeStats.totalEarthPointsEarned + earned,
            ),
        )

        val postPrestige = computePrestigeMultipliers(fresh.prestige.upgradesOwned)
        for (techId in postPrestige.startingTechIds) fresh = grantTechnology(fresh, techId)
        for ((techId, extra) in postPrestige.startingGenerators) {
            fresh = fresh.copy(techOwned = fresh.techOwned + (techId to (fresh.techOwned[techId] ?: 0) + extra))
        }
        for ((resource, amount) in postPrestige.startingResources) {
            fresh = fresh.copy(resources = fresh.resources.with(resource, fresh.resources[resource] + gd(amount)))
        }

        val afterPrestige = steps[stepIndex++].jsonObject
        assertDecimalNear(
            "Earth Points earned for the run",
            afterPrestige.getValue("earthPointsEarned").asGameDecimal(),
            earned,
            Fixtures.TOLERANCE_LOOSE,
        )
        assertDecimalNear(
            "Earth Points balance after the reset",
            afterPrestige.getValue("earthPointsBalance").asGameDecimal(),
            fresh.prestige.earthPoints,
            Fixtures.TOLERANCE_LOOSE,
        )
        assertEquals("run number after the reset", afterPrestige.getInt("runNumber"), fresh.runNumber)
        assertSnapshot(afterPrestige, fresh, postPrestige)

        // ...and the new run advances with the prestige bonuses applied.
        var bonusState = fresh
        for (seconds in listOf(1.0, 600.0, 3600.0)) {
            bonusState = simulateStep(bonusState, seconds, postPrestige).state
            assertSnapshot(steps[stepIndex++].jsonObject, bonusState, postPrestige)
        }

        assertEquals("every recorded step was replayed", steps.size, stepIndex)
    }

    /**
     * Milestone headlines and achievements are raised outside `simulateStep` —
     * the pure simulation stays free of bookkeeping — so the replay mirrors what
     * the store does after each step, exactly as the fixture generator did.
     */
    private fun applyMilestonesAndAchievements(input: GameState): GameState {
        var state = input
        val fired = checkMilestones(state)
        if (fired.isNotEmpty()) {
            state = state.copy(
                milestonesTriggered = state.milestonesTriggered + fired.associate { it.id to true },
                newsFeed = (
                    fired.reversed().map { NewsItem(it.id, 0L, 0.0) } + state.newsFeed
                    ).take(NEWS_FEED_LIMIT),
            )
        }
        val newAchievements = checkAchievements(
            AchievementContext(state, computeCivLevel(state.techOwned)),
            state.achievementsUnlocked,
        )
        if (newAchievements.isNotEmpty()) {
            state = state.copy(
                achievementsUnlocked = state.achievementsUnlocked + newAchievements.associateWith { true },
            )
        }
        return state
    }

    private fun assertSnapshot(expected: JsonObject, state: GameState, prestige: PrestigeMultipliers) {
        val label = expected.getString("label")

        assertEquals(
            "$label techOwned",
            expected.getValue("techOwned").jsonObject.mapValues { it.value.jsonPrimitive.int },
            state.techOwned,
        )

        for ((key, value) in expected.getValue("resources").jsonObject) {
            val resource = ResourceId.entries.first { it.id == key }
            assertDecimalNear("$label resources[$key]", value.asGameDecimal(), state.resources[resource], Fixtures.TOLERANCE_LOOSE)
        }
        for ((key, value) in expected.getValue("atmosphere").jsonObject) {
            val gas = GasId.entries.first { it.id == key }
            assertDecimalNear("$label atmosphere[$key]", value.asGameDecimal(), state.atmosphere[gas], Fixtures.TOLERANCE_LOOSE)
        }

        assertDoubleNear("$label temperature", expected.getDouble("temperatureAnomalyC"), state.temperatureAnomalyC, Fixtures.TOLERANCE_LOOSE)
        assertDoubleNear("$label previous temperature", expected.getDouble("previousTemperatureAnomalyC"), state.previousTemperatureAnomalyC, Fixtures.TOLERANCE_LOOSE)
        assertDoubleNear("$label forcing total", expected.getDouble("forcingTotal"), state.forcing.total, Fixtures.TOLERANCE_LOOSE)
        for ((key, value) in expected.getValue("forcingPerGas").jsonObject) {
            val gas = GasId.entries.first { it.id == key }
            assertDoubleNear("$label forcing[$key]", value.jsonPrimitive.double, state.forcing.perGas[gas], Fixtures.TOLERANCE_LOOSE)
        }

        assertDoubleNear("$label habitability", expected.getDouble("habitability"), state.habitability.fraction, Fixtures.TOLERANCE_LOOSE)
        val factors = expected.getValue("habitabilityFactors").jsonObject
        assertDoubleNear("$label factors.temperature", factors.getDouble("temperature"), state.habitability.factors.temperature, Fixtures.TOLERANCE_LOOSE)
        assertDoubleNear("$label factors.oceanAcidity", factors.getDouble("oceanAcidity"), state.habitability.factors.oceanAcidity, Fixtures.TOLERANCE_LOOSE)
        assertDoubleNear("$label factors.seaLevel", factors.getDouble("seaLevel"), state.habitability.factors.seaLevel, Fixtures.TOLERANCE_LOOSE)
        assertDoubleNear("$label factors.agriculture", factors.getDouble("agriculture"), state.habitability.factors.agriculture, Fixtures.TOLERANCE_LOOSE)
        assertDoubleNear("$label factors.biodiversity", factors.getDouble("biodiversity"), state.habitability.factors.biodiversity, Fixtures.TOLERANCE_LOOSE)

        assertDoubleNear("$label oceanPh", expected.getDouble("oceanPh"), state.oceanPh, Fixtures.TOLERANCE_LOOSE)
        assertDoubleNear("$label seaLevel", expected.getDouble("seaLevelRiseMeters"), state.seaLevelRiseMeters, Fixtures.TOLERANCE_LOOSE)
        assertEquals("$label collapsed", expected.getBoolean("collapsed"), state.collapsed)
        assertEquals("$label civLevel", expected.getInt("civLevel"), computeCivLevel(state.techOwned))

        val runStats = expected.getValue("runStats").jsonObject
        for ((key, value) in runStats.getValue("totalGasProducedKg").jsonObject) {
            val gas = GasId.entries.first { it.id == key }
            assertDecimalNear("$label runStats.gas[$key]", value.asGameDecimal(), state.runStats.totalGasProducedKg[gas], Fixtures.TOLERANCE_LOOSE)
        }
        assertDoubleNear("$label runStats.peakForcing", runStats.getDouble("peakForcingWm2"), state.runStats.peakForcingWm2, Fixtures.TOLERANCE_LOOSE)
        assertDoubleNear("$label runStats.peakTemperature", runStats.getDouble("peakTemperatureC"), state.runStats.peakTemperatureC, Fixtures.TOLERANCE_LOOSE)
        assertDoubleNear("$label runStats.peakCo2", runStats.getDouble("peakCo2Ppm"), state.runStats.peakCo2Ppm, Fixtures.TOLERANCE_LOOSE)
        assertDecimalNear(
            "$label runStats.peakGasRate",
            runStats.getValue("peakGasProductionRateKgPerS").asGameDecimal(),
            state.runStats.peakGasProductionRateKgPerS,
            Fixtures.TOLERANCE_LOOSE,
        )

        val lifetime = expected.getValue("lifetimeStats").jsonObject
        assertDoubleNear("$label lifetime.playTime", lifetime.getDouble("totalPlayTimeSeconds"), state.lifetimeStats.totalPlayTimeSeconds, Fixtures.TOLERANCE_LOOSE)
        assertDoubleNear("$label lifetime.highestTemperature", lifetime.getDouble("highestTemperatureC"), state.lifetimeStats.highestTemperatureC, Fixtures.TOLERANCE_LOOSE)
        assertDoubleNear("$label lifetime.highestCo2", lifetime.getDouble("highestCo2Ppm"), state.lifetimeStats.highestCo2Ppm, Fixtures.TOLERANCE_LOOSE)
        assertEquals("$label lifetime.resets", lifetime.getInt("totalResets"), state.lifetimeStats.totalResets)
        assertEquals("$label lifetime.purchases", lifetime.getInt("totalTechnologiesPurchased"), state.lifetimeStats.totalTechnologiesPurchased)

        val multipliers = expected.getValue("multipliers").jsonObject
        val effective = computeEffectiveMultipliers(state.techOwned, prestige)
        assertDoubleNear("$label multipliers.global", multipliers.getDouble("global"), effective.global, Fixtures.TOLERANCE_LOOSE)
        assertDoubleNear("$label multipliers.research", multipliers.getDouble("research"), effective.research, Fixtures.TOLERANCE_LOOSE)
        assertDoubleNear("$label multipliers.allGas", multipliers.getDouble("allGas"), effective.allGas, Fixtures.TOLERANCE_LOOSE)
        for ((key, value) in multipliers.getValue("perGas").jsonObject) {
            val gas = GasId.entries.first { it.id == key }
            assertDoubleNear("$label multipliers.perGas[$key]", value.jsonPrimitive.double, effective.perGas.getValue(gas), Fixtures.TOLERANCE_LOOSE)
        }
        for ((key, value) in multipliers.getValue("perBranch").jsonObject) {
            val branch = TechBranch.entries.first { it.id == key }
            assertDoubleNear("$label multipliers.perBranch[$key]", value.jsonPrimitive.double, effective.perBranch.getValue(branch), Fixtures.TOLERANCE_LOOSE)
        }

        val rates = expected.getValue("productionRates").jsonObject
        val actualRates = computeProductionRates(state.techOwned, effective)
        for ((key, value) in rates.getValue("gasGrossKgPerS").jsonObject) {
            val gas = GasId.entries.first { it.id == key }
            assertDecimalNear("$label rates.gross[$key]", value.asGameDecimal(), actualRates.gasGrossKgPerS[gas], Fixtures.TOLERANCE_LOOSE)
        }
        for ((key, value) in rates.getValue("gasRemovalKgPerS").jsonObject) {
            val gas = GasId.entries.first { it.id == key }
            assertDecimalNear("$label rates.removal[$key]", value.asGameDecimal(), actualRates.gasRemovalKgPerS[gas], Fixtures.TOLERANCE_LOOSE)
        }
        for ((key, value) in rates.getValue("resourcePerS").jsonObject) {
            val resource = ResourceId.entries.first { it.id == key }
            assertDecimalNear("$label rates.resource[$key]", value.asGameDecimal(), actualRates.resourcePerS[resource], Fixtures.TOLERANCE_LOOSE)
        }

        assertEquals(
            "$label milestones triggered",
            expected.getValue("milestonesTriggered").jsonArray.map { it.jsonPrimitive.content }.sorted(),
            state.milestonesTriggered.keys.sorted(),
        )
        assertEquals(
            "$label achievements unlocked",
            expected.getValue("achievementsUnlocked").jsonArray.map { it.jsonPrimitive.content }.sorted(),
            state.achievementsUnlocked.keys.sorted(),
        )
        assertEquals(
            "$label news feed",
            expected.getValue("newsFeed").jsonArray.map { it.jsonPrimitive.content },
            state.newsFeed.map { it.milestoneId },
        )
    }

    @Test
    fun `step size independence - one hour in one step equals 14400 quarter-second ticks`() {
        // This is the property the closed-form gas integration exists to
        // provide, and the reason offline catch-up is a single calculation
        // rather than a stepped loop.
        val fixture = Fixtures.obj("stepIndependence")
        val base = createNewGame(0).copy(
            techOwned = mapOf("natural_fire" to 5, "controlled_fire" to 12, "coal_mining" to 30),
        )

        val oneShot = simulateStep(base, 3600.0, noPrestige).state
        var stepped = base
        repeat(14_400) { stepped = simulateStep(stepped, 0.25, noPrestige).state }

        val expectedOneShot = fixture.getValue("oneShot").jsonObject
        val expectedStepped = fixture.getValue("stepped").jsonObject

        for ((key, value) in expectedOneShot.getValue("atmosphere").jsonObject) {
            val gas = GasId.entries.first { it.id == key }
            assertDecimalNear("one-shot atmosphere[$key]", value.asGameDecimal(), oneShot.atmosphere[gas])
        }
        for ((key, value) in expectedStepped.getValue("atmosphere").jsonObject) {
            val gas = GasId.entries.first { it.id == key }
            assertDecimalNear("stepped atmosphere[$key]", value.asGameDecimal(), stepped.atmosphere[gas], Fixtures.TOLERANCE_LOOSE)
        }

        // ...and the two agree with each other, which is the actual invariant.
        //
        // Water vapour is excluded, and its exclusion is the point rather than
        // an oversight: it is not an accumulating stock but a feedback driven by
        // the *previous* step's temperature, so it necessarily lags by one step
        // and a single 3600 s call sees a temperature of zero throughout where
        // 14,400 quarter-second calls do not. The reference engine behaves the
        // same way and its own step-size test likewise checks only the
        // accumulating quantities. Nothing the player spends or is scored on
        // depends on it: H2O emits nothing, is never banked, and contributes
        // forcing only through a temperature the other gases already set.
        for (gas in GAS_LIST.filter { it.directlyEmitted }) {
            assertDecimalNear(
                "step-size independence for ${gas.id}",
                oneShot.atmosphere[gas.id],
                stepped.atmosphere[gas.id],
                Fixtures.TOLERANCE_LOOSE,
            )
        }
        assertTrue(
            "water vapour is expected to lag by one step, and does",
            oneShot.atmosphere[GasId.H2O].isZero() && stepped.atmosphere[GasId.H2O].gt(GameDecimal.ZERO),
        )
        for (resource in ResourceId.entries) {
            assertDecimalNear(
                "step-size independence for $resource",
                oneShot.resources[resource],
                stepped.resources[resource],
                Fixtures.TOLERANCE_LOOSE,
            )
        }
        // Temperature inherits the water-vapour lag above, so it agrees closely
        // rather than exactly. The residual is bounded and tiny — well under a
        // tenth of a percent — and it shrinks as the run warms, because H2O's
        // ~11-day lifetime means it re-equilibrates almost immediately.
        assertDoubleNear(
            "step-size independence for temperature",
            oneShot.temperatureAnomalyC,
            stepped.temperatureAnomalyC,
            1e-3,
        )
    }

    @Test
    fun `the simulation is deterministic - the same inputs give the same outputs`() {
        val base = createNewGame(0).copy(techOwned = mapOf("natural_fire" to 3, "coal_mining" to 12))
        val first = simulateStep(base, 1234.5, noPrestige).state
        val second = simulateStep(base, 1234.5, noPrestige).state
        assertEquals("identical inputs must give identical state", first, second)
    }

    @Test
    fun `zero and negative time steps change nothing`() {
        val base = createNewGame(0).copy(techOwned = mapOf("natural_fire" to 3))
        assertEquals(base, simulateStep(base, 0.0, noPrestige).state)
        assertEquals(base, simulateStep(base, -5.0, noPrestige).state)
    }

    @Test
    fun `a completed run's numbers stay finite`() {
        // Late-game totals exceed what a Double can hold; the point of
        // GameDecimal is that nothing here becomes Infinity.
        var state = createNewGame(0).copy(
            techOwned = mapOf(
                "natural_fire" to 200, "matrioshka_brain" to 200,
                "planetary_industry" to 200, "stellar_energy" to 1,
            ),
        )
        repeat(50) { state = simulateStep(state, 86_400.0, noPrestige).state }
        assertTrue("CO2 must stay finite", state.atmosphere[GasId.CO2].isFinite())
        assertTrue("energy must stay finite", state.resources[ResourceId.ENERGY].isFinite())
        assertTrue("lifetime gas must stay finite", state.lifetimeStats.totalGasProducedKg.sum().isFinite())
        assertTrue(
            "fifty days of a finished economy should bank a great deal of Energy, was " +
                state.resources[ResourceId.ENERGY].toExponential(3),
            state.resources[ResourceId.ENERGY].log10() > 20,
        )

        // And past the point where a Double would have given up: seed a balance
        // beyond 1.8e308 and confirm the simulation still advances it correctly
        // rather than saturating at Infinity.
        var beyondDouble = state.copy(
            resources = state.resources.with(ResourceId.ENERGY, gd("1e400")),
            atmosphere = state.atmosphere.with(GasId.CO2, gd("1e350")),
        )
        beyondDouble = simulateStep(beyondDouble, 86_400.0, noPrestige).state
        assertTrue("Energy past Double range must stay finite", beyondDouble.resources[ResourceId.ENERGY].isFinite())
        assertTrue("...and keep its magnitude", beyondDouble.resources[ResourceId.ENERGY].log10() > 399)
        assertTrue("CO2 past Double range must stay finite", beyondDouble.atmosphere[GasId.CO2].isFinite())
    }
}
