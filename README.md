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
npm test           # vitest — 145 unit tests over the whole simulation engine
npm run build      # production build to dist/
npm run lint       # tsc --noEmit
```

No backend, no network calls — the whole game runs client-side and saves to local storage.

## Technology choice: web app + Capacitor, not native Kotlin

The brief asks for an Android app. Rather than a native Kotlin/Compose project (which needs an
Android SDK, an emulator, and a Gradle toolchain — none available in a plain dev container, and
none of which let me actually run and screenshot the app while building it), this is built as a
TypeScript/React web app designed mobile-first, wrapped for Android via
[Capacitor](https://capacitorjs.com/) (`capacitor.config.ts` is already in place). That gets you:

- A real, installable Android app (`npx cap add android && npx cap sync android` once you have
  the Android SDK available — Capacitor wraps the built `dist/` in a WebView shell).
- A dev loop where the game can actually be run, played, and screenshotted in a browser during
  development (which is how this build was verified — see "What's been verified" below).
- The same codebase also works as an installable PWA if you never want to touch Gradle at all.

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
plugin in the packaged Android app.

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

- `npm test` — 145 unit tests covering the Decimal system, climate/forcing/habitability
  formulas, the full technology graph, production math, prestige, offline catch-up, save
  round-tripping (including large-Decimal precision), achievements, and challenges.
- The full golden path (tap → unlock Controlled Fire → CO₂ flowing → buy loop → every screen
  renders live data) was driven end-to-end in a real headless-Chromium browser at a 390×844
  mobile viewport, catching and fixing two real bugs in the process: `natural_fire` originally
  cost Research with no way to ever earn Research before owning it (a hard dead-end at turn
  one — fixed by making it a starting condition rather than a purchase), and the Home screen's
  "next objective" hint could get stuck forever suggesting an already-owned generator.
- The collapse → reset → prestige-summary → new-run → offline-catch-up modal sequence was
  verified by injecting a crafted collapsed save state and confirming each screen in order.
