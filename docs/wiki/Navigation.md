# Navigation

[← Documentation home](Home.md)

`presentation/EarthApp.kt`, `presentation/navigation/Destinations.kt`.

## An explicit stack, not `NavHost`

Destinations are tracked as a plain back stack held in `rememberSaveable`:

```kotlin
var current by rememberSaveable { mutableStateOf(Destination.START) }
val history = rememberSaveable(saver = destinationStackSaver) { mutableListOf() }

fun navigate(destination: Destination) {
    if (destination == current) return
    history.add(current)
    current = destination
}

BackHandler(enabled = true) {
    val previous = history.removeLastOrNull()
    if (previous != null) current = previous else onExit()
}
```

**The reason is Back.** On a nine-tab game, Android's Back has to unwind the screens the player
actually visited and only leave the app from Home. `NavHost`'s default single-destination
behaviour closed the game on any mis-swipe, which was one of the concrete complaints about the
previous build.

A bottom-nav `NavHost` with `popUpTo(startDestination) { saveState = true }` gets closer, but
"Home → Production → Technology → Back" should return to Production, and that configuration
returns to Home. Getting the desired behaviour out of `NavHost` meant fighting it; twenty lines of
list do it exactly.

The trade is deliberate and worth stating: no deep links, no navigation animations, and no type-safe
route arguments. The game has none of those needs — nine sibling tabs, no arguments, no external
entry points beyond the launcher.

`destinationStackSaver` is a `listSaver` over route strings, so the stack survives configuration
changes **and** process death, and an unknown route restores to Home rather than crashing.

## Nine tabs, eleven destinations

```kotlin
enum class Destination(
    val route: String, val title: String, val shortLabel: String, val icon: String,
    val inNavigation: Boolean = true,
) {
    HOME("home", "Home", "Home", "🌍"),
    ATMOSPHERE("atmosphere", "Atmosphere", "Air", "☁️"),
    TECHNOLOGY("technology", "Technology", "Tech", "🔬"),
    PRODUCTION("production", "Production", "Output", "🏭"),
    PRESTIGE("prestige", "Prestige", "Reset", "✨"),
    CHALLENGES("challenges", "Challenges", "Trials", "🎯"),
    ACHIEVEMENTS("achievements", "Achievements", "Awards", "🏆"),
    STATISTICS("statistics", "Statistics", "Stats", "📊"),
    SETTINGS("settings", "Settings", "Setup", "⚙️"),
    ABOUT("about", "About", "About", "ℹ️", inNavigation = false),
    LICENSES("licenses", "Open Source Licenses", "Licenses", "📄", inNavigation = false);
}
```

`shortLabel` is what fits under an icon on a 360 dp phone with nine columns across; `title` is the
full name, used as the **accessibility label** — so a screen reader announces "Atmosphere" where
the tab says "Air".

`inNavigation` separates the nine game tabs from the screens reached from inside another one.
`BottomNav` and `SideNav` iterate `Destination.navigationEntries`; a tenth and eleventh column
would not fit across a phone, and neither About nor the licence notices is somewhere a player
navigates to mid-game.

They are **ordinary destinations** otherwise: opened from Settings with the same `navigate()`,
pushed onto the same back stack, restored by the same saver. Back unwinds
Licenses → About → Settings exactly as it unwinds any other path, which
`AboutScreenTest.backUnwindsLicencesToAboutToSettings` asserts.

## The bars

**Bottom bar** (compact widths): a hand-rolled `Row`, not Material's `NavigationBar`, which assumes
three to five items and reserves far more width per destination than 360 dp has. Each column flexes
to an equal share with a 52 dp minimum height, and sits **above** the navigation-bar inset via
`windowInsetsPadding`, so the bottom row of pixels is never clipped by a gesture bar.

**Side rail** (wider): `SideNav`, with room for full labels.

Both use `Modifier.selectable(selected, role = Role.Tab)`, which is what gives a screen reader the
correct role and selection state.

## The ad banner's position

The banner sits **above** the navigation, never flush against it. AdMob policy forbids placing an
ad against a control the player is tapping, and a mistap that lands on the ad is an accidental
click — which is bad for the player and, at scale, gets accounts suspended. It also occupies **no
space at all** until an ad actually loads, so a device where nothing fills loses no screen to an
empty strip.

## Testing

`EarthAppScreenTest.backUnwindsScreenHistoryAndOnlyLeavesFromHome` navigates several tabs deep,
presses Back repeatedly, and asserts both that the history unwinds in order and that `onExit` fires
only from Home. `everyDestinationIsReachableAndRenders` visits all nine.

---

**Next:** [UI architecture](UI-Architecture.md) · [Accessibility](Accessibility.md) · [Advertising](Advertising.md)
