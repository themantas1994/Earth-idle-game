# Codebase audit

**Date:** 2026-09-10
**Scope:** the entire repository — Gradle configuration, manifest, R8, all source, resources, tests,
documentation, the TypeScript reference, CI and repository metadata.
**Baseline:** commit `654346e`, 142 JVM tests passing.
**After:** 205 JVM tests passing, zero lint issues in both variants, release bundle building.

This document separates **FIXED** from **REMAINING**. Nothing is listed as fixed unless the code
actually changed and a build or test confirmed it.

---

## Architecture assessment

**The architecture is sound and needs no restructuring.** That is the headline, and it is worth
stating plainly because the audit went looking for the opposite.

| | |
| :-- | :-- |
| **Layering** | `domain/` → `data/`/`platform/` → `presentation/`, pointing inward. Verified: `domain/`'s only non-project imports are `java.math`, `kotlin.math`, `kotlin.random` and `kotlinx.serialization.json`. No Android, no Compose, no coroutines. |
| **Purity** | `simulateStep(state, dt)` is pure, deterministic, and step-size independent — the last of which is a genuinely strong design decision, not a claim. It is what makes offline progress one calculation and eliminates the need for a background service. |
| **Abstraction** | Proportionate. Three platform adapters behind interfaces, one repository interface, and no DI framework for three dependencies. Nothing abstract exists without a caller that needs it. |
| **Presentation/domain separation** | Clean. The session *rules* (absences, event rolls, milestone firing, challenge settlement) live in `domain/engine/GameLoop.kt`; only coroutines, lifecycle and `StateFlow` plumbing are in the ViewModel. That split is why those rules are covered by fast JVM tests. |
| **Data-driven content** | 99 technologies, 32 achievements, 8 challenges, 13 events and 39 headlines are all data. Adding content needs no engine change. |
| **Comment quality** | Unusually high, and load-bearing — the maths *is* the product, and most comments explain a decision rather than restating code. Three were stale (see below); the rest were checked against the code and were accurate. |

**No inappropriate dependencies, no presentation logic in domain, no persistence in UI, no platform
assumptions in pure Kotlin, no god classes.** The largest engine file is 483 lines
(`SaveSerialization.kt`, which is inherently long-form) and the largest function is `simulateStep` at
~90 lines of clearly-sequenced steps.

**Dead code:** none found. Every public declaration has a caller; every technology is reachable from
the starting fire (asserted by a test); every branch is populated. The one deliberate stub is the
audio layer, documented as such.

---

## FIXED

### Critical

#### 1. A backwards device clock froze the game indefinitely

`GameLoop.advance` early-returned on `elapsedMs <= 0` without touching `lastTickAt`. A clock moved
backwards — a manual change, a time-zone edit, an NTP correction — left `lastTickAt` in the future, so
**every subsequent tick did nothing until the wall clock caught back up.** For a one-hour correction
that is an hour of watching a dead planet with no production and no explanation.

Now the tick **re-anchors**: nothing is simulated (no resource un-produced, no gas un-emitted) but
`lastTickAt` is set to `nowMs`, so the game resumes immediately.

> This changed an existing test's expectation. `time never runs backwards` asserted the state was
> *completely* unchanged; it is now `a backwards clock re-anchors the tick instead of rewinding the
> world`, plus a new `the game keeps running after the clock is moved backwards`. The original
> intent — nothing may be un-produced — is still asserted.

*Files:* `domain/engine/GameLoop.kt`, `GameLoopTest.kt`, `OfflineLifecycleTest.kt`

#### 2. A race between the tick and the player silently discarded purchases

The tick ran on `Dispatchers.Default`; player actions arrived on the main thread. Both did
*read `_uiState.value` → compute → write*, with the write as a `MutableStateFlow.update` whose
lambda rebuilt from a snapshot taken **before** the compute.

