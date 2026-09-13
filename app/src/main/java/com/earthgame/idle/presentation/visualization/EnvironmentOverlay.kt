package com.earthgame.idle.presentation.visualization

/**
 * The environmental views the player can put on the globe.
 *
 * Exactly one is active at a time. That is a deliberate limit rather than a
 * simplification: every one of these is a full-globe treatment, and two of them
 * at once produces a planet that is colourful and says nothing. The two that
 * *are* only markers — events and storms — are drawn alongside whichever
 * surface overlay is selected where it makes sense, which is what
 * [showsStorms] and [showsEvents] encode.
 *
 * Each carries the words its legend needs. Colour never communicates a
 * gameplay effect on its own here: every overlay prints its own scale in text
 * beside the globe, and every marker on it has a spoken description — see
 * `docs/wiki/Accessibility.md`.
 */
enum class EnvironmentOverlay(
    val id: String,
    val label: String,
    val icon: String,
    /** The left-hand end of the legend. */
    val legendLow: String,
    /** The right-hand end of the legend. */
    val legendHigh: String,
    /** One line explaining what the player is looking at. */
    val description: String,
) {
    ATMOSPHERE(
        id = "atmosphere",
        label = "Atmosphere",
        icon = "🌫️",
        legendLow = "Thin",
        legendHigh = "Choked",
        description = "The greenhouse blanket. The halo thickens and reddens as radiative forcing climbs.",
    ),
    TEMPERATURE(
        id = "temperature",
        label = "Temperature",
        icon = "🌡️",
        legendLow = "Cool",
        legendHigh = "Extreme",
        description = "Surface warming, strongest at the equator. Cool, neutral, warm, then extreme.",
    ),
    HUMIDITY(
        id = "humidity",
        label = "Humidity",
        icon = "💧",
        legendLow = "Dry",
        legendHigh = "Saturated",
        description = "Water vapour held in the air, read off the climate model's own feedback term.",
    ),
    WIND(
        id = "wind",
        label = "Wind",
        icon = "🌬️",
        legendLow = "Still",
        legendHigh = "Gale",
        description = "Streamlines following the circulation: tropical easterlies, mid-latitude westerlies.",
    ),
    EVENTS(
        id = "events",
        label = "Events",
        icon = "📍",
        legendLow = "Calm",
        legendHigh = "Active",
        description = "Where the world events currently running are being felt.",
    ),
    STORMS(
        id = "storms",
        label = "Storms",
        icon = "🌀",
        legendLow = "Clear",
        legendHigh = "Severe",
        description = "Every live storm, at the position and intensity the simulation gives it.",
    ),
    ;

    /**
     * Storms stay visible under most overlays — they are a gameplay penalty the
     * player is entitled to see whatever view they have chosen — but are hidden
     * under the wind overlay, where hundreds of particles and six rotating
     * spirals fight each other.
     */
    val showsStorms: Boolean get() = this != WIND

    /** Event pins are shown on their own overlay and alongside the storm view. */
    val showsEvents: Boolean get() = this == EVENTS || this == STORMS

    companion object {
        val DEFAULT = ATMOSPHERE

        fun fromId(id: String?): EnvironmentOverlay = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
