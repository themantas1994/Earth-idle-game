# EARTH

An idle/incremental civilization simulator: build technology, generate greenhouse gases, heat
the planet past the point of habitability, then reset and do it faster next time. Inspired by
*Antimatter Dimensions* / *AdVenture Capitalist*-style incrementals, themed around atmospheric
chemistry and civilizational collapse. Playable one-handed, portrait orientation, designed
mobile-first.

> This is a gameplay simulation, not a scientific climate model. Formulas are simplified but
> internally consistent (see "The climate model" below) and use real, plausible relationships
> (logarithmic CO₂ forcing, gas lifetimes, sink weakening under warming, etc.) for flavor and
> to make the numbers behave sensibly at both small and absurd scale.

## Running it

```bash
npm install
npm run dev        # dev server at http://localhost:5173
npm test           # vitest — 208 unit tests over the whole simulation engine
npm run test:e2e   # drives the packaged bundle in headless Chromium (see below)
npm run build      # production build to dist/
npm run lint       # tsc --noEmit
```

There is also a headless pacing harness, used to tune the balance curves:

```bash
npx vite-node scripts/balanceSim.ts               # play a whole run, report when everything happens
ACTIVE_HOURS=16 SPEND_INTERVAL=3600 TRACE=14400 \
  npx vite-node scripts/balanceSim.ts             # model a realistic play pattern, with a climate trace
BALANCE='{"generatorCostGrowthPerTier":3.2}' \
  npx vite-node scripts/balanceSim.ts             # try a change to constants.ts > BALANCE without editing it
PRESTIGE='{"atmospheric_momentum":5}' \
  npx vite-node scripts/balanceSim.ts             # model a second/third Earth with upgrades already bought
POLICY=greedy npx vite-node scripts/balanceSim.ts # upper bound: always buy the highest tier affordable
```

No backend, no network calls — the whole game runs client-side and saves to device storage.

## Building the Android app

```bash
npm run android:build     # web build -> cap sync -> assembleDebug
npm run android:release   # ...plus assembleRelease and bundleRelease (AAB)
```

Outputs land in `android/app/build/outputs/`:

| Artifact | Path | Notes |
| --- | --- | --- |
| Debug APK | `apk/debug/app-debug.apk` | Signed with the standard Android debug key — `adb install` it and play. |
| Release APK | `apk/release/app-release-unsigned.apk` | Unsigned until you supply a keystore (below). |
| Play bundle | `bundle/release/app-release.aab` | Upload format for Google Play. |

### Prerequisites

- **JDK 17** — Capacitor 6's Android toolchain (AGP 8.2.1 / Gradle 8.2.1) does not
  run on JDK 21. `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` (or your JDK 17 path).
- **Android SDK** with `platforms;android-34`, `build-tools;34.0.0` and `platform-tools`.
  Point `ANDROID_HOME` at it, or write `sdk.dir=/path/to/sdk` into `android/local.properties`
  (git-ignored).

The `android/` directory is committed, so a clean checkout builds without needing
`npx cap add android` first. Re-running `cap sync` is safe — the customisations
below live in files Capacitor does not regenerate.

### Signing a release

No keystore is committed. Create one and pass it through Gradle properties or
environment variables:

```bash
keytool -genkeypair -v -keystore earth-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias earth

EARTH_KEYSTORE=/abs/path/earth-release.jks \
EARTH_KEYSTORE_PASSWORD=... EARTH_KEY_ALIAS=earth EARTH_KEY_PASSWORD=... \
  npm run android:release
```

When none is configured the release target still assembles (unsigned), so a fresh
clone is never blocked on secrets.

### What the native shell adds

The Android build is not just the web bundle in a WebView — these pieces are
native and only active on-device (all no-ops in the browser build, behind
`src/platform/native.ts`):

- **Saves go to `SharedPreferences`** via Capacitor Preferences, not `localStorage`.
  A WebView's local storage lives in the app's cache-ish data directory and can be
  cleared by the system or by "clear cache"; SharedPreferences survives that.
- **`appStateChange` is the authoritative save point.** `beforeunload` is not
  guaranteed to fire when Android kills a backgrounded app, so the game saves when
  the app goes inactive and re-ticks (collecting offline progress) when it returns.