Interleaved, one side's work is erased. A purchase settled against a state the tick had since
replaced would undo itself — the technology un-bought and the resources refunded — or, in the other
order, a tick's simulated progress would roll back.

Every transition now runs as a read-compute-write under a `stateLock`, through a single
`mutate { current -> Transition(next, carried) }` helper. Side effects (haptics, audio, saving)
deliberately happen *outside* the lock, on what the transition carried out. The critical section is
one tick's work, so the main thread is never held for a visible frame.

> **Verified by experiment, not by reasoning.**
> `GameViewModelConcurrencyTest.aTickLandingInsideAPurchaseIsSerializedNotLost` forces the exact
> interleaving with a clock that blocks inside the purchase's critical section, and records **every
> state the ViewModel publishes** so a transient rollback cannot be repaired by a later tick before
> the assertion sees it. Reverting `mutate` to the unsynchronized form makes it fail
> (`a tick was rolled back by a purchase: lastTickAt 1700000001000 then 1700000000000`); restoring
> the lock makes it pass.
>
> Two invariant stress tests were also written first and are kept — but they did **not** reproduce
> the bug, because the natural window is a fraction of a percent of each 250 ms tick. They are a
> regression net, not a reproduction, and are labelled as such in the test.

*Files:* `presentation/GameViewModel.kt`, `GameViewModelConcurrencyTest.kt` (new)

#### 3. A corrupt DataStore file crash-looped the app on launch

`load()` called `context.saveDataStore.data.first()` with no corruption handler and no `IOException`
catch. DataStore throws `CorruptionException` from every read and write **for the life of the
process** when the file itself will not parse — so a torn write at the filesystem level meant the app
crashed on launch, every launch, permanently. For a game whose entire state is that one file, that is
the worst available failure.

Added `ReplaceFileCorruptionHandler`, which resets the file instead, plus a process-wide flag so the
next `load()` reports `Corrupted` rather than `Empty` — the player is *told* a new game was started
rather than silently handed one. `IOException` is now caught separately on read, write and clear,
covering a full disk or a revoked volume.

*Files:* `data/persistence/DataStoreSaveRepository.kt`

#### 4. Loading a save from a newer build could halve a player's Earth Points twice

`migrate()` ended with `if (saveVersion == SAVE_VERSION) it else copy(saveVersion = SAVE_VERSION)`,
which **stamped a future version down**. A player who ran a newer build and then a downgrade would
have their save marked v3; upgrading again re-ran `migrateV2ToV3`, dividing banked Earth Points by
250,000 **a second time**.

`migrate` now preserves a higher version. Fields the older build does not know are still dropped on
write (unavoidable on a downgrade); corrupting the ones it does know is not.

*Files:* `domain/save/Migrations.kt`, `SaveMigrationTest.kt` (new)

### Significant

#### 5. Non-finite values in a save permanently broke the run

JSON has no `Infinity` literal, but `1e999` is a legal token that parses to one. A single infinite
temperature or forcing turned every downstream value into `NaN` for the rest of the run — a save that
is structurally valid and permanently unplayable, with no error and no recovery.

`asDoubleOr` and `asDoubleOrNull` now reject non-finite values in favour of the field default.
Nothing the encoder writes is ever non-finite, so this can only reject damage.

#### 6. Negative owned counts from a save reached the cost curves

`decodeIntMap` accepted any integer. A hand-edited or damaged `techOwned` of `-5` flowed into
ownership bonuses and cost curves as a real number. Counts below 1 are now dropped — absent and zero
already mean the same thing everywhere in the engine. `tutorial.step` is clamped non-negative.

*Files (5, 6):* `domain/save/SaveSerialization.kt`, `SaveMigrationTest.kt`

#### 7. The compact number notation printed 1e69 as "1B", indistinguishable from 1e9

The alphabetic suffix fallback past `Vg` (1e63) started at a **single** letter, colliding with the
named short-scale suffixes: 1e69 → `B` (billions), 1e96 → `K` (thousands), 1e102 → `M`, 1e123 → `T`.
A run passes through both magnitudes of each pair, so a player genuinely could not tell 1e9 from 1e69
on the only screen that shows it.

