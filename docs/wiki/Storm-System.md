# Storm system

[← Documentation home](Home.md)

Storms are a **gameplay system**, not a visual effect. They are simulation
entities with a position, an intensity, a lifetime and real production
penalties; they live in the domain layer, they are saved, and they advance
whether or not anything is being drawn.

> **The weather and storm system is a gameplay simulation, not a scientific
> weather forecast.** Every number below was chosen for how a run feels — when
> storms start appearing, how often, and how much they are allowed to cost — not
> for meteorological realism. There is no pressure field, no Coriolis term and no
> moisture budget anywhere in it.

---

## Where storms sit in the engine

Exactly where random events sit, and for the same reason.

```
GameLoop.advance / computeOfflineProgress
  → advance the storm timeline
  → fold the resulting penalties into extraMultipliers
  → simulateStep
      → GameState.storms
        → EnvironmentalVisualizationState
          → the globe
```

[`simulateStep`](Game-Engine.md) is the reference-parity core — gases, resources,
temperature, habitability — and this feature does not touch it. The numbers it
produces still match the numbers the original engine produced, which is what the
[parity suite](Reference-Parity.md) asserts. Storms are a layer above it, folded
in as a `MultiplierContribution` by the caller, exactly like an active event.

**Which storms are charged for a step:** the ones present at the *start* of it,
never the ones that formed during it — matching how event multipliers are read
from the pre-tick event list. On a 250 ms tick the distinction is invisible.
Across an absence it is the whole basis of the offline settlement, below.

---

## The files

| File | What is in it |
| :-- | :-- |
| `domain/storms/StormTypes.kt` | `Storm`, `StormType`, `StormSeverity`, the name pool |
| `domain/storms/StormSimulation.kt` | Formation, movement, intensity, dissipation, the RNG |
| `domain/storms/StormEffects.kt` | Penalties, stacking, caps, the offline average |
| `domain/engine/StormEngine.kt` | The bridge to `GameState` |
| `domain/engine/Constants.kt` → `STORMS` | Every tuning number |

The storm simulation itself knows nothing about `GameState`: it takes a climate,
a seed and a `dt`, and returns storms. That is what makes it testable on its own.

---

## The storm entity

```kotlin
data class Storm(
    val id: String,                 // "storm-<formation step>"
    val type: StormType,
    val name: String,               // "Iris"
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val intensity: Double,          // 0..1, now
    val targetIntensity: Double,    // 0..1, the peak it is heading for
    val peakIntensity: Double,      // 0..1, the highest it has reached
    val ageSeconds: Double,
    val lifetimeSeconds: Double,
    val headingDeg: Double,
    val speedDegPerSecond: Double,
    val formedAtStep: Long,
)
```

Derived, not stored: `remainingSeconds`, `severity`, `radiusDeg`, `displayName`,
`accessibleSummary`.

---

## The four categories

Deliberately few. Each has to be distinguishable on a globe the size of a thumb
and explainable in one line on a card, and a dozen categories would be neither.

| Category | Needs | Peak intensity | Lifetime | Forms at | Penalties at full intensity |
| :-- | :-- | :-- | :-- | :-- | :-- |
| 🌀 **Tropical Storm** | +0.6 °C, 52% RH | 0.18–0.45 | 3–7 min | 5–24° | all −4%, Electricity −6% |
| 🌪️ **Hurricane** | +2.5 °C, 58% RH | 0.42–0.72 | 5–11 min | 8–32° | all −7%, Electricity −12%, Construction −9%, Transportation −8% |
| ⚡ **Severe Atmospheric Storm** | +5 °C, 60% RH | 0.35–0.68 | 2.5–6 min | 18–58° | all −5%, Electricity −10%, Digital −14% |
| 🌩️ **Superstorm** | +9 °C, 66% RH | 0.70–1.0 | 7–15 min | 5–38° | all −11%, Electricity −16%, Industry −13%, Construction −13%, Transportation −11% |

The Severe Atmospheric Storm is the one that is not tropical: it forms over the
mid-latitudes, where the grid and the datacentres are.

---

## Formation

Storm formation depends on the game state, never on chance alone.

