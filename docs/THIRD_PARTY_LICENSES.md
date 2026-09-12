# Third-party licences

[← Documentation home](wiki/Home.md)

Every piece of third-party software EARTH ships, builds with, or tests with, and
what each one's licence actually requires.

> **How this was determined.** Every licence below was read from the module's
> own published metadata — the `<licenses>` block of its POM (following
> `<parent>` where the POM inherits it) and the `LICENSE` files embedded in the
> published artifact — not from a directory, a summary site, or an assumption.
> The dependency set is whatever `./gradlew :app:dependencies` resolves, so it
> includes transitive dependencies rather than only the ones the build file
> names. Reproduce all of it with
> [`scripts/third-party-notices.py`](../scripts/third-party-notices.py).

---

## The short version

| | |
| :-- | :-- |
| Modules shipped inside the app | **149** — 16 declared by the build, 133 transitive |
| Distinct licences | Apache-2.0 (136 modules), Android SDK Licence (11), BSD-3-Clause (1), MIT (1) |
| Copyleft | **None.** No GPL, LGPL, AGPL, MPL or EPL anywhere in the shipped app |
| Attribution required in the distribution | **Yes** — satisfied by [`THIRD_PARTY_NOTICES.txt`](../THIRD_PARTY_NOTICES.txt), which ships inside the APK and is readable from Settings → About → Open Source Licenses |
| Source-disclosure obligation | **None** for any shipped licence |
| Unresolved / unverifiable licence | **None** among shipped modules |

---

## What ships inside the app

### Declared dependencies

The sixteen modules the build asks for by name. Versions are the resolved ones,
which for the Compose artifacts come from the BOM rather than from a version in
the catalogue.

