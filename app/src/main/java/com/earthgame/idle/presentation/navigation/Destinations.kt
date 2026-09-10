package com.earthgame.idle.presentation.navigation

/**
 * The game's nine destinations.
 *
 * [shortLabel] is what fits under the icon on a 360 dp phone with nine columns
 * across; [title] is the full name, used in the app bar and as the accessibility
 * label, so a screen reader announces "Atmosphere" where the tab says "Air".
 */
enum class Destination(
    val route: String,
    val title: String,
    val shortLabel: String,
    val icon: String,
) {
    HOME("home", "Home", "Home", "🌍"),
    ATMOSPHERE("atmosphere", "Atmosphere", "Air", "☁️"),
    TECHNOLOGY("technology", "Technology", "Tech", "🔬"),
    PRODUCTION("production", "Production", "Output", "🏭"),
    PRESTIGE("prestige", "Prestige", "Reset", "✨"),
    CHALLENGES("challenges", "Challenges", "Trials", "🎯"),
    ACHIEVEMENTS("achievements", "Achievements", "Awards", "🏆"),
    STATISTICS("statistics", "Statistics", "Stats", "📊"),
    SETTINGS("settings", "Settings", "Setup", "⚙️");

    companion object {
        val START = HOME

        fun fromRoute(route: String?): Destination =
            entries.firstOrNull { it.route == route } ?: START
    }
}
