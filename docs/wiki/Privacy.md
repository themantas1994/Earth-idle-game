# Privacy

[← Documentation home](Home.md)

The consent architecture, what a player controls, and how the published policy
maps onto the code.

For the threat model, the permission table and the secrets audit, see
[Security and privacy](Security-and-Privacy.md). For the published document
itself, see [the privacy policy](../PRIVACY_POLICY.md). This page is about the
*implementation*.

---

## The one-paragraph version

EARTH stores one save file in private application storage and sends it nowhere.
The only network traffic the app produces is a Google ad request and the consent
form that gates it. In the EEA, the UK and Switzerland nothing is requested until
Google's User Messaging Platform reports that a request is permitted, and the
player can change that answer at any time from two places in the app.

---

## Consent architecture

```
MainActivity.onCreate
    └── MonetizationController(activity).start()
            ├── ConsentRequestParameters
            │       └── debug builds only: DEBUG_GEOGRAPHY_EEA (+ optional test device)
            ├── requestConsentInfoUpdate ──────────────┐
            │       success                            │ failure
            │         └── loadAndShowConsentFormIfRequired
            │                     └── onConsentSettled ┘
            └── onConsentSettled
                    ├── privacyOptionsAvailable = (status == REQUIRED)
                    ├── canRequestAds()  ── false ──▶ nothing more happens
                    └──                    true  ──▶ MobileAds.initialize (IO, once)
                                                 └──▶ canRequestAds = true
                                                         └──▶ BannerAd loads
```

Three properties do the work:

**`canRequestAds` is the gate, not a hint.** `BannerAd` returns immediately while
it is false, and `createBannerView` refuses to build a view. A player who
declines, or who launched with no network so the consent update failed, gets no
ad request at all — not a non-personalised one.

**Both flags are Compose state.** The flow finishes well after the first frame.
Plain fields would leave the Settings entry and the banner showing whatever was
true at composition time.

**Failure is a closed door, not an open one.** Every error path leads to
`onConsentSettled` with whatever UMP actually recorded, which for an error is
"may not request". The alternative — serve something rather than nothing — is the
one that is a policy violation.

## What the player controls

| Control | Where | Effect |
| :-- | :-- | :-- |
| Consent choices | The UMP form on first launch in the EEA/UK/CH | Whether ads are requested at all, and whether they are personalised |
| **Manage advertising privacy** | Settings → Privacy | Reopens the UMP form |
| **Manage advertising privacy** | Settings → About → Privacy & advertising | The same form, from the About screen |
| Advertising ID | Android Settings → Privacy → Ads | Reset or delete it; applies to every app |
| Everything the app stores | Uninstall, or clear app data | Deletes the save permanently |
| Auto Backup | Android's own backup setting | Whether the save is included in a device backup |

The privacy-options entries appear when
`PrivacyOptionsRequirementStatus == REQUIRED` and are hidden otherwise — a player
who was never shown a message has no form to reopen, and a button that opens
nothing is worse than no button. They are **not** hidden once a decision has been
made.

## What actually leaves the device

| | |
| :-- | :-- |
| The simulation | **Nothing.** Every number is computed on the phone. |
| The save | **Nothing.** One file in private storage, never read out to a network. |
| The ad request | Advertising ID, IP address, device/app information, and the recorded consent signals — by Google's SDK, over HTTPS. |
| The consent form | Downloaded from Google when it needs to be shown. |
| Analytics, telemetry, crash reporting | **None exist.** No such SDK is on the classpath. |

Confirm the last line at any time:

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | \
  grep -iE "analytics|crashlytics|firebase|sentry|bugsnag|appsflyer|adjust|amplitude|mixpanel"
