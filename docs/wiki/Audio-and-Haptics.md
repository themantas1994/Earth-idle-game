# Audio and haptics

[← Documentation home](Home.md)

Both are garnish. Every call is a no-op when the setting is off, when the device cannot do it, or
when the platform refuses — **the game plays identically without them**, and that is the state
every test runs in.

## Haptics

`platform/haptics/HapticFeedback.kt`.

```kotlin
interface Haptics {
    fun tap()      // light, for a purchase
    fun impact()   // heavier, for a collapse or a prestige reset
}
```

| | Duration | Amplitude |
| :-- | --: | --: |
| `tap()` | 12 ms | 90 |
| `impact()` | 40 ms | 200 |

`AndroidHaptics` resolves the vibrator once at construction, through `VibratorManager` on API 31+
and the deprecated `getSystemService(Vibrator::class.java)` below it, wrapped in `runCatching` and
filtered by `hasVibrator()`. Amplitude control arrived with `VibrationEffect` in API 26; on 24 and
25 only duration is available, which is enough for a tap the player barely registers as a distinct
pulse.

Every call site checks `state.settings.vibrationEnabled` first, and every `vibrate` is inside
`runCatching` — a manufacturer ROM that throws cannot take the game down.

`NoHaptics` is the inert implementation.

## Audio

`platform/audio/GameAudio.kt`.

```kotlin
enum class Sound(@param:RawRes val resourceId: Int?) {
    PURCHASE(null),   // a technology purchase lands
    MILESTONE(null),  // an ownership threshold doubles a building's output
    COLLAPSE(null),   // the planet becomes uninhabitable
}
```

> [!NOTE]
> **No audio assets ship.** The sound and music settings are real and the plumbing is real, but
> every `Sound` has a `null` resource, so nothing plays. This is the same state the original web
> build was in — the toggles existed, the assets never did.
>
> Rather than leave that as a dangling TODO, the seam is drawn where the assets will land: **add a
> raw resource, point a `Sound` entry at it, and it plays.** Nothing else changes.

`SoundPoolAudio` is a `SoundPool`-backed implementation, which is the right primitive for short
effects: it decodes once, plays with no per-call latency, and mixes several overlapping sounds
without allocating a player each time. Four streams, 0.6 volume, `USAGE_GAME` /
`CONTENT_TYPE_SONIFICATION`.

The pool is `by lazy` and is only touched when a `Sound` has a non-null resource, so **a build with
no audio assets costs nothing at runtime** — no pool is ever constructed. `release()` is guarded on
the same condition, and is called from `ViewModel.onCleared`.

`musicEnabled` is stored but does nothing yet; no music track ships. The flag exists so a future
track starts and stops without the caller changing.

`SilentAudio` is the inert implementation.

## Adding sounds

1. Drop the file in `app/src/main/res/raw/` (short, small — `.ogg` is a good default).
2. Point the `Sound` entry at it: `PURCHASE(R.raw.purchase)`.
3. That is all. `SoundPoolAudio` loads it lazily on first play and the existing setting gates it.

For music, add a track and implement `setMusicEnabled` against a `MediaPlayer` or `ExoPlayer` —
`SoundPool` is not for long audio. Remember to stop it in `release()`.

---

**Next:** [Android platform](Android-Platform.md) · [Accessibility](Accessibility.md)
