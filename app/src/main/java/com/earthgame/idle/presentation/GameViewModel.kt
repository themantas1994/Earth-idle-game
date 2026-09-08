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
import kotlinx.coroutines.flow.update
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
 * ## Lifecycle
 *
 * The loop is tied to [viewModelScope], so it cannot outlive the screen. It is
 * additionally paused while the app is backgrounded ([onEnterBackground]),
 * because there is nothing to render and Android will not reliably keep running
 * it anyway — coming back calls [onEnterForeground], which settles the whole
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
                _uiState.value = GameUiState(
                    state = fresh,
                    derived = computeDerived(fresh, now),
                    loaded = true,
                    saveWasCorrupted = result is LoadResult.Corrupted,
                )
            } else {
                // Settle the absence before the first frame, so the player sees
                // the world as it is now rather than as it was when they left.
                val derived = computeDerived(loadedState, now)
                val stepped = withContext(simulationDispatcher) {
                    gameLoop.advance(loadedState, now, derived)
                }
                _uiState.value = GameUiState(
                    state = stepped.state,
                    derived = computeDerived(stepped.state, now),
                    loaded = true,
                    offlineSummary = stepped.events.offlineProgress,
                    recoveredFromBackup = result is LoadResult.RecoveredFromBackup,
                ).applyEvents(stepped.events)
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

    private fun tick() {
        val now = clock()
        val current = _uiState.value
        if (!current.loaded) return

        val result = gameLoop.advance(current.state, now, current.derived)
        if (result.state === current.state) return

        _uiState.update { previous ->
            previous.copy(
                state = result.state,
                derived = computeDerived(result.state, now),
                offlineSummary = result.events.offlineProgress ?: previous.offlineSummary,
            ).applyEvents(result.events)
        }

        if (result.events.justCollapsed) {
            if (current.state.settings.vibrationEnabled) haptics.impact()
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

    // ------------------------------------------------------------- actions --

    fun buyTechnology(techId: String, quantity: BuyQuantity) {
        val current = _uiState.value
        val result = gameLoop.purchase(current.state, techId, quantity.amount, current.derived)
        if (result.state === current.state) return

        if (current.state.settings.vibrationEnabled) haptics.tap()
        audio.play(if (result.events.ownershipMilestone != null) Sound.MILESTONE else Sound.PURCHASE)

        commit(result.state) { it.applyEvents(result.events) }
    }

    fun buyPrestigeUpgrade(upgradeId: String) {
        val current = _uiState.value
        val next = gameLoop.buyPrestigeUpgrade(current.state, upgradeId)
        if (next === current.state) return
        if (current.state.settings.vibrationEnabled) haptics.tap()
        audio.play(Sound.PURCHASE)
        commit(next)
    }

    /** Whether the Reset button should do anything — the planet has to be dead first. */
    fun canResetEarth(): Boolean = _uiState.value.state.collapsed

    fun resetEarth() {
        val current = _uiState.value
        if (!current.state.collapsed) return

        val now = clock()
        val summary = gameLoop.scoreRun(current.state, now, current.derived)
        val result = gameLoop.resetEarth(current.state, now, current.derived)
        if (result.state === current.state) return

        if (current.state.settings.vibrationEnabled) haptics.impact()

        _uiState.update { previous ->
            previous.copy(
                state = result.state,
                derived = computeDerived(result.state, now),
                collapseSummary = summary,
            ).applyEvents(result.events)
        }
        saveNow()
    }

    fun startChallenge(challengeId: String) {
        val current = _uiState.value
        commit(gameLoop.startChallenge(current.state, challengeId, clock()))
        saveNow()
    }

    fun abandonChallenge() = commit(gameLoop.abandonChallenge(_uiState.value.state))

    fun updateSettings(transform: (Settings) -> Settings) {
        val current = _uiState.value
        val settings = transform(current.state.settings)
        audio.setSoundEnabled(settings.soundEnabled)
        audio.setMusicEnabled(settings.musicEnabled)
        commit(gameLoop.updateSettings(current.state, settings))
        saveNow()
    }

    fun advanceTutorial() = commit(gameLoop.advanceTutorial(_uiState.value.state))

    fun skipTutorial() {
        commit(gameLoop.skipTutorial(_uiState.value.state))
        saveNow()
    }

    fun dismissOfflineSummary() = _uiState.update { it.copy(offlineSummary = null) }
    fun dismissCollapseSummary() = _uiState.update { it.copy(collapseSummary = null) }
    fun dismissAchievementToast() = _uiState.update { it.copy(newAchievements = emptyList()) }
    fun dismissEventToast() = _uiState.update { it.copy(activeEventToast = null) }
    fun dismissMilestoneToast() = _uiState.update { it.copy(activeMilestoneToast = null) }
    fun dismissOwnershipToast() = _uiState.update { it.copy(activeOwnershipToast = null) }
    fun dismissSaveWarnings() = _uiState.update { it.copy(recoveredFromBackup = false, saveWasCorrupted = false) }

    // ----------------------------------------------------------- lifecycle --

    /**
     * The authoritative save point. Android does not guarantee anything runs
     * when it kills a backgrounded app, so the moment the app stops being
     * visible is the last reliable chance to write.
     */
    fun onEnterBackground() {
        tickJob?.cancel()
        tickJob = null
        saveNow()
    }

    /** Resumes the loop, settling the whole absence in one step first. */
    fun onEnterForeground() {
        if (!started) return
        if (_uiState.value.loaded) tick()
        if (tickJob == null) startLoops()
    }

    fun saveNow() {
        val state = _uiState.value
        if (!state.loaded) return
        viewModelScope.launch { saveRepository.save(state.state) }
    }

    private fun commit(next: GameState, decorate: (GameUiState) -> GameUiState = { it }) {
        val now = clock()
        _uiState.update { previous ->
            decorate(previous.copy(state = next, derived = computeDerived(next, now)))
        }
    }

    override fun onCleared() {
        audio.release()
        super.onCleared()
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
