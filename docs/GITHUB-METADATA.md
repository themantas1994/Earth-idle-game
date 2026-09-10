# GitHub repository metadata

> [!IMPORTANT]
> **None of the settings on this page have been applied.** The tooling available to the session
> that produced this audit can read repository metadata but cannot write it — there is no API
> surface here for setting a description, homepage or topics, and no way to push to the wiki
> repository. Everything below is a **recommendation**, verified against the repository's actual
> current state, for a maintainer to apply by hand.

## Current state (verified)

| | |
| :-- | :-- |
| Description | *(empty)* |
| Website | *(empty)* |
| Topics | *(none)* |
| Licence | **none** |
| Releases | **none** |
| Tags | **none** |
| Wiki | Enabled, never populated |
| Issues / Discussions | Issues on, Discussions off |
| Default branch | `main` |
| Primary language | Kotlin |

## 1. Description

**Settings → General → Description**, or the ✏️ next to "About" on the repository home page.

```
Open-source Android idle/incremental civilization simulator about technology, greenhouse gases, climate collapse, and prestige. Kotlin + Jetpack Compose.
```

That is 152 characters, inside GitHub's 350-character limit and short enough to survive truncation
in search results. It leads with what the thing *is*, which is what a search snippet needs.

## 2. Website

Leave **empty** until something real exists. When a first release is published, set it to:

```
https://github.com/themantas1994/Earth-idle-game/releases/latest
```

Do not point it at a page that does not exist yet.

## 3. Topics

**Settings → General → Topics**, or the ⚙️ next to "About".

Each of these is accurate for this repository. Nothing here is aspirational or added for traffic:

```
android
android-game
idle-game
incremental-game
simulation-game
strategy-game
civilization-game
climate-game
open-source-game
kotlin
jetpack-compose
mobile-game
material-design
game-development
```

GitHub allows up to 20; 14 is comfortable. The first four are the ones people actually browse.

**Do not add** `climate-science`, `climate-model`, `education` or similar. The project explicitly
describes its simulation as a gameplay model rather than a scientific one, and a topic implying
otherwise would be a false claim — see the disclaimer in the README and in
[Climate model](wiki/Climate-Model.md).

## 4. Social preview image

**Settings → General → Social preview.** 1280×640 PNG.

None exists, and one should not be invented. When there is a real build to photograph, the natural
choice is a device-frame screenshot of the Atmosphere or Home screen at a high temperature, with the
title "EARTH" and the one-line description. Until then GitHub's generated card is honest and fine.

> The repository currently contains **no screenshots at all** — only launcher icons under
> `app/src/main/res/mipmap-*`. The README therefore shows none, deliberately: a fabricated
> screenshot of an app nobody has run would be worse than no screenshot.

## 5. Licence — the important one

> [!WARNING]
> **This repository has no licence file, and no licence declared in its GitHub metadata.**
>
> Under copyright law the default applies: **all rights reserved**. Nobody has permission to copy,
> modify or redistribute the source, and a contribution cannot be accepted under defined terms.
> That is almost certainly not what is intended for a project the README calls open source.

Choosing a licence is the repository owner's decision, not a documentation one, so no file has been
added. To apply one:

**GitHub UI:** *Add file → Create new file →* name it `LICENSE` *→ "Choose a license template"*.

**Which one:**

| | For |
| :-- | :-- |
| **MIT** | Maximum reuse, minimum ceremony. The common default for a game like this, and the recommendation absent a reason to prefer otherwise. |
| **Apache-2.0** | Same permissiveness plus an explicit patent grant and contribution terms. Worth it if contributions from strangers are expected. |
| **GPL-3.0** | Requires derivative works to stay open. Choose deliberately — it constrains forks and some app-store distribution. |

Once the file exists, GitHub detects it automatically and shows the badge; add a matching badge and
a real `## License` section to the README, replacing the current warning.

Note that a licence covers this repository's own code. The frozen TypeScript reference in
`tools/ts-reference/` is the same project's code and is covered by the same choice.

## 6. Repository features

| | |
| :-- | :-- |
| **Issues** | On. Keep. |
| **Wiki** | On and empty. **Either populate it from `docs/wiki/` or turn it off** — an empty wiki tab on a project with 31 pages of documentation sends people to a dead end. The mirror command is in [Home.md](wiki/Home.md#pushing-this-to-the-github-wiki). |
| **Discussions** | Off. Worth enabling once there are players; balance feedback belongs there rather than in Issues. |
| **Projects** | On, unused. Harmless. |
| **Releases** | Empty. See [Release process](wiki/Release-Process.md). |

## 7. Suggested additions

Not metadata, but the same "professional open-source repository" checklist:

| File | Why |
| :-- | :-- |
| `LICENSE` | **The blocker.** See above. |
| `CONTRIBUTING.md` at the root | GitHub links it automatically from the issue and PR forms. A three-line file pointing at [docs/wiki/Contributing.md](wiki/Contributing.md) is enough. |
| `.github/ISSUE_TEMPLATE/bug_report.yml` | Device model and Android version are the two things every bug report needs and most omit. |
| `.github/ISSUE_TEMPLATE/balance_feedback.yml` | Run number, civilization level, temperature — the context a balance report is useless without. |
| `.github/PULL_REQUEST_TEMPLATE.md` | A checkbox for "regenerated parity fixtures" would catch the single most common mistake. |
| `CODE_OF_CONDUCT.md` | Conventional once a project accepts outside contributions. |
| `SECURITY.md` | Low stakes here (the app stores nothing off-device) but conventional. |

## 8. Discoverability, honestly

The README is written to be found by someone searching for *"open source Android idle game"*,
*"incremental game Kotlin"* or *"climate simulation game"*, using those phrases where they are
genuinely accurate and nowhere else. What actually moves discoverability further:

1. **Publish a release.** A repository with no downloadable artifact converts almost nobody.
2. **Add topics.** They are how GitHub's own browse pages work.
3. **Add screenshots**, once there are real ones.
4. **Add a licence.** Some people filter open-source searches by licence, and everyone else reads
   its absence as "not really open source".

None of that is keyword work, and no amount of keyword work substitutes for it.