```
thermal  = ln(1 + max(0, T − 0.6)) / ln(1 + 40)          // 0 below the floor
moisture = clamp01((humidity − 0.52) / 0.22)
pressure = thermal × (0.4 + 0.6 × moisture)
chance   = 0.045 × pressure^1.6      per 5-second step
```

- `T` is `GameState.temperatureAnomalyC`.
- `humidity` is `relativeHumidity(atmosphere[H2O])` — the climate model's own
  water-vapour feedback term, not a new variable.
- **Below +0.6 °C of warming the chance is exactly zero.** A player who has just
  lit their first fire cannot get a storm however the dice fall.

The thermal term is read **logarithmically**. This game's temperature range is not
a climate scientist's: a run opens near 0 °C of anomaly and an endgame planet
reaches six figures. A linear ramp wide enough to matter late would leave the
whole interesting middle of the run pinned at one end of it.

At most one storm forms per step, and never more than `MAX_ACTIVE` (6) run at
once.

### Measured frequency

From `StormBalanceTest`, which simulates hours of game time across several seeds
and prints this table on every run:

| Stage | Warming | Storms/hour | Concurrent | Sky busy | Mean drag | Worst moment |
| :-- | --: | --: | --: | --: | --: | --: |
| early game | +0.3 °C | 0.00 | 0.00 | 0% | 0.0% | 0.0% |
| first warming | +1.5 °C | 1.16 | 0.10 | 9% | 0.1% | 1.3% |
| moderate | +4 °C | 7.38 | 0.76 | 56% | 1.0% | 6.5% |
| high | +10 °C | 17.28 | 1.90 | 87% | 4.6% | 16.6% |
| severe | +25 °C | 25.84 | 2.82 | 96% | 6.8% | 19.2% |
| extreme | +120 °C | 30.63 | 3.31 | 98% | 8.0% | 20.4% |

Storms are a standing environmental pressure on a warmed planet and a real cost
in the endgame. They never dominate the game, and they never make progress
impossible.

---

## Movement

