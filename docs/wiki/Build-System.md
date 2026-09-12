# Build system

[← Documentation home](Home.md)

## Requirements

| | |
| :-- | :-- |
| **JDK** | **21.** The wrapper pins Gradle 9.7.1 and AGP 9.4.0, which run on JDK 17–21; 21 is what CI uses. |
| **Android SDK** | `platforms;android-37`, `build-tools;37.0.0`, `platform-tools` |
| **SDK location** | `ANDROID_HOME` pointing at your SDK, or `sdk.dir=/path/to/sdk` in `local.properties` (git-ignored) |
| **Node** | Only for [regenerating parity fixtures](Reference-Parity.md). Not in the app build path at all. |

Android Studio has all of this. Open the repository root — it is the Gradle project — and press Run.

## Layout

```
settings.gradle.kts        pluginManagement, dependency repositories, include(":app")
build.gradle.kts           root — declares plugins, applies none
gradle/libs.versions.toml  the single source of truth for every version
gradle.properties          JVM args, parallel, caching
app/build.gradle.kts       the only module
app/compose-stability.conf domain types declared stable to the Compose compiler
app/proguard-rules.pro     R8 keep rules
```

One module, `:app`. Version catalogue for every dependency — **never write a version literal in a
build file.**

`RepositoriesMode.FAIL_ON_PROJECT_REPOS` means repositories are declared once, in
`settings.gradle.kts`, and a module cannot quietly add its own.

## Commands

```bash
./gradlew assembleDebug            # debug APK, standard debug key
./gradlew assembleRelease          # release APK
./gradlew bundleRelease            # Play bundle (AAB)
./gradlew test                     # JVM unit tests (216)
./gradlew lintDebug lintRelease
./gradlew connectedAndroidTest     # instrumented; needs a device or emulator

./gradlew assembleDebug -Pearth.composeReports=true   # + Compose skippability reports
```

Outputs under `app/build/`:

| Artifact | Path |
| :-- | :-- |
| Debug APK | `outputs/apk/debug/app-debug.apk` |
| Release APK | `outputs/apk/release/app-release.apk`, or `app-release-unsigned.apk` with no keystore |
| Play bundle | `outputs/bundle/release/app-release.aab` |
| Lint reports | `reports/lint-results-debug.html`, `lint-results-release.html` |
| Test reports | `reports/tests/testDebugUnitTest/index.html` |
| Compose reports | `compose-reports/debug/app-module.json` (opt-in) |

## Variants

| | Debug | Release |
| :-- | :-- | :-- |
| `applicationId` | `com.earthgame.idle.debug` | `com.earthgame.idle` |
| `versionName` | `1.0.0-debug` | `1.0.0` |
| Minify / shrink | off | **on** (R8 + resource shrinking) |
| `BuildConfig.DEBUG` | `true` | `false` |
| AdMob app ID | Google's public sample | the configured live app ID |
| Ad unit | Google's public test unit | the configured live unit |
| UMP debug geography | forced to EEA | off |
| Signing | standard debug key | the configured keystore, or unsigned |

The `.debug` suffix means a debug build installs **alongside** a release build rather than replacing
it, so you can keep both on one device. They do not share a save.

## Dependencies

Every version lives in `gradle/libs.versions.toml`.

| | Why |
| :-- | :-- |
| `androidx.core:core-ktx` | Baseline AndroidX |
| `androidx.lifecycle:*-runtime-ktx`, `-runtime-compose` | `collectAsStateWithLifecycle` |
| `androidx.lifecycle:lifecycle-process` | `ProcessLifecycleOwner` — the whole offline trigger |
| `androidx.activity:activity-compose` | `setContent`, `viewModels`, `BackHandler`, `enableEdgeToEdge` |
| Compose BOM + `ui`, `ui-graphics`, `material3`, `material3-window-size-class` | The UI |
| `androidx.compose.ui:ui-tooling-preview` | The `@Preview` annotation. **Nothing declares one yet**; kept so adding the first preview is not also a build-file change. |
| `androidx.compose.ui:ui-tooling` (debug only) | The preview renderer |
| `androidx.datastore:datastore-preferences` | The save |
| `kotlinx-coroutines-android` | The tick loop |
| `kotlinx-serialization-json` | The save format |
| `play-services-ads`, `user-messaging-platform` | The one banner and its consent form |

Test-only: `junit`, `kotlinx-coroutines-test`, `robolectric`, `androidx.test:core`,
`androidx.test.ext:junit`, `compose-ui-test-junit4`, `compose-ui-test-manifest`.

### Removed in the audit

