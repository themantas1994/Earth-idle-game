# Atmospheric half-life

[← Documentation home](Home.md)

> [!CAUTION]
> **THIS IS A GAMEPLAY SIMULATION, NOT A SCIENTIFIC ATMOSPHERIC MODEL.**
>
> The *shape* is real — greenhouse gases genuinely do leave the atmosphere as a first-order
> decay, and the half-lives here are within sight of published figures — but the clock they run
> on is a game clock, the masses behind them are scaled by orders of magnitude, and the whole
> thing is tuned so that a multi-day run behaves coherently from one campfire to 10⁴⁰⁰ kg of
> methane. Nothing here predicts anything. Do not cite it.

Files: `domain/engine/GameTime.kt`, `domain/model/Gases.kt`, `domain/climate/Climate.kt`.

## What a half-life is here

Every gas the player can accumulate has a **half-life**: the span of simulated time in which an
undisturbed stock of it falls to half. The atmosphere obeys the standard law

```
C(t) = C₀ × 2^(−t / H)
```

Continuous, not a once-a-year subtraction — half of a half-life leaves `1/√2` of the stock, not
half of a half.

### The example

CO₂ has a half-life of **120 years**. Starting from an excess of 1,000 ppm with nothing emitting
and nothing removing:

| Simulated time | CO₂ excess |
| --: | --: |
| 0 | 1,000 |
| 60 years | 707.11 |
| **120 years** | **500** |
| **240 years** | **250** |
| **360 years** | **125** |

`AtmosphericHalfLifeTest` asserts exactly this, through the engine's own integrator rather than
through a private copy of the formula.

## What decays — and what must not

`GameState.atmosphere` holds, for each gas, the concentration **above its pre-industrial
baseline**, in the gas's native unit. The baseline itself is not simulated at all: it is assumed
to be a natural steady state, equal natural emission and removal, and it is added back only when
a concentration is displayed or a forcing is computed (`gasDisplayConcentration`,
`computeForcing`).

That split is what makes this safe. The decay operates on the *anthropogenic excess* and nothing
else, so **the Earth's baseline atmosphere can never drain away** however long a run lasts: CO₂
tends toward 280 ppm, not toward zero, and a gas at baseline has nothing to decay. The clamp at
zero is on the excess, so the displayed concentration is floored at the baseline rather than at
nothing.

| Gas | Baseline | What decays | Produced by | Engineered removal |
| :-- | --: | :-- | :-- | :-- |
| CO₂ | 280 ppm | excess ppm | most generators | capture, reforestation, terraforming |
| CH₄ | 700 ppb | excess ppb | agriculture, fossil fuels | — |
| N₂O | 270 ppb | excess ppb | agriculture, chemistry | — |
| O₃ | 300 DU | excess DU | transportation, globalization | CFC technologies |
| Fluorinated | 0 ppt | all of it | chemistry, digital | — |
| H₂O | 0 ppm | *relaxes to a feedback target, see below* | nothing — never emitted | — |

Fluorinated gases have a zero baseline, so in their case "the excess" and "the concentration" are
the same number — which is correct, since they are synthetic and none of them existed before
industry.

## The two clocks

This is the part that is easy to get wrong, so it is stated once, in `GameTime.kt`, and nowhere
else.

| Clock | Unit | What runs on it |
| :-- | :-- | :-- |
| **Real** | seconds of wall clock | ticks, offline gaps, event durations, autosave, production rates (everything the UI prints as `/s`), the prestige speed bonus, `totalPlayTimeSeconds` |
| **Simulated** | seconds of the Earth's own age | `GameState.gameAgeSeconds`, `LifetimeStats.totalSimulatedSeconds`, and **gas half-life decay** |

```kotlin
const val GAME_SECONDS_PER_YEAR       = 365.25 * 24 * 3600   // 31,557,600
const val GAME_SECONDS_PER_REAL_SECOND = 24.0 * 3600         // one real second = one simulated day
const val REAL_SECONDS_PER_GAME_YEAR   = 365.25              // ≈ 6 minutes of play
```

