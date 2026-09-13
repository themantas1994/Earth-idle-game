# Achievements and challenges

[← Documentation home](Home.md)

Both are data plus a predicate. Neither needs engine changes to extend.

## Achievements — 32

`domain/achievements/Achievements.kt`.

```kotlin
data class Achievement(
    val id: String, val displayName: String, val description: String, val icon: String,
    val check: (AchievementContext) -> Boolean,
)
```

Checked in `applyBookkeeping` on **both** the live and the offline path, so a night away unlocks
what a live session would have. Once unlocked, permanent: `checkAchievements` skips anything
already in `achievementsUnlocked`, and the map survives every reset.

`checkAchievements` allocates its result list lazily — on the overwhelming majority of ticks
nothing new unlocks, and 32 predicates evaluating to false should cost nothing.

### `AchievementContext`

Most checks read `GameState` alone. Three need things state does not record:

```kotlin
data class AchievementContext(
    val state: GameState,
    val civLevel: Int,
    val justCollapsed: Boolean = false,        // the planet died *this step*
    val justReset: Boolean = false,
    val lastRunDurationSeconds: Double? = null,
    val previousRunDurationSeconds: Double? = null,
)
```

### What they cover

| Theme | Examples |
| :-- | :-- |
| Technology milestones (9) | First Spark, Rise of Agriculture, Industrial Revolution, Let There Be Light, Fossil Fever, Digital Age, Into the Absurd |
| Climate records (7) | Carbon Age (1,000 ppm), Hot (+5 °C), Very Hot (+10), Scorching (+25), Apocalyptic Heat (+50), Rising Tides (50 m), Acid Ocean (pH ≤ 7) |
| Consequences (3) | Oops (first collapse), Mass Extinction (biodiversity ≤ 10%), Ozone Hole (O₃ below −50 DU — **unreachable**, see [Climate model](Climate-Model.md#natural-removal)) |
| Scale (4) | Industrial Monster (100 of one generator), Economies of Scale (×32 ownership bonus), Bigger Than Earth (1e18 of any resource), Hearth Keeper (50 Natural Fires) |
| Prestige (4) | New Beginning, Again?, Serial Destroyer (10 resets), Wealthy Civilization (1e6 EP) |
| Speed (2) | Faster This Time, Civilization Speedrun (under 12 h) |
| Breadth (3) | Renaissance Civilization (one tech in every branch), The Road Not Taken (any choice tech), Front Page (10 headlines in one run) |

Two thresholds are deliberately *not* round numbers:

- **Industrial Monster at 100**, not 1,000. Per-unit costs grow ~15% a unit, so the thousandth
  copy of anything costs some 10⁶⁰ times the first and no run will ever buy it. A completed run
  tops out somewhere under 100 of its favourite building — and 100 lands exactly on the tenth
  ownership doubling.
- **Civilization Speedrun at 12 hours.** A first run takes several days; twelve hours is what a
  deep prestige stack and a well-drilled route can do to that, not a target for a fresh Earth.

## Challenges — 8

`domain/challenges/Challenges.kt`. A challenge **abandons the current Earth** and starts a fresh
one with a live restriction armed, in exchange for a permanent reward on completion.

| Challenge | Restriction | Goal | Reward |
| :-- | :-- | :-- | :-- |
| 🧊 Ice Age | Fails above +2 °C | Civ level 40 | +10% production |
| 🚫 Low Carbon | No technology with "coal" in its id | 1e12 Energy | +25% Research |
| 🛢️ No Oil | No Fossil Fuels branch, no automobile/diesel/jet/petrochemicals | Civ level 35 | +5% production |
| ⛏️ Primitive | Nothing above tier 6 | 120 ppm CO₂ above baseline | +500 starting Energy |
| ⏱️ Speedrun | — | Reset within 15 minutes | *(none — see below)* |
| ☁️ Single Gas | Only technologies emitting CO₂ or nothing | Civ level 30 | +30% CO₂ production |
| 🐄 Methane World | — | CH₄ out-forcing CO₂ at reset | +50% CH₄ production |
| 🌱 Zero Emissions | No coal power, supercritical coal, coal mega-mining or oil sands | Civ level 25 | +20% Research |

### Two restriction shapes

**Purchase-time** restrictions (`disabledTechIds`, `maxTechTier`, `onlyGasIds`) are precomputed
once per state change into `DerivedState.disabledTechIds`, rather than re-derived per card render.
`purchaseTechnology` refuses anything in the set.

**Live** restrictions (`maxTemperatureC`) are checked every step by
`isChallengeRestrictionViolated`. Breaking one **fails the challenge immediately** — `activeId` is
cleared and the run continues as an ordinary Earth. Nothing else is lost.

### Determinism

The Speedrun goal originally read `Date.now()` inside its predicate, which made a pure function
depend on a wall clock: the same state could give different answers on consecutive calls, and it
could not be tested at all. It now measures `state.lastTickAt - state.runStartedAt`, which the
tick sets to the current time every step. Live behaviour is identical to within one tick. This is
one of the two documented [deliberate differences](Reference-Parity.md#deliberate-differences)
from the reference.

### Known copy discrepancies

Two pieces of text inherited from the reference did not match their own effects. The effects are
the shipped balance and were left alone; **the text was corrected in both engines** and the
fixtures regenerated, so parity holds:

- **No Oil** advertised "+20% Transportation branch production" for an effect that is a +5%
  *global* multiplier.
- **Speedrun** advertised "+15% Earth Points from every future prestige" for an effect that is
  `globalProductionMultiplier = 1.0` — literally nothing. There is no "Earth Points earned" field
  in `PrestigeUpgradeEffect` for it to have been wired to, so implementing it would have been a
  balance change rather than a fix. It now reads as what it is.

A third is left as-is and documented: **Zero Emissions**' goal text mentions a "under 1e6 kg/s"
emission ceiling that no live check enforces — the ceiling is expressed through the disabled
technology list. All three are tracked in [the audit](../CODEBASE_AUDIT.md).

## Adding one

**An achievement:** add an `Achievement(...)` to `ACHIEVEMENTS`, mirror it in
`tools/ts-reference/engine/achievements.ts`, regenerate fixtures, update the counts here and in
the README. Read `AchievementContext`, never a clock or a global.

**A challenge:** add a `Challenge(...)` to `CHALLENGES` and mirror it. Its reward must be
expressible as a `PrestigeUpgradeEffect` — `ContentParityTest.every challenge reward is reachable
through the prestige effect shape` asserts exactly that. Its goal predicate must be **pure**:
`(GameState, civLevel) -> Boolean`, with no clock and no randomness.

`ContentParityTest` compares both tables field by field against the reference, **including each
challenge's computed technology-lockout set** — so a challenge whose `disabledTechIds` is derived
from a filter is pinned to the exact technologies that filter selects today.

---

**Next:** [Random events](Random-Events.md) · [Prestige system](Prestige-System.md)
