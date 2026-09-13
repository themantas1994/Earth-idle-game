package com.earthgame.idle.domain.storms

import com.earthgame.idle.domain.technologies.TechBranch

/**
 * The storm domain model.
 *
 * A storm is a **simulation entity**, not a visual effect. It has a position,
 * an intensity, a lifetime and a set of production penalties, all of it in the
 * domain layer, all of it saved, and all of it advanced by the engine whether
 * or not anything is being drawn. The renderer reads storms; it never creates
 * one. See `docs/wiki/Storm-System.md`.
 *
 * The weather here is a game system. Nothing in it is a forecast, and the
 * categories below are chosen because they read clearly on a globe and price
 * differently, not because they map onto real storm taxonomy.
 */

/** What a storm does to production, at full intensity. Values are penalties, 0..1. */
data class StormEffectProfile(
    /** Taken off every generator in the world. */
    val globalPenalty: Double,
    /** Taken off specific branches on top of the global penalty. */
    val branchPenalties: Map<TechBranch, Double> = emptyMap(),
)

/**
 * The four storm categories the first version ships.
 *
 * Deliberately few. Each one has to be distinguishable on a globe the size of
 * a thumb and explainable in one line on a card, and a dozen categories would
 * be neither. New categories are additive — nothing in the engine enumerates
 * them exhaustively except the save format's id lookup, which falls back
 * safely.
 */