**One real second of simulation is one simulated day.** A simulated year passes every 365.25 real
seconds; a three-day run ages the planet by roughly 700 years.

### Where the ratio comes from

It is not invented. The engine already had an implied mapping, baked into the one rate constant
quoted per *year*: `HABITABILITY.seaLevelPerDegreeYear = 120` metres per degree per year, against
a real-world figure near 0.001 (0.1 m per degree-century). That is a factor of about 10⁵ — the
engine was already treating a real second as worth on the order of a simulated day. `GameTime.kt`
states the mapping rather than leaving it baked into a scaled constant, and rounds it to the
obvious figure.

The practical consequence is the one that makes the mechanic felt rather than theoretical:

> **CO₂'s 120-year half-life is 12.2 real hours** — about one offline cap. Leave an untended
> Earth overnight and roughly half its excess CO₂ is gone.

| Gas | Half-life (simulated years) | …in real time |
| :-- | --: | --: |
| CO₂ | 120 | 12.2 hours |
| CH₄ | 12 | 1.2 hours |
| N₂O | 114 | 11.6 hours |
| O₃ | 0.06 | 22 seconds |
| Fluorinated | 3,200 | 13.5 days |
| H₂O | — | *not a half-life, see below* |

## Integration method

The half-life is converted into a first-order decay constant **once**, in
`naturalRemovalRateConstant`:

```kotlin
λ = ln 2 / (halfLifeYears × REAL_SECONDS_PER_GAME_YEAR)     // per real second
```

Dividing by real-seconds-per-simulated-year rather than by a year of real seconds is the entire
conversion between the clocks. Nothing downstream has to know which clock it is on.

The step itself is then the **exact analytic solution** to the production/decay equation, not a
Euler approximation and emphatically not a linear `C × Δt / H` subtraction:

```
dE/dt = P − k·E                      k = λ × sinkEfficiency

E(t + Δt) = E(t)·e^(−kΔt) + (P/k)·(1 − e^(−kΔt))
```

With `P = 0` this is exactly `E × 2^(−t/H)`. With `P > 0` it is the same curve approached from
whichever side of the equilibrium `P/k` the gas currently sits on.

Being closed-form is load-bearing, not an optimisation: it is why a 250 ms tick, a one-hour catch-up
and a twelve-hour offline settlement all produce the same numbers for the same elapsed time, and
therefore why [offline progress](Offline-Progression.md) is one calculation instead of a loop.
`SimulationParityTest` and `AtmosphericHalfLifeTest` both assert it directly.

## Production against decay

Every gas has an equilibrium at `P/k`, and the whole balance of the atmosphere is which side of it
the player is on:

| Production | What happens |
| :-- | :-- |
| none | the stock halves every half-life, all the way down |
| below `k·E` | the stock declines toward the new, lower equilibrium |
| exactly `k·E` | the stock holds steady — emissions replace precisely what decays |
| above `k·E` | the stock climbs toward the higher equilibrium |

An idle game's production grows exponentially, which is what keeps this from being a wall: early
on, decay meaningfully suppresses a thin trickle of emissions, and by the late game production
outruns λ by orders of magnitude and the atmosphere accumulates as before. What the decay removes
is the assumption that gas simply piles up forever regardless of whether anything is still
emitting.

## Order of operations

