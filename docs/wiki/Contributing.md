# Contributing

[← Documentation home](Home.md)

Contributions welcome: bug reports, balance feedback, accessibility fixes, and code. This page is
the part that is specific to *this* codebase — the things a first change tends to get wrong.

> [!IMPORTANT]
> **This repository has no licence file.** Until one exists, the default applies — all rights
> reserved — which means the terms under which a contribution could be accepted are undefined. If
> you are considering a substantial contribution, ask the owner to add one first. See
> [GitHub metadata](../GITHUB-METADATA.md).

## Getting set up

```bash
git clone https://github.com/themantas1994/Earth-idle-game.git
cd Earth-idle-game
./gradlew test        # 205 tests, should pass on a clean clone
```

Needs **JDK 21** and an Android SDK with `platforms;android-37` and `build-tools;37.0.0`. See
[Build system](Build-System.md).

## Before you start

Read [Architecture](Architecture.md). The layering is the load-bearing decision in the project, and
a change that breaks it will be asked to change.

**The one rule that catches everyone:** `domain/` has **no Android dependency at all** — no
`android.*`, no `androidx.*`, no `kotlinx.coroutines`. Its only non-project imports are
`java.math`, `kotlin.math`, `kotlin.random` and `kotlinx.serialization.json`. That is what lets 205
tests run on the JVM in seconds instead of needing a device.

```bash
# Must print nothing.
grep -rn "^import android\|^import androidx\|^import kotlinx.coroutines" \
  app/src/main/java/com/earthgame/idle/domain/
```

## Conventions

- **Kotlin official style** (`kotlin.code.style=official`). Android Studio's default formatter.
- **Immutable data.** `GameState` and every value type it holds is immutable; transitions take one
  and return the next. No `var` in domain models.
- **Pure functions in `domain/`.** No clock, no I/O, no randomness except an injected `Random`.
  `nowMs` is always a parameter.
- **No magic numbers in engine logic.** Tuning constants live in `Constants.kt` or the technology
  data files, so the game can be rebalanced without touching simulation code.
