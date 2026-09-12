# Security and privacy

[← Documentation home](Home.md)

EARTH is **local-first**. The design goal is that the app is uninteresting to attack because there
is nothing in it worth taking and nowhere for anything to go.

## What leaves the device

**The simulation: nothing at all.** Every number is computed on the phone. There is no server, no
account, no cloud save, no analytics, no telemetry, no crash reporting and no tracking of any kind.

**The ad banner: an ad request.** Google's Mobile Ads SDK makes it, and it carries what that SDK
carries, including the **advertising ID**. That is the only network traffic the app produces, and the
only reason the internet permission exists.

That is the complete list.

## Permissions

The app's own manifest declares three:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.VIBRATE" />
```

| | Used by | Removable? |
| :-- | :-- | :-- |
| `INTERNET` | The ad banner only | Yes — see [Advertising](Advertising.md#removing-ads-entirely) |
| `ACCESS_NETWORK_STATE` | The ad SDK | Yes, same |
| `VIBRATE` | Two haptic pulses | Yes; `AndroidHaptics` already degrades to a no-op |

**The shipped app has more than three, and the Play data-safety form must be filled in against the
merged list rather than this one.** `play-services-ads` and the libraries it depends on contribute:

| | Merged in by | For |
| :-- | :-- | :-- |
| `com.google.android.gms.permission.AD_ID` | Mobile Ads SDK | The advertising ID |
| `ACCESS_ADSERVICES_AD_ID` | Mobile Ads SDK | Privacy Sandbox |
| `ACCESS_ADSERVICES_ATTRIBUTION` | Mobile Ads SDK | Privacy Sandbox |
| `ACCESS_ADSERVICES_TOPICS` | Mobile Ads SDK | Privacy Sandbox |
| `WAKE_LOCK` | WorkManager, via the ad SDK | Background scheduling **this app never uses** |
| `FOREGROUND_SERVICE` | WorkManager, via the ad SDK | The same |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX | Internal receiver protection |

```bash
./gradlew assembleRelease
$ANDROID_HOME/build-tools/<version>/aapt2 dump badging \
  app/build/outputs/apk/release/app-release*.apk | grep uses-permission
```

The same libraries add components the app never touches: `androidx.work` and `androidx.room`
services and receivers, `com.google.android.gms.ads.AdActivity`, `OutOfContextTestingActivity` and
`HsdpShimActivity`. They come with the ad SDK and cannot be removed short of dropping ads — but
they belong in an honest description of what ships.

No location, no camera, no microphone, no contacts, no storage, no notifications, no exact alarms,
no `QUERY_ALL_PACKAGES`. **Nothing the app itself declares is a foreground service** — that
permission arrives with WorkManager, and no code in this repository starts one.

## The save

```
/data/data/com.earthgame.idle/files/datastore/earth-save.preferences_pb
```

Private app storage. Unreadable by other apps and by the user without root. It contains only game
state — no name, no email, no device identifier, no timestamps beyond the game's own clock readings.

It is **not encrypted**, and that is a deliberate proportionality judgement: the threat model for a
file containing "you own 40 campfires" does not justify a key-management story. There is nothing in
it a determined attacker with root would want.

Android's Auto Backup can include it if the user has that switched on. `backup_rules.xml` and
`data_extraction_rules.xml` scope it to that one file, so a restore brings the save and nothing
stale alongside it. To opt out entirely, set `android:allowBackup="false"`.

## Attack surface

**One exported component:** `MainActivity`, with the launcher intent filter. No services, no
receivers, no content providers, no deep links, no exported providers, and no `intent-filter` that
accepts data from another app. **The app's entire external entry point is "the user tapped the
icon."**

| | |
| :-- | :-- |
| **WebView** | None of the app's own. `androidx.webkit` and `androidx.browser` arrive transitively with the ad SDK, which uses them for ad content and the consent form; no code in this repository instantiates one. This was a WebView app once; nothing of that remains. |
| **Cleartext traffic** | Not enabled. `usesCleartextTraffic` is unset, so the API-28+ default (cleartext blocked) applies, and there is no network security config overriding it. |
| **Dynamic code loading** | None. No `DexClassLoader`, no reflection, no JS engine, no plugin mechanism. |
| **Deserialization** | Only the app's own save, parsed with `kotlinx.serialization.json` into `JsonObject` — a data tree, never reflective type instantiation. Nothing in a save can name a class. |
| **Intents sent out** | Two, both from the About screen and both `ACTION_VIEW` on a committed `https://` URL — the repository and the privacy policy. Each is wrapped so a device with no browser is a no-op rather than a crash. Nothing else in the app calls `startActivity`. |

