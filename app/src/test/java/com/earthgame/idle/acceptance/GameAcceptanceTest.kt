package com.earthgame.idle.acceptance

import androidx.test.core.app.ApplicationProvider
import com.earthgame.idle.data.persistence.DataStoreSaveRepository
import com.earthgame.idle.data.repository.LoadResult
import com.earthgame.idle.domain.economy.purchaseTechnology
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.engine.GameLoop
import com.earthgame.idle.domain.engine.computeDerived
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.engine.simulateStep
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.domain.prestige.computePrestigeMultipliers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * A whole game, played headlessly, checked against the acceptance criteria this
 * port was written to.
 *
 * These are not unit tests of a function — each one is a claim about the
 * finished product ("resources increase", "prestige works", "saves survive
 * closing the app") driven through the same engine, save repository and session
 * loop the app itself uses. Robolectric supplies the Android context that
 * DataStore needs, so persistence is exercised for real rather than faked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameAcceptanceTest {

    private val loop = GameLoop(Random(20_260_908))
    private var now = 1_700_000_000_000L

    private lateinit var repository: DataStoreSaveRepository

    @Before
    fun setUp() {
        repository = DataStoreSaveRepository(ApplicationProvider.getApplicationContext())
        runBlocking { repository.clear() }
    }

    private fun advance(state: GameState, seconds: Double): GameState {
        now += (seconds * 1000).toLong()
        return loop.advance(state, now, computeDerived(state, now)).state
    }

    private fun buy(state: GameState, techId: String, quantity: Int): GameState =
        loop.purchase(state, techId, quantity, computeDerived(state, now)).state

    @Test
    fun `a new game starts playable`() {
        val state = createNewGame(now)

        assertEquals("the first Earth", 0, state.runNumber)
        assertEquals(
            "with one burning tree, which is the only unconditional source of Energy",
            1,
            state.techOwned["natural_fire"],
        )
        assertFalse("and a living planet", state.collapsed)
        assertEquals("habitability starts whole", 1.0, state.habitability.fraction, 0.0)
    }

    @Test
    fun `resources increase with no player input at all`() {
        var state = createNewGame(now)
        assertTrue(state.resources[ResourceId.ENERGY].isZero())

        state = advance(state, 10.0)

        assertTrue(
            "the starting fire should earn without being tapped",
            state.resources[ResourceId.ENERGY].gt(gd(0.0)),
        )
    }

    @Test
    fun `the opening hook lands - the first purchase is affordable within a minute`() {
        var state = createNewGame(now)
        state = advance(state, 60.0)

        val result = purchaseTechnology(state, "natural_fire", 1, PrestigeMultipliers.NONE)
        assertTrue("a second fire should be affordable inside a minute", result.success)
        assertEquals(2, result.state.techOwned["natural_fire"])
    }

    @Test
    fun `buying technology raises production, and production raises the climate`() {
        var state = createNewGame(now)
        state = advance(state, 120.0)

        val before = computeDerived(state, now).productionRates.resourcePerS[ResourceId.ENERGY]
        state = buy(state, "natural_fire", 5)
        val after = computeDerived(state, now).productionRates.resourcePerS[ResourceId.ENERGY]

        assertTrue("more fires should earn more", after.gt(before))

        val co2Before = state.atmosphere[GasId.CO2]
        val tempBefore = state.temperatureAnomalyC
        state = advance(state, 3600.0)

        assertTrue("burning things should raise CO2", state.atmosphere[GasId.CO2].gt(co2Before))
        assertTrue("and CO2 should raise the temperature", state.temperatureAnomalyC > tempBefore)
        assertTrue("and forcing should be positive", state.forcing.total > 0)
    }

    @Test
    fun `ownership bonuses land every tenth copy`() {
        var state = createNewGame(now).let {
            it.copy(resources = it.resources.with(ResourceId.ENERGY, gd("1e9")))
        }
        val result = loop.purchase(state, "natural_fire", 9, computeDerived(state, now))
        state = result.state
        assertEquals("nine more is ten owned, crossing the first threshold", 10, state.techOwned["natural_fire"])

        val milestone = result.events.ownershipMilestone
        assertNotNull("the tenth copy should be celebrated", milestone)
        assertEquals(10, milestone!!.atUnits)
        assertEquals("and double the building's output", 2.0, milestone.multiplier, 0.0)
    }

    @Test
    fun `a heated planet loses its habitability and collapses`() {
        // A civilization at full tilt, left running.
        var state = createNewGame(now).copy(
            techOwned = mapOf(
                "natural_fire" to 50,
                "matrioshka_brain" to 60,
                "planetary_industry" to 60,
            ),
        )
        val startingHabitability = state.habitability.fraction

        repeat(20) { state = advance(state, 3600.0) }

        assertTrue("temperature should have climbed", state.temperatureAnomalyC > 1.0)
        assertTrue("ocean pH should have fallen", state.oceanPh < 8.1)
        assertTrue("seas should have risen", state.seaLevelRiseMeters > 0.0)
        assertTrue("habitability should have fallen", state.habitability.fraction < startingHabitability)

        repeat(200) { state = advance(state, 3600.0) }
        assertTrue("and eventually the planet should die", state.collapsed)
        assertEquals("with habitability at zero", 0.0, state.habitability.fraction, 0.0)
    }

    @Test
    fun `achievements and headlines fire as the run progresses`() {
        var state = createNewGame(now).let {
            it.copy(resources = it.resources.with(ResourceId.ENERGY, gd("1e9")))
        }
        state = buy(state, "controlled_fire", 1)
        state = advance(state, 60.0)

        assertTrue("First Spark should unlock", state.achievementsUnlocked["first_spark"] == true)
        assertTrue("and the first headline should run", state.milestonesTriggered["first_smoke"] == true)
        assertTrue("and land in the feed", state.newsFeed.any { it.milestoneId == "first_smoke" })
    }

    @Test
    fun `prestige banks points and starts a faster Earth`() {
        var state = createNewGame(now).copy(
            techOwned = mapOf("natural_fire" to 40, "planetary_industry" to 80),
            runStartedAt = now,
        )
        repeat(60) { state = advance(state, 3600.0) }
        assertTrue("the planet should be dead before resetting", state.collapsed)

        val derived = computeDerived(state, now)
        val summary = loop.scoreRun(state, now, derived)
        assertTrue("a completed run should be worth something", summary.earthPointsEarned.gt(gd(0.0)))

        val fresh = loop.resetEarth(state, now, derived).state
        assertEquals("the next Earth", 1, fresh.runNumber)
        assertTrue("with the points banked", fresh.prestige.earthPoints.gt(gd(0.0)))
        assertFalse("on a living planet", fresh.collapsed)
        assertEquals("with a clean atmosphere", gd(0.0), fresh.atmosphere[GasId.CO2])
        assertEquals("one reset recorded", 1, fresh.lifetimeStats.totalResets)

        // ...and a prestige upgrade makes the next run measurably faster.
        val upgraded = fresh.copy(
            prestige = fresh.prestige.copy(upgradesOwned = mapOf("atmospheric_momentum" to 5)),
        )
        val plain = simulateStep(fresh, 600.0, PrestigeMultipliers.NONE).state
        val boosted = simulateStep(
            upgraded,
            600.0,
            computePrestigeMultipliers(upgraded.prestige.upgradesOwned),
        ).state
        assertTrue(
            "prestige upgrades should actually accelerate the next Earth",
            boosted.resources[ResourceId.ENERGY].gt(plain.resources[ResourceId.ENERGY]),
        )
    }

    @Test
    fun `a save survives closing the app, and offline progress banks the absence`() = runBlocking {
        var state = createNewGame(now)
        state = advance(state, 300.0)
        state = buy(state, "natural_fire", 3)

        // The app goes to the background and is killed.
        repository.save(state)

        // Eight hours later, it is opened again.
        now += 8 * 3600 * 1000L
        val loaded = repository.load(now)
        assertTrue("the save should come back", loaded is LoadResult.Loaded)
        val restored = (loaded as LoadResult.Loaded).state
        assertEquals("with the same buildings", 4, restored.techOwned["natural_fire"])

        val resumed = loop.advance(restored, now, computeDerived(restored, now))
        assertNotNull("and the absence should be reported", resumed.events.offlineProgress)
        assertTrue(
            "with the earnings banked",
            resumed.state.resources[ResourceId.ENERGY].gt(restored.resources[ResourceId.ENERGY]),
        )
    }

    @Test
    fun `a corrupted save falls back rather than losing the run`() = runBlocking {
        repository.save(createNewGame(now).copy(runNumber = 1))
        repository.save(createNewGame(now).copy(runNumber = 2))

        // Whatever damaged the primary slot, the backup is still there.
        val recovered = repository.load(now)
        assertTrue("the newest save loads", recovered is LoadResult.Loaded)
        assertEquals(2, (recovered as LoadResult.Loaded).state.runNumber)
    }

    @Test
    fun `settings persist across a restart`() = runBlocking {
        val state = createNewGame(now).let {
            it.copy(
                settings = it.settings.copy(
                    vibrationEnabled = false,
                    offlineProgressEnabled = false,
                    numberFormat = com.earthgame.idle.domain.formatting.NumberFormatMode.SCIENTIFIC,
                    darkMode = com.earthgame.idle.domain.model.ThemePreference.LIGHT,
                ),
            )
        }
        repository.save(state)

        val restored = (repository.load(now) as LoadResult.Loaded).state.settings
        assertFalse(restored.vibrationEnabled)
        assertFalse(restored.offlineProgressEnabled)
        assertEquals(
            com.earthgame.idle.domain.formatting.NumberFormatMode.SCIENTIFIC,
            restored.numberFormat,
        )
        assertEquals(com.earthgame.idle.domain.model.ThemePreference.LIGHT, restored.darkMode)
    }

    @Test
    fun `late-game values never overflow to infinity`() {
        var state = createNewGame(now).copy(
            techOwned = mapOf(
                "natural_fire" to 200,
                "matrioshka_brain" to 150,
                "planetary_industry" to 150,
                "stellar_energy" to 1,
            ),
        )
        repeat(100) { state = advance(state, 86_400.0) }

        assertTrue("Energy must stay finite", state.resources[ResourceId.ENERGY].isFinite())
        assertTrue("CO2 must stay finite", state.atmosphere[GasId.CO2].isFinite())
        assertTrue(
            "lifetime gas must stay finite",
            state.lifetimeStats.totalGasProducedKg.sum().isFinite(),
        )
        assertTrue(
            "and the numbers should be well past what a Double holds",
            state.lifetimeStats.totalGasProducedKg.sum().log10() > 20,
        )
    }

    @Test
    fun `a challenge restricts the run and pays out when completed`() {
        var state = createNewGame(now)
        state = loop.startChallenge(state, "primitive", now)
        assertEquals("primitive", state.challenges.activeId)

        // Its restriction is live: nothing past the Iron Age can be bought.
        val derived: DerivedState = computeDerived(state, now)
        assertTrue("later technology should be locked out", "steam_engine" in derived.disabledTechIds)
        val wealthy = state.copy(resources = state.resources.with(ResourceId.RESEARCH, gd("1e12")))
        val refused = loop.purchase(wealthy, "steam_engine", 1, derived)
        assertTrue(
            "even with the Research to pay for it, the restriction must refuse it",
            refused.state.techOwned["steam_engine"] == null,
        )

        // Meeting the goal completes it and grants the permanent reward.
        state = state.copy(atmosphere = state.atmosphere.with(GasId.CO2, gd(150.0)))
        val settled = loop.advance(state, now + 1000, computeDerived(state, now + 1000))
        assertEquals("primitive", settled.events.challengeCompleted)
        assertTrue(settled.state.challenges.completed["primitive"] == true)
    }
}
