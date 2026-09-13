# EARTH Technical Debt

[← Documentation home](wiki/Home.md) · [Codebase audit](CODEBASE_AUDIT.md)

Everything below is a real, verified item — not a hypothetical. Each is
classified by whether fixing it changes behaviour a player would notice.

## Outstanding defects (verified this audit)

| ID | Item | Evidence | Fix cost |
| :-- | :-- | :-- | :-- |
| M-1 | `TechCard.kt:374` sets `Modifier.semantics { contentDescription = CONSUMER_DETAIL_TAG }` on the processor detail column. `contentDescription` is spoken by TalkBack; a screen-reader user would hear the literal string `"consumer-detail"`. The tag is also never referenced by any test (`grep -rn CONSUMER_DETAIL_TAG app/src` finds only its declaration and this one use). | Direct code read + grep, this audit | Trivial — replace with `Modifier.testTag(CONSUMER_DETAIL_TAG)`, or delete the constant and the modifier if truly unused. |
| L-1 | `ProductionChainLifecycleTest.kt` (lines 226, 263, 281) has 3 unnecessary `!!` non-null assertions on `SaveSerialization.decode(...)`, which now returns a non-nullable `GameState`. Confirmed by live Kotlin compiler warnings during this audit's `testDebugUnitTest --rerun-tasks` run. | Compiler warning, this audit | Trivial — delete the `!!`. |
| L-3 | 3 test files (`AboutScreenTest.kt`, `EarthAppScreenTest.kt`, `GameHeaderTest.kt`) use the deprecated v1 `createAndroidComposeRule`. Confirmed by live compiler warnings. Already tracked as non-blocking finding M6 in `docs/RELEASE_BLOCKERS.md`. | Compiler warning, this audit | Small — migrate to `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule`; tests may need explicit `waitForIdle()` calls since the v2 API queues rather than runs immediately. |
| M-2 | `docs/RELEASE_BLOCKERS.md` cites "220 unit tests" from an earlier audit pass; actual count is now 424. | `find app/build/test-results -name '*.xml'` count, this audit | Trivial — update the number. |

## Documentation drift (verified this audit)

| Doc | Claim | Actual | Severity |
| :-- | :-- | :-- | :-- |
| `docs/RELEASE_BLOCKERS.md` | "220 unit tests" | 424 (37 suites) | Low — number only, conclusions unaffected |
| README (tagline, footer) | "open source" | No LICENSE file exists; default all-rights-reserved applies | Critical — see C-1 in `CODEBASE_AUDIT.md`. The README's own License section (lines 395–403) already discloses this, so the document is self-aware but the underlying fact is unresolved. |
| `docs/PRIVACY_POLICY.md` | Published-looking privacy policy | 3 unfilled `OWNER ACTION REQUIRED` placeholders (date, controller entity, contact email) | Critical — see C-3 |

All other README/wiki numeric claims checked in this audit (technology
count, resource count, gas count, achievement/challenge/event/milestone
counts, offline-cap hours, branch count) were independently re-derived
from source and found **accurate**. `scripts/check-docs-links.py` (0
broken links across 56 files) and `scripts/third-party-notices.py --check`
(149/149 modules attributed) both re-run clean.

## Dependency inventory

Every entry in `gradle/libs.versions.toml`, checked against
`app/build.gradle.kts` usage.

