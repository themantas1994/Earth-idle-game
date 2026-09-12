# Google Play Data Safety — preparation

[← Documentation home](wiki/Home.md)

What to enter in Play Console's **Data safety** form, derived from what EARTH
1.0.0 actually does.

> [!WARNING]
> **This document does not make the app compliant, and its existence proves
> nothing.** It is a preparation aid. Play Console's form changes, and the
> declaration you submit is a statement *you* make to Google and to players. The
> publisher must check every answer below against the live form and against
> Google's current
> [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469)
> before submitting, and re-check it whenever a dependency is added or upgraded.

---

## Where these answers come from

| Claim | How it was established |
| :-- | :-- |
| The app itself collects nothing | `grep` for every network-capable API in `app/src/main` returns only the advertising code; `domain/` and `data/` have no network dependency |
| The save never leaves the device | `DataStoreSaveRepository` writes one file in private storage; nothing reads it back out to a network |
| The advertising ID is processed | `com.google.android.gms.permission.AD_ID` is in the **merged** manifest, added by `play-services-ads` |
| No analytics or crash reporting | The full release classpath (149 modules) contains no analytics or crash-reporting SDK — see [Third-party licences](THIRD_PARTY_LICENSES.md) |
| Traffic is encrypted | All of it is Google's SDK over HTTPS; `usesCleartextTraffic` is unset, so the API-28+ default (cleartext blocked) applies |

Verify the merged permission list yourself at any time:

```bash
./gradlew assembleRelease
$ANDROID_HOME/build-tools/<version>/aapt2 dump badging \
  app/build/outputs/apk/release/app-release*.apk | grep uses-permission
```

---

## Section by section

### Data collection and sharing

| Question | Answer |
| :-- | :-- |
| Does your app collect or share any of the required user data types? | **Yes** — through the advertising SDK, not by the app itself |
| Is all of the user data collected by your app encrypted in transit? | **Yes** |
| Do you provide a way for users to request that their data be deleted? | **No** — and see the note below |

On deletion: the publisher holds no user data, so there is nothing to delete on
request. Uninstalling the app removes everything it stores. Google's own deletion
mechanisms cover the advertising data. Declare this honestly rather than claiming
a deletion channel that does not exist.

### Data types

| Category | Type | Collected | Shared | Purpose | Optional? |
| :-- | :-- | :-- | :-- | :-- | :-- |
| Device or other IDs | **Device or other IDs** (advertising ID) | **Yes** | **Yes** — with Google, the advertising provider | Advertising or marketing | **Yes** — users in the EEA/UK/CH consent first and can withdraw; Android's own ad-ID controls apply everywhere |
| App activity | — | No | No | — | — |
| App info and performance | — | No | No | — | — |
| Personal info | — | No | No | — | — |
| Financial info | — | No | No | — | — |
| Location | — | No | No | — | — |
| Files and docs | — | No | No | — | — |
| Messages, contacts, calendar | — | No | No | — | — |
| Photos and videos, audio | — | No | No | — | — |
| Health and fitness | — | No | No | — | — |
| Web browsing | — | No | No | — | — |

> **Approximate location.** Google's Data safety guidance treats data processed
> only ephemerally, or inferred from an IP address by the SDK, differently from
> data an app collects. The publisher should read Google's current definition of
> "collected" against the IP-derived coarse location an ad request implies, and
> answer accordingly. It is recorded here so the question is asked rather than
> missed.

### Security practices

| Question | Answer |
| :-- | :-- |
| Data is encrypted in transit | **Yes** |
| Users can request data deletion | **No** — nothing is held off-device to delete |
| Committed to Play Families Policy | Not applicable — the app is not targeted at children |
| Independent security review | **No** |

---

## Related Play Console declarations

These live outside the Data safety form but must agree with it:

| Declaration | Answer | Because |
| :-- | :-- | :-- |
| **Ads** — does your app contain ads? | **Yes** | One AdMob banner |
| **Advertising ID** — does your app use it? | **Yes**, for advertising | `play-services-ads` merges `AD_ID` |
| **Target audience and content** | 13+, not primarily child-directed | No age gate, no child-directed configuration — see [Privacy policy §11](PRIVACY_POLICY.md#11-children) |
| **Content rating questionnaire** | Answer it against the actual game: no violence, no sexual content, no profanity, no gambling, no user-generated content, no user-to-user communication, no purchases. Contains advertising. | |
| **Privacy policy URL** | The published policy | Required for any app that collects or shares personal data |
| **Government apps / financial features / health** | No | |
| **Data deletion URL** | None | No account exists to delete |
| **News app** | No | |
| **COVID-19 contact tracing** | No | |

---

## What would change these answers

Re-check this document if any of the following happens:

- an analytics, attribution, crash-reporting or A/B SDK is added — this is the
  change most likely to invalidate "collects nothing itself";
- advertising mediation is enabled, which adds other networks as recipients;
- interstitial or rewarded formats are added;
- cloud save, leaderboards or accounts are added — that is personal data the
  publisher would then hold, with deletion rights attached;
- `play-services-ads` is upgraded and merges a new permission;
- the app starts being targeted at children.

---

**Next:** [Privacy policy](PRIVACY_POLICY.md) · [Google Play release checklist](GOOGLE_PLAY_RELEASE_CHECKLIST.md) · [Advertising](wiki/Advertising.md)
