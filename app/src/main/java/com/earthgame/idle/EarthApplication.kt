package com.earthgame.idle

import android.app.Application
import android.util.Log
import com.earthgame.idle.data.persistence.DataStoreSaveRepository
import com.earthgame.idle.data.repository.SaveRepository
import com.earthgame.idle.platform.audio.GameAudio
import com.earthgame.idle.platform.audio.SoundPoolAudio
import com.earthgame.idle.platform.haptics.AndroidHaptics
import com.earthgame.idle.domain.production.EconomyProblemSeverity
import com.earthgame.idle.domain.production.validateEconomy
import com.earthgame.idle.platform.haptics.Haptics

/**
 * The application, and the composition root.
 *
 * The game has exactly three long-lived collaborators — the save repository,
 * haptics and audio — so they are constructed here and handed to the ViewModel
 * rather than pulled in through a dependency-injection framework. Each is
 * behind an interface, so a test substitutes an in-memory or silent
 * implementation without any of the machinery.
 */
class EarthApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        assertEconomyIsPlayable()
    }

    /**
     * Fails loudly, at launch, on an economy graph a player could not actually
     * run — a processor with no supplier, a technology that unlocks before its
     * own feedstock, a loop that mints resources out of nothing.
     *
     * `EconomyValidationTest` already fails the build on any of these, so this
     * should never fire in a shipped binary. It exists for the case the test
     * cannot cover: a debug build assembled from a working tree where the data
     * files and the test suite have drifted apart. In a release build a broken
     * graph is logged rather than thrown, because crashing a player's game is a
     * worse outcome than a mis-tuned chain.
     */
    private fun assertEconomyIsPlayable() {
        val problems = validateEconomy().filter { it.severity == EconomyProblemSeverity.ERROR }
        if (problems.isEmpty()) return

        val report = problems.joinToString("\n") { it.toString() }
        Log.e(TAG, "The economy graph is not playable:\n$report")
        if (BuildConfig.DEBUG) {
            throw IllegalStateException("The economy graph is not playable:\n$report")
        }
    }

    val saveRepository: SaveRepository by lazy { DataStoreSaveRepository(applicationContext) }

    val haptics: Haptics by lazy { AndroidHaptics(applicationContext) }

    val audio: GameAudio by lazy { SoundPoolAudio(applicationContext) }

    private companion object {
        const val TAG = "EarthApplication"
    }
}