| Name | Version | Purpose | Scope | Necessary? | Note |
| :-- | :-- | :-- | :-- | :-- | :-- |
| `androidx.core:core-ktx` | 1.19.0 | Kotlin extensions for AndroidX core | Production | Yes | — |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.11.0 | Lifecycle-aware coroutines | Production | Yes | — |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | `collectAsStateWithLifecycle` etc. | Production | Yes | — |
| `androidx.lifecycle:lifecycle-process` | 2.11.0 | Process-level lifecycle (foreground/background detection) | Production | Yes | Used by `MainActivity`'s process-lifecycle observer, not the Activity lifecycle — deliberate, so a rotation is not mistaken for backgrounding. |
| `androidx.activity:activity-compose` | 1.13.0 | `ComponentActivity` + Compose integration | Production | Yes | — |
| `androidx.compose:compose-bom` | 2026.08.00 | Version alignment for all Compose artifacts | Production | Yes | — |
| `androidx.compose.ui:ui`, `ui-graphics` | (BOM) | Core Compose | Production | Yes | — |
| `androidx.compose.ui:ui-tooling-preview` | (BOM) | `@Preview` annotation artifact | Production | **Confirmed unused** — zero `@Preview` sites exist | Disclosed as intentional in the build file's own comment ("kept so adding the first preview is not also a build-file change"). Low-cost to keep, honestly labelled. |
| `androidx.compose.ui:ui-tooling` | (BOM) | Preview renderer | `debugImplementation` | Same as above | Debug-only, zero release-APK cost. |
| `androidx.compose.material3:material3` | (BOM) | Material 3 components | Production | Yes | — |
| `androidx.compose.material3:material3-window-size-class` | (BOM) | Window-size breakpoints (tablet/foldable layout) | Production | Yes | Pulls in `androidx.window` **transitively**; the app has no direct `androidx.window` call site (confirmed by grep). Disclosed in the build file's own comment. |
| `androidx.datastore:datastore-preferences` | 1.2.1 | Save persistence | Production | Yes | — |
| `kotlinx-coroutines-android` | 1.11.0 | Coroutines on Android | Production | Yes | — |
| `kotlinx-coroutines-test` | 1.11.0 | Virtual-time coroutine testing | Test | Yes | Used by `GameViewModelTest`'s virtual-time assertions. |
| `kotlinx-serialization-json` | 1.11.0 | Save-file JSON | Production | Yes | — |
| `com.google.android.gms:play-services-ads` | 25.4.0 | AdMob banner | Production | Yes (monetization) | Pulls in the merged-manifest components documented in `CODEBASE_AUDIT.md`'s Android section. |
| `com.google.android.ump:user-messaging-platform` | 4.0.0 | EEA/UK consent flow | Production | Yes | Required alongside AdMob for GDPR-region compliance. |
| `junit` | 4.13.2 | Test framework | Test | Yes | — |
| `androidx.test.ext:junit` | 1.3.0 | AndroidX JUnit extensions | Test | Yes | — |
| `robolectric` | 4.16.1 | JVM-hosted Android framework shadow | Test | Yes | Runs the DataStore and Compose UI test suites without an emulator — the only reason this repo's UI is tested at all in this environment. |
| `androidx.test:core` | 1.7.0 | AndroidX test core | Test | Yes | — |
| `androidx.compose.ui:ui-test-junit4` | (BOM) | Compose test rules | Test | Yes | — |
| `androidx.compose.ui:ui-test-manifest` | (BOM) | Test-activity manifest for Compose UI tests | `debugImplementation` | Yes | Declared once (a prior audit found and fixed a duplicate declaration — commit `a3e1a0e`; confirmed not duplicated now). |

