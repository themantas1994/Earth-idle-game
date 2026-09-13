# Licensing

[← Documentation home](Home.md)

Three separate questions that are easy to conflate:

1. **What licence covers EARTH's own source code?** — currently none, and that is
   a release blocker.
2. **What licences cover the software EARTH ships?** — Apache-2.0, the Android SDK
   Licence, BSD-3-Clause and MIT, all audited.
3. **What licences cover the artwork?** — one icon, provenance unrecorded, also a
   blocker.

---

## 1. The project's own licence

**There is no `LICENSE` file in this repository.**

Under copyright law the default applies — all rights reserved. Nobody has
permission to copy, modify or redistribute the source, whatever the word
"open-source" elsewhere in the README suggests about the project's intent.

This is almost certainly an oversight rather than a decision, but **choosing a
licence is the repository owner's call and nobody else's.** It is not something
documentation can decide, and it is not something to guess from the README's
tone.

It blocks a public **source** release. It does not block a Play upload: publishing
a binary you own the copyright in is fine.

### What to do

| Option | Effect |
| :-- | :-- |
| **MIT** | Shortest permissive licence; anyone may do anything, with attribution |
| **Apache-2.0** | Permissive with an explicit patent grant; matches every dependency the app ships |
| **GPL-3.0** | Copyleft; derivative works must also be GPL |
| **Stay proprietary** | Then remove "open-source" from the README, the store listing and the About screen |

Applying one touches four places:

1. `LICENSE` at the repository root, with the chosen text and the copyright line.
2. The GitHub repository settings, so the sidebar shows it.
3. The README's Licence section, replacing the warning.
4. `project_license` in `app/src/main/res/values/strings.xml`, which is what the
   About screen shows.

`AboutScreenTest.aboutStatesTheProjectLicenceRatherThanAssumingOne` reads that
string, so the test follows the change rather than blocking it.

### Source headers

The codebase carries **no per-file licence headers**, and that is consistent:
adding them without a licence would be asserting terms that do not exist. If a
licence is adopted and headers are wanted, add them in one mechanical change
across `app/src/**` and `tools/ts-reference/**` — not gradually, which leaves an
ambiguous half-state.

---

## 2. Third-party software

Full audit: **[Third-party licences](../THIRD_PARTY_LICENSES.md)**.
Distributable notices: **[`THIRD_PARTY_NOTICES.txt`](../../THIRD_PARTY_NOTICES.txt)**.

| | |
| :-- | :-- |
| Modules shipped | 149 — 16 declared by the build, 133 transitive |
| Licences | Apache-2.0 (136), Android SDK Licence (11), BSD-3-Clause (1), MIT (1) |
| Copyleft in the shipped app | **None** |
| Source-disclosure obligations | **None** |
| Unverifiable licences | **None** |

Every licence was read from the module's own published metadata — the
`<licenses>` block of its POM, following `<parent>` where inherited, and the
`LICENSE` files embedded in the artifacts. Nothing was taken from a directory
site or assumed from a group ID.

### How the notices are produced

```bash
python3 scripts/third-party-notices.py
```

The script resolves `releaseRuntimeClasspath` with Gradle, reads each module's
POM and artifact from the Gradle cache, groups modules by licence, extracts the
Apache-2.0 and BSD-3-Clause texts from the artifacts that ship them, and lists the
components Google embeds inside its own binaries from Google's published index.

**It fails loudly** on any module whose POM declares a licence it does not
recognise, rather than filing it under a default. An unattributed dependency
should stop a release, so it stops the script.

**CI checks rather than regenerates.** `--check` compares the committed file
against the resolved classpath and fails on a module missing from it, or one
still listed after leaving the build. It reads no POMs and no artifacts, so it
cannot fail because a runner's Gradle cache holds less than a developer's —
which is exactly how the first version of this check failed, reporting all 149
dependencies as unlicensed when the truth was a cold cache.

Regeneration stays a maintainer step, run where a build has just populated the
cache:

```bash
./gradlew assembleRelease && python3 scripts/third-party-notices.py
```

### How they reach the player

`THIRD_PARTY_NOTICES.txt` lives at the repository root as the single copy.
`BundleNoticesTask` in `app/build.gradle.kts` copies it into the variant's assets
at build time, and `LicensesScreen` reads it back at **Settings → About → Open
Source Licenses**. Two committed copies would drift, and one of them would then
be wrong.

