# Release process

[← Documentation home](Home.md)

> **Current state: no release has ever been published.** No Play Store listing, no GitHub release,
> no tag. `versionCode = 1`, `versionName = "1.0.0"`. Everything below is the procedure for the
> first one.

## Versioning

In `app/build.gradle.kts`:

```kotlin
versionCode = 1        // must increase by at least 1 for every Play upload, ever
versionName = "1.0.0"  // what the player sees
```

`versionCode` is Play's identity for a build. It can never decrease and can never be reused, even
for a build that was never rolled out. `versionName` is cosmetic; semantic versioning is the
convention.

**`SAVE_VERSION` is unrelated and moves independently.** Bump it only when the save format needs a
[migration](Save-Migrations.md), and never as part of an app-version bump.

## Signing

**No keystore is committed, and none should be.** `.gitignore` covers `*.jks`, `*.keystore` and
`keystore.properties`.

```bash
keytool -genkeypair -v -keystore earth-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias earth
```

> [!CAUTION]
> **Back up the keystore and its passwords somewhere you will still have them in ten years.** Losing
> the upload key for a published app means a Play support process to reset it. Losing it before
> enrolling in Play App Signing means you cannot update the app at all.

Supply it three ways, checked in this order:

**1. `keystore.properties`** at the repository root (git-ignored):

```properties
storeFile=/abs/path/earth-release.jks
storePassword=…
keyAlias=earth
keyPassword=…
```

**2. Gradle properties** — `-PstoreFile=… -PstorePassword=…`, or `~/.gradle/gradle.properties`.

**3. Environment variables** — `EARTH_KEYSTORE`, `EARTH_KEYSTORE_PASSWORD`, `EARTH_KEY_ALIAS`,
`EARTH_KEY_PASSWORD`. This is what CI uses.

When nothing is configured the release variant still assembles, **unsigned**, so a fresh clone is
never blocked on secrets.

## Building

```bash
./gradlew bundleRelease     # app/build/outputs/bundle/release/app-release.aab   ← Play wants this
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk      ← sideloading, GitHub releases
```

The **AAB** is what Play requires. An **APK** is what a GitHub release should attach, since a player
downloading from GitHub cannot install an AAB.

## Checklist

**Before tagging**

- [ ] `./gradlew test` — all 205 pass
- [ ] `./gradlew lintDebug lintRelease` — zero issues
- [ ] `./gradlew connectedAndroidTest` on a real device, or an explicit note that it was not run
- [ ] Install the release build on a physical device and play it: first purchase, a background/
      resume cycle to check offline progress, a reset
- [ ] `versionCode` incremented, `versionName` set
- [ ] Fixtures regenerated if any shared maths changed, and `git status` clean
- [ ] Wiki pages updated for anything that moved
- [ ] `grep -rn "TODO\|FIXME" app/src/main` reviewed
- [ ] **A licence file exists** — see [GitHub metadata](../GITHUB-METADATA.md)

**Play-specific, first time only**

- [ ] Enrol in Play App Signing
- [ ] **Data safety form declares the advertising ID** — `play-services-ads` merges
      `com.google.android.gms.permission.AD_ID` into the manifest
- [ ] The GDPR/EEA consent message exists under *Privacy & messaging* in the AdMob console
- [ ] The live AdMob app ID and banner unit in `res/values/ads.xml` belong to the shipping account
- [ ] Content rating questionnaire, target audience, ads declaration (**yes, it contains ads**)
- [ ] Store listing: screenshots from a real device, feature graphic, description

**Tagging**

```bash
git tag -a v1.0.0 -m "EARTH 1.0.0"
git push origin v1.0.0
```

Then create a GitHub release for the tag and attach the **signed APK** so players can sideload it,
and update the README's "no published release yet" note.

## Never document a secret

No keystore path, password, alias or key material belongs in this repository — including in this
file, in a commit message, or in a CI log. The AdMob app ID and banner unit **are** in the
repository, deliberately: they are public identifiers that ship in every APK. Nothing else is.

See [Security and privacy](Security-and-Privacy.md).

## Rolling back

There is no downgrade path on Play: `versionCode` cannot decrease. To undo a bad release, halt the
staged rollout, then ship a **higher** `versionCode` with the fix.

If the bad release changed `SAVE_VERSION`, the fix must handle saves that already migrated — and
because `migrate` [preserves a newer save's version rather than stamping it down](Save-Migrations.md#saves-from-a-newer-build),
a player who got the bad build and then the fix will not have their save double-migrated. That is
the specific disaster that behaviour exists to prevent.

---

**Next:** [Build system](Build-System.md) · [Security and privacy](Security-and-Privacy.md)