Each step a storm is steered by `windAt(latitude, windStrength)` — the **same**
wind field the [wind overlay](Environmental-Visualization.md#the-wind-field)
draws — plus a poleward bias. The classic tropical track, west with the trades
and then recurving poleward, falls out of the two together.

Longitude is corrected by `1/cos(latitude)` (floored, so it stays finite near the
pole) and wraps at the date line. A storm dragged past 78° of latitude has run out
of ocean and is torn apart.

Speed scales with the storm's own drift rate, its intensity, and the planet's
wind strength.

---

## Intensity

```
intensity = targetIntensity × profile(age / lifetime) × sustain(pressure)
```

`profile` is a smoothstepped spin-up to 28% of life, a plateau to 62%, then a
wind-down to nothing. `sustain` is `0.75 + 0.25 × pressure`, so a storm that
formed under a stormier sky than the one it now sits under winds down early —
improving the climate visibly calms the weather rather than only delaying the
next storm.

Intensity drives the visual scale, the particle density, the spin rate, the
lightning, **and** the gameplay penalty. `StormSeverity` names the band in words:
`Forming` under 0.25, `Moderate` under 0.5, `Severe` under 0.78, `Extreme` above.

---

## Gameplay effects

Storms reduce production while they run, and nothing at all once they are gone.
No destroyed buildings, no lost resources, no progress to earn back — a
deliberate fit with the rest of the game, where prices never rise and no
multiplier is ever taken away.

Two rules stop them from becoming a spiral:

1. **Diminishing returns.** Within one penalty channel the storms are sorted
   strongest-first and each subsequent one counts for `STACKING_FALLOFF` (0.55)
   of the one before. Six storms are worse than one, nowhere near six times
   worse.
2. **A hard cap.** Whatever is running, the global penalty never exceeds
   `MAX_GLOBAL_PENALTY` (35%) and no branch exceeds `MAX_BRANCH_PENALTY` (45%).

Ten simultaneous superstorms — more than the game can produce — come to about
24% globally, below the cap, and the worst possible combined drag still leaves
more than a third of production running.

All of it is centralised in `computeStormEffects`, and surfaces as a
`MultiplierContribution` folded in alongside random events. Storms only ever
scale **production**: they never touch research directly, never touch emissions,
never touch the per-gas curves, and never touch habitability.

### What the player sees

The storm card prints the numbers, never implying them from the size of the
spiral:

```
🌪️ Hurricane Iris
Severe · 18°N 62°W
Intensity            61% — Severe
Duration remaining   4m 32s

CURRENT EFFECTS
All production       −4%
Electricity          −7%
Construction         −5%
Transportation       −4%
```

The Active Phenomena strip on Home shows the **combined** drag after stacking and
caps, which answers a different question: not "what is Iris doing" but "why is my
output down".

---

## Technology interaction

The technology tree was inspected for nodes that logically interact with storms.
Its shape is a linear historical progression — Primitive → Agriculture →
Industry → Electricity → … → Endgame — with no disaster-preparedness,
forecasting or resilience branch anywhere in it.

**No new technology branch was created for this feature.** Adding one would have
been a large content change riding along on a rendering feature, and it would
have needed its own balance pass against the cost curves. Storms interact with
the existing systems instead:

- The **branch penalties** are the interaction. A storm hurts Electricity,
  Construction, Transportation, Industry and Digital differently, so a player's
  branch mix decides how exposed they are.
- The **global production multiplier** is what a storm bites into, so every
  multiplier technology already owned is a hedge against weather in proportion
  to its size.
- Storms respond directly to the **climate**, so carbon-removal technologies
  (Direct Air Capture, reforestation, the terraforming engines) reduce storm
  frequency and severity through the temperature they lower — the one real lever
  the player already has.

### Documented future expansion

Natural, and deliberately not built yet:

| Direction | What it would be |
| :-- | :-- |
| Weather forecasting | A technology that shows storm risk ahead of formation, and previews where a storm will track |
| Infrastructure resilience | A multiplier that reduces a storm's branch penalty rather than raising production |
| Emergency response | An active ability that weakens one storm, on a cooldown |
| Grid hardening | Electricity-branch nodes that cap the Electricity penalty specifically |
| Regional weather | Storms affecting only the generators in their footprint, once generators have locations |
| More categories | Blizzards, dust storms and heat domes, once the four here have been played with |

Each is additive. Nothing in the engine enumerates storm categories exhaustively
except the save format's id lookup, which falls back safely on an unknown one.

---

## Determinism

Storms are probabilistic but never arbitrary.

- Every draw comes from `DeterministicRandom` (SplitMix64, written out rather
  than taken from `kotlin.random` so a Kotlin upgrade cannot retune it) seeded by
  `(run seed, step index)`.
- The run seed is `deriveStormSeed(createdAt, runNumber)`, fixed when the Earth
  begins and never changed while it runs.
- Storms advance in whole **5-second steps** with a carry accumulator, never on
  whatever `dt` the caller passed.

So a live session of 250 ms ticks and a twelve-hour offline catch-up execute the
**same steps, at the same indices, against the same draws** — the storm system's
version of the property [the closed-form gas integration](Game-Engine.md) gives
the atmosphere. Reloading a save continues the same sequence. A test can assert
on a specific storm rather than on a distribution.

Nothing random happens in Compose or in the renderer. Even the wind particles are
seeded from the domain's own generator.

---

## Persistence

Written under `storms` in the save ([Save system](Save-System.md)), at
`SAVE_VERSION` 5:

```json
"storms": {
  "seed": "-2210746743261616867",
  "stepsElapsed": 603,
  "carrySeconds": 2.5,
  "active": [ { "id": "storm-412", "type": "hurricane", ... } ]
}
```

Every field of every storm is written, including `targetIntensity`,
`lifetimeSeconds` and `formedAtStep` — which the player never sees but without
which a reloaded storm would restart its life curve or change its name.

**The seed is stored as a string.** A JSON number decodes through a `Double`,
which cannot hold a 19-digit `Long` exactly, and a seed that comes back off disk a
few bits different is a reloaded Earth with different weather from the one that
was saved.

A storm whose category id this build does not recognise decodes as **absent**
rather than as a default one: it would have effects and visuals this build cannot
honour, and silently turning it into a tropical storm would be the worse lie.

### Migration

`migrateV4ToV5` starts a returning player's Earth with **clear skies**. A v4 save
records nothing about weather and there is no honest way to reconstruct what
storms a run "should" have had — the timeline depends on a seed that did not
exist and a step count that was never kept. So nothing is invented: the field
starts empty at step zero under the seed the Earth would have been given at
creation, and the next warm spell produces its first storm the way a fresh run
would. Nothing else in the save is touched; storms take a slice off production
while they run and nothing once they are gone, so an Earth arriving without any
is not owed a correction.

---

## Offline behaviour

Storms are **simulated across the elapsed time**, not frozen and not skipped.

`computeOfflineProgress` settles an absence in two passes, and the order matters:

1. The storms that were **already running** when the player left are priced,
   averaged across the window over their own intensity curves. This is the
   analytic settlement an absence gets instead of a replayed frame loop.
2. Production runs with that penalty folded in.
3. Only then does the storm timeline advance — through the same fixed steps a
   live session would have run — so a storm can form, intensify, move and
   dissipate entirely while the app was closed. The bulletins come back with it,
   and the welcome-back summary reports how many formed and how many are still
   running.

A storm that forms *during* an absence costs nothing until the player is back. It
is on the board when they return, doing exactly what the simulation says it
should be, but the time they were not there is not billed to them — the same
bargain the [random-event system](Random-Events.md) already makes.

That also means **a player who left under clear skies is settled bit-for-bit as
they were before storms existed**, which is what keeps the offline path's
reference parity intact. `StormLifecycleTest` asserts it directly.

---

## Prestige

A new Earth gets new weather. `startNewRun` clears the storm list, resets the
step counter and the carry to zero, and derives a **new seed** from the new run
number. No storm can be carried from the old Earth into the new one, and no two
Earths share a forecast.

---

## Tuning

Every number lives in `STORMS` in `domain/engine/Constants.kt`. Nothing is
hard-coded anywhere in the simulation.

| Constant | Value | What it does |
| :-- | --: | :-- |
| `STEP_SECONDS` | 5 | The fixed cadence the timeline runs on |
| `MAX_ACTIVE` | 6 | Concurrent storm ceiling |
| `MAX_FORMATION_CHANCE_PER_STEP` | 0.045 | Formation rate at full pressure |
| `FORMATION_TEMPERATURE_FLOOR_C` | 0.6 | Below this, no storms at all |
| `FORMATION_TEMPERATURE_SCALE_C` | 40 | How fast the log thermal term climbs |
| `FORMATION_HUMIDITY_FLOOR` / `_SPAN` | 0.52 / 0.22 | The moisture term |
| `MOISTURE_SHARE` | 0.6 | How much of pressure is moisture, not heat |
| `PRESSURE_EXPONENT` | 1.6 | Bends early warming gentler than late |
| `STACKING_FALLOFF` | 0.55 | Diminishing returns per further storm |
| `MAX_GLOBAL_PENALTY` | 0.35 | The cap on all production |
| `MAX_BRANCH_PENALTY` | 0.45 | The cap on any one branch |
| `DISSIPATION_LATITUDE` | 78 | Where a storm runs out of ocean |
| `SPIN_UP_FRACTION` / `DECAY_FROM_FRACTION` | 0.28 / 0.62 | The intensity profile |

---

## Tests

All of it is testable without a GPU, which is the point of the architecture as
much as of the tests.

| Suite | Covers |
| :-- | :-- |
| `StormSimulationTest` | Formation thresholds, the pressure curve, category eligibility, the active cap, determinism, step-size independence, the intensity profile, movement, dissipation, naming, bulletins |
| `StormEffectsTest` | Per-storm penalties, stacking, the caps, ordering, the offline average |
| `StormLifecycleTest` | Live ticks, offline resolution, the clear-skies parity guarantee, save/reload continuity, prestige reset, the news feed |
| `StormBalanceTest` | Long runs at six warming stages, with the table above printed on every run |
| `EnvironmentalVisualizationTest` | That every storm reaches the globe at the position and intensity the simulation gave it |
| `SaveMigrationTest` | Storm round-trip, and the seed surviving bit-for-bit |

---

**Next:** [Home screen](Home-Screen.md) · [Environmental visualization](Environmental-Visualization.md) · [Random events](Random-Events.md) · [Offline progression](Offline-Progression.md)
