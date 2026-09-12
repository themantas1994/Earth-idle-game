# Building EARTH

A native Kotlin/Jetpack Compose Android game. Open the repository root in
Android Studio — it is the Gradle project — or build it from the command line
with the wrapper.

> **This page is the quick reference.** The full treatment lives in the
> technical wiki: **[Build system](docs/wiki/Build-System.md)** (toolchain,
> variants, dependencies, R8, CI), **[Release process](docs/wiki/Release-Process.md)**
> (versioning, artifacts, the checklist),
> **[Release signing](docs/RELEASE-SIGNING.md)**,
> **[Google Play](docs/wiki/Google-Play.md)**,
> **[Advertising](docs/wiki/Advertising.md)**,
> **[Privacy](docs/wiki/Privacy.md)**,
> **[Licensing](docs/wiki/Licensing.md)** and
> **[Testing](docs/wiki/Testing.md)**. Start at
> **[docs/wiki/Home.md](docs/wiki/Home.md)**.

## Requirements

| | |
| --- | --- |
| **JDK** | 21. The wrapper pins Gradle 9.7.1 and AGP 9.4.0, which run on JDK 17–21; 21 is what CI uses. |
| **Android SDK** | `platforms;android-37.0` and `platform-tools`. AGP picks its own build-tools (36.0.0 for AGP 9.4) and installs them if the SDK licences are accepted. |
| **SDK location** | Point `ANDROID_HOME` at your SDK, or write `sdk.dir=/path/to/sdk` into `local.properties` (git-ignored). |

