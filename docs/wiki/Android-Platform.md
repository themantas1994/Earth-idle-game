# Android platform

[← Documentation home](Home.md)

## SDK and toolchain

| | |
| :-- | :-- |
| `applicationId` | `com.earthgame.idle` (`.debug` suffix on debug builds) |
| `minSdk` | **24** (Android 7.0) — the floor for this Compose/AndroidX stack, ~98% of active devices |
| `targetSdk` / `compileSdk` | **37** |
| `versionCode` / `versionName` | **1** / **1.0.0** |
| Java / Kotlin target | 17 |
| JDK to build with | 21 |

Core library desugaring is **off**: the engine formats numbers without `java.time`. The flag is
present and commented so it is a one-line change if a library ever needs it.

## Manifest

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.VIBRATE" />
```

Three, and no more. The game itself makes **zero network requests** — `INTERNET` and
`ACCESS_NETWORK_STATE` exist for the ad banner alone, and the game is fully playable without
either. `VIBRATE` is for the two haptic pulses.

> `play-services-ads` merges `com.google.android.gms.permission.AD_ID` into the final manifest.
> It is not declared here but it *is* in the shipped app, and the Play Console data-safety form
> has to declare it. See [Security and privacy](Security-and-Privacy.md).

**The app's own manifest declares one component**: `MainActivity`, with the launcher intent filter.
No services, no receivers, no providers and no deep links are written here, so the app's own
external entry point is "the user tapped the icon".

The **merged** manifest is larger, because the ad SDK's dependency chain contributes components of
its own — including three that are exported, each behind a system permission. They are inventoried
in [Security and privacy](Security-and-Privacy.md#attack-surface); the short version is that
nothing in this repository reaches any of them.

```xml
android:launchMode="singleTask"
android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode|density|fontScale"
```

`configChanges` keeps Compose in charge of reconfiguration rather than recreating the activity,
which matters because the running game lives in the ViewModel and a recreation would cost a frame
of re-composition for no benefit. `singleTask` stops a second instance appearing behind the first.

## Backup

`android:allowBackup="true"`, scoped by `backup_rules.xml` and `data_extraction_rules.xml` to
exactly one path:

```xml
<include domain="file" path="datastore/earth-save.preferences_pb" />
```

Backing up everything would drag a stale cache onto a fresh install; the save is the only thing
worth restoring. Both cloud backup and device-transfer are covered.

## Lifecycle

```kotlin
private val processObserver = object : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) { viewModel.onEnterForeground() }
    override fun onStop(owner: LifecycleOwner) { viewModel.onEnterBackground() }
}
ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)   // in onCreate
```

**Process lifecycle, not activity lifecycle.** A configuration change destroys and recreates the
activity, and treating that as "the player left" would save and re-settle an absence on every
rotation. `ProcessLifecycleOwner` fires only when the app as a whole goes to the background —
which is both the last reliable chance to write the save before Android may kill the process, and
the moment [offline progress](Offline-Progression.md) starts accruing.

The observer is removed in `onDestroy`. The tick and autosave loops live in `viewModelScope`, so
they cannot outlive the screen even if something goes wrong.

## Edge to edge

`enableEdgeToEdge()` before `super.onCreate`, with the platform theme painting the same dark ground
the game sits on:

```xml
<style name="Theme.Earth" parent="android:Theme.Material.NoActionBar">
    <item name="android:windowBackground">@color/splash_background</item>
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
</style>
```

The theme only has to bridge the gap between process start and the first composed frame. Left at
the system default it flashes white into a dark game.

Compose then handles insets itself: the header takes `WindowInsets.statusBars`, the bottom nav
takes `WindowInsets.navigationBars`.

## Platform adapters

Each is an interface in `platform/` with a real implementation and an inert one, constructed in
`EarthApplication` and injected through `GameViewModel.Factory`. The game is fully playable with
all three inert, which is the state every test runs them in.

| | Real | Inert |
| :-- | :-- | :-- |
| Haptics | `AndroidHaptics` | `NoHaptics` |
| Audio | `SoundPoolAudio` | `SilentAudio` |
| Ads | `MonetizationController` | *(null — the banner composable returns immediately)* |

See [Audio and haptics](Audio-and-Haptics.md) and [Advertising](Advertising.md).

## R8 / ProGuard

Release builds have `isMinifyEnabled = true` and `isShrinkResources = true`.

The engine and save models are plain Kotlin with no reflection, so R8's defaults handle them.
`kotlinx.serialization` generates its own serializers at compile time; `proguard-rules.pro` keeps
R8 from stripping the companion-object hooks it looks them up through:

```proguard
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.earthgame.idle.data.persistence.**$$serializer { *; }
-keepclassmembers class com.earthgame.idle.data.persistence.** { *** Companion; }
```

`lintRelease` and `bundleRelease` run in CI on every push, so a rule that stops being sufficient is
caught there rather than by a player.

> **Note:** the save format is hand-written `JsonObject` building rather than `@Serializable`
> classes, so those rules are currently belt-and-braces. They are correct and cost nothing, and
> they become load-bearing the moment anyone adds a `@Serializable` model.

## Lint

```kotlin
lint {
    warningsAsErrors = false
    abortOnError = true
    checkReleaseBuilds = true
    disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "ObsoleteLintCustomCheck")
}
```

`abortOnError` means a lint regression fails the build rather than scrolling past in a log. The
three disabled checks are version-currency nags that would fail CI the day a new dependency ships,
which is not a signal worth a red build.

**Current state: zero issues in both `lintDebug` and `lintRelease`.**

## Testing on a device

```bash
./gradlew connectedAndroidTest    # needs a device or emulator
```

`app/src/androidTest/` holds `EarthAppUiTest` and `DataStoreSaveRepositoryTest`. Most of what they
cover also runs under Robolectric in `./gradlew test`, so the instrumented suite is a confirmation
on real hardware rather than the only coverage — see [Testing](Testing.md).

---

**Next:** [Security and privacy](Security-and-Privacy.md) · [Build system](Build-System.md) · [Offline progression](Offline-Progression.md)
