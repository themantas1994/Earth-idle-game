# Release signing

[← Documentation home](wiki/Home.md)

How to sign the EARTH release APK that is published on **GitHub Releases**, and
what must never reach this repository.

> **No keystore exists in this repository, and none was created for it.** The
> build is configured so that whoever ships the app supplies their own key
> securely. Until that happens `assembleRelease` still produces an artifact; it
> is simply **unsigned**, it is named `-unsigned` to say so, and Android will
> refuse to install it.

EARTH is distributed as a **signed APK attached to a GitHub release**. There is
no Play Store listing, no Play Console account and no Play App Signing in this
path: the key created below is the app signing key, held by the owner, and it is
the one every device verifies. That makes backing it up more important here than
it would be on Play, not less — see [§4](#4-back-up-the-keystore).

---

## Never commit

| | |
| :-- | :-- |
| Keystores | `*.jks`, `*.keystore`, `*.p12`, `*.pfx` |
| Key material | `*.pem`, `*.key`, any `-----BEGIN … PRIVATE KEY-----` block |
| Passwords | store password, key password, in any file, commit message, comment or CI log |
| `keystore.properties` | the local signing file |
| Base64 of any of the above | encoding is not encryption |

`.gitignore` already covers `*.jks`, `*.keystore` and `keystore.properties`.
That is a safety net, not the rule — the rule is that key material lives outside
the repository entirely.

The AdMob app ID and banner unit **are** committed, deliberately. They are public
identifiers that ship inside every APK and are visible to anyone who unzips one.
Neither is a key. See [Security and privacy](wiki/Security-and-Privacy.md).

---

## 1. Create the release keystore

Run this **once**, on a machine you control, in a directory outside this
repository:

```bash
keytool -genkeypair -v \
  -keystore earth-release-key.jks \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -alias earth-release
```

`keytool` then prompts for the store password, the key password and a
distinguished name. Answer the DN with a real name or organisation — it is
embedded in the certificate and visible to anyone who inspects the APK.

| Flag | Why this value |
| :-- | :-- |
| `-keyalg RSA -keysize 4096` | RSA is what Android's APK Signature Scheme v2/v3 expects. 4096 bits is comfortably beyond what is needed for the key's lifetime; 2048 is also acceptable and produces a marginally smaller signature block |
| `-validity 10000` | Roughly 27 years. A certificate that expires is a certificate you cannot ship an update under — Android refuses an update signed by a different certificate, expiry included |
| `-alias earth-release` | The name the key is addressed by inside the store. Whatever you choose here is what `keyAlias` must be set to below |

Do **not** put the file inside the repository working tree, even temporarily.

---

## 2. Configure local signing

`app/build.gradle.kts` looks for four values, in this order, and uses the first
source that supplies them:

| Order | Source | Keys |
| --: | :-- | :-- |
| 1 | `keystore.properties` at the repository root (git-ignored) | `storeFile`, `storePassword`, `keyAlias`, `keyPassword` |
| 2 | Gradle properties (`-P…`, or `~/.gradle/gradle.properties`) | the same four names |
| 3 | Environment variables | `EARTH_KEYSTORE`, `EARTH_KEYSTORE_PASSWORD`, `EARTH_KEY_ALIAS`, `EARTH_KEY_PASSWORD` |

**`keystore.properties`** — the usual choice for a development machine. Create it
at the repository root; `.gitignore` already covers it:

```properties
storeFile=/absolute/path/to/earth-release-key.jks
storePassword=…
keyAlias=earth-release
keyPassword=…
```

Use an **absolute** path for `storeFile`. A relative one is resolved against
`app/`, which is rarely what anyone means, and points at the repository — which
is where the keystore must not be.

**`~/.gradle/gradle.properties`** — outside the repository, so it cannot be
committed by accident, and shared across projects:

```properties
storeFile=/absolute/path/to/earth-release-key.jks
storePassword=…
keyAlias=earth-release
keyPassword=…
```

**Environment variables** — one-shot, and what CI uses:

```bash
EARTH_KEYSTORE=/absolute/path/to/earth-release-key.jks \
EARTH_KEYSTORE_PASSWORD=… \
EARTH_KEY_ALIAS=earth-release \
EARTH_KEY_PASSWORD=… \
  ./gradlew packageReleaseApk
```

All four must be present. With any of them missing, the release variant is built
**unsigned** rather than failing — a fresh clone is never blocked on secrets, and
CI on a fork still produces an artifact.

---

## 3. Protect the keystore

The key is the app's identity. Anyone holding the file **and** its passwords can
publish an APK that Android will install as an update over a player's copy of
EARTH, inheriting its save data and its permissions. Treat it as you would a
production credential.

| | |
| :-- | :-- |
| Where the file lives | Outside every git working tree. Not in `~/Downloads`, not in a directory that syncs to a shared drive by default |
| File permissions | `chmod 600 earth-release-key.jks` |
| Passwords | A password manager. Not in the same folder as the keystore, not in a note beside it, and never in this repository |
| Store vs key password | They may be the same; keeping them distinct means a leaked store password alone is not enough |
| Who holds it | As few people as possible. There is no revocation for an Android signing key |

---

## 4. Back up the keystore

> [!CAUTION]
> **THE KEY MUST BE BACKED UP. Back up the keystore file and both passwords
> somewhere you will still have them in ten years.**
>
> **Every future release of EARTH must be signed with this same key.** Android
> identifies an app by `applicationId` *and* signing certificate. An APK signed
> with a different key is not an update — it is a different app, and the system
> refuses to install it over the existing one.
>
> **Losing this keystore means you can never update EARTH again.** Players would
> have to uninstall — losing every save, which is stored only on the device — and
> install a fresh app signed with the new key. There is no recovery process and
> no support channel to appeal to: outside Play App Signing, nobody else holds a
> copy.

A backup that satisfies this:

- **Two copies, two locations**, at least one of them offline (an encrypted USB
  drive, an encrypted archive in separate cloud storage).
- **The passwords in a password manager**, whose own recovery you have tested —
  and not in the same place as the keystore file.
- **A note of the alias** (`earth-release`) alongside them. A keystore whose
  alias nobody remembers is recoverable, but only the hard way.
- **Verify the backup restores** before publishing the first release: copy it to
  a scratch directory, point `keystore.properties` at the copy, and build.

---

## 5. Build the signed APK

With signing configured, the one command that produces the GitHub release
artifact:

```bash
./gradlew clean packageReleaseApk
```

It runs `assembleRelease` (R8, resource shrinking, signing) and collects the
results into `release/`:

| File | What it is |
| :-- | :-- |
| `EARTH-<version>-release.apk` | **The artifact to attach to the GitHub release.** |
| `EARTH-<version>-release-mapping.txt` | R8's symbol map for this exact build. **Keep it; do not attach it.** Without it a crash report from this APK is unreadable |
| `SHA256SUMS.txt` | Checksums covering both, in the format `sha256sum -c` reads |

A **signed** build is named `EARTH-<version>-release.apk`. An **unsigned** one is
named `EARTH-<version>-release-unsigned.apk` and the build says so on the console.
The filename is the build telling the truth about itself — if you see
`-unsigned`, signing is not configured, and there is nothing to publish yet.

`./gradlew packageReleaseArtifacts` does the same and additionally builds the AAB.
A GitHub release does not need one — a player cannot install an AAB — so it is
only worth the extra R8 pass if you also want the Play upload archived alongside
the tag. See [Google Play](wiki/Google-Play.md) if that becomes the plan.

---

## 6. Verify the signature

**Never publish an APK whose signature you have not verified.** `BUILD
SUCCESSFUL` is not evidence: the build succeeds when it produces an unsigned
artifact too.

```bash
$ANDROID_HOME/build-tools/<version>/apksigner verify --verbose --print-certs \
  release/EARTH-<version>-release.apk
```

What a correctly signed release APK reports — this is the real output from a
verification build of 1.0.0, not an idealised one:

```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v4 scheme (APK Signature Scheme v4): false
Number of signers: 1
V2 Signer: certificate DN: CN=…
V2 Signer: certificate SHA-256 digest: …
V2 Signer: key algorithm: RSA
V2 Signer: key size (bits): 4096
```

| Line | What to check |
| :-- | :-- |
| `Verifies` | Present, with no `DOES NOT VERIFY` anywhere in the output |
| v2 scheme `true` | This is the one that matters. APK Signature Scheme v2 is honoured by every Android version this app supports (`minSdk` is 24; v2 landed in 24) |
| v1 / v3 / v4 `false` | **Expected, and not a problem.** AGP does not add v1 JAR signing because nothing below API 24 can install this app, and it does not add v3 or v4 for this configuration. v3 only enables key *rotation*, which this project does not use, and v4 exists for incremental install over ADB |
| `certificate DN` | **Your** name or organisation, exactly as given to `keytool` |
| `certificate SHA-256 digest` | Record it. Every future release must show the same digest; a different one means a different key, and players cannot update across it |
| `key size (bits)` | 4096, if you followed [§1](#1-create-the-release-keystore) |

Two further checks worth running once per release:

```bash
# The archive is correctly aligned for mmap. AGP does this, but confirm it.
$ANDROID_HOME/build-tools/<version>/zipalign -c -v 4 \
  release/EARTH-<version>-release.apk | tail -1

# Package name, version and permissions are the ones you meant to ship.
$ANDROID_HOME/build-tools/<version>/aapt2 dump badging \
  release/EARTH-<version>-release.apk | head -5
```

And confirm the checksums match what you are about to upload:

```bash
cd release && sha256sum -c SHA256SUMS.txt
```

The SHA-256 of the APK belongs in the release notes so downloaders can check
what they got — see
[GitHub release template](GITHUB_RELEASE_TEMPLATE.md).

---

## 7. Configure CI signing (optional)

CI signing is **not required** to publish a GitHub release; building locally with
`keystore.properties` is enough, and it keeps the key off every machine but
yours. Configure it only if you want release artifacts built on every push.

GitHub Actions cannot hold a file, so the keystore travels as base64 in a secret
and is written to disk for the length of the job.

**Create the secret** (locally, once):

```bash
base64 -w0 earth-release-key.jks            # Linux
base64 earth-release-key.jks | tr -d '\n'   # macOS
```

Add four **repository secrets** under *Settings → Secrets and variables →
Actions*:

| Secret | Value |
| :-- | :-- |
| `EARTH_KEYSTORE_BASE64` | the base64 string above |
| `EARTH_KEYSTORE_PASSWORD` | the store password |
| `EARTH_KEY_ALIAS` | the key alias (`earth-release`) |
| `EARTH_KEY_PASSWORD` | the key password |

`.github/workflows/android.yml` already decodes the keystore into the runner's
temporary directory when the secret is present, exports `EARTH_KEYSTORE`, and
deletes the file in an `always()` step before artifacts are uploaded. Without the
secrets the same job runs and produces unsigned artifacts, so a pull request from
a fork is not blocked.

Two rules for any CI change here:

- **Never `echo` a secret**, and never pass one on a command line — a command
  line is visible in the process table and in `set -x` output. Environment
  variables only.
- **Never upload the decoded keystore as a build artifact**, including by
  globbing a directory that contains it.

> A secret is readable by every workflow in the repository, which includes any
> workflow a future change adds. Keeping the key on one machine and building
> releases there is the more conservative choice, and it is the one this
> documentation assumes.

---

## 8. If Google Play is ever added

Not part of this release, and not a prerequisite for it. Recorded because the
signing story changes if it happens.

Play enrols apps in **Play App Signing**: Google holds the *app signing key* and
re-signs every upload with it, while you hold an *upload key*. The consequence
that matters here:

> A GitHub-released APK signed with the key created above has a **different
> signature** from a Play build of the same app. A player cannot install one over
> the other — they must uninstall first, which loses the save, because saves are
> local. If EARTH is ever published on both channels, say so plainly on the
> release page.

Enrolment happens once, in Play Console, when the app is first created. The
options and their consequences are in [Google Play](wiki/Google-Play.md).

---

## If a key is exposed

Treat a keystore pushed to a public repository as compromised even if it is
force-pushed away minutes later: it is in the fork network, in caches, and in
anyone's clone.

1. **Do not** try to scrub history and carry on.
2. Generate a new key, following [§1](#1-create-the-release-keystore).
3. Existing installs **cannot** be upgraded across a signature change. The
   release notes have to tell players to uninstall and reinstall, and that their
   save will not survive it.
4. Rotate every password that was stored alongside the old key.
5. If the app was also on Play and enrolled in Play App Signing, request an
   **upload key reset** in Play Console instead; the app signing key is
   unaffected, so players are not.

There is no certificate revocation for Android app signing. Prevention is the
whole mitigation, which is what [§3](#3-protect-the-keystore) is for.

---

**Next:** [GitHub release template](GITHUB_RELEASE_TEMPLATE.md) · [Release process](wiki/Release-Process.md) · [Release blockers](RELEASE_BLOCKERS.md) · [Security and privacy](wiki/Security-and-Privacy.md)
