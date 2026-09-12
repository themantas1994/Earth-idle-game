# Release process

[← Documentation home](Home.md)

> **Current state: no release has ever been published.** No Play Store listing, no GitHub release,
> no tag. `versionCode = 1`, `versionName = "1.0.0"`. Everything below is the procedure for the
> first one — and [Release blockers](../RELEASE_BLOCKERS.md) lists what still stands in its way.

## Versioning

Both numbers live in one place, at the top of `app/build.gradle.kts`:

```kotlin
val earthVersionCode = 1        // Play's identity for a build
val earthVersionName = "1.0.0"  // what the player sees
```

`defaultConfig` reads them, and so does `packageReleaseArtifacts`, so a build cannot be filed under
a version it does not carry.

### The rules

**`versionCode` must increase by at least one for every upload, ever.** It can never decrease and
can never be reused, even for a build that was never rolled out. One increment per upload — not
per release, per *upload* — is the habit that avoids "you already used that version code" at the
worst possible moment.

**`versionName` is cosmetic and follows semantic versioning:**

| Change | Bump | Example |
| :-- | :-- | :-- |
| A bug fix, a doc change, a dependency patch | PATCH | 1.0.0 → 1.0.1 |
| New content, a new screen, new settings — save-compatible | MINOR | 1.0.1 → 1.1.0 |
| A save format that old builds cannot read, or gameplay that invalidates existing runs | MAJOR | 1.1.0 → 2.0.0 |

A pre-release goes out as `1.1.0-beta1` with its own `versionCode`. Play sorts by `versionCode`
alone; the name is for humans.

**`SAVE_VERSION` is unrelated and moves independently.** Bump it only when the save format needs a
[migration](Save-Migrations.md), and never as part of an app-version bump.

## Signing

**No keystore is committed, and none should be.** `.gitignore` covers `*.jks`, `*.keystore` and
`keystore.properties`, but that is a safety net rather than the rule — key material lives outside
the repository entirely.

The full procedure, including CI and Play App Signing, is
**[Release signing](../RELEASE-SIGNING.md)**. The short version:

```bash
keytool -genkeypair -v -keystore earth-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias earth
```

Supply it three ways, checked in this order: `keystore.properties` at the repository root, Gradle
properties, then the `EARTH_KEYSTORE` / `EARTH_KEYSTORE_PASSWORD` / `EARTH_KEY_ALIAS` /
`EARTH_KEY_PASSWORD` environment variables.

> [!CAUTION]
> **Back up the keystore and its passwords somewhere you will still have them in ten years.**
> Losing the upload key for a published app means a Play support process. Losing it before
> enrolling in Play App Signing means you cannot update the app at all.

When nothing is configured the release variant still assembles, **unsigned**, so a fresh clone is
never blocked on secrets — and the artifacts are named `-unsigned` so nobody mistakes them for
distributable ones.

## Building

```bash
./gradlew packageReleaseArtifacts
```

That runs `assembleRelease` and `bundleRelease` and collects both into `release/`:

| Artifact | Path | For |
| :-- | :-- | :-- |
| Release APK | `release/EARTH-<version>-release.apk` | GitHub releases, sideloading |
| Play bundle | `release/EARTH-<version>-release.aab` | Play Store uploads |

`release/` is git-ignored. A 20 MB binary in a repository is painful to undo, and both artifacts
are reproducible from a tag.

The underlying outputs stay where AGP puts them (`app/build/outputs/apk/release/`,
`app/build/outputs/bundle/release/`) if you want them there.

## Checklist

**Before tagging**

- [ ] `./gradlew test` — all 215 pass
- [ ] `./gradlew lintDebug lintRelease` — zero issues
- [ ] `python3 scripts/third-party-notices.py --check` — every shipped module is attributed
- [ ] `./gradlew connectedAndroidTest` on a real device, or an explicit note that it was not run
- [ ] **[Production QA checklist](../PRODUCTION_QA_CHECKLIST.md) walked on a release build,
      on a physical device** — not a debug build; it has a different application ID, different
      AdMob identifiers and no minification
- [ ] `earthVersionCode` incremented, `earthVersionName` set
- [ ] Fixtures regenerated if any shared maths changed, and `git status` clean
- [ ] Wiki pages updated for anything that moved
- [ ] `grep -rn "TODO\|FIXME" app/src/main` reviewed
- [ ] **A licence file exists** — see [Licensing](Licensing.md)
- [ ] [Release blockers](../RELEASE_BLOCKERS.md) has no open CRITICAL items

**Play-specific, first time only** — the full list is
[Google Play release checklist](../GOOGLE_PLAY_RELEASE_CHECKLIST.md):

- [ ] Enrol in Play App Signing
- [ ] **Data safety form** submitted, from [the prepared answers](../GOOGLE_PLAY_DATA_SAFETY.md)
- [ ] The GDPR/EEA consent message exists under *Privacy & messaging* in the AdMob console —
      **without it, EEA/UK players see no banner at all**
- [ ] Privacy policy hosted, placeholders filled in, URL entered
- [ ] Content rating questionnaire, target audience, ads declaration (**yes, it contains ads**)
- [ ] Store listing: screenshots from a real device, 512×512 icon, feature graphic, description

**Tagging**

```bash
git tag -a v1.0.0 -m "EARTH 1.0.0"
git push origin v1.0.0
```

Then create a GitHub release for the tag, attach the **signed APK** so players can sideload it, and
update the README's "no published release yet" note.

**Keep `app/build/outputs/mapping/release/mapping.txt`** with every release. Without it a crash
report from a minified build is unreadable.

## Never document a secret

No keystore path, password, alias or key material belongs in this repository — including in this
file, in a commit message, or in a CI log. The AdMob app ID and banner unit **are** in the
repository, deliberately: they are public identifiers that ship in every APK. Nothing else is.

See [Security and privacy](Security-and-Privacy.md) and [Release signing](../RELEASE-SIGNING.md).

## Rolling back

There is no downgrade path on Play: `versionCode` cannot decrease. To undo a bad release, halt the
staged rollout, then ship a **higher** `versionCode` with the fix.

If the bad release changed `SAVE_VERSION`, the fix must handle saves that already migrated — and
because `migrate` [preserves a newer save's version rather than stamping it down](Save-Migrations.md#saves-from-a-newer-build),
a player who got the bad build and then the fix will not have their save double-migrated. That is
the specific disaster that behaviour exists to prevent.

---

**Next:** [Google Play](Google-Play.md) · [Release signing](../RELEASE-SIGNING.md) · [Build system](Build-System.md)
