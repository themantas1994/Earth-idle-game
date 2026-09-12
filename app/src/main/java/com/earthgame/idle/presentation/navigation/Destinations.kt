package com.earthgame.idle.presentation.navigation

/**
 * Every screen the app can show.
 *
 * [shortLabel] is what fits under the icon on a 360 dp phone with nine columns
 * across; [title] is the full name, used in the app bar and as the accessibility
 * label, so a screen reader announces "Atmosphere" where the tab says "Air".
 *
 * [inNavigation] separates the nine game tabs from the screens reached from
 * inside another one. About and the licence notices are opened from Settings:
 * they are ordinary destinations — they push onto the same back stack and
 * survive a configuration change the same way — but a tenth and eleventh column
 * would not fit across a phone and neither is somewhere a player navigates to
 * mid-game.
 */
enum class Destination(
    val route: String,
    val title: String,
    val shortLabel: String,
    val icon: String,
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

    companion object {
        val START = HOME

        /** The destinations that get a tab in the bottom bar and the side rail. */
        val navigationEntries: List<Destination> = entries.filter { it.inNavigation }

        fun fromRoute(route: String?): Destination =
            entries.firstOrNull { it.route == route } ?: START
    }
}
