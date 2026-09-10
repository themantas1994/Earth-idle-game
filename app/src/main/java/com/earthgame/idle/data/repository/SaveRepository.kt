package com.earthgame.idle.data.repository

import com.earthgame.idle.domain.model.GameState

/**
 * Where a playthrough is persisted.
 *
 * Kept as an interface with no Android types so the engine's tests can drive a
 * real save/load cycle in memory, and so swapping the storage backend later is
 * one class rather than a refactor.
 */
interface SaveRepository {

    /**
     * Loads the current save, falling back to the backup slot if the primary is
     * missing or corrupted. Returns null when there is nothing playable, which
     * the caller reads as "start a new game".
     */
    suspend fun load(now: Long): LoadResult

    /**
     * Persists [state], keeping the previous save as a backup first. If a write
     * is interrupted — the OS killing a backgrounded app mid-save — the backup
     * still holds the last known-good state.
     */
    suspend fun save(state: GameState)

    /** Erases both slots. Used by the "delete save" action in Settings. */
    suspend fun clear()
}

/**
 * What a load produced, and how. The provenance matters to the UI: a player
 * whose primary save was corrupt should be told their backup was used rather
 * than silently handed a slightly older game.
 */
sealed interface LoadResult {
    /** Nothing was stored. A new game starts. */
    data object Empty : LoadResult

    /** The primary save loaded cleanly. */
    data class Loaded(val state: GameState) : LoadResult

    /** The primary save was unreadable and the backup was used instead. */
    data class RecoveredFromBackup(val state: GameState) : LoadResult

    /** Both slots were unreadable. A new game starts, and the player is told. */
    data object Corrupted : LoadResult
}

/** The state a load produced, or null when there is nothing to resume. */
val LoadResult.stateOrNull: GameState?
    get() = when (this) {
        is LoadResult.Loaded -> state
        is LoadResult.RecoveredFromBackup -> state
        else -> null
    }
