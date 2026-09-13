<div align="center">

# 🌍 EARTH

**An open-source idle game for Android about building a civilization — and cooking the planet it stands on.**

[![Android CI](https://github.com/themantas1994/Earth-idle-game/actions/workflows/android.yml/badge.svg)](https://github.com/themantas1994/Earth-idle-game/actions/workflows/android.yml)
[![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)](#getting-started)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Tests](https://img.shields.io/badge/tests-220%20passing-brightgreen)](#for-developers)

*Incremental · Civilization simulator · Climate strategy · Fully offline*

</div>

---

## What is EARTH?

EARTH is an **idle / incremental game** for Android. You start with a single lightning-struck
tree, still smouldering, and you finish with a Dyson swarm and an atmosphere no longer capable
of holding people.

There is no tap button. Your civilization produces on its own, whether the app is open or not.
What you do is decide *what to build next* — and every choice heats the planet a little more.
When habitability finally reaches zero, the run ends, you bank **Earth Points**, and the next
civilization starts from the ashes a little faster than the last one did.

It is a **civilization simulator** and a **climate strategy game** wearing the clothes of a
number-goes-up game, in the tradition of *Antimatter Dimensions* and *AdVenture Capitalist* —
except the number that goes up is the one you should probably be worrying about.

> [!NOTE]
> **This is a gameplay simulation, not a scientific climate model.** The formulas are
> simplified but internally consistent, and built on real, plausible *relationships* —
> logarithmic CO₂ forcing, per-gas atmospheric lifetimes, natural sinks that weaken as the
> world warms. They are tuned so the game behaves coherently from one campfire to 10⁴⁰⁰
> kilograms of methane. They are not a prediction of anything.

**Free. Open source. No accounts, no tracking, and it works with the plane in flight mode.**

---

## How to play

The loop takes about thirty seconds to learn and several days to finish.

| | |
| :-- | :-- |
| **1. Burn something** | Your Natural Fire produces Energy on its own. It never stops. |
| **2. Research** | Spend Energy and Research on the **Technology** tab to unlock what comes next. |
| **3. Build** | Buy generators on the **Production** tab. More copies, more output. |
| **4. Watch the sky** | The **Atmosphere** tab fills in with what you have done to it. |
| **5. Collapse** | Habitability hits zero. The Earth is finished — and so is the run. |
| **6. Prestige** | Bank Earth Points, spend them on permanent upgrades, start EARTH 2. |
| **7. Go faster** | Repeat. Every civilization is quicker and dirtier than the last. |

Two rules make it different from most idle games:

🔒 **Prices never rise.** The price you were quoted is the price you pay, however large your
civilization grows. The *only* thing that makes something more expensive is you, buying more
of that exact thing. No hidden complexity tax, no re-pricing of what you were already saving
toward — the countdown on a button is a promise, not an estimate.

⚡ **Every tenth copy doubles its output.** Each generator keeps its own progress track, so
going deep on one building pays off visibly — and it is spaced so branching out into new
technology still wins in the long run. Depth is a satisfying detour, not a replacement for
the tech tree.

---

## Offline progression

**The game runs while it is closed.** Not "sort of" — it is the point of the design.

When you leave, EARTH records the time. When you come back, it computes exactly what your
civilization produced while you were gone and hands it to you in one lump, with a summary of
what changed. Up to **12 hours** is banked by default, and prestige upgrades push that as far
as **8 days**.

This is done with mathematics, not a background service. The gas-concentration model has an
exact closed-form solution, so eight hours away is a *single calculation* that lands on the
same numbers as if you had sat and watched all 115,200 ticks. That means:

- **no background service**, no scheduled work, no wake locks;
- **no battery drain** while the game is closed;
- coming back to a stockpile big enough to buy a dozen things at once, which is the entire
  point of checking in on an idle game.

There is no punishment for closing the app and no advantage to leaving it open.

---

## Features

| | |
| :-- | :-- |
| 🔬 **99 technologies** | Across 11 branches, from Controlled Fire to a Matrioshka Brain. 68 are repeatable generators, 31 are research nodes — including a mutually exclusive Coal-vs-Nuclear decision you only get to make once per Earth. |
| ☁️ **6 greenhouse gases** | CO₂, CH₄, N₂O, H₂O, O₃ and an aggregate fluorinated bucket. Each has its own atmospheric **half-life**, forcing curve and display unit, and they interact — water vapour is modelled as a feedback that the *other* gases drive. Gas decays away on the simulated calendar, so emissions have to outrun it rather than simply pile up. |
| 🕰️ **A planet with an age** | Each Earth tracks its own simulated age — one real second is one simulated day, so an evening is a few decades and a full run is centuries. The header shows it, and it is the clock the atmosphere's half-lives decay against: leave an Earth untended and its greenhouse gases drain away. |
| 🌡️ **A 5-factor habitability model** | Temperature, ocean acidity, sea level, agriculture and biodiversity, **multiplied** together — so any single one of them collapsing ends the run, even while the others look fine. |
| ✨ **Prestige** | Earth Points and 11 permanent upgrades, scored on how *fast* the run was rather than only how it ended. Faster runs pay dramatically better. |
| 🎯 **8 challenges** | Runs with a live restriction — no coal, never above +2 °C, never past the Iron Age — for a permanent reward. |
| 🏆 **32 achievements** | From "First Spark" to "Apocalyptic Heat". |
| 🎲 **13 random events** | Short multiplier swings, deliberately skewed heavily positive: a negative event in an idle game is a tax on being away, and only two of the thirteen are one. |
| 📰 **39 world-news headlines** | Deterministic, once per run, with no mechanical effect whatsoever. They exist so a multi-day run reads as a story of consequences instead of a rising number. |
| ♾️ **Numbers without a ceiling** | A finished run passes 10⁴⁰⁰, which an ordinary double cannot even hold. Four notations to read them in: compact, scientific, engineering and full. |
| 📱 **Built for a phone** | Portrait, one-handed, nine tabs, dark and light themes, and a tablet layout that uses the extra width as margin rather than stretching stat rows across it. |

---

## Getting started

EARTH runs on **Android 7.0 (API 24) and newer**, which is somewhere north of 98% of active
devices.

### Where to get it

| | |
| :-- | :-- |
| **GitHub Releases** | The intended channel — a signed APK you download and install. [The releases page](https://github.com/themantas1994/Earth-idle-game/releases) is still empty |
| **Google Play** | Not published, and not planned for the first release |
| **Build it yourself** | One command, below |

> [!IMPORTANT]
> **There is no published release yet.** The app builds, tests and packages a production APK, but
> two things must come from the repository owner before one can be published: a **licence file**,
> and a **signing key** (without one the release build produces *unsigned* artifacts, which Android
> will not install).
> [docs/RELEASE_BLOCKERS.md](docs/RELEASE_BLOCKERS.md) is the honest list of what is outstanding —
> no technical blockers, four owner actions. Watch the repository to hear about the first release.
> Until then, building it yourself is the way to play it, and it takes one command.

```bash
git clone https://github.com/themantas1994/Earth-idle-game.git
cd Earth-idle-game
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

You need **JDK 21** and an **Android SDK** with `platforms;android-37.0` and
`build-tools;37.0.0` — point `ANDROID_HOME` at it, or put `sdk.dir=/path/to/sdk` in a
`local.properties` file at the repository root. Android Studio has all of this already; open
the repository root and press Run.

Full build instructions live in **[docs/wiki/Build-System.md](docs/wiki/Build-System.md)**;
signing and publishing in **[docs/RELEASE-SIGNING.md](docs/RELEASE-SIGNING.md)** and
**[docs/wiki/Release-Process.md](docs/wiki/Release-Process.md)**.

---

## Privacy

EARTH is **local-first by design**, and the code is there to check.

- **Your save never leaves your device.** It is a file in the app's private storage. There is
  no account, no cloud save, no server, and no way for anyone else to read it. Android's own
  Auto Backup can include it if you have that switched on, which is between you and Google.
- **No analytics. No telemetry. No crash reporting. No tracking of any kind.**
- **The simulation makes zero network requests.** Every number in the game is computed on your
  phone.
- **One ad banner**, at the bottom of the screen. That is the only reason the app asks for the
  internet permission, and the only thing in the app that talks to a network. There are no
  interstitials and no rewarded ads, nothing in the game is gated behind watching one, and if
  it never loads the game plays exactly the same. In the EEA and UK you get Google's consent
  form first, and you can reopen it any time from Settings.

**The full privacy policy is [docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md).** The
implementation behind it — the consent flow, what each permission is for, what is logged — is in
**[docs/wiki/Privacy.md](docs/wiki/Privacy.md)** and
**[docs/wiki/Security-and-Privacy.md](docs/wiki/Security-and-Privacy.md)**.

### Advertising

EARTH carries **one banner advertisement**, at the bottom of the screen, supplied by Google
AdMob. It is the only network traffic the app produces.

- There are **no interstitials and no rewarded ads**, and nothing in the game is locked behind
  one. If it never loads, the game plays exactly the same.
- In the **EEA, the UK and Switzerland** you get Google's consent form before anything is
  requested. **Decline and no advertisement is requested at all** — not a non-personalised one,
  none.
- You can change or withdraw that choice any time from **Settings → Manage advertising
  privacy**, or **Settings → About → Manage advertising privacy**.

How it is built, and why it can never touch the live ad account from a developer build, is in
**[docs/wiki/Advertising.md](docs/wiki/Advertising.md)**.

---

## FAQ

<details>
<summary><b>Is the game free?</b></summary><br>

Yes. Free to play, free to build, free to fork, with no in-app purchases of any kind. There is
a single banner ad and nothing in the game is locked behind it.
</details>

<details>
<summary><b>Does it work offline?</b></summary><br>

Completely. The entire simulation runs on your device with no network access at all. You can
play the whole game in flight mode; the only thing that needs a connection is the ad banner,
and the game does not care whether it loads.
</details>

<details>
<summary><b>Is there a tap-to-earn mechanic?</b></summary><br>

No, and deliberately not. Energy comes from things that produce energy — a fire, a mill, a
reactor — not from your thumb. The original build had tapping and it was removed: it made the
first ten minutes about wrist stamina and was worthless for the rest of the run. What you do
in EARTH is *choose*, not grind.
</details>

<details>
<summary><b>What happens when Earth becomes uninhabitable?</b></summary><br>

The run ends. You get a summary of what your civilization managed — peak temperature, peak
CO₂, how long it lasted, what it produced — and you bank Earth Points for it. Then you press
Reset and start EARTH 2 with your permanent upgrades intact. Nothing is lost; that *is* the
progression.
</details>

<details>
<summary><b>What is prestige?</b></summary><br>

The reset loop. When a planet dies you convert the run into **Earth Points**, a currency that
survives the reset, and spend them on permanent upgrades — more production, cheaper
technology, a longer offline cap, generators and resources you start each new Earth already
holding.

The payout is scored on total greenhouse gas produced, how hard you pushed the atmosphere, how
far up the tech tree you got, and — most importantly — **how fast you did it**. Every run ends
in the same place, so scoring only the ending would pay a stronger civilization *less*, since
a stronger one kills the planet sooner. Speed is what prestige upgrades actually buy, so speed
is what gets scored.
</details>

<details>
<summary><b>How does offline progression work?</b></summary><br>

Close the app, come back later, and everything your civilization produced while you were away
is waiting, with a summary of what changed. Up to 12 hours by default, up to 8 days with
upgrades.

It costs no battery, because nothing runs in the background: the game notes the time you left
and computes the answer in one step when you return. See
[Offline progression](#offline-progression) above.
</details>

<details>
<summary><b>Is this a scientific climate simulator?</b></summary><br>

**No.** It is a game. The relationships are real ones — CO₂ forcing really is logarithmic,
gases really do have wildly different atmospheric lifetimes, warming really does weaken
natural carbon sinks — but the numbers are tuned for a satisfying multi-day run, not for
accuracy. CO₂'s kilograms-per-ppm is scaled down by a factor of about four; sea level rises at
a game-time rate that would be absurd in reality.

If it leaves you curious about the real thing, that is the best outcome it can hope for. Do
not cite it.
</details>

<details>
<summary><b>How large can the numbers get?</b></summary><br>

Larger than a computer's normal floating-point numbers can hold. A double gives up at about
1.8 × 10³⁰⁸; a finished EARTH run sails past 10⁴⁰⁰ and prestige multiplies from there.

The game stores every value as `sign × mantissa × 10^exponent` with the exponent *itself* a
full-size number, which puts the ceiling somewhere around 10^(1.8 × 10³⁰⁸) — a number with
more digits than the universe has atoms. You will not reach it. Pick a notation you like in
Settings; there are four.
</details>

<details>
<summary><b>Can I lose my save?</b></summary><br>

The game keeps two copies. Every save rotates the previous one into a backup slot in the same
atomic write, so a phone killed mid-save still has a good one. If the main save is ever
unreadable the game loads the backup and tells you it did. If the file itself is damaged
beyond that, the game starts fresh and says so rather than crashing on launch.
</details>

<details>
<summary><b>Why does my planet die faster every run?</b></summary><br>

Because you are better at killing it. Prestige upgrades multiply production, and production is
what heats the atmosphere. That is intended — the whole progression is getting quicker at the
same apocalypse, and the prestige payout rewards exactly that.
</details>

---

## For developers

Native **Kotlin** and **Jetpack Compose**. No WebView, no JavaScript, no cross-platform
runtime.

```
domain/        Pure Kotlin. No Android, no Compose. The whole simulation.
data/          Versioned save, DataStore-backed, with a backup slot.
platform/      Haptics, audio, ads — each behind an interface.
presentation/  ViewModel, StateFlow, Compose UI.
```

Three things are worth knowing:

**The engine is a pure function of state.** `simulateStep(state, dt)` returns the next state
and nothing else — no clocks, no I/O, no Android anywhere in `domain/`. That is what lets the
entire simulation be tested in seconds on the JVM, and what makes the offline path and the
live path provably identical.

**Gas concentrations are integrated in closed form.** `dE/dt = production − k·E` has an exact
solution, so one call with `dt = 8 hours` produces the same numbers as 115,200 calls with
`dt = 250 ms`. Offline progress is one calculation, not a replayed loop.

**Numbers are arbitrary-scale.** `GameDecimal` stores `sign × mantissa × 10^exponent` with the
exponent itself a `Double`, and the save format persists the exact triple rather than a
rounded number.

```bash
./gradlew test                    # 220 JVM tests, seconds
./gradlew lintDebug lintRelease   # Android lint, both variants
./gradlew assembleDebug           # debug APK
./gradlew packageReleaseApk       # signed release APK + checksums, into release/
```

The suite is mostly **parity tests**: the original TypeScript engine's answers were captured
as golden fixtures, and the Kotlin port asserts against them — every field of all 99
technologies, 576 arbitrary-precision operand pairs, 3,240 gas integrations, every formatting
mode, the whole save-migration chain, and a 95-step scripted playthrough compared value by
value. The frozen reference lives in [`tools/ts-reference/`](tools/ts-reference) and nothing
in the app build depends on it.

**📖 [Full technical documentation →](docs/wiki/Home.md)**

| | |
| :-- | :-- |
| [Developer overview](docs/DEVELOPER_OVERVIEW.md) | Start here if you are new to the codebase |
| [Architecture](docs/wiki/Architecture.md) | Layers, dependency rules, what is forbidden where |
| [Game engine](docs/wiki/Game-Engine.md) | GameState, simulateStep, the game loop |
| [GameDecimal](docs/wiki/GameDecimal.md) | The arbitrary-scale number type |
| [Climate model](docs/wiki/Climate-Model.md) | Every formula, with its rationale |
| [Save system](docs/wiki/Save-System.md) | Format, slots, corruption recovery |
| [Testing](docs/wiki/Testing.md) | How to run everything, how to add parity cases |
| [Contributing](docs/wiki/Contributing.md) | Conventions, and the things people get wrong |
| [Codebase audit](docs/CODEBASE_AUDIT.md) | Known issues and remaining technical debt |
| [Release blockers](docs/RELEASE_BLOCKERS.md) | What stands between this and a public release |
| [Release process](docs/wiki/Release-Process.md) | Versioning, signing, artifacts, the checklist |
| [Final device QA](docs/FINAL_DEVICE_QA.md) | The manual pass on real hardware, before anything ships |
| [Privacy policy](docs/PRIVACY_POLICY.md) | What the app stores and what leaves the device |
| [Third-party licences](docs/THIRD_PARTY_LICENSES.md) | Every dependency, audited |

---

## Contributing

Contributions are welcome — bug reports, balance feedback, accessibility fixes, and code.
**[docs/wiki/Contributing.md](docs/wiki/Contributing.md)** covers the architecture rules, the
testing requirements, and the specific things a first change tends to get wrong (adding a
technology, changing a formula, or touching the save format each have a procedure).

The one hard rule: **the reference implementation in `tools/ts-reference/` is the parity
oracle.** If you change gameplay maths, change it in both and regenerate the fixtures.

---

## License

> [!WARNING]
> **This repository does not currently carry a licence file.** Under copyright law that means
> the default applies — all rights reserved — and nobody has permission to copy, modify or
> redistribute the source, whatever the word "open-source" elsewhere in this README suggests
> about the project's intent.
>
> This is very likely an oversight rather than a decision, but choosing a licence is the
> repository owner's call and not one this documentation can make for them.
> **[docs/wiki/Licensing.md](docs/wiki/Licensing.md)** lays out the options and the four places
> a choice has to be applied; [docs/GITHUB-METADATA.md](docs/GITHUB-METADATA.md) has the GitHub
> settings steps.

### Third-party software

EARTH ships **149 third-party modules** — AndroidX and Jetpack Compose, Kotlin and kotlinx,
Google Play services, the Mobile Ads SDK and the User Messaging Platform. Every licence was read
from each module's own published metadata:

| | |
| :-- | :-- |
| Apache-2.0 | 136 modules |
| Android SDK Licence | 11 modules (Google Play services, UMP) |
| BSD-3-Clause | 1 (a repackaged subset of Protocol Buffers) |
| MIT | 1 (Checker Framework qualifiers) |
| Copyleft | **none** |

Android, Kotlin, Jetpack Compose, Google Mobile Ads and the User Messaging Platform are other
people's work, used under their licences — this project claims none of them.

The audit is **[docs/THIRD_PARTY_LICENSES.md](docs/THIRD_PARTY_LICENSES.md)**, the distributable
notices are **[THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt)**, and both are also readable
inside the app at **Settings → About → Open Source Licenses**.

---

<div align="center">

**EARTH** · an open-source Android idle game about technology, greenhouse gases and
starting over

[Documentation](docs/wiki/Home.md) · [Player FAQ](docs/wiki/FAQ.md) · [Contributing](docs/wiki/Contributing.md) · [Report a bug](https://github.com/themantas1994/Earth-idle-game/issues)

</div>
