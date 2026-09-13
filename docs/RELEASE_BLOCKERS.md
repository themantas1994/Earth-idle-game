# Release blockers and findings

[← Documentation home](wiki/Home.md)

Everything found while preparing EARTH 1.0.0 for public release, classified by
what it actually costs and by who can close it. **Nothing here is hidden or
softened.**

> **The release target is a signed APK published on
> [GitHub Releases](GITHUB_RELEASE_TEMPLATE.md).** Google Play is not part of
> this release. Every Play-specific requirement has therefore been taken off the
> critical path and moved to
> [Not blocking a GitHub release](#not-blocking-a-github-release); none of them
> stands between this repository and a public APK. Play remains a future option —
> see [Google Play](wiki/Google-Play.md).

---

## How this is classified

Two categories, because they fail for entirely different reasons and are closed
by different people.

| Category | What it means | Who closes it |
| :-- | :-- | :-- |
| **[Technical blockers](#technical-blockers)** | Something in this repository stops a public APK: it does not build, the APK is unsigned or its signature is invalid, it crashes at startup, a save breaks, the UMP integration is wrong, the production configuration is wrong, or there is a known security issue | Fixable in code, in this repository |
| **[Owner / distribution requirements](#owner--distribution-requirements)** | Something only the repository owner can supply or decide — a signing key, a legal choice, an account-side configuration, hardware | The owner. Not one of them is a code change |

Within each, severity says how much it costs:

| Severity | Meaning |
| :-- | :-- |
| **BLOCKER** | Must be closed before the APK is distributed at all |
| **HIGH** | Should be fixed before distribution |
| **MEDIUM** | Recommended |
| **LOW** | Optional |

Every item marked ✅ was fixed in the release-preparation work and is listed for
the record.

---

## Technical blockers

> ### None open.
>
> The technical release configuration builds, tests, lints and packages a
> production APK, and every technical finding in this document is either fixed or
> classified as non-blocking below.

Reconciled against a full build of this branch: `./gradlew clean test` (**220
unit tests, 0 failures**), `./gradlew lint lintRelease` (**0 issues on each
variant**), `./gradlew assembleRelease` and `./gradlew packageReleaseApk` all
succeed, and the resulting APK was inspected with `aapt2`, `apkanalyzer`,
`zipalign` and `apksigner`. The signing path was exercised end to end with a
throwaway key generated outside the repository and then discarded — see
[O2](#o2-no-production-signing-key-exists) for what that does and does not prove.

What was verified on that build, rather than assumed:

| | |
| :-- | :-- |
| Package name | `com.earthgame.idle`, no suffix |
| Version | `versionName` 1.0.0, `versionCode` 1 |
| Build type | `release`; **not** debuggable; `testOnly` not set |
| Minification | R8 on, resource shrinking on, `mapping.txt` produced |
| AdMob | production app ID and banner unit present; **no** Google sample identifier anywhere in the APK |
| Debug isolation | the debug APK carries the sample identifiers and no production one |
| Permissions | `INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE` and the seven the Ads SDK contributes — nothing else |
| Launcher icon | adaptive icon with the in-repo background colour, at all five densities |
| Notices | `third_party_notices.txt` present in assets |
| Alignment | `zipalign -c 4` passes; `.so` files 16 KB-aligned |

### Debug and release separation, read from the two APKs

Not asserted from the build files — both APKs were built and taken apart:

| | Release APK | Debug APK |
| :-- | :-- | :-- |
| `applicationId` | `com.earthgame.idle` | `com.earthgame.idle.debug` |
| `versionName` | `1.0.0` | `1.0.0-debug` |
| AdMob app ID | `…6872627319793193~7208922044` (production) | `…3940256099942544~3347511713` (Google sample) |
| Banner unit | `…6872627319793193/5213314092` (production) | `…3940256099942544/6300978111` (Google sample) |
| Production identifiers anywhere in the archive | present, once each, in `resources.arsc` | **absent** |
| Google sample identifiers anywhere in the archive | **absent** | present |
| `DEBUG_GEOGRAPHY` references in the dex | **0** | 5 |
| `ump_test_device_hashed_id` resource | **not present** — the debug-only consent branch is dead code under `BuildConfig.DEBUG`, so R8 removes it and the resource shrinker then drops the string as unreferenced | present |
| `android:debuggable` | not set | set |
| Minified | yes (R8 + resource shrinking) | no |

The two source-set files are the only place the identifiers exist, and the
variants cannot share them: `ProductionAdConfigTest` additionally asserts that no
shipped Kotlin or Java file contains an AdMob identifier at all, and that Google's
sample publisher ID appears nowhere outside `src/debug`.

The two things a technical pass cannot establish are recorded honestly as
[O2](#o2-no-production-signing-key-exists) (the artifact is signed with the
owner's key) and [O6](#o6-the-release-build-has-never-run-on-a-physical-device)
(it runs on hardware).

---

## Fixed technical findings

Nine HIGH findings were found and fixed during release preparation. They are kept
here because several of them were real GDPR or revenue defects, and because a
regression in any of them would be a technical blocker again.

### H1. Ads were requested regardless of the consent outcome ✅ FIXED

The previous flow initialised the Mobile Ads SDK and loaded a banner on **every**
path, including the one where the consent update had failed and the one where the
player had declined. `canRequestAds()` was fetched and then used as the
*personalisation* flag rather than as the gate on requesting at all.

That is the substantive GDPR failure: no ad may be requested in the EEA, UK or
Switzerland when the recorded consent state does not permit it.

**Fixed** in `platform/ads/Monetization.kt`: `MobileAds.initialize` runs only when
`canRequestAds()` is true, `BannerAd` renders nothing until that flag flips, and
the flag is Compose state so the UI reacts when the flow finishes.

### H2. Personalisation was signalled incorrectly ✅ FIXED

The old code attached the `npa=1` network extra whenever `canRequestAds()` was
false, and omitted it whenever it was true — conflating "may I request an ad" with
"may I personalise it". With a TCF-certified CMP in place, the serving mode is
determined by the consent signals UMP writes and the Mobile Ads SDK reads; `npa`
is the mechanism for publishers *without* such a CMP, and setting it alongside UMP
overrides the player's actual choice in one direction.

**Fixed:** the extra is gone and the SDK is left to honour the UMP signals.

### H3. Debug builds used the production AdMob app ID ✅ FIXED

The banner unit was swapped at runtime for a debug build, but the **app ID** in
the manifest was the live one in every variant, so a developer build still
initialised the SDK against the production AdMob app.

**Fixed** with a debug resource overlay (`app/src/debug/res/values/ads.xml`)
carrying Google's public sample app ID and banner unit. Because it is a resource
overlay rather than a runtime branch, a debug build cannot reach the live account
at all. `ProductionAdConfigTest` asserts both sides.

### H4. Mobile Ads SDK initialised on the main thread ✅ FIXED

`MobileAds.initialize` does Play Services, disk and network work. Google's quick
start puts it on a background thread; it was being called inline from a
`LaunchedEffect` on the main dispatcher.

**Fixed:** it now runs on `Dispatchers.IO`, once, guarded by an `AtomicBoolean`.

### H5. The controller was constructed inside a composable ✅ FIXED

`MonetizationController` was created in a `LaunchedEffect` inside `setContent`.
It survived in practice, but SDK initialisation does not belong anywhere that
recomposition can reach.

**Fixed:** constructed in `MainActivity.onCreate`, once per activity.

### H6. No in-app licence notices ✅ FIXED

Attribution for 149 shipped modules existed nowhere a player could see. Apache-2.0
section 4, the MIT licence and BSD-3-Clause all require notices to travel with
the distribution.

**Fixed:** `THIRD_PARTY_NOTICES.txt` is generated from the resolved classpath by
`scripts/third-party-notices.py`, copied into the app's assets at build time, and
shown at **Settings → About → Open Source Licenses**.

### H7. CI could never actually sign ✅ FIXED

The workflow exported `EARTH_KEYSTORE_PASSWORD`, `EARTH_KEY_ALIAS` and
`EARTH_KEY_PASSWORD` but never `EARTH_KEYSTORE`, so the keystore file was never
materialised and every CI release build was unsigned regardless of secrets.

**Fixed:** the workflow decodes `EARTH_KEYSTORE_BASE64` into the runner's
temporary directory, exports the path, and deletes the file in an `always()` step
before artifacts are uploaded.

Re-verified in this audit: all four values the build reads now reach it. The
`Decode release keystore` step writes `EARTH_KEYSTORE` into `$GITHUB_ENV`, and the
`Release APK and bundle` step passes `EARTH_KEYSTORE_PASSWORD`, `EARTH_KEY_ALIAS`
and `EARTH_KEY_PASSWORD`. Nothing is echoed, and `rm -f` runs under `if: always()`
before the upload step.

### H8. The banner was requested during composition, and went blank on a resize ✅ FIXED

Two defects in `BannerAd`, both from the same root — the ad request lived inside
the `remember` that built the view:

- **`loadAd` ran before the listener was attached.** `createBannerView` called it
  while composing; the `AdListener` was attached afterwards, in `DisposableEffect`.
  An ad that resolved immediately — a cached fill, or a failure the SDK answers
  without the network — reported to nobody, leaving `loaded` false and the banner
  sized as though nothing had arrived. A composition that was then abandoned also
  spent a request on an `AdView` nothing would ever `destroy()`.
- **The banner went blank after a window-size change.** `remember(widthDp)` built a
  new `AdView` and `DisposableEffect` destroyed the old one, but `AndroidView`
  calls its factory **once per node** — so the view on screen stayed the destroyed
  original and the replacement was never attached. `MainActivity` handles
  `orientation|screenSize` itself, so this was a plain rotation, not an edge case.

**Fixed:** `createBannerView` now only constructs and sizes the view;
`MonetizationController.loadBanner` makes the request from `DisposableEffect`,
after the listener is attached and after re-checking `canRequestAds`. The
`AndroidView` is wrapped in `key(adView)` so a new view actually replaces the old
node, and `loaded` is keyed on the view so an empty replacement does not inherit
the previous ad's width.

### H9. The documentation described a merged manifest that does not exist ✅ FIXED

Four documents claimed the shipped app had **exactly one exported component** and
**no services, receivers or providers** — two of them citing "merged manifest" as
the evidence. Read from the release APK, the merged manifest holds 6 activities,
5 services, 9 receivers and 2 providers, and **three** exported components besides
`MainActivity`:

| Exported | From | Gate |
| :-- | :-- | :-- |
| `androidx.work.impl.background.systemjob.SystemJobService` | WorkManager | `BIND_JOB_SERVICE` |
| `androidx.work.impl.diagnostics.DiagnosticsReceiver` | WorkManager | `DUMP` |
| `androidx.profileinstaller.ProfileInstallReceiver` | ProfileInstaller | `DUMP` |

All three are gated by permissions no ordinary app holds, so the security
conclusion was right — but the statement of it was false, and a false statement
backed by a citation is worse than no statement.

The privacy policy also said the app makes **"no query of the other apps installed
on the device."** The merged manifest carries a `<queries>` element from
`androidx.browser` and the Mobile Ads SDK (https `VIEW`, `CustomTabsService`,
calendar `INSERT`, `sms`, `DIAL`, and `com.android.vending` by name). That is
filtered package visibility, not `QUERY_ALL_PACKAGES` — the app still cannot
enumerate what is installed — but it is not "no query", and a privacy policy is the
last place to be loose about it. The policy's permission table was also missing
`com.earthgame.idle.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

**Fixed:** [Privacy policy §5](PRIVACY_POLICY.md#5-permissions),
[Security and privacy](wiki/Security-and-Privacy.md#attack-surface),
[Android platform](wiki/Android-Platform.md), [Privacy](wiki/Privacy.md) and the
[Play checklist](GOOGLE_PLAY_RELEASE_CHECKLIST.md) now carry the real inventory,
with the command that reproduces it.

---
## Owner / distribution requirements

Six items, none of them a code change, and none of them closable from inside the
codebase. Four of them **do** block a public GitHub release; two are strong
recommendations that the owner may knowingly accept.

| | Item | Blocks a GitHub release? | Severity |
| :-- | :-- | :-- | :-- |
| [O1](#o1-the-project-has-no-licence) | The project has no licence | **Yes** — the source is published without one | BLOCKER |
| [O2](#o2-no-production-signing-key-exists) | No production signing key exists | **Yes** — an unsigned APK cannot be installed | BLOCKER |
| [O3](#o3-the-privacy-policy-contains-placeholders) | The privacy policy contains placeholders | **Yes** — the app links to it from About, and it names no controller | BLOCKER |
| [O4](#o4-the-launcher-icons-provenance-is-unrecorded) | The launcher icon's provenance is unrecorded | **Yes** — it would be distributing artwork of unknown licensing | BLOCKER |
| [O5](#o5-no-consent-message-exists-in-the-admob-console) | No consent message exists in the AdMob console | Not the build — but it decides whether the app is GDPR-correct in the EEA | HIGH |
| [O6](#o6-the-release-build-has-never-run-on-a-physical-device) | The release build has never run on a physical device | Not the build — but it is the only way to know the minified APK works | HIGH |

### O1. The project has no licence

`LICENSE` does not exist. Under copyright law the default applies — **all rights
reserved** — so nobody may copy, modify or redistribute the source, whatever the
word "open-source" elsewhere in the README implies.

This blocks a public GitHub release directly: the release page publishes a tag of
the source alongside the APK, and the
[release template](GITHUB_RELEASE_TEMPLATE.md) links to `LICENSE`. Publishing the
binary you own the copyright in would be fine on its own; publishing the source
under no licence while calling the project open-source is not.

**Only the owner can fix this**, and a licence must not be invented on their
behalf. The decision is between, typically:

| | |
| :-- | :-- |
| **MIT** | Shortest permissive licence; anyone may do anything with attribution |
| **Apache-2.0** | Permissive with an explicit patent grant; matches every dependency the app ships |
| **GPL-3.0** | Copyleft; derivative works must also be GPL |
| **No licence** | Keep it proprietary — then remove "open-source" from the README, the release page and the About screen, and distribute the APK only |

To apply one: add `LICENSE` at the repository root with the chosen text, set the
same licence in the GitHub repository settings, update the README's Licence
section, and change `project_license` in `app/src/main/res/values/strings.xml`
from "No licence declared — all rights reserved" to the licence name. The About
screen and `AboutScreenTest` read that one string, so nothing else changes.

### O2. No production signing key exists

`assembleRelease` succeeds but produces an **unsigned** APK, which Android will
not install. `packageReleaseApk` names it
`EARTH-1.0.0-release-unsigned.apk` — the filename is the build telling the truth
about itself.

Deliberately not fixed here: a signing key must be generated by whoever owns the
app, on a machine they control, and never committed. Nothing in this repository
generates or holds one.

**What has been verified**, so that the owner is not debugging the build and the
key at the same time:

- All three local routes the build reads signing material through
  (`keystore.properties`, Gradle properties, environment variables) resolve, and
  all four CI variables reach the build.
- The full signing path was exercised with a **throwaway key generated outside the
  repository, used once, and discarded.** With it, `packageReleaseApk` produced
  `EARTH-1.0.0-release.apk` — no `-unsigned` suffix — and
  `apksigner verify --verbose` reported `Verifies`, with APK Signature Scheme v2
  and v3 both true.

**That proves the pipeline, not the release.** The published APK must be signed
with the owner's own key, and its signature verified against *that* certificate.
The throwaway key was never committed, is not in the repository or its history,
and no longer exists.

**Fix:** follow [Release signing](RELEASE-SIGNING.md), which is written for
GitHub distribution and needs no Play Console. Note especially
[§4 — back up the keystore](RELEASE-SIGNING.md#4-back-up-the-keystore): outside
Play App Signing, **losing this key means never being able to update the app
again**, and no support process can recover it.

### O3. The privacy policy contains placeholders

[`docs/PRIVACY_POLICY.md`](PRIVACY_POLICY.md) is accurate about the app's
behaviour, but three fields are placeholders: the controller's name, the contact
address, and the publication date.

This blocks release rather than merely being untidy. The app ships AdMob and the
UMP consent flow, the About screen links to this policy by URL, and the
[release page](GITHUB_RELEASE_TEMPLATE.md) links to it too — so it is the app's
actual published privacy notice. A notice naming no controller and offering no
contact does not satisfy GDPR Articles 13–14 regardless of how the app is
distributed. GitHub-only distribution removes the Play Console requirement for a
policy URL; it does not remove the legal requirement for the policy.

A legal entity and a contact address **must not be invented**. The repository
records neither, and none has been fabricated.

**Fix:** fill in sections 2 and 14 of the policy and set the date. Both are marked
`OWNER ACTION REQUIRED` in the file itself.

### O4. The launcher icon's provenance is unrecorded

`app/src/main/res/mipmap-*/ic_launcher*.png` is the only non-code asset in the
repository, and nothing records a source, author or licence for it.

Now documented in full in [**Assets and their provenance**](ASSETS.md), which
also records what *is* verifiable: the bitmaps carry no PNG metadata of any kind
(no `tEXt`, `iTXt`, `zTXt` or `tIME` chunk, so no embedded author or tool), they
were added in the first Android commits with no note of a source, and the adaptive
icon XML, the background colour and the layer structure are all authored in-repo
and are plainly this project's. The **bitmap artwork itself** is what has no
recorded origin. Provenance cannot be established by inspection and will not be
invented here.

Shipping an asset whose licensing is unclear is exactly what an asset audit is
supposed to prevent, and a GitHub release distributes it just as widely as a store
would.

**Fix:** the owner records in [`docs/ASSETS.md`](ASSETS.md) that the icon is
original work of the project and states its licence; or records the stock source
or generator and its terms; or replaces the artwork. [ASSETS.md](ASSETS.md) lists
the three options and what each needs.

### O5. No consent message exists in the AdMob console

**This is an AdMob console dependency, not an application defect.** The app's side
of the consent flow is implemented and verified — see [H1](#h1-ads-were-requested-regardless-of-the-consent-outcome--fixed), H2 and the
[Advertising](wiki/Advertising.md) page. What the app cannot contain is the
consent message itself: the UMP SDK *downloads* it from the account's
**AdMob → Privacy & messaging → GDPR** configuration at runtime. There is no way
to create, bundle or simulate it in application code, and nothing in this
repository can confirm whether it has been created.

Both failure modes matter, and they pull in opposite directions:

| If no message is configured | What happens |
| :-- | :-- |
| UMP finds no message that applies to an EEA/UK/CH user | `consentStatus` comes back `NOT_REQUIRED`, `canRequestAds()` returns **true**, and the banner is requested **with no consent message ever shown** — the exact policy failure H1 was about, moved from the code into the account |
| UMP errors instead | No ad is ever requested, and the app earns nothing in those regions |

The app behaves correctly in both cases. Neither is an acceptable thing to ship
without checking which one it is.

**Owner action, in the AdMob console — cannot be done from this repository:**

1. **AdMob → Privacy & messaging → GDPR** — create a message, select the app,
   choose the purposes and ad partners, and **publish** it. A saved-but-unpublished
   message does not serve.
2. Do the same for **US state regulations** if the app ships in those states.
3. Confirm the app is listed under **Privacy & messaging → app list** with the app
   ID `ca-app-pub-6872627319793193~7208922044`.
4. Verify on hardware: a debug build forces UMP's EEA debug geography, so the form
   must appear on first launch. It appearing is the only proof the console side is
   done.

### O6. The release build has never run on a physical device

Every claim in this repository about the release build is a claim about an
**artifact**, established by building it and taking it apart — not by running it.
No emulator or KVM has been available in any environment that has built this
project, and no `connectedAndroidTest` run and no manual pass has ever happened on
hardware.

**BUILD VALIDATED. RUNTIME ON A PHYSICAL DEVICE NOT VERIFIED.**

That gap matters most for exactly one thing: the release variant is the **only**
minified one. `isMinifyEnabled = true` and `isShrinkResources = true` apply to
`release` alone, so every test in this repository — all 220 of them, including the
Robolectric UI and DataStore suites — runs against **unminified** code. R8 was
inspected statically and looks sound — read from this build's own `mapping.txt`,
`GameDecimal`, `SaveSerialization` and `DataStoreSaveRepository` are retained,
`MonetizationController`'s methods survive class merging with `canRequestAds` and
`loadBanner` both present in the dex, and 233 UMP `consent_sdk`/`ump` classes are
retained — but static inspection is not execution.

These are also unverified, because they cannot be verified without a device:

- the two instrumented suites, `DataStoreSaveRepositoryTest` and `EarthAppUiTest`,
  have never been executed anywhere
- the UMP consent form actually rendering (which is also [O5](#o5-no-consent-message-exists-in-the-admob-console))
- a real banner filling from the live unit
- the launcher icon under a real launcher's masks and the themed/monochrome variant
- haptics, offline progression across a genuine process death, and rotation

**Fix:** walk [Final device QA](FINAL_DEVICE_QA.md) on a release build installed on
real hardware, and run `./gradlew connectedAndroidTest`. Until that has happened,
this item stays open regardless of how green CI is.

---
## Not blocking a GitHub release

Every item below was on the critical path when this project was being prepared for
the Play Store. **None of them stands between this repository and a public,
installable APK on GitHub Releases**, and none of them is a prerequisite for the
consent flow, the privacy policy or the licence work above.

| Play requirement | Why it does not block a GitHub release |
| :-- | :-- |
| A Play Console account and its one-off fee | Nothing in the build or the APK depends on it |
| A Play Store listing — title, descriptions, screenshots | The release page is written in Markdown; see [GitHub release template](GITHUB_RELEASE_TEMPLATE.md) |
| A 512×512 store icon and a 1024×500 feature graphic (was M3) | Play listing assets. Neither belongs in an APK and neither is shown anywhere on a GitHub release |
| Play App Signing enrolment | The owner's own key signs the APK directly, which is [Release signing](RELEASE-SIGNING.md) in full. Google holds no key in this path |
| Uploading an AAB | A player cannot install an AAB. `packageReleaseApk` does not build one; `packageReleaseArtifacts` still can if Play is ever added |
| A Data Safety declaration | A Play Console form. The same facts are published in the [privacy policy](PRIVACY_POLICY.md), which is what the app actually links to. The prepared answers are kept in [Data safety](GOOGLE_PLAY_DATA_SAFETY.md) for later |
| A content rating questionnaire | A Play Console form, per-territory. No equivalent exists for a GitHub download |
| Play's target-API deadlines | A condition of staying listed on Play. `targetSdk` is already 37 regardless |

`targetSdk 37`, `minSdk 24`, R8, resource shrinking, 16 KB page alignment and the
third-party notices are **not** in this table: they are good engineering for any
distribution channel, they are all already done, and they stay.

If Play is added later, [Google Play checklist](GOOGLE_PLAY_RELEASE_CHECKLIST.md)
and [Data safety](GOOGLE_PLAY_DATA_SAFETY.md) are still accurate and still apply —
they simply are not this release's gate. The one thing to know before doing it is
in [Release signing §8](RELEASE-SIGNING.md#8-if-google-play-is-ever-added): a Play
build and a self-signed GitHub build cannot be installed over one another.

---

## Non-blocking findings

Recommended and optional, in no case standing between this repository and a public
APK.

### MEDIUM — recommended

### M1. Adaptive-icon foreground layers are authored at legacy dimensions

`ic_launcher_foreground.png` ships at the *legacy launcher* sizes (48 px at mdpi
up to 192 px at xxxhdpi). An adaptive icon's foreground layer is 108 dp, which
needs 108 px at mdpi up to **432 px** at xxxhdpi. Android therefore upscales the
foreground by 2.25× on a modern phone, and the icon is softer than it should be.

The artwork itself is fine — the art occupies about 65% of its canvas, which sits
inside the visible mask. Only the export resolution is wrong.

**Fix:** re-export the foreground from its source art at 108/162/216/324/432 px —
worth doing at the same time as [O4](#o4-the-launcher-icons-provenance-is-unrecorded),
since both touch the same artwork.
Not done here because upscaling the existing bitmaps would add no detail, and
inventing replacement artwork is worse than naming the problem.

### M2. Google's own bundled notices are referenced rather than reproduced

`play-services-ads` and `user-messaging-platform` embed 84 further third-party
components, whose full licence texts Google publishes inside the AARs and which
total roughly 600 KB — several times the size of everything else in the notices
file, and larger than EARTH's own compiled code.

`THIRD_PARTY_NOTICES.txt` §4 lists all 84 by name, read from Google's own index,
and points at the authoritative text. Whether a reference suffices or the verbatim
text should ship is a judgement for the publisher.

**Fix if wanted:** add Google's
[oss-licenses Gradle plugin](https://developers.google.com/android/guides/opensource),
which generates them at build time from the same indexes.

### M4. The release build strips no native libraries here

Two `.so` files ship unstripped because this build environment has no NDK:

```
Unable to strip the following libraries, packaging them as they are:
libandroidx.graphics.path.so, libdatastore_shared_counter.so
```

Cosmetic — it costs a few kilobytes and nothing else — but a release built on a
machine with the NDK installed produces a slightly smaller APK, which is the
build the publisher should ship.

### M5. Gradle deprecation warnings ✅ FIXED — and they were ours

This entry previously blamed the AGP/KGP pair. That was wrong, and the build said
so: every warning pointed at `app/build.gradle.kts:253`, the
`val packageReleaseArtifacts by tasks.registering` delegate, deprecated in Gradle
9.6 in favour of `tasks.register(name)`.

**Fixed:** the task is registered with `tasks.register("packageReleaseArtifacts")`.
`./gradlew clean test lint assembleRelease bundleRelease packageReleaseArtifacts`
now completes with **no deprecation notice at all** — the "incompatible with Gradle
10" summary line is gone, not merely quieter.

The configuration cache is still switched off (see `gradle.properties`), for the
unrelated Kotlin build-tools classpath reason recorded there.

### M6. `createAndroidComposeRule` is deprecated

Both UI test suites use the v1 Compose test API, which now warns in favour of
`androidx.compose.ui.test.junit4.v2`. The v2 API changes dispatcher semantics, so
this is a migration rather than an import change, and it touches tests only.

### M7. The packaged release carried no metadata ✅ FIXED

`packageReleaseArtifacts` copied the APK and the AAB and nothing else, while both
[Release process](wiki/Release-Process.md) and the
[Production QA checklist](PRODUCTION_QA_CHECKLIST.md) instruct the publisher to
keep `mapping.txt` with every release. Nothing produced it under a release name,
so keeping it was a manual step against a path in `app/build/`.

**Fixed:** the task now also writes `EARTH-<version>-release-mapping.txt` and a
`SHA256SUMS.txt` file covering every artifact it produced, in the format `sha256sum -c`
reads. The CI workflow uploads both. `release/` is still git-ignored, the keystore
is never read by the task, and CI still deletes its decoded copy before the upload
step runs.

---

### LOW — optional

### L1. The Ads SDK contributes components the app does not use

This app declares one activity. The merged release manifest holds **6 activities,
5 services, 9 receivers and 2 providers**, plus seven permissions and a `<queries>`
element this repository never asked for — `androidx.work`, `androidx.room`,
`androidx.startup`, `androidx.profileinstaller` and `androidx.browser` components,
`AdActivity`, `AdService`, `OutOfContextTestingActivity`,
`NotificationHandlerActivity`, `GoogleApiActivity`, `HsdpShimActivity`,
`MobileAdsInitProvider`, and the `AD_ID`, `ACCESS_ADSERVICES_*`, `WAKE_LOCK` and
`FOREGROUND_SERVICE` permissions. All of it arrives with `play-services-ads` and
its dependency chain; none of it is reached by any code here.

Nothing can be done about it short of dropping ads. It must simply be declared
accurately rather than described as the app's own — which is what H9 was about.
The full inventory, with which components are exported and what gates them, is in
[Security and privacy](wiki/Security-and-Privacy.md#attack-surface); the permission
list is in the [privacy policy](PRIVACY_POLICY.md#5-permissions).

Reproduce it with:

```bash
./gradlew assembleRelease
$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer manifest print \
  app/build/outputs/apk/release/app-release*.apk
```

### L2. `SoundPoolAudio` is a working stub

The audio adapter exists and is wired, but no sound files are bundled, so the
"Sound Effects" and "Music" settings toggle nothing audible. Not a release
blocker — the settings are honest about what they control and the game is silent
either way — but a player may reasonably expect otherwise.

### L3. The `ump_test_device_hashed_id` resource is empty in debug

Forcing UMP's EEA debug geography on *physical* hardware requires a hashed test
device ID. The overlay has the slot and the instructions; it is empty until a
developer pastes theirs in. An emulator needs nothing.

---

## Summary

| Category | Open | Fixed |
| :-- | --: | --: |
| **Technical blockers** | **0** | 9 (H1–H9) |
| **Owner / distribution requirements** | 6 — four of them blocking | 0 |
| MEDIUM findings | 4 | 2 |
| LOW findings | 3 | 0 |
| Play-only requirements | not on this release's critical path | — |

### The six owner items, and who can close them

| | Item | Blocks release | Why only the owner |
| :-- | :-- | :-- | :-- |
| [O1](#o1-the-project-has-no-licence) | Project licence | **Yes** | A legal choice. A licence must not be invented on the owner's behalf |
| [O2](#o2-no-production-signing-key-exists) | Production signing key | **Yes** | The build is **ready to sign** — all three local routes, all four CI variables and the whole pipeline were verified with a throwaway key. Only the real key is missing, and it must be generated by the owner on a machine they control |
| [O3](#o3-the-privacy-policy-contains-placeholders) | Privacy-policy controller and contact | **Yes** | A real legal entity and address. Neither is recorded anywhere in this repository and neither will be fabricated |
| [O4](#o4-the-launcher-icons-provenance-is-unrecorded) | Icon provenance | **Yes** | Only the person who made or obtained the artwork knows. It cannot be read off the pixels — see [ASSETS.md](ASSETS.md) |
| [O5](#o5-no-consent-message-exists-in-the-admob-console) | AdMob consent message | No, but ship-affecting | Lives in the AdMob console. The app side is complete and verified; the console side cannot be created, simulated or even observed from here |
| [O6](#o6-the-release-build-has-never-run-on-a-physical-device) | Physical-device validation | No, but ship-affecting | Needs hardware. No environment that has built this project has had an emulator or KVM, so the minified release binary has never been executed |

**Not one of the six is a code change**, and not one could honestly be closed from
inside the codebase. O2 and O6 are the two that most often get waved through — O2
because `BUILD SUCCESSFUL` looks like success when the artifact is unsigned, and O6
because 220 green tests look like coverage when every one of them runs against
unminified code. Neither is.

### What is genuinely finished

The technical release configuration is done, and was re-verified rather than
assumed: `clean`, `test` (220 pass), `lint` and `lintRelease` (zero issues each),
`assembleRelease`, `packageReleaseApk` and `packageReleaseArtifacts` all succeed;
the release APK carries the production AdMob identifiers and no sample one; the
debug APK carries the sample identifiers and no production one; every `.so` is
16 KB-aligned; `targetSdk`/`compileSdk` are 37; the release build is not
debuggable; `THIRD_PARTY_NOTICES.txt` covers all 149 shipped modules; the signing
pipeline produces a `Verifies` APK when a key is supplied; and there is no secret
of any kind in the working tree or in git history.

### Release readiness

**AMBER** — technically buildable and packaged, with owner actions outstanding.

It is not GREEN, and cannot be from inside this repository: GREEN requires an APK
signed with the owner's own key and that signature verified, which is
[O2](#o2-no-production-signing-key-exists). It is not RED either — nothing
technical is broken, and no Play requirement is holding it up.

---

**Next:** [Release signing](RELEASE-SIGNING.md) · [GitHub release template](GITHUB_RELEASE_TEMPLATE.md) · [Assets](ASSETS.md) · [Third-party licences](THIRD_PARTY_LICENSES.md)
