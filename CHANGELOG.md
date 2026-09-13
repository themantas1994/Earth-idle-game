# EARTH — Complete Changelog

This changelog is reconstructed directly from `git log` (39 commits: 24
non-merge commits across 15 pull requests, oldest to newest) and verified
against the current state of `main` at commit `8a77ed358`. No version tags
exist in this repository, so entries are grouped as **Current Development**
(the state as of this audit) and **Historical Changes**, in strict
chronological order. Every commit is represented; none are collapsed into
a vague summary. Numbers quoted (test counts, technology counts, etc.) are
the figures stated in each commit's own message at the time it was made —
where a later commit changed that figure, the later entry says so.

## Current Development

As of commit `ca209b5` / merge `8a77ed358` (2026-09-13): 157 technologies
across 16 branches (78 producers, 26 processors, 53 research nodes), 15
resources on an explicit producer/consumer production graph, a progressive
ownership-milestone ladder, a NEXT buy mode, 424 passing unit tests, clean
lint on both build variants, and successful `assembleDebug`/
`assembleRelease`/`bundleRelease` builds. Outstanding: no LICENSE file, no
production signing key, incomplete privacy-policy legal placeholders — all
owner-side, not code defects. See `docs/CODEBASE_AUDIT.md` for the full
current-state audit.

## Historical Changes

### 2026-09-07 — Initial web prototype

**Commit:** `98fe1e8` (initial commit), `5652899`

#### Added
- Playable vertical slice of an idle/incremental civilization &
  greenhouse-gas simulator: tap → buy → automate → prestige loop.
- 93 technologies across 11 branches, including a mutually-exclusive
  Coal-vs-Nuclear branching choice.
- 6 tracked greenhouse gases with individually-tuned forcing, lifetime and
  removal characteristics; a carbon cycle with warming-weakened sinks.
- 5-factor multiplicative habitability collapse model.
- Earth-N reset/prestige system with 9 permanent upgrades.
- Capped, upgradeable offline progression.
- 30 achievements, 8 challenges, 10 random events.
- 4 number-format display modes; corruption-safe save/load; a 10-step
  interactive tutorial; light/dark/era-based theming.

#### Build & Release
- Architecture: dependency-free TypeScript engine (`engine/`, 145 unit
  tests) using a custom mantissa/exponent Decimal type; a Zustand store
  for the tick loop and wall-clock/offline handling; React screens that
  only read state and dispatch actions.
- Packaged as a web app wrapped for Android via Capacitor
  (`capacitor.config.ts`), built this way specifically because no Android
  SDK was available in the working environment at the time — it could
  still be run and screenshotted in a browser.

#### Testing
- Verified end-to-end in headless Chromium at a mobile viewport: the
  golden path (tap → fire → tech tree → production) and the full
  collapse → reset → prestige-summary → new-run → offline-catch-up modal
  sequence. Two real bugs found and fixed in the process: a Research-cost
  dead end on the very first technology, and a stuck "next objective"
  hint.

---

### 2026-09-07 — Audit the game code and ship a buildable Android app

