package com.earthgame.idle.platform.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Short haptic pulses for purchases and milestone moments.
 *
 * Haptics are a garnish, never a requirement: every call is a no-op when the
 * setting is off, when the device has no vibrator, or when the platform refuses
 * — the game plays identically without them.
 */
interface Haptics {
    /** A light tap, for a purchase. */
    fun tap()

    /** A heavier pulse, for a collapse or a prestige reset. */
    fun impact()
}

object NoHaptics : Haptics {
    override fun tap() = Unit
    override fun impact() = Unit
}

class AndroidHaptics(context: Context) : Haptics {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }.getOrNull()?.takeIf { it.hasVibrator() }

    override fun tap() = vibrate(TAP_MS, TAP_AMPLITUDE)

    override fun impact() = vibrate(NOTIFY_MS, NOTIFY_AMPLITUDE)

    private fun vibrate(durationMs: Long, amplitude: Int) {
        val device = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Amplitude control arrived with VibrationEffect in API 26.
                device.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                // On 24 and 25 the only control is duration, which is enough
                // for a tap the player barely registers as a distinct pulse.
                @Suppress("DEPRECATION")
                device.vibrate(durationMs)
            }
        }
    }

    private companion object {
        const val TAP_MS = 12L
        const val TAP_AMPLITUDE = 90
        const val NOTIFY_MS = 40L
        const val NOTIFY_AMPLITUDE = 200
    }
}
