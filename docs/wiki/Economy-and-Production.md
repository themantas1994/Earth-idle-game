# Economy and production

[← Documentation home](Home.md)

Files: `domain/economy/Economy.kt`, `domain/technologies/TechnologyRegistry.kt`,
`domain/technologies/Scaling.kt`, `domain/engine/Ownership.kt`, `domain/engine/Constants.kt`.

## The one invariant

> **A price never rises for any reason other than your own purchases of that exact thing.**

The amount charged for a technology is a pure function of `(technology, how many of that
technology you already own)`, scaled **down — never up** — by the prestige discount. Nothing about
the rest of the civilization enters into it. Concretely:

- a one-time unlock, multiplier or choice is quoted once and costs exactly that forever, however
  large the tree around it grows;
- a generator's **first** unit always costs its listed base price, and only the units *you* have
  bought of it make the next one dearer.

The reference implementation once had a civilization-complexity surcharge that multiplied every
price by the number of distinct technologies owned. It paced the middle of the game, but it did so
by quietly re-pricing things the player had already been shown: every new frontier made every
*other* frontier more expensive, so the reward for expanding was a bigger bill. It is gone.
Pacing comes entirely from the tier curves, which are baked into a listed price up front and never
move.

`EconomyParityTest` locks this in with five separate assertions, including *a technology costs
the same in an empty world and a fully-built one* and *only the prestige discount moves a price,
and only downward*. **Do not add anything that scales a price by global state.**

The player-facing payoff is the countdown on a Buy button: because prices never move, "affordable
in 4m 12s" is a promise rather than an estimate.

## Resources

Six spendable currencies (`ResourceId`), distinct from the atmospheric gases:

| | |
| :-- | :-- |
| **Energy** | The general economic currency. Most technologies are built with it. |
| **Research** | Spent to unlock tree nodes. |
| **Coal / Oil** | Mined and drilled materials, feeding industrial, power and petrochemical lines. |
| **Metals / Concrete** | Refined and manufactured materials, feeding construction and heavy industry. Metals is persisted under the id `steel`, which predates the rename and is kept so existing saves keep their balance — see [Save migrations](Save-Migrations.md). |

## Pricing

### The next unit

```kotlin
fun nextPurchaseCost(tech, owned) =
    tech.cost.map { CostLine(it.resource, gd(it.baseAmount) * gd(growth).pow(owned)) }
    // growth = 1.0 for one-time nodes, tech.costGrowth for generators
```

### Buying several

The sum of a geometric series, closed-form — never a loop:

```
total = base · (growth^quantity − 1) / (growth − 1)
```

### Buy-max

`maxAffordableQuantity` inverts that series:

```
balance ≥ base · (growth^n − 1)/(growth − 1)
    ⟹ n ≤ log(balance·(growth−1)/base + 1) / log(growth)
```

so "Max" is a logarithm, not a loop, even for thousands of units.

> [!IMPORTANT]
> The logarithm is evaluated in floating point, so on a boundary — a balance exactly equal to the
> cost of *n* units — it can land one unit either side of the true answer. **Over-counting is the
> dangerous direction:** the caller would charge more than the player has, the subtraction clamps
> at zero, and the player gets a free purchase. So the last unit is settled against the exact
> geometric-series cost rather than trusting the float, capped at 4 corrections so a pathological
> cost curve can never loop.
>
> `EconomyParityTest.a wallet holding exactly the cost of n units buys exactly n` and
> `buy-max never spends more than the wallet holds` guard both directions.

### The prestige discount

`effectiveCostAmount(nominal, discount) = nominal * (1 − min(0.9, discount))` — capped at 90% so a
price can never reach zero.

`nominalizeWallet` restates the player's balances in the "nominal" units the cost curves are
written in, by dividing by exactly the factor that will later multiply the price. That is what
lets the closed-form affordability maths stay ignorant of the discount — and, critically, it means
**the UI and the engine answer the affordability question with the same function**, so a Buy
button can never offer a purchase `purchaseTechnology` would then refuse.
`EconomyParityTest.the prestige discount can never make a purchase the engine then refuses`
asserts it.

## `purchaseTechnology`

Atomic: either the affordable quantity (capped at what was requested and at the remaining room)
is bought and paid for in one step, or nothing changes at all.

```
tech exists?           → no: fail
disabled by challenge? → yes: fail
requirements met?      → no: fail
room left?             → no: fail
affordable quantity    → 0: fail
charge the exact bulk cost, clamped at zero
```

`BUY_MAX_QUANTITY` is `Int.MAX_VALUE`. Generators have `maxOwned = Int.MAX_VALUE` too, standing in
for the reference's `Infinity`: per-unit costs grow ~15% a unit, so the two-billionth copy of
anything would cost some 10¹³⁹ times the first.

## The balance curves

