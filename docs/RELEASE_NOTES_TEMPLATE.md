# Release notes template

[← Documentation home](wiki/Home.md)

A draft for the **GitHub release** and for the **Play "What's new"** field, filled
in against the version this repository currently builds. It is a draft, not a
publication: nothing here should go out until
[Release blockers](RELEASE_BLOCKERS.md) has no open CRITICAL item.

> [!WARNING]
> **Do not publish a release from an unsigned build.** `packageReleaseArtifacts`
> names an unsigned artifact `EARTH-<version>-release-unsigned.apk`, and an
> unsigned APK cannot be installed by anyone who downloads it. Attach
> `EARTH-<version>-release.apk` — the signed one — or attach nothing.

---

## Current version

Read from `app/build.gradle.kts`, which is the single source:

| | |
| :-- | :-- |
| `versionName` | **1.0.0** |
| `versionCode` | **1** |
| Tag | `v1.0.0` |
| Title | **EARTH 1.0.0** |
| applicationId | `com.earthgame.idle` |

No release has ever been published, so 1.0.0 is genuinely the first one. Do not
invent a later version here: the next one is decided when there is a next one, by
the rules in [Release process](wiki/Release-Process.md#the-rules).

---

## What to attach

| File | From | Why |
| :-- | :-- | :-- |
| `EARTH-1.0.0-release.apk` | `release/` | So a player can sideload it |
| `SHA256SUMS` | `release/` | So they can check what they downloaded |
| `EARTH-1.0.0-release.aab` | `release/` | Optional. Play uploads only — a player cannot install an AAB, so attach it only if you want the exact upload archived alongside the tag |
| `EARTH-1.0.0-release-mapping.txt` | `release/` | **Keep it, do not attach it.** Without it a crash report from this build is unreadable; publishing it is not required and hands out your symbol map |

```bash
./gradlew packageReleaseArtifacts
sha256sum -c release/SHA256SUMS
```

---

## Draft — GitHub release body

> Copy from here down. Replace the two bracketed values; delete any line that is
> not true of the build you are actually shipping. Links in the draft are
> absolute on purpose — a relative path does not resolve from a release page.

---

## EARTH 1.0.0

An idle game about building a civilization — and cooking the planet it stands on.
Start with a campfire, end with a planetary-scale industrial economy, watch the
atmosphere respond, and then decide whether to start over.

**The first public release.**

### What it is

- Ten technology branches, from Primitive to Endgame, each feeding the next
- A greenhouse-gas and habitability model that reacts to what you build
- Prestige: end an Earth, keep what you learned, start a faster one
- Achievements, challenges and random events
- Offline progression — it keeps running while you do not
- **Plays entirely offline.** No account, no login, no cloud save, no analytics,
  no telemetry, no crash reporting. Your save never leaves your device.

### Requirements

- **Android 7.0 (API 24)** or newer
- About 4 MB installed

### Advertising and privacy

EARTH shows **one banner advertisement**, supplied by Google AdMob. There are no
interstitials, no rewarded ads, and nothing in the game is locked behind one. If
the ad never loads, the game plays exactly the same.

In the EEA, the UK and Switzerland you are asked for consent before any
advertisement is requested, through Google's User Messaging Platform, and you can
change or withdraw that choice at any time from **Settings → Manage advertising
privacy**.

Full detail: [Privacy policy](https://github.com/themantas1994/Earth-idle-game/blob/main/docs/PRIVACY_POLICY.md).

### Installing the APK

```
sha256sum -c SHA256SUMS
```

Then allow installation from your browser or file manager when Android asks.

> **This APK and the Play Store build are signed with different keys and cannot be
> installed over one another.** If you later switch to the Play version you must
> uninstall this one first, **which deletes your save.** Pick one and stay on it.

### Known limitations

- **Sound and music settings toggle nothing audible.** The adapter is wired but no
  sound files ship in this release; the game is silent either way.
- The launcher icon's foreground layer is authored at legacy dimensions, so
  Android upscales it slightly on modern devices. Cosmetic.
- Third-party notices for components Google bundles inside its own SDKs are listed
  by name and linked rather than reproduced in full — see
  **Settings → About → Open Source Licenses**.

### Licensing

Built on 149 third-party modules — AndroidX and Jetpack Compose, Kotlin and
kotlinx, Google Play services, the Mobile Ads SDK and the User Messaging Platform.
Every notice ships inside the app at **Settings → About → Open Source Licenses**
and in [`THIRD_PARTY_NOTICES.txt`](https://github.com/themantas1994/Earth-idle-game/blob/main/THIRD_PARTY_NOTICES.txt).

*[EARTH's own licence — fill in from the `LICENSE` file once one exists. Until then
default copyright applies and this section must not claim otherwise. See
[C1](https://github.com/themantas1994/Earth-idle-game/blob/main/docs/RELEASE_BLOCKERS.md#c1-the-project-has-no-licence).]*

### Checksums

```
[paste release/SHA256SUMS here]
```

---

## Draft — Play Console "What's new" (≤ 500 characters)

> Play truncates hard. This fits.

```
The first release of EARTH.

Build a civilization from a campfire to a planetary industrial economy, watch the
atmosphere respond to what you build, then prestige and do it faster.

Plays fully offline — no account, no cloud save, no analytics. Your save stays on
your device. One banner ad, and the game is identical without it.
```

---

## Before you publish

- [ ] [Release blockers](RELEASE_BLOCKERS.md) has **no open CRITICAL item**
- [ ] [Final device QA](FINAL_DEVICE_QA.md) walked on real hardware, on this exact build
- [ ] The attached APK is **signed** — its filename has no `-unsigned`
- [ ] `apksigner verify --print-certs` shows the certificate you expect
- [ ] The checksums pasted above are the ones from the build being attached
- [ ] The licence section states the real licence, or the release is held until one exists
- [ ] "Known limitations" matches what is actually still true
- [ ] `mapping.txt` archived somewhere you will still have it when a crash report arrives
- [ ] Play: staged rollout rather than 100% on day one

---

**Next:** [Release process](wiki/Release-Process.md) · [Google Play checklist](GOOGLE_PLAY_RELEASE_CHECKLIST.md) · [Final device QA](FINAL_DEVICE_QA.md)