- **Hardware back navigates, it doesn't quit.** Back unwinds the screen history and
  only exits from Home. Previously a mis-swipe on any screen closed the app.
- **Haptics on purchase**, wired to the existing (previously inert) `vibrationEnabled` setting.
- **Status bar** colour/style follows the in-app light/dark theme.
- **Splash screen** is held until React paints, then dismissed, rather than on a timer.
- Portrait-locked, `targetSdk 34`, `minSdk 22`, Auto Backup restricted to the save.
- **An AdMob banner** anchored to the bottom of the screen — see below.

### Ads (AdMob)

A single anchored adaptive banner, via `@capacitor-community/admob`, wired up in
`src/platform/ads.ts` and started once from `App.tsx`. There are no interstitials
and no rewarded ads.

| | |
| --- | --- |
| App ID | `ca-app-pub-6872627319793193~7208922044` — declared as `com.google.android.gms.ads.APPLICATION_ID` in `android/app/src/main/AndroidManifest.xml`, where the SDK reads it. It is **not** read from JavaScript, and the app crashes at startup if it is missing. |
| Banner unit | `ca-app-pub-6872627319793193/5213314092` |
| Test unit | `ca-app-pub-3940256099942544/6300978111` (Google's public sample unit) |

Three things about the implementation are worth knowing before changing it:

- **The banner is a native view drawn over the WebView.** Nothing in CSS knows it
  is there, so the layout would happily put the bottom nav underneath it. The
  plugin reports the ad's height once it loads; `App.tsx` writes that (plus an 8px
  gap) into the `--ad-banner-inset` custom property, which pads the app shell and
  raises the modal overlay's floor. The property is `0px` until an ad actually
  loads, so the browser build and any device where nothing fills lose no space at
  all — and it goes back to `0px` if the banner is removed. `npm run test:e2e`
  asserts all three states.
- **Only a production build asks for live ads.** `npm run dev`, and any build made
  with `VITE_ADMOB_TEST=1`, request Google's test unit instead. AdMob counts
  impressions and clicks a developer generates on their own live unit as invalid
  traffic, and that gets accounts suspended — so to exercise ads on a device,
  build with `VITE_ADMOB_TEST=1 npm run android:build`, not a plain debug build.
- **Consent runs before the SDK is initialized.** Serving ads in the EEA/UK without
  a User Messaging Platform consent message is a policy violation, so startup goes
  UMP consent → `AdMob.initialize()` → `showBanner()`, in that order, and falls back
  to requesting non-personalized ads whenever no consent choice could be recorded.
  Where a consent form exists, Settings grows an **Ad Privacy Choices** row that
  reopens it; players who were never shown one don't see the row.

Two things are configured outside this repo and are on you: the GDPR/EEA consent
message has to be created under **Privacy & messaging** in the AdMob console (the
UMP form is loaded from there, not bundled), and the Play Console data-safety form
has to declare the advertising ID, because `play-services-ads` merges
`com.google.android.gms.permission.AD_ID` into the manifest.

## Technology choice: web app + Capacitor, not native Kotlin