## The save is untrusted input

The one place hostile data can enter is a modified save (root, or a restored backup from another
device). `SaveSerialization` treats it accordingly:

- **Structural validation** before anything else; a save that fails it is rejected outright.
- **Loading never throws.** Every field falls back to a default rather than propagating.
- **Non-finite doubles are rejected** — `1e999` is legal JSON that parses to `Infinity`, and one
  infinite temperature would `NaN` every downstream value for the rest of the run.
- **Negative and zero owned counts are dropped**, so they cannot flow into cost curves.
- **Unknown fields are ignored**, so a newer save cannot inject anything.
- **File-level corruption** is handled by `ReplaceFileCorruptionHandler` rather than crash-looping.

The worst a modified save can do is give its owner a better game, which is their own device and
their own business. It cannot execute anything or escape the app.

## Secrets

**There are none in this repository, and the audit checked.**

| | |
| :-- | :-- |
| Keystores | Not committed. `.gitignore` covers `*.jks`, `*.keystore`, `keystore.properties`. Signing comes from Gradle properties, env vars, or a git-ignored file. |
| Signing passwords | Never in a build file, a workflow, or a log. CI reads them from repository secrets. |
| AdMob app ID + banner unit | **In the repository, deliberately.** Both are public identifiers that ship inside every APK and are visible to anyone who unzips one. Neither is a key. |
| API keys, tokens | None exist. The app calls no API. |

If you fork this, replace the values in `res/values/ads.xml` with your own — using someone else's
live ad unit sends them your impressions and can get both accounts flagged.

## Debug-only behaviour that must not leak

| | |
| :-- | :-- |
| **Test ad units** | A **resource overlay**, not a runtime branch: `app/src/debug/res/values/ads.xml` replaces both the app ID and the banner unit at resource-merge time, so a debug build cannot reach the live AdMob account at all. `ProductionAdConfigTest` asserts both sides, and that no sample identifier is in the release resources. |
| **EEA consent debug geography** | Forced only under `BuildConfig.DEBUG`. The optional test-device hashed ID it needs on physical hardware is empty in the release resources, and asserted empty by `ProductionAdConfigTest`. |
| **Logging** | Nine call sites, all `Log.w`, all on failures already recovered from — ad-SDK and save-IO. No game state, no save content, no consent details, no identifiers, at any level in any variant. |
| `ui-tooling` | `debugImplementation` — the preview renderer is not in the release build. |

## What the Play data-safety form should say

| | |
| :-- | :-- |
| Data collected | **Device or other IDs — advertising ID**, by the Mobile Ads SDK, for advertising |
| Data shared | The same, with Google as the ad provider |
| Collected by the app itself | **None** |
| Encrypted in transit | Yes (the SDK's own traffic) |
| Deletion request mechanism | Not applicable — the app stores nothing off-device |
| Contains ads | **Yes** |

The full form, answer by answer and with the reasoning behind each, is
[Google Play Data Safety](../GOOGLE_PLAY_DATA_SAFETY.md).

## For a reviewer

The fastest way to confirm all of this:

```bash
# Every network-capable call site (should be ads only)
grep -rn "HttpURLConnection\|OkHttp\|Retrofit\|URLConnection\|Socket\|WebView" app/src/main

# Every permission
cat app/src/main/AndroidManifest.xml

# Everything that is logged
grep -rn "Log\.\|println" app/src/main

# The domain layer's imports (no Android, no network)
grep -rhn "^import" app/src/main/java/com/earthgame/idle/domain | sort -u
```

---

**Next:** [Privacy](Privacy.md) · [Advertising](Advertising.md) · [Licensing](Licensing.md) · [Save system](Save-System.md)
