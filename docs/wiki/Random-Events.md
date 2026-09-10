# Random events and world news

[← Documentation home](Home.md)

Two systems that look similar and are deliberately not the same thing.

| | `domain/events/Events.kt` | `domain/milestones/Milestones.kt` |
| :-- | :-- | :-- |
| **What** | 13 random events | 39 news headlines |
| **Trigger** | Chance, weighted, live path only | Deterministic threshold, first time it is true |
| **Effect** | Multiplier swings, some with an instant gas burst | **None whatsoever** |
| **Duration** | 30–150 seconds | Instant, then it sits in the feed |
| **Scope** | Up to 3 at once, paused during a challenge | Once per run |

## Random events

### Rolling

```kotlin
const val EVENT_ROLL_CHANCE_PER_SECOND = 0.006   // ≈ one roll every ~3 minutes
const val MAX_CONCURRENT_EVENTS = 3
```

On each live tick, if no challenge is active and fewer than three events are running,
`random.nextDouble() < dtSeconds * 0.006` decides whether to roll at all. Then
`rollRandomEvent(civLevel, activeIds, draw)` picks one by weight among the currently eligible —
those whose `minCivLevel` is met and which are not already running.

The caller supplies the draw in `[0,1)` rather than the function drawing its own, which is what
makes selection reproducible in tests and comparable against the reference for the same seed.
`GameLoop` takes its `Random` by constructor for the same reason.

Two suppressions are deliberate:

- **During a challenge.** A focused run should not be shoved around by luck.
- **Offline.** An event is a swing the player could have reacted to; awarding a handful for a
  night asleep would be noise at best. `OfflineLifecycleTest.offline progress rolls no random
  events` asserts it.

### The 13

| Event | Min civ | Duration | Weight | Effect |
| :-- | --: | --: | --: | :-- |
| 🌻 Bumper Harvest | 1 | 90 s | 4 | ×3 global |
| 🔨 Master Craftsman | 3 | 90 s | 4 | ×8 Research |
| 🌋 Volcanic Eruption | 0 | 30 s | 3 | +5e10 kg CO₂, instantly |
| 🔥 Wildfire | 3 | 30 s | 3 | +2e11 kg CO₂, instantly |
| 🌊 El Niño | 5 | 120 s | 3 | ×2 all gas |
| 🧊 Methane Release | 8 | 120 s | 3 | ×5 CH₄ |
| 📈 Industrial Boom | 6 | 120 s | 4 | ×8 global |
| 📉 **Economic Crash** | 6 | 30 s | 1 | ×0.7 global |
| 🌾 Green Revolution | 7 | 120 s | 3 | ×15 Agriculture branch |
| 💡 Technological Breakthrough | 10 | 120 s | 4 | ×30 Research |
| 🚧 **Supply Chain Shock** | 18 | 30 s | 1 | ×0.75 global |
| 🤖 AGI Breakthrough | 26 | 150 s | 4 | ×50 Research, ×4 global |
| ☀️ Stellar Flare | 40 | 150 s | 4 | ×20 global, ×3 all gas |

**Only two of thirteen are negative, and both are short and mild.** That is the design, not an
oversight: a negative event in an idle game is a tax on being away — you were not there, you could
not react, and all it did was quietly make the bar fill slower. The two that remain exist so a
green banner means something. Weights and durations are tuned so the *positive* ones are the ones
long enough to notice and act on.

Note also that positive events skew to *higher* weights (4 for the best) and negatives sit at
weight 1.

### While one is running

`ActiveEvent` records a wall-clock window (`startedAt`, `endsAt`). Each tick,
`computeActiveEventMultipliers(activeEvents, nowMs)` folds the still-live ones into one
`MultiplierContribution`, which is passed into `simulateStep` alongside the prestige bundle.
`removeExpiredEvents` prunes anything whose window has closed — including after an absence, so an
event can never outlive its duration by being backgrounded.

`applyInstantGasBurst` applies a one-time atmospheric mass injection the instant an event
triggers, converting kg to concentration through the gas's `massPerUnit`.

Because the window is wall-clock, an event's effect ends at the right *time* even though the
offline path does not roll new ones.

## World news — 39 headlines

Deterministic milestones with **no mechanical effect at all**. Their whole job is to narrate what
the player's civilization is doing to the world, so a week-long run reads as a story of
consequences rather than a rising number.

```kotlin
data class MilestoneDefinition(
    val id: String,
    val headline: String,   // "Coal smog kills thousands in a week"
    val body: String,       // one or two sentences of wire copy
    val source: String,     // "THE ILLUSTRATED NEWS · LONDON"
    val icon: String,
    val category: MilestoneCategory,   // INDUSTRY, CLIMATE, ECOLOGY, SOCIETY, APOCALYPSE
    val condition: (GameState) -> Boolean,
)
```

The set deliberately spans the whole run, and **changes what it keys on partway through**:

- **Early headlines fire on technology**, because a handful of campfires genuinely cannot move a
  planet's atmosphere and pretending otherwise would be a lie the rest of the simulation does not
  tell. The consequences reported there are local and regional — a valley, a river, a city.
- **Climate headlines take over in the back half**, once emissions are large enough to show up in
  the global numbers, escalating from treaty thresholds to obituaries.

Each fires at most **once per run**, tracked in `milestonesTriggered`, which resets with every new
Earth. Fired headlines are prepended to `newsFeed` newest-first and capped at `NEWS_FEED_LIMIT`
(**40**), so a long run cannot grow the save without bound.

When several fire in one step — common after an absence — the UI toasts only the **most recent**;
the rest are waiting in the feed on the Home screen.

## Testing

| Test | Covers |
| :-- | :-- |
| `EventsParityTest` | Every eligibility state × draw against the reference, multiplier composition, instant bursts, pruning, no double-rolling a running event |
| `ContentParityTest` | All 13 events and all 39 headlines, field by field, including weights, durations and copy |
| `GameLoopTest` | Seeded reproducibility, the challenge pause, the three-at-once cap, pruning, headlines firing once per run |
| `OfflineLifecycleTest` | No rolls offline; expired events pruned by an absence that outlasted them |

---

**Next:** [Achievements and challenges](Achievements-and-Challenges.md) · [Game engine](Game-Engine.md)
