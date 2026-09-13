# Game state

[← Documentation home](Home.md)

`domain/model/GameState.kt`. One immutable `data class` holding everything a playthrough is.
Everything the simulation reads or writes lives here, and everything here is
[serialized into the save](Save-System.md).

```kotlin
val next = simulateStep(state, dt, prestige).state   // never mutates `state`
```

Immutability is what makes the engine testable without a device, lets the UI diff cheaply
between frames, and makes "what did eight hours do?" answerable by subtracting two states.

## Fields

### Identity and time

| Field | Type | Notes |
| :-- | :-- | :-- |
| `saveVersion` | `Int` | Defaults to `SAVE_VERSION` (currently **4**). See [Save migrations](Save-Migrations.md). |
| `runNumber` | `Int` | 0 = "EARTH", 1 = "EARTH 1", … Incremented by `startNewRun`. |
| `runStartedAt` | `Long` | Wall-clock ms. Drives run duration and the prestige speed term. |
| `lastTickAt` | `Long` | Wall-clock ms of the last simulated tick. **The whole offline mechanism is this field.** |
| `createdAt` | `Long` | First ever launch. Never reset. |
| `gameAgeSeconds` | `Double` | **Simulated** seconds: how old *this Earth* is. See below. |

#### `gameAgeSeconds`

