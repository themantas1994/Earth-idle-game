# Save migrations

[← Documentation home](Home.md)

`domain/save/Migrations.kt`. Current `SAVE_VERSION` is **3**.

Each step moves a save one version forward, and they run in order, so a save written by any
released version reaches the current shape by falling through the chain. The version numbers and
the fix-ups are inherited from the web build, because a save exported from that build has to keep
working.

```kotlin
fun migrate(state: GameState): GameState {
    var migrated = state
    if (migrated.saveVersion < 2) migrated = migrateV1ToV2(migrated)
    if (migrated.saveVersion < 3) migrated = migrateV2ToV3(migrated)
    return if (migrated.saveVersion >= SAVE_VERSION) migrated
           else migrated.copy(saveVersion = SAVE_VERSION)
}
```

Run inside `deserialize`, after decoding and before the state reaches the engine. A save is written
back at whatever version it now carries.

## v1 → v2: tapping was removed

v2 removed manual tapping. Energy now comes from Natural Fire, which every run owns from the start
— but a v1 save can be missing that generator entirely: it used to be a zero-output unlock, and a
run begun before it was granted has nothing at all. Left alone the player would have no income and
no way to earn any, so it is granted here.

The "Tap Conditioning" prestige upgrade went with it. Refunding what was spent on it — a geometric
series at its old 1.8 growth from a base of 50 — is the only fair option: the player bought
something the game no longer contains.

```kotlin
val tapLevels = upgrades.remove("tap_conditioning") ?: 0
val refund = if (tapLevels > 0) gd(50.0) * (gd(1.8).pow(tapLevels) - ONE) / 0.8 else ZERO
techOwned = techOwned + ("natural_fire" to max(1, techOwned["natural_fire"] ?: 0))
```

## v2 → v3: the economy was rebuilt

Two things in a v2 save are no longer worth what they were, and **both are about Earth Points
rather than the run itself** — a run in progress only ever gets *cheaper*, since the complexity
surcharge is gone and no price rises any more, so it needs no fixing up.

**1. The prestige payout was rescaled by roughly 10⁵.** The old formula multiplied by raw
civilization level, which runs into the thousands, and a single completed run paid out enough to
buy the entire upgrade tree at once. Banked points and lifetime totals are divided by
`250_000.0` — the ratio between what a completed run used to pay and what one pays now — so a
returning player's balance buys about what it bought before.

It is a single ratio against two differently-shaped formulas, so it is an **approximation**, and
deliberately a generous one: erring toward giving a player too much is the kinder failure.

**2. "Institutional Memory" used to cancel the complexity surcharge**, which no longer exists.
Rather than delete it, it now grants +40% production per level, so levels already bought keep
paying — and it is cheaper than it was, so nobody overpaid. No migration code is needed for this;
it is a data change that a v2 save picks up for free.

## Saves from a newer build

A save whose `saveVersion` is **greater** than `SAVE_VERSION` keeps its own version number rather
than being stamped down.

Stamping it down would mean that upgrading back to the newer build re-ran migrations the save had
already been through — running `migrateV2ToV3`'s rescale a second time would divide a player's
banked Earth Points by 250,000 again. Fields this build does not know about are still dropped on
the next write, which is unavoidable on a downgrade; **corrupting the ones it does know about is
not.**

`SaveMigrationTest.a save from a newer build keeps its own version` and
`a future save is not rescaled a second time by the v3 migration` guard it.

## Adding a version

1. Bump `SAVE_VERSION` in `domain/model/GameState.kt`.
2. Add **one** `if (migrated.saveVersion < n) migrated = migrateVn1ToVn(migrated)` block, in order.
3. Write the migration as a pure `GameState -> GameState` that sets `saveVersion = n` itself.
4. **Make it idempotent.** `SaveMigrationTest.migrating twice is the same as migrating once`
   asserts it for the whole chain, and it is the property that stops a replay from double-applying.
5. Add a case to `SaveMigrationTest` and, if you want it pinned against the reference, mirror the
   migration in `tools/ts-reference/engine/save.ts` and regenerate `save.json`.
6. Update this page.

> **Only migrate what is actually wrong.** A field that is merely *absent* from an older save needs
> no migration at all — the decoder's default handles it. A migration is for a value whose old
> meaning is incorrect under the new rules.

> **Never destroy a valid save.** A migration that cannot make sense of its input should leave the
> field at a safe default and let the run continue, not return null and send the player to the
> backup slot. The rejection path exists for structurally broken saves, not for surprising ones.

---

**Next:** [Save system](Save-System.md) · [Game state](Game-State.md)
