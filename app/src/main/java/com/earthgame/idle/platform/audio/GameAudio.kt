package com.earthgame.idle.platform.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes

/**
 * The sounds the game can make.
 *
 * The original build shipped the *settings* for sound and music but no audio
 * assets, and this port is the same: the toggles are real, the plumbing is
 * real, and there is nothing to play yet. Rather than leave that as a dangling
 * TODO, the seam is drawn where the assets will land — add a raw resource, add
 * a [Sound] entry, and it plays. Nothing else has to change.
 */
enum class Sound(@param:RawRes val resourceId: Int?) {
    /** A technology purchase lands. */
    PURCHASE(null),

    /** An ownership threshold doubles a building's output. */
    MILESTONE(null),

    /** The planet becomes uninhabitable. */
    COLLAPSE(null),
}

interface GameAudio {
    fun play(sound: Sound)
    fun setSoundEnabled(enabled: Boolean)
    fun setMusicEnabled(enabled: Boolean)
    fun release()
}

object SilentAudio : GameAudio {
    override fun play(sound: Sound) = Unit
    override fun setSoundEnabled(enabled: Boolean) = Unit
    override fun setMusicEnabled(enabled: Boolean) = Unit
    override fun release() = Unit
}

/**
 * A [SoundPool]-backed implementation, which is the right primitive for short
 * effects: it decodes once, plays with no per-call latency, and mixes several
 * overlapping sounds without allocating a player each time.
 *
 * It loads lazily and holds nothing while every [Sound] has a null resource, so
 * a build with no audio assets costs nothing at runtime.
 */
class SoundPoolAudio(private val context: Context) : GameAudio {

    private var soundEnabled = true
    private var musicEnabled = true

    private val pool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
    }

    private val loaded = mutableMapOf<Sound, Int>()

    override fun play(sound: Sound) {
        if (!soundEnabled) return
        val resourceId = sound.resourceId ?: return
        val soundId = loaded.getOrPut(sound) { pool.load(context, resourceId, 1) }
        pool.play(soundId, VOLUME, VOLUME, 1, 0, 1f)
    }

    override fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }

    override fun setMusicEnabled(enabled: Boolean) {
        // No music track ships yet; the flag is stored so a future track starts
        // and stops without the caller changing.
        musicEnabled = enabled
    }

    override fun release() {
        if (loaded.isNotEmpty()) {
            pool.release()
            loaded.clear()
        }
    }

    private companion object {
        const val MAX_STREAMS = 4
        const val VOLUME = 0.6f
    }
}
