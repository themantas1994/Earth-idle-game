package com.earthgame.idle.data.persistence

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

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
 *
 * ## Two independent kinds of corruption
 *
 * A *save* can be unreadable while the file around it is fine — truncated JSON,
 * a hand-edited value of the wrong shape. That is what the backup slot is for,
 * and [SaveSerialization.deserialize] reports it by returning null.
 *
 * The *file itself* can also be unreadable: a torn write at the filesystem
 * level, or a proto that no longer parses. DataStore signals that by throwing
 * `CorruptionException` from every read and write, for the life of the process,
 * which without a handler is a permanent crash loop on launch — the worst
 * possible failure for a game whose whole state is that one file.
 * [ReplaceFileCorruptionHandler] resets it to empty instead, and
 * [FileCorruptionFlag] remembers that it happened so the player is told a new
 * game was started rather than silently handed one.
 */
private val Context.saveDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATA_STORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler {
        Log.w(DATA_STORE_NAME, "Save file was unreadable and has been reset", it)
        FileCorruptionFlag.mark()
        emptyPreferences()
    },
)

/** Also named in `backup_rules.xml`, which scopes Android Auto Backup to this file alone. */
internal const val DATA_STORE_NAME = "earth-save"

/**
 * Set when DataStore had to replace the save file wholesale. Read once by the
 * next [DataStoreSaveRepository.load] so an empty store can be reported as
 * corruption rather than as a first launch.
 *
 * Process-wide because the corruption handler is installed on the
 * process-wide DataStore delegate, not on the repository instance.
 */
internal object FileCorruptionFlag {
    private val flagged = AtomicBoolean(false)

    fun mark() = flagged.set(true)

    /** Reads and clears the flag. */
    fun consume(): Boolean = flagged.getAndSet(false)
}

class DataStoreSaveRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SaveRepository {

    private val primaryKey = stringPreferencesKey(PRIMARY_SLOT)
    private val backupKey = stringPreferencesKey(BACKUP_SLOT)

    override suspend fun load(now: Long): LoadResult = withContext(ioDispatcher) {
        val preferences = try {
            context.saveDataStore.data.first()
        } catch (e: IOException) {
            // The corruption handler above covers a file DataStore can identify
            // as corrupt; this covers the rest — a read error, a permission
            // problem, a full or unmounted volume. A load must never crash the
            // game on launch.
            Log.w(TAG, "Save could not be read", e)
            return@withContext LoadResult.Corrupted
        }

        val fileWasReplaced = FileCorruptionFlag.consume()
        val primary = preferences[primaryKey]
        val backup = preferences[backupKey]

        if (primary == null && backup == null) {
            return@withContext if (fileWasReplaced) LoadResult.Corrupted else LoadResult.Empty
        }

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
        try {
            context.saveDataStore.edit { preferences ->
                // Rotate the previous save into the backup slot in the same
                // transaction that writes the new one, so the two can never
                // disagree about what the last known-good state was.
                preferences[primaryKey]?.let { preferences[backupKey] = it }
                preferences[primaryKey] = serialized
            }
        } catch (e: IOException) {
            // A full disk or a revoked volume. The next autosave tries again,
            // and losing a save is not worth killing the run over.
            Log.w(TAG, "Save could not be written", e)
        }
        Unit
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        try {
            context.saveDataStore.edit { it.clear() }
        } catch (e: IOException) {
            Log.w(TAG, "Save could not be cleared", e)
        }
        Unit
    }

    companion object {
        internal const val PRIMARY_SLOT = "save"
        internal const val BACKUP_SLOT = "save.backup"
        private const val TAG = "SaveRepository"
    }
}