| Dependency | Version | Purpose | Licence | Attribution required | Project |
| :-- | :-- | :-- | :-- | :-- | :-- |
| `androidx.core:core-ktx` | 1.19.0 | Kotlin extensions over the platform APIs | Apache-2.0 | Yes | [AndroidX](https://developer.android.com/jetpack/androidx) |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.11.0 | Lifecycle-aware coroutine scopes | Apache-2.0 | Yes | [AndroidX](https://developer.android.com/jetpack/androidx) |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | `collectAsStateWithLifecycle` | Apache-2.0 | Yes | [AndroidX](https://developer.android.com/jetpack/androidx) |
| `androidx.lifecycle:lifecycle-process` | 2.11.0 | Process lifecycle — the save-on-background trigger | Apache-2.0 | Yes | [AndroidX](https://developer.android.com/jetpack/androidx) |
| `androidx.activity:activity-compose` | 1.13.0 | `ComponentActivity` + `setContent`, Back handling | Apache-2.0 | Yes | [AndroidX](https://developer.android.com/jetpack/androidx) |
| `androidx.compose:compose-bom` | 2026.08.00 | Version alignment for every Compose artifact | Apache-2.0 | n/a (metadata only, ships no code) | [AndroidX](https://developer.android.com/jetpack/compose/bom) |
| `androidx.compose.ui:ui`, `:ui-graphics`, `:ui-tooling-preview` | 1.12.0 | The Compose UI toolkit and the `@Preview` annotation | Apache-2.0 | Yes | [AndroidX](https://developer.android.com/jetpack/compose) |
| `androidx.compose.material3:material3`, `:material3-window-size-class` | 1.4.0 | Material 3 components and the adaptive layout breakpoints | Apache-2.0 | Yes | [AndroidX](https://developer.android.com/jetpack/androidx) |
| `androidx.datastore:datastore-preferences` | 1.2.1 | The save file | Apache-2.0 (bundles BSD-3-Clause protobuf, below) | Yes | [AndroidX](https://developer.android.com/topic/libraries/architecture/datastore) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | The tick loop and every async boundary | Apache-2.0 | Yes | [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | Save serialization | Apache-2.0 | Yes | [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) |
| `com.google.android.gms:play-services-ads` | 25.4.0 | The banner advertisement | Android SDK Licence | See below | [Google Mobile Ads SDK](https://developers.google.com/admob/android/quick-start) |
| `com.google.android.ump:user-messaging-platform` | 4.0.0 | The EEA/UK consent form | Android SDK Licence | See below | [UMP SDK](https://developers.google.com/admob/android/privacy) |

`org.jetbrains.kotlin:kotlin-stdlib` 2.4.20 (Apache-2.0) arrives with the Kotlin
plugin rather than through a `dependencies` line, and ships in the app.

`androidx.compose.ui:ui-tooling` is `debugImplementation` only: the preview
renderer is not in the release build and is not counted here.

### Transitive dependencies

133 further modules are pulled in. They fall into four groups:

| Group | Modules | Licence | Copyright |
| :-- | --: | :-- | :-- |
| AndroidX / Jetpack (Compose, Lifecycle, DataStore, SavedState, Window, WorkManager, Room, SQLite, Profileinstaller, Privacy Sandbox Ads, AppCompat, Emoji2, WebKit, Browser, …) | 113 | Apache-2.0 | The Android Open Source Project |
| Kotlin and kotlinx (coroutines, serialization, `org.jetbrains:annotations`) | 8 | Apache-2.0 | JetBrains s.r.o. and Kotlin contributors |
| Google Play services (`play-services-ads-api`, `-ads-identifier`, `-appset`, `-base`, `-basement`, `-measurement-base`, `-measurement-sdk-api`, `-tasks`) and `com.google.android.play:hsdp` | 9 | Android Software Development Kit Licence | Google LLC |
| Java annotation and utility libraries — Guava 31.1-android (+ `failureaccess`, `listenablefuture`), Error Prone annotations, J2ObjC annotations, FindBugs JSR-305, JSpecify, Okio | 9 | Apache-2.0 (one MIT, below) | Google LLC, Square Inc., The JSpecify Authors |

Two modules are not Apache-2.0 and need their own notice:

| Dependency | Version | Licence | Requirement |
| :-- | :-- | :-- | :-- |
| `androidx.datastore:datastore-preferences-external-protobuf` | 1.2.1 | **BSD-3-Clause** — a repackaged subset of Protocol Buffers, `Copyright 2008 Google Inc.` | Binary redistribution must reproduce the copyright notice, the conditions and the disclaimer. Done in `THIRD_PARTY_NOTICES.txt` §2. |
| `org.checkerframework:checker-qual` | 3.12.0 | **MIT** — `Copyright 2004-present by the Checker Framework developers` | The copyright notice and permission notice must travel with copies. Done in `THIRD_PARTY_NOTICES.txt` §3. |

### Apache-2.0 in practice

Section 4 requires that redistributors give recipients a copy of the licence,
keep the existing copyright/patent/attribution notices, mark modified files, and
reproduce any `NOTICE` file the original carries.

EARTH modifies none of these libraries and repackages none of them — they are
consumed as published artifacts. The script checks every shipped artifact for an
embedded `NOTICE` file on each run and folds any it finds into the notices; at
the versions above **none of them ships one**, so 4(d) adds nothing beyond the
licence text and attribution that `THIRD_PARTY_NOTICES.txt` already carries.

### Google Play services and UMP

These are licensed under the [Android Software Development Kit License
Agreement](https://developer.android.com/studio/terms), not an OSI licence. It
permits distributing the SDK as part of an application and does not require its
own text to be reproduced in the app.

The Google binaries do, however, embed 84 further third-party components (Guava,
Gson, Protocol Buffers, Tink, Conscrypt, ExoPlayer, Volley, Dagger, the Checker
Framework, Kotlin, and others), and Google publishes their full licence texts
inside each AAR as `third_party_licenses.txt` with a `third_party_licenses.json`
index.

**That combined text is about 600 KB** — several times the size of everything
else in the notices, and larger than EARTH's own compiled code. The judgement
taken here is to list every component by name, read from Google's own index, and
point at the authoritative text rather than paste it. If you would rather ship
Google's notices verbatim, the supported way is Google's own
[oss-licenses Gradle plugin](https://developers.google.com/android/guides/opensource),
which generates them from the same indexes and provides an activity to display
them; it costs roughly that 600 KB in the APK plus the
`play-services-oss-licenses` dependency.

> **For owner/legal review.** Whether the reference is sufficient, or the
> verbatim text should be shipped, is a call for whoever publishes the app. It is
> recorded as a MEDIUM item in [Release blockers](RELEASE_BLOCKERS.md).

---

## What does not ship

Build- and test-time software is not distributed inside the APK, so its licences
impose nothing on players. Listed for completeness, read from the same metadata:

| Software | Version | Licence |
| :-- | :-- | :-- |
| Gradle | 9.7.1 | Apache-2.0 |
| Android Gradle Plugin | 9.4.0 | Apache-2.0 |
| Kotlin compiler, Compose compiler and serialization plugins | 2.4.20 | Apache-2.0 |
| JUnit 4 | 4.13.2 | **Eclipse Public License 1.0** |
| Hamcrest | 1.3 | New BSD |
| Robolectric | 4.16.1 | MIT |
| Robolectric `nativeruntime-dist-compat` | 1.0.18 | Apache-2.0 |
| AndroidX Test (`core`, `runner`, `monitor`, `ext:junit`, Espresso) | various | Apache-2.0 |
| ASM | 9.8 | BSD-3-Clause |
| ICU4J | 77.1 | Unicode-3.0 |
| Android SDK platform, build-tools, platform-tools | 37 | Android SDK Licence |

JUnit's EPL-1.0 is weak copyleft, and the only reason it is worth naming: it
applies to JUnit itself, and EARTH neither modifies nor distributes it. Nothing
in `app/src/main` depends on it.

`tools/ts-reference/` is the project's own frozen TypeScript engine, kept as the
parity oracle. It is EARTH's own code, not a third-party dependency, and
`npm install` there is a developer step that touches nothing in the app build.

---

## Assets and artwork

| Asset | Files | Origin | Licence status |
| :-- | :-- | :-- | :-- |
| Launcher icon | `mipmap-*/ic_launcher*.png`, `mipmap-anydpi-v26/ic_launcher*.xml` | Produced for this project during the Android port. No provenance record exists in the repository. | **Unverified — owner must confirm.** See [Release blockers](RELEASE_BLOCKERS.md). |
| Fonts | none | The app uses the platform's default type only — no font file is bundled | n/a |
| Sound / music | none | `SoundPoolAudio` is a working stub with no audio files behind it | n/a |
| Images, illustrations | none | Every visual in the game is drawn by Compose from colour tokens, or is an emoji rendered by the platform font | n/a |
| Emoji | n/a | Rendered by the device's own font, never bundled | n/a |

The icon is the only non-code asset in the repository. It is not derived from
any stock library the repository records, but the repository records nothing
either way, and a licence cannot be asserted from a file's pixels. Confirming
its provenance is a release blocker, not a documentation gap.

---

## Regenerating this

```bash
# The notices file, from the real resolved classpath:
python3 scripts/third-party-notices.py

# What the script reads, if you want to check it by hand:
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

The script fails loudly on any module whose POM declares a licence it does not
recognise, rather than filing it under a default. An unattributed dependency
should stop a release, so it stops the script.

Run it whenever a dependency is added, removed or upgraded, and commit the
result: the build copies `THIRD_PARTY_NOTICES.txt` into the app's assets, so a
stale file is a stale in-app licence screen.

---

**Next:** [Licensing](wiki/Licensing.md) · [Release blockers](RELEASE_BLOCKERS.md) · [Security and privacy](wiki/Security-and-Privacy.md)
