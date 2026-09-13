# EARTH Feature Inventory

[← Documentation home](wiki/Home.md) · [Codebase audit](CODEBASE_AUDIT.md)

Every feature below was verified against the current `main` (commit
`8a77ed358`) by reading the implementing code, and cross-checked against a
passing test where one exists. "Implemented" is only marked after that
verification — not from README or wiki claims.

| Feature | Implemented? | Location | Tested? | Documented? | Risk |
| :-- | :-- | :-- | :-- | :-- | :-- |
| Idle/passive production | Yes | `domain/engine/Simulation.kt` `computeProductionRates` | Yes — `GameLoopTest`, `SimulationParityTest` | Yes — Simulation.md | Low |
| Producer/processor economy | Yes | `domain/production/` (`ProductionGraph`, `ResourceFlow`, `EconomyValidation`) | Yes — `ResourceFlowTest` (15), `EconomyValidationTest` (12), `ProductionChainLifecycleTest` (9) | Yes — Economy-and-Production.md | Low |
| Offline progress | Yes | `domain/engine/Offline.kt` | Yes — `OfflineLifecycleTest` (18), `OfflineParityTest` (5), `ProductionChainLifecycleTest` | Yes — Offline-Progression.md | Low |
| Technology tree (157 nodes) | Yes | `domain/technologies/*.kt`, `TechnologyRegistry.kt` | Yes — `TechnologyParityTest` (11) | Yes — Technology-System.md | Low |
| Ownership milestones (progressive ladder) | Yes | `domain/engine/Ownership.kt` | Yes — `MilestoneLadderTest` (15), `BalanceInvariantsTest` | Yes — Economy-and-Production.md | Low |
| NEXT buy mode | Yes | `domain/economy/NextPurchase.kt`, `presentation/GameViewModel.kt` (`BuyQuantity.NEXT`) | Yes — `NextPurchaseTest` (13) | Yes — Economy-and-Production.md, README | Low |
| Prestige (Earth Points, 11 upgrades) | Yes | `domain/prestige/Prestige.kt` | Yes — `PrestigeParityTest` (6) | Yes — Prestige-System.md | Low |
| Achievements (32) | Yes | `domain/achievements/Achievements.kt` | Yes — `ContentParityTest` | Yes — Achievements-and-Challenges.md | Low |
| Challenges (8) | Yes | `domain/challenges/Challenges.kt` | Yes — `ContentParityTest` | Yes — Achievements-and-Challenges.md | Low |
| Random events (13) | Yes | `domain/events/Events.kt` | Yes — `EventsParityTest` (5) | Yes — Random-Events.md | Low |
| World-news milestones/headlines (39) | Yes | `domain/milestones/Milestones.kt` | Yes — `ContentParityTest` | Yes — README | Low |
| Climate/atmosphere (6 gases, half-life decay) | Yes | `domain/climate/Climate.kt`, `Environment.kt`, `Habitability.kt` | Yes — `ClimateParityTest` (13), `AtmosphericHalfLifeTest` (19) | Yes — Climate-Model.md, Atmospheric-Half-Life.md | Low |
| Simulated planetary age | Yes | `GameState.gameAgeSeconds`, `domain/engine/GameTime.kt` | Yes — `GameAgeTest` (18) | Yes — Game-State.md | Low |
| Storms (weather gameplay system) | Yes | `domain/storms/StormSimulation.kt`, `StormEffects.kt` | Yes — `StormSimulationTest` (19), `StormEffectsTest` (12), `StormLifecycleTest` (10), `StormBalanceTest` (3) | Yes — Storm-System.md | Low |
| Live 3D Earth globe | Yes | `presentation/globe/GlobeRenderer.kt` (OpenGL ES 2.0), 2D Compose fallback | Yes — `EnvironmentalVisualizationTest` (16) (state, not rendering) | Yes — Environmental-Visualization.md | Medium — rendering path itself unverified on real hardware (no emulator available) |
| Save/load (DataStore, dual-slot) | Yes | `data/persistence/DataStoreSaveRepository.kt` | Yes — instrumented `DataStoreSaveRepositoryTest`, `DataStoreCorruption.kt` (not run in this audit — no emulator) | Yes — Save-System.md | Low (logic verified via Robolectric-backed unit paths + `SaveParityTest`) |
| Save migrations (v1→v6) | Yes | `domain/save/Migrations.kt` | Yes — `SaveMigrationTest` (17) | Yes — Save-Migrations.md | Low |
| GameDecimal (arbitrary-scale numbers) | Yes | `domain/engine/GameDecimal.kt` | Yes — `GameDecimalParityTest` (10), `GameDecimalEdgeCaseTest` (20) | Yes — GameDecimal.md | Low |
| AdMob banner ad + UMP consent | Yes | `platform/ads/Monetization.kt` | Partial — `ProductionAdConfigTest` (10) covers config only, not the live ad-request flow | Yes — Advertising.md | Medium — consent message not yet configured in the AdMob console (owner action) |
| Haptics | Yes (real vibration, behind a setting) | `platform/haptics/HapticFeedback.kt` | Indirect (`GameViewModelTest` gating checks) | Yes — Audio-and-Haptics.md | Low |
| Audio (sound effects/music) | **Plumbing only — no assets shipped** | `platform/audio/GameAudio.kt` (`Sound` enum, all `resourceId = null`) | Indirect (gating) | Yes, and honestly — the file's own doc comment states no assets exist yet | Low (by design, not a bug) |
| Dark mode / theming | Yes | `presentation/theme/Theme.kt` | Yes — `EarthAppScreenTest` (light theme case) | Yes | Low |
| Accessibility (TalkBack labels, touch targets) | Mostly yes | `presentation/` (28 `contentDescription` sites, 8 `semantics{}` blocks, `Dimens.MinTouchTarget = 48.dp`) | Partial — `GameHeaderTest`, `EarthAppScreenTest` | Yes — Accessibility.md | Medium — see Finding M-1 in the audit: one new `contentDescription` misuse (spoken debug tag) |
| Number formatting (4 modes) | Yes | `domain/formatting/NumberFormatting.kt` | Yes — `FormattingParityTest` (6) | Yes | Low |
| Reference-implementation parity (TS oracle) | Yes | `tools/ts-reference/` (frozen, not built), `app/src/test/.../parity/*` | Yes — 11 parity suites, ~90 tests | Yes — Reference-Parity.md | Low |
| Third-party notices (in-app) | Yes | `THIRD_PARTY_NOTICES.txt`, bundled via `BundleNoticesTask`, shown in About | Yes — `scripts/third-party-notices.py --check` (re-run, passes) | Yes | Low |
| Release packaging (GitHub + Play) | Yes (unsigned pending owner key) | `app/build.gradle.kts` (`packageReleaseApk`, `packageReleaseArtifacts`) | Verified by build in this audit (`assembleRelease`, `bundleRelease` both succeed) | Yes — Release-Process.md, RELEASE-SIGNING.md | Medium — unsigned (C-2) |
| Economy graph validation (startup + CI) | Yes | `domain/production/EconomyValidation.kt`, `EarthApplication.assertEconomyIsPlayable` | Yes — `EconomyValidationTest` (12) | Yes — Economy-and-Production.md | Low |
| Balance/playthrough simulation testing | Yes | `app/src/test/.../balance/EconomyBalanceSimulationTest.kt` (bot plays up to 3 simulated days) | Yes — 12 tests | Partial — results not summarized in any wiki page | Low |
| Analytics / crash reporting | **Not implemented** | — | — | Accurately not claimed anywhere | N/A |
| In-app purchases | **Not implemented** | — | — | Accurately not claimed anywhere | N/A |