enum class StormType(
    val id: String,
    val displayName: String,
    val icon: String,
    /** Warming (°C) at or above which this category can form at all. */
    val minTemperatureAnomalyC: Double,
    /** Humidity (0..1) at or above which this category can form at all. */
    val minHumidity: Double,
    /** Relative weight among the categories currently eligible. */
    val weight: Int,
    /** Latitude band the storm is born in, as absolute degrees. */
    val formationLatitudeRange: ClosedFloatingPointRange<Double>,
    /** Peak intensity band, 0..1. Where in it a given storm lands scales with formation pressure. */
    val peakIntensityRange: ClosedFloatingPointRange<Double>,
    /** Lifetime band in real seconds. */
    val lifetimeSecondsRange: ClosedFloatingPointRange<Double>,
    /** Angular radius in degrees at full intensity — the footprint drawn on the globe. */
    val maxRadiusDegrees: Double,
    /** Degrees of arc travelled per real second at full intensity. */
    val driftDegreesPerSecond: Double,
    val effect: StormEffectProfile,
) {
    TROPICAL_STORM(
        id = "tropical_storm",
        displayName = "Tropical Storm",
        icon = "🌀",
        minTemperatureAnomalyC = 0.6,
        minHumidity = 0.52,
        weight = 6,
        formationLatitudeRange = 5.0..24.0,
        peakIntensityRange = 0.18..0.45,
        lifetimeSecondsRange = 180.0..420.0,
        maxRadiusDegrees = 9.0,
        driftDegreesPerSecond = 0.018,
        effect = StormEffectProfile(
            globalPenalty = 0.04,
            branchPenalties = mapOf(TechBranch.ELECTRICITY to 0.06),
        ),
    ),

    HURRICANE(
        id = "hurricane",
        displayName = "Hurricane",
        icon = "🌪️",
        minTemperatureAnomalyC = 2.5,
        minHumidity = 0.58,
        weight = 4,
        formationLatitudeRange = 8.0..32.0,
        peakIntensityRange = 0.42..0.72,
        lifetimeSecondsRange = 300.0..660.0,
        maxRadiusDegrees = 13.0,
        driftDegreesPerSecond = 0.022,
        effect = StormEffectProfile(
            globalPenalty = 0.07,
            branchPenalties = mapOf(
                TechBranch.ELECTRICITY to 0.12,
                TechBranch.CONSTRUCTION to 0.09,
                TechBranch.TRANSPORTATION to 0.08,
            ),
        ),
    ),

    SEVERE_ATMOSPHERIC(
        id = "severe_atmospheric",
        displayName = "Severe Atmospheric Storm",
        icon = "⚡",
        minTemperatureAnomalyC = 5.0,
        minHumidity = 0.60,
        weight = 3,
        // The one category that is not tropical: it forms anywhere, including
        // over the mid-latitude landmasses where the grid and the datacentres are.
        formationLatitudeRange = 18.0..58.0,
        peakIntensityRange = 0.35..0.68,
        lifetimeSecondsRange = 150.0..360.0,
        maxRadiusDegrees = 11.0,
        driftDegreesPerSecond = 0.030,
        effect = StormEffectProfile(
            globalPenalty = 0.05,
            branchPenalties = mapOf(
                TechBranch.ELECTRICITY to 0.10,
                TechBranch.DIGITAL to 0.14,
            ),
        ),
    ),

    SUPERSTORM(
        id = "superstorm",
        displayName = "Superstorm",
        icon = "🌩️",
        minTemperatureAnomalyC = 9.0,
        minHumidity = 0.66,
        weight = 2,
        formationLatitudeRange = 5.0..38.0,
        peakIntensityRange = 0.70..1.0,
        lifetimeSecondsRange = 420.0..900.0,
        maxRadiusDegrees = 19.0,
        driftDegreesPerSecond = 0.016,
        effect = StormEffectProfile(
            globalPenalty = 0.11,
            branchPenalties = mapOf(
                TechBranch.ELECTRICITY to 0.16,
                TechBranch.INDUSTRY to 0.13,
                TechBranch.CONSTRUCTION to 0.13,
                TechBranch.TRANSPORTATION to 0.11,
            ),
        ),
    ),
    ;

    companion object {
        fun fromId(id: String?): StormType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The player-facing intensity band.
 *
 * Every place a storm's strength is shown states this word as well as drawing
 * it, because colour and size alone cannot carry a gameplay effect — see
 * `docs/wiki/Accessibility.md`.
 */
enum class StormSeverity(val displayName: String) {
    FORMING("Forming"),
    MODERATE("Moderate"),
    SEVERE("Severe"),
    EXTREME("Extreme"),
    ;

    companion object {
        fun of(intensity: Double): StormSeverity = when {
            intensity < 0.25 -> FORMING
            intensity < 0.5 -> MODERATE
            intensity < 0.78 -> SEVERE
            else -> EXTREME
        }
    }
}

/**
 * One storm, as the simulation holds it.
 *
 * Every field is plain data and every field is saved, so a storm survives a
 * reload, a process death and an absence identically. Positions are in
 * degrees: latitude −90..90, longitude −180..180 wrapping.
 */
data class Storm(
    /** Stable identity for the run: derived from the step it formed on. */
    val id: String,
    val type: StormType,
    /** The name the news feed and the storm card use, e.g. "Iris". */
    val name: String,

    val latitudeDeg: Double,
    val longitudeDeg: Double,

    /** Current strength, 0..1. Drives both the gameplay penalty and the visuals. */
    val intensity: Double,
    /** The peak this storm is heading for, 0..1, fixed when it formed. */
    val targetIntensity: Double,
    /** The highest intensity it has actually reached, for the "peaked at" readout. */
    val peakIntensity: Double,

    /** Real seconds since it formed. */
    val ageSeconds: Double,
    /** Real seconds of life it was born with. */
    val lifetimeSeconds: Double,

    /** Heading in degrees clockwise from north. */
    val headingDeg: Double,
    /** Degrees of arc per real second. */
    val speedDegPerSecond: Double,

    /** The storm step index it formed on — the anchor that makes its id and name deterministic. */
    val formedAtStep: Long,
) {
    /** Real seconds of life remaining. Never negative. */
    val remainingSeconds: Double get() = (lifetimeSeconds - ageSeconds).coerceAtLeast(0.0)

    val severity: StormSeverity get() = StormSeverity.of(intensity)

    /** Angular footprint in degrees, which grows with intensity. */
    val radiusDeg: Double get() = type.maxRadiusDegrees * (0.35 + 0.65 * intensity)

    /** "Hurricane Iris". */
    val displayName: String get() = "${type.displayName} $name"

    /** A one-line spoken description, used as the accessibility label everywhere a storm appears. */
    val accessibleSummary: String
        get() = "$displayName, ${severity.displayName.lowercase()}, " +
            "at ${formatLatitude(latitudeDeg)} ${formatLongitude(longitudeDeg)}"
}

/** "12°N" / "4°S". */
fun formatLatitude(latitudeDeg: Double): String {
    val hemisphere = if (latitudeDeg >= 0) "N" else "S"
    return "${kotlin.math.abs(latitudeDeg).toInt()}°$hemisphere"
}

/** "63°W" / "110°E". */
fun formatLongitude(longitudeDeg: Double): String {
    val hemisphere = if (longitudeDeg >= 0) "E" else "W"
    return "${kotlin.math.abs(longitudeDeg).toInt()}°$hemisphere"
}

/**
 * The name pool.
 *
 * A fixed list rather than generated text: a storm has to be recognisable
 * across a news headline, a marker on the globe and a card, and the same
 * seeded run has to produce the same names every time it is replayed.
 */
val STORM_NAMES: List<String> = listOf(
    "Iris", "Bramble", "Corvus", "Delphine", "Ember", "Fenwick", "Galena", "Halcyon",
    "Ingrid", "Juniper", "Kestrel", "Larkspur", "Mistral", "Nadir", "Onyx", "Petrichor",
    "Quill", "Rosalind", "Sable", "Tamarind", "Ulysses", "Verity", "Wren", "Xanthe",
    "Yarrow", "Zephyr", "Alder", "Brindle", "Cinder", "Dunlin", "Everest", "Flint",
)