**No dependency was found declared and never referenced anywhere in the
codebase** (the two flagged above are referenced — transitively or by
plugin machinery — and are honestly disclosed as not directly called).
**No outdated-with-known-CVE dependency was identified**; a full CVE
database check against these exact versions was not performed as part of
this audit (would require network access to a vulnerability database,
which this environment does not have configured for that purpose — "not
verified" rather than "clean").

## Test coverage gaps

37 test suites, 424 tests, 0 failures (verified by fresh execution this
audit). Coverage by area:

| Area | Suites | Notable gap |
| :-- | :-- | :-- |
| Economy / production chain | `EconomyValidationTest`, `ResourceFlowTest`, `NextPurchaseTest`, `MilestoneLadderTest`, `ProductionChainLifecycleTest`, `EconomyBalanceSimulationTest`, `EconomyDiagramTest` (7 suites, ~88 tests) | Comprehensive at the domain level. **No dedicated Compose test for `TechCard`'s new processor status block** (utilization %, limiting-resource text, expand/collapse) — only indirect coverage via `EarthAppScreenTest`'s broader screen-level assertions. |
| Reference parity | 11 suites (`parity/*`) | Comprehensive; deliberately does not (and should not) cover content the reference never had — see `docs/wiki/Reference-Parity.md`. |
| Simulation / engine | `GameLoopTest`, `AtmosphericHalfLifeTest`, `GameAgeTest`, `OfflineLifecycleTest`, `GameDecimalEdgeCaseTest` (5 suites, ~91 tests) | Comprehensive. |
| Storms | 4 suites (~44 tests) | Comprehensive at the simulation level; the OpenGL rendering path itself is untested (no hardware-accelerated emulator available in any environment this project has been audited from — a long-standing, disclosed limitation). |
| Presentation / UI | `EarthAppScreenTest`, `GameViewModelTest`, `GameViewModelConcurrencyTest`, `GameHeaderTest`, `AboutScreenTest`, `EnvironmentalVisualizationTest` (6 suites, ~91 tests) | Robolectric-hosted, so real device rendering/input latency is untested. `GameViewModelConcurrencyTest` (4 tests) covers one deterministic forced interleaving plus stress tests — a small suite for a genuinely hard problem (concurrent tick vs. purchase), though the underlying lock discipline was independently read and confirmed sound in this audit. |
| Release/build config | `ProductionAdConfigTest` (10 tests) | Confirms identifiers and variant isolation; does not (and cannot, without a device) exercise a live ad request. |
| Save/persistence | `SaveMigrationTest` (unit), `DataStoreSaveRepositoryTest` + `DataStoreCorruption.kt` (instrumented, **not executed in this audit** — no emulator) | The instrumented DataStore tests are a real gap in *this specific audit's* verification, though `SaveParityTest` and Robolectric-backed paths cover the encode/decode logic itself. |

No test was found to assert only an implementation detail with no
behavioural meaning (a common weak-test anti-pattern) in the suites
sampled during this audit; assertions consistently check either a
parity-fixture value, a documented invariant, or an observable UI/state
outcome.

## Compose-layer notes

- Domain types are declared "stable" to the Compose compiler via
  `compose-stability.conf` rather than `@Immutable` annotations, to avoid
  pulling `androidx.compose.runtime` into the domain layer. This is a
  deliberate, measured trade-off (commit `a3e1a0e`), not debt, but it does
  mean stability is enforced by a config file kept in sync by hand rather
  than by the compiler checking the annotation against the actual type —
  a future refactor of a domain class that breaks its stability
  assumptions would not be caught until a recomposition regression is
  noticed empirically (no test currently asserts recomposition counts).
- `MonetizationController` couples a platform-layer class directly to
  `androidx.compose.runtime.mutableStateOf` rather than exposing a plain
  `StateFlow`. Functionally fine and documented; a `StateFlow`-based
  version would be marginally more consistent with the rest of the
  platform layer's Android-without-Compose design.

## Architecture notes (not defects, but worth recording)

- The domain/presentation boundary is real and enforced by convention
  (verified: zero violating imports), not by a build-time check (e.g. a
  Gradle module boundary or a lint rule). A future contributor could
  introduce an `androidx.compose` import into `domain/` and nothing would
  fail except human review. Worth considering a dedicated lint rule or a
  separate Gradle module if the team grows.
- `ProductionGraph`, `EconomyDiagnostics`, and `SaveSerialization` are
  Kotlin `object`s (module-level singletons) holding derived-but-immutable
  data computed once at class-load. This is idiomatic and safe (verified:
  no mutable `var` at object scope in any of them), but it does mean their
  initialization order is JVM classloader-determined rather than
  explicit — not a practical risk at this codebase's size, but worth
  noting if the technology table ever needs to be swapped at runtime
  (e.g. for a future mod-support or A/B-test feature, neither of which
  exists today).

## What is genuinely finished (no action needed)

- Domain/presentation layering (verified clean).
- Architecture for offline progression (single closed-form step, verified
  correct for the new production chains without modification).
- Save round-trip completeness (every `GameState` field persisted,
  verified exhaustively).
- GameDecimal precision behaviour at the extremes (NaN → zero rather than
  propagating; overflow → a saturated Infinity sentinel rather than a
  silent wraparound; `.toDouble()` is documented and consistently used
  only for UI/comparison purposes, never for accumulating game-state
  values, across all 21 non-formatting call sites checked in this audit).
- CI pipeline (docs links → unit tests → lint → notices check → debug
  build → release build+sign → artifact upload), all green on the current
  `main`.
