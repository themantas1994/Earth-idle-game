# Events

[← Documentation home](Home.md)

Everything that *happens to* a run, how each kind is decided, and how each one
reaches the player.

EARTH has three sources of them, and they are deliberately different things.
This page is the map; each has its own page with the detail.

| Kind | Decided by | Effect | Page |
| :-- | :-- | :-- | :-- |
| **Random events** | A weighted roll, roughly every few minutes | A temporary multiplier swing, sometimes a one-off gas burst | [Random events](Random-Events.md) |
| **Milestone headlines** | Crossing a threshold, once per run | None. They narrate | [Random events](Random-Events.md#world-news--39-headlines) |
| **Storm bulletins** | The storm simulation | The storm itself carries the effect | [Storm system](Storm-System.md) |

---

## One feed, not three

All three write into the **same** world-news feed — `GameState.newsFeed`, shown
by `NewsFeedCard` on Home — and raise the same kind of banner. There is no second
notification system anywhere in the game, and the storm feature deliberately did
not add one.

```kotlin
data class NewsItem(
    val milestoneId: String,      // a milestone id, or a unique id for a bulletin
    val at: Long,
    val runSeconds: Double,
    val bulletin: NewsBulletin? = null,
)
```

A headline is either **looked up** — `MILESTONE_BY_ID[item.milestoneId]`, for the
fixed milestone table — or **carried**, in `bulletin`, for text that was written
at the time because it names something the table could not have known about.
Currently that is storms: "Hurricane Iris intensifies to severe" needs a storm
that did not exist a minute ago.

The text is written into the feed rather than reconstructed on read, so a
headline about Iris still reads correctly long after Iris has dissipated.

The feed is capped at `NEWS_FEED_LIMIT` (40) entries, newest first, and resets
with each Earth.

---

## Events on the globe

The [Home screen's globe](Home-Screen.md) shows running events under the
**Events** and **Storms** overlays.

### Storms have real geography

A storm is a simulation entity with a latitude and a longitude, and the marker on
the globe is drawn at exactly the coordinates the simulation gave it — the same
number the storm card prints. `EnvironmentalVisualizationTest` asserts the two
agree.

### Random events do not

The event system has no geography: events are global multiplier swings. So the
latitude and longitude of an event pin are **presentation only** — a stable
pseudo-position derived from the event's own id by `pseudoPlacement`, so a
wildfire stays where it first appeared for as long as it burns and two events
never sit on top of each other. Nothing in the simulation reads them back, and
they are not saved.

That is a deliberate limitation and the honest one: inventing a location for an
event the engine models globally would be making up simulation data to decorate
a renderer, which is the thing this whole feature is built not to do. Giving
events real regions is a change to the *event system*, and it belongs there.

### The visual mapping

```
Event                     → EventVisual                → marker
RandomEventDefinition.icon  icon, label                  pulsing ring
isNegative                  isNegative                   red or green
startedAt/endsAt            progress (0..1)              —
id                          latitude, longitude          where it sits

Storm                     → StormVisual                → marker
type.icon                   icon, typeLabel              rotating spiral
latitudeDeg, longitudeDeg   latitudeDeg, longitudeDeg    where it sits
intensity                   intensity                    size, brightness, spin, lightning
severity                    severityLabel                the word on the card
stormContribution()         globalPenalty, branchPenalties  the effects list
```

---

## What interrupts the player, and what does not

An extremely warmed planet produces a storm bulletin every few seconds. Printing
all of them would bury the milestone headlines under a weather ticker, so there
are two filters:

1. **`StormBulletin.isMajor`** decides what reaches the feed at all: any
   intensification to severe or worse, any hurricane or superstorm forming, and
   the passing of a storm that peaked above 0.7. A tropical storm quietly
   forming and quietly dying is not news.
2. **`StepEvents.majorStormBulletin`** picks the single most recent major
   development for a banner. One toast, not a queue.

The camera follows the same rule. It eases toward a storm the player has
**selected**, and it is never snatched by the weather changing.

Milestones do the same thing: several can fire on one (especially offline) step,
and the most recent is the one worth interrupting for. The rest are waiting in
the feed.

---

## Across an absence

All three are settled on the way back in, through the same paths a live session
uses:

- **Random events** expire (`removeExpiredEvents`) and none are rolled while
  away. See [Offline progression](Offline-Progression.md).
- **Milestones** are checked by the same `applyBookkeeping` the live tick runs,
  so a night away reports the same headlines a live session would have shown.
- **Storms** are simulated across the elapsed time — forming, moving,
  intensifying and dissipating — and their major bulletins are written into the
  feed with the rest. See
  [Storm system → Offline behaviour](Storm-System.md#offline-behaviour).

---

**Next:** [Random events](Random-Events.md) · [Storm system](Storm-System.md) · [Home screen](Home-Screen.md) · [Offline progression](Offline-Progression.md)
