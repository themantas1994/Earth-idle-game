package com.earthgame.idle.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.earthgame.idle.data.persistence.DataStoreSaveRepository
import com.earthgame.idle.data.repository.LoadResult
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The save repository against a real DataStore on a real device.
 *
 * The serialization itself is covered by JVM tests; what needs a device is the
 * storage behaviour — that a write survives, that the backup slot rotates, and
 * that a corrupted primary falls back rather than losing the run.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreSaveRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = DataStoreSaveRepository(context)
    private val now = 1_700_000_000_000L

    @Before
    fun clean() = runBlocking { repository.clear() }

    @Test
    fun anEmptyStoreReportsEmpty() = runBlocking {
        assertEquals(LoadResult.Empty, repository.load(now))
    }

    @Test
    fun aSavedGameLoadsBack() = runBlocking {
        val state = createNewGame(now).let {
            it.copy(
                runNumber = 4,
                techOwned = mapOf("natural_fire" to 21, "coal_mining" to 3),
                resources = it.resources.with(ResourceId.ENERGY, gd("1.5e30")),
                atmosphere = it.atmosphere.with(GasId.CO2, gd("412.5")),
            )
        }

        repository.save(state)
        val loaded = repository.load(now)

        assertTrue("should load cleanly", loaded is LoadResult.Loaded)
        val restored = (loaded as LoadResult.Loaded).state
        assertEquals(4, restored.runNumber)
        assertEquals(21, restored.techOwned["natural_fire"])
        assertEquals(
            "an enormous balance must survive the round trip exactly",
            state.resources[ResourceId.ENERGY].exponent,
            restored.resources[ResourceId.ENERGY].exponent,
            0.0,
        )
    }

    @Test
    fun theBackupSlotHoldsThePreviousSave() = runBlocking {
        repository.save(createNewGame(now).copy(runNumber = 1))
        repository.save(createNewGame(now).copy(runNumber = 2))

        // The primary is the newer one...
        val loaded = repository.load(now)
        assertEquals(2, (loaded as LoadResult.Loaded).state.runNumber)

        // ...and corrupting it falls back to the older one rather than losing
        // everything.
        DataStoreCorruption.corruptPrimary(context)
        val recovered = repository.load(now)
        assertTrue("should recover from backup", recovered is LoadResult.RecoveredFromBackup)
        assertEquals(1, (recovered as LoadResult.RecoveredFromBackup).state.runNumber)
    }

    @Test
    fun clearingRemovesBothSlots() = runBlocking {
        repository.save(createNewGame(now).copy(runNumber = 1))
        repository.save(createNewGame(now).copy(runNumber = 2))
        repository.clear()
        assertEquals(LoadResult.Empty, repository.load(now))
    }
}