`navigation-compose` (the app uses [an explicit destination stack](Navigation.md)),
`lifecycle-viewmodel-compose` (the `viewModels()` delegate comes from `activity-compose`),
`androidx.window` (transitive through `material3-window-size-class`; nothing uses it directly), and
`espresso-core` (the instrumented tests use Compose test rules).

> **Honest note on impact:** removing them changed the release APK by **48 bytes** (3,733,335 →
> 3,733,287). R8 was already stripping the unused code. The benefit is a shorter dependency list to
> keep current and audit, not size.

### Adding a dependency

Ask: is it actually needed, is it maintained, is there a platform alternative, does it enlarge the
APK, does it introduce a security surface, and does it complicate the architecture? The bar is
deliberately high — this app ships with **twelve** runtime dependencies for a reason. If it goes in,
it goes in the version catalogue, never as a literal.

## Compose stability

```kotlin
composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
    if (providers.gradleProperty("earth.composeReports").isPresent) {
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose-reports"))
    }
}
```

The domain layer has no Compose dependency, so its value types cannot carry `@Immutable`. They are
declared stable from outside instead. See [UI architecture](UI-Architecture.md#recomposition-and-stability).

> Comments in `compose-stability.conf` use `//`, **not `#`** — a `#` comment is a hard compiler
> error (`COMPOSE_CONFIGURATION_ERROR`).

## `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=false
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

The configuration cache is **off**: Kotlin's build-tools classpath does not serialise cleanly into
it on this AGP/KGP pair. The build is fast enough without it, and this is worth revisiting on the
next toolchain bump.

`android.defaults.buildfeatures.buildconfig` used to be here, set to `false`. AGP 9 removed the
option and warns about it; the module now opts in explicitly with `buildConfig = true` in
`buildFeatures`, because `BuildConfig.DEBUG` gates the UMP debug geography and
`BuildConfig.VERSION_NAME` is what the About screen shows. **Which AdMob identifiers a build uses
is not decided there** — that is a resource overlay, see [Advertising](Advertising.md).

## Generated sources

Two things in `app/build.gradle.kts` produce files the build then consumes:

| | |
| :-- | :-- |
| `BundleNoticesTask` | Copies `THIRD_PARTY_NOTICES.txt` from the repository root into the variant's assets, wired through `variant.sources.assets.addGeneratedSourceDirectory` so task ordering is AGP's problem rather than a `dependsOn` guess. One committed copy, no drift — see [Licensing](Licensing.md). |
| `packageReleaseArtifacts` | Runs `assembleRelease` and `bundleRelease` and collects both into `release/` as `EARTH-<version>-release.{apk,aab}`, suffixed `-unsigned` when no keystore is configured. `release/` is git-ignored. |

## CI

`.github/workflows/android.yml` — every push to `main`, every pull request, and manual dispatch.
Ubuntu, JDK 21 (Temurin), `gradle/actions/setup-gradle` with the cache read-only off `main`:

1. `python3 scripts/check-docs-links.py` — every relative Markdown link and anchor
2. `./gradlew testDebugUnitTest --stacktrace`
3. `./gradlew lintDebug lintRelease --stacktrace`
4. `python3 scripts/third-party-notices.py --check`
5. `./gradlew assembleDebug --stacktrace`
6. Decode `EARTH_KEYSTORE_BASE64` into `$RUNNER_TEMP`, if the secret exists
7. `./gradlew packageReleaseArtifacts --stacktrace`
8. Delete the keystore — `if: always()`, and **before** the upload step so it can never be globbed
   into an artifact

Artifacts (debug APK, release APK and AAB, lint reports, test reports) are uploaded on every run,
`if: always()`.

Step 4 fails the build if a dependency changed without the notices being regenerated — a stale
notices file is a stale in-app licence screen. It **checks** rather than regenerates: regeneration
reads POMs and artifacts out of the Gradle module cache, and a runner's cache holds only what its
build happened to need, so a full regeneration there reported every dependency as unlicensed
instead of reporting the real problem. `--check` compares the committed file against the resolved
classpath and needs no cache at all.

Release signing secrets are **optional**: without them the artifacts are still produced, unsigned,
so a pull request from a fork is not blocked. See [Release signing](../RELEASE-SIGNING.md).

There is no `connectedAndroidTest` step — GitHub's standard runners have no KVM, so no emulator can
boot; that is why the UI and DataStore tests also run under Robolectric.

---

**Next:** [Release process](Release-Process.md) · [Release signing](../RELEASE-SIGNING.md) · [Testing](Testing.md) · [Troubleshooting](Troubleshooting.md)
