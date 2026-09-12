# Google Play release checklist

[← Documentation home](wiki/Home.md)

EARTH audited against Google Play's requirements, with each item marked by
**whether it was actually verified from the build** or still needs the publisher.

> [!IMPORTANT]
> **Nothing here is a claim that Play will accept the app.** Items marked
> *verified* were checked against the built artifacts in this repository and the
> commands that checked them are given. Items marked *publisher* cannot be
> verified from a repository at all — they live in Play Console, in an AdMob
> account, or in a legal decision — and are listed so they are not forgotten.
> Google's requirements also change; re-read
> [the Play Console requirements](https://support.google.com/googleplay/android-developer/answer/9859152)
> before submitting.

Legend: ✅ verified from the build · ⬜ publisher action · ⚠️ blocker

---

## Technical requirements

| | Requirement | State | Evidence |
| :-- | :-- | :-- | :-- |
| ✅ | **Target API level.** New apps and updates submitted from **31 August 2026** must target **API 36 (Android 16)** or higher. | `targetSdk = 37` | `aapt2 dump badging` → `targetSdkVersion:'37'` |
| ✅ | `compileSdk` at least as high as `targetSdk` | `compileSdk = 37` | `app/build.gradle.kts` |
| ✅ | **64-bit support.** Every native library must have a 64-bit variant. | `arm64-v8a` and `x86_64` present for both `.so` files | `unzip -l` on the release APK |
| ✅ | **App Bundle.** New apps must be published as an AAB. | `bundleRelease` produces `app-release.aab`; `bundletool build-apks` turns it into installable APKs | `bundletool dump manifest` |
| ✅ | **16 KB page alignment** (required for apps targeting Android 15+ on 16 KB devices) | The bundle declares `PAGE_ALIGNMENT_16K`, and `extractNativeLibs=false` | `bundletool dump config` |
| ✅ | Unique, stable `applicationId` | `com.earthgame.idle` — unchanged from the existing build | `aapt2 dump badging` |
| ✅ | `versionCode` is a positive integer, increasing per upload | `1` (first release) | `aapt2 dump badging` |
| ✅ | `versionName` present and human-readable | `1.0.0` | `aapt2 dump badging` |
| ✅ | No `android:debuggable="true"` in the release build | Attribute absent | `aapt2 dump xmltree` |
| ✅ | App label set and not a placeholder | `EARTH` | `aapt2 dump badging` → `application-label:'EARTH'` |
| ✅ | Launcher icon present at every density, with an adaptive icon | `mipmap-{m,h,xh,xxh,xxxh}dpi` + `mipmap-anydpi-v26` with background, foreground and monochrome layers | `app/src/main/res/mipmap-*` |
| ✅ | Exactly one exported component | `MainActivity`, launcher intent filter only; no exported services, receivers or providers | merged manifest |
| ✅ | Minified and shrunk release build that still works | R8 on, resource shrinking on, 6,312 classes in the release dex; UMP and Mobile Ads classes retained (198 of 224 `consent_sdk` classes kept, the rest unreachable) | `mapping.txt`, `apkanalyzer dex packages` |
| ⬜ | **Signed with an upload key, enrolled in Play App Signing** | No keystore exists | [Release signing](RELEASE-SIGNING.md) |
| ⬜ | Pre-launch report reviewed after the first internal-testing upload | — | Play Console |

`minSdk = 24` is a choice, not a requirement — Play has no minimum.

---

## Privacy and policy

| | Requirement | State | Evidence |
| :-- | :-- | :-- | :-- |
| ✅ | Consent flow for EEA/UK/CH users, via a Google-certified CMP | Google UMP SDK 4.0.0; no ad is requested unless `canRequestAds()` is true | `platform/ads/Monetization.kt` |
| ✅ | Users can withdraw or change consent | Settings → Manage advertising privacy, and About → Manage advertising privacy, both calling `showPrivacyOptionsForm` | `SettingsScreen.kt`, `AboutScreen.kt` |
| ✅ | Ads declaration is truthful | The app does contain ads | one AdMob banner |
| ✅ | Advertising ID declaration matches the merged manifest | `AD_ID` is merged in by `play-services-ads` | `aapt2 dump badging` |
| ✅ | A privacy policy exists and describes the real behaviour | [Privacy policy](PRIVACY_POLICY.md), written from the source | |
| ⚠️ | **Privacy policy placeholders filled in** | Controller name and contact are `[…]` | [Release blockers](RELEASE_BLOCKERS.md) |
| ⬜ | Privacy policy hosted at a stable URL and entered in Play Console | The repository copy is reachable on GitHub; the publisher decides whether that is the canonical URL | |
| ⬜ | **Data safety form submitted** | Prepared, not submitted | [Data safety](GOOGLE_PLAY_DATA_SAFETY.md) |
| ⬜ | **Content rating questionnaire** completed | — | Play Console |
| ⬜ | **Target audience and content** set to 13+, not child-directed | Consistent with the app having no age gate | [Privacy policy §11](PRIVACY_POLICY.md#11-children) |
| ⬜ | **AdMob consent message created** under *Privacy & messaging* | The UMP form is loaded from the AdMob console, not bundled in the app. Without it, the flow reports that ads may not be requested and no banner appears in the EEA/UK. | AdMob console |
| ⬜ | AdMob app linked to the Play listing | | AdMob console |
| ⬜ | Account deletion policy | Not applicable — no accounts | |

---

## Store listing

All publisher actions; none can be verified from a repository.

| | Item | Notes |
| :-- | :-- | :-- |
| ⬜ | App name (≤ 30 chars) | "EARTH" is available as a name to submit, but is a common word — check it is not already taken and is findable |
| ⬜ | Short description (≤ 80 chars) | |
| ⬜ | Full description (≤ 4000 chars) | The README's opening section is accurate source material |
| ⬜ | App icon, 512×512 PNG | The launcher icon exists in the repository but **not** at 512×512 — see [Release blockers](RELEASE_BLOCKERS.md) |
| ⬜ | Feature graphic, 1024×500 | Does not exist |
| ⬜ | Phone screenshots (2–8, 16:9 or 9:16) | Must be from a real build |
| ⬜ | Tablet screenshots | The app has a genuine tablet layout (side rail, width-capped content), so these are worth taking |
| ⬜ | Category | Games → Simulation |
| ⬜ | Contact email | Required, and must match the privacy policy |
| ⬜ | Countries and regions | Consent handling assumes EEA/UK/CH are included |
| ⬜ | Pricing: free, no in-app purchases | True of the build — there is no billing dependency at all |

**Do not ship placeholder artwork.** A screenshot of an emulator with a debug
banner, or an icon generated to fill a slot, is worse than delaying the listing.

---

## Before every subsequent release

```bash
./gradlew clean
./gradlew test
./gradlew lintDebug lintRelease
python3 scripts/third-party-notices.py --check
./gradlew packageReleaseArtifacts
```

- [ ] `earthVersionCode` incremented in `app/build.gradle.kts`
- [ ] `earthVersionName` set
- [ ] [Production QA checklist](PRODUCTION_QA_CHECKLIST.md) walked on a real device
- [ ] Data safety form re-checked if any dependency changed
- [ ] Release notes written
- [ ] Staged rollout rather than 100% on day one

---

**Next:** [Release process](wiki/Release-Process.md) · [Data safety](GOOGLE_PLAY_DATA_SAFETY.md) · [Release blockers](RELEASE_BLOCKERS.md)
