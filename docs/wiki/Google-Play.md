# Google Play

[← Documentation home](Home.md)

Everything about shipping EARTH through the Play Store: what the build already
satisfies, what lives in Play Console, and what the app's configuration commits
the listing to saying.

The working documents are [Google Play release
checklist](../GOOGLE_PLAY_RELEASE_CHECKLIST.md) (item by item, with evidence) and
[Data safety](../GOOGLE_PLAY_DATA_SAFETY.md) (the form, answer by answer). This
page is the map.

---

## Current status

> **No release has ever been published.** No Play listing, no GitHub release, no
> tag. `versionCode = 1`, `versionName = "1.0.0"`.

| | |
| :-- | :-- |
| Target API | **37** — Play requires 36+ for submissions from 31 August 2026 |
| Compile SDK | 37 |
| Min SDK | 24 |
| Application ID | `com.earthgame.idle` |
| Format | AAB for Play, APK for GitHub releases |
| Signing | **None configured** — artifacts build unsigned |
| Ads | Yes, one AdMob banner |
| In-app purchases | None — there is no billing dependency at all |
| Accounts | None |

---

## What the build already satisfies

Each of these was checked against the built artifacts, not asserted:

| Requirement | Verified by |
| :-- | :-- |
| `targetSdk` ≥ 36 | `aapt2 dump badging` → `targetSdkVersion:'37'` |
| 64-bit native code | `arm64-v8a` and `x86_64` present for both `.so` files |
| Publishable as an AAB | `bundletool build-apks --mode=universal` produces an installable APK |
| 16 KB page alignment | `bundletool dump config` → `PAGE_ALIGNMENT_16K` |
| Not debuggable | `android:debuggable` absent from the release manifest |
| Real app label and icon | `application-label:'EARTH'`, adaptive icon at five densities |
| One exported component | `MainActivity` with the launcher filter; nothing else exported |
| Minified build that works | R8 + resource shrinking on; UMP and Mobile Ads classes retained |
| Production ad identifiers | `aapt2 dump resources` on the release APK; no sample ID anywhere in it |

Reproduce the lot:

```bash
./gradlew packageReleaseArtifacts
$ANDROID_HOME/build-tools/<version>/aapt2 dump badging release/EARTH-*.apk
$ANDROID_HOME/build-tools/<version>/aapt2 dump resources release/EARTH-*.apk | grep -A1 admob_
```

---

## What Play Console needs that a repository cannot provide

| | Depends on |
| :-- | :-- |
| **Upload key + Play App Signing enrolment** | A keystore the owner generates — [Release signing](../RELEASE-SIGNING.md) |
| **Privacy policy URL** | The policy's placeholders being filled in first |
| **Data safety declaration** | The owner submitting the prepared answers |
| **Content rating questionnaire** | The owner answering it |
| **Target audience** | 13+, not primarily child-directed — must match the app having no age gate |
| **Ads declaration** | Yes |
| **Store listing** | Name, descriptions, 512×512 icon, feature graphic, screenshots from a real build |
| **AdMob consent message** | Created in the AdMob console under *Privacy & messaging* |

That last one is the trap. **The UMP form is loaded from the AdMob console, not
bundled in the app.** Until it exists, the consent flow correctly reports that ads
may not be requested, and EEA/UK players see no banner at all. That is the right
behaviour and it looks exactly like a bug.

---

## What the app's configuration commits the listing to

These are not free choices — the code has already decided them, and a listing
that says otherwise would be false:

| Declaration | Must be | Because |
| :-- | :-- | :-- |
| Contains ads | **Yes** | One AdMob banner |
| Uses advertising ID | **Yes**, for advertising | `play-services-ads` merges `AD_ID` into the manifest |
| Data collected by the app itself | **None** | Nothing in `domain/` or `data/` touches a network |
| Data shared | Device or other IDs, with Google | The ad request |
| Encrypted in transit | **Yes** | All traffic is Google's SDK over HTTPS |
| Deletion request mechanism | **No** | Nothing is held off-device to delete |
| In-app purchases | **No** | No billing dependency |
| Target audience | 13+, not child-directed | No age gate, no child-directed ad configuration |

---

## Release sequence

```bash
# 1. Version
#    app/build.gradle.kts: earthVersionCode += 1, earthVersionName = "x.y.z"

# 2. Verify
./gradlew clean
./gradlew test
./gradlew lintDebug lintRelease
python3 scripts/third-party-notices.py --check

# 3. Build both artifacts, signed
EARTH_KEYSTORE=… EARTH_KEYSTORE_PASSWORD=… EARTH_KEY_ALIAS=… EARTH_KEY_PASSWORD=… \
  ./gradlew packageReleaseArtifacts

# 4. Walk docs/PRODUCTION_QA_CHECKLIST.md on a real device, on the release APK

# 5. Tag
git tag -a v1.0.0 -m "EARTH 1.0.0" && git push origin v1.0.0

# 6. Upload release/EARTH-<version>-release.aab to Play, internal testing first
# 7. Read the pre-launch report before promoting
# 8. Staged rollout, not 100% on day one
# 9. GitHub release for the tag, with the APK attached
```

**Archive `app/build/outputs/mapping/release/mapping.txt` with every release.**
Play will accept it as a deobfuscation file; without it a crash report from a
minified build is unreadable. Play Console uploads it automatically from an AAB,
but keep your own copy for the GitHub-distributed APK.

---

## Rollback

There is no downgrade path: `versionCode` can never decrease. To undo a bad
release, **halt the staged rollout**, then ship a higher `versionCode` with the
fix.

If the bad release changed `SAVE_VERSION`, the fix has to handle saves that
already migrated. Because `migrate` [preserves a newer save's version rather than
stamping it down](Save-Migrations.md#saves-from-a-newer-build), a player who got
the bad build and then the fix will not have their save double-migrated — that is
the specific disaster that behaviour exists to prevent.

---

## APK and AAB are not interchangeable

| | |
| :-- | :-- |
| **AAB** | What Play requires for a new app. Google generates per-device APKs from it and signs them with the app signing key. |
| **APK** | What a player downloads from GitHub and sideloads. Signed by you, with your key. |

The two carry **different signatures**, so a player cannot install one over the
other — Android refuses, and they must uninstall first, which deletes the save.
Say so on the GitHub release page.

---

**Next:** [Google Play checklist](../GOOGLE_PLAY_RELEASE_CHECKLIST.md) · [Release process](Release-Process.md) · [Data safety](../GOOGLE_PLAY_DATA_SAFETY.md)
