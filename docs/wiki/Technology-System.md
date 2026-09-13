# Technology system

[← Documentation home](Home.md)

**157 technologies across 16 branches**, defined entirely as data. Adding technology #158 means
adding one entry to one branch file — no engine change.

## The five kinds

| Kind | Count | Behaviour |
| :-- | --: | :-- |
| `GENERATOR` | **78** | A **producer**. Repeatably purchasable, takes no input, produces gas and/or resources per owned unit. Cost scales with `costGrowth` per unit already owned. `maxOwned = Int.MAX_VALUE`. |
| `CONSUMER` | **26** | A **processor**. Repeatably purchasable like a producer, but it consumes a per-unit *flow* of one or more resources and runs at the rate its scarcest input allows. See [Economy and production](Economy-and-Production.md). |
| `MULTIPLIER` | **28** | One-time. Permanently scales production — globally, per branch, per gas or per resource. |
| `UNLOCK` | **23** | One-time tree node. Gates later technologies, produces nothing itself (e.g. "Cooking"). |
| `CHOICE` | **2** | One-time and **mutually exclusive** with its siblings in the same `choiceGroup`. |

Producers and processors sell on the **Production** screen, in separate sections; the other 53 sell
on **Technology**. The two lists are disjoint by construction (`BUILDING_TECHNOLOGIES` and
`RESEARCH_TECHNOLOGIES`), and `TechnologyParityTest` asserts they partition the tree, so neither
screen ever shows the other's cards.

## The 16 branches

The first eleven are the original tree; the last five came with the production-chain economy and
exist because the chains needed somewhere coherent to live.

| Branch | Id | Techs | Arc |
| :-- | :-- | --: | :-- |
| Primitive Civilization | `primitive` | 10 | Natural Fire → Controlled Fire → cooking, kilns |
| Agriculture | `agriculture` | 10 | Farming, livestock, rice, fertiliser |
| Industrial Revolution | `industry` | 9 | Steam, coal mining, steel, factories |
| Electricity | `electricity` | 16 | Dynamos, grids, coal power, the coal-fired and fuel-fired stations, wind and solar — and the Coal-vs-Nuclear choice |
| Fossil Fuels | `fossilFuels` | 12 | Drilling, rotary rigs, fractional distillation, the refinery complex, gas, oil sands |
| Transportation | `transportation` | 12 | Automobiles, diesel, shipping, aviation |
| Construction | `construction` | 7 | Cement, urbanisation, megastructures |
| Chemical Industry | `chemistry` | 13 | Petrochemicals, plastics, CFCs, SF₆, ammonia synthesis, synthetic fuel, catalysis, carbon capture |
| Globalization | `globalization` | 7 | Trade, logistics, global supply chains |
| Digital Civilization | `digital` | 7 | Computers, datacentres, crypto, AI |
| Endgame | `endgame` | 11 | Fusion, orbital industry, Dyson Swarm Prototype, Matrioshka Brain, Stellar Energy |
| Mining & Extraction | `mining` | 12 | Prospecting, iron and copper, mechanisation, open pits, uranium, rare earths, deep-sea, autonomous fleets |
| Materials & Metallurgy | `materials` | 10 | Blast furnaces, the steel mill, cement kilns, arc furnaces, alloys, recycling, composites, metamaterials, molecular foundries |
| Nuclear | `nuclear` | 5 | Fission plants, enrichment, fast breeders, fusion research, the fusion complex |
| Electronics & Computing | `electronics` | 8 | Semiconductors, integrated circuits, lithography, batteries, supercomputing, nanofabrication, robotics, automation |
| Space Industry | `space` | 8 | Rocketry, rocket factories, reusable launch, orbital shipyards, asteroid mining, space-based solar, Dyson assembly |

Branch order is the declaration order in `TechBranch`, which is also the order the UI walks.
`ALL_TECHNOLOGIES` is sorted by **tier**, not by branch, with a **stable** sort over a list that
puts the original eleven branches first — so the reference implementation's 99 technologies keep
their exact relative order and new content interleaves between them. Tiers run from 0 to **39**.

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
- every one-time node is genuinely one-time and every building is genuinely unbounded;
- every branch is populated;
- every gas and resource reference resolves.

`EconomyValidationTest` adds the rules that only make sense once processors exist: a processor never
unlocks before something that can feed it, no technology requires something from a higher tier than
itself, and every production loop is anchored to something outside itself. See
[Economy and production](Economy-and-Production.md#validation).

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
