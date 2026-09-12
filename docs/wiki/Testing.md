# Testing

[← Documentation home](Home.md)

## Running everything

```bash
./gradlew test                    # 216 JVM tests, ~30 s cold, seconds warm
./gradlew lintDebug lintRelease   # Android lint, both variants
./gradlew connectedAndroidTest    # instrumented; needs a device or emulator

python3 scripts/check-docs-links.py   # every relative Markdown link and anchor

cd tools/ts-reference
npm install && npx vitest run     # the reference engine's own 198 tests
npm run fixtures                  # regenerate the golden fixtures
```

## What lives where

```
app/src/test/java/com/earthgame/idle/
  parity/          10 classes — agreement with the TypeScript reference
  engine/          GameLoopTest, GameDecimalEdgeCaseTest, OfflineLifecycleTest
  save/            SaveMigrationTest
  balance/         BalanceInvariantsTest
  acceptance/      GameAcceptanceTest — a headless player
  presentation/    GameViewModelTest, GameViewModelConcurrencyTest, EarthAppScreenTest

app/src/androidTest/java/com/earthgame/idle/
  data/            DataStoreSaveRepositoryTest + DataStoreCorruption helper
  presentation/    EarthAppUiTest
```

## The suites

### Parity — 80 cases

The core of the suite. The original TypeScript engine was **run**, and its answers captured as JSON
fixtures; the Kotlin tests assert against those rather than against a re-reading of its source. See
[Reference parity](Reference-Parity.md).

| Class | Cases | Pins down |
| :-- | --: | :-- |
| `GameDecimalParityTest` | 10 | 24 operands from 1e-400 to 1e1000, all 576 binary pairs, 104 `pow` cases, the save triple |
| `FormattingParityTest` | 5 | 36 values × 4 notations × 2 precisions as **strings**, plus durations, temperatures, percentages, and suffix uniqueness to 1e900 |
| `TechnologyParityTest` | 10 | All 99 technologies field by field, the tier curves at 45 tiers, and the graph properties: no cycles, everything reachable, screens disjoint |
| `EconomyParityTest` | 13 | Quoted and bulk prices, buy-max including the exact geometric boundary, the discount, the ownership ladder, and five separate assertions of the price-stability invariant |
| `ClimateParityTest` | 11 | **3,240 gas integrations**, forcing per gas and total, temperature, ocean chemistry, all five habitability factors, sea level |
| `PrestigeParityTest` | 6 | The upgrade table, nine ownership combinations, the speed term, payouts for totals from 0 to 1e120 |
| `ContentParityTest` | 7 | The achievement, challenge, event, milestone, gas and resource tables — including each challenge's **computed** lockout set |
| `EventsParityTest` | 5 | Every eligibility state × draw, multiplier composition, instant bursts |
| `SimulationParityTest` | 5 | A **95-step scripted playthrough** to collapse and through a reset, compared value by value; step-size independence; determinism |
| `OfflineParityTest` | 5 | Absences from 0 to a week, with and without cap upgrades, enabled and disabled |

### Behaviour — 79 cases

Properties that must hold whatever the reference does.

| Class | Cases | Covers |
| :-- | --: | :-- |
| `GameLoopTest` | 21 | Ticks, absences, backwards clocks, seeded event reproducibility, the challenge pause, the 3-event cap, pruning, headlines, achievements, challenge settlement, purchasing, prestige, reset |
| `GameDecimalEdgeCaseTest` | 20 | Normalization across every operand pair, canonical zero, total ordering, saturation, **formatting purity**, no NaN anywhere, parse forms |
| `OfflineLifecycleTest` | 18 | Every lifecycle event and clock anomaly a real device produces |
| `SaveMigrationTest` | 12 | The chain, idempotence, future-version saves, hostile save content |
| `BalanceInvariantsTest` | 8 | The pacing relationships the prose in `Constants.kt` claims about itself |

### Acceptance — 13 cases

`GameAcceptanceTest` plays the game headlessly, in the order a player experiences it: a new game
starts playable → resources accrue with no input → the first purchase is affordable within a minute
→ buying raises production and production raises the climate → ownership bonuses land every tenth
copy → a heated planet collapses → achievements and headlines fire → prestige banks points and
starts a faster Earth → a save survives closing the app → a corrupted save falls back → settings
persist → late-game values never overflow → a challenge restricts and pays out.

These are the tests that would catch "the game is unplayable" as opposed to "a formula moved".

### Presentation — 28 cases

