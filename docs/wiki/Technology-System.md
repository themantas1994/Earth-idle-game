# Technology system

[← Documentation home](Home.md)

**99 technologies across 11 branches**, defined entirely as data. Adding technology #100 means
adding one entry to one branch file — no engine change.

## The four kinds

| Kind | Count | Behaviour |
| :-- | --: | :-- |
| `GENERATOR` | **68** | Repeatably purchasable. Produces gas and/or resources per owned unit. Cost scales with `costGrowth` per unit already owned. `maxOwned = Int.MAX_VALUE`. |
| `MULTIPLIER` | **16** | One-time. Permanently scales production — globally, per branch, per gas or per resource. |
| `UNLOCK` | **13** | One-time tree node. Gates later technologies, produces nothing itself (e.g. "Cooking"). |
| `CHOICE` | **2** | One-time and **mutually exclusive** with its siblings in the same `choiceGroup`. |

Generators sell on the **Production** screen; the other 31 sell on **Technology**. The two lists
are disjoint by construction (`GENERATOR_TECHNOLOGIES` and `RESEARCH_TECHNOLOGIES`), and
`TechnologyParityTest` asserts they partition the tree, so neither screen ever shows the other's
cards.

## The 11 branches

| Branch | Id | Techs | Arc |
| :-- | :-- | --: | :-- |
| Primitive Civilization | `primitive` | 10 | Natural Fire → Controlled Fire → cooking, kilns |
| Agriculture | `agriculture` | 10 | Farming, livestock, rice, fertiliser |
| Industrial Revolution | `industry` | 9 | Steam, coal mining, steel, factories |
| Electricity | `electricity` | 11 | Dynamos, grids, coal power — and the Coal-vs-Nuclear choice |
| Fossil Fuels | `fossilFuels` | 8 | Drilling, refining, gas, oil sands |
| Transportation | `transportation` | 12 | Automobiles, diesel, shipping, aviation |
| Construction | `construction` | 7 | Cement, urbanisation, megastructures |
| Chemical Industry | `chemistry` | 7 | Petrochemicals, plastics, CFCs, SF₆ |
| Globalization | `globalization` | 7 | Trade, logistics, global supply chains |
| Digital Civilization | `digital` | 7 | Computers, datacentres, crypto, AI |
| Endgame | `endgame` | 11 | Fusion, orbital industry, Dyson Swarm Prototype, Matrioshka Brain, Stellar Energy |

Branch order is the declaration order in `TechBranch`, which is also the order the UI walks.
`ALL_TECHNOLOGIES` is sorted by **tier**, not by branch. Tiers run from 0 to **38**.

## Tier

`tier` is a rough **overall-progression index**, not per-branch: technologies unlocked around the
same point in a normal playthrough share a similar tier whichever branch they belong to. It drives
cost and production scaling ([the curves](Economy-and-Production.md#the-balance-curves)) and UI
ordering. Nothing else.

The per-technology `scale` argument on each curve function is the fudge factor: a
dirtier-than-average generator gets a gas `scale` above 1, a bargain unlock a cost `scale` below 1.
**Keep it within roughly 0.3–3** or a single technology starts distorting the pacing the curves are
built on. `BalanceInvariantsTest` enforces a looser 0.1–10 bound as a backstop.

## Requirements and availability

```kotlin
fun isTechAvailable(tech, ownedCounts): Boolean {
    if (!requirementsMet(tech, ownedCounts)) return false      // every `requires` id owned ≥ 1
    if ((ownedCounts[tech.id] ?: 0) >= tech.maxOwned) return false
    // a rival in the same choiceGroup already taken closes this off for the run
    return !isRivalChoiceTaken(tech, ownedCounts)
}
```

Choice-group membership is precomputed into `TECHS_BY_CHOICE_GROUP` at class-init so availability
is not O(tree) per card.

`TechnologyParityTest` asserts the graph properties that make the tree playable at all:

- every `requires` entry names a technology that exists;
- **the dependency graph has no cycles**;
- **every technology is reachable from the starting fire** — so no content is orphaned;
- every one-time node is genuinely one-time and every generator is genuinely unbounded;
- all eleven branches are populated;
- every gas and resource reference resolves.

## The strategic choice

One `choiceGroup`, `energy_strategy`, at tier 14, both requiring `coal_power_plant`:

| | Cost | Effect |
| :-- | :-- | :-- |
| ⚫ **Coal Industrialization** | `unlockCost(14)` | ×10 CO₂ production |
| ☢️ **Nuclear Industrialization** | `unlockCost(14) × 1.5` | ×5 Energy production |

Cheap and filthy, or expensive and clean. Taking one permanently locks the other for that Earth;
the card renders as "NOT CHOSEN". A reset opens the decision again.

## `TechEffect`

All fields optional. A generator uses the per-unit maps; a multiplier uses the multiplier fields;
an unlock may use neither.

```kotlin
data class TechEffect(
    val gasProductionPerUnit: Map<GasId, Double> = emptyMap(),
    val resourceProductionPerUnit: Map<ResourceId, Double> = emptyMap(),
    val gasRemovalPerUnit: Map<GasId, Double> = emptyMap(),   // engineered removal
    val globalProductionMultiplier: Double? = null,
    val branchProductionMultiplier: BranchMultiplier? = null,
    val gasProductionMultiplier: GasMultiplier? = null,
    val resourceProductionMultiplier: ResourceMultiplier? = null,
    val researchMultiplier: Double? = null,
)
```

`gasRemovalPerUnit` is how carbon capture, reforestation and terraforming engines work — a flat
subtraction from the gas's production term. It deliberately does **not** receive the per-gas
multiplier, so a production-boosting event cannot accidentally boost removal with it.

## Adding a technology

1. Add a `Technology(...)` to the right `domain/technologies/*Technologies.kt`.
2. Derive every number from the curves: `generatorBaseCost(tier)`, `unlockCost(tier)`,
   `researchCost(tier)`, `gasProduction(tier)`, `resourceProduction(tier)`, and
   `generatorCostGrowth(tier)` for `costGrowth`. **Do not hand-pick a price.**
3. Set `requires` so the node is reachable, and `maxOwned` to `Technology.UNLIMITED` for a
   generator or `1` for anything else.
4. **Mirror it in `tools/ts-reference/engine/technologies/`.** The count and every field are
   pinned by `TechnologyParityTest` against the fixtures.
5. `cd tools/ts-reference && npx vitest run && npm run fixtures`.
6. `./gradlew test`.
7. Update the counts on this page and in the README feature table.

> Skipping step 4 fails `TechnologyParityTest` immediately — the tree is compared entry by entry
> against the fixture, including the count.

---

**Next:** [Economy and production](Economy-and-Production.md) · [Reference parity](Reference-Parity.md)