The planet's own age, not the player's time at the controls — the two clocks are laid out in
[Atmospheric half-life](Atmospheric-Half-Life.md#the-two-clocks). It is advanced by
`simulateStep` and by nothing else, at `GAME_SECONDS_PER_REAL_SECOND` (86,400 — one simulated
day) per real second of simulation, which gives it three properties worth relying on:

- It counts time the world was **actually simulated for**. It does not move while the app merely
  exists in the background, and it never reads the install date, the device uptime or the wall
  clock directly.
- Because `simulateStep` is the single path, [offline catch-up](Offline-Progression.md) ages the
  planet for free and cannot drift from a live session. An absence longer than the offline cap
  ages it by the cap, not by the absence.
- It is the clock every gas half-life decays against, so it is load-bearing rather than
  decorative: a wrong age is a wrong atmosphere.

A [prestige reset](Prestige-System.md) returns it to zero.
`LifetimeStats.totalSimulatedSeconds` keeps the running total across every Earth and is never
reset. Shown in the header as `AGE`, on Home as "Earth Age", and on Statistics as both.

### Economy

| Field | Type | Notes |
| :-- | :-- | :-- |
| `resources` | `ResourceAmounts` | Six [`GameDecimal`](GameDecimal.md) balances, array-backed by `ResourceId.ordinal`. |
| `techOwned` | `Map<String, Int>` | Owned count per technology id. Absent and zero mean the same thing; the decoder drops any entry below 1. |

### Atmosphere and climate

| Field | Type | Notes |
| :-- | :-- | :-- |
| `atmosphere` | `AtmosphereState` (= `GasAmounts`) | Concentration **above the pre-industrial baseline**, in each gas's native unit. Zero at the start of every run. |
| `seaLevelRiseMeters` | `Double` | Accumulated. |
| `temperatureAnomalyC` | `Double` | Cached derived value, recomputed every tick. |
| `previousTemperatureAnomalyC` | `Double` | Last tick's anomaly. Drives the [water-vapour feedback](Climate-Model.md#water-vapour), which is one step behind by design. |
| `forcing` | `ForcingBreakdown` | Per-gas W/m² plus the total. Cached. |
| `habitability` | `HabitabilityResult` | The five factors and their product. Cached. `fraction <= 0.0001` ends the run. |
| `oceanPh` | `Double` | Cached. Starts at 8.1. |

> The four cached fields are derived, not authoritative — `simulateStep` overwrites all of them
> every tick from `atmosphere`. They live on state so the UI can read them without recomputing,
> and they are persisted so the first frame after a load is correct before the first tick lands.

### Progress and content

| Field | Type | Notes |
| :-- | :-- | :-- |
| `runStats` | `RunStats` | Per-run peaks: forcing, temperature, CO₂ ppm, gas production rate, and total gas produced. Reset every run; feeds the [prestige payout](Prestige-System.md). |
| `lifetimeStats` | `LifetimeStats` | Never reset. Play time, **total simulated time**, resets, records, fastest/longest run, technologies purchased, total Earth Points earned. |
| `prestige` | `PrestigeState` | `earthPoints` and `upgradesOwned`. Survives every reset. |
| `achievementsUnlocked` | `Map<String, Boolean>` | Only `true` entries are stored. Permanent. |
| `challenges` | `ChallengeState` | `activeId` plus the permanent `completed` map. |
| `activeEvents` | `List<ActiveEvent>` | Running [random events](Random-Events.md) with their wall-clock windows. |
| `milestonesTriggered` | `Map<String, Boolean>` | Per-run dedupe for headlines. |
| `newsFeed` | `List<NewsItem>` | Newest-first, capped at `NEWS_FEED_LIMIT` = **40**, so a week-long run cannot grow the save without bound. |
| `settings` | `Settings` | Number format, sound, music, vibration, reduced animations, theme, confirm-reset, offline-progress. |
| `tutorial` | `TutorialState` | `step` (0–11), `completed`, `skipped`. |
| `collapsed` | `Boolean` | Latched once habitability bottoms out. Gates the Reset button. |

## What survives a reset

`startNewRun(previous, now)` is the authority. Get this wrong and you either wipe permanent
progress or leak per-run state into a fresh Earth.

| Carried over | Reset |
| :-- | :-- |
| `prestige` (points **and** upgrades) | `resources` |
| `achievementsUnlocked` | `atmosphere` |
| `challenges.completed` | `seaLevelRiseMeters`, `previousTemperatureAnomalyC` |
| `lifetimeStats` | `techOwned` → back to `STARTING_TECH_IDS` |
| `settings` | `temperatureAnomalyC`, `forcing`, `habitability`, `oceanPh` |
| `tutorial` | `runStats` |
| `createdAt` | `activeEvents`, `milestonesTriggered`, `newsFeed` |
| | `storms` → empty, `stormSeed` → a new one for the new Earth |
| `lifetimeStats.totalSimulatedSeconds` | `gameAgeSeconds` → 0 (a new planet starts at age zero) |
| | `collapsed` → false |
| | `runNumber` → +1, `runStartedAt`/`lastTickAt` → now |

`challenges.activeId` is *not* explicitly cleared, because a challenge run starts with
`startNewRun` and then arms it — see `GameLoop.startChallenge`.

`GameLoopTest.resetting a dead Earth banks points and preserves exactly what should carry`
asserts the whole table.

## The weather

```kotlin
val storms: StormField = StormField.EMPTY,
val stormSeed: Long = 0L,
```

`StormField` holds the live storms plus the two counters that make their timeline
reproducible — the whole-steps-elapsed index the per-step random draw is anchored
to, and the simulated seconds not yet consumed by a whole step. `stormSeed` is
the Earth's own weather seed, fixed when it begins and never changed while it
runs, so the same planet always gets the same storms through a save, a reload and
an absence.

Both are advanced in exactly one place, `advanceStormsFor` in
`domain/engine/StormEngine.kt`, which the live tick and the offline catch-up both
go through. Neither is ever written by the renderer. See
[Storm system](Storm-System.md).

## Starting technologies

```kotlin
val STARTING_TECH_IDS = listOf("natural_fire")
```

Every purchase is denominated in resources that generators produce, so the player has to be
handed one running generator or the economy can never start. Natural Fire is it — a lightning
strike, not an invention, and the game's only unconditional source of Energy.

`resetEarth` then layers on the prestige "starting" bonuses: `startingTechIds` (granted at 1),
`startingGenerators` (extra owned units), and `startingResources` (a flat balance).

## The amount containers

`GasAmounts`, `GasDoubles` and `ResourceAmounts` in `domain/model/Amounts.kt` are immutable
classes backed by flat arrays indexed by enum ordinal, with mutable `Builder`s.

The reference implementation used `Record<GasId, Decimal>` maps, which is idiomatic in
TypeScript. Here that would mean a hash lookup for every gas of every owned generator on every
tick — the hot loop of the whole game — plus a fresh map allocation per step. An array of six
slots costs a bounds check, and a builder lets one tick mutate a scratch copy and freeze it
once.

```kotlin
val next = state.resources.toBuilder().apply {
    add(ResourceId.ENERGY, gained)
}.build()
```

`build()` copies, so a builder cannot alias a published value.

## Adding a field

1. Add it to `GameState` (or a nested class) **with a default**.
2. Decide whether it resets: add it to `startNewRun` if it does.
3. Encode and decode it in `SaveSerialization`. A missing field must fall back to the default,
   never throw — see [Save system](Save-System.md#robustness).
4. If an existing save needs fixing up, bump `SAVE_VERSION` and add a step to the chain — see
   [Save migrations](Save-Migrations.md).
5. Add a round-trip case to `SaveMigrationTest`.
6. Update this page and [Save system](Save-System.md).

Most new fields need **no migration**: the decoder's default handles an older save that simply
lacks the key. A migration is only for a field whose *old* value is wrong under the new rules.

---

**Next:** [Save system](Save-System.md) · [Simulation](Simulation.md) · [GameDecimal](GameDecimal.md)
