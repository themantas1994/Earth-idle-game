# GitHub release template

[← Documentation home](wiki/Home.md)

The release page for EARTH, distributed as a **signed APK on GitHub Releases**.
Copy everything under [The release body](#the-release-body) into the GitHub
release description, fill in the two bracketed values, and attach the files
listed in [What to attach](#what-to-attach).

> [!WARNING]
> **Do not publish a release from an unsigned build.** `packageReleaseApk` names
> an unsigned artifact `EARTH-<version>-release-unsigned.apk`, and Android will
> not install one. Attach `EARTH-<version>-release.apk` — the signed one, whose
> signature you have verified with `apksigner` — or attach nothing. See
> [Release signing §6](RELEASE-SIGNING.md#6-verify-the-signature).

---

## Before publishing

| | |
| :-- | :-- |
| Open technical blockers | None — see [Release blockers](RELEASE_BLOCKERS.md#technical-blockers) |
| Owner actions | All closed — see [Owner and distribution requirements](RELEASE_BLOCKERS.md#owner--distribution-requirements) |
| Build | `./gradlew clean test lint packageReleaseApk` all green |
| Signature | `apksigner verify` reports `Verifies`, and the certificate digest matches every previous release |
| Checksums | `cd release && sha256sum -c SHA256SUMS.txt` passes |

---

## Release metadata

Read from `app/build.gradle.kts`, which is the single source of both numbers:

| | |
| :-- | :-- |
| Tag | `v1.0.0` |
| Release title | **EARTH 1.0.0** |
| `versionName` | 1.0.0 |
| `versionCode` | 1 |
| `applicationId` | `com.earthgame.idle` |
| Target | Android 7.0 (API 24) and newer |

Do not invent a later version here: the next one is decided when there is a next
one, by the rules in [Release process](wiki/Release-Process.md#the-rules).

---

## What to attach

| File | From | Attach? |
| :-- | :-- | :-- |
| `EARTH-<version>-release.apk` | `release/` | **Yes** — this is what a player installs |
| `SHA256SUMS.txt` | `release/` | **Yes** — so a download can be checked |
| `EARTH-<version>-release-mapping.txt` | `release/` | **No.** Keep it privately: without it a crash report from this build is unreadable, and publishing it hands out your symbol map |
| `EARTH-<version>-release.aab` | `release/` | **No.** A player cannot install an AAB. Only relevant if Play is ever added, and `packageReleaseApk` does not build one |

```bash
./gradlew clean packageReleaseApk
cd release && sha256sum -c SHA256SUMS.txt
```

---

## The release body

Everything below this line is the release description. Replace
`<SHA-256 OF THE APK>` and the signing-certificate digest with the real values
from your build.

---

## EARTH 1.0.0

Build the planet from the first stone tool to the edge of the solar system. EARTH
is an offline idle game about a civilisation's whole industrial arc — and about
the atmosphere it changes on the way.

### Highlights

- **13 technology eras**, from primitive tools through electricity, chemistry and
  globalisation to endgame megaprojects.
- **A real climate model.** Production emits CO₂, methane and other gases;
  concentrations drive radiative forcing, temperature and habitability, and
  habitability feeds straight back into output. Growth has a cost that shows up
  in the numbers.
- **Offline progression.** The simulation catches up on what happened while you
  were away, with the same maths it uses while you watch.
- **Prestige and challenges.** Reset for permanent multipliers, or take on
  constrained runs with their own rewards.
- **Achievements, milestones and random events** that react to how your world is
  actually developing.
- **Numbers that keep going.** A custom arbitrary-magnitude number type, so late
  game values stay exact rather than saturating at a ceiling.
- **Plays entirely on the device.** No account, no login, no cloud save, no
  network calls by the game itself.

### Installation

1. Download **`EARTH-1.0.0-release.apk`** from the Assets below, on the Android
   device you want to play on.
2. Open the downloaded file — from the notification, or from your browser's
   downloads list, or with a file manager.
3. Android will ask whether to allow installs from the app you downloaded with
   (your browser or file manager). This prompt is normal for any app that does
   not come from a store.
4. Choose **Settings** on that prompt and allow installs for *that one app*, then
   go back and continue. Grant it only to the app you are installing from, and
   turn it off again afterwards if you prefer.
5. Tap **Install**.
6. Open **EARTH** and start playing.

Android may also show a scan-before-install prompt from Play Protect. Letting it
scan is fine and is worth doing.

**Requires Android 7.0 (API 24) or newer.** No permissions are requested at
install time beyond network access for the ad banner and vibration for haptics.

### Verify your download

```
SHA-256 (EARTH-1.0.0-release.apk) = <SHA-256 OF THE APK>
```

Check it before installing:

```bash
sha256sum -c SHA256SUMS.txt          # Linux
shasum -a 256 EARTH-1.0.0-release.apk # macOS
```

```powershell
Get-FileHash EARTH-1.0.0-release.apk -Algorithm SHA256   # Windows
```

If the digest does not match, do not install the file — re-download it.

The APK is signed with the project's own release key. Its certificate SHA-256
digest is:

```
<SIGNING CERTIFICATE SHA-256 DIGEST>
```

Every future EARTH release will be signed with the same key, so this digest
should never change. Verify it yourself with
`apksigner verify --print-certs EARTH-1.0.0-release.apk`.

### Updating later

Install a future release straight over this one — the save is kept, because both
are signed with the same key. Do **not** uninstall first unless a release note
says to: the save lives only on the device, and uninstalling deletes it.

### Privacy

EARTH keeps your game entirely on your device. There is no account, no cloud
save, and the game itself makes no network requests.

The app shows **one banner ad** (Google AdMob), which is the only reason it needs
network access. Before any ad is requested, Google's **User Messaging Platform**
asks for consent where the law requires it, and no ad is requested at all unless
the recorded consent state permits it. You can change or withdraw that choice at
any time from **Settings → Privacy options**, and the game is fully playable with
no ad ever shown.

Full details, including exactly what Google receives: **[Privacy
policy](https://github.com/themantas1994/Earth-idle-game/blob/main/docs/PRIVACY_POLICY.md)**.

### Open source

The complete source for this build is in this repository, at tag `v1.0.0`, and
the APK can be rebuilt from it — see
[README](https://github.com/themantas1994/Earth-idle-game/blob/main/README.md#building).

EARTH ships 149 third-party modules; every licence and notice is reproduced in
the app at **Settings → About → Open Source Licenses**, and in
[`THIRD_PARTY_NOTICES.txt`](https://github.com/themantas1994/Earth-idle-game/blob/main/THIRD_PARTY_NOTICES.txt).

The project's own licence is in
[`LICENSE`](https://github.com/themantas1994/Earth-idle-game/blob/main/LICENSE).

### Known limitations

- **Not on Google Play.** This APK is distributed here only. Installing it means
  going through the "install from this source" prompt described above.
- **Sound and music settings control nothing yet.** The audio system is wired but
  no sound files are bundled, so the game is silent whichever way the toggles are
  set.
- **Saves are local and are not backed up anywhere.** Uninstalling the app
  deletes your progress.
- **One save slot.** There is no export or import.
- **Phone-shaped layouts are what has had the most attention.** The UI adapts to
  tablets and landscape, but large screens have seen less polish.
- **Ads need a network.** With no connection the banner simply does not appear;
  nothing else changes.

### Testing

This build was verified by building and inspecting the artifact: unit and
Robolectric suites green, `lint` clean on both variants, and the release APK
checked for its package name, version, signature, production ad configuration
and permissions.

*[Replace this paragraph with the real device-testing status before publishing.
If the release APK has been installed and played on a physical device, say which
Android version and what was exercised. If it has not, say so — see [Final device
QA](https://github.com/themantas1994/Earth-idle-game/blob/main/docs/FINAL_DEVICE_QA.md).]*

### Reporting a problem

Open an issue: https://github.com/themantas1994/Earth-idle-game/issues — include
your Android version, device model, and the app version from **Settings →
About**.

---

**Next:** [Release signing](RELEASE-SIGNING.md) · [Release process](wiki/Release-Process.md) · [Release blockers](RELEASE_BLOCKERS.md) · [Final device QA](FINAL_DEVICE_QA.md)