The brief asks for an Android app. Rather than a native Kotlin/Compose project, this is a
TypeScript/React game engine designed mobile-first and shipped as a real Android app via
[Capacitor](https://capacitorjs.com/). That gets you:

- **An installable Android app.** `android/` is a full Gradle project and
  `npm run android:build` produces a signed-for-debug APK, an unsigned release APK, and a Play
  AAB (see "Building the Android app" above).
- **A dev loop where the game can be run, played and screenshotted** during development —
  the same bundle that ships in the APK is exercised end-to-end by `npm run test:e2e`.
- **The same codebase as an installable PWA** (`public/manifest.webmanifest`) if you never want
  to touch Gradle at all.

The trade-off is honest: the simulation and UI are web code running in a WebView, so this
inherits WebView performance rather than Compose's. For an idle game whose hot loop is a 250 ms
tick over a few hundred objects, that is not the bottleneck; the parts where the platform
genuinely matters (storage durability, app lifecycle, back navigation, haptics) are handled
natively rather than papered over.

## Architecture

```
src/
  engine/           Pure TypeScript, zero UI dependencies, fully unit-tested.
    bignum.ts          Arbitrary-scale Decimal (mantissa/exponent), like break_infinity.js
    format.ts          Number formatting: compact / scientific / engineering / full
    constants.ts        All tunable balance constants in one place
    gases.ts           Gas registry (CO2/CH4/N2O/H2O/O3/fluorinated) — data-driven, extensible
    climate.ts         Carbon-cycle integration, radiative forcing, temperature anomaly
    habitability.ts    Composite habitability score from 5 independent factors
    resources.ts       Spendable currencies (Energy, Research, Coal, Oil, Steel, Concrete)
    technologies/      99 technologies across 11 branches (data files, one per branch)
    simulation.ts      The tick function: production, multipliers, gas/resource integration
    economy.ts         Purchase logic + the price-stability invariant everything else rests on
    ownership.ts       Per-generator "every 10th copy doubles its output" bonuses
    prestige.ts        Earth Points formula + permanent upgrade tree
    offline.ts         Offline/backgrounded catch-up (closed-form, not stepped — see below)
    achievements.ts    32 achievements, pure check functions
    challenges.ts      8 challenges with live restrictions + goals + permanent rewards
    events.ts          12 random events (temporary multipliers + instant gas bursts)
    milestones.ts      39 once-per-run news headlines, threshold-triggered, no mechanical effect
    save.ts            Versioned JSON (de)serialization, corruption-safe, backup slot
    gameState.ts       The GameState shape + factories (createNewGame / startNewRun)
  store/
    useGameStore.ts    Zustand store: owns the tick loop, autosave, wires engine -> UI
  ui/
    screens/           Home, Atmosphere, Technology, Production, Prestige, Achievements,
                       Challenges, Statistics, Settings
    components/        BottomNav, TechCard, modals (offline/collapse/uninhabitable), toasts,
                       tutorial banner
  styles/global.css    Theme tokens (light/dark via prefers-color-scheme + explicit override),
                       era-based visual progression (green -> industrial -> red)
```

**Why this split:** `engine/` never imports React or Zustand — every simulation rule is a pure
function of `GameState` and can be (and is) unit-tested without a DOM. The store is the only
place that knows about wall-clock time, `setInterval`, and browser APIs; screens only read
`useGameStore` state and dispatch actions, never compute simulation values themselves.

### The number system

Real-world late-game numbers in this genre exceed `Number.MAX_VALUE` (~1.8e308). `bignum.ts`
implements a small `Decimal` class storing `sign * mantissa * 10^exponent` with the mantissa
normalized to `[1, 10)` — the same technique `break_infinity.js` uses, reimplemented locally
so there's no runtime dependency and every operation has a unit test. Because `exponent` is
itself just a JS number, the representable range extends to roughly `10^(1.8e308)`, which is
effectively unbounded for this game.

### The climate model

- **Gases** (`gases.ts`) are tracked as concentration *above pre-industrial baseline* — not
  absolute mass — so the natural steady-state doesn't need to be simulated, only the
  anthropogenic perturbation. Each gas carries a lifetime (years), a native display unit
  (ppm/ppb/ppt/DU), and its own forcing function (CO₂ and O₃ logarithmic, CH₄/N₂O
  square-root, fluorinated gases linear — all loosely modeled on real IPCC-style
  approximations, per gas, per `constants.ts`).
- **Carbon cycle** (`climate.ts`): each gas decays via `dE/dt = production − k·E`, integrated
  with the *closed-form* solution rather than small Euler steps. That's not just an
  optimization — it means a 250 ms live tick and an 8-hour offline catch-up produce
  numerically identical results for the same elapsed time (see
  `simulation.test.ts` → "is step-size independent"), which is also what makes offline
  progress a single calculation instead of a stepped simulation loop.
  Sink efficiency (how well oceans/soil/vegetation absorb emissions) weakens as temperature
  rises, per `CARBON_CYCLE.warmingSinkPenalty`.
- **Temperature** (`climate.ts` → `computeTemperatureAnomaly`): linear in total radiative
  forcing (real-world-plausible climate sensitivity ~0.8°C per W/m²) plus a mild super-linear
  term so extreme late-game forcing produces escalating, increasingly absurd temperatures
  instead of flattening out.
- **Habitability** (`habitability.ts`): five independent 0–1 factors — temperature, ocean
  acidity (pH drift from CO₂), sea-level rise (integrated from sustained warming), agricultural
  output, biodiversity — **multiplied together**, not averaged, so any single factor collapsing
  to zero (e.g. temperature past the threshold) zeroes habitability even if others are fine.
  `habitability ≤ 0` is the reset trigger.

### The technology tree

99 technologies (68 buildable generators, 31 research nodes) across the 11 requested branches (Primitive, Agriculture, Industry,
Electricity, Fossil Fuels, Transportation, Construction, Chemistry, Globalization, Digital,
Endgame), defined as data (`technologies/*.ts`) rather than hardcoded logic — adding
technology #100 means adding one object to a branch file. Every tech has a `kind`:

- `unlock` — one-time tree node, gates later tech, no production of its own
- `generator` — repeatably purchasable, produces gas and/or resources per owned unit
- `multiplier` — one-time purchase that permanently scales production
- `choice` — mutually exclusive with siblings sharing a `choiceGroup` (the Coal vs. Nuclear
  "Energy Strategy" branching decision from the spec is implemented this way)

Cost and production numbers come from `technologies/scaling.ts`'s tier-based curves rather than
94 hand-picked constants, keyed off each tech's overall-progression `tier` (not per-branch), so
branches that unlock in parallel stay balanced against each other. Those curves read every one
of their numbers from `constants.ts > BALANCE` — see "Pacing" below.

**The two shopping screens are disjoint.** The Technology screen is the research tree and sells
only `unlock`, `multiplier` and `choice` nodes; every `generator` — anything you buy repeatedly
to raise output — is sold on the Production screen and nowhere else. Neither screen ever shows
the same card as the other.

A full graph-integrity test (`technologies/index.test.ts`) checks there are no dangling
`requires` references and no dependency cycles across all 99 nodes, and `balance.test.ts`
checks every node is actually reachable from the one technology a run starts with.

### Prices never rise

The one rule the whole economy is built around: **a price you have been quoted is the price you
pay.** Nothing in the game ever revises a number upward behind the player's back.

Concretely, the amount charged for a technology is a pure function of the technology and how
many of *that* technology you already own, scaled down (never up) by the prestige discount. A
one-time unlock, multiplier or choice is quoted once and costs exactly that forever. A
generator's first unit always costs its listed base price, and only the units you have
personally bought of it make the next one dearer.

There used to be a civilization-complexity surcharge that multiplied every price by the number
of distinct technologies owned. It paced the middle of the game effectively, but it did so by
re-pricing things the player had already been shown: every new frontier made every *other*
frontier more expensive, so the reward for expanding was a bigger bill. It is gone, along with
the prestige upgrade that existed to cancel it. `balance.test.ts` locks the invariant in by
running the live purchase path against an empty world and a world that owns forty of
everything, and asserting the charge is identical.

### Ownership bonuses

Every generator carries its own progress track: **every tenth copy you own doubles that
building's entire output**, permanently, for the rest of the run (`ownership.ts`, spacing in
`constants.ts > OWNERSHIP_BONUS`). The card shows the bar filling and a toast fires when one
lands.

