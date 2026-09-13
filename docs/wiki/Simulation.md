# Simulation

[← Documentation home](Home.md)

`domain/engine/Simulation.kt`. The physics: one function, `simulateStep`, that advances the whole
world by `dt` seconds of **real** time.

```kotlin
fun simulateStep(
    state: GameState,
    dtSeconds: Double,
    prestige: PrestigeMultipliers,
    extraMultipliers: List<MultiplierContribution> = emptyList(),
): SimulationStepResult
```

It is also the only thing that ages the planet: each call adds
`dt × GAME_SECONDS_PER_REAL_SECOND` to `GameState.gameAgeSeconds`, the
[simulated calendar](Atmospheric-Half-Life.md#the-two-clocks) the atmosphere's half-lives decay
against. Production rates and `dt` stay in real seconds; the conversion happens once, in the gas
decay constant.

Pure. Deterministic. No clock, no I/O, no randomness, no Android. The same inputs always give
the same outputs — `SimulationParityTest.the simulation is deterministic` asserts it, and
`zero and negative time steps change nothing` covers the degenerate inputs.

## Step-size independence

**One call with `dt = 3600` produces the same numbers as 14,400 calls with `dt = 0.25`.**

This is the property the whole [offline design](Offline-Progression.md) rests on, and it is not
an accident of small step sizes — it is because the gas integration is
[closed-form](Climate-Model.md#gas-integration) rather than a stepped Euler approximation.
Resource accumulation is linear in `dt`, so it is trivially step-size independent too.

`SimulationParityTest.step size independence - one hour in one step equals 14400 quarter-second
ticks` asserts it directly, against a fixture the reference engine generated
(`stepIndependence.json`).

> **One documented exception: water vapour.** It is a feedback driven by the *previous* step's
> temperature, not an accumulating stock, so a single 3,600-second call sees a temperature of
> zero throughout where 14,400 quarter-second calls do not. This matches the reference exactly —
> its own step-size test likewise checks only the accumulating quantities — and nothing a player
> spends or is scored on depends on it: H₂O emits nothing, is never banked, and contributes
> forcing only through a temperature the other gases already set.

## Order of operations

Exact, and load-bearing — several steps read a value the previous one has not yet overwritten.

1. **Fold multipliers.** `computeEffectiveMultipliers(techOwned, prestige, extra)` walks every
   owned technology and every prestige upgrade once, plus any transient contributions (active
   events, challenge rewards).
2. **Sum production.** `computeProductionRates(techOwned, multipliers)` walks every owned
   *generator*, accumulating gross gas, engineered removal and resources into builders.
3. **Sink efficiency** from the **current** (pre-step) temperature anomaly.
4. **Per gas:** convert kg/s to native-unit/s by dividing by `massPerUnit`, then integrate —
   [natural half-life decay](Atmospheric-Half-Life.md), continuous production and engineered
   removal all resolved together in one closed-form step, never as a sequence of passes. H₂O
   substitutes an equilibrium target derived from the previous anomaly instead of a production
   rate. Directly-emitted gases add `gross × dt` to the run and lifetime totals.
5. **Accumulate resources:** `balance += rate × dt`.
6. **Recompute climate** from the *new* atmosphere: forcing → temperature → sea level (using the
   mean of the old and new anomaly over the interval) → ocean pH → the five habitability factors.
7. **Update peaks** (run and lifetime), `totalPlayTimeSeconds`, and the simulated clock —
   `gameAgeSeconds` and `lifetimeStats.totalSimulatedSeconds`.
8. **Latch `collapsed`** if `habitability.fraction <= 0.0001`. Once set it stays set.

Steps 3 and 4's use of the *pre-step* temperature is what keeps the model stable without having
to solve forcing and temperature simultaneously.

## `EffectiveMultipliers`

```kotlin
data class EffectiveMultipliers(
    val global: Double,                        // scales everything
    val perBranch: Map<TechBranch, Double>,    // scales one branch
    val perGas: Map<GasId, Double>,            // scales one gas
    val perResource: Map<ResourceId, Double>,  // scales one resource
    val research: Double,                      // Research income specifically
    val allGas: Double,                        // every greenhouse gas at once
)
```

Folded once per state change so per-tick maths is a lookup rather than a tree walk. Sources, all
multiplicative:

| Source | Contributes |
| :-- | :-- |
| Prestige upgrades | `global`, `research`, `allGas`, `perGas`, and the cost discount / offline cap |
| Completed challenges | The same shape, applied at a flat level of 1 |
| Owned multiplier & choice technologies | `global`, `research`, `perBranch`, `perGas`, `perResource` |
| Active random events | `global`, `research`, `allGas`, `perGas`, `perBranch` |

## One generator's output

```kotlin
private fun techScale(tech, owned, m) =
    m.global * (m.perBranch[tech.branch] ?: 1.0) * ownershipMultiplier(owned)
```

Then per gas: `perUnit × owned × scale × (perGas ?: 1) × allGas`.
Per resource: `perUnit × owned × scale × (perResource ?: 1) × (research if RESEARCH)`.
Engineered removal: `perUnit × owned × scale` — **no** gas multiplier, so a
production-boosting event cannot accidentally boost carbon capture with it.

`computeTechProductionRates(tech, owned, multipliers)` computes this for a single technology, so
the Production screen can show a building's *own* contribution rather than the global total for
the gases it happens to emit.

## Performance

`computeProductionRates` is the hottest function in the game — four times a second plus every
UI refresh — so:

- it iterates `ALL_TECHNOLOGIES` (99 entries) once and `continue`s immediately on anything not
  owned or not a generator;
- it accumulates into `GasAmounts.Builder` / `ResourceAmounts.Builder` (flat arrays indexed by
  enum ordinal) rather than materialising a per-technology result and folding it in;
- the containers themselves are arrays, not maps, so a gas lookup is a bounds check rather than
  a hash.

There is no per-tick allocation beyond the three builders and the resulting immutable containers.
See [Performance](Performance.md) for what was measured.

## `computeCivLevel`

```kotlin
fun computeCivLevel(techOwned: Map<String, Int>): Int   // Σ (tier + 1) over distinct owned techs
```

The overall progress score. Distinct technologies only — buying a thousand of one generator does
not move it. It feeds the prestige payout, challenge goals and random-event eligibility.

---

**Next:** [Climate model](Climate-Model.md) · [Economy and production](Economy-and-Production.md) · [Performance](Performance.md)