| Class | Runs under | Covers |
| :-- | :-- | :-- |
| `GameViewModelTest` | JVM + virtual time | Loading, tick cadence, autosave, background/foreground, the cap, backup fallback, corruption, haptics and audio gating |
| `GameViewModelConcurrencyTest` | JVM + **real threads** | The tick racing the player. One deterministic forced interleaving plus two invariant stress tests |
| `EarthAppScreenTest` | **Robolectric** | The whole Compose UI: nine destinations, Back unwinding, a purchase reaching the game, disjoint shopping lists, every Settings toggle, the reset gate, the tutorial, light theme, the wide layout |
| `AboutScreenTest` | **Robolectric** | About and the licence notices: reachable from Settings, showing the real build, stating the project's licence rather than assuming one, Back unwinding Licenses → About → Settings, and the bundled notices asset being real |
| `ProductionAdConfigTest` | JVM | The shipped AdMob identifiers, read from `ads.xml` in both source sets: the production values are exactly the account's, the debug overlay is Google's sample, no sample identifier is in the release resources, and the app ID is declared exactly once per variant |

### Instrumented

`DataStoreSaveRepositoryTest` (empty store, save/load, backup rotation, clearing, **real** primary
corruption) and `EarthAppUiTest` (a subset of the screen test on hardware).

> Most of what these cover also runs under Robolectric in `./gradlew test`, deliberately: there is
> no hardware-accelerated emulator in CI, and leaving DataStore and the UI to a suite nobody runs
> would have been the same as not testing them.

## Driving virtual time

`GameViewModelTest` advances the scheduler by hand rather than using `advanceUntilIdle()`, because
the tick loop is `while (isActive) { delay(250); tick() }` and never goes idle — `advanceUntilIdle`
against it spins forever.

The **wall clock is injected separately from virtual time**, so "eight hours passed while the app
was backgrounded" is one variable rather than a wait:

```kotlin
private fun elapse(millis: Long) {
    now += millis                  // the injected clock
    scope.advanceTimeBy(millis)    // virtual time, so the loop fires
    scope.runCurrent()
}
```

## Floating-point tolerances

Documented in `parity/Fixtures.kt` and treated as a contract:

| | Value | For |
| :-- | --: | :-- |
| `TOLERANCE_EXACT` | 0.0 | Integers, counts, ids, booleans — anything that is a copy rather than a computation |
| `TOLERANCE_TIGHT` | 1e-9 | A single arithmetic operation or a short chain |
| `TOLERANCE_LOOSE` | 1e-6 | The end of a long dependent chain (a full simulated run) |

The two engines run identical formulas but through different `pow`/`log10`/`exp` implementations,
which are permitted to differ in the last ulp, so error accumulates slightly differently across
thousands of dependent operations.

`GameDecimal` comparisons are made **in log space**, so "relative difference" is meaningful across
the whole representable range: a value near 1e1000 is held to the same relative accuracy as one
near 1.

## Adding a test

**A new engine behaviour** → a behaviour test in `engine/`, `save/` or `balance/`. Assert what the
player would notice, not how the code is arranged.

**A change to shared maths** → change it in both engines, regenerate the fixtures, and let the
existing parity test compare the new values. See
[Reference parity](Reference-Parity.md#changing-shared-maths).

**A new parity case** → add it to `tools/ts-reference/exportParityFixtures.ts`, regenerate, then
read it in the matching `*ParityTest`. Never hand-write a fixture: the point is that the numbers
came from executing the reference.

**A UI behaviour** → `EarthAppScreenTest` under Robolectric, so it runs in `./gradlew test`. Reserve
`androidTest/` for things that genuinely need hardware.

### What not to write

- Tests that assert a private helper's shape rather than an observable behaviour.
- Tests that depend on `System.currentTimeMillis()`. Every clock in the engine is a parameter;
  inject it.
- Tests that depend on real elapsed time. Use virtual time, or inject the clock.
- Hand-computed "expected" values for shared maths. That is what the fixtures are for.

## CI

`.github/workflows/android.yml`, on every push to `main`, every pull request, and manually:

1. `./gradlew testDebugUnitTest`
2. `./gradlew lintDebug lintRelease`
3. `./gradlew assembleDebug`
4. `./gradlew bundleRelease`

APKs, the AAB, lint reports and test reports are uploaded as artifacts on every run, pass or fail.
The parity fixtures are committed, so CI needs no Node toolchain.

---

**Next:** [Reference parity](Reference-Parity.md) · [Build system](Build-System.md) · [Troubleshooting](Troubleshooting.md)