The fallback now starts at two letters (`AA`, `AB`, …). Every named suffix is a single letter or mixed
case, so no all-caps pair can collide.

This was a **shared bug with the reference implementation**, so it was fixed in *both* engines and
`format.json` regenerated — parity holds. `FormattingParityTest` now asserts no suffix repeats across
0…1e900.

*Files:* `domain/formatting/NumberFormatting.kt`, `tools/ts-reference/engine/format.ts`,
`FormattingParityTest.kt`, `parity/format.json`

#### 8. Autosave kept writing every 15 seconds while the app was backgrounded

`onEnterBackground` cancelled the tick but not the autosave. With the tick stopped there was nothing
new to write, so it rewrote an identical save every fifteen seconds for as long as the app sat in the
background — pure wasted I/O and wakeups. Both loops now stop, via a `stopLoops()` that `onCleared`
also calls.

#### 9. The offline catch-up ran on the main thread

`onEnterForeground` called `tick()` synchronously from a lifecycle callback — so the whole absence
settlement, plus a `computeDerived`, ran on the main thread, contradicting the class's own documented
threading contract. It is now dispatched to the simulation dispatcher like every other step.

*Files (8, 9):* `presentation/GameViewModel.kt`

#### 10. Two challenge rewards advertised bonuses their effects did not grant

- **No Oil** promised "+20% Transportation branch production" for an effect that is a +5% *global*
  multiplier.
- **Speedrun** promised "+15% Earth Points from every future prestige" for an effect of
  `globalProductionMultiplier = 1.0` — literally nothing.

The **effects are the shipped balance and were left untouched**; the text was wrong, so the text was
corrected — in both engines, with `content.json` regenerated. Implementing Speedrun's promise would
have required a new `PrestigeUpgradeEffect` field and would have been a balance change, not a fix; it
is listed under REMAINING with the proposal.

*Files:* `domain/challenges/Challenges.kt`, `tools/ts-reference/engine/challenges.ts`,
`parity/content.json`

### Documentation accuracy

#### 11. Three comments named test classes that do not exist

`Ownership.kt` cited `BalanceTest`, `Constants.kt` cited `BalanceParityTest`, and `Migrations.kt`
cited `SaveMigrationTest` — **none existed.** Two claimed coverage of relationships nothing asserted.

Both gaps were closed by writing the tests rather than deleting the claims:
`BalanceInvariantsTest` (8 cases) and `SaveMigrationTest` (12 cases). The comments now name real
classes.

#### 12. `Fixtures.kt` told contributors to run a script that does not exist

Its error message said `npm run parity:fixtures`; the actual script is `npm run fixtures`. Anyone
hitting a missing fixture would have run a failing command. Corrected.

#### 13. `gradlew.bat` was permanently dirty in every clone

The committed blob carried CRLF while `.gitattributes` declares `gradlew.bat text eol=crlf`, so
`git status` showed it modified on a fresh checkout with no local change. Renormalized in the index;
the working tree still checks out as CRLF, as intended.

---

## Performance findings

