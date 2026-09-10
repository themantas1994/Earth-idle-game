package com.earthgame.idle.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.earthgame.idle.data.repository.LoadResult
import com.earthgame.idle.data.repository.SaveRepository
import com.earthgame.idle.domain.economy.BUY_MAX_QUANTITY
import com.earthgame.idle.domain.engine.CollapseSummary
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.engine.GameLoop
import com.earthgame.idle.domain.engine.OfflineProgressResult
import com.earthgame.idle.domain.engine.OwnershipMilestone
import com.earthgame.idle.domain.engine.SIMULATION
import com.earthgame.idle.domain.engine.StepEvents
import com.earthgame.idle.domain.engine.computeDerived
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.Settings
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.platform.audio.GameAudio
import com.earthgame.idle.platform.audio.Sound
import com.earthgame.idle.platform.haptics.Haptics
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the UI renders, in one immutable snapshot. */
data class GameUiState(
    val state: GameState,
    val derived: DerivedState,
    val loaded: Boolean = false,
    /** Shown once on return from a long absence. */
    val offlineSummary: OfflineProgressResult? = null,
    /** Shown once when an Earth ends and its points are banked. */
    val collapseSummary: CollapseSummary? = null,
    val newAchievements: List<String> = emptyList(),
    val activeEventToast: String? = null,
    val activeMilestoneToast: String? = null,
    val activeOwnershipToast: OwnershipMilestone? = null,
    /** Set when the primary save was unreadable and the backup was used. */
    val recoveredFromBackup: Boolean = false,
    /** Set when both save slots were unreadable and a new game was started. */
    val saveWasCorrupted: Boolean = false,
)

/** How many units a Buy button asks for. */
enum class BuyQuantity(val label: String, val amount: Int) {
    ONE("×1", 1),
    TEN("×10", 10),
    HUNDRED("×100", 100),
    MAX("Max", BUY_MAX_QUANTITY),
}

/**
 * Owns the running game: the simulation loop, the autosave cadence, and the
 * single [StateFlow] every screen reads.
 *
 * ## Threading
 *
 * The tick runs on [simulationDispatcher] (Default), never the main thread. A
 * late-game step walks the whole technology tree and does a few thousand
 * arbitrary-precision operations; at four ticks a second on the main thread
 * that is a dropped frame every 250 ms. Saving runs on IO inside the
 * repository. The main thread only ever reads the finished [StateFlow].
 *
 * That means two threads produce state: the tick, and the player tapping Buy.
 * Every transition is therefore a *read-compute-write* under [stateLock] rather
 * than a bare [MutableStateFlow.update]: the compute step reads the current
 * state, and a purchase settled against a snapshot the tick has since replaced
 * would silently undo itself (the tech un-bought, the resources refunded). The
 * critical section is a single tick's worth of work — tens of microseconds —
 * so the main thread is never held up for a visible frame.
 *
 * ## Lifecycle
 *
 * The loops are tied to [viewModelScope], so they cannot outlive the screen.
 * They are additionally stopped while the app is backgrounded
 * ([onEnterBackground]), because there is nothing to render, Android will not
 * reliably keep running them anyway, and a paused game has nothing new to
 * autosave. Coming back calls [onEnterForeground], which settles the whole
 * absence in one closed-form step. That is the entire offline-progress
 * mechanism: no background service, no scheduled work, no wake locks.
 */
