package com.earthgame.idle.data.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.earthgame.idle.data.repository.LoadResult
import com.earthgame.idle.data.repository.SaveRepository
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.save.SaveSerialization
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The save file, backed by Jetpack DataStore.
 *
 * DataStore is used rather than raw `SharedPreferences` for two reasons that
 * matter to an idle game: its writes are transactional and atomic, so a process
 * killed mid-save leaves the previous contents intact rather than a truncated
 * file; and its API is suspending, so the main thread never blocks on disk —
 * saving happens on the app's autosave cadence and on every backgrounding,
 * which is exactly when a dropped frame would be noticed.
 *
 * Both slots live in one DataStore and are written in a single `edit`
 * transaction, so the backup can never be left describing a save that no longer
 * exists.
 */
private val Context.saveDataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)

/** Also named in `backup_rules.xml`, which scopes Android Auto Backup to this file alone. */
internal const val DATA_STORE_NAME = "earth-save"

class DataStoreSaveRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SaveRepository {

    private val primaryKey = stringPreferencesKey(PRIMARY_SLOT)
    private val backupKey = stringPreferencesKey(BACKUP_SLOT)

    override suspend fun load(now: Long): LoadResult = withContext(ioDispatcher) {
        val preferences = context.saveDataStore.data.first()
        val primary = preferences[primaryKey]
        val backup = preferences[backupKey]

        if (primary == null && backup == null) return@withContext LoadResult.Empty

        primary?.let { SaveSerialization.deserialize(it, now) }
            ?.let { return@withContext LoadResult.Loaded(it) }

        // The primary save is unreadable. This is the case the backup slot
        // exists for, and it is worth telling the player about rather than
        // quietly resuming a slightly older game.
        backup?.let { SaveSerialization.deserialize(it, now) }
            ?.let { return@withContext LoadResult.RecoveredFromBackup(it) }

        LoadResult.Corrupted
    }

    override suspend fun save(state: GameState) = withContext(ioDispatcher) {
        val serialized = SaveSerialization.serialize(state)
        context.saveDataStore.edit { preferences ->
            // Rotate the previous save into the backup slot in the same
            // transaction that writes the new one, so the two can never
            // disagree about what the last known-good state was.
            preferences[primaryKey]?.let { preferences[backupKey] = it }
            preferences[primaryKey] = serialized
        }
        Unit
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        context.saveDataStore.edit { it.clear() }
        Unit
    }

    companion object {
        internal const val PRIMARY_SLOT = "save"
        internal const val BACKUP_SLOT = "save.backup"
    }
}
