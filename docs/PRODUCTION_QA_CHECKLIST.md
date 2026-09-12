# Production QA checklist

[← Documentation home](wiki/Home.md)

Walk this against a **release build installed on a real device** before every
publication. Not a debug build: the debug variant has a different application ID,
different AdMob identifiers, no minification, and a forced EEA consent geography.
Nothing below tests what it needs to unless the artifact under test is the one
being shipped.

```bash
./gradlew packageReleaseArtifacts
adb install -r release/EARTH-<version>-release.apk
```

> A signed release APK cannot be installed over a debug build, and vice versa —
> different signatures and different application IDs. Uninstall first if
> switching, and remember that doing so deletes the save.

---

## Startup

- [ ] Launches from the launcher icon
- [ ] No crash, no ANR, no visible jank on the first frame
- [ ] The window background is the game's dark ground, not a white flash
- [ ] App name under the icon reads **EARTH**
- [ ] Launcher icon is the planet, at the right shape on a circular, squircle and
      rounded-square mask, and the themed (monochrome) variant is legible
- [ ] Settings → About shows the version being tested, the version code, and
      `com.earthgame.idle` — **not** `com.earthgame.idle.debug`
- [ ] First launch shows the tutorial; skipping it works

## Gameplay

*Nothing in this release work changed gameplay. These confirm that R8 did not.*

- [ ] Simulation ticks — resources rise on their own
- [ ] Production — buying a technology increases the rate; buy ×1, ×10 and Max
      all work
- [ ] Technology — the tree renders, branch filters work, requirements gate
      correctly
- [ ] Climate — temperature, CO₂ and habitability move as production grows
- [ ] Prestige — a run can be ended, Earth Points are awarded, upgrades buy
- [ ] Achievements — at least one unlocks and toasts
- [ ] Challenges — one can be started and abandoned
- [ ] Events — a random event fires and toasts; the news feed populates
- [ ] Offline progression — background the app for several minutes, return, and
      check the offline summary is plausible rather than zero or absurd
- [ ] Uninhabitable → collapse summary → reset works end to end

## Persistence

- [ ] Save — background the app, force-stop it, relaunch: progress is intact
- [ ] Load — reopening after a reboot restores the same state
- [ ] Migration — install over a build with an older `SAVE_VERSION` and confirm
      the save survives (or note that this is the first release, so there is
      nothing to migrate from)
- [ ] Corruption handling — the app recovers rather than crash-looping
      (`adb shell run-as` is not available on a release build; test this on the
      debug variant, or trust `DataStoreSaveRepositoryTest`)
- [ ] Reset — "Reset Earth" clears the run and keeps prestige progress

## Ads and consent

- [ ] **Production App ID** in the shipped build:
      `ca-app-pub-6872627319793193~7208922044`
- [ ] **Production banner unit** in the shipped build:
      `ca-app-pub-6872627319793193/5213314092`
- [ ] **No test identifiers in the release build** — `ca-app-pub-3940256099942544`
      appears nowhere (`ProductionAdConfigTest` asserts the source; verify the
      artifact too)
- [ ] UMP consent form appears on a first launch in the EEA/UK/CH
- [ ] Accepting consent → a banner appears
- [ ] Declining consent → **no banner, and the game is completely playable**
- [ ] Settings → Manage advertising privacy reopens the form
- [ ] About → Manage advertising privacy reopens the form
- [ ] A changed choice takes effect on the next launch
- [ ] Airplane mode → no banner, no crash, no hang, no error dialog; the
      simulation keeps running
- [ ] Network lost *while* the banner is showing → no crash
- [ ] Background and return several times → no duplicate consent form, no
      duplicate SDK initialization, no leak (`adb shell dumpsys meminfo`)
- [ ] Rotate the device with a banner on screen → it re-lays out, no crash
- [ ] The banner never covers the bottom navigation or a game control
- [ ] **Do not tap the live banner.** A developer's own clicks on their live unit
      are invalid traffic and repeat offences suspend the AdMob account. Use the
      debug build to test tapping.

## Display and accessibility

- [ ] Portrait on a phone (360 dp and wider)
- [ ] A tablet or unfolded foldable uses the side rail, with content width-capped
- [ ] Dark mode, light mode, and "follow system" each render every screen
- [ ] Font scale at 200% — nothing is clipped or unreadable
- [ ] Gesture navigation and 3-button navigation both leave the bottom row of the
      nav bar visible
- [ ] TalkBack announces destinations by their full names
- [ ] Reduced-animations setting is honoured

## Security and build hygiene

- [ ] No secrets in the repository (`git ls-files` shows no keystore, key or
      credential file)
- [ ] Release build is signed with the upload key, and `apksigner verify`
      confirms the expected certificate
- [ ] No debug logging in Logcat during normal play:
      `adb logcat --pid=$(adb shell pidof -s com.earthgame.idle)` should be
      quiet except for warnings from the ad SDK
- [ ] Nothing logged contains game state, consent details or an identifier
- [ ] No debug-only UI, no developer menu, no test data
- [ ] R8 is on and the minified build has been played, not merely built
- [ ] `mapping.txt` archived with the release — without it a crash report from
      Play is unreadable

## Legal

- [ ] Project licence — a `LICENSE` file exists, and About states the same
      licence *(currently a blocker; see [Release blockers](RELEASE_BLOCKERS.md))*
- [ ] Third-party licences — Settings → About → Open Source Licenses opens and
      shows the real notices
- [ ] `THIRD_PARTY_NOTICES.txt` regenerated after any dependency change
- [ ] Asset licences — every shipped asset has a known provenance
      *(currently a blocker for the launcher icon)*
- [ ] Privacy policy — no `[…]` placeholders left, reachable from About, and the
      URL matches the Play listing
- [ ] Data safety declaration re-checked against the merged manifest

## Distribution

- [ ] `release/EARTH-<version>-release.apk` produced and installs
- [ ] `release/EARTH-<version>-release.aab` produced and accepted by Play
- [ ] `versionCode` is higher than any previously uploaded build
- [ ] `versionName` matches the tag and the release notes
- [ ] `targetSdk` ≥ 36
- [ ] Play Console configuration reviewed against
      [the Play checklist](GOOGLE_PLAY_RELEASE_CHECKLIST.md)
- [ ] GitHub release created for the tag with the **APK** attached — a player
      cannot install an AAB

---

**Next:** [Google Play checklist](GOOGLE_PLAY_RELEASE_CHECKLIST.md) · [Release process](wiki/Release-Process.md) · [Testing](wiki/Testing.md)