This exists because a price that climbs per unit makes the eleventh copy of something strictly
worse than the first copy of something newer — correct pacing, but feedback that goes nowhere.
The spacing is what keeps it honest: across one ten-unit span a generator's unit price grows by
`unitCostGrowth^10` (about ×4) against a single ×2 from the bonus, so the value of each further
copy still falls and broadening into new technology still wins in the long run. Tightening
`everyUnits` far enough to invert that would make the tech tree decoration; `balance.test.ts`
guards the relationship directly.

### Pacing

A first run takes a few days of real time — roughly 2½ days of near-continuous play, 3–5 days
at a realistic check-in-a-few-times-a-day pace — and every reset after that is faster. Getting
there is not a matter of one multiplier, because the tech tree is not a uniform ladder: through
the middle of a run ten branches produce in parallel and compound into each other, while the
endgame narrows to a single chain. Three knobs in `constants.ts > BALANCE` do the work, and
they solve three different problems:

- **`generatorCostGrowthPerTier` vs `productionGrowthPerTier`.** Their ratio sets how much
  longer each tier takes than the last. Below 1 the game runs away and finishes itself in an
  evening; the shipped values put it around 1.5. With the complexity surcharge gone this ratio
  carries the pacing that used to be split between the two, which is why it is much steeper
  than it looks like it should be.
