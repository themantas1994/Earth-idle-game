# Save system

[← Documentation home](Home.md)

Files: `domain/save/SaveSerialization.kt`, `domain/save/Migrations.kt`,
`data/repository/SaveRepository.kt`, `data/persistence/DataStoreSaveRepository.kt`.

## Where the save lives

```
/data/data/com.earthgame.idle/files/datastore/earth-save.preferences_pb
```

(`com.earthgame.idle.debug` for a debug build, which is why a debug and a release install do not
share a save.)

Private app storage: unreadable by other apps and by the user without root. Backed up by Android's
Auto Backup **only** if the user has that switched on — `backup_rules.xml` and
`data_extraction_rules.xml` scope it to exactly this one file, so a restore brings the save and
nothing stale alongside it.

There is no cloud save, no account and no server. See
[Security and privacy](Security-and-Privacy.md).

## Two slots, one transaction

```kotlin
context.saveDataStore.edit { preferences ->
    preferences[primaryKey]?.let { preferences[backupKey] = it }   // rotate
    preferences[primaryKey] = serialized                            // then write
}
```

Both slots (`"save"` and `"save.backup"`) live in one DataStore and are written in a **single
`edit` transaction**, so the two can never disagree about what the last known-good state was. The
backup is always exactly one save behind.

DataStore rather than `SharedPreferences` for two reasons that matter here: its writes are
transactional and atomic, so a process killed mid-save leaves the previous contents intact rather
than a truncated file; and its API is suspending, so the main thread never blocks on disk — which
matters because saving happens on the autosave cadence and on every backgrounding, exactly when a
dropped frame would be noticed.

## When it saves

| Trigger | |
| :-- | :-- |
| Autosave | Every **15 s** (`SIMULATION.AUTOSAVE_INTERVAL_MS`) while foregrounded |
| Backgrounding | `onEnterBackground` — **the authoritative save point** |
| Reset, challenge start, settings change, tutorial skip | Immediately |

Backgrounding is authoritative because Android does not guarantee anything runs when it kills a
backgrounded app. The moment the app stops being visible is the last reliable chance to write, and
[offline progress](Offline-Progression.md) is measured from what was written.

## The format

Versioned JSON, one object, field names matching the original web build's — so a save exported
from that build loads here and migrates forward through the same chain.

```json
{
  "saveVersion": 3,
  "runNumber": 2,
  "runStartedAt": 1700000000000,
  "lastTickAt": 1700000042000,
  "createdAt": 1699000000000,
  "resources":   { "energy": { "__decimal": [1, 4.82, 137] }, "…": "…" },
  "atmosphere":  { "co2":    { "__decimal": [1, 3.10, 2] },   "…": "…" },
  "techOwned":   { "natural_fire": 40, "coal_mining": 12 },
  "prestige":    { "earthPoints": { "__decimal": [1, 1.5, 6] }, "upgradesOwned": { "…": 3 } },
  "runStats": { "…": "…" }, "lifetimeStats": { "…": "…" },
  "settings": { "…": "…" }, "tutorial": { "…": "…" },
  "achievementsUnlocked": { "oops": true },
  "challenges": { "activeId": null, "completed": { "ice_age": true } },
  "activeEvents": [], "milestonesTriggered": {}, "newsFeed": [],
  "collapsed": false
}
```

### Why `GameDecimal` is a tagged triple

```json
{ "__decimal": [sign, mantissa, exponent] }
```

A JSON **number** would round-trip through a double and cap the save at 1.8e308, losing a finished
run's totals outright. A formatted **string** would lose mantissa precision. The triple is the
value's exact internal representation, so a save round-trips bit-for-bit.
`SaveParityTest.enormous values survive serialization without precision loss` asserts it.

Infinity has no JSON representation; a value can only reach it through a division by zero the
engine already guards, so it is written as `Double.MAX_VALUE` rather than as `null`, which would
revive as zero.

Boolean maps store only `true` entries. Integer maps drop anything below 1 — for `techOwned` and
`upgradesOwned`, absent and zero mean the same thing everywhere in the engine.

## Robustness

**Loading never throws.** Three layers:

### 1. Structural plausibility

`isPlausibleSave(root)` requires a numeric `saveVersion` and a present `runNumber`, `resources`,
`atmosphere`, `techOwned` and `prestige`. Anything failing it is rejected outright, so the caller
falls back to the backup slot rather than loading garbage.

### 2. Field-level defaults

Every field that is missing, of the wrong type, or nonsensical falls back to its default rather
than failing the whole load. A partially-damaged save costs the player one value, not the run.
Specifically:

- **Non-finite doubles are rejected.** JSON has no `Infinity` literal, but `1e999` is a perfectly
  legal token that parses to one — and a single infinite temperature or forcing turns every
  downstream value into `NaN` for the rest of the run: a save that is structurally valid but
  permanently unplayable. Nothing the encoder writes is ever non-finite, so this can only ever
  reject damage.
- **Negative and zero owned counts are dropped**, rather than flowing into ownership bonuses and
  cost curves as real numbers.
- **A damaged `GameDecimal` triple decodes as zero** rather than as an infinity.
- **Unknown fields are ignored** (`ignoreUnknownKeys = true`), so a save from a newer build still
  loads.

### 3. The two slots

```kotlin
sealed interface LoadResult {
    data object Empty : LoadResult                            // nothing stored → new game
    data class Loaded(val state: GameState) : LoadResult      // primary loaded cleanly
    data class RecoveredFromBackup(val state: GameState)      // primary unreadable, backup used
    data object Corrupted : LoadResult                        // both unreadable → new game, and say so
}
```

The provenance matters to the UI: a player whose primary save was corrupt is **told** their backup
was used, rather than silently handed a slightly older game. `SaveWarningDialog` surfaces both
non-clean outcomes.

### File-level corruption is a separate problem

A *save* can be unreadable while the file around it is fine. The **file itself** can also be
unreadable — a torn write at the filesystem level, or a proto that no longer parses. DataStore
signals that by throwing `CorruptionException` from every read and write for the life of the
process, which without a handler is a **permanent crash loop on launch** — the worst possible
failure for a game whose entire state is that one file.

`ReplaceFileCorruptionHandler` resets it to empty instead, and a process-wide flag remembers that
it happened so the next `load()` reports `Corrupted` rather than `Empty` — the player is told a new
game was started rather than silently handed one. `IOException` on read, write and clear is caught
separately, covering a full disk or a revoked volume.

## Testing

| Test | Covers |
| :-- | :-- |
| `SaveParityTest` (8) | A save the **reference implementation actually wrote** loads; both migrations against recorded output; corrupt and truncated saves rejected; round-trip; enormous values |
| `SaveMigrationTest` (12) | The chain end to end, idempotence, future-version saves, infinite values, negative counts, structureless input, a minimal save, unknown fields, a full round trip |
| `DataStoreSaveRepositoryTest` (instrumented + Robolectric) | Empty store, save/load, backup rotation, clearing both slots, real corruption of the primary slot |

## Adding a persistent field

See [Game state](Game-State.md#adding-a-field). The short version: give it a default, encode and
decode it, and only add a **migration** if an *existing* value is wrong under the new rules — a
merely-absent field is handled by the decoder's default.

---

**Next:** [Save migrations](Save-Migrations.md) · [Game state](Game-State.md) · [Offline progression](Offline-Progression.md)
