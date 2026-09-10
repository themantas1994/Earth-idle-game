package com.earthgame.idle.presentation

import androidx.lifecycle.ViewModelStore
import com.earthgame.idle.data.repository.LoadResult
import com.earthgame.idle.data.repository.SaveRepository
import com.earthgame.idle.domain.engine.GameLoop
import com.earthgame.idle.domain.engine.SIMULATION
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.ResourceAmounts
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.platform.audio.GameAudio
import com.earthgame.idle.platform.audio.Sound
import com.earthgame.idle.platform.haptics.Haptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * The tick and the player run on different threads, so every state transition
 * has to be atomic. This drives both at once against real dispatchers and
 * asserts the invariants that a lost update would break.
 *
 * ## What is being guarded
 *
 * The failure mode is a read-compute-write interleaving: a caller reads a
 * snapshot, spends a few hundred microseconds computing against it, and writes
 * back a state derived from what it read. Whatever the other thread committed in
 * between is erased — a purchase un-bought and refunded, or a tick's simulated
 * progress rolled back.
 *
 * Two tests cover it from opposite directions.
 * [aTickLandingInsideAPurchaseIsSerializedNotLost] forces the exact
 * interleaving through a clock that blocks inside the purchase's critical
 * section, so it is deterministic and it does fail against an unsynchronized
 * implementation. The two stress tests then hammer the real paths against a real
 * running tick and assert the invariants that must hold whatever the
 * interleaving — they are a regression net rather than a reproduction, since the
 * natural window is a fraction of a percent of each 250 ms tick.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelConcurrencyTest {

    private val mainExecutor = Executors.newSingleThreadExecutor { Thread(it, "test-main") }
    private val mainDispatcher = mainExecutor.asCoroutineDispatcher()
    private val simulationExecutor = Executors.newSingleThreadExecutor { Thread(it, "test-sim") }
    private val simulationDispatcher = simulationExecutor.asCoroutineDispatcher()

    private lateinit var store: ViewModelStore

    /**
     * Swapped in by the deterministic test to hold a purchase's critical section
     * open. Every other test leaves it null and gets the plain wall clock.
     */
    @Volatile
    private var pausingClock: (() -> Long)? = null

    /** A wallet deep enough that a purchase never fails for lack of funds. */
    private val richState: GameState = createNewGame(START_MS).let { fresh ->
        fresh.copy(
            techOwned = fresh.techOwned + ("controlled_fire" to 1),
            resources = ResourceAmounts.of(ResourceId.entries.associateWith { gd("1e120") }),
        )
    }

    private object SilentHaptics : Haptics {
        override fun tap() = Unit
        override fun impact() = Unit
    }

    private object SilentAudio : GameAudio {
        override fun play(sound: Sound) = Unit
        override fun setSoundEnabled(enabled: Boolean) = Unit
        override fun setMusicEnabled(enabled: Boolean) = Unit
        override fun release() = Unit
    }

    private inner class FixedRepository : SaveRepository {
        override suspend fun load(now: Long): LoadResult = LoadResult.Loaded(richState.copy(lastTickAt = now))
        override suspend fun save(state: GameState) = Unit
        override suspend fun clear() = Unit
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        store = ViewModelStore()
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        mainExecutor.shutdownNow()
        simulationExecutor.shutdownNow()
    }

    private fun startedViewModel(clock: () -> Long): GameViewModel {
        val viewModel = GameViewModel(
            saveRepository = FixedRepository(),
            haptics = SilentHaptics,
            audio = SilentAudio,
            gameLoop = GameLoop(Random(3)),
            simulationDispatcher = simulationDispatcher,
            clock = { (pausingClock ?: clock)() },
        )
        store.put("game", viewModel)
        viewModel.start()
        // The load is a suspending hop through Main; wait for it to land.
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!viewModel.uiState.value.loaded && System.nanoTime() < deadline) Thread.sleep(1)
        assertTrue("the ViewModel should have loaded", viewModel.uiState.value.loaded)
        return viewModel
    }

    /**
     * Runs [action] from this thread until the tick loop has landed
     * [ticks] simulation steps, so the hammering genuinely overlaps the tick
     * rather than finishing inside one 250 ms interval.
     */
    private fun hammerAcrossTicks(viewModel: GameViewModel, ticks: Int, action: (Int) -> Unit): Int {
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        val startedAt = viewModel.uiState.value.state.lastTickAt
        var observedTicks = 0
        var lastSeen = startedAt
        var iterations = 0

        while (observedTicks < ticks && System.nanoTime() < deadline) {
            action(iterations++)
            val tickedAt = viewModel.uiState.value.state.lastTickAt
            if (tickedAt > lastSeen) {
                observedTicks++
                lastSeen = tickedAt
            }
        }

        assertTrue(
            "the tick loop must have run during the test (saw $observedTicks of $ticks)",
            observedTicks >= ticks,
        )
        return iterations
    }

    @Test
    fun `a purchase is never undone by a concurrent tick`() {
        // A clock that really advances, so the tick loop really simulates.
        val viewModel = startedViewModel(WALL_CLOCK)

        var highestOwned = 0
        var highestPurchaseCount = 0
        var successfulBuys = 0

        val iterations = hammerAcrossTicks(viewModel, OVERLAPPING_TICKS) {
            val before = viewModel.uiState.value.state.techOwned["controlled_fire"] ?: 0
            viewModel.buyTechnology("controlled_fire", BuyQuantity.ONE)
            val owned = viewModel.uiState.value.state.techOwned["controlled_fire"] ?: 0
            if (owned > before) successfulBuys++

            assertTrue(
                "owned count went backwards: $highestOwned then $owned — a tick overwrote a purchase",
                owned >= highestOwned,
            )
            highestOwned = owned

            val purchased = viewModel.uiState.value.state.lifetimeStats.totalTechnologiesPurchased
            assertTrue(
                "the purchase counter went backwards: $highestPurchaseCount then $purchased",
                purchased >= highestPurchaseCount,
            )
            highestPurchaseCount = purchased
        }

        assertTrue("the hammer loop should have run many times, ran $iterations", iterations > 100)
        assertEquals(
            "every reported purchase must still be owned at the end",
            successfulBuys,
            (viewModel.uiState.value.state.techOwned["controlled_fire"] ?: 0) - 1,
        )
        assertEquals(
            "and every one must have been counted",
            successfulBuys,
            viewModel.uiState.value.state.lifetimeStats.totalTechnologiesPurchased,
        )
    }

    @Test
    fun `settings and dismissals cannot clobber simulation progress`() {
        val viewModel = startedViewModel(WALL_CLOCK)

        var highestPlayTime = 0.0
        hammerAcrossTicks(viewModel, OVERLAPPING_TICKS) { index ->
            // The UI-only paths write the same StateFlow the tick does.
            viewModel.updateSettings { it.copy(reducedAnimations = index % 2 == 0) }
            viewModel.dismissEventToast()
            viewModel.dismissMilestoneToast()

            val playTime = viewModel.uiState.value.state.lifetimeStats.totalPlayTimeSeconds
            assertTrue(
                "simulated time went backwards: $highestPlayTime then $playTime",
                playTime >= highestPlayTime,
            )
            highestPlayTime = playTime
        }

        assertTrue("the simulation must have advanced during the hammering", highestPlayTime > 0.0)
    }

    @Test
    fun `backgrounding stops both loops and returning restarts them`() = runBlocking {
        val viewModel = startedViewModel(WALL_CLOCK)

        viewModel.onEnterBackground()
        Thread.sleep(SIMULATION.TICK_INTERVAL_MS * 4)
        val paused = viewModel.uiState.value.state.lastTickAt
        Thread.sleep(SIMULATION.TICK_INTERVAL_MS * 4)

        assertEquals("nothing may tick while backgrounded", paused, viewModel.uiState.value.state.lastTickAt)

        viewModel.onEnterForeground()
        val deadline = System.nanoTime() + 5_000_000_000L
        while (viewModel.uiState.value.state.lastTickAt <= paused && System.nanoTime() < deadline) Thread.sleep(5)

        assertTrue("returning must restart the loop", viewModel.uiState.value.state.lastTickAt > paused)
    }

    @Test
    fun aTickLandingInsideAPurchaseIsSerializedNotLost() {
        // A hand-driven clock, so "one second passed" is a variable rather than
        // a wait, and every state the ViewModel ever publishes is recorded — a
        // lost update is a *transient* regression, and a later tick would
        // otherwise quietly repair it before any assertion could see it.
        val nowMs = java.util.concurrent.atomic.AtomicLong(START_MS)
        val insidePurchase = java.util.concurrent.CountDownLatch(1)
        val releasePurchase = java.util.concurrent.CountDownLatch(1)
        val buyerThreadName = "test-buyer"

        val viewModel = startedViewModel { nowMs.get() }
        val history = java.util.Collections.synchronizedList(mutableListOf<Pair<Long, Int>>())
        // Unconfined delivers each emission synchronously on the thread that
        // published it, so nothing is conflated away.
        val recorder = CoroutineScope(Dispatchers.Unconfined).launch {
            viewModel.uiState.collect {
                history += it.state.lastTickAt to (it.state.techOwned["controlled_fire"] ?: 0)
            }
        }

        try {
            val ownedBefore = viewModel.uiState.value.state.techOwned["controlled_fire"] ?: 0

            // The clock is read from inside the purchase's critical section,
            // which makes it the one place a test can hold that section open.
            pausingClock = {
                if (Thread.currentThread().name == buyerThreadName && insidePurchase.count > 0) {
                    insidePurchase.countDown()
                    releasePurchase.await(5, java.util.concurrent.TimeUnit.SECONDS)
                }
                nowMs.get()
            }

            val buyer = Thread({ viewModel.buyTechnology("controlled_fire", BuyQuantity.ONE) }, buyerThreadName)
            buyer.start()
            assertTrue(
                "the purchase should have reached its critical section",
                insidePurchase.await(5, java.util.concurrent.TimeUnit.SECONDS),
            )

            // A full second of simulated time, so the tick that fires while the
            // purchase is paused has real work to commit.
            nowMs.addAndGet(1_000)
            Thread.sleep(SIMULATION.TICK_INTERVAL_MS * 3)

            releasePurchase.countDown()
            buyer.join(5_000)
            Thread.sleep(SIMULATION.TICK_INTERVAL_MS * 3)

            val snapshot = history.toList()
            assertTrue("nothing was recorded", snapshot.size > 1)

            // Neither side may ever go backwards, at any point in the sequence.
            var highestTick = snapshot.first().first
            var highestOwned = snapshot.first().second
            for ((tickAt, owned) in snapshot) {
                assertTrue(
                    "a purchase was rolled back by a tick: owned $highestOwned then $owned",
                    owned >= highestOwned,
                )
                assertTrue(
                    "a tick was rolled back by a purchase: lastTickAt $highestTick then $tickAt",
                    tickAt >= highestTick,
                )
                highestTick = tickAt
                highestOwned = owned
            }

            val finalState = viewModel.uiState.value.state
            assertEquals(
                "the purchase must survive the tick that landed during it",
                ownedBefore + 1,
                finalState.techOwned["controlled_fire"],
            )
            assertEquals("and the tick must survive the purchase", START_MS + 1_000, finalState.lastTickAt)
        } finally {
            recorder.cancel()
            viewModel.onEnterBackground()
        }
    }

    private companion object {
        const val START_MS = 1_700_000_000_000L

        /** How many real simulation steps the hammering has to overlap. */
        const val OVERLAPPING_TICKS = 4

        const val TIMEOUT_NANOS = 20_000_000_000L

        private val startNanos = System.nanoTime()

        /** Real elapsed time, offset onto a fixed epoch so runs are comparable. */
        val WALL_CLOCK: () -> Long = { START_MS + (System.nanoTime() - startNanos) / 1_000_000 }
    }
}