- **`unitCostGrowth`.** Per unit owned of the same generator — the only thing in the game that
  ever raises a price, and only in response to the player's own purchases. It is also what the
  ownership bonuses are balanced against.
- **`lateTierCompression`.** Above `flattenLadderFromTier` the cost *and* output ladders are
  compressed together, so late technologies stay priced against what a late economy can
  actually earn.

Two further relationships matter. `gasProductionGrowthPerTier` sits *below* the resource curve,
so emissions lag the economy and the tree can be finished before the planet dies — this is the
binding constraint on run length, since a run ends when the planet dies rather than when the
tree runs out, and pushing the cost curve up without pulling this down simply makes the last
technologies unreachable. And the `massPerUnit` figures in `gases.ts` are scaled so CO₂ leads
the warming for most of a run rather than being a rounding error next to the synthetic gases.

The endgame chain is deliberately **dense** — a node at every tier from 28 to 38, alternating
generators (something to keep buying, and to farm ownership doublings on) with multipliers (a
single loud payoff). Widening those tier gaps is the fastest way to reintroduce the hours of
dead air the last third of the game used to have.

`scripts/balanceSim.ts` is the tool all of this was measured with: it plays a full run under a
configurable play pattern and reports when each technology is bought, the worst gaps between
new unlocks, the climate trace, and what the run would pay out in Earth Points.
`balance.test.ts` locks in the *relationships* above (not the specific numbers, which are meant
to be retuned), and `pacing.test.ts` plays a compressed run in CI so a retune cannot silently
ship a half-hour game or an endgame priced out of reach.

### World news

`milestones.ts` holds 39 deterministic, once-per-run headlines that fire the first time a run
crosses a threshold. They have no mechanical effect: they exist so a multi-day run reads as a
story rather than a rising number. Early ones are keyed to *technology* and report local
consequences (a river below the mills, smog, an ozone hole), because a handful of campfires
genuinely cannot move a planet's atmosphere and the rest of the simulation doesn't pretend
otherwise; climate-keyed ones take over in the back half and escalate from treaty thresholds to
obituaries. They surface as a breaking-news banner and accumulate in a feed on the Home screen
(latest four) and the Atmosphere screen (all of them).

### Prestige

Earth Points (name is one constant, `PRESTIGE_CURRENCY_NAME` in `constants.ts` — change it
there to rebrand). The formula (`prestige.ts`) scores total greenhouse-gas mass produced (with
diminishing returns via a sub-1 exponent), scales that by damped peak-radiative-forcing and
civilization-level terms, then multiplies by **speed** — how much faster the run was than
`PRESTIGE.referenceRunSeconds`, squared and bounded.

That last term is what makes prestige a loop rather than a decoration, and it is worth
understanding before retuning it. Every run in this game ends in the same place — habitability
zero, tech tree finished — so an outcome-only score pays a *stronger* civilization *less*: a
stronger civilization kills the planet sooner and therefore emits less in total before it does.
Measured before the speed term was added, a second Earth built on a full first prestige spend
finished 32% faster and earned 8% **fewer** points than the first. Scoring speed instead makes
each Earth roughly double the last, which is the shape the upgrade ladder's costs assume.

Eleven permanent upgrades are implemented (production multipliers, starting technologies,
starting generators, starting resources, offline cap, tech cost discount); challenge completions
grant permanent rewards through the same effect shape, folded in alongside prestige upgrades in
`computePrestigeMultipliers`.

The architecture has one prestige layer (`Earth Points`) implemented; `gameState.ts`'s
`runNumber`/`startNewRun` split from lifetime-persistent fields is deliberately structured so
additional layers (Civilization/Planetary/Cosmic Points) can be added later without touching
the reset logic for this layer.

### Save system

`save.ts` walks `GameState` and JSON-serializes every `Decimal` as a tagged `{__decimal:
[sign, mantissa, exponent]}` triple (via a `replacer`/`reviver` pair — note `Decimal`
deliberately does *not* define a method literally named `toJSON`, since `JSON.stringify`
auto-invokes that before a replacer ever sees the value, which would silently unwrap it and
defeat the tagging). Every save keeps the previous save as a backup slot; a corrupted primary
save falls back to it automatically. Storage is behind a tiny `StorageAdapter` interface so the
same logic works against `localStorage` in the browser build and Capacitor's `Preferences`
plugin in the packaged Android app; `store/useGameStore.ts` picks the adapter once at startup
from `Capacitor.isNativePlatform()`, so no engine code knows which platform it is on.

