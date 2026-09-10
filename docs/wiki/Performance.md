# Performance

[← Documentation home](Home.md)

Three levels: the simulation, persistence, and the UI. This page records what was actually measured
and how to measure it again — **not** estimates.

## Simulation

### What runs, and how often

Four times a second while foregrounded:

```
computeEffectiveMultipliers   walks ALL_TECHNOLOGIES (99), skipping unowned
computeProductionRates        walks ALL_TECHNOLOGIES (99), skipping unowned and non-generators
integrateGasConcentration     × 6 gases
computeForcing                × 6 gases
computeHabitability           5 factors
applyBookkeeping              up to 8 challenge checks + 39 milestones + 32 achievements
computeDerived                repeats the two tree walks for the UI
```

**Everything is O(n) in the number of technologies, gases, milestones and achievements.** There is
no nested walk anywhere in the tick path, and no accidental O(n²): the two places that could have
been quadratic are both closed-form —

- **Buy-max** inverts a geometric series with a logarithm rather than looping units, so buying 5,000
  copies is the same cost as buying one. Bounded to at most 4 exact corrections for float error.
- **Ownership thresholds crossed** are computed arithmetically (`reached(after) − reached(before)`)
  rather than by walking the units, since a single buy-max can land thousands at once.

Choice-group membership is precomputed into a map at class-init, so an availability check is a hash
lookup rather than a tree filter — and it is called once per visible card.

### Allocation

The hot path allocates three builders and the immutable containers they produce. The per-gas and
per-resource containers are **flat arrays indexed by enum ordinal**, not hash maps:

> The reference implementation used `Record<GasId, Decimal>`, which is idiomatic in TypeScript. Here
> that would mean a hash lookup for every gas of every owned generator on every tick — the hot loop
> of the whole game — plus a fresh map allocation per step. An array of six slots costs a bounds
> check.

`GameDecimal` is a small immutable object, so arithmetic does allocate. That is the cost of the
type; the alternative (mutable accumulators) would make the engine's purity — and therefore its
testability and the offline guarantee — impossible. At 99 technologies × a handful of gases and
resources, a tick is a few hundred short-lived objects, which is nothing to a generational collector.

`checkAchievements` allocates its result list **lazily**: on the overwhelming majority of ticks
nothing new unlocks, and 32 predicates evaluating to false should cost nothing.

### Threading

The tick runs on `Dispatchers.Default`, **never the main thread** — a late-game step does a few
thousand arbitrary-precision operations, and at four ticks a second on the main thread that is a
dropped frame every 250 ms. The catch-up on return from the background runs there too.

### The offline win

This is the largest performance decision in the codebase and it is architectural, not micro:

**An 8-hour absence is one `simulateStep` call, not 115,200.** Because the gas integration is
[closed-form](Climate-Model.md#gas-integration), a single call with `dt = 28800` is *exactly* equal
to the loop — so the loop is not an optimisation target, it simply does not exist. A naive stepped
implementation would take seconds of CPU on resume; this takes microseconds.

## Persistence

| | |
| :-- | :-- |
| Autosave | every 15 s while foregrounded |
| Save size | a few kB — one JSON object, with `newsFeed` capped at 40 entries so a long run cannot grow it without bound |
| Thread | `Dispatchers.IO`, inside the repository. The main thread never blocks on disk. |
| Atomicity | DataStore's transactional write; both slots in one `edit` |

**Both loops stop when the app backgrounds.** Before the audit the autosave kept running, rewriting
an unchanging save every fifteen seconds for as long as the app sat in the background. There was
nothing to write — the tick was already stopped — so it was pure wasted I/O and wakeups.

## UI

### Recomposition

The screen genuinely recomposes four times a second, because most displayed numbers change every
tick. That is inherent.

What was addressed is composables being unable to *skip* when their inputs are unchanged. Measured
with the Compose compiler's own reports:

```bash
./gradlew assembleDebug -Pearth.composeReports=true
cat app/build/compose-reports/debug/app-module.json
```

| | Before | After |
| :-- | --: | --: |
| Effectively stable classes | 43 / 80 | **68 / 80** |
| Arguments compared by value | 3,202 | **3,230** |
| Skippable composables | 152 / 201 | 152 / 201 |

The skippable count is **unchanged**, because strong skipping is on by default in Kotlin 2.x and
already made them skippable using reference identity. The gain is narrower and honest: 25 more
classes and 28 more arguments are now compared by **value**, so an equal-valued-but-newly-allocated
argument can actually skip.

See [UI architecture](UI-Architecture.md#recomposition-and-stability) for how it is declared without
putting Compose into `domain/`.

### Other UI discipline

- `LazyColumn` with stable `key = { it.id }` for every long list, so scrolling reuses slots.
- The expensive per-card computation (`maxAffordableQuantity`, which is a logarithm plus up to four
  exact geometric-series evaluations) is wrapped in `remember` keyed on everything that affects it.
- `DerivedState` is computed **once per state change** in the ViewModel, not per composable — the
  Production screen would otherwise re-fold the tech tree for all 68 cards.
- The banner `AdView` is `remember`ed and destroyed in `onDispose`, so it is neither rebuilt per
  frame nor leaked.
- No blocking work in a composable. No `runBlocking` anywhere in `main/`.

### Late-game number formatting

Formatting is on the hot path of every frame. `formatCompact` does one `GameDecimal` division and
one `BigDecimal` rounding per value — the `BigDecimal` is the price of matching ECMAScript's exact
rounding rules (see [GameDecimal](GameDecimal.md)) and is a few hundred nanoseconds. At a few dozen
visible numbers that is well inside a frame.

Crucially, formatting a value near 1e400 costs **the same** as formatting one near 10: the mantissa
is always in `[1, 10)`, so there is no digit expansion. `FULL` notation falls back to scientific
above 1e100 precisely because full digit expansion would be both slow and unreadable.

## Startup

- No work on the main thread before `setContent` beyond `enableEdgeToEdge()` and registering one
  lifecycle observer.
- The save load is a suspending call in `viewModelScope`; the UI shows "Loading Earth…" until
  `loaded` flips.
- `MobileAds.initialize` is deferred: the manifest sets `OPTIMIZE_INITIALIZATION` and
  `OPTIMIZE_AD_LOADING`, and the consent flow runs in a `LaunchedEffect` **after** the first
  composition, so the SDK is never on the launch path.
- The platform theme paints the game's own background colour, so there is no white flash before the
  first composed frame.
- No `BuildConfig` generation, no reflection, no annotation processors, no DI graph to build.

## Measuring it yourself

| | |
| :-- | :-- |
| Recomposition | `./gradlew assembleDebug -Pearth.composeReports=true`, then `app/build/compose-reports/` |
| Frame timing | Android Studio's Layout Inspector / Compose recomposition counts on a real device |
| Simulation cost | Add a JMH-style micro-benchmark, or time `simulateStep` in a JVM test — it is pure, so it needs no device |
| Allocation | Android Studio memory profiler; watch the tick's steady-state allocation rate |
| APK size | `./gradlew assembleRelease` and check `app/build/outputs/apk/release/` |

> **Not yet measured, and honestly so:** frame timings, jank statistics, allocation rates and startup
> time on real hardware. This environment has no KVM, so no emulator can boot and no device is
> attached. The reasoning above is architectural and the Compose figures are from the compiler, but
> nobody has watched this app run at 60 Hz. See [the audit](../CODEBASE_AUDIT.md).

---

**Next:** [Simulation](Simulation.md) · [UI architecture](UI-Architecture.md) · [Build system](Build-System.md)