Per gas, inside [`simulateStep`](Simulation.md#order-of-operations) — unchanged by this feature
except for what `k` now means:

1. Fold every multiplier (prestige, technologies, events, challenge rewards).
2. Sum production and engineered removal from owned generators, in kg/s.
3. Compute **sink efficiency** from the *pre-step* temperature.
4. Convert kg/s to native-unit/s by dividing by `massPerUnit`.
5. Integrate: natural half-life decay, continuous production and engineered removal all resolved
   **together** in the single closed-form step above — engineered removal as a flat subtraction
   from the production term, sink efficiency as a multiplier on `k`.
6. Clamp at zero. A gas can never be driven negative by engineering.
7. Advance `gameAgeSeconds` by `dt × GAME_SECONDS_PER_REAL_SECOND`.
8. Recompute forcing → temperature → sea level → ocean pH → habitability from the new atmosphere.

Decay is *not* a separate pass applied before or after production. Applying them in sequence would
make the result depend on step size and break the live/offline equivalence the whole engine rests
on; the analytic solution resolves both at once, which is why the order within step 5 does not
exist as a question.

Every existing technology effect still applies, and applies where it always did: production
multipliers and engineered removal feed steps 2 and 5, and nothing bypasses them. No technology
modifies a half-life — there is no mechanism for it to, by design.

## The two documented exceptions

Not every gas is a stock, and the implementation is deliberately not identical for all six.

### Water vapour keeps the real clock

H₂O has `decaysOnSimulatedClock = false`. It is not an accumulating stock the player builds up: it
is a diagnostic feedback relaxing toward an equilibrium that warming sets, and its `halfLifeYears`
is read as the mean residence time governing how fast it tracks that equilibrium — on the real
clock, at exactly the rate it has always had.

This is a balance decision and worth being explicit about. Sink efficiency multiplies `k`, and the
H₂O equilibrium target is fed in as `equilibrium × k_raw`, so the effective target becomes
`target / sinkEfficiency` — [a known quirk preserved for parity](Climate-Model.md#sink-efficiency).
Today that quirk is harmless because H₂O never gets close to equilibrium inside a run. Put the
feedback on the simulated clock and it reaches equilibrium in seconds, the quirk amplifies
late-game warming by up to several times, and two endgame technologies stop being reachable in a
completed run — measured, not guessed. Water vapour therefore stays where it was.

### Sea level keeps the real clock

`HABITABILITY.seaLevelPerDegreeYear` is the constant the simulated clock was *derived* from. It
was pre-scaled by about 10⁵ precisely so a run's worth of real seconds produces a meaningful rise.
Running it on simulated years while leaving the constant alone would apply that scaling twice;
rescaling the constant to compensate would be a change with no behavioural effect. So the year in
`integrateSeaLevelRise` is a real one, and the only thing it shares with the simulated calendar is
the length of a year.

## Numerical considerations

- Concentrations are [`GameDecimal`](GameDecimal.md), so a late-game stock of 10³⁰⁰ ppm is a
  number rather than an infinity.
- `e^(−kΔt)` is a `Double`, which is correct here: it is a bounded factor in `(0, 1]`, and `kΔt`
  large enough to underflow it to zero is a gas that has genuinely decayed away.
- The result is clamped at zero, so no combination of engineered removal, absurd `Δt` or
  floating-point noise can produce a negative concentration.
- `Δt ≤ 0` returns the input untouched. Time never runs backwards, and no gas is ever un-emitted.
- Half-lives span five orders of magnitude (0.06 to 3,200 years) and the same closed form handles
  all of them, because it is exact rather than a small-step approximation.
- `AtmosphericHalfLifeTest.extreme spans and magnitudes stay finite and non-negative` sweeps
  spans from 10⁻⁹ to 10³⁰ years against every half-life and asserts no NaN, no infinity and
  nothing below zero.

## Where the age comes from

`gameAgeSeconds` is advanced by [`simulateStep`](Simulation.md) and by nothing else, so it counts
time the world was actually simulated for. It does not advance while the app merely exists in the
background, it does not read the install date or device uptime, and an absence longer than the
[offline cap](Offline-Progression.md) ages the planet by the cap rather than by the absence. A
[prestige reset](Prestige-System.md) returns it to zero; `LifetimeStats.totalSimulatedSeconds`
keeps the running total across every Earth. See [Game state](Game-State.md#gameageseconds).

---

**Next:** [Climate model](Climate-Model.md) · [Simulation](Simulation.md) · [Offline progression](Offline-Progression.md) · [Game state](Game-State.md)