```

## Permissions, including the ones the app does not declare

The app's own manifest declares three. The merged manifest has more, because
`play-services-ads` and its dependencies contribute their own — and a data-safety
declaration has to be made against the **merged** list, not the source one.

| Permission | Source | Why |
| :-- | :-- | :-- |
| `INTERNET` | the app | The ad and the consent form |
| `ACCESS_NETWORK_STATE` | the app | The ad SDK checks connectivity |
| `VIBRATE` | the app | Two haptic pulses |
| `com.google.android.gms.permission.AD_ID` | Mobile Ads SDK | The advertising ID |
| `ACCESS_ADSERVICES_AD_ID` | Mobile Ads SDK | Privacy Sandbox |
| `ACCESS_ADSERVICES_ATTRIBUTION` | Mobile Ads SDK | Privacy Sandbox |
| `ACCESS_ADSERVICES_TOPICS` | Mobile Ads SDK | Privacy Sandbox |
| `WAKE_LOCK` | WorkManager, via the ad SDK | Background scheduling the app never uses |
| `FOREGROUND_SERVICE` | WorkManager, via the ad SDK | The same |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX | Internal receiver protection |

```bash
./gradlew assembleRelease
$ANDROID_HOME/build-tools/<version>/aapt2 dump badging \
  app/build/outputs/apk/release/app-release*.apk | grep uses-permission
```

No location, camera, microphone, contacts, calendar, photos, storage,
notifications, exact alarms or `QUERY_ALL_PACKAGES`.

The merged manifest **does** carry a `<queries>` element, contributed by
`androidx.browser` and the Mobile Ads SDK. It is package-visibility filtering, not
a permission, and it is narrow: an https `VIEW` intent, `CustomTabsService`, a
calendar `INSERT`, an `sms` `VIEW`, a `DIAL`, and the Play Store package by name.
It lets those SDKs find a browser or a Custom Tabs provider for an ad's landing
page and the consent form. It is emphatically **not** `QUERY_ALL_PACKAGES` — the
app cannot enumerate what is installed — but "the app queries nothing" would be
the wrong thing to say, so it is recorded here.

## Age and child-directed treatment

EARTH has **no age gate** and therefore makes no assumption about a player's age.
`setTagForChildDirectedTreatment` and `setTagForUnderAgeOfConsent` are both
unset — calling either would be asserting something the app has no basis for, and
a wrong assertion is worse than an absent one. The store listing declares 13+ and
not primarily child-directed, which is the configuration that matches the code.

See [Privacy policy §11](../PRIVACY_POLICY.md#11-children) and
[Advertising](Advertising.md#under-age-users).

## Logging

Only `Log.w`, and only on failures the app has already recovered from:

| Site | What is logged |
| :-- | :-- |
| `MonetizationController` | Google's own diagnostic message for a consent or ad-SDK failure |
| `DataStoreSaveRepository` | That a save could not be read, written or cleared |

**Nothing logs game state, save contents, consent choices or any identifier**, at
any level, in any variant. A release build is silent in Logcat during normal play.

```bash
grep -rn "Log\.\|println" app/src/main    # nine call sites, all Log.w
```

## Keeping the published policy true

[`docs/PRIVACY_POLICY.md`](../PRIVACY_POLICY.md) makes specific factual claims
about this code. Any of these changes falsifies one of them:

| Change | Re-read |
| :-- | :-- |
| Adding any analytics, attribution or crash-reporting SDK | §1, §4, §7 — "no analytics" becomes false |
| Enabling ad mediation | §7 — other networks become recipients |
| Adding interstitial or rewarded formats | §6 |
| Adding cloud save, accounts or leaderboards | §3, §9, §10 — the publisher would then hold personal data |
| Upgrading `play-services-ads` such that it merges a new permission | §5 |
| Adding an age gate | §11 |
| Writing anything new to the save | §3 |

And in every case, [Data safety](../GOOGLE_PLAY_DATA_SAFETY.md) too.

---

**Next:** [Advertising](Advertising.md) · [Security and privacy](Security-and-Privacy.md) · [Google Play](Google-Play.md)
