# Release signing

[← Documentation home](wiki/Home.md)

How to sign an EARTH release locally, in CI, and for Google Play — and what must
never reach this repository.

> **No keystore exists in this repository, and none was created for it.** The
> build is configured so that whoever ships the app supplies their own key
> securely. Until that happens `assembleRelease` and `bundleRelease` still
> produce artifacts; they are simply **unsigned** and cannot be installed or
> uploaded.

---

## Never commit

| | |
| :-- | :-- |
| Keystores | `*.jks`, `*.keystore`, `*.p12`, `*.pfx` |
| Key material | `*.pem`, `*.key`, any `-----BEGIN … PRIVATE KEY-----` block |
| Passwords | store password, key password, in any file, commit message, comment or CI log |
| `keystore.properties` | the local signing file |
| Service-account JSON | Play publishing credentials |
| Base64 of any of the above | encoding is not encryption |

`.gitignore` already covers `*.jks`, `*.keystore` and `keystore.properties`.
That is a safety net, not the rule — the rule is that key material lives outside
the repository entirely.

The AdMob app ID and banner unit **are** committed, deliberately. They are public
identifiers that ship inside every APK and are visible to anyone who unzips one.
Neither is a key. See [Security and privacy](wiki/Security-and-Privacy.md).

---

## 1. Create a keystore

```bash
keytool -genkeypair -v \
  -keystore earth-release.jks \
  -alias earth \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -dname "CN=<your name>, O=<your organisation or your name>, C=PT"
```

`-validity 10000` is roughly 27 years. Play requires an upload key valid past
2033; a key that expires mid-life is a support ticket, not a build failure.

> [!CAUTION]
> **Back up the keystore file and both passwords somewhere you will still have
> them in ten years.** With Play App Signing, losing the *upload* key means a
> Google support process to register a new one. Without Play App Signing, losing
> the *app signing* key means you can never update the app again — the listing is
> effectively dead and players must uninstall and reinstall from a new one.
>
> Two copies, two locations, and the passwords in a password manager rather than
> in the same place as the file.

---

## 2. Configure local signing

`app/build.gradle.kts` looks for four values, in this order, and uses the first
source that supplies them:

| Order | Source | Keys |
| --: | :-- | :-- |
| 1 | `keystore.properties` at the repository root (git-ignored) | `storeFile`, `storePassword`, `keyAlias`, `keyPassword` |
| 2 | Gradle properties (`-P…`, or `~/.gradle/gradle.properties`) | the same four names |
| 3 | Environment variables | `EARTH_KEYSTORE`, `EARTH_KEYSTORE_PASSWORD`, `EARTH_KEY_ALIAS`, `EARTH_KEY_PASSWORD` |

**`keystore.properties`** — the usual choice for a development machine:

```properties
storeFile=/absolute/path/to/earth-release.jks
storePassword=…
keyAlias=earth
keyPassword=…
```

**`~/.gradle/gradle.properties`** — outside the repository, so it cannot be
committed by accident, and shared across projects:

```properties
storeFile=/absolute/path/to/earth-release.jks
storePassword=…
keyAlias=earth
keyPassword=…
```

**Environment variables** — one-shot, and what CI uses:

```bash
EARTH_KEYSTORE=/absolute/path/to/earth-release.jks \
EARTH_KEYSTORE_PASSWORD=… \
EARTH_KEY_ALIAS=earth \
EARTH_KEY_PASSWORD=… \
  ./gradlew bundleRelease
```

All four must be present. With any of them missing, the release variant is built
**unsigned** rather than failing — a fresh clone is never blocked on secrets, and
CI on a fork still produces an artifact.

### Check it worked

```bash
./gradlew packageReleaseArtifacts
```

A signed build writes `release/EARTH-<version>-release.apk`; an unsigned one
writes `release/EARTH-<version>-release-unsigned.apk` and says so. Confirm the
signature directly:

```bash
$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs \
  release/EARTH-1.0.0-release.apk
```

---

## 3. Configure CI signing

GitHub Actions cannot hold a file, so the keystore travels as base64 in a secret
and is written to disk for the length of the job.

**Create the secret** (locally, once):

```bash
base64 -w0 earth-release.jks       # Linux
base64 earth-release.jks | tr -d '\n'   # macOS
```

Add four **repository secrets** under *Settings → Secrets and variables →
Actions*:

| Secret | Value |
| :-- | :-- |
| `EARTH_KEYSTORE_BASE64` | the base64 string above |
| `EARTH_KEYSTORE_PASSWORD` | the store password |
| `EARTH_KEY_ALIAS` | the key alias (`earth`) |
| `EARTH_KEY_PASSWORD` | the key password |

`.github/workflows/android.yml` already decodes the keystore into the runner's
temporary directory when the secret is present, exports `EARTH_KEYSTORE`, and
deletes the file afterwards. Without the secrets the same job runs and produces
unsigned artifacts, so a pull request from a fork is not blocked.

Two rules for any CI change here:

- **Never `echo` a secret**, and never pass one on a command line — a command
  line is visible in the process table and in `set -x` output. Environment
  variables only.
- **Never upload the decoded keystore as a build artifact**, including by
  globbing a directory that contains it.

---

## 4. Play App Signing

Google Play holds the *app signing key* and re-signs every upload with it. What
you hold is the *upload key* — the one created above.

| | Upload key | App signing key |
| :-- | :-- | :-- |
| Who holds it | You | Google |
| What it signs | The AAB you upload | The APKs delivered to devices |
| If lost | Support process to register a new one; the app survives | Not applicable — Google holds it |

Enrolment happens once, in Play Console, the first time an app is created. Two
routes:

- **Let Google generate the app signing key.** Upload the AAB signed with your
  upload key; Google generates and keeps the app signing key. Simplest, and the
  right default for a new app.
- **Upload your own app signing key.** Only needed to keep continuity with an
  app that was already published outside Play, which does not apply here.

Consequences worth knowing before enrolling:

- The certificate players' devices verify is **Google's**, not yours. The SHA-1
  and SHA-256 in Play Console (*Setup → App signing*) are what any external
  service — an API key restriction, a deep-link asset file — must be told about.
  The upload certificate's fingerprints are not.
- A GitHub-released APK you sign yourself has a **different signature** from the
  Play build. A player cannot install one over the other; they must uninstall
  first, which loses the save. Say so on the release page.

---

## 5. What to distribute

| Channel | Artifact | Signed with |
| :-- | :-- | :-- |
| Google Play | `EARTH-<version>-release.aab` | your upload key; Google re-signs |
| GitHub Releases / sideload | `EARTH-<version>-release.apk` | your key, and only ever yours |

Play will not accept an APK for a new app, and a player cannot install an AAB —
both artifacts are needed, which is why `packageReleaseArtifacts` produces both.

---

## If a key is exposed

Treat a keystore pushed to a public repository as compromised even if it is
force-pushed away minutes later: it is in the fork network, in caches, and in
anyone's clone.

1. **Do not** try to scrub history and carry on.
2. If the app is on Play and enrolled in Play App Signing, request an **upload
   key reset** in Play Console. The app signing key is unaffected, so players are
   unaffected.
3. If the app is distributed only outside Play, generate a new key. Existing
   installs cannot be upgraded across a signature change; the release notes have
   to tell players to uninstall and reinstall.
4. Rotate every password that was stored alongside it.

---

**Next:** [Release process](wiki/Release-Process.md) · [Google Play checklist](GOOGLE_PLAY_RELEASE_CHECKLIST.md) · [Security and privacy](wiki/Security-and-Privacy.md)