## What's implemented vs. deferred

Built as a genuinely playable, complete vertical slice (Phase 1–3 of the brief's own phasing,
plus pieces of Phase 4), not a design document:

**Implemented:** the full research → build → compound loop, driven from the first tick by a
Natural Fire every run owns (there is no tap button — Energy comes from a source of energy);
all 6 gas types with individually-tuned forcing/lifetime/removal; 99 technologies across all 11
branches including a branching strategic choice; per-generator ownership bonuses that double a
building's output every tenth copy; carbon-cycle sinks that weaken with warming; 5-factor
habitability collapse; Earth-N reset/prestige with 11 permanent upgrades and a speed-scored
payout; offline progress (12h base, upgradeable); 32 achievements; 8 challenges with live
restrictions and permanent rewards; 12 random events; 39 milestone news headlines with a per-run
feed; 4 number-format modes; save/load with corruption protection and a versioned migration
chain; a 12-step interactive tutorial; light/dark/system theming; temperature-driven visual era
progression; buy 1/10/100/max on every generator, defaulting to max.

**Deliberately deferred** (flagged rather than half-built): additional prestige layers beyond
Earth Points (architecture supports adding them — see "Prestige" above); individually-simulated
synthetic gases (CFCs/HFCs/PFCs/SF₆ are currently one aggregate `fluorinated` bucket — the gas
registry is designed so splitting it into four is a data change, not an engine change);
material-resource *upkeep* (materials are one-time purchase costs, not continuous consumption,
to avoid shutdown/brownout logic that the brief didn't require); audio (hooks and settings
toggles exist; no audio assets are wired up); a from-scratch illustrated art style (visual
progression is currently conveyed through color/theme, not custom artwork).

## What's been verified

- **`npm run lint`** — `tsc --noEmit`, clean.
- **`npm test`** — 208 unit tests covering the Decimal system, climate/forcing/habitability
  formulas, the full technology graph, production math, prestige, offline catch-up, save
  round-tripping (including large-Decimal precision) and the v1→v2 migration, achievements,
  challenges, milestone news, the pacing invariants described under "Pacing", and the
  purchase-affordability boundary cases added by the audit below.
- **`npm run test:e2e`** (`scripts/playtest.mjs`) — serves the *exact bundle packaged into the
  APK* (`android/app/src/main/assets/public`) and drives it in headless Chromium at both
  390×844 and 360×740 with a mobile user agent and touch enabled: 34 assertions covering
  layout (no document overflow, bottom nav on-screen, 48dp touch targets, the banner-ad inset
  raising and releasing the nav), the golden path
  (Energy accruing with no input → the Technology and Production screens selling disjoint
  lists → buying Controlled Fire on Production → CO₂ flowing → the first news headline), every
  one of the nine screens rendering live data, save-to-storage, and survival across a reload —
  with zero uncaught page errors.
- **`./gradlew assembleDebug assembleRelease bundleRelease`** — all three Android variants
  build, with Android Lint's release-blocking checks reporting **no issues**. The debug APK is
  4.7 MB, the release AAB 3.6 MB. The five Capacitor plugin classes are confirmed present in
  the packaged `classes.dex`. That run predates the AdMob banner, which adds a sixth plugin and
  the `play-services-ads` dependency; those have not been through Gradle here (no Android SDK
  in this environment) — re-run the command above after `npm install`.

**Not verified on real hardware.** This build environment has no KVM/VT-x, so an Android
emulator cannot boot and no physical device is attached. Everything above the native bridge is
exercised in a Chromium of the same engine family as the Android WebView; what remains unproven
by execution is the behaviour of the native plugins themselves (Preferences, App, Haptics,
StatusBar, SplashScreen) — they are verified structurally (registered, compiled into the APK)
rather than by running. Install `app-debug.apk` on a device to close that gap.

