# Privacy Policy — EARTH

**Application:** EARTH (`com.earthgame.idle`)
**Version this describes:** 1.0.0
**Last updated:** *[OWNER ACTION REQUIRED — DATE, set when this is first published]*

> [!IMPORTANT]
> **OWNER ACTION REQUIRED**
>
> **Three values in this policy are placeholders and must be filled in before the
> app is published:** the data controller's name (§2, §14), the contact address
> (§2, §14), and the "Last updated" date above.
>
> They are **not** filled in because this repository records no legal entity and no
> contact address, and inventing either would be worse than leaving the gap
> visible. Only the owner can supply them. Tracked as
> [O3](RELEASE_BLOCKERS.md#o3-the-privacy-policy-contains-placeholders).
>
> A privacy notice naming no controller and offering no contact does not satisfy
> GDPR Articles 13–14. That is true regardless of how the app is distributed:
> EARTH is published as a signed APK on GitHub Releases rather than through a
> store, which removes the store's requirement for a policy URL but not the legal
> requirement for the policy itself. The app links to this file from
> **Settings → About**, so it is the app's actual published privacy notice.
>
> This document is an accurate technical description of what the application
> does, written from its source code. **It is not legal advice**, and it has not
> been reviewed by a lawyer. Whoever publishes the app is the data controller and
> is responsible for the final wording.

---

## 1. In short

EARTH is an offline, single-player idle game. Your game progress is stored on
your own device and is never sent anywhere.

The app shows **one banner advertisement**, supplied by **Google AdMob**. That
advertisement is the only part of the app that uses the internet, and it is the
only reason any data about your device is processed at all. In the European
Economic Area, the United Kingdom and Switzerland you are asked for consent
before any advertisement is requested, and you can change your answer at any time
from **Settings → Manage advertising privacy**.

There is no account, no login, no cloud save, no analytics, no telemetry and no
crash reporting.

---

## 2. Who is responsible

| | |
| :-- | :-- |
| Data controller | *[OWNER ACTION REQUIRED — NAME OR LEGAL ENTITY OF THE PUBLISHER]* |
| Contact | *[OWNER ACTION REQUIRED — CONTACT EMAIL ADDRESS]* |
| Source code | https://github.com/themantas1994/Earth-idle-game |

For anything relating to the advertisement itself, Google acts as a separate
controller or as a processor depending on your consent choices; see
[Google's own disclosures](#7-google-as-a-third-party).

---

## 3. What the app stores on your device

EARTH keeps one save file in its private application storage:

```
/data/data/com.earthgame.idle/files/datastore/earth-save.preferences_pb
```

It contains only game state — resources produced, technologies bought,
atmospheric values, prestige points, achievements, challenge progress, the
in-game clock readings used for offline progress, and your settings (number
format, theme, sound, vibration, offline progress, confirm-before-reset).

It contains **no name, no email address, no account identifier, no advertising
identifier, and no device identifier**, and it is never transmitted anywhere.
Other applications cannot read it.

**Android Auto Backup.** If you have Android's own backup switched on, the system
may include this save in your device backup to your Google account. That is
Android's feature and is governed by Google's terms, not by the app. You can
switch it off in Android's settings. Nothing in the app initiates it.

**Deleting it.** Uninstalling EARTH, or clearing its data in Android's app
settings, deletes the save permanently. There is no copy anywhere else to
request the deletion of.

---

## 4. What the app sends over the internet

**The game itself sends nothing.** Every number is computed on the device. The
simulation, saving, loading and offline progression work with no network access
at all, in flight mode, forever.

**The banner advertisement** makes network requests through Google's Mobile Ads
SDK. Those requests are made by Google's software, and they carry what Google's
software carries, which typically includes:

- your device's **advertising ID** (a resettable identifier Android provides for
  advertising, unless you have limited or reset it),
- your **IP address**, from which an approximate location may be inferred,
- **device and app information** — device model, operating-system version,
  screen size, language, the app's package name and version,
- **your consent choices**, as recorded by Google's consent tool.

**The consent form** is downloaded from Google when it needs to be shown.

The app declares `INTERNET` and `ACCESS_NETWORK_STATE` permissions for these
requests and for nothing else.

---

## 5. Permissions

| Permission | Why | In the app's own manifest |
| :-- | :-- | :-- |
| `INTERNET` | The advertisement and the consent form | Yes |
| `ACCESS_NETWORK_STATE` | The advertising SDK checks connectivity | Yes |
| `VIBRATE` | A short pulse on a purchase; a heavier one when a planet ends | Yes |
| `com.google.android.gms.permission.AD_ID` | Access to the advertising ID | Added by the Google Mobile Ads SDK |
| `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_TOPICS` | Android Privacy Sandbox advertising APIs | Added by the Google Mobile Ads SDK |
| `WAKE_LOCK`, `FOREGROUND_SERVICE` | Background scheduling used internally by libraries the advertising SDK depends on. The app itself schedules no background work and runs no foreground service | Added by those libraries |
| `com.earthgame.idle.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | A private, signature-level permission Android's own support libraries use to keep their internal broadcasts unreachable by other apps | Added by those libraries |

There is **no** access to location, camera, microphone, contacts, calendar,
photos, files, SMS, call logs or notifications.

**App visibility.** The advertising SDK and the browser library it uses declare a
narrow `<queries>` list, which lets them find an app that can open a web page
(for an advertisement's landing page, and for the consent form), and the Play
Store by name. The application cannot enumerate the apps installed on your
device — it does not hold `QUERY_ALL_PACKAGES` — and it does not read, record or
transmit any list of them.

---

## 6. Advertising and your consent

EARTH shows a single banner at the bottom of the screen. There are no
interstitials, no rewarded advertisements, and nothing in the game is locked
behind watching one. If the advertisement never loads — no network, no fill, or
consent declined — the game plays exactly the same.

**In the EEA, the UK and Switzerland**, before any advertisement is requested the
app shows a consent message built with **Google's User Messaging Platform (UMP)**,
a Google-certified consent management platform operating within the IAB
Transparency & Consent Framework. The message discloses the purposes and the
advertising partners involved and lets you accept or refuse each of them.

- **No advertisement is requested until that flow has completed and reports that
  a request is permitted.** If it reports otherwise — you declined, or the form
  could not be loaded — nothing is requested and no banner is shown.
- **Whether the advertisements you see are personalised depends on your answers**,
  which Google's consent tool records and Google's advertising software reads.
- **You can change or withdraw your consent at any time** from
  **Settings → Manage advertising privacy**, or from **Settings → About →
  Manage advertising privacy**. The new choice applies from the next launch.

**Outside those regions**, local law and Google's policies apply. Android's own
controls still apply everywhere: *Settings → Privacy → Ads* lets you reset or
delete your advertising ID, which limits the personalisation Google can perform.

---

## 7. Google as a third party

Google Ireland Limited / Google LLC processes the data described in section 4 for
advertising. Their handling of it is governed by their own terms, not by this
policy:

- Google Privacy Policy — https://policies.google.com/privacy
- How Google uses information from sites or apps that use its services —
  https://policies.google.com/technologies/partner-sites
- Google Advertising Policies — https://policies.google.com/technologies/ads

Google is the **only** third party the application communicates with. There are
no analytics providers, no crash-reporting services, no attribution or
measurement SDKs, no social-network SDKs and no mediation with other advertising
networks.

---

## 8. Legal bases (EEA/UK)

| Processing | Basis |
| :-- | :-- |
| Storing your game progress on your own device | Not personal data processing by the publisher — nothing leaves the device |
| Requesting an advertisement, personalised | Your **consent**, gathered through the UMP consent message |
| Requesting an advertisement, non-personalised | The purposes you consented to, or, where the framework allows it and you have not objected, legitimate interests — as disclosed in the consent message itself |
| Showing the consent message | Compliance with a legal obligation |

The consent message is the authoritative disclosure of purposes and partners for
advertising, because it is generated from the configuration in the AdMob console
and can change without an app update.

---

## 9. Retention

| Data | Kept | By whom |
| :-- | :-- | :-- |
| Your save file | Until you delete the app's data or uninstall it | You, on your device |
| Your consent choices | Until you change them, clear the app's data, or uninstall | Stored locally by the UMP SDK |
| Advertising data | According to Google's retention policies | Google |

The publisher holds no copy of anything and therefore has nothing to retain,
export or delete.

---

## 10. Your rights

Because the publisher operates no server and holds no personal data, the most
effective controls are the ones on your own device:

- **Erase everything the app holds:** uninstall it, or clear its data in
  Android's app settings.
- **Change your advertising consent:** Settings → Manage advertising privacy.
- **Reset or delete your advertising ID:** Android Settings → Privacy → Ads.

For data Google holds, exercise your rights with Google using
https://policies.google.com/privacy. If you are in the EEA or the UK you also
have rights of access, rectification, erasure, restriction, portability and
objection, and a right to complain to your national supervisory authority. To
raise any of this with the publisher, use the contact in section 2.

---

## 11. Children

EARTH is **not directed at children**, and it has no age gate: it does not ask
your age and does not try to infer it. The application is therefore not
configured as child-directed for advertising purposes, and it does not tag
requests as being from a user under the applicable age of consent, because it
has no truthful basis on which to do so.

The store listing should declare the target audience as **13+ / not primarily
child-directed**, consistent with this. If the publisher decides to target
children, that is a different app configuration — it requires an age screen and
the child-directed and under-age-of-consent settings in the advertising SDK — and
this policy would have to change with it.

No personal information is knowingly collected from children. If you believe a
child has provided information through the advertising described above, contact
Google using the links in section 7, and the publisher using section 2.

---

## 12. Security

The save is held in Android's private application storage, which other apps
cannot read. It is not additionally encrypted: it contains only game progress,
and nothing in it would be of use to anyone. All network traffic is made by
Google's SDK over HTTPS; the app enables no cleartext traffic and loads no code
at runtime.

---

## 13. Changes to this policy

Material changes will be published in this file in the repository, with the
"Last updated" date changed, and — where required — surfaced in the app or on the
[GitHub release page](GITHUB_RELEASE_TEMPLATE.md) before the change takes effect.
The app is distributed as a signed APK on GitHub Releases, so the repository is
the authoritative copy of this policy and the app links to it directly.

---

## 14. Contact

> **OWNER ACTION REQUIRED.** Both values below are placeholders. See the notice at
> the top of this policy.

*[OWNER ACTION REQUIRED — CONTACT EMAIL ADDRESS]*

*[OWNER ACTION REQUIRED — NAME OR LEGAL ENTITY OF THE PUBLISHER]*

Source: https://github.com/themantas1994/Earth-idle-game

---

*This policy describes EARTH 1.0.0 as built from this repository. The behaviour
it documents was read from the source; see
[Advertising](wiki/Advertising.md) and
[Security and privacy](wiki/Security-and-Privacy.md) for the implementation.*