class GameViewModel(
    private val saveRepository: SaveRepository,
    private val haptics: Haptics,
    private val audio: GameAudio,
    private val gameLoop: GameLoop = GameLoop(),
    private val simulationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val initialState = createNewGame(clock())

    private val _uiState = MutableStateFlow(
        GameUiState(state = initialState, derived = computeDerived(initialState, initialState.lastTickAt)),
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /** Serializes the tick against player actions — see "Threading" above. */
    private val stateLock = Any()

    private var tickJob: Job? = null
    private var autosaveJob: Job? = null
    private var started = false

    /** Loads the save, settles any absence since it was written, and starts the loop. */
    fun start() {
        if (started) return
        started = true

        viewModelScope.launch {
            val now = clock()
            val result = saveRepository.load(now)

            val loadedState = when (result) {
                is LoadResult.Loaded -> result.state
                is LoadResult.RecoveredFromBackup -> result.state
                LoadResult.Empty, LoadResult.Corrupted -> null
            }

            if (loadedState == null) {
                val fresh = createNewGame(now)
                synchronized(stateLock) {
                    _uiState.value = GameUiState(
                        state = fresh,
                        derived = computeDerived(fresh, now),
                        loaded = true,
                        saveWasCorrupted = result is LoadResult.Corrupted,
                    )
                }
            } else {
                // Settle the absence before the first frame, so the player sees
                // the world as it is now rather than as it was when they left.
                val derived = computeDerived(loadedState, now)
                val stepped = withContext(simulationDispatcher) {
                    gameLoop.advance(loadedState, now, derived)
                }
                synchronized(stateLock) {
                    _uiState.value = GameUiState(
                        state = stepped.state,
                        derived = computeDerived(stepped.state, now),
                        loaded = true,
                        offlineSummary = stepped.events.offlineProgress,
                        recoveredFromBackup = result is LoadResult.RecoveredFromBackup,
                    ).applyEvents(stepped.events)
                }
                audio.setSoundEnabled(stepped.state.settings.soundEnabled)
                audio.setMusicEnabled(stepped.state.settings.musicEnabled)
            }

            startLoops()
        }
    }

    private fun startLoops() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch(simulationDispatcher) {
            while (isActive) {
                delay(SIMULATION.TICK_INTERVAL_MS)
                tick()
            }
        }

        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            while (isActive) {
                delay(SIMULATION.AUTOSAVE_INTERVAL_MS)
                saveNow()
            }
        }
    }

    private fun stopLoops() {
        tickJob?.cancel()
        tickJob = null
        autosaveJob?.cancel()
        autosaveJob = null
    }

    private fun tick() {
        val now = clock()

        val events = mutate { current ->
            if (!current.loaded) return@mutate null
            val result = gameLoop.advance(current.state, now, current.derived)
            if (result.state === current.state) return@mutate null

            Transition(
                next = current.copy(
                    state = result.state,
                    derived = computeDerived(result.state, now),
                    offlineSummary = result.events.offlineProgress ?: current.offlineSummary,
                ).applyEvents(result.events),
                carried = result.events to current.state.settings.vibrationEnabled,
            )
        } ?: return

        val (stepEvents, vibrationEnabled) = events
        if (stepEvents.justCollapsed) {
            if (vibrationEnabled) haptics.impact()
            audio.play(Sound.COLLAPSE)
        }
    }

    /** Folds a step's outcomes into the toast/notification slots the UI reads. */
    private fun GameUiState.applyEvents(events: StepEvents): GameUiState = copy(
        newAchievements = newAchievements + events.newAchievements,
        // Several milestones can trip on one (especially offline) step. The most
        // recent is the one worth interrupting the player for; the rest are
        // waiting in the news feed.
        activeMilestoneToast = events.firedMilestones.lastOrNull() ?: activeMilestoneToast,
        activeEventToast = events.startedEventId ?: activeEventToast,
        activeOwnershipToast = events.ownershipMilestone ?: activeOwnershipToast,
    )

    // ------------------------------------------------- atomic state changes --

    /** The next UI state, plus whatever the caller needs to act on outside the lock. */
    private class Transition<T>(val next: GameUiState, val carried: T)

    /**
     * Runs one read-compute-write against [_uiState] under [stateLock] and
     * returns whatever the transition carried, or null when [transform] decided
     * nothing should change.
     *
     * Side effects — haptics, audio, saving — deliberately happen *outside* the
     * lock, on what the transition carried out.
     */
    private fun <T> mutate(transform: (GameUiState) -> Transition<T>?): T? =
        synchronized(stateLock) {
            val current = _uiState.value
            val transition = transform(current) ?: return@synchronized null
            _uiState.value = transition.next
            transition.carried
        }

    /** [mutate] for the common case: a pure [GameState] transition with no carried value. */
    private fun commit(transform: (GameState) -> GameState): Boolean =
        mutate { current ->
            val next = transform(current.state)
            if (next === current.state) {
                null
            } else {
                Transition(current.copy(state = next, derived = computeDerived(next, clock())), Unit)
            }
        } != null

    /** [mutate] for a change that only touches the UI slots, never the game state. */
    private fun updateUi(transform: (GameUiState) -> GameUiState) {
        mutate { current -> Transition(transform(current), Unit) }
    }

    // ------------------------------------------------------------- actions --

    fun buyTechnology(techId: String, quantity: BuyQuantity) {
        val outcome = mutate { current ->
            val result = gameLoop.purchase(current.state, techId, quantity.amount, current.derived)
            if (result.state === current.state) return@mutate null

            Transition(
                next = current.copy(
                    state = result.state,
                    derived = computeDerived(result.state, clock()),
                ).applyEvents(result.events),
                carried = current.state.settings.vibrationEnabled to (result.events.ownershipMilestone != null),
            )
        } ?: return

        val (vibrationEnabled, crossedThreshold) = outcome
        if (vibrationEnabled) haptics.tap()
        audio.play(if (crossedThreshold) Sound.MILESTONE else Sound.PURCHASE)
    }

    fun buyPrestigeUpgrade(upgradeId: String) {
        val vibrationEnabled = mutate { current ->
            val next = gameLoop.buyPrestigeUpgrade(current.state, upgradeId)
            if (next === current.state) return@mutate null

            Transition(
                next = current.copy(state = next, derived = computeDerived(next, clock())),
                carried = current.state.settings.vibrationEnabled,
            )
        } ?: return

        if (vibrationEnabled) haptics.tap()
        audio.play(Sound.PURCHASE)
    }

    /** Whether the Reset button should do anything — the planet has to be dead first. */
    fun canResetEarth(): Boolean = _uiState.value.state.collapsed

    fun resetEarth() {
        val now = clock()

        val vibrationEnabled = mutate { current ->
            if (!current.state.collapsed) return@mutate null
            val summary = gameLoop.scoreRun(current.state, now, current.derived)
            val result = gameLoop.resetEarth(current.state, now, current.derived)
            if (result.state === current.state) return@mutate null

            Transition(
                next = current.copy(
                    state = result.state,
                    derived = computeDerived(result.state, now),
                    collapseSummary = summary,
                ).applyEvents(result.events),
                carried = current.state.settings.vibrationEnabled,
            )
        } ?: return

        if (vibrationEnabled) haptics.impact()
        saveNow()
    }

    fun startChallenge(challengeId: String) {
        val now = clock()
        if (commit { gameLoop.startChallenge(it, challengeId, now) }) saveNow()
    }

    fun abandonChallenge() {
        commit(gameLoop::abandonChallenge)
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val settings = mutate { current ->
            val settings = transform(current.state.settings)
            val next = gameLoop.updateSettings(current.state, settings)
            Transition(
                next = current.copy(state = next, derived = computeDerived(next, clock())),
                carried = settings,
            )
        } ?: return

        audio.setSoundEnabled(settings.soundEnabled)
        audio.setMusicEnabled(settings.musicEnabled)
        saveNow()
    }

    fun advanceTutorial() {
        commit(gameLoop::advanceTutorial)
    }

    fun skipTutorial() {
        if (commit(gameLoop::skipTutorial)) saveNow()
    }

    fun dismissOfflineSummary() = updateUi { it.copy(offlineSummary = null) }
    fun dismissCollapseSummary() = updateUi { it.copy(collapseSummary = null) }
    fun dismissAchievementToast() = updateUi { it.copy(newAchievements = emptyList()) }
    fun dismissEventToast() = updateUi { it.copy(activeEventToast = null) }
    fun dismissMilestoneToast() = updateUi { it.copy(activeMilestoneToast = null) }
    fun dismissOwnershipToast() = updateUi { it.copy(activeOwnershipToast = null) }
    fun dismissSaveWarnings() = updateUi { it.copy(recoveredFromBackup = false, saveWasCorrupted = false) }

    // ----------------------------------------------------------- lifecycle --

    /**
     * The authoritative save point. Android does not guarantee anything runs
     * when it kills a backgrounded app, so the moment the app stops being
     * visible is the last reliable chance to write.
     *
     * Both loops stop here. The tick has nothing to render and the autosave has
     * nothing new to write — leaving it running would rewrite an unchanging
     * save every fifteen seconds for as long as the app sits in the background.
     */
    fun onEnterBackground() {
        stopLoops()
        saveNow()
    }

    /** Resumes the loops, settling the whole absence in one step first. */
    fun onEnterForeground() {
        if (!started) return
        // The catch-up is a full simulation step and belongs off the main
        // thread like every other one, even though it is a single closed-form
        // call rather than a replayed loop.
        if (_uiState.value.loaded) viewModelScope.launch(simulationDispatcher) { tick() }
        if (tickJob == null) startLoops()
    }

    fun saveNow() {
        val state = _uiState.value
        if (!state.loaded) return
        viewModelScope.launch { saveRepository.save(state.state) }
    }

    override fun onCleared() {
        stopLoops()
        audio.release()
    }

    class Factory(
        private val saveRepository: SaveRepository,
        private val haptics: Haptics,
        private val audio: GameAudio,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GameViewModel(saveRepository, haptics, audio) as T
    }
}