The same gap covers AdMob, and more of it: the banner's Gradle wiring, manifest app ID and the
inset it drives are in place and the inset is exercised in Chromium, but no ad has been
requested from a real device here, and the consent form itself is served from the AdMob console
rather than the APK. Build with `VITE_ADMOB_TEST=1 npm run android:build` and install on a
device to see a real (test) banner fill.

## Code audit

The existing engine was in good shape — 145 tests passing, typecheck clean, no dangling
technology references. The audit found the following, all fixed in this change:

### Correctness

1. **Buy-max left an affordable unit on the table.** `maxAffordableQuantity` inverts a
   geometric series through `log10`, and on an exact boundary the float landed one unit low —
   a wallet holding exactly the cost of 4 units bought 3. The same error in the other direction
   would have been worse: the caller charges the over-counted quantity and the subtraction
   clamps at zero, i.e. a free unit. The quantity is now settled against the exact
   geometric-series cost rather than the logarithm, with the correction bounded.
   Regression test: `engine/economy.affordability.test.ts` (fails on the pre-fix code).

### Android / mobile

2. **The layout overflowed the viewport.** `body` had no `margin` reset, so the UA's default
   8 px margin sat around a `100dvh` app shell — the document scrolled by 16 px and the bottom
   nav's last row of pixels fell off-screen. Now reset, and asserted in the e2e run.
3. **Three navigation destinations were unreachable.** Nine tabs at `min-width: 58px` need
   522 px; phones are 360–430 dp. Achievements, Statistics and Settings sat past the right
   edge behind a horizontal scroll with no visual affordance. Columns now flex to fit, with
   labels shortened to stay legible at 360 dp and 52 px touch targets.
4. **The save could be wiped by the system.** The README described Capacitor `Preferences` as
   the Android storage backend, but nothing imported it — the packaged app would have used
   `localStorage`, which Android may clear. Now wired for real.
5. **Saves could be lost on app kill.** The only save-on-exit hook was `beforeunload`, which
   Android does not guarantee to run for a backgrounded app. `appStateChange` is now the
   authoritative save point.
6. **Hardware back quit the game from any screen.** Now unwinds screen history, exiting only
   from Home.
7. **`vibrationEnabled` was a dead setting** — present in the settings UI and the save format,
   wired to nothing. Now drives Capacitor Haptics on a technology purchase.
8. **Long-press raised a text-selection callout** on buttons, and repeated presses could be
   read as double-tap gestures. `user-select`/`touch-callout`/`touch-action` now set for a
   chrome-like surface rather than a document.
9. **White flash on launch.** The generated theme left `windowBackground` null, showing the
   system default white between splash teardown and first WebView paint, in a dark game.

### Packaging

10. **Default Capacitor branding.** Replaced with generated EARTH launcher icons (legacy,
    round, and adaptive-foreground at all five densities) and splash screens at all densities
    in both orientations — see `scripts/gen_icons.py`.
11. **`@capacitor/core` and `@capacitor/preferences` were devDependencies.** They are bundled
    into the shipped app, so they are runtime dependencies; moved.
12. **Source maps were being packaged into the APK** (~750 KB of developer-only weight).
    Excluded via `aaptOptions.ignoreAssetsPattern`.
13. **Portrait lock was missing** from the manifest despite the UI being a fixed one-handed
    column.
14. **Auto Backup was unrestricted.** Now scoped to the save in SharedPreferences, so a restore
    does not drag a stale WebView state onto a fresh install.
15. **`init()` stacked event listeners** on every call (React StrictMode double-invokes effects
    in development). Listeners are now disposed and re-registered.

### Known and accepted

- **`npm audit` reports 7 advisories, all in devDependencies** (`@capacitor/cli` → `tar`,
  `vite`, `vitest`). None ship in the APK. `vite`/`vitest` are at the latest patch of their
  major; clearing the rest requires major upgrades (Vite 7, Vitest 4, Capacitor CLI 8) that
  would be a separate, breaking change.
- **`minifyEnabled false` for release.** R8 plus Capacitor's reflection-based plugin loading
  wants device verification before being turned on; enabling it blind is how you ship a release
  build that crashes where the debug build did not.
- **`android.permission.INTERNET` is still requested.** Capacitor's WebView bridge requires it
  even though the game makes no network requests; `usesCleartextTraffic="false"` is set.
