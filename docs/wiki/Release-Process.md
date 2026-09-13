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

The full procedure, written for GitHub distribution, is
**[Release signing](../RELEASE-SIGNING.md)**. The short version:

```bash
keytool -genkeypair -v -keystore earth-release-key.jks \
  -keyalg RSA -keysize 4096 -validity 10000 -alias earth-release
```

Supply it three ways, checked in this order: `keystore.properties` at the repository root, Gradle
properties, then the `EARTH_KEYSTORE` / `EARTH_KEYSTORE_PASSWORD` / `EARTH_KEY_ALIAS` /
`EARTH_KEY_PASSWORD` environment variables.

> [!CAUTION]
> **Back up the keystore and its passwords somewhere you will still have them in ten years, and
> sign every future release with the same key.** EARTH is distributed as a self-signed APK, so
> this key *is* the app signing key — nobody else holds a copy. Losing it means you can never
> update the app again: players would have to uninstall, which deletes their save. See
> [Release signing §4](../RELEASE-SIGNING.md#4-back-up-the-keystore).

When nothing is configured the release variant still assembles, **unsigned**, so a fresh clone is
never blocked on secrets — and the artifacts are named `-unsigned` so nobody mistakes them for
distributable ones.

## Building

The release target is a **signed APK on GitHub Releases**:

```bash
./gradlew clean packageReleaseApk
```

That runs `assembleRelease` and collects the results into `release/`:

| Artifact | Path | For |
| :-- | :-- | :-- |
| Release APK | `release/EARTH-<version>-release.apk` | **The GitHub release. What a player installs** |
| R8 mapping | `release/EARTH-<version>-release-mapping.txt` | Reading crash reports. Keep it; do not attach it |
| Checksums | `release/SHA256SUMS.txt` | So a download can be verified |

`./gradlew packageReleaseArtifacts` does the same and additionally builds the AAB
(`release/EARTH-<version>-release.aab`). A player cannot install an AAB and a GitHub release does
not need one, so it is only worth the extra R8 pass if Play is also in play — see
[Google Play](Google-Play.md).

Each task clears this version's previous artifacts from `release/` before it writes, so a signed
build never sits next to a stale `-unsigned` one.

`release/` is git-ignored. A 20 MB binary in a repository is painful to undo, and every artifact
is reproducible from a tag.

The underlying outputs stay where AGP puts them (`app/build/outputs/apk/release/`,
`app/build/outputs/bundle/release/`) if you want them there.

## Checklist

**Before tagging**

- [ ] `./gradlew test` — all 220 pass
- [ ] `./gradlew lintDebug lintRelease` — zero issues
- [ ] `python3 scripts/third-party-notices.py --check` — every shipped module is attributed
- [ ] `./gradlew connectedAndroidTest` on a real device, or an explicit note that it was not run
- [ ] **[Final device QA](../FINAL_DEVICE_QA.md) walked on a release build, on a physical
      device** — not a debug build; it has a different application ID, different AdMob
      identifiers and no minification. The release variant is the only minified one, so this
      is the only thing that has ever run R8's output. See also the longer
      [Production QA checklist](../PRODUCTION_QA_CHECKLIST.md)
- [ ] `earthVersionCode` incremented, `earthVersionName` set
- [ ] Fixtures regenerated if any shared maths changed, and `git status` clean
- [ ] Wiki pages updated for anything that moved
- [ ] `grep -rn "TODO\|FIXME" app/src/main` reviewed
- [ ] **A licence file exists** — see [Licensing](Licensing.md)
- [ ] [Release blockers](../RELEASE_BLOCKERS.md) has no open [technical blocker](../RELEASE_BLOCKERS.md#technical-blockers),
      and every [owner requirement](../RELEASE_BLOCKERS.md#owner--distribution-requirements) marked
      *blocking* is closed
- [ ] `apksigner verify --print-certs` on the built APK reports `Verifies`, with the same
      certificate digest as every previous release

**Not required for a GitHub release.** These are Play-only, kept for if Play is ever added; the
full list is [Google Play release checklist](../GOOGLE_PLAY_RELEASE_CHECKLIST.md). The one
exception is the AdMob consent message, which is an AdMob-console matter rather than a Play one and
[applies either way](../RELEASE_BLOCKERS.md#o5-no-consent-message-exists-in-the-admob-console):

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

Then create a GitHub release for the tag, attach the **signed APK** and `SHA256SUMS.txt` so players can
install it and check what they downloaded, and update the README's "no published release yet" note.
[GitHub release template](../GITHUB_RELEASE_TEMPLATE.md) is the page to copy, including the
installation instructions a sideloading player needs;
[Release notes template](../RELEASE_NOTES_TEMPLATE.md) is the longer draft behind it.

**Keep the R8 mapping** with every release. Without it a crash report from a minified build is
unreadable. Both packaging tasks write it to `release/EARTH-<version>-release-mapping.txt`
alongside the artifacts, with a `SHA256SUMS.txt` covering everything produced; the original stays where AGP put it
at `app/build/outputs/mapping/release/mapping.txt`. Keep the mapping, but do not attach it to a
public release.

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