Every technology's cost and output is derived from its **tier** through `Scaling.kt`, which reads
`BALANCE` in `Constants.kt`. The whole game is retuned by adjusting a handful of numbers there
rather than hundreds of hand-picked ones in the data files.

| Constant | Value | What it is |
| :-- | --: | :-- |
| `productionGrowthPerTier` | 2.0 | Per-tier growth of a generator's per-unit **resource** output |
| `gasProductionGrowthPerTier` | 1.7 | Per-tier growth of its per-unit **gas** output |
| `generatorCostGrowthPerTier` | 3.05 | Per-tier growth of its base (first-unit) cost |
| `unlockCostGrowthPerTier` | 3.05 | Per-tier growth of one-time node costs |
| `researchCostGrowthPerTier` | 2.85 | Per-tier growth of research costs |
| `generatorBaseCost` | 10.0 | First-unit cost of a tier-0 generator |
| `unlockBaseCost` | 9.0 | Cost of a tier-0 one-time unlock |
| `researchBaseCost` | 5.0 | Research cost of a tier-0 node |
| `gasProductionBase` | 2.0 | kg/s of one tier-0 generator unit |
| `resourceProductionBase` | 0.5 | units/s of one tier-0 generator unit |
| `unitCostGrowth` | 1.15 | Cost growth **per unit already owned** of the same generator |
| `unitCostGrowthPerTier` | 0.0015 | Extra per-unit growth added per tier |
| `flattenLadderFromTier` | 16.0 | Above this, cost and production ladders compress together |
| `lateTierCompression` | 0.55 | How much of a tier step still counts above the knee |

### The ratio that paces the game

```
r = generatorCostGrowthPerTier / productionGrowthPerTier = 3.05 / 2.0 = 1.525
```

A tier's income scales with `productionGrowthPerTier^tier` while the next tier's price scales with
`generatorCostGrowthPerTier^tier`, so the wall-clock time to climb one tier is multiplied by `r`
every tier, and total run length is roughly
`firstTierSeconds · (r^tierCount − 1)/(r − 1)`.

**With `r ≤ 1` the game runs away and finishes itself in minutes.** `BalanceInvariantsTest`
asserts `r > 1` directly.

### Why gas grows more slowly than resources

`gasProductionGrowthPerTier` (1.7) is deliberately below `productionGrowthPerTier` (2.0). If
emissions grew as fast as the economy, the planet would always collapse a few tiers before the
tree ran out and the last technologies would be unreachable content. That gap is what makes the
endgame reachable at all. Also asserted by `BalanceInvariantsTest`.

### The late-tier knee

```kotlin
fun ladderTier(tier: Double): Double {
    val knee = 16.0
    return if (tier <= knee) tier else knee + (tier - knee) * 0.55
}
```

Below tier 16 the tree is broad: ten branches produce at once and total income climbs steeply.
Above it the tree narrows to a single chain, so a new tier adds one generator to a large static
base — income barely moves while a full-size tier step would more than double the price. Left
uncompressed, that gap turns the last third of the tree into an unclimbable wall.

**Both ladders are compressed by the same function.** Compressing costs without compressing
output would simply hand the late game back its runaway.

## Ownership bonuses

`domain/engine/Ownership.kt`.

| | |
| :-- | :-- |
| `everyUnits` | **10** — a bonus lands on every tenth copy of one generator |
| `multiplier` | **2.0** — that building's entire output doubles, permanently, for the rest of the run |

Every generator's price climbs with each unit you own, which on its own makes buying the eleventh
copy of something strictly worse than buying the first copy of something newer. That is correct
pacing but miserable feedback: the number goes up, the bar does not, and nothing ever *happens*.
So every generator carries its own progress track.

**The spacing is what keeps it honest.** Across one ten-unit span a generator's unit price grows
by `unitCostGrowth^10` ≈ ×4.05, against a single ×2 from the bonus — so the value of each further
copy still falls, and broadening into new technology still wins in the long run. Depth is a
satisfying detour, not a replacement for the tech tree.

> `OWNERSHIP_BONUS.multiplier` must stay **below** `generatorCostGrowth(tier)^everyUnits` for every
> generator, or depth outruns the price that buys it and the tech tree stops mattering.
> `BalanceInvariantsTest.going deep on one generator never beats broadening into new technology`
> checks it per technology.

`ownershipMilestonesCrossed(before, after)` computes thresholds arithmetically rather than by
walking units, since a single buy-max can land thousands of copies at once. A buy-max crossing
several thresholds reports the **highest** one, so the toast names a real threshold.

## `secondsUntilAffordable`

The countdown on a Buy button. Returns the worst per-resource wait, or **null** when nothing is
producing a required resource — which the UI renders as "no income" rather than an infinite
countdown.

---

**Next:** [Technology system](Technology-System.md) · [Prestige system](Prestige-System.md) · [Simulation](Simulation.md)