`AboutScreenTest.theBundledNoticesAreRealAndCoverTheShippedLicences` asserts the
asset exists, is not a stub, and mentions the licences and the SDKs it should.

### Google Play services and UMP

These are under the [Android Software Development Kit License
Agreement](https://developer.android.com/studio/terms), not an OSI licence. It
permits distributing the SDK inside an application and does not require its own
text to be reproduced.

Their binaries embed 84 further components whose full texts Google publishes
inside the AARs, totalling roughly 600 KB. The notices list all 84 by name, from
Google's own index, and point at the authoritative text rather than pasting it.
Shipping it verbatim instead is a supported option — Google's
[oss-licenses plugin](https://developers.google.com/android/guides/opensource) —
and is recorded as a decision for the publisher in
[Release blockers M2](../RELEASE_BLOCKERS.md#m2-googles-own-bundled-notices-are-referenced-rather-than-reproduced).

### Not claiming what is not ours

Android, Kotlin, Jetpack Compose, Google Mobile Ads and the User Messaging
Platform are other people's work, used under their licences. The README, the
About screen and the notices all keep the project's own copyright separate from
third-party software, and nothing in the repository claims otherwise.

---

## 3. Assets

| Asset | Files | Status |
| :-- | :-- | :-- |
| Launcher icon | `mipmap-*/ic_launcher*.png`, `mipmap-anydpi-v26/*.xml` | **Provenance unrecorded — release blocker** |
| Fonts | none | The platform's default type only |
| Sound, music | none | `SoundPoolAudio` is a stub with no files behind it |
| Images, illustrations | none | Every visual is drawn by Compose from colour tokens |
| Emoji | none bundled | Rendered by the device's own font |

The icon is the only non-code asset in the repository. It appears to have been
produced for this project during the Android port, but the repository records no
source, author or licence, and a licence cannot be read off a file's pixels.

**Fix:** the owner states in writing — in the README or a `docs/ASSETS.md` — that
it is original work of the project and under which licence; or records the stock
source and its terms; or it is replaced. Guessing is exactly what an asset audit
exists to prevent. See
[Release blockers O4](../RELEASE_BLOCKERS.md#o4-the-launcher-icons-provenance-is-unrecorded) and
[Assets](../ASSETS.md).

---

## The 3D globe added nothing

Worth stating explicitly, because a 3D feature is normally where a dependency
audit grows: the [Home screen's globe](Home-Screen.md) introduced **no new
dependency and no new asset**.

- It is drawn against `android.opengl`, which is the platform.
- Its Earth and cloud textures are [generated in code](../ASSETS.md#the-earth-surface-texture)
  from outlines authored in this repository, so there is nothing to attribute and
  nothing whose provenance has to be established.
- `THIRD_PARTY_NOTICES.txt` is unchanged, and `scripts/third-party-notices.py --check`
  still resolves the same 149 modules.

SceneView and Filament were both evaluated first and rejected — on artifact size,
on a `uses-feature` that would have become a Play install filter, on an HTTP
client arriving in an app that makes no network requests of its own, and on
Filament's material compiler not being published to Maven. The reasoning, with
the measured figures, is in
[Environmental visualization](Environmental-Visualization.md#why-opengl-es-directly-and-not-sceneview-or-filament).

---

## Adding a dependency

1. Add it to `gradle/libs.versions.toml` and `app/build.gradle.kts`.
2. `python3 scripts/third-party-notices.py`, and commit the regenerated file.
3. If the script refuses it, find the real licence from the project's own source
   and teach `classify()` about it. Do not let it through under a default.
4. If the licence is copyleft, stop and think — nothing in the shipped app is
   today, and that is a property worth keeping deliberately rather than by luck.
5. Update [Third-party licences](../THIRD_PARTY_LICENSES.md) if the summary
   counts change.
6. Re-check [Data safety](../GOOGLE_PLAY_DATA_SAFETY.md) — a new dependency can
   merge a new permission or collect new data.

---

**Next:** [Third-party licences](../THIRD_PARTY_LICENSES.md) · [Release blockers](../RELEASE_BLOCKERS.md) · [Google Play](Google-Play.md)
