package com.earthgame.idle.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.runBlocking

/**
 * Writes garbage into the primary save slot, so the backup-recovery path can be
 * exercised for real rather than simulated.
 *
 * It opens the same DataStore by name — deliberately, since that is what a
 * damaged file on a real device looks like to the repository.
 */
private val Context.corruptionDataStore: DataStore<Preferences> by preferencesDataStore(name = "earth-save")

object DataStoreCorruption {
    fun corruptPrimary(context: Context) = runBlocking {
        context.corruptionDataStore.edit { preferences ->
            preferences[stringPreferencesKey("save")] = "{ not a save at all"
        }
        Unit
    }
}
