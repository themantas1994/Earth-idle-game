# Final device QA — EARTH 1.0.0

[← Documentation home](wiki/Home.md)

**The one test pass nothing in this repository can do for you.**

Everything EARTH claims about itself has been established by building the release
artifact and taking it apart. None of it has been established by *running* it: no
environment that has built this project has had an emulator or KVM, so the
minified release binary has never executed. That is
[blocker O6](RELEASE_BLOCKERS.md#o6-the-release-build-has-never-run-on-a-physical-device),
and this page is how it gets closed.

> [!IMPORTANT]
> **The release variant is the only minified one.** `isMinifyEnabled` and
> `isShrinkResources` are set on `release` alone, so all 220 unit tests — the
> Robolectric UI and DataStore suites included — run against unminified code. A
> debug build proves nothing about R8. **Test the signed release APK or nothing.**

Budget about 45 minutes. Tick as you go; a box you skipped is not a box that passed.

---

## Before you start

```bash
./gradlew clean
./gradlew test lintDebug lintRelease
python3 scripts/third-party-notices.py --check
./gradlew packageReleaseApk
```

You need a keystore configured first ([Release signing](RELEASE-SIGNING.md)) —
without one the artifact is named `-unsigned` and cannot be installed at all.

```bash
sha256sum -c release/SHA256SUMS.txt
adb install -r release/EARTH-1.0.0-release.apk
```

> A release APK cannot be installed over a debug build: different signature,
> different application ID (`com.earthgame.idle` vs `com.earthgame.idle.debug`).
> Uninstall the debug build first — **which deletes its save.**

Keep `release/EARTH-1.0.0-release-mapping.txt`. If anything below crashes, that
file is the only thing that makes the stack trace readable:

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/retrace \
  release/EARTH-1.0.0-release-mapping.txt crash.txt
```

---

## INSTALL

- [ ] **1.** Install the release APK. It installs without a signature or
      compatibility error.
- [ ] **2.** Launch it from the launcher icon. It reaches the game with no crash,
      no ANR, and no white flash before the first frame.
- [ ] **3.** Settings → About shows **version 1.0.0**, **version code 1**, and the
      package **`com.earthgame.idle`** — *not* `…idle.debug`, *not* `1.0.0-debug`.
      If you see either, you are testing the wrong build; stop and reinstall.

## FIRST RUN

- [ ] **4. UMP.** In the EEA, the UK or Switzerland, a Google consent message
      appears **before any advertisement**. Outside those regions it correctly
      does not. **If no form appears where one should, that is
      [O5](RELEASE_BLOCKERS.md#o5-no-consent-message-exists-in-the-admob-console) —
      the AdMob console message is missing or unpublished, not an app bug.** To
      exercise the flow outside the EEA, use a debug build, which forces UMP's EEA
      debug geography.
- [ ] **5. Privacy options.** After a consent message was shown, **Settings →
      Manage advertising privacy** and **Settings → About → Manage advertising
      privacy** both appear and both reopen the form. Where no message was shown,
      both rows are correctly absent rather than present-and-dead.
- [ ] **6. Banner.** Accept consent → a banner appears at the bottom, **above** the
      navigation bar and never overlapping it. Decline → **no banner at all**, and
      the game is completely playable. **Do not tap a live banner** — your own
      clicks are invalid traffic and repeat offences suspend the AdMob account.

## GAMEPLAY

*Nothing in the release work changed gameplay. These confirm R8 did not either.*

- [ ] **7.** Start a game. Resources rise on their own; the tick is smooth.
- [ ] **8.** Buy a technology. Buy ×1, ×10 and Max; costs and affordability behave.
- [ ] **9.** Production rate increases after a purchase, on the header and on the
      Production screen.
- [ ] **10.** Climate responds: temperature, CO₂ and habitability move as
      production grows. The Atmosphere screen renders.
- [ ] **11.** Prestige: end a run, Earth Points are awarded, prestige upgrades buy
      and apply.
- [ ] **12.** Save: the game persists without you doing anything (step 19 proves it).

## LIFECYCLE

- [ ] **13.** Background the app with the home gesture.
- [ ] **14.** Wait at least 5 minutes.
- [ ] **15.** Return to it.
- [ ] **16.** The offline summary appears and its numbers are **plausible** —
      neither zero nor absurd for the time elapsed.
- [ ] **17.** Force-close: `adb shell am force-stop com.earthgame.idle`.
- [ ] **18.** Relaunch.
- [ ] **19.** State is intact — resources, technologies, prestige, settings.
      **This is the single most important check on this page**: it is the one that
      exercises `SaveSerialization` and DataStore through R8.

## SETTINGS

- [ ] **20.** Dark, light and "follow system" each render every screen legibly.
- [ ] **21.** Haptics: a short pulse on a purchase, a heavier one when an Earth
      ends. Toggling vibration off silences both.
- [ ] **22.** Audio: the Sound Effects and Music toggles flip and persist.
      *(Expected: nothing audible — no sound files ship. See
      [L2](RELEASE_BLOCKERS.md#l2-soundpoolaudio-is-a-working-stub).)*
- [ ] **23.** Privacy options — as step 5.
- [ ] **24.** About — as step 3, plus: the **Project licence** row, the privacy
      policy link and the repository link all open. **No placeholder legal text is
      presented as real.**
- [ ] **25.** About → **Open Source Licenses**: the screen opens, entries load,
      scrolling is smooth to the end, the attribution text is readable, and it
      does **not** show "The third-party notices could not be read from this
      build." That message means the asset is missing from the release build.

## NETWORK

- [ ] **26.** With network: the banner loads (consent permitting).
- [ ] **27.** Airplane mode, then launch: **no banner, no crash, no hang, no error
      dialog.** The simulation runs normally.
- [ ] **28.** Airplane mode *while* a banner is on screen: no crash; the space is
      given back. Restore the network: the game is unaffected either way.

## ADS

- [ ] **29.** The banner is served from the **production** unit
      `ca-app-pub-6872627319793193/5213314092` — confirm in the AdMob dashboard
      that requests are arriving, not by tapping.
- [ ] **30.** Consent behaviour: a changed choice in the privacy options form takes
      effect **on the next launch** (this is how UMP works, not a bug). Backgrounding
      and returning several times shows no duplicate form and no duplicate SDK
      initialisation.
- [ ] **31.** A failed ad request does not break the game: no crash, no blank
      strip left behind, no effect on the simulation.

## SYSTEM

- [ ] **32.** Android Back: unwinds screens, and from Home it leaves the game
      rather than dropping into an empty stack.
- [ ] **33.** Rotate the device with a banner on screen. **The banner re-lays out
      at the new width and is still there** — it does not go blank. *(This is the
      exact path [H8](RELEASE_BLOCKERS.md#h8-the-banner-was-requested-during-composition-and-went-blank-on-a-resize--fixed)
      fixed, and it has never been observed on hardware.)* No crash, and the game
      state survives — the activity handles the configuration change itself.
- [ ] **34.** Leave it running in the background for a while on a low battery, and
      under battery saver. Return: no crash, and offline progression still resolves.

## Display and accessibility

- [ ] **35.** A tablet or unfolded foldable uses the side rail with content
      width-capped.
- [ ] **36.** Font scale at 200%: nothing clipped, nothing unreadable.
- [ ] **37.** Gesture and 3-button navigation both leave the bottom row visible.
- [ ] **38.** TalkBack announces destinations by their full names.

## Build hygiene, on the device

- [ ] **39.** `adb logcat --pid=$(adb shell pidof -s com.earthgame.idle)` is quiet
      during normal play, except for warnings from the ad SDK. **Nothing logged
      contains game state, consent details or any identifier.**
- [ ] **40.** No debug-only UI, no developer menu, no test data anywhere.
- [ ] **41.** `apksigner verify --print-certs` on the installed APK shows the
      certificate you expect.

## Instrumented tests

Neither suite has ever run. Both need a device:

```bash
./gradlew connectedAndroidTest
```

- [ ] **42.** `DataStoreSaveRepositoryTest` passes — real DataStore persistence and
      corruption recovery.
- [ ] **43.** `EarthAppUiTest` passes — the Compose UI on a real device.

---

## Recording the result

A pass is only useful if it is written down. Note the device, the Android version,
the artifact's SHA-256 from `release/SHA256SUMS.txt`, and anything that failed.

**If everything above passes**, O6 can be closed —
[update the blockers](RELEASE_BLOCKERS.md) with the device and date rather than
deleting the entry. **If anything fails**, it is a release blocker until it does
not: this is the last gate before other people run this code.

---

**Next:** [Production QA checklist](PRODUCTION_QA_CHECKLIST.md) · [Release blockers](RELEASE_BLOCKERS.md) · [Google Play checklist](GOOGLE_PLAY_RELEASE_CHECKLIST.md)