- **`GameDecimal`, not `Double`, for anything a player accumulates.** See
  [the common mistakes table](GameDecimal.md#common-mistakes).
- **Comments explain *why*.** The codebase is unusually heavily commented, deliberately: the maths is
  the product, and a formula without its rationale is unmaintainable. Match that. Do not add comments
  that restate the code.
- **Visibility is the narrowest that works.** `private` by default; `internal` for cross-file
  helpers; public only for a real API.

## Testing requirements

Every behavioural change needs a test. See [Testing](Testing.md) for where things live.

- **Behaviour, not implementation.** Assert what a player would notice, not how a private helper is
  arranged.
- **Never a clock or real elapsed time.** Every clock in the engine is a parameter; inject it. Use
  virtual time for coroutines.
- **UI tests go in `EarthAppScreenTest`** (Robolectric), so they run in `./gradlew test`. Reserve
  `androidTest/` for what genuinely needs hardware.
- **Never hand-write a parity fixture.** The whole point is that the numbers came from executing the
  reference.

Run before opening a PR:

```bash
./gradlew test lintDebug lintRelease assembleDebug
```

## Procedures for the risky changes

### Changing gameplay maths

> **The reference implementation in `tools/ts-reference/` is the parity oracle.** Any change to a
> formula, constant, price, growth rate, technology, achievement, challenge, event or headline is a
> change to **both** engines.

```bash
# 1. change app/src/main/java/com/earthgame/idle/domain/…
# 2. change tools/ts-reference/engine/… identically
cd tools/ts-reference && npm install
npx vitest run          # the reference's own 198 tests
npm run fixtures        # regenerates app/src/test/resources/parity/*.json
cd ../.. && ./gradlew test
git diff --stat app/src/test/resources/parity/    # only what you expected should move
```

Generation is deterministic, so an unexpected fixture change means you coupled two systems you did
not mean to. See [Reference parity](Reference-Parity.md).

### Adding a technology

See [the full procedure](Technology-System.md#adding-a-technology). The short version: derive every
number from the curves in `Scaling.kt` — **never hand-pick a price** — mirror it in the reference,
regenerate, and update the counts in the README and on the wiki page.

### Adding an achievement, challenge or event

See [Achievements and challenges](Achievements-and-Challenges.md#adding-one). Goal predicates must
be **pure**: `(GameState, civLevel) -> Boolean`, no clock, no randomness. A challenge's reward must
be expressible as a `PrestigeUpgradeEffect` — a test asserts exactly that.

### Touching the save format

See [Game state](Game-State.md#adding-a-field) and [Save migrations](Save-Migrations.md).

- Give every new field a **default**, and make the decoder fall back to it. Loading must never throw.
- Only add a **migration** if an *existing* value is wrong under the new rules. A merely-absent field
  needs none.
- Migrations must be **idempotent**. A test asserts it for the whole chain.
- Decide whether the field resets, and put it in `startNewRun` if so.

### Touching the UI

- Read [UI architecture](UI-Architecture.md) first, particularly the threading section.
- Every state transition goes through `mutate { … }` under `stateLock`. Do **not** add a bare
  `_uiState.update { }` around a compute that read `_uiState.value` first — that is the exact bug
  `GameViewModelConcurrencyTest` exists to catch.
- New domain value types that a composable takes as a parameter belong in
  `app/compose-stability.conf`.
- Read [Accessibility](Accessibility.md): merge label/value semantics, hide decoration, keep touch
  targets at 48 dp, and honour `LocalReducedAnimations`.

### Changing balance

Balance changes are welcome but need **evidence** — a description of the run that motivated it, not
a feeling. `BalanceInvariantsTest` will refuse a change that breaks the pacing relationships (cost
growth outpacing production growth, gas growth staying under resource growth, the ownership bonus
staying under the price of the span that earns it), and those refusals are load-bearing. Read
[Economy and production](Economy-and-Production.md#the-balance-curves) before moving a number there.

## Things people get wrong

| | |
| :-- | :-- |
| Adding an Android import to `domain/` | Breaks the layering and makes the tests need a device |
| Using `Double` for a player-facing quantity | It becomes `Infinity` late-game and everything downstream turns to `NaN` |
| Reading `System.currentTimeMillis()` in the engine | Makes the function untestable and breaks the offline guarantee |
| Changing Kotlin without changing the reference | Fails the parity tests immediately (which is the point) |
| Adding anything that scales a price by global state | Breaks [the price-stability invariant](Economy-and-Production.md#the-one-invariant), which five tests defend |
| Bumping `SAVE_VERSION` for a field that only needs a default | Adds a migration nobody needs and a risk of double-application |
| Adding an animation without reading `LocalReducedAnimations` | Silently ignores an accessibility setting |
| Updating a count in code but not in the README | Nothing catches a stale sentence |

## Pull requests

- One concern per PR. A refactor and a behaviour change in the same diff cannot be reviewed.
- Say **why**, not just what. The codebase's comments are written that way and so should the PR be.
- Include the test that would have failed before.
- If a fixture changed, say which and why.
- If a wiki page is now stale, update it in the same PR. See
  [Documentation maintenance](Home.md#documentation-maintenance).

## Reporting a bug

[Open an issue](https://github.com/themantas1994/Earth-idle-game/issues) with:

- device model and Android version;
- what you did, what happened, what you expected;
- a screenshot if it is visual;
- for a balance or progression problem, roughly where you were (run number, civilization level,
  temperature).

**Save-corruption reports are the highest-value ones.** If the game told you it recovered from a
backup or started fresh, say so — that path is defended by design and should never fire in practice.

---

**Next:** [Architecture](Architecture.md) · [Testing](Testing.md) · [Reference parity](Reference-Parity.md)
