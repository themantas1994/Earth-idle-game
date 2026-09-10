# Troubleshooting

[← Documentation home](Home.md)

## Build failures

### `SDK location not found`

```
> SDK location not found. Define a valid SDK location with an ANDROID_HOME
  environment variable or by setting the sdk.dir path in your project's
  local.properties file.
```

```bash
echo "sdk.dir=/path/to/Android/sdk" > local.properties     # git-ignored
# or
export ANDROID_HOME=/path/to/Android/sdk
```

### `Failed to find target with hash string 'android:37'` / missing build-tools

```bash
sdkmanager "platforms;android-37" "build-tools;37.0.0" "platform-tools"
```

Android Studio: **SDK Manager → SDK Platforms → Android API 37**, and **SDK Tools → Android SDK
Build-Tools 37**.

### `Unsupported class file major version` / a Java version complaint

Build with **JDK 21**. AGP 9.4 runs on 17–21.

```bash
java -version
./gradlew -version     # check "Launcher JVM"
export JAVA_HOME=/path/to/jdk-21
```

Android Studio: **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**.

### `Error parsing stability configuration file on line 0`

```
# … is not a valid pattern
```

`app/compose-stability.conf` uses **`//` comments, not `#`**. A `#` line is a hard compiler error.

### The build cannot download dependencies

Repositories are declared once in `settings.gradle.kts`
(`RepositoriesMode.FAIL_ON_PROJECT_REPOS`), so a module cannot add its own. Behind a proxy, configure
Gradle's proxy settings in `~/.gradle/gradle.properties` rather than editing the build files.

### Out of memory during the build

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

### The build behaves strangely after switching branches

```bash
./gradlew --stop && ./gradlew clean
rm -rf .gradle app/build build
```

The configuration cache is already off (see [Build system](Build-System.md)), so it is not the cause.

## Test failures

### A parity test fails after you changed a formula

**Expected** — that is the suite doing its job. You changed Kotlin without changing the reference:

```bash
cd tools/ts-reference
npm install && npx vitest run && npm run fixtures
cd ../.. && ./gradlew test
```

See [Reference parity](Reference-Parity.md).

### `Missing parity fixture parity/<name>.json`

The fixtures are committed, so this means a checkout problem or a deleted file. Restore with
`git checkout -- app/src/test/resources/parity/`, or regenerate as above.

### A parity test fails on a *tiny* difference

Check the tolerance being used. `TOLERANCE_EXACT` (0.0) is for values that are copies rather than
computations; a computed value at the end of a long chain belongs at `TOLERANCE_LOOSE` (1e-6). The
two engines use different `pow`/`log10`/`exp` implementations, which may differ in the last ulp.

If a value that should be a **copy** differs at all, that is a real bug — do not widen the tolerance
to make it pass.

### `ContentParityTest` fails after adding content

You added it to Kotlin but not to `tools/ts-reference/engine/`, or you did not regenerate. The count
and every field are pinned.

### A concurrency test fails intermittently

`GameViewModelConcurrencyTest` uses real threads. `aTickLandingInsideAPurchaseIsSerializedNotLost` is
deterministic and should never flake — if it fails, a state transition has escaped `stateLock`. The
two stress tests are timing-sensitive by nature; a failure there is still worth investigating before
assuming it is the machine.

### A Robolectric test fails with a resource error

`isIncludeAndroidResources = true` is already set. If it appears after adding a resource, `./gradlew
clean` first — stale merged resources are the usual cause.

### `connectedAndroidTest` finds no device

```bash
adb devices              # must list a device as "device", not "unauthorized"
adb kill-server && adb start-server
```

Requires a booted emulator or a physical device with USB debugging on. CI does not run this step:
GitHub's standard runners have no KVM, which is why the UI and DataStore tests also run under
Robolectric.

## Runtime problems

### The app crashes at launch with a Mobile Ads error

The Mobile Ads SDK reads its app ID from the manifest and **crashes at startup if it is missing**.
`res/values/ads.xml` must define `admob_application_id`. See [Advertising](Advertising.md).

### No ad banner appears

Expected in many cases, and never a problem for the game: no network, no fill, no Play Services, or a
consent form that would not load. The banner takes **no space at all** until an ad loads.

Debug builds always request Google's public test unit, which always fills — if a debug build shows
nothing, the problem is Play Services or the network, not the ad unit.

### The game says it recovered from a backup

The primary save was unreadable and the one-save-old backup was used. The game is playable and you
have lost at most one autosave interval (15 s). If it recurs,
[file an issue](https://github.com/themantas1994/Earth-idle-game/issues) — this path is defended by
design and should never fire in practice.

### The game says the save was corrupted and started fresh

Both slots were unreadable, or the DataStore file itself was damaged and had to be replaced. The
previous save is gone. This is the failure mode the two-slot design exists to make almost impossible,
so it is worth reporting with the device model and what happened just before.

### Offline progress banked less than expected

Check the cap. **12 hours** by default; **8 days** with Extended Endurance ×3 and Automated Industry.
A single absence longer than the cap is truncated to it — but the cap is per *absence*, so two short
absences bank both in full.

Also check Settings → offline progress is on. And note that offline progress grants production, never
purchases.

### The game seems frozen — nothing is producing

Almost certainly a device clock that moved backwards. The game **re-anchors** on the next tick and
resumes immediately; if it does not, that is a bug worth reporting.

If `lastTickAt` is in the future relative to the device clock, the game settles to an ordinary tick
rather than banking anything. See
[Offline progression](Offline-Progression.md#clock-anomalies).

### A debug and a release build do not share a save

Correct, and deliberate. Debug builds carry the `.debug` application-id suffix so they install
alongside a release build; separate application ids mean separate private storage.

### Numbers show a suffix I do not recognise

Past `Vg` (10⁶³) the compact notation uses two-letter names — `AA`, `AB`, `AC` … They are always two
letters so a huge number can never be mistaken for a small one. Settings → Number Format has three
other notations.

## Development annoyances

### Compose previews do not render

`ui-tooling-preview` is a dependency but **nothing in the codebase declares an `@Preview` yet**. Add
the annotation to a composable and it should work; if it does not, `ui-tooling` is `debugImplementation`
only, so make sure you are on the debug variant.

### The Compose reports directory is empty

They are opt-in:

```bash
./gradlew assembleDebug -Pearth.composeReports=true
cat app/build/compose-reports/debug/app-module.json
```

### `npm install` in `tools/ts-reference` fails

That directory is only needed to **regenerate** the parity fixtures — the fixtures are committed, so
`./gradlew test` never touches Node. If you are not changing gameplay maths, you can ignore it
entirely.

---

**Next:** [Build system](Build-System.md) · [Testing](Testing.md) · [FAQ](FAQ.md)
