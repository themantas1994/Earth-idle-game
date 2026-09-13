# EARTH Codebase Audit

## Audit Date

2026-09-13, against `main` at commit `8a77ed3582b95ed239c5c8a2c947160642191002`
(merge of PR #15, "Turn the economy into a producer/processor production
chain"). This supersedes the audit section previously embedded in this file
(dated against an earlier, pre-production-chain commit); that content is
folded into the findings below where it is still accurate, and dropped
where it has been superseded.

## Repository State

- 39 commits total (24 non-merge, 15 merge commits — one per pull request,
  #1 through #15). Single contributor pair: `themantas1994` (repository
  owner) and `Claude` (agent commits), collaborating entirely through PRs
  from `claude/*` branches into `main`.
- Working tree is clean at HEAD; CI (`.github/workflows/android.yml`, run
  #19) is green on this exact commit (`conclusion: success`, verified via
  the GitHub Actions API).
- No uncommitted local changes; `local.properties` (SDK path) and the
  Gradle/Kotlin caches are present locally but correctly gitignored and not
  tracked.

## Executive Summary

EARTH is a native Android idle/incremental game (Kotlin + Jetpack Compose)
simulating civilization growth against a greenhouse-gas/climate model. It
began as a web app (React + Capacitor, commit `5652899`) and was fully
rewritten to native Kotlin over commits `9b92ba4`..`3c9c0c6` (2026-09-08).
The original TypeScript engine is kept, frozen, as a golden-fixture oracle
(`tools/ts-reference/`) and confirmed **not** wired into the Gradle build.

The domain layer (`app/src/main/java/.../domain/`, ~21k LOC) is verified
free of any Android, Compose, or presentation-layer import — a real,
enforced architectural boundary, not just a stated intention. All 424 unit
tests pass; lint is clean on both variants; `assembleDebug`,
`assembleRelease`, and `bundleRelease` all succeed. This is a
well-engineered, heavily self-documented codebase — the code comments
routinely explain *why*, including the history of bugs found and fixed in
prior audit passes.

**The most significant unresolved issue is not technical: there is still no
LICENSE file**, first flagged in commit `0f52c5c` (2026-09-10) and still
absent as of this audit (2026-09-13), while the README's own tagline and
footer describe the project as "open source." The README's License section
is self-aware of this and does not overstate it, but the contradiction is
project-facing and legally live. A second real, if minor, finding
originates in the most recent PR's own new UI code: an accessibility
misuse (`contentDescription` set to a debug tag string) that is also dead
code — introduced and never exercised by any test.

No secrets, credentials, or private keys were found anywhere in the
repository or its full git history. No web-runtime remnants (React,
Capacitor, WebView) exist in the shipped Android path. No analytics or
crash-reporting SDK is present; the only third-party SDK is Google Mobile
Ads (banner-only) plus its required UMP consent library.

## Architecture

Four layers, verified by import-direction grep across the whole
`app/src/main/java/com/earthgame/idle/` tree:

| Layer | Package | Verified property |
| :-- | :-- | :-- |
| Domain | `domain/` | **Zero** imports of `android.*`, `androidx.*`, `com.earthgame.idle.presentation.*`, or `com.earthgame.idle.data.*` anywhere in ~21k LOC. Pure Kotlin, testable on a bare JVM. |
| Data | `data/repository/`, `data/persistence/` | `repository/SaveRepository` is an Android-free interface; `persistence/DataStoreSaveRepository` is the sole Android-dependent implementation. |
| Platform | `platform/audio`, `platform/haptics`, `platform/ads` | Android-dependent by necessity (SoundPool, Vibrator, AdMob). One class (`MonetizationController`) reaches into `androidx.compose.runtime.mutableStateOf` directly rather than exposing a plain `StateFlow` — see Finding L-2. |
| Presentation | `presentation/` | Compose UI, ViewModel, navigation. Reads domain state; the presentation layer's screens contain **no** independent business-rule calculations — the composables that reference `GameDecimal`/`computeProductionRates`/etc. (`ProductionScreen.kt`, `HomeScreen.kt`, `AtmosphereScreen.kt`) only *call* domain functions to render their results, never reimplement logic. |

Navigation is a hand-rolled `Destination` enum (11 entries: 9 tabs + About +
Licenses) with a manual back-stack in `EarthApp.kt`, not Navigation Compose
— confirmed: `androidx.navigation` does not appear in `build.gradle.kts` or
the version catalogue. This is a deliberate, documented choice (removed in
commit `a3e1a0e` as an unused dependency after being present at scaffold
time — actually never wired to real navigation to begin with).

No singleton holds *mutable* global state outside two legitimate, narrowly-
scoped exceptions: `MonetizationController` (an activity-owned class, not a
Kotlin `object`, holding ad-consent state) and `GameViewModel`'s
`stateLock`/`loopLock`-guarded fields (see Concurrency, below). Objects such
as `ProductionGraph`, `SaveSerialization`, `EconomyDiagnostics`, and the
`BALANCE`/`CLIMATE`/`OWNERSHIP_BONUS` constant tables are stateless or hold
only immutable, once-computed derived data.

## Domain / Game Engine

`domain/engine/` — `GameLoop.kt` (session layer: absences, event rolls,
milestones, achievements, challenges, purchases, prestige resets) sits above
`Simulation.kt` (`simulateStep`, the "physics"). The separation is real:
`simulateStep` is a pure function of `(GameState, dtSeconds, prestige,
multipliers) -> SimulationStepResult`, called identically from the live tick
path and the offline catch-up path — verified by reading both call sites in
`GameLoop.advance` and `Offline.computeOfflineProgress`.

**Determinism**: `GameLoop` takes an injected `kotlin.random.Random`, not a
global source, so a seeded run reproduces its event/storm sequence exactly.
Storms advance on discrete 5-second steps keyed by `(runSeed, stepIndex)`
through a SplitMix64-style stream (`StormSimulation.kt`), not wall-clock
time, so live ticking and offline catch-up produce the identical storm
timeline — this is directly asserted by `StormSimulationTest`'s "one long
step vs many short steps" case, which passed. Production maths
(`computeProductionRates`) is a pure function of `(techOwned, multipliers)`
with no hidden dependence on execution order, UI frame rate, or device
speed: verified independently by `ResourceFlowTest.the answer does not
depend on the order the consumers are defined in`, which builds two
identically-valued but differently-ordered consumer lists and asserts
equal results (test passed).

**One documented, deliberate non-determinism-adjacent behaviour**: water
vapour (H2O) is a one-step-lagged feedback term rather than an
accumulating stock, matching the TypeScript reference's own behaviour
exactly (both were audited and confirmed to share this trait, per commit
`79b3bf7`). It affects no banked or scored value.

## Economy

The economy graph was rebuilt in the most recent PR (`ca209b5`,
2026-09-13) from a flat list of resource-producing "generators" into an
explicit producer/processor graph. Verified by independent parsing of the
technology source files (not by reading the PR's own prose):

- **157 technologies** total (`grep -c '^\s*id = "'` across all
  `*Technologies.kt` files, cross-checked by a from-scratch paren-balanced
  Python parser — both give 157).
- Kind breakdown: **78 `GENERATOR`** (producers), **26 `CONSUMER`**
  (processors), **28 `MULTIPLIER`**, **23 `UNLOCK`**, **2 `CHOICE`**.
  `78 + 26 + 28 + 23 + 2 = 157`.
- **16 branches** (`TechBranch` enum), matching the per-branch technology
  count exactly (10+13+7+7+16+8+11+12+7+9+10+12+5+10+8+12 = 157).
- **Zero duplicate technology IDs** across all 17 branch data files.
- **15 resources** (`ResourceId` enum): the original 6
  (Energy/Research/Coal/Oil/Steel/Concrete) plus 9 appended for the
  production chain (Iron/Copper/Uranium/RareEarths/Fuel/Chemicals/
  Electronics/AdvancedMaterials/LaunchCapacity). The original six are never
  reordered — new entries are appended, which matters because
  `ResourceAmounts` is ordinal-indexed and a save's balances are keyed by
  enum position.

Full producer, consumer and resource inventories are in
[docs/ECONOMY_AUDIT.md](ECONOMY_AUDIT.md). Full technology inventory
(all 157, field by field) is in
[docs/TECHNOLOGY_AUDIT.md](TECHNOLOGY_AUDIT.md).

### Graph integrity (independently re-derived, not read from prior claims)

A from-scratch Python re-implementation of the resource-dependency graph
(built directly from the parsed technology data, not from
`domain/production/EconomyValidation.kt`'s own logic) found:

| Check | Result |
| :-- | :-- |
| Resources with no producer at all | **None.** All 15 resources have at least one producer. |
| Resources with no consumer at all | **2**: `RESEARCH` and `CONCRETE`. Both are explicitly documented as intentional dead ends (`ResourceDefinition.deadEndReason`) — Research is spent on the tech tree, Concrete is spent on construction costs. |
| Resource-to-resource cycles | **8**, all confirmed anchored (see below). |
| Consumers whose rated output exceeds rated input (flow-positive) | **None** — every one of the 26 consumers produces `<=` as many units/second as it consumes, at rated capacity, which is the property that keeps a cyclic economy from being free resources. |

**Cycle anchoring** (independently verified): a cycle is only safe if at
least one step of it has a processor whose *inputs cannot be satisfied
entirely from resources internal to the cycle*. All 8 discovered cycles
were checked against this rule using a from-scratch implementation
(distinct from `EconomyValidation.kt`'s own `checkCycles`) and all 8 pass.
Example: `energy -> steel -> advanced_materials -> energy` is anchored
because `steel_mill` (the `energy -> steel` step) also requires `IRON`,
which the cycle does not produce.

`domain/production/EconomyValidation.kt`'s own `validateEconomy()` (15
rules: unsupplied inputs, dead-end resources, orphaned technologies,
self-sustaining loops, tier inversions, an impossible starting economy,
etc.) is exercised by `EconomyValidationTest` (12 tests, all passing) and
is also run at app startup in debug builds (`EarthApplication.onCreate` ->
`assertEconomyIsPlayable`), throwing in debug if the graph is ever broken
and only logging in release. This is a real, load-bearing safety net, not
a documentation claim.

**Two grandfathered tier inversions** exist and are explicitly allow-listed
in `EconomyValidationTest` rather than silently tolerated: `concrete`
(tier 11) requires `cement_production` (tier 12), and
`industrial_chemistry` (tier 11) requires `chemical_industry` (tier 15).
Both predate the production-chain PR and are pinned by reference-parity
fixtures, so fixing them would break parity with the frozen TypeScript
oracle. This is a known, accepted, and tested trade-off — not an
oversight.

## Producers

78 producers (`TechKind.GENERATOR`) — full table in
[docs/ECONOMY_AUDIT.md](ECONOMY_AUDIT.md#producers-78). 4 of the 78 produce no
`ResourceId` output at all and exist purely to emit or remove a gas:
`rice_cultivation` (CH4/N2O), `cfcs` (fluorinated, removes O3), `hfcs`
(fluorinated), `industrial_fluorinated_gases` (fluorinated). This is
intentional — they model real-world sources/effects that are gameplay-
relevant through the climate model rather than the resource economy — but
it means "producer" is a slightly broader category than "resource
producer"; a future glossary update could make this distinction explicit
(currently implicit).

## Consumers

26 processors (`TechKind.CONSUMER`), spanning refining (4), power
generation (6), metallurgy/materials (7), electronics (4), and space
industry (5). Every one of the 26 declares at least one input and at
least one resource output — verified by direct parsing, not by trusting
`EconomyValidationTest`'s own assertion of the same fact (both agree).
14 of the 26 have a single input resource; 12 have two or three
(multi-input processors whose utilization is bounded by their scarcest
input via a max-min-fair solver in `domain/production/ResourceFlow.kt`).

## Resources

15 total; 4 classes (`RAW`, `PROCESSED`, `ADVANCED`, `ENERGY`) plus
`SPECIAL` for Research. Every resource class has at least one member
(`RAW`: 6, `PROCESSED`: 4, `ADVANCED`: 3, `ENERGY`: 1, `SPECIAL`: 1 = 15).

## Technology Tree

157 technologies, 16 branches, tiers 0–39. Reachability: an independent
breadth-first closure from `STARTING_TECH_IDS = ["natural_fire"]` over the
`requires` graph reaches all 157 (re-derived independently in Python,
matching `TechnologyParityTest`'s own reachability assertion which also
passed). No orphaned or permanently-unreachable technology exists.
`TechnologyRegistry.kt`'s concatenation order (11 original branches, then
5 new ones, sorted stably by tier) keeps the reference implementation's 99
technologies in their original relative order, which `TechnologyParityTest`
asserts directly against the frozen TypeScript engine's own fixture.

## Milestones

The ownership milestone ladder (`domain/engine/Ownership.kt`) replaced a
flat "every 10th copy" rule with a progressively-widening one. Independently
re-derived in Python from the English specification in the code's own doc
comment (not copied from the Kotlin), then cross-checked against the
passing `MilestoneLadderTest` suite (15/15 passing):

| Owned | Next milestone | Units required |
| --: | --: | --: |
| 10 | 20 | 10 |
| 20 | 30 | 10 |
| 50 | 60 | 10 |
| 96 | 100 | 4 |
| 100 | **111** | 11 |
| 101 | 111 | 10 |
| 110 | 111 | 1 |
| 111 | **122** | 11 |
| 190 | **199** | 9 |
| 199 | 200 | 1 |
| 200 | **212** | 12 |
| 300 | 313 | 13 |
| 1000 | 1020 | 20 |
| 3000 | 3040 | 40 |

Formula: `step(owned) = 10 + 1 * (owned // 100)`, with the rule that a
milestone never crosses a block-of-100 boundary (so 100, 200, 300, … are
always exact milestones). This is a genuine logarithmic-*feeling*
progression (linear step growth per 100-unit block, not exponential),
matching the design brief's intent.

**Note on a documented divergence from a plausible reading of the original
design brief**: a brief describing this feature gave the worked example
"owning 190, next milestone 200, buys 10" — but the shipped algorithm
(190 → 199, buys 9) is what the block-boundary rule in the code's own
specification produces, and it is internally consistent (100→111,
111→122, …, 199→200 is the correct 11-unit-step sequence through the
100–200 block). The shipped behavior matches its own documented formula
exactly; it is the *example in a hypothetical brief*, not the code, that
would have been wrong to follow literally. This is flagged for
transparency, not as a defect.

## Buy System

Five modes (`presentation/GameViewModel.kt`, `BuyQuantity` enum): `ONE`,
`TEN`, `HUNDRED`, `NEXT`, `MAX`. `NEXT.resolveQuantity(owned)` returns
`getUnitsToNextMilestone(owned)` (exactly the milestone-ladder gap above);
`NEXT.requiresFullQuantity` is `true`, which is threaded through
`purchaseTechnology(..., requireFullQuantity = true)` in `Economy.kt` — a
partial purchase (affording, say, 8 of the 9 needed units) fails the
transaction atomically rather than silently buying 8. Verified both by
reading the code and by `NextPurchaseTest` (13/13 passing), including
`a partial next purchase changes nothing at all` and `the quote never
offers a purchase the engine would refuse`.

## Prestige

11 permanent upgrades (`PRESTIGE_UPGRADES` in `Prestige.kt`), independently
counted. Verified the offline-cap-related claim in the README ("up to 8
days with prestige"): `BASE_OFFLINE_CAP_SECONDS = 12h`; two upgrades grant
`offlineCapMultiplier = 2.0` per level — `extended_endurance` (maxLevel 3)
and `automated_industry` (maxLevel 1) — multiplying to `2^3 * 2^1 = 16`.
`12h * 16 = 192h = 8 days` exactly. The claim is accurate.

## Climate Simulation

Six gases (`GasId`: CO2, CH4, N2O, H2O, O3, FLUORINATED), each with its own
atmospheric half-life, decaying against the planet's own simulated age
(`gameAgeSeconds`, one real second = one simulated day) rather than
wall-clock time — a genuine bug fix from commit `dd56350`
(2026-09-13): before that change, half-lives in years were run against
*real* seconds, so CO2's 120-year half-life removed roughly 0.007% of
excess CO2 over a 3-day run instead of a meaningful fraction. Verified by
`AtmosphericHalfLifeTest` (19/19 passing), which checks the decay law
through the actual production integrator at one/two/three half-lives.

## Offline Progression

Offline catch-up is a single closed-form calculation
(`Offline.computeOfflineProgress`), not a stepped replay loop, made
possible because `simulateStep`'s gas integration is step-size
independent (asserted directly by `SimulationParityTest.step size
independence`). This property was extended, not reworked, for the new
production-chain economy: since ownership counts (and therefore
utilization ratios) are constant across an offline gap — nothing is
purchased while the player is away — a bottlenecked, multi-input processor
chain produces the same total as it would live, verified by
`ProductionChainLifecycleTest.a bottlenecked chain produces the same
amount offline as it does live` (compares one 6-hour step against 360
stepped 60-second calls, agreeing to 1e-9 relative tolerance; test
passed).

## Persistence

`SAVE_VERSION = 6`. **Every field of `GameState`** is round-tripped through
`SaveSerialization.encode`/`decode` — verified by exhaustively listing
every top-level `put(...)` call in `encode()` (69 keys/sub-keys) against
every field declared in `data class GameState` and its nested state
classes; no field was found to be silently dropped. `GameDecimal` values
are stored as an exact `{sign, mantissa, exponent}` triple rather than a
JSON number, avoiding the `1.8e308` Double ceiling. The storm seed is
stored as a string specifically because a 19-digit `Long` through a JSON
number loses precision (documented and tested:
`SaveMigrationTest`/`SaveParityTest`).

Migration chain: `v1→v2→v3→v4→v5→v6`, each gated by `if
(migrated.saveVersion < N)`, applied in strict order. A save from a
*newer* build than the running one keeps its own version number rather
than being stamped down (preventing a double-application of a rescale on
a later downgrade/upgrade cycle — this exact bug was found and fixed in
commit `4b21e0d`). Full per-field persistence table in
[docs/FEATURE_INVENTORY.md](FEATURE_INVENTORY.md).

## Android

- `applicationId` `com.earthgame.idle`; `minSdk` 24, `targetSdk`/`compileSdk`
  37; `versionCode` 1, `versionName` 1.0.0. Matches `ANDROID.md` exactly.
- **No web-runtime remnants**: zero references to React, React Native,
  WebView, Capacitor, or any JS runtime anywhere under `app/`; no
  `package.json` outside the intentionally-isolated `tools/ts-reference/`
  (whose own `package.json` states "Not part of the shipped app," and which
  is confirmed absent from every Gradle build file).
- App declares exactly one `Activity` (`MainActivity`, exported, launcher
  intent-filter) and one `Application` subclass (`EarthApplication`) in its
  own manifest. The app's own Kotlin code registers **no**
  `BroadcastReceiver`, `Service`, or `ContentProvider` of its own (grep
  confirmed).
- The **merged** release manifest (freshly built and inspected, not read
  from prior documentation) carries, from transitively-included libraries:
  **6 activities**, **5 services**, **9 receivers**, **2 providers** — all
  from Google Mobile Ads and AndroidX WorkManager/Startup/Profileinstaller.
  Every exported non-launcher component (`SystemJobService`,
  `DiagnosticsReceiver`, `ProfileInstallReceiver`) requires a permission
  (`BIND_JOB_SERVICE` or `android.permission.DUMP`) that an ordinary
  third-party app cannot hold, so none is practically exploitable by
  another app on the device.
- Permissions in the app's own manifest: `INTERNET`, `ACCESS_NETWORK_STATE`,
  `VIBRATE`. The merged manifest adds `AD_ID` and three
  `ACCESS_ADSERVICES_*` permissions (from `play-services-ads`), `WAKE_LOCK`
  and `FOREGROUND_SERVICE` (from WorkManager, transitively via AndroidX
  Startup — not used by any app code), and a self-declared
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` signature permission (an
  Android 13+ requirement surfaced by a transitive dependency, not
  something the app's own code registers a receiver against).
- A `<queries>` block (browsable HTTPS, `CustomTabsService`, MRAID intents,
  `com.android.vending`) comes entirely from the ads SDK. This is
  filtered package-visibility, not `QUERY_ALL_PACKAGES`.

## Jetpack Compose

Domain value types (`GameState`, `GameDecimal`, `Technology`, etc.) cannot
carry `@Immutable` without pulling `androidx.compose.runtime` into the
domain layer, which would violate the project's own architectural rule.
Instead they are declared stable via `compose-stability.conf` (referenced
from `app/build.gradle.kts`'s `composeCompiler { stabilityConfigurationFiles
... }`), a documented and measured trade-off (commit `a3e1a0e`: 43→68
effectively-stable classes, 3202→3230 arguments compared by value).

Screen-level composables cache expensive derived lists (`GENERATOR_
TECHNOLOGIES.groupBy { ... }` etc.) behind `remember(state.techOwned)`
rather than recomputing on every 250 ms tick — verified in
`ProductionScreen.kt`. `ALL_TECHNOLOGIES`/`GENERATOR_TECHNOLOGIES`/
`CONSUMER_TECHNOLOGIES`/`RESEARCH_TECHNOLOGIES` are top-level `val`s
computed exactly once at class-load, not per-frame.

**Finding (new, from the most recent PR)**: `TechCard.kt`'s new processor-
detail block sets `Modifier.semantics { contentDescription =
CONSUMER_DETAIL_TAG }` on the expandable detail `Column`, where
`CONSUMER_DETAIL_TAG = "consumer-detail"` is a plain string intended as a
test hook. This is the wrong API for that purpose: `contentDescription` is
read aloud by TalkBack, so a screen-reader user landing on that column
would hear the literal string "consumer-detail" rather than any
meaningful content. The correct primitive is `Modifier.testTag(...)`,
which does not affect the spoken accessibility tree. Compounding this,
`CONSUMER_DETAIL_TAG` is **never referenced by any test** (`grep -rn
CONSUMER_DETAIL_TAG app/src` finds only its declaration and this one use
site) — it is simultaneously dead code and a live accessibility defect.
Severity: LOW (the column is only reached by expanding a processor's
detail view, and its content is otherwise properly labelled), but real
and concretely verified.

## Performance

No O(n²) or worse pattern was found in the hot paths. `computeProductionRates`
iterates the 157-entry technology list once per call (not per-consumer),
and the resource-flow solver (`ResourceFlow.kt`) is bounded to
`PRODUCTION.maxSolverRounds = 12` fixed-point iterations over at most 26
consumers and 15 resources — a worst case in the low thousands of
arithmetic operations per tick, at 4 ticks/second, which is trivial for a
modern mobile CPU. No benchmark was run to produce a measured frame-time
figure in this audit; commit `a3e1a0e`'s own Compose-compiler-report
numbers (cited above) are the only measured performance data in the
repository, and they are cited as historical record, not re-verified
here.

## Security

No secrets, private keys, passwords, or API tokens were found in the
working tree or anywhere in the full git history (`git log --all
--diff-filter=A --name-only` for keystore/secret/password filenames
returns nothing). `keystore.properties` and `*.jks`/`*.keystore` are
gitignored and were never committed. CI decodes a keystore from a GitHub
Actions secret to a runner temp file and deletes it unconditionally
(`if: always()`) before uploading any artifact. The workflow correctly
uses the `pull_request` trigger (not `pull_request_target`), so GitHub
does not expose repository secrets to a fork's pull request build — the
comment in the workflow acknowledging "a pull request from a fork is not
blocked" is consistent with this being a safe, understood trade-off (an
unsigned build), not an oversight.

AdMob's publisher ID (`ca-app-pub-6872627319793193`) and banner unit ID
appear in `app/src/main/res/values/ads.xml` in plaintext. **These are not
credentials** — AdMob's own architecture requires these IDs to ship inside
every APK; the actual trust boundary is server-side at Google. They are
correctly isolated from the debug build via a resource overlay
(`app/src/debug/res/values/ads.xml`, Google's public sample IDs), verified
by `ProductionAdConfigTest` (10/10 passing), which also asserts no AdMob
identifier appears as a literal in any Kotlin/Java source file.

## Privacy

No analytics or crash-reporting SDK exists anywhere in the dependency
graph (`grep -riE "firebase|crashlytics|analytics|mixpanel|sentry|
bugsnag"` across build files returns nothing). The only network-capable
component is Google Mobile Ads (banner ad) plus the User Messaging
Platform (UMP) consent library, gated behind `canRequestAds` — verified:
`MobileAds.initialize` and the banner's `loadAd` are both behind that
flag, which is only set `true` after the UMP consent flow completes and
reports a permitting outcome (commit `31dee75` fixed a real prior defect
where the flag was fetched but not actually used to gate initialization).
The published privacy policy (`docs/PRIVACY_POLICY.md`) still contains
**three unfilled `OWNER ACTION REQUIRED` placeholders**: publish date,
data-controller legal entity name, and contact email — unresolved since
commit `de4146e` (2026-09-12) and still open in this audit.

## Dependencies

Full inventory in [docs/TECHNICAL_DEBT.md](TECHNICAL_DEBT.md#dependency-inventory).
15 production/test libraries via one version catalogue
(`gradle/libs.versions.toml`); no dependency was found declared but wholly
unreferenced except `androidx.compose.ui.tooling.preview`, which the
build file's own comment already discloses is unused pending the first
`@Preview` (confirmed: zero `@Preview` annotations exist in the codebase).
`androidx.window` is pulled in transitively by
`material3-window-size-class` and is likewise disclosed as unused by the
build file's own comment.

## Testing

**424 unit tests across 37 suites, 0 failures, 0 errors, 0 skipped**
(freshly executed for this audit: `./gradlew testDebugUnitTest
--rerun-tasks`, `BUILD SUCCESSFUL`). Plus 3 instrumented-test files
(`app/src/androidTest/`) that were not executed in this audit (no
hardware-accelerated emulator is available in this environment — the same
limitation every prior audit in this repository's history has recorded).
Full per-suite breakdown, coverage gaps, and weak-assertion notes in
[docs/TECHNICAL_DEBT.md](TECHNICAL_DEBT.md#test-coverage-gaps).

## Documentation

56 Markdown files (`README.md`, `ANDROID.md`, `docs/*.md`, `docs/wiki/*.md`);
`scripts/check-docs-links.py` (dependency-free, runs first in CI) reports
"all internal links resolve" — re-run for this audit, still true.
`scripts/third-party-notices.py --check` confirms all 149 release-classpath
modules are covered in `THIRD_PARTY_NOTICES.txt` — re-run for this audit,
still true. README numeric claims (technology/resource/gas/achievement/
challenge/event/milestone counts, offline cap figures) were independently
re-derived from source in this audit and all match. One stale figure was
found: `docs/RELEASE_BLOCKERS.md` still cites "220 unit tests" from an
earlier audit pass; the actual count is now 424. Direction and
conclusions in that document are otherwise unaffected.

## Git History

Full commit-by-commit history is reconstructed in [CHANGELOG.md](../CHANGELOG.md)
directly from `git log`, not summarized from memory. Six major phases are
visible: (1) web prototype, (2) Capacitor Android wrapper + balance pass,
(3) full native Kotlin/Compose rewrite with parity fixtures, (4)
post-rewrite bug-fix and documentation pass, (5) production-release
hardening (ads/consent/signing/legal), (6) atmospheric half-life
correctness fix + live 3D globe/storms + the production-chain economy
rewrite (most recent).

## Critical Findings

| ID | Finding |
| :-- | :-- |
| C-1 | No LICENSE file exists in the repository, while the README describes the project as "open source" in its tagline (line 5) and footer (line 434). Under default copyright, no one may legally redistribute or modify the source. The README's own License section (lines 395–403) discloses this, but the contradiction is live and unresolved since 2026-09-10. **Owner action required.** |
| C-2 | No production signing key exists (previously documented as O2 in `docs/RELEASE_BLOCKERS.md`, still open). Both release artifacts this audit built are unsigned. **Owner action required — not a code defect.** |
| C-3 | Privacy policy (`docs/PRIVACY_POLICY.md`) still contains 3 unfilled legal placeholders (date, data-controller entity, contact email). Publishing with these as-is would be a materially incomplete privacy disclosure. **Owner action required.** |

## High Priority Findings

| ID | Finding |
| :-- | :-- |
| H-1 | No AdMob consent message exists in the AdMob console (previously O5, still unverifiable from this repository — requires the owner's AdMob account). Until one exists, EEA/UK users see a banner with no consent message ever shown. |
| H-2 | The release build has never been run on a physical device (previously O6, still true — no emulator/hardware in any environment this project has been audited from). R8's minified output has never been executed. |
| H-3 | Launcher icon provenance is unrecorded (previously O4) — the bitmaps carry no source metadata and no attribution was invented. |

## Medium Priority Findings

| ID | Finding |
| :-- | :-- |
| M-1 | `TechCard.kt` sets `contentDescription` (spoken by TalkBack) to a debug-only tag string (`"consumer-detail"`) on the processor detail column, and the tag is never referenced by any test. Should use `Modifier.testTag(...)` instead, or be removed if genuinely unused. See Compose section above. |
| M-2 | `docs/RELEASE_BLOCKERS.md` cites a stale test count (220; actual is 424). Low-cost documentation drift, easily corrected. |
| M-3 | No dedicated component-level test exists for the new processor status card (utilization %, limiting-resource text, expand/collapse) — coverage is indirect, through `EarthAppScreenTest`'s broader screen assertions and the domain-level `ResourceFlowTest`. A focused Compose test for `TechCard`'s processor block would close a real gap. |

## Low Priority Findings

| ID | Finding |
| :-- | :-- |
| L-1 | `ProductionChainLifecycleTest.kt` contains 3 unnecessary non-null assertions (`!!`) on values the compiler already knows are non-null, following a signature change to `SaveSerialization.decode` (now returns non-nullable `GameState`). Confirmed by Kotlin compiler warnings during this audit's own test run. Harmless, but should be cleaned up. |
| L-2 | `MonetizationController` (a platform-layer class) uses `androidx.compose.runtime.mutableStateOf` directly rather than exposing a plain `StateFlow`, coupling a non-UI controller class to the Compose runtime. Deliberate and documented in its own comment; a `StateFlow`-based alternative would be marginally cleaner architecturally but is not a defect. |
| L-3 | `createAndroidComposeRule` (v1) is deprecated by AndroidX in favour of a v2 API; 3 test files still use it. Already tracked as non-blocking (M6) in `docs/RELEASE_BLOCKERS.md`. |
| L-4 | `androidx.compose.ui.tooling.preview` and (transitively) `androidx.window` are declared dependencies with zero current call sites. Both are disclosed as intentional in the build file's own comments. |

## Technical Debt

Full detail in [docs/TECHNICAL_DEBT.md](TECHNICAL_DEBT.md).

## Recommended Future Work

In priority order — see also the "Recommended priority order" in the final
chat response for this audit:

1. Resolve the three CRITICAL owner-action items (license, signing key,
   privacy-policy placeholders) — none require code changes.
2. Fix the `contentDescription`/`testTag` misuse in `TechCard.kt` (M-1) —
   small, isolated, no behavior change to sighted users.
3. Clean up the three unnecessary `!!` assertions (L-1) — trivial.
4. Add a focused Compose test for the processor status card (M-3).
5. Correct the stale test count in `docs/RELEASE_BLOCKERS.md` (M-2).
6. When ready, run the release build on a physical device (H-2) and obtain
   AdMob console consent-message configuration (H-1) before any public
   distribution.