**Commit:** `6f145b7` (PR #1, `d96b9f6`)

#### Fixed
- An off-by-one in `maxAffordableQuantity`: inverting a geometric series
  through `log10` landed one unit low on an exact boundary (a wallet
  holding exactly the cost of 4 units bought 3). The opposite direction
  was worse — the caller would charge an over-counted quantity and the
  subtraction clamped at zero, handing out a free unit. The last unit is
  now settled against the exact series cost with a bounded correction.
- `body` margin reset: the 8px user-agent default sat around a 100dvh app
  shell, overflowing the document by 16px and clipping the bottom nav.
- All nine navigation destinations made reachable: at min-width 58px they
  needed 522px on 360–430dp phones, so three tabs sat behind an
  undiscoverable horizontal scroll. Columns now flex to fit with shorter
  labels and 52px touch targets.
- Saves now persist to Capacitor Preferences on-device rather than
  `localStorage`, which the OS can clear; saves are written on
  `appStateChange` rather than relying on the unreliable `beforeunload`.
- Hardware back now navigates instead of quitting from any screen.
- `vibrationEnabled` setting is now actually wired to Haptics.
- Disabled long-press text selection and double-tap zoom on the tap
  surface.
- Painted `windowBackground` to remove the white launch flash.
- Listeners are disposed and re-registered in `init()` so StrictMode's
  double-invoke cannot stack duplicates.

#### Added
- Generated EARTH launcher icons (legacy, round, adaptive) and splash
  screens at all densities/orientations, replacing default Capacitor
  branding.
- `npm run test:e2e`: serves the exact packaged bundle and drives it in
  headless Chromium at two viewports (23 assertions: layout, golden path,
  all nine screens, save/reload).

#### Build & Release
- `npm run android:build` produces an installable debug APK, an unsigned
  release APK, and a Play AAB.
- Moved `@capacitor/core` and `@capacitor/preferences` to dependencies
  (they ship in the app); excluded Vite source maps from the APK (~750KB).
- Locked portrait orientation, scoped Auto Backup to the save file, added
  a keystore-driven release signing config that still assembles unsigned
  on a clean checkout.

#### Testing
- `npm run lint` clean; `npm test` 152 passing (up from 145). All three
  Gradle variants build with zero Android Lint issues.
- Explicitly **not verified**: real-hardware behaviour — no KVM available
  in this environment, so no emulator could boot; the five native
  plugins were verified structurally (registered, compiled into
  `classes.dex`) rather than by execution.

---

### 2026-09-07 — Slow the game to a week-long first run, drop tapping, add world news

**Commit:** `45828e2` (PR #3, `4043033`)

#### Changed
- The first run previously finished in about half an hour (an hour of
  tapping to fund the stone age, then the entire tree consumed in twelve
  minutes once production growth `2.25^tier` outran cost growth
  `1.62^tier`). Reworked so a first run lands around a week.
- **Manual tapping removed entirely.** Natural Fire is now a generator
  every run owns from tick one; `applyManualTap`, the tap-power
  multiplier, and the Tap Conditioning prestige upgrade are gone. A save
  migration grants the starting fire and refunds spending on the removed
  upgrade.
- **Technology and Production made disjoint**: Technology sells only
  research (unlocks, multipliers, choices); every generator sells on
  Production and nowhere else.
- Pacing reworked into three separate mechanisms rather than one
  multiplier: per-tier cost growth raised above production growth
  (2.15 vs. 2.0); a new civilization-complexity surcharge (later removed,
  see `432cbd5`) charging for owning more *distinct* technologies; late
  ladder compression above tier 16 so the endgame is priced against what a
  late economy can actually earn.
- Emissions now grow more slowly per tier than the economy, so a
  completed tree does not require an already-dead planet; gas masses
  rescaled so CO2 leads the warming rather than being a rounding error
  beside the synthetic gases.
- Sea-level rise, previously inert at a real-world rate over a days-long
  run, rescaled to actually drown coastlines within a run's timescale.

#### Added
- `milestones.ts`: 39 once-per-run world-news headlines with no mechanical
  effect, from the first deliberate fire to the Amazon burning to
  astronomers comparing Earth to Venus. Surfaced as a breaking-news
  banner and a feed on Home and Atmosphere.
- `scripts/balanceSim.ts`: plays a whole run under a configurable pattern
  and reports pacing; `balance.test.ts` locks in the *relationships*
  between constants (not the values themselves, which are meant to be
  retuned). Measured result: 7.2 days at hourly check-ins over 16h/day,
  whole tree bought, all 39 headlines fired.

#### Fixed
- The Production screen was showing a building's global emission rate as
  if it were that specific building's own output.
- Two achievements were unreachable at any balance setting.

---

### 2026-09-08 — Mirror the Economy resources in the header off the Home tab

**Commit:** `73fe368` (PR #4, `56a98fc`)

#### Added
- Resource balances now render as chips in the app header on every screen
  except Home (which already shows the full card). `visibleResources`
  extracts the "hold a balance or produce it" filter so the header and the
  Economy card can never disagree about which resources exist.
- The strip is a 3-up auto-fit grid that folds to a second row rather than
  clipping resources off the right edge at 390dp.

#### Testing
- 31/31 playtest checks: absent on Home, mirrors every Economy resource
  elsewhere, carries live balances/rates, no chip escapes the header at
  360dp with all six resources in the widest notation.

---

### 2026-09-08 — Make prices permanent, reward depth, and rebuild the economy around it

**Commit:** `432cbd5` (PR #5, `883340a`)

#### Changed
- **Removed the civilization-complexity surcharge** introduced in
  `45828e2` (and the prestige upgrade that existed only to cancel it): it
  multiplied every price by the number of distinct technologies owned, so
  every new frontier made every other frontier more expensive. The rule
  from this commit forward: a technology's price is a pure function of
  `(technology, units already owned)`, scaled down (never up) by the
  prestige discount. This is the invariant the project still enforces
  today (`EconomyParityTest`, `docs/wiki/Economy-and-Production.md`).
- **Ownership bonuses introduced**: every tenth copy of a building doubles
  that building's output for the rest of the run, with a progress bar and
  a toast. (Later superseded by the progressive milestone ladder — see
  `ca209b5`, 2026-09-13.)
- The endgame densified: the last third of the tree, previously six nodes
  spread over tiers 31–40 (leaving multi-hour stretches with nothing new
  to buy), became eleven nodes with one at every tier from 28 to 38
  (Fusion Power, Space Elevator, Asteroid Capture, Terraforming Engines,
  Matrioshka Brain).
- Retuned cost/output curves so the first purchase lands ~23 seconds in
  and a completed tree remains reachable before planetary collapse.
  Measured: a first run averages 2.7 days of continuous play, 3.4 days
  engaged play, 5.1 days at a few check-ins/day — full tree bought in all
  three.
- Prestige payout rescaled to score **speed**: every run ends in the same
  place (habitability zero), so an outcome-only score previously paid a
  *stronger* civilization *less* (a second Earth finished 32% faster but
  earned 8% fewer points). Each successive Earth is now worth roughly
  double the last.
- Offline cap raised 8h → 12h; Buy Max became the default quantity mode;
  buy buttons gained an "affordable in" countdown; random events made more
  frequent and skewed more positive; Home's Complexity card replaced with
  a Momentum card.

#### Testing
- `pacing.test.ts`: plays a compressed run in CI so a future rebalance
  cannot silently ship a half-hour game or price the endgame out of reach.

#### Save / Migration
- Save v3 migrates banked Earth Points onto the new prestige payout curve.

---

### 2026-09-08 — Add AdMob banner ad to the Android build

**Commit:** `8d3cca2` (PR #6, `2b50609`)

#### Added
- A single anchored adaptive banner at the bottom of the screen via
  `@capacitor-community/admob`, using the app's own AdMob app ID and
  banner unit. No interstitials, no rewarded ads.
- `--ad-banner-inset` CSS variable, written once the native banner
  reports its real height, so the bottom nav is never covered; it stays
  `0px` (no browser-build cost) until an ad actually loads.
- UMP consent gathered before SDK initialization; falls back to
  non-personalized ads when no consent choice could be recorded; a
  Settings "Ad Privacy Choices" row reopens the consent form where one
  exists.

#### Changed
- Only a production build requests live ads; `npm run dev` and any
  `VITE_ADMOB_TEST=1` build request Google's sample unit — self-generated
  impressions on a live unit are invalid traffic and can suspend an
  account.

#### Testing
- `scripts/playtest.mjs` asserts all three banner-inset states. 208 unit
  tests and 34 e2e assertions pass. **Not verified**: the sixth Capacitor
  plugin (`play-services-ads`) was not compiled in this environment (no
  Android SDK present at the time).

---

### 2026-09-08 — Add golden parity fixtures generated from the TypeScript engine

**Commit:** `4d2a94e`

#### Testing
- `scripts/exportParityFixtures.ts`: runs the reference TypeScript engine
  and writes its answers to JSON, establishing it as the parity oracle
  the native port would later be checked against by execution rather than
  by reading.
- Fixtures cover: the Decimal type across operands from `1e-400` to
  `1e1000`; all four formatting modes; the full 99-technology table and
  tier-scaling curves; pricing and buy-max affordability including the
  exact geometric-series boundary; climate integration/forcing/
  temperature/habitability; prestige multipliers and payouts; the
  achievement/challenge/event/milestone content tables; offline catch-up;
  save round-tripping and the v1→v2→v3 migration chain.
- Centrepiece: a 95-step scripted playthrough from a fresh game to
  planetary collapse (+600,000°C, 33 milestone headlines, 22
  achievements) and through a prestige reset, snapshotting every
  simulation value at every step.

---

### 2026-09-08 — Native Kotlin/Compose Android migration (four commits, one arc)

This is the single largest architectural event in the project's history:
retiring the React/Vite/Capacitor web app entirely and replacing it with a
native Kotlin + Jetpack Compose Android application, built and verified
piece by piece across four commits in one day.

#### Migration — `9b92ba4`: Scaffold the native Kotlin/Compose Android project
- A real Android Studio project at the repository root: AGP 9.4.0 on
  Gradle 9.7.1, `compileSdk`/`targetSdk` 37, `minSdk` 24, Jetpack Compose
  with Material 3, Navigation Compose (present at scaffold time; later
  found unused and removed), DataStore, Lifecycle, and
  kotlinx.serialization — all versions pinned in one version catalogue.
- Release signing reads from `keystore.properties`, Gradle properties, or
  environment variables in that order; the release variant still
  assembles unsigned when none is configured. Production AdMob
  identifiers live in one commented resource file; debug builds use
  Google's test units.
- Launcher icons and the pre-first-frame window background carried over
  unchanged from the web build.

#### Migration — `79b3bf7`: Port the complete game engine to Kotlin, verified against the reference
- Pure Kotlin, zero Android/Compose dependency in `domain/`, so the whole
  simulation is testable on the bare JVM:
  - `GameDecimal`: sign/mantissa/exponent number type for late-game values
    past `1e400`, which a `Double` cannot hold.
  - The full climate model: analytic (closed-form) carbon-cycle
    integration, per-gas radiative forcing, temperature with its
    super-linear tail, ocean pH, sea level, and the five multiplied
    habitability factors.
  - All 99 technologies across 11 branches, the tier scaling curves, and
    the pricing invariant.
  - Prestige (11 upgrades), 32 achievements, 8 challenges, 13 random
    events, 39 milestone headlines.
  - Offline catch-up as one closed-form calculation rather than a stepped
    loop, made possible by the gas integration being step-size
    independent.
- Technology, milestone, and event tables were translated from the
  TypeScript data files by a parser, not by hand.

#### Testing (this sub-commit)
- 77 tests check the port against golden values captured from the
  reference engine: every field of all 99 technologies, 576 Decimal
  operand pairs, 3,240 gas integrations, every formatting mode, and the
  full 95-step scripted playthrough compared value-by-value.

#### Documentation (this sub-commit)
- Two deliberate, documented differences from the reference recorded from
  the start: the Speedrun challenge reads elapsed time from the game
  clock rather than a wall clock (so the same state always answers the
  same way); water vapour lags one simulation step, matching the
  reference's own behaviour exactly.

#### Migration — `800be5c`: Add the save system, session loop and game ViewModel
- Versioned JSON persistence with every `GameDecimal` stored as its exact
  `{sign, mantissa, exponent}` triple — a JSON number would round-trip
  through a Double and cap the save at `1.8e308`. Same field names and
  tagging as the web build, so an exported save loads and migrates
  forward through the same v1→v2→v3 chain.
- DataStore-backed with primary and backup slots rotated in one
  transaction so they can never disagree; a load reports whether it
  recovered from backup. Loading never throws — a damaged field falls
  back to its default, and a structurally implausible save is rejected so
  the backup gets a turn.
- `GameLoop`: the session layer above the pure-physics `simulateStep` —
  absences, event rolls, milestone headlines, achievements, challenge
  settlement, purchases, prestige resets — kept in the domain layer, and
  taking an injected `Random` so a seeded run reproduces its events.
- `GameViewModel` ticks on `Dispatchers.Default` (not the main thread — a
  late-game step walks the whole tech tree four times a second) and
  pauses while backgrounded, settling the entire absence in one
  closed-form step on return: no background service, no scheduled work,
  no wake locks.
- Platform seams added for haptics (real, behind the existing setting)
  and audio (SoundPool-backed, no assets yet — same state as the reference
  shipped in, and still true as of the most recent commit in this
  history).

#### Testing (this sub-commit)
- 105 tests pass.

#### Migration — `3c9c0c6`: Build the Compose UI and retire the web runtime
- All nine screens rebuilt in Jetpack Compose + Material 3, preserving
  the original build's information architecture and exact colour tokens.
- Portrait-first design, 48dp touch targets throughout, no hover states.
  Tablets/unfolded foldables get a left navigation rail and a
  width-capped content column.
- A hand-rolled bottom navigation bar (Material's `NavigationBar` reserves
  too much width per item for nine destinations on a 360dp phone); each
  tab's full name is its accessibility label so a screen reader announces
  "Atmosphere" where the visible label says "Air."
- Back now unwinds the screens actually visited and only leaves the app
  from Home.
- Light/dark/system theming; reduced-animation setting honoured through
  a `CompositionLocal`; toasts as polite live regions for TalkBack.
- `MainActivity` watches the **process** lifecycle rather than the
  Activity's, so a rotation is never mistaken for the player leaving.
- **The React/Vite/Capacitor runtime is removed entirely from the shipped
  app.** The TypeScript engine is kept, frozen, in `tools/ts-reference/`
  as the parity oracle the golden fixtures are generated from; nothing in
  the Gradle build depends on it, confirmed at the time and reconfirmed
  in this audit.

#### Testing (this sub-commit)
- 117 JVM tests pass; lint clean on both variants; debug APK (18.7 MB)
  and release AAB (7.7 MB, R8-minified) both build; the instrumented test
  APK compiles.

#### Documentation (this sub-commit)
- `ANDROID.md`, a rewritten README, and a CI workflow added.

---

### 2026-09-09 — Run the UI and persistence under Robolectric, and close the last gaps

**Commit:** `0783fc7`

#### Testing
- The Compose UI, the DataStore repository, and the app end-to-end now
  run on the JVM under Robolectric as part of `./gradlew test` — there
  being no hardware-accelerated emulator in this environment, these paths
  were previously covered only by an instrumented suite nobody could
  execute. The instrumented suite is kept for real hardware.

#### Fixed
- The side navigation rail's rows carried no merged content description,
  so TalkBack announced the emoji as a second, meaningless node — found
  by the new Robolectric UI tests, not by inspection.
- The legacy (pre-API-26) launcher icon filled its whole square rather
  than being masked like the adaptive icon.
- Dropped a dead child-directed-treatment block from the ad controller;
  moved to `getLargeAnchoredAdaptiveBannerAdSize` since the fixed-height
  anchored sizes it used were deprecated.

#### Documentation
- `docs/MIGRATION.md` added: where every TypeScript file went, what each
  parity fixture pins down, why the tolerances are what they are, the two
  deliberate gameplay differences, and what remained unproven by
  execution.

#### Testing
- 142 unit tests pass; lint zero issues on debug/release; debug APK,
  release AAB, and instrumented test APK all build.

---

### 2026-09-10 — Document how to build the app in the README

**Commit:** `fa448b7` (PR #8, `654346e`)

#### Documentation
- Added a Building section (prerequisites, clone-to-installed-APK
  sequence, common-task table) — previously the README pointed to
  `ANDROID.md` with three bare Gradle lines and no JDK/SDK guidance.
- Corrected the documented release-APK filename in both README and
  `ANDROID.md`: an unsigned release assembles as
  `app-release-unsigned.apk`, not `app-release.apk`, which both documents
  had claimed since they were written.

---

### 2026-09-10 — Fix the bugs the audit found, and test them

**Commit:** `4b21e0d`

Nine defects found, four capable of costing a player their progress.

#### Fixed
- **A backwards device clock froze the game.** `GameLoop.advance`
  returned early on non-positive elapsed time without updating
  `lastTickAt`, so a manual clock change or NTP correction left the tick
  anchored in the future until the wall clock caught up. It now
  re-anchors: nothing is simulated (no resource un-produced), but the
  game keeps running.
- **The tick raced the player.** Both the tick and a purchase did
  read-compute-write against the same `StateFlow` from different threads
  with no synchronization, so a purchase could settle against a
  since-replaced snapshot and silently undo itself, or a tick's progress
  could roll back under a purchase. Every transition now runs under a
  lock through one `mutate` helper. A new deterministic test forces the
  interleaving with a clock that blocks inside the critical section,
  recording every published state — it fails against the pre-fix code.
- **A corrupt DataStore file crash-looped the app permanently** (a
  `CorruptionException` is thrown from every subsequent read for the life
  of the process). A `ReplaceFileCorruptionHandler` now resets the file,
  and a flag reports the next load as `Corrupted` rather than `Empty`.
- **Loading a save from a newer build could halve banked Earth Points
  twice**: the migration chain stamped a future version number down to
  the current one, so a later app upgrade could re-run the v2→v3 rescale.
  It now preserves the higher version.
- Non-finite doubles in a save (`1e999` is legal JSON) turned every
  downstream value to NaN for the rest of the run — now rejected/clamped.
- Negative owned counts could reach the cost curves.
- The compact number notation's alphabetic fallback past `Vg` (1e63)
  started at one letter, so `1e69` printed as `"1B"` — indistinguishable
  from `1e9`. Now starts at two letters (`AA`, `AB`, …).
- Autosave kept writing every 15s while backgrounded with nothing to
  write.
- Offline catch-up ran on the main thread.

#### Fixed (shared with the reference engine)
- The same suffix collision and two challenge-reward descriptions that
  did not match their own effects were bugs in the *reference*
  implementation too — fixed there as well, with fixtures regenerated.
  No effect, constant, price, or formula changed; only descriptive text.

#### Testing
- Three code comments referenced test classes that did not exist
  (`SaveMigrationTest`, `BalanceInvariantsTest`) — rather than delete the
  false claims, the tests were written.
- 142 → 205 JVM tests, all passing.

---

### 2026-09-10 — Drop four unused dependencies and let Compose skip on value

**Commit:** `a3e1a0e`

#### Refactored
- Removed `navigation-compose`, `lifecycle-viewmodel-compose`,
  `androidx.window`, and `espresso-core` — zero references anywhere in
  the codebase; `ui-test-manifest` was found declared twice and
  deduplicated. Release APK size changed by 48 bytes only (R8 was already
  stripping the dead code) — the value of the change was reduced
  audit/maintenance surface, not binary size.

#### Performance
- Domain value types (`GameState`, amount containers) were all inferred
  *unstable* by the Compose compiler, since `GameState` holds `Map`/`List`
  and the amount containers hold a private array. Rather than annotate
  them `@Immutable` (which would require importing
  `androidx.compose.runtime` into `domain/`, breaking the project's
  central architectural rule), they are declared stable externally via
  `compose-stability.conf`. Measured with the compiler's own reports:
  effectively-stable classes 43 → 68, arguments compared by value
  3202 → 3230. Skippable-composable count was unchanged at 152/201
  (strong skipping already made them skippable by reference identity) —
  the actual gain is that *equal values* now compare equal, which matters
  for recomposition correctness, not just count.
- `-Pearth.composeReports=true` gradle property added to make this
  measurement repeatable on demand.

#### Fixed
- `gradlew.bat` renormalized — the committed blob carried CRLF while
  `.gitattributes` requested LF, so every fresh clone showed it as
  locally modified.

---

### 2026-09-10 — Rewrite the README for players, and document the whole codebase

**Commit:** `0f52c5c` (PR #9, `743a711`)

#### Documentation
- README rewritten to open with what the game *is* and how it plays,
  rather than a build section — including why "prices never rise" and
  "every tenth copy doubles output" matter. Every feature count, offline
  cap, and install step read out of the source or executed rather than
  recalled from memory.
- `docs/wiki/` created: 31 new pages covering architecture, the engine and
  `GameState`, `GameDecimal`, the simulation and climate model (every
  formula with its rationale), the economy and price-stability invariant,
  the technology tree, prestige, achievements and challenges, random
  events, offline progression, the save format and migration chain, UI
  and navigation, the Android platform, audio, advertising,
  accessibility, testing, reference parity, build/release process,
  performance, security and privacy, a player FAQ, a glossary, and
  troubleshooting.
- `docs/CODEBASE_AUDIT.md` added (the original version of the file this
  changelog's own audit now supersedes), separating fixed issues from
  remaining ones with evidence for each. `docs/DEVELOPER_OVERVIEW.md`
  added as a five-minute orientation. `docs/GITHUB-METADATA.md` added,
  explicitly marked "not applied" — repository metadata (description,
  topics, licence) can be read but not written from this environment.
- **The licence gap first documented here**: no LICENSE file exists, so
  the default of all-rights-reserved applies and "open source" is not yet
  legally true anywhere in the README. The README states this plainly
  rather than implying otherwise. **This remains unresolved as of the
  most recent commit in this history** (2026-09-13) — see
  `docs/CODEBASE_AUDIT.md` Finding C-1.

#### Testing
- `scripts/check-docs-links.py` added: checks every relative link and
  anchor across all 37 Markdown files at the time (56 as of this audit),
  dependency-free by design, runs first in CI.

---

### 2026-09-12 — Prepare the app for a real production release

**Commit:** `31dee75` (PR #10, `3c4f320`)

Productionizes the build, ad/consent flow, and legal documentation.
**Gameplay untouched**: no formula, constant, balance value, or save
behaviour changed; the 205 existing tests still passed alongside 10 new
ones.

#### Fixed (Security/Privacy)
- **`canRequestAds()` was fetched but not actually used as the gate.**
  The Mobile Ads SDK was initialized and the banner loaded on every code
  path, including when the player had declined consent or the consent
  update had failed — serving an ad in the EEA/UK against a recorded
  "declined" consent state is exactly the violation the flow exists to
  prevent. `canRequestAds()` is now the actual gate for both
  initialization and banner composition.
- The `npa=1` (non-personalized ads) extra was removed: with a
  TCF-certified CMP in place, the serving mode already comes from the
  signals UMP writes, and setting `npa` alongside it could override the
  player's real choice in one direction.
- Debug builds could previously still reach the *live* AdMob app because
  only the banner unit ID was swapped at runtime while the manifest's
  application ID stayed the production one in every variant. Both
  identifiers are now a debug resource overlay carrying Google's public
  sample values, split at resource-merge time with no runtime branch to
  get wrong.

#### Added
- `scripts/third-party-notices.py`: generates `THIRD_PARTY_NOTICES.txt`
  from the resolved release classpath (licences read from each module's
  own POM metadata and embedded LICENSE files, never guessed) — 149
  modules at the time. The build copies it into app assets; Settings →
  About → Open Source Licenses shows it in-app. CI regenerates and fails
  on drift (later refined — see `b3a2ae2`).
- About screen: version, package, privacy policy link, and the project's
  actual current licence status (stated honestly, not aspirationally).
- `packageReleaseArtifacts` Gradle task: collects both build artifacts
  into a git-ignored `release/` directory as
  `EARTH-<version>-release.{apk,aab}`, suffixed `-unsigned` when no
  keystore is configured.

#### Fixed (Build)
- CI previously exported only three of the four required signing
  variables, so it could never actually produce a signed build. Now
  decodes a keystore from a secret and deletes it before uploading any
  artifact.

#### Documentation
- `docs/RELEASE_BLOCKERS.md` added, classifying every finding. Four
  CRITICAL items recorded, all requiring the repository owner and none
  invented: no project licence, no signing key, no legal contact for the
  privacy policy, no recorded provenance for the launcher icon. **All
  four remain open as of the most recent commit in this history.**

#### Testing
- 215 tests pass; lint clean on both variants; the release APK/AAB
  verified to carry production AdMob identifiers, target API 37, not
  debuggable, no Google sample identifier, installable via `bundletool`.

---

### 2026-09-12 — Check the notices against the classpath instead of regenerating in CI

**Commit:** `b3a2ae2` (same PR as above's follow-up, `3c4f320`)

#### Fixed
- The notices-generation step failed on its *first* CI run, reporting
  all 149 dependencies as declaring no recognised licence. They declare
  licences correctly — the script reads them from POMs in the Gradle
  module cache, and the runner's cache had none of them yet. A cold cache
  and 149 genuinely-unlicensed dependencies would have produced the
  identical error message, which is the worst property a check can have.
- `--check` mode added: compares the *committed* notices file against the
  resolved classpath directly, needing no cache, no downloads, and no
  licence parsing. This is what CI now runs; regeneration stays a
  maintainer-only step. Verified on both failure directions (a removed
  dependency, a lingering stale one) and against a cold cache.
- The cache-root lookup now honours `GRADLE_USER_HOME` (which CI sets),
  previously ignored.

---

### 2026-09-12 — Audit the release candidate, and fix what the audit found

**Commit:** `f2f52c1` (PR #11, `912cc6f`)

Final release-gate audit of `main` before the first production release —
built the release artifacts and inspected them directly rather than
trusting prior documentation about them, which turned out to matter.

#### Fixed
- `BannerAd` requested its ad while composing, from inside the `remember`
  block that built the view, before the `AdListener` was attached — an
  abandoned composition could spend a request on an `AdView` nothing
  would ever destroy. Moved to `DisposableEffect`, after the listener,
  behind a re-check of `canRequestAds`.
- The banner went blank after a device rotation:
  `remember(widthDp)` built a replacement view and the effect destroyed
  the original, but `AndroidView` only calls its factory once per node,
  so the destroyed view stayed on screen. Fixed by wrapping in
  `key(adView)`.
- Registered `packageReleaseArtifacts`-family tasks with
  `tasks.register` instead of the delegate deprecated in Gradle 9.6 — the
  source of every "incompatible with Gradle 10" warning, which the
  now-superseded blockers document had misattributed to AGP.
- `ProductionAdConfigTest` gained a guard against a `src/release/res`
  overlay ever silently shadowing the shipped AdMob identifiers.

#### Added
- `packageReleaseArtifacts` now also writes the R8 `mapping.txt` and a
  `SHA256SUMS` file covering everything it produces.

#### Fixed (Documentation, against the real merged manifest)
- **Four documents had claimed exactly one exported component and no
  services/receivers/providers**, two of them citing the merged manifest
  as their evidence. The real merged manifest at the time held 6
  activities, 5 services, 9 receivers, and 2 providers (all from
  transitive dependencies) — the underlying security conclusion (all
  exported non-launcher components require permissions no ordinary app
  holds) was correct, but the *statement* of it was false. **This audit
  (2026-09-13) independently re-verified these exact figures against a
  fresh build and confirms they remain accurate.**
- The privacy policy claimed "no query of the other apps installed on the
  device"; the merged manifest carries a `<queries>` element from
  `androidx.browser` and the ads SDK — filtered visibility, not
  `QUERY_ALL_PACKAGES`, but not "no query" either. Corrected, along with a
  missing `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` entry in the
  permission table.
- The manifest's own comment still described the app ID as Google's
  sample one.

#### Documentation
- Two new CRITICAL blockers recorded, neither closable from this
  environment: no AdMob-console consent message exists yet (the app-side
  implementation is correct and verified; nothing can bundle the message
  itself), and the release build has never run on physical hardware
  (`release` is the only minified variant, so all 216 tests at the time
  ran against unminified code). `docs/FINAL_DEVICE_QA.md` added — 43
  manual steps for real hardware. `docs/RELEASE_NOTES_TEMPLATE.md` added.

#### Testing
- 216 tests, lint, `lintRelease`, `assembleRelease`, `bundleRelease`,
  `packageReleaseArtifacts` all pass. Release APK's `.so` files confirmed
  16 KB-page-aligned; `THIRD_PARTY_NOTICES.txt` byte-identical on
  regeneration and covers all 149 shipped modules. No secret found in the
  tree or in git history at the time (**this audit re-confirms this is
  still true**). Both artifacts remained unsigned.

---

### 2026-09-12 — Aim the release at GitHub Releases, and verify the APK it produces

**Commit:** `de4146e` (PR #12, `9444b84`)

#### Changed
- Established the actual distribution channel: a signed APK downloaded
  from GitHub Releases, **not** a Google Play upload. Every prior document
  had implicitly treated Play as the release gate.
- Split packaging in two: `packageReleaseApk` (APK + `mapping.txt` +
  checksums only, skipping the second full R8 pass that `bundleRelease`
  costs, since a GitHub release needs no bundle) and
  `packageReleaseArtifacts` (adds the AAB, for a future Play upload).
- Any previously-packaged artifact for the same version is cleared before
  writing new ones — signing a build that was packaged unsigned earlier
  previously left both an `-unsigned` and a signed APK in the same
  directory, risking the wrong one being attached to a release.
- Dropped `DebugProbesKt.bin` from the packaged app (kotlinx-coroutines
  ships it for a debug agent nothing here installs).

#### Testing (four new tests closing a real gap)
- No shipped Kotlin/Java file may contain an AdMob identifier as a
  literal (a literal would be variant-blind, silently defeating the
  resource-overlay split). Google's sample publisher ID may appear
  nowhere outside `src/debug`. The release build type keeps R8, resource
  shrinking, and non-debuggability. The debug build type keeps its
  `.debug` application-ID suffix.

#### Documentation
- `RELEASE-SIGNING.md` rewritten for GitHub distribution (Play App
  Signing demoted to a future option); `GITHUB_RELEASE_TEMPLATE.md` and
  `ASSETS.md` added; `RELEASE_BLOCKERS.md` reclassified into technical
  blockers (none open at the time) versus six owner requirements (four
  blocking).

#### Testing
- 220 unit tests pass; lint/`lintRelease` zero issues; both packaging
  tasks succeed. The release APK was taken apart and verified field by
  field (package name, versions, minification, AdMob identifiers,
  launcher icon, notices, `zipalign -c -P 16`). Signing was exercised
  end-to-end with a throwaway key generated and deleted outside the
  repository — `apksigner verify` reported `Verifies` (APK Signature
  Scheme v2). This proves the *pipeline*, not the release: no keystore
  was created for or committed to this project.
- **Not verified**: still nothing run on physical hardware.

---

### 2026-09-13 — Give the Earth an age, and decay the atmosphere against it

**Commit:** `dd56350` (PR #13, `81846f5`)

#### Fixed
- **Every gas's atmospheric half-life was being run against real
  seconds, not simulated time** — CO2's 120-year half-life was 120
  *calendar* years, so across a 3-day run the decay term removed roughly
  0.007% of excess CO2. Gas effectively accumulated forever while the
  documentation described genuine decay. No half-life *value* changed
  (120, 12, 114, 0.06, 3200 remain exactly what shipped before); what
  changed is that they are now correctly interpreted as half-lives
  operating over the planet's own simulated age.

#### Added
- `GameTime.kt`: states the simulated calendar once (one real second =
  one simulated day, so a simulated year passes every 365.25 real
  seconds) — a ratio derived from the existing `seaLevelPerDegreeYear`
  constant's implicit scale, not invented.
- `GameState.gameAgeSeconds`: the planet's own age, advanced only by
  `simulateStep`, so live ticks and offline catch-up cannot drift and an
  absence past the offline cap ages the Earth by the cap, not the full
  absence. `LifetimeStats.totalSimulatedSeconds` is the lifetime
  counterpart, surviving every prestige reset.

#### Changed
- `naturalRemovalRateConstant` recomputed as
  `ln(2) / (halfLife * REAL_SECONDS_PER_GAME_YEAR)`. The integration math
  itself is untouched (already the exact closed-form solution to
  `dE/dt = P - kE`), so one tick, one hour, and a twelve-hour catch-up
  still agree to the last digit.
- Only the *anthropogenic excess* above baseline decays — CO2 tends
  toward 280 ppm, never to zero.
- Measured balance impact: a full efficient-play run went from 2.676 to
  2.694 days (still buys the entire tree — exponential production
  outruns the decay constant well before the late game). The Primitive
  challenge (low emissions, accumulating slowly — exactly the case this
  change affects most) went from 58 to 91 hours under a modest prestige
  loadout. CO2's half-life lands at 12.2 real hours, close to one
  offline cap, so an untended Earth left overnight loses roughly half its
  excess.
- Water vapour is the one documented exception, kept on the real-time
  relaxation rate since it is a feedback rather than an accumulating
  stock; sea level likewise stays on the real clock since its constant is
  the one the new simulated calendar was itself derived from.

#### Fixed (unrelated race condition, surfaced by this work)
- `tickJob`/`autosaveJob` were written from the load coroutine's thread
  and read from the main thread with no happens-before relationship, so
  `stopLoops` could observe a stale `null` and cancel nothing — a
  backgrounded game that kept ticking and autosaving. Now guarded by a
  dedicated `loopLock`. This was the root cause of a previously-flaky
  concurrency test (`GameViewModelConcurrencyTest.backgrounding stops
  both loops`, failing 2 runs in 4 on the prior commit).

#### Save / Migration
- Save v4: serializes `gameAgeSeconds` and `totalSimulatedSeconds`.
  `migrateV3ToV4` starts the *current* Earth's age at zero rather than
  reconstructing it from `lastTickAt - runStartedAt`, since that
  wall-clock gap would routinely credit centuries the planet never
  actually simulated (offline caps and the offline-progress-disabled
  setting both mean simulated time and wall-clock time diverge). The
  *lifetime* total is derived exactly, since it was already tracked in
  units the new clock could reuse directly.

#### Changed (UI)
- "Metals" is now the player-facing name for what has always been
  persisted as `steel` — the id is kept exactly because every save ever
  written contains it. `AGE` shown in the header; welcome-back modal now
  states how much the planet aged, explaining a gas total that can go
  *down* while away. "Natural Lifetime" on Atmosphere renamed
  "Half-Life."

#### Testing
- 275 JVM tests (was 223); reference-engine parity fixtures also grew to
  216 (from 198). New suites: `AtmosphericHalfLifeTest`, `GameAgeTest`.
  `GameHeaderTest` gained pinned-width cases (Robolectric's font metrics
  differ from a real device's, so overflow behaviour needs explicit
  coverage rather than an assumption).

#### Documentation
- `docs/wiki/Atmospheric-Half-Life.md` added. A standing inaccuracy
  corrected while writing it: the docs claimed O3 could go negative and
  unlock an "Ozone Hole" achievement at -50 DU, but the integration
  clamps every gas at zero excess, making that achievement permanently
  unreachable at any balance — verified, not assumed, and recorded rather
  than silently fixed (fixing it would be a balance change, out of scope
  for a correctness pass).

---

### 2026-09-13 — Make Home a live 3D Earth, and give the planet weather

**Commit:** `4725b47` (PR #14, `54939b3`)

#### Added
- Home rebuilt as a live 3D visualization of the actual running
  simulation (previously a list of numbers), rendered in OpenGL ES 2.0
  with a one-way data flow (`GameState → DerivedState →
  EnvironmentalVisualizationState → GlobeRenderer`) — the renderer writes
  nothing back into simulation state, verified by there being no
  `GameState` reference anywhere in the renderer and no rendering object
  anywhere in `GameState`.
- Six overlays: atmosphere, temperature, humidity, wind, events, storms.
  Drag to rotate, pinch to zoom, tap a storm. Day/night driven by
  `gameAgeSeconds` (the simulation's own clock), never the device clock.
- **Storms**: a full gameplay system, not a visual effect. Formation
  depends on the planet's own temperature and water vapour (no storm can
  form below +0.6°C of warming); storms move along the same wind field
  the wind overlay draws, intensify through a life curve, and dissipate.
  They reduce production while active and cost nothing once gone —
  consistent with the game's existing rule that no multiplier is ever
  permanently taken away.
- Diminishing returns within each storm-penalty channel plus hard caps:
  ten concurrent storms cannot zero production. Measured: the worst
  climate state in the game costs ~8% average, never more than the 35%
  hard cap.

#### Changed (Architecture / Build)
- Evaluated and rejected both SceneView and Filament before building
  against raw OpenGL ES 2.0: SceneView/Filament together would have added
  ~24 MB of artifacts, a `glEsVersion` feature requirement that would
  become a Play install filter for an otherwise-2D game, an HTTP client
  in an app that makes no network requests otherwise, and Filament's
  material compiler was not published to Maven at the time. The chosen
  approach cost +64 KB of release APK (3,742,304 → 3,807,840 bytes),
  nothing added to the licence audit, and excludes no device.
- The Earth's surface texture is generated in code from outlines authored
  directly in this repository — deliberately, so no image asset's
  provenance needs to be established (a live concern per
  `docs/RELEASE_BLOCKERS.md`'s O4).

#### Fixed / Reliability
- A device with no GLES 2.0, or any renderer failure, falls back to a
  Compose-drawn rendering of the identical state snapshot: same overlays,
  same storms at the same coordinates, same spoken descriptions.
- Determinism preserved: storms advance in whole 5-second steps against a
  SplitMix64-style stream seeded by `(runSeed, stepIndex)`, so 14,400
  live ticks and one twelve-hour offline catch-up produce the identical
  storm timeline — and a player who left under clear skies is settled
  bit-for-bit identically to how the offline path behaved before storms
  existed, preserving the reference-parity guarantee on that path.
  `simulateStep` itself is untouched; storms fold in exactly where random
  events already did, as a `MultiplierContribution`.

#### Save / Migration
- Save v5: adds the storm field and the two counters that make its
  timeline reproducible. A v4 save starts with clear skies (no invented
  history). The storm seed is stored as a **string**, not a JSON number —
  a 19-digit `Long` through a JSON number loses precision, which would
  have meant a reloaded Earth getting different weather from the one that
  was saved.

#### Testing
- 340 unit tests pass; lint clean on both variants; release build
  succeeds. **Not verified**: no device or emulator was available, so no
  claim is made about measured frame rate or actual on-device rendering
  behaviour.

---

### 2026-09-13 — Turn the economy into a producer/processor production chain

**Commit:** `ca209b5` (PR #15, `8a77ed358` — current `main` HEAD)

The largest gameplay/content change in the project's history: the economy
changed from a flat list of resource-producing "generators" (every
building made something out of nothing) to an explicit directed
production graph with producers and processors that consume real inputs.

#### Added
- **`domain/production/` package**: `ProducerDefinition`/
  `ConsumerDefinition` domain types, `ProductionGraph` (the whole economy
  assembled and cross-indexed once), `ResourceFlow` (a fixed-point
  max-min-fair allocator that solves every processor's utilization
  simultaneously), and `EconomyValidation` (15 graph-integrity rules).
- **9 new resources** (appended to `ResourceId`, never inserted, to
  preserve save-file ordinal compatibility): Iron Ore, Copper, Uranium,
  Rare Earths, Refined Fuel, Chemicals, Electronics, Advanced Materials,
  Launch Capacity — bringing the total from 6 to 15.
- **5 new technology branches**: Mining & Extraction, Materials &
  Metallurgy, Nuclear, Electronics & Computing, Space Industry — bringing
  the total from 11 to 16.
- **58 new technologies** (99 → 157), 26 of them the new `CONSUMER` kind
  — processors spanning refining, power generation, metallurgy,
  electronics, and space industry.
- **A new `TechKind.CONSUMER`**, distinct from `GENERATOR`: a processor
  declares a per-unit input-resource flow (`inputsPerUnit`) and runs at
  the rate its scarcest input allows, rather than always producing at
  100% like a generator.
- **A progressive ownership-milestone ladder**, replacing the flat
  "every 10th copy doubles output" rule from `432cbd5`: milestones land
  every 10 units over the first 100 owned, 11 over the second hundred, 12
  over the third, and so on — linearly, not exponentially — with the rule
  that a milestone never crosses a hundred-unit boundary (so 100, 200,
  300, … are always exact milestones). Every milestone still pays the
  same ×2 the flat ladder did, preserving the early-game power curve.
- **A NEXT buy mode**, alongside the existing ×1/×10/×100/Max: buys
  exactly enough units to reach a building's next milestone, atomically
  (a shortfall fails the whole purchase rather than buying a partial
  amount), and names both the quantity and the target on the button.
- Startup economy-graph validation (`EarthApplication.onCreate` →
  `assertEconomyIsPlayable`): throws in debug builds if the graph is ever
  broken; logs only in release, so a broken graph never crashes a
  player's actual game.

#### Changed
- Intake for a processor scales with the building's own *size* (units
  owned × the ownership milestones those units have earned) and with
  nothing else — global/prestige/event/branch production multipliers
  raise **output only**. This asymmetry is deliberate: multiplying both
  sides would make every production bonus a no-op across the whole
  processing half of the economy.
- Processors may claim at most 90% of any resource's gross supply,
  guaranteeing a resource always accumulates and a player can always buy
  their way out of a shortage rather than hitting a hard bottleneck floor.
- Emissions scale with a processor's actual utilization: a starved
  refinery emits proportionally less carbon than a fully-supplied one.
- The offline-progress calculation required **no new machinery**: because
  the resource-flow solver is a pure function of constant ownership
  counts during an absence (no purchases happen while away), the existing
  closed-form single-step calculation remains exact for bottlenecked and
  multi-input chains alike — verified directly by a new test comparing
  one 6-hour step against 360 stepped 60-second calls (agreement to
  1e-9 relative tolerance).

#### Save / Migration
- Save v6. Nothing in a v5 save is *wrong*, so nothing is changed:
  9 new resources default to zero (ordinal-safe by construction);
  processor ownership is ordinary technology ownership (a pre-v6 save
  simply owns none, correctly); milestone state was never stored (the
  bonus is derived from ownership count, so the new ladder applies itself
  on load with nothing to convert — no previously-granted reward is taken
  back).

#### Testing
- 84 new tests (340 → 424): `ResourceFlowTest` (15 — utilization,
  fairness, order-independence, the 90% cap, loop-safety, extreme-value
  handling), `MilestoneLadderTest` (15 — the exact ladder sequence and
  every worked example), `NextPurchaseTest` (13 — quantity, price
  parity with the other buy modes, all-or-nothing behaviour),
  `EconomyValidationTest` (12 — all 15 graph rules plus documented-count
  assertions), `EconomyBalanceSimulationTest` (12 — a bot plays up to 3
  simulated days and asserts against stalls, chain onset timing, and
  bottleneck realism), `ProductionChainLifecycleTest` (9 — offline/live
  parity, prestige asymmetry, save migration), `EconomyDiagramTest` (2 —
  keeps the wiki's diagram honest against the live graph).
- Reference parity preserved deliberately and precisely rather than
  broken: all 99 of the TypeScript reference's original technologies
  remain unchanged field-for-field and in their original relative order;
  the reference's own milestone ladder is asserted to still match exactly
  below 100 units owned, with the divergence above 100 documented and
  tested on both sides (a genuine, intentional, tested behavioural
  difference from the reference — not an accidental drift).

#### Documentation
- `docs/wiki/Economy-and-Production.md` rewritten around the
  producer/processor model, the flow-allocation algorithm, and the
  milestone formula, including a Mermaid diagram of the real production
  graph. `Technology-System.md`, `Reference-Parity.md`,
  `Save-Migrations.md`, `Simulation.md`, `Testing.md`, `Architecture.md`,
  `FAQ.md`, and the README updated to match the new numbers throughout.

---

*(This audit — 2026-09-13, same-day follow-on — verified every figure in
the entry above against source independently, and found them all
accurate; see `docs/CODEBASE_AUDIT.md` for the verification method and
the small number of new findings from this specific commit, none of which
affect the numbers cited here.)*
