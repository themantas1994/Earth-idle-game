# Assets and their provenance

[← Documentation home](wiki/Home.md)

Every non-code asset that ships inside the EARTH APK, and what is actually known
about where it came from.

This file exists because an asset whose licensing cannot be established must not
be published, and because provenance cannot be read off a file's pixels. Nothing
here is inferred: each row records what is verifiable from the repository, and
where that is nothing, it says so.

---

## The complete inventory

EARTH ships remarkably few assets. The simulation is code, the UI is Compose, and
there are no textures, sprites, fonts or audio files at all.

| Asset | Files | Status |
| :-- | :-- | :-- |
| Launcher icon bitmaps | `app/src/main/res/mipmap-*/ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png` (5 densities × 3 files) | **OWNER ACTION REQUIRED** — see below |
| Adaptive icon definitions | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | Authored in this repository |
| Icon background colour | `ic_launcher_background` in `app/src/main/res/values/colors.xml` | Authored in this repository |
| Theme and colour tokens | `app/src/main/res/values/colors.xml`, `themes.xml`, `values-night/themes.xml` | Authored in this repository |
| Backup rules | `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Authored in this repository |
| Third-party notices | `THIRD_PARTY_NOTICES.txt`, bundled into assets at build time | Generated from the resolved classpath by `scripts/third-party-notices.py`; each notice is its own author's, reproduced as their licence requires |
| Sound and music | **None.** `SoundPoolAudio` is wired but no audio file is bundled | Nothing to license |
| Fonts | **None.** The UI uses the platform's default typeface | Nothing to license |

---

## OWNER ACTION REQUIRED — launcher icon bitmaps

> **The licensing of the launcher icon artwork is unrecorded, and it must be
> settled before the APK is published.** This is the only asset in the repository
> with this problem.

### What is verifiable

- The bitmaps were added in the first Android commits (`6f145b7`,
  `9b92ba4`, `0783fc7`) with no note of a source in any commit message.
- They carry **no PNG metadata at all** — no `tEXt`, `iTXt`, `zTXt` or `tIME`
  chunk, so no embedded author, copyright, creation date or generating tool.
- Dimensions are the legacy launcher sizes: `ic_launcher.png` and
  `ic_launcher_round.png` at 48/72/96/144/192 px, and `ic_launcher_foreground.png`
  at the same sizes rather than the 108 dp adaptive-icon sizes
  (108/162/216/324/432 px) — which is a separate, cosmetic problem, recorded as
  M1 in [Release blockers](RELEASE_BLOCKERS.md).
- Nothing anywhere in the repository — README, wiki, commit history, or a licence
  file — states an author, a source or a licence for them.

### What is not verifiable, and will not be invented

Whether the artwork is original work of this project, exported from an icon
generator, or taken from a stock library. Only the person who created or obtained
it knows, and a licence cannot be established by inspection. No provenance has
been written into this file on the owner's behalf.

### How the owner closes this

Pick whichever of these is true and record it here, replacing this section:

1. **It is original work of this project.** State that, name the author, and state
   the licence it is released under — normally the project's own licence, once
   [that is chosen](RELEASE_BLOCKERS.md#o1-the-project-has-no-licence). One
   sentence in this file is enough.
2. **It came from a generator or a stock library.** Record the source, the exact
   asset, and the terms — including whether attribution is required, and if so
   where that attribution appears in the app. Android Studio's Image Asset Studio
   output from a shape or a Material icon falls here; note which.
3. **Neither can be established.** Then it must be **replaced** before release
   with artwork whose provenance is known, and this file updated to describe the
   replacement. Re-exporting at the correct adaptive-icon sizes at the same time
   would also close M1.

Until one of those is recorded, publishing the APK means publishing an asset the
project cannot show it has the right to distribute.

---

## Adding an asset later

Any new non-code file that ships inside the APK gets a row in the table above, at
the time it is added, with its source and licence. An asset added without one is
the situation this file documents, and it is much cheaper to record provenance on
the way in than to reconstruct it afterwards.

For third-party code, nothing needs doing by hand: `scripts/third-party-notices.py`
reads the resolved classpath and CI fails if `THIRD_PARTY_NOTICES.txt` has drifted
from it. See [Licensing](wiki/Licensing.md).

---

**Next:** [Release blockers](RELEASE_BLOCKERS.md) · [Licensing](wiki/Licensing.md) · [Third-party licences](THIRD_PARTY_LICENSES.md)
