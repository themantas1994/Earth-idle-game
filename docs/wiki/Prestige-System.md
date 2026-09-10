# Prestige system

[← Documentation home](Home.md)

`domain/prestige/Prestige.kt`. The reset loop: a dead planet becomes **Earth Points**, and Earth
Points buy permanent advantages for every Earth after it.

## The payout

```kotlin
fun calculatePrestigeGain(params): GameDecimal {
    val gasScore = (totalGasProducedKg.sum() / 1e12).clampMin(ZERO)
    val gasComponent = gasScore.pow(0.5) * 1.0
    val forcingMultiplier = 1.0 + 0.6 * sqrt(max(0.0, peakForcingWm2))
    val civMultiplier = 1.0 + 0.4 * sqrt(max(0.0, civLevel.toDouble()))
    return gasComponent * forcingMultiplier * civMultiplier * speedMultiplier(runDurationSeconds)
}
```

| Constant | Value | Role |
| :-- | --: | :-- |
| `baseDivisor` | 1e12 | Scales total kg into a workable range |
| `exponent` | 0.5 | Sub-1, so the headline mass term has diminishing returns |
| `gasWeight` | 1.0 | |
| `forcingWeight` | 0.6 | Square-rooted, for the same reason |
| `civLevelWeight` | 0.4 | Square-rooted — civ level runs into the thousands by the end of a run |
| `referenceRunSeconds` | 3 days | A run at exactly this pace scores ×1 for speed |
| `speedExponent` | 2.0 | Halving your run time roughly quadruples the payout |
| `minSpeedMultiplier` / `maxSpeedMultiplier` | 0.25 / 64 | Bounds, so neither a crawl nor a record distorts the economy |

### Why speed is scored

Every run ends in the same place — habitability zero, tech tree finished. An outcome-only score
would therefore pay a **stronger** civilization **less**, because a stronger civilization kills the
planet sooner and emits less in total before it does. That is backwards, and it is what makes
prestige a decoration instead of a loop.

Scoring speed tracks the one thing prestige upgrades actually buy.

```kotlin
fun speedMultiplier(runDurationSeconds: Double): Double {
    if (!runDurationSeconds.isFinite() || runDurationSeconds <= 0) return 1.0
    val ratio = 3.0 * 24 * 3600 / runDurationSeconds
    return min(64.0, max(0.25, ratio.pow(2.0)))
}
```

A run of zero or nonsensical length is treated as the *reference* pace rather than as infinitely
fast, so **a corrupted clock cannot mint points**. `PrestigeParityTest.a corrupted clock cannot
mint points` asserts it.

## The 11 upgrades

| Upgrade | Base cost | Growth | Max | Effect (per level) |
| :-- | --: | --: | --: | :-- |
| 🔥 Eternal Flame | 50 | 1.8 | 10 | +2 Natural Fire at the start of every future Earth |
| 🌬️ Atmospheric Momentum | 100 | 2.2 | ∞ | +10% production |
| ⚙️ Industrial Memory | 1,000 | — | 1 | Start every Earth knowing the Steam Engine |
| ⏳ Extended Endurance | 5,000 | 3.5 | 3 | +100% offline cap |
| 📚 Rapid Research | 10,000 | 3.0 | 5 | +50% Research |
| 🚩 Head Start | 20,000 | — | 1 | Start with 1,000 Energy |
| 🧱 Deep Foundations | 40,000 | — | 1 | Start with 5,000 Research and 25,000 Energy |
| 🏛️ Institutional Memory | 150,000 | 4.0 | 6 | +40% production |
| 🤖 Automated Industry | 200,000 | — | 1 | +100% offline cap, and 25 Natural Fires |
| 📐 Civilizational Acceleration | 1,000,000 | 4.0 | 4 | −20% on every technology cost |
| 🌋 Anthropocene Mastery | 25,000,000 | — | 1 | ×10 all greenhouse-gas production |

`cost(level) = baseCost × costGrowth^currentLevel`. Atmospheric Momentum has no ceiling
(`UNLIMITED_LEVELS`); cost growth makes anything past a few dozen levels unaffordable anyway.

The maximum offline cap is therefore **12 h × 2³ × 2 = 8 days** (Extended Endurance at 3 plus
Automated Industry). `OfflineLifecycleTest` asserts the ×16.

## Aggregation

`computePrestigeMultipliers(upgradesOwned, challengeRewardEffects)` folds everything into one
bundle. Multiplicative effects use `value.pow(level)`; the cost discount stacks **additively** and
is capped at 0.9:

```kotlin
e.globalProductionMultiplier?.let { global *= it.pow(level) }
e.techCostDiscount?.let { techCostDiscount = min(0.9, techCostDiscount + it * level) }
```

Completed [challenges](Achievements-and-Challenges.md) contribute through the **same**
`PrestigeUpgradeEffect` shape, applied at a flat level of 1 — a challenge is either completed once
or not.

> **A known quirk:** `startingResources` is summed *without* multiplying by level, unlike every
> other field. Every upgrade that grants starting resources has `maxLevel = 1`, so it is
> unobservable today. It would matter the moment a repeatable one is added, and is recorded in
> [the audit](../CODEBASE_AUDIT.md).

## The reset

`GameLoop.resetEarth(state, nowMs, derived)`:

1. **Refuse** unless `state.collapsed`.
2. `scoreRun` for the payout and the summary the collapse dialog shows.
3. `startNewRun` — see [what survives](Game-State.md#what-survives-a-reset).
4. Bank the points; update `totalResets`, `fastestResetSeconds`, `longestRunSeconds`,
   `totalEarthPointsEarned`.
5. Recompute multipliers (they now include anything just bought) and apply the starting bonuses:
   `startingTechIds` granted at 1, `startingGenerators` added as extra owned units,
   `startingResources` added to the balance.
6. Check the reset-flavoured achievements ("Again?", "Faster This Time", "Civilization Speedrun"),
   which need to know a reset just happened and how this run compared with the previous one —
   things `GameState` does not record, so they arrive through `AchievementContext`.

`previousRunDurationSeconds` is read as `lifetimeStats.longestRunSeconds` *before* the reset
updates it, which is what makes "Faster This Time" answerable.

---

**Next:** [Achievements and challenges](Achievements-and-Challenges.md) · [Game state](Game-State.md)