Nothing else is needed. There is no Node, npm or web toolchain in the build
path — see [The TypeScript reference](#the-typescript-reference) for the one
place TypeScript still appears, which is a developer tool rather than part of
the app.

## Building

```bash
./gradlew assembleDebug      # debug APK, signed with the standard debug key
./gradlew assembleRelease    # release APK
./gradlew bundleRelease      # Play bundle (AAB)
./gradlew packageReleaseArtifacts  # both of the above, collected into release/
./gradlew test               # JVM unit tests
./gradlew lintDebug lintRelease
./gradlew connectedAndroidTest   # instrumented tests; needs a device or emulator
```

Outputs land under `app/build/outputs/`:

| Artifact | Path |
| --- | --- |
| Debug APK | `apk/debug/app-debug.apk` |
| Release APK | `apk/release/app-release.apk`, or `app-release-unsigned.apk` when no keystore is configured |
| Play bundle | `bundle/release/app-release.aab` |
| Collected release artifacts | `release/EARTH-<version>-release.{apk,aab}` (git-ignored), from `packageReleaseArtifacts` |
| Lint reports | `../reports/lint-results-debug.html`, `lint-results-release.html` |
| Test reports | `../reports/tests/testDebugUnitTest/index.html` |

`adb install app/build/outputs/apk/debug/app-debug.apk` puts the debug build on
a device. It installs alongside a release build rather than replacing it: the
debug variant carries the `.debug` application-id suffix.

## Signing a release

**Full procedure, including CI and Play App Signing:
[docs/RELEASE-SIGNING.md](docs/RELEASE-SIGNING.md).**

No keystore is committed, and none should be. Create one and supply it through
a git-ignored `keystore.properties` at the repository root, Gradle properties,
or environment variables — checked in that order:

```bash
keytool -genkeypair -v -keystore earth-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias earth
```

```properties
# keystore.properties (git-ignored — never commit this)
storeFile=/abs/path/earth-release.jks
storePassword=…
keyAlias=earth
keyPassword=…
```

or:

```bash
EARTH_KEYSTORE=/abs/path/earth-release.jks \
EARTH_KEYSTORE_PASSWORD=… EARTH_KEY_ALIAS=earth EARTH_KEY_PASSWORD=… \
  ./gradlew bundleRelease
```

When nothing is configured the release variant still assembles, unsigned, so a
fresh clone is never blocked on secrets — and `packageReleaseArtifacts` names
those artifacts `-unsigned` so nobody mistakes them for distributable ones.

## Ads

One anchored adaptive banner at the bottom of the screen. There are no
interstitials and no rewarded ads, and the game is fully playable if the banner
never loads.

Both AdMob identifiers live in **`app/src/main/res/values/ads.xml`**, which is
the single place to change them for a different account:

| | |
| --- | --- |
| App ID | Read from the merged manifest by the Mobile Ads SDK at startup. The SDK crashes on launch if it is missing. |
| Banner unit | Requested by `MonetizationController`. |

Neither is a secret — both ship inside every APK and are visible to anyone who
unzips one — and no secret belongs in that file.

**Debug builds cannot reach the live account at all.**
`app/src/debug/res/values/ads.xml` overlays *both* identifiers with Google's
public sample ones at resource-merge time, so there is no runtime branch to get
wrong. AdMob counts impressions and clicks a developer generates on their own
live unit as invalid traffic, and repeat offences suspend the whole account. To
exercise ads on a device, install the debug APK — it will show a real test
banner.

Startup runs **consent → `canRequestAds()` → SDK initialization → banner**, in
that order and only when consent permits it. Details in
[docs/wiki/Advertising.md](docs/wiki/Advertising.md).

Two things are configured outside this repository and are on you:

- The **GDPR/EEA consent message** has to be created under *Privacy & messaging*
  in the AdMob console. The UMP form is loaded from there, not bundled here —
  **without it, EEA/UK players see no banner at all**, which is correct
  behaviour that looks like a bug. A debug build forces the EEA debug geography
  so the flow can be exercised anywhere.
- The **Play Console data-safety form** has to declare the advertising ID,
  because `play-services-ads` merges `com.google.android.gms.permission.AD_ID`
  into the manifest. Prepared answers:
  [docs/GOOGLE_PLAY_DATA_SAFETY.md](docs/GOOGLE_PLAY_DATA_SAFETY.md).

## Project layout

```
app/src/main/java/com/earthgame/idle/
  domain/            Pure Kotlin. No Android, no Compose — runs on the JVM.
    engine/            GameDecimal, constants, simulation, ownership, offline,
                       and GameLoop (the session layer above the physics)
    model/             GameState, gases, resources, typed amount containers
    climate/           Carbon-cycle integration, forcing, habitability
    technologies/      99 technologies across 11 branches + the scaling curves
    economy/           Purchasing and the price-stability invariant
    prestige/          Earth Points and the permanent upgrade tree
    achievements/  challenges/  events/  milestones/
    formatting/        The four number notations
    save/              Versioned JSON serialization and the migration chain
  data/
    repository/        SaveRepository interface + LoadResult
    persistence/       DataStore-backed implementation
  platform/
    haptics/  audio/  ads/
  presentation/
    GameViewModel      The tick loop, autosave and the single StateFlow
    EarthApp           App shell, navigation stack, Back handling
    theme/  screens/  components/  navigation/
```

`domain/` has no Android dependency at all, which is why the entire simulation
is covered by fast JVM tests rather than instrumented ones. Its only non-project
imports are `java.math`, `kotlin.math`, `kotlin.random` and
`kotlinx.serialization.json` — see [Architecture](docs/wiki/Architecture.md).

## Tests

```bash
./gradlew test                 # 215 JVM tests, a few seconds
./gradlew connectedAndroidTest # instrumented; needs a device or emulator
```

The JVM suite is mostly **parity tests**: it replays golden values captured from
the original TypeScript engine and asserts this port produces the same answers.
That includes a 95-step scripted playthrough that runs a planet from one
campfire to collapse and through a prestige reset, comparing every simulation
value at every step. See `app/src/test/java/com/earthgame/idle/parity/`, and
`Fixtures.kt` for the documented floating-point tolerances.

The instrumented suite covers what needs a device: DataStore persistence,
backup rotation and corruption recovery, and the Compose UI — every destination
rendering, Back unwinding the screen history, and a purchase reaching the game.

## The TypeScript reference

`tools/ts-reference/` holds the original engine, frozen. **It is not part of the
app and nothing in the build depends on it.** It exists so the parity fixtures
can be regenerated if the reference is ever consulted again:

```bash
cd tools/ts-reference
npm install
npm test        # the original engine's own 198 tests
npm run fixtures # rewrites app/src/test/resources/parity/*.json
```

The fixtures themselves are committed, so `./gradlew test` needs none of this.

## Continuous integration

`.github/workflows/android.yml` runs the documentation link check, the unit
tests, both lint variants, a coverage check on `THIRD_PARTY_NOTICES.txt`,
the debug APK and both release artifacts on every push and pull request, then
uploads the artifacts and reports.

Release signing secrets are optional: without them the artifacts are still
produced, unsigned, so a pull request from a fork is not blocked. With
`EARTH_KEYSTORE_BASE64` and the three password/alias secrets set, the workflow
decodes the keystore into the runner's temporary directory, signs, and deletes
it again before anything is uploaded. See
[docs/RELEASE-SIGNING.md](docs/RELEASE-SIGNING.md).
