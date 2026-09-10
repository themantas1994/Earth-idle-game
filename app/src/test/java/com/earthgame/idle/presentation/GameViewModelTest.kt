package com.earthgame.idle.presentation

import androidx.lifecycle.ViewModelStore
import com.earthgame.idle.data.repository.LoadResult
import com.earthgame.idle.data.repository.SaveRepository
import com.earthgame.idle.domain.engine.GameLoop
import com.earthgame.idle.domain.engine.SIMULATION
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.save.SaveSerialization
import com.earthgame.idle.platform.audio.GameAudio
import com.earthgame.idle.platform.audio.Sound
import com.earthgame.idle.platform.haptics.Haptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * The ViewModel's own behaviour: loading, the tick cadence, autosave, the
 * lifecycle transitions that drive offline progress, and the settings that gate
 * haptics and audio.
 *
 * ## Why this drives the scheduler by hand
 *
 * The tick loop is `while (isActive) { delay(250); tick() }`, which never goes
 * idle — `advanceUntilIdle()` against it spins forever. Every test here
 * advances virtual time by a bounded amount instead, which is also closer to
 * what it is actually asserting ("after one second of ticking…").
 *
 * The wall clock is injected separately from virtual time, so "eight hours
 * passed while the app was backgrounded" is one variable rather than a wait.
 * Each test clears the ViewModel afterwards so its loops are cancelled and no
 * coroutine outlives the test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private var now = 1_700_000_000_000L

    private class FakeSaveRepository(var stored: String? = null, var backup: String? = null) : SaveRepository {
        var saveCount = 0

        override suspend fun load(now: Long): LoadResult {
            val primary = stored?.let { SaveSerialization.deserialize(it, now) }
            if (primary != null) return LoadResult.Loaded(primary)
            val fallback = backup?.let { SaveSerialization.deserialize(it, now) }
            if (fallback != null) return LoadResult.RecoveredFromBackup(fallback)
            return if (stored == null && backup == null) LoadResult.Empty else LoadResult.Corrupted
        }

        override suspend fun save(state: GameState) {
            saveCount++
            stored?.let { backup = it }
            stored = SaveSerialization.serialize(state)
        }

        override suspend fun clear() {
            stored = null
            backup = null
        }
    }

    private class RecordingHaptics : Haptics {
        var taps = 0
        var impacts = 0
        override fun tap() { taps++ }
        override fun impact() { impacts++ }
    }

    private class RecordingAudio : GameAudio {
        val played = mutableListOf<Sound>()
        var soundOn = true
        override fun play(sound: Sound) { played += sound }
        override fun setSoundEnabled(enabled: Boolean) { soundOn = enabled }
        override fun setMusicEnabled(enabled: Boolean) = Unit
        override fun release() = Unit
    }

    private lateinit var repository: FakeSaveRepository
    private lateinit var haptics: RecordingHaptics
    private lateinit var audio: RecordingAudio
    private lateinit var store: ViewModelStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSaveRepository()
        haptics = RecordingHaptics()
        audio = RecordingAudio()
        store = ViewModelStore()
    }

    @After
    fun tearDown() {
        // Cancels viewModelScope, and with it the tick and autosave loops.
        store.clear()
        Dispatchers.resetMain()
    }

    /** Builds a started ViewModel with its load settled, ready to be ticked. */
    private fun startedViewModel(): GameViewModel {
        val viewModel = GameViewModel(
            saveRepository = repository,
            haptics = haptics,
            audio = audio,
            gameLoop = GameLoop(Random(1)),
            simulationDispatcher = dispatcher,
            clock = { now },
        )
        store.put("game", viewModel)
        viewModel.start()
        settle()
        return viewModel
    }

    /** Runs everything already scheduled, without advancing virtual time. */
    private fun settle() = scope.runCurrent()

    /** Advances both the wall clock and virtual time, so the loop ticks. */
    private fun elapse(millis: Long) {
        now += millis
        scope.advanceTimeBy(millis)
        scope.runCurrent()
    }

    @Test
    fun `an empty repository starts a new game`() {
        val viewModel = startedViewModel()

        val state = viewModel.uiState.value
        assertTrue("should be loaded", state.loaded)
        assertEquals("a fresh Earth", 0, state.state.runNumber)
        assertEquals("with the starting fire", 1, state.state.techOwned["natural_fire"])
        assertNull("and nothing to report", state.offlineSummary)
        assertFalse(state.saveWasCorrupted)
    }

    @Test
    fun `the simulation advances on its own`() {
        val viewModel = startedViewModel()
        assertTrue("nothing earned yet", viewModel.uiState.value.state.resources[ResourceId.ENERGY].isZero())

        elapse(1_000)

        assertTrue(
            "a running fire should have earned something",
            viewModel.uiState.value.state.resources[ResourceId.ENERGY].gt(gd(0.0)),
        )
    }

    @Test
    fun `autosave runs on its own cadence`() {
        val viewModel = startedViewModel()
        val before = repository.saveCount

        elapse(SIMULATION.AUTOSAVE_INTERVAL_MS + 1_000)

        assertTrue("the game should have autosaved", repository.saveCount > before)
        assertNotNull("and written something", repository.stored)
    }

    @Test
    fun `backgrounding saves, and returning settles the absence`() {
        val viewModel = startedViewModel()
        val savesAfterLoad = repository.saveCount

        viewModel.onEnterBackground()
        settle()
        assertTrue("backgrounding must write the save", repository.saveCount > savesAfterLoad)

        val before = viewModel.uiState.value.state.resources[ResourceId.ENERGY]

        // Eight hours pass with the app closed. Only the wall clock moves —
        // the loop is not running, which is the point.
        now += 8 * 3600 * 1000L
        viewModel.onEnterForeground()
        settle()

        val after = viewModel.uiState.value.state.resources[ResourceId.ENERGY]
        assertTrue("the absence should have been banked", after.gt(before))
        assertNotNull("and reported", viewModel.uiState.value.offlineSummary)
    }

    @Test
    fun `an absence longer than the cap banks only the cap`() {
        val viewModel = startedViewModel()

        viewModel.onEnterBackground()
        settle()
        now += 30L * 24 * 3600 * 1000
        viewModel.onEnterForeground()
        settle()

        val summary = viewModel.uiState.value.offlineSummary
        assertNotNull(summary)
        assertTrue("should report being capped", summary!!.cappedByLimit)
        assertEquals("at twelve hours", 12.0 * 3600, summary.simulatedSeconds, 0.0)
    }

    @Test
    fun `the tick loop stops while backgrounded`() {
        val viewModel = startedViewModel()

        viewModel.onEnterBackground()
        settle()
        val whenBackgrounded = viewModel.uiState.value.state.lastTickAt

        elapse(5_000)

        assertEquals(
            "no ticks should land while backgrounded",
            whenBackgrounded,
            viewModel.uiState.value.state.lastTickAt,
        )
    }

    @Test
    fun `a resumed save picks up where it left off`() {
        repository.stored = SaveSerialization.serialize(
            createNewGame(now).let {
                it.copy(
                    runNumber = 3,
                    techOwned = mapOf("natural_fire" to 12),
                    resources = it.resources.with(ResourceId.ENERGY, gd("1e9")),
                    lastTickAt = now,
                )
            },
        )

        val viewModel = startedViewModel()

        assertEquals("the same Earth", 3, viewModel.uiState.value.state.runNumber)
        assertEquals("the same buildings", 12, viewModel.uiState.value.state.techOwned["natural_fire"])
        assertTrue(
            "and the same balance",
            viewModel.uiState.value.state.resources[ResourceId.ENERGY].gte(gd("1e9")),
        )
    }

    @Test
    fun `a corrupt primary save falls back to the backup and says so`() {
        repository.stored = "{ this is not json"
        repository.backup = SaveSerialization.serialize(
            createNewGame(now).copy(runNumber = 5, lastTickAt = now),
        )

        val viewModel = startedViewModel()

        assertEquals("the backup's Earth", 5, viewModel.uiState.value.state.runNumber)
        assertTrue("and the player is told", viewModel.uiState.value.recoveredFromBackup)
    }

    @Test
    fun `two unreadable slots start a new game and say so`() {
        repository.stored = "{ broken"
        repository.backup = "also broken"

        val viewModel = startedViewModel()

        assertTrue("should still be playable", viewModel.uiState.value.loaded)
        assertEquals("from scratch", 0, viewModel.uiState.value.state.runNumber)
        assertTrue("and the player is told", viewModel.uiState.value.saveWasCorrupted)
    }

    @Test
    fun `buying respects the vibration setting`() {
        val viewModel = startedViewModel()

        // Give the player enough Energy to buy with.
        elapse(120_000)

        viewModel.buyTechnology("natural_fire", BuyQuantity.ONE)
        settle()
        val tapsWithVibration = haptics.taps
        assertTrue("a purchase should buzz", tapsWithVibration > 0)
        assertTrue("and make a sound", audio.played.contains(Sound.PURCHASE))

        viewModel.updateSettings { it.copy(vibrationEnabled = false) }
        settle()
        viewModel.buyTechnology("natural_fire", BuyQuantity.ONE)
        settle()
        assertEquals("with vibration off, it must not", tapsWithVibration, haptics.taps)
    }

    @Test
    fun `settings changes reach the audio layer and are saved`() {
        val viewModel = startedViewModel()
        val savesBefore = repository.saveCount

        viewModel.updateSettings { it.copy(soundEnabled = false) }
        settle()

        assertFalse("audio should be muted", audio.soundOn)
        assertFalse("and the setting stored", viewModel.uiState.value.state.settings.soundEnabled)
        assertTrue("a settings change should persist immediately", repository.saveCount > savesBefore)
    }

    @Test
    fun `resetting a living Earth does nothing`() {
        val viewModel = startedViewModel()

        assertFalse(viewModel.canResetEarth())
        viewModel.resetEarth()
        settle()

        assertEquals("still the first Earth", 0, viewModel.uiState.value.state.runNumber)
        assertNull(viewModel.uiState.value.collapseSummary)
    }
}
