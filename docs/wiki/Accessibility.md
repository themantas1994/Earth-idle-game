# Accessibility

[← Documentation home](Home.md)

A dense idle game is a hostile shape for a screen reader if nobody thinks about it: dozens of
label/number pairs per screen, a nine-item navigation bar with three-letter labels, and a UI that
changes four times a second. These are the decisions made about that.

## Semantics

**Label and value are merged into one node.** `StatRow` and the header's `StatChip` build a single
spoken string and set it as the node's `contentDescription` with `mergeDescendants = true`, so
TalkBack reads *"Habitability, 84%"* rather than announcing two unrelated fragments in sequence.

```kotlin
Row(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken })
```

**A scrollable strip says that it scrolls.** The header's resource row is labelled *"Resource
balances, scroll sideways for more"* when its content overflows and plainly *"Resource balances"*
when it does not — the same information the right-edge fade gives a sighted player, from the same
`maxValue`, so neither affordance can be present without the other. The chips inside keep their
own merged labels.

**Decoration is hidden.** `DecorativeIcon` (the emoji on every card), `ColorDot` (the gas colour
key) and `ProgressBar` all carry `clearAndSetSemantics { }`. A progress bar is a picture of a number
that is already announced next to it; announcing it twice is noise, and announcing an emoji as
"fire emoji" before every technology name is worse.

**Navigation announces its full name.** The bottom bar's labels are three or four characters to fit
nine columns across a 360 dp phone (`Air`, `Tech`, `Output`, `Trials`, `Setup`), but each carries
`contentDescription = destination.title` — so a screen reader says *"Atmosphere"* where the tab says
*"Air"*.

**Tabs use the right role.** `Modifier.selectable(selected, role = Role.Tab)` on both the bottom bar
and the side rail, which is what gives a screen reader the selection state and the correct role
rather than "button".

## Touch targets

`Dimens.MinTouchTarget = 48.dp` — Material's minimum, and the floor for every interactive element.
Concretely:

| | |
| :-- | :-- |
| Bottom-nav columns | `heightIn(min = 52.dp)`, each flexing to an equal width share |
| Buy-quantity chips | `heightIn(min = Dimens.MinTouchTarget)` |
| The Reset button | `heightIn(min = 52.dp)`, full width |
| Cards | Padded well past the minimum |

The ad banner sits **above** the navigation with 6 dp of separation, never flush against it — a
mistap that lands on an ad is an accidental click, which is bad for the player and, at scale, gets
AdMob accounts suspended.

## Dynamic type

All text uses `sp` through Material 3 typography, so it scales with the system font size. The
activity declares `fontScale` in `configChanges`, so a font-size change reconfigures in place
without recreating the activity.

`NumericTextStyle` gives the readouts a tabular-friendly weight and tighter line height than
Material's defaults, which are tuned for prose — but it is still `sp` and still scales.

> **Known limitation:** the bottom bar's 9 sp labels across nine columns are the tightest thing in
> the UI, and at the largest accessibility font scales they will clip (`TextOverflow.Clip`,
> `maxLines = 1`). The icon and the merged content description still identify the tab, so it degrades
> to "usable but ugly" rather than "broken". A rail or an overflow at large scales is an open
> improvement — see [the audit](../CODEBASE_AUDIT.md).

## Reduced motion

A real setting, honoured in **one place** rather than in the two places someone remembered:

```kotlin
val LocalReducedAnimations: ProvidableCompositionLocal<Boolean>
```

`ProgressBar` reads it and jumps to its target instead of easing. Every animated surface reads the
same local, so adding an animation and forgetting the setting is a visible omission rather than a
silent one.

## Colour and contrast

Light and dark palettes are both defined explicitly, not derived. The light palette is **not** a
mechanical inversion — the accent is a darker teal (`#177F76` against the dark theme's `#4FD1C5`)
because the dark accent washes out on white.

Text has three deliberate steps — `text`, `textDim`, `textFaint` — used consistently: primary values,
labels, and de-emphasised context.

**Colour is never the only signal.** Habitability shows a percentage *and* a colour *and* a status
word ("Stable" / "Strained" / "Critical" / "Collapsed"). Gases are keyed by colour *and* by formula
text. Locked technologies are dimmed *and* show their unmet requirements. Owned one-time nodes show
an "OWNED" badge, not just a colour.

The `VisualEra` header tint is a low-alpha background wash (0x1F–0x52) behind text that carries its
own contrast, so it changes mood without changing legibility.

## Keyboard and pointer

Compose's default focus traversal applies. Everything interactive is a `Button`, `FilterChip`, or a
`Modifier.selectable`/`clickable`, so all of it is focusable and activatable from a keyboard or a
connected controller. Nothing is drag-only, nothing is gesture-only, and there is no timed
interaction anywhere in the game — an idle game has no reflex requirement by definition.

Back is handled explicitly and predictably: it unwinds the screens actually visited and only leaves
from Home. See [Navigation](Navigation.md).

## Dialogs and confirmation

Destructive actions are confirmed:

| | |
| :-- | :-- |
| **Reset Earth** | `ConfirmResetDialog` when `settings.confirmReset` is on (the default). The Reset button is *disabled* until the planet is actually dead, so it cannot be hit by accident at all. |
| **Delete save** | Confirmed in Settings. |
| **Start a challenge** | The button says "(resets current Earth)" — the consequence is in the label, not only in a dialog. |

Dialogs are standard Material 3, so focus handling and the screen-reader announcement come from the
platform.

Toasts float over the content rather than displacing it, so a headline arriving mid-purchase never
moves the button out from under a thumb — which matters most for someone with a motor impairment,
for whom a moved target is a mis-tap rather than an annoyance.

## What has not been done

Stated plainly rather than implied:

- **No screen reader has actually been run against this build.** There is no device or emulator in
  the build environment. The semantics above are correct by construction and covered by
  `EarthAppScreenTest`, but nobody has listened to TalkBack read a screen.
- **Contrast ratios have not been measured** against WCAG AA. The palettes were chosen by eye from
  the original build's tokens.
- **No `stateDescription`** on the progress bars for people who *do* want them announced (they are
  currently fully hidden).
- **Large font scales clip the bottom-nav labels**, as above.

All four are in [the audit](../CODEBASE_AUDIT.md) as open work.

---

**Next:** [UI architecture](UI-Architecture.md) · [Navigation](Navigation.md)
