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
npm test           # vitest — 152 unit tests over the whole simulation engine
npm run test:e2e   # drives the packaged bundle in headless Chromium (see below)
npm run build      # production build to dist/
npm run lint       # tsc --noEmit
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
- **Haptics on tap**, wired to the existing (previously inert) `vibrationEnabled` setting.
- **Status bar** colour/style follows the in-app light/dark theme.
- **Splash screen** is held until React paints, then dismissed, rather than on a timer.
- Portrait-locked, `targetSdk 34`, `minSdk 22`, Auto Backup restricted to the save.

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
    technologies/      93 technologies across 11 branches (data files, one per branch)
    simulation.ts      The tick function: production, multipliers, gas/resource integration
    economy.ts         Purchase logic, manual tap, cost-affordability math
    prestige.ts        Earth Points formula + permanent upgrade tree
    offline.ts         Offline/backgrounded catch-up (closed-form, not stepped — see below)
    achievements.ts    30 achievements, pure check functions
    challenges.ts      8 challenges with live restrictions + goals + permanent rewards
    events.ts          10 random events (temporary multipliers + instant gas bursts)
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

93 technologies across the 11 requested branches (Primitive, Agriculture, Industry,
Electricity, Fossil Fuels, Transportation, Construction, Chemistry, Globalization, Digital,
Endgame), defined as data (`technologies/*.ts`) rather than hardcoded logic — adding
technology #94 means adding one object to a branch file. Every tech has a `kind`:

- `unlock` — one-time tree node, gates later tech, no production of its own
- `generator` — repeatably purchasable, produces gas and/or resources per owned unit
- `multiplier` — one-time purchase that permanently scales production
- `choice` — mutually exclusive with siblings sharing a `choiceGroup` (the Coal vs. Nuclear
  "Energy Strategy" branching decision from the spec is implemented this way)

Cost and production numbers come from `technologies/scaling.ts`'s tier-based curves rather than
93 hand-picked constants, keyed off each tech's overall-progression `tier` (not per-branch), so
branches that unlock in parallel stay balanced against each other.

A full graph-integrity test (`technologies/index.test.ts`) checks there are no dangling
`requires` references and no dependency cycles across all 93 nodes.

### Prestige

Earth Points (name is one constant, `PRESTIGE_CURRENCY_NAME` in `constants.ts` — change it
there to rebrand). The formula (`prestige.ts`) scores total greenhouse-gas mass produced (with
diminishing returns via a sub-1 exponent), then scales that by peak radiative forcing,
civilization level, and run duration — all four weights live in `constants.ts.PRESTIGE`. Nine
permanent upgrades are implemented (production multipliers, starting tech, tap power, offline
cap, tech cost discount, etc.); challenge completions grant permanent rewards through the same
effect shape, folded in alongside prestige upgrades in `computePrestigeMultipliers`.

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

**Implemented:** the full tap → buy → automate loop; all 6 gas types with individually-tuned
forcing/lifetime/removal; 93 technologies across all 11 branches including a branching
strategic choice; carbon-cycle sinks that weaken with warming; 5-factor habitability collapse;
Earth-N reset/prestige with 9 permanent upgrades; offline progress (capped, upgradeable);
30 achievements; 8 challenges with live restrictions and permanent rewards; 10 random events;
4 number-format modes; save/load with corruption protection; a 10-step interactive tutorial;
light/dark/system theming; temperature-driven visual era progression; buy 1/10/100/max on every
generator.

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
- **`npm test`** — 152 unit tests covering the Decimal system, climate/forcing/habitability
  formulas, the full technology graph, production math, prestige, offline catch-up, save
  round-tripping (including large-Decimal precision), achievements, challenges, and the
  purchase-affordability boundary cases added by the audit below.
- **`npm run test:e2e`** (`scripts/playtest.mjs`) — serves the *exact bundle packaged into the
  APK* (`android/app/src/main/assets/public`) and drives it in headless Chromium at both
  390×844 and 360×740 with a mobile user agent and touch enabled: 23 assertions covering
  layout (no document overflow, bottom nav on-screen, 48dp touch targets), the golden path
  (tap → earn → buy Controlled Fire → CO₂ flowing), every one of the nine screens rendering
  live data, save-to-storage, and survival across a reload — with zero uncaught page errors.
- **`./gradlew assembleDebug assembleRelease bundleRelease`** — all three Android variants
  build, with Android Lint's release-blocking checks reporting **no issues**. The debug APK is
  4.7 MB, the release AAB 3.6 MB. The five Capacitor plugin classes are confirmed present in
  the packaged `classes.dex`.

**Not verified on real hardware.** This build environment has no KVM/VT-x, so an Android
emulator cannot boot and no physical device is attached. Everything above the native bridge is
exercised in a Chromium of the same engine family as the Android WebView; what remains unproven
by execution is the behaviour of the five native plugins themselves (Preferences, App, Haptics,
StatusBar, SplashScreen) — they are verified structurally (registered, compiled into the APK)
rather than by running. Install `app-debug.apk` on a device to close that gap.

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
   wired to nothing. Now drives Capacitor Haptics on tap.
8. **Long-press raised a text-selection callout** on the tap button, and repeated taps could be
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