| Finding | Outcome |
| :-- | :-- |
| **No accidental O(n²) anywhere** in the tick path. The two candidates are both already closed-form: buy-max inverts a geometric series with a logarithm (bounded to ≤4 exact corrections for float error), and ownership thresholds crossed are computed arithmetically rather than by walking units. | No change needed |
| **Per-gas/per-resource containers are flat arrays indexed by enum ordinal**, not hash maps, with mutable builders — a deliberate divergence from the reference's `Record<>` maps, correctly motivated. | No change needed |
| **`checkAchievements` allocates its result list lazily**, so 32 false predicates cost nothing on the overwhelming majority of ticks. | No change needed |
| **An 8-hour absence is one `simulateStep`, not 115,200.** The largest performance decision in the codebase, and it is architectural. | No change needed |
| **Autosave while backgrounded** | Fixed (#8) |
| **Compose skippability** — `GameState`, `GameDecimal`, `Technology` and the amount containers were all inferred *unstable* (Maps/Lists are interfaces; the containers hold a private array), so equal-valued arguments were compared by reference identity rather than by value. | Fixed, see below |

### Compose stability, measured honestly

Annotating the types `@Immutable` would put `androidx.compose.runtime` into `domain/`, breaking the
one architectural rule the project is built on. They are declared stable from the outside instead, via
`app/compose-stability.conf` and the Compose compiler's `stabilityConfigurationFiles`.

Measured with the compiler's own reports (`-Pearth.composeReports=true`):

| | Before | After |
| :-- | --: | --: |
| Effectively stable classes | 43 / 80 | **68 / 80** |
| Arguments compared by value | 3,202 | **3,230** |
| Skippable composables | 152 / 201 | 152 / 201 |

> **The skippable count did not change**, because strong skipping is on by default in Kotlin 2.x and
> already made those composables skippable using reference identity. The real gain is narrower: 25
> more classes and 28 more arguments now compare by *value*, so an equal-valued but
> newly-allocated argument can actually skip. This is a modest improvement and is not presented as
> more than that.

A `-Pearth.composeReports=true` flag was added to the build so the measurement is repeatable.

---

## Security findings

**No security issues found.** Every claim below was verified with a grep, not assumed:

| Checked | Result |
| :-- | :-- |
| Committed secrets, keys, tokens, keystores | **None.** `.gitignore` covers `*.jks`, `*.keystore`, `keystore.properties`. Signing comes from Gradle properties, env vars or a git-ignored file. |
| AdMob identifiers in `res/values/ads.xml` | Present and **correctly so** — public identifiers that ship in every APK, documented as not being secrets. |
| Network call sites | `grep -rn "HttpURLConnection\|OkHttp\|Retrofit\|URLConnection\|Socket\|WebView" app/src/main` → **nothing.** The only network traffic is the ad SDK's. |
| WebView remnants | **None.** This was a WebView app once; nothing remains. |
| Cleartext traffic | Not enabled, no network security config overriding the API-28+ default. |
| Exported components | **One:** `MainActivity` with the launcher filter. No services, receivers, providers or deep links. |
| Permissions | Three (`INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE`), all justified, all removable with ads. `AD_ID` is merged in by `play-services-ads` and must be declared on Play. |
| Logging | Nine `Log.w` calls, all in the ad adapter and the save repository, none logging game state, save content or identifiers. |
| Debug behaviour leaking to release | Test ad units and the EEA debug geography are both gated on `ApplicationInfo.FLAG_DEBUGGABLE` at runtime; `ui-tooling` is `debugImplementation`. |
| Dynamic code loading / reflection | **None.** |
| Deserialization | The app's own save only, into a `JsonObject` data tree — never reflective type instantiation. Nothing in a save can name a class. |
| Analytics / telemetry / tracking | **None.** |

The one hostile-input surface is a modified save file. It was hardened (#5, #6) and is documented in
[Security and privacy](wiki/Security-and-Privacy.md).

---

## Dependency audit

Every dependency was checked for actual use. Four were unused:

| Removed | Why |
| :-- | :-- |
| `androidx.navigation:navigation-compose` | **Zero references.** The app uses [an explicit destination stack](wiki/Navigation.md). |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | Unused; the `viewModels()` delegate comes from `activity-compose`. |
| `androidx.window:window` | No direct use; transitive through `material3-window-size-class`. |
| `androidx.test.espresso:espresso-core` | Unused; the instrumented tests use Compose test rules. |
| A duplicate `debugImplementation(compose-ui-test-manifest)` | Declared twice. |

> **Honest impact:** the release APK went from **3,733,335 to 3,733,287 bytes — 48 bytes.** R8 was
> already stripping the unused code. The benefit is a shorter list to keep current and audit, **not
> size.** Reported this way deliberately, because "removed four dependencies" reads like a size win
> and was not one.

`ui-tooling-preview` is **kept** despite nothing declaring an `@Preview`, so adding the first preview
is not also a build-file change. It is annotated as such in the build file.

Nothing was updated: every version in the catalogue is current for this toolchain, the build is
green, and updating without a reason is churn. No dependency was removed merely for being unfamiliar.

---

## Testing findings

**142 → 205 JVM tests**, all passing. The existing suite was strong — the parity approach in
particular is better verification than most projects have — but four areas claimed coverage they did
not have.

| Added | Cases | Closes |
| :-- | --: | :-- |
| `SaveMigrationTest` | 12 | The class three comments referenced and that did not exist. The chain end to end, **idempotence**, future-version saves, infinite values, negative counts, structureless input, a minimal save, unknown fields, a full round trip. |
| `GameDecimalEdgeCaseTest` | 20 | Invariants independent of the reference: normalization across every operand pair, canonical zero, total ordering, saturation, **formatting purity** (formatting must not mutate a live gameplay value), no NaN reachable, parse forms. |
| `OfflineLifecycleTest` | 18 | Every lifecycle event and clock anomaly a real device produces — gap granularity, the cap and repeat absences, backwards clocks, future and missing timestamps, absurd elapsed time, cold start after process death, first launch, opting out, challenges surviving an absence, no events rolled offline. |
| `BalanceInvariantsTest` | 8 | The pacing relationships the prose in `Constants.kt` and `Ownership.kt` asserts *about itself* and nothing checked: `r > 1`, gas growth under resource growth, research under generator cost, the ownership bonus under the price of the span that earns it, ladder continuity and monotonicity past the knee. |
| `GameViewModelConcurrencyTest` | 4 | The tick racing the player. One deterministic forced interleaving that **fails against the pre-fix code**, plus two invariant stress tests. |
| `GameLoopTest` | +1 | The game resumes after a backwards clock. |
| `FormattingParityTest` | +1 assertion | No compact suffix repeats across 0…1e900. |

### A note on test honesty

Two of the stress tests in `GameViewModelConcurrencyTest` were written believing they would reproduce
the race. **They did not** — the natural window is too small. Rather than leave a comment claiming
they do, the test's documentation says exactly what each one is for, and a deterministic test was
written afterwards to actually prove the bug and the fix. A test whose docs overstate it is worse
than no test.

---

## Documentation changes

| | |
| :-- | :-- |
| `README.md` | **Rewritten for players.** Opens with what the game is, how it plays, what makes it different, how offline progression works, and an accurate feature table. FAQ, privacy section, verified install instructions, and links into the wiki. Every count was read out of the source. |
| `docs/wiki/` | **31 new pages.** All 29 required, plus `Accessibility.md`. |
| `docs/DEVELOPER_OVERVIEW.md` | New. The five-minute orientation. |
| `docs/CODEBASE_AUDIT.md` | This file. |
| `docs/GITHUB-METADATA.md` | New. Recommended description, topics, licence and repository settings — explicitly marked as **not applied**. |
| `docs/MIGRATION.md` | Kept. Still accurate as the port's historical record; two figures refreshed. |
| `ANDROID.md` | Kept, with the stale test count corrected. Now largely superseded by [Build system](wiki/Build-System.md) and [Release process](wiki/Release-Process.md); both link back. |

**Every count, constant, formula, path and command in the documentation was read out of the source or
executed.** Where a claim could not be verified — hardware behaviour, frame timings, screen-reader
output — the documentation says so rather than implying otherwise.

---

## REMAINING

Nothing here is a regression. Each is either a deliberate non-goal, a decision that belongs to the
repository owner, or work that needs a device.

### Blocking a genuine open-source release

**1. There is no licence file.** The default applies — all rights reserved — so nobody may legally
copy, modify or redistribute the source, and a contribution cannot be accepted under defined terms.
Choosing a licence is the owner's decision, so none was added. **This is the single most important
outstanding item.** See [GITHUB-METADATA.md](GITHUB-METADATA.md).

**2. Repository metadata is empty** — no description, no topics, no homepage, and an enabled but
empty wiki tab. The session's tooling can read metadata but not write it, so nothing was changed.
Exact recommended values are in [GITHUB-METADATA.md](GITHUB-METADATA.md).

### Gameplay quirks, documented rather than changed

**3. The Speedrun challenge grants no reward.** Its effect is `globalProductionMultiplier = 1.0`.
Its text now says so honestly, but a challenge with no reward is poor design. Implementing the
original intent needs a new field:

```kotlin
// PrestigeUpgradeEffect
val earthPointsMultiplier: Double? = null
// PrestigeMultipliers: fold as earthPoints *= it.pow(level)
// calculatePrestigeGain: multiply the result by prestige.earthPointsMultiplier
```

That is a **balance change** (+15% EP for anyone who completes it), so it is an owner decision.
Mirror it in the reference and regenerate fixtures.

**4. The Zero Emissions goal text mentions an unenforced ceiling.** "…while total gross gas
production stays under 1e6 kg/s" is not checked anywhere; the constraint is expressed through the
disabled-technology list. The text is pinned by `ContentParityTest`, so correcting it means changing
both engines and regenerating — a small, safe change, but a gameplay-text one.

**5. `PrestigeAccumulator` ignores `level` for `startingResources`.** Every other effect field is
raised to the power of, or multiplied by, the level; this one adds the flat amount once. Unobservable
today (every upgrade granting starting resources has `maxLevel = 1`) and would silently under-grant
the moment a repeatable one is added.

**6. Sink efficiency inflates the water-vapour equilibrium.** The H₂O target is fed in as
`equilibrium × k_raw` while the integrator divides by `k_raw × sinkEfficiency`, so the effective
equilibrium is `target / sinkEfficiency` — a hotter planet holds proportionally *more* feedback water
vapour than the target alone implies. **This matches the reference exactly** and contributes to the
late-game temperature curve, so changing it would be a rebalance. Documented in
[Climate model](wiki/Climate-Model.md).

### Verification gaps

**7. Nothing has been run on physical hardware or an emulator.** This environment has no KVM. What
*was* run: `test` (205 passing), `lintDebug`, `lintRelease`, `assembleDebug`, `assembleRelease`,
`bundleRelease`, `assembleDebugAndroidTest` (the instrumented suite compiles and packages), and the
reference's own 198 tests. What was **not**: `connectedAndroidTest`. Unverified by execution: the
vibrator, `SoundPool`, the Mobile Ads SDK and its consent form, frame timings, jank, allocation
rates, startup time, and TalkBack.

**8. No performance benchmarks exist.** The performance reasoning is architectural and the Compose
figures come from the compiler, but nobody has profiled a tick. A JMH-style benchmark over
`simulateStep` at a late-game state would be cheap — the function is pure and needs no device.

**9. Accessibility is correct by construction but unverified.** Semantics merging, hidden decoration,
48 dp targets and reduced-motion support are all in place and covered by `EarthAppScreenTest`, but no
screen reader has read a screen and no contrast ratio has been measured against WCAG AA. Also known:
the bottom bar's 9 sp labels across nine columns will clip at the largest font scales (the icon and
the content description still identify the tab).

### Technical debt

**10. The layering rule is not enforced automatically.** `domain/` having no Android dependency is the
project's load-bearing rule and is currently maintained by convention and review. A Konsist-style
test, a lint rule, or splitting `domain/` into its own pure-Kotlin Gradle module would make it
mechanical. The module split is the strongest option and the most disruptive.

**11. The Gradle configuration cache is disabled**, because Kotlin's build-tools classpath does not
serialise cleanly into it on this AGP/KGP pair. Worth retrying on the next toolchain bump.

**12. No audio assets ship.** The settings, the plumbing and the seam are all real; every `Sound` has
a `null` resource. The same state the original build was in. Adding a raw resource and pointing a
`Sound` at it is the whole change — see [Audio and haptics](wiki/Audio-and-Haptics.md).

**13. No screenshots exist anywhere in the repository** (only launcher icons). The README shows none
deliberately: a fabricated screenshot of an app nobody has run would be worse than none.

**14. `proguard-rules.pro`'s serialization rules are currently belt-and-braces.** The save format is
hand-written `JsonObject` building rather than `@Serializable` classes, so nothing needs them today.
They are correct, cost nothing, and become load-bearing the moment anyone adds a `@Serializable`
model.

**15. Clock-forward exploitation is undefended, deliberately.** Jumping the device clock forward grants
up to one capped absence. A single-player offline game with no leaderboard has no competitive surface,
and a monotonic elapsed-time source would mis-measure real absences across reboots — a worse bug than
the exploit.

**16. `EarthAppScreenTest` uses a deprecated `createAndroidComposeRule`.** The compiler suggests the
`junit4.v2` variant, which switches from `UnconfinedTestDispatcher` to `StandardTestDispatcher` and
would need explicit synchronisation in the tests. Migrate deliberately, not incidentally.

---

## Recommended next steps

In order.

1. **Add a licence.** Everything else about presenting this as open source is blocked on it.
2. **Set the repository description and topics** — [GITHUB-METADATA.md](GITHUB-METADATA.md) has the
   exact strings.
3. **Build the debug APK and play it on a real phone.** First purchase, a background/resume cycle to
   watch offline progress land, a collapse and a reset. That closes most of gap 7 in an hour, and it
   is the only thing that can.
4. **Run `connectedAndroidTest`** on that device.
5. **Populate the GitHub Wiki from `docs/wiki/`, or turn the wiki tab off.** An empty wiki next to 31
   pages of documentation is a dead end for anyone who clicks it.
6. **Publish a release** — a tag, a GitHub release, and a signed APK attached. See
   [Release process](wiki/Release-Process.md). No amount of README work substitutes for a
   downloadable build.
7. **Add screenshots** from step 3 to the README.
8. **Decide on the Speedrun reward** (item 3) and the Zero Emissions text (item 4).
9. **Add root `CONTRIBUTING.md` and issue templates** pointing at the wiki. Device model and Android
   version are the two things every bug report needs.
10. **Enforce the layering mechanically** (item 10) — a Konsist test is an afternoon; the module split
    is a weekend.
11. **Add a `simulateStep` benchmark** (item 8) at a late-game state, so future formula changes have a
    performance baseline.
12. **Run TalkBack over every screen** and measure the contrast ratios (item 9).

---

## Validation performed

| Command | Result |
| :-- | :-- |
| `./gradlew testDebugUnitTest` | ✅ **205 tests, 0 failures** |
| `./gradlew lintDebug` | ✅ **0 issues** |
| `./gradlew lintRelease` | ✅ **0 issues** |
| `./gradlew assembleDebug` | ✅ |
| `./gradlew assembleRelease` | ✅ unsigned APK, 3,733,287 bytes |
| `./gradlew bundleRelease` | ✅ AAB produced |
| `./gradlew assembleDebugAndroidTest` | ✅ the instrumented suite compiles and packages |
| `npx vitest run` (reference) | ✅ **198 tests, 0 failures** |
| `npm run fixtures` (reference) | ✅ regenerated; only `format.json` and `content.json` changed, both intentionally |
| `./gradlew connectedAndroidTest` | ❌ **NOT RUN** — no KVM in this environment, so no emulator can boot and no device is attached |

The Android SDK (platform 37, build-tools 37.0.0) was installed in this environment specifically so
these builds could actually be executed rather than reasoned about.