## Persisted `GameState` fields

Every field below is confirmed round-tripped by `SaveSerialization.kt`
(checked exhaustively — see Persistence section of `CODEBASE_AUDIT.md`).

| Field | Type | Persisted? | Default | Migration behaviour | Risk |
| :-- | :-- | :-- | :-- | :-- | :-- |
| `saveVersion` | Int | Yes | `SAVE_VERSION` (6) | Drives the whole migration chain | Low |
| `runNumber` | Int | Yes | 0 | Reset to +1 on `startNewRun` | Low |
| `runStartedAt` / `lastTickAt` / `createdAt` | Long (ms) | Yes | — | v1 saves migrate cleanly; a backwards clock re-anchors `lastTickAt` rather than freezing (bug fixed in `4b21e0d`) | Low |
| `gameAgeSeconds` | Double | Yes | 0.0 | v3→v4: reset to 0 for the *current* Earth (no invented history); lifetime total derived exactly | Low |
| `resources` (`ResourceAmounts`) | 15× `GameDecimal` | Yes | ZERO | New (v6) resources default to zero for a pre-v6 save; original 6 keep their ordinal slots | Low |
| `atmosphere` (`AtmosphereState`) | 6× `GameDecimal` | Yes | pre-industrial baseline | v4→v5 unaffected; storms (v5) start clear rather than invented | Low |
| `techOwned` (`Map<String, Int>`) | Map | Yes | empty | Unaffected by v6 — new (processor) tech ids simply absent until researched | Low |
| Derived climate caches (`temperatureAnomalyC`, `forcing`, `habitability`, `oceanPh`) | Double / nested | Yes | pristine | Recomputed every tick regardless; persisted for cheap load | Low |
| `runStats` / `lifetimeStats` | nested | Yes | zeroed / accumulated | `lifetimeStats` is the one state that survives every prestige reset | Low |
| `prestige` (`earthPoints`, `upgradesOwned`) | `GameDecimal` + Map | Yes | ZERO / empty | v2→v3 rescale (÷250,000, one-time) | Low — rescale is a one-time, tested, documented event |
| `achievementsUnlocked` / `challenges` | Map / nested | Yes | empty | Unaffected by later migrations | Low |
| `activeEvents` | List | Yes | empty | Pruned on load (expired events removed) | Low |
| `storms` (`StormField`) | nested + step counters | Yes | `EMPTY` | v4→v5 introduces this; pre-v5 saves start with clear skies | Low |
| `stormSeed` | Long, **stored as String** | Yes | derived from `createdAt`+`runNumber` if zero | Deliberately not a JSON number (precision) | Low |
| `milestonesTriggered` / `newsFeed` | Map / capped List (40) | Yes | empty | Unaffected | Low |
| `settings` | nested (8 fields) | Yes | `Settings.DEFAULT` | Unaffected by save-format migrations | Low |
| `tutorial` | nested (3 fields) | Yes | fresh | Unaffected | Low |
| `collapsed` | Boolean | Yes | false | Unaffected | Low |

No field was found declared on `GameState` (or a nested state class) that
is silently excluded from `encode()`/`decode()`.
