# Building EARTH

A native Kotlin/Jetpack Compose Android game. Open the repository root in
Android Studio — it is the Gradle project — or build it from the command line
with the wrapper.

## Requirements

| | |
| --- | --- |
| **JDK** | 21. The wrapper pins Gradle 9.7.1 and AGP 9.4.0, which run on JDK 17–21; 21 is what CI uses. |
| **Android SDK** | `platforms;android-37`, `build-tools;37.0.0`, `platform-tools`. |
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
| Lint reports | `../reports/lint-results-debug.html`, `lint-results-release.html` |
| Test reports | `../reports/tests/testDebugUnitTest/index.html` |

`adb install app/build/outputs/apk/debug/app-debug.apk` puts the debug build on
a device. It installs alongside a release build rather than replacing it: the
debug variant carries the `.debug` application-id suffix.

## Signing a release

No keystore is committed, and none should be. Create one and supply it through
Gradle properties, environment variables, or a git-ignored
`keystore.properties` at the repository root — checked in that order:

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
fresh clone is never blocked on secrets.

## Ads

One anchored adaptive banner at the bottom of the screen. There are no
interstitials and no rewarded ads, and the game is fully playable if the banner
never loads.

Both AdMob identifiers live in **`app/src/main/res/values/ads.xml`**, which is
the single place to change them for a different account:

| | |
| --- | --- |
| App ID | Read from the manifest by the Mobile Ads SDK at startup. The SDK crashes on launch if it is missing. |
| Banner unit | Requested by `MonetizationController`. |

Neither is a secret — both ship inside every APK and are visible to anyone who
unzips one — and no secret belongs in that file.

**Debug builds always request Google's public test unit** instead of the
configured one. AdMob counts impressions and clicks a developer generates on
their own live unit as invalid traffic, and repeat offences suspend the whole
account, so a live unit is only ever requested from a non-debuggable build. To
exercise ads on a device, install the debug APK — it will show a real test
banner.

Two things are configured outside this repository and are on you:

- The **GDPR/EEA consent message** has to be created under *Privacy & messaging*
  in the AdMob console. The UMP form is loaded from there, not bundled here.
  Startup runs consent → SDK initialization → banner, in that order, because
  serving ads in the EEA or UK without a consent message is a policy violation.
  A debug build forces the EEA debug geography so the flow can be exercised
  anywhere.
- The **Play Console data-safety form** has to declare the advertising ID,
  because `play-services-ads` merges `com.google.android.gms.permission.AD_ID`
  into the manifest.

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
is covered by fast JVM tests rather than instrumented ones.

## Tests

```bash
./gradlew test                 # ~120 JVM tests, a few seconds
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

`.github/workflows/android.yml` runs the unit tests, both lint variants, the
debug APK and the release bundle on every push and pull request, and uploads
the artifacts and reports. Release signing secrets are optional: without them
the bundle is still produced, unsigned.
