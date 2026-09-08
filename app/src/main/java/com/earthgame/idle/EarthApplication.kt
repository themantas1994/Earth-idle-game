package com.earthgame.idle

import android.app.Application
import com.earthgame.idle.data.persistence.DataStoreSaveRepository
import com.earthgame.idle.data.repository.SaveRepository
import com.earthgame.idle.platform.audio.GameAudio
import com.earthgame.idle.platform.audio.SoundPoolAudio
import com.earthgame.idle.platform.haptics.AndroidHaptics
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

    val saveRepository: SaveRepository by lazy { DataStoreSaveRepository(applicationContext) }

    val haptics: Haptics by lazy { AndroidHaptics(applicationContext) }

    val audio: GameAudio by lazy { SoundPoolAudio(applicationContext) }
}
