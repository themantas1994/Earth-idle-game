# EARTH

An idle/incremental civilization simulator for Android: build technology,
generate greenhouse gases, heat the planet past the point of habitability, then
reset and do it faster next time. Themed around atmospheric chemistry and
civilizational collapse, in the tradition of *Antimatter Dimensions* and
*AdVenture Capitalist*. Portrait-first, one-handed, and designed to be checked
in on a few times a day rather than watched.

Native Kotlin and Jetpack Compose. No WebView, no JavaScript.

> This is a gameplay simulation, not a scientific climate model. The formulas
> are simplified but internally consistent, and use real, plausible
> relationships — logarithmic CO₂ forcing, gas lifetimes, sinks that weaken
> under warming — so the numbers behave sensibly at both small and absurd
> scale.

## Building

Open the repository root in Android Studio — it is the Gradle project — or use
the wrapper from the command line. You need:

- **JDK 21.** The wrapper pins Gradle 9.7.1 and AGP 9.4.0, which run on 17–21;
  21 is what CI uses.
- **Android SDK** with `platforms;android-37`, `build-tools;37.0.0` and
  `platform-tools`. The app targets API 37 and runs back to API 24.
- **`ANDROID_HOME`** pointing at that SDK, or `sdk.dir=/path/to/sdk` in a
  `local.properties` at the repository root (git-ignored).

Nothing else — no Node, no npm, no web toolchain anywhere in the build path.

```bash
git clone https://github.com/themantas1994/Earth-idle-game.git
cd Earth-idle-game
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The debug build installs alongside a release build rather than replacing it —
it carries a `.debug` application-id suffix — so you can keep both on one
device.

| | |
| --- | --- |
| `./gradlew assembleDebug` | Debug APK → `app/build/outputs/apk/debug/app-debug.apk` |
| `./gradlew bundleRelease` | Play bundle → `app/build/outputs/bundle/release/app-release.aab` |
| `./gradlew assembleRelease` | Release APK → `app/build/outputs/apk/release/` (`app-release-unsigned.apk` until a keystore is configured) |
| `./gradlew test` | The whole simulation, on the JVM, in a few seconds |
| `./gradlew lintDebug lintRelease` | Reports → `app/build/reports/lint-results-*.html` |
| `./gradlew connectedAndroidTest` | Instrumented tests; needs a device or emulator |

A release build assembles unsigned when no keystore is configured, so a fresh
clone is never blocked on secrets. **[ANDROID.md](ANDROID.md)** covers signing,
where the AdMob identifiers live, the project layout and CI.

## The game

You start with one lightning-struck tree, still smouldering. It produces Energy
whether you are watching or not — there is no tap button, because Energy comes
from a source of energy. Everything after that is the same loop: research an
unlock on the **Technology** tab, build what it unlocked on the **Production**
tab, watch the **Atmosphere** tab fill in with what you have done.

A first Earth takes a few days of real time. It keeps running while the app is
closed — twelve hours banked at a time, more with upgrades — so a check-in
means arriving to a stockpile and spending all of it at once. When habitability
reaches zero the run ends, and you bank Earth Points toward making the next
civilization faster.

**Prices never rise.** A price you have been quoted is the price you pay,
however large the tree around it grows. The only thing that ever makes
something dearer is you, buying more of that same thing.

**Every tenth copy doubles its output.** Each generator carries its own
progress track, so going deep on one building has a visible payoff — spaced so
that broadening into new technology still wins in the long run.

## What is in it

| | |
| --- | --- |
| **99 technologies** | Across 11 branches, from Controlled Fire to a Matrioshka Brain, including a mutually exclusive Coal-vs-Nuclear strategic choice. 68 are repeatable generators; 31 are research nodes. |
| **6 greenhouse gases** | CO₂, CH₄, N₂O, H₂O, O₃ and an aggregate fluorinated bucket, each with its own lifetime, forcing curve and display unit. |
| **A 5-factor habitability model** | Temperature, ocean acidity, sea level, agriculture and biodiversity — *multiplied*, so any one of them collapsing ends the run. |
| **Prestige** | Earth Points and 11 permanent upgrades, scored on how *fast* the run was rather than only how it ended. |
| **32 achievements, 8 challenges, 13 random events** | Challenges impose a live restriction for a permanent reward. |
| **39 world-news headlines** | Deterministic, once per run, no mechanical effect — they exist so a multi-day run reads as a story rather than a rising number. |

## How it is built

```
domain/        Pure Kotlin. No Android, no Compose. The whole simulation.
data/          Versioned save, DataStore-backed, with a backup slot.
platform/      Haptics, audio, ads — each behind an interface.
presentation/  ViewModel, StateFlow, Compose UI.
```

Three things are worth knowing about the architecture.

**The engine is a pure function of state.** `simulateStep(state, dt)` returns
the next state and nothing else — no clocks, no I/O, no Android. That is what
lets the entire simulation be tested in a few seconds on the JVM, and what
makes the offline path and the live path provably identical.

**Gas concentrations are integrated in closed form**, not stepped. `dE/dt =
production − k·E` has an exact solution, so one call with `dt = 8 hours`
produces the same numbers as 115,200 calls with `dt = 250 ms`. Offline progress
is therefore a single calculation rather than a simulation loop, and there is
no background service, no scheduled work and no wake locks anywhere in the app.

**Numbers are arbitrary-scale.** A finished run passes 1e400, which a `Double`
cannot hold. `GameDecimal` stores `sign × mantissa × 10^exponent` with the
exponent itself a `Double`, so the representable range runs to about
10^(1.8e308) — unreachable in this game — and the save format stores the exact
triple rather than a rounded number.

## Porting notes

This started life as a TypeScript/React game shipped through Capacitor. The
Android app is a full native rewrite, and its correctness is checked against
the original by execution rather than by reading: the reference engine's
answers were captured as golden fixtures, and the Kotlin suite asserts against
them — every field of all 99 technologies, 576 Decimal operand pairs, 3,240 gas
integrations, every formatting mode in every notation, the save format
including the v1→v2→v3 migration chain, and a 95-step scripted playthrough
compared value by value.

The frozen reference lives in `tools/ts-reference/` so those fixtures can be
regenerated. Nothing in the build depends on it.

Two deliberate differences from the reference, both documented where they live:

- **The Speedrun challenge** scores elapsed run time from the game clock rather
  than reading a wall clock inside its goal check, so the same state always
  gives the same answer. Live behaviour is unchanged.
- **Water vapour** lags one simulation step, because it is a feedback driven by
  the previous step's temperature rather than an accumulating stock. This
  matches the reference; it emits nothing and is never banked or scored.

## Known gaps

- **No audio assets ship.** The sound and music settings are real and the audio
  layer is wired up, but there is nothing to play yet — the same state the
  original was in. Adding a raw resource and a `Sound` entry is all it takes.
- **Not yet run on physical hardware.** The build environment has no KVM, so no
  emulator can boot here. Everything above the platform layer is covered by JVM
  tests; the instrumented suite (`connectedAndroidTest`) covers DataStore and
  the Compose UI but has not been executed against a real device.
- **The fluorinated gases are one aggregate bucket.** Splitting CFCs, HFCs,
  PFCs and SF₆ into four is a data change rather than an engine change — the
  gas registry is built for it.
