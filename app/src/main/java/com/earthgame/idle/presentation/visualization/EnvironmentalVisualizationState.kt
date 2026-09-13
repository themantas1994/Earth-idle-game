package com.earthgame.idle.presentation.visualization

import androidx.compose.runtime.Immutable
import com.earthgame.idle.domain.climate.relativeHumidity
import com.earthgame.idle.domain.climate.windStrength
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.engine.GAME_SECONDS_PER_REAL_SECOND
import com.earthgame.idle.domain.engine.stormRiskOf
import com.earthgame.idle.domain.events.RANDOM_EVENT_BY_ID
import com.earthgame.idle.domain.model.GAS_LIST
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.storms.Storm
import com.earthgame.idle.domain.storms.stormContribution
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Everything the globe needs to draw itself, and nothing else.
 *
 * ## Why this type exists
 *
 * The renderer is downstream of the simulation and must never become a second
 * source of truth for it. This is the one-way valve between them: a plain,
 * immutable snapshot built from [GameState] and [DerivedState] by
 * [environmentalVisualizationOf], holding only numbers a shader or a marker
 * needs and holding no simulation logic at all.
 *
 *     GameState + DerivedState
 *        -> EnvironmentalVisualizationState   (this file, pure, testable)
 *           -> the 3D renderer
 *
 * Nothing flows back. There are no rendering objects in `GameState` and no
 * `GameState` in the renderer; a storm's position on the globe is the position
 * the *simulation* gave it, and if the renderer never runs the simulation is
 * unaffected. See `docs/wiki/Environmental-Visualization.md`.
 *
 * Every field is already normalised into the 0..1 range the visuals work in,
 * against **gameplay-relevant** ranges rather than real-world physical ones —
 * a planet at +8 °C should look dramatic here even though a real +8 °C Earth
 * would still be mostly blue from orbit.
 */
@Immutable
data class EnvironmentalVisualizationState(
    /** The raw anomaly, for the readouts. */
    val temperatureAnomalyC: Double,
    /** [temperatureAnomalyC] mapped onto 0..1 across the range a run actually spans. */
    val temperatureScale: Float,
    /** Relative humidity, 0..1, from the simulated water vapour. */
    val humidity: Float,
    /** Overall wind strength, 0..1. */
    val windStrength: Float,
    /** How thick the greenhouse blanket looks, 0..1, from total radiative forcing. */
    val atmosphericOpacity: Float,
    /** Cloud cover, 0..1, driven by humidity. */
    val cloudCoverage: Float,
    /** Habitability, 0..1, straight from the simulation. */
    val habitability: Float,
    /** How ready the planet is to make a storm, 0..1. */
    val stormRisk: Float,
    /**
     * Where the planet is in its rotation, 0..1.
     *
     * Driven by the Earth's own simulated age, never by the device clock — so
     * it stops when the simulation stops and jumps forward correctly after an
     * absence. See [VISUAL_ROTATION_SIMULATED_DAYS] for why it is not the
     * simulated day itself.
     */
    val rotationPhase: Float,
    /** The Earth's simulated age in seconds, for the readouts. */
    val planetAgeSeconds: Double,
    /** Per-gas share of the greenhouse effect, strongest first. */
    val gasContributions: List<GasContribution>,
    val storms: List<StormVisual>,
    val events: List<EventVisual>,
    val collapsed: Boolean,
) {
    companion object {
        val EMPTY = EnvironmentalVisualizationState(
            temperatureAnomalyC = 0.0,
            temperatureScale = 0f,
            humidity = 0f,
            windStrength = 0f,
            atmosphericOpacity = 0f,
            cloudCoverage = 0f,
            habitability = 1f,
            stormRisk = 0f,
            rotationPhase = 0f,
            planetAgeSeconds = 0.0,
            gasContributions = emptyList(),
            storms = emptyList(),
            events = emptyList(),
            collapsed = false,
        )
    }
}

/** One gas's share of the warming, for the atmosphere overlay and its legend. */
@Immutable
data class GasContribution(
    val gasId: GasId,
    val formula: String,
    val forcingWm2: Double,
    /** Share of the total, 0..1. */
    val share: Float,
)

/** A storm as the globe and the storm card need it. */
@Immutable
data class StormVisual(
    val id: String,
    val displayName: String,
    val typeLabel: String,
    val icon: String,
    val severityLabel: String,
    val latitudeDeg: Float,
    val longitudeDeg: Float,
    val intensity: Float,
    val radiusDeg: Float,
    val headingDeg: Float,
    val remainingSeconds: Double,
    val locationLabel: String,
    /** This storm's own global production penalty, 0..1, before stacking. */
    val globalPenalty: Double,
    /** This storm's own per-branch penalties, strongest first, as (branch name, penalty). */
    val branchPenalties: List<Pair<String, Double>>,
    /** One line a screen reader can read instead of looking at the globe. */
    val accessibleSummary: String,
)

/**
 * An active random event, placed on the globe.
 *
 * The event system has no geography — events are global multiplier swings —
 * so the latitude and longitude here are **presentation only**: a stable
 * pseudo-position derived from the event's own id, so a wildfire stays where it
 * first appeared for as long as it burns and two events never sit on top of
 * each other. Nothing in the simulation reads them back.
 */
@Immutable
data class EventVisual(
    val id: String,
    val icon: String,
    val label: String,
    val latitudeDeg: Float,
    val longitudeDeg: Float,
    val isNegative: Boolean,
    /** How far through its duration the event is, 0..1. */
    val progress: Float,
    val accessibleSummary: String,
)

/**
 * Simulated days per full turn of the globe.
 *
 * The globe's rotation is driven by [GameState.gameAgeSeconds] rather than the
 * phone's clock, as the day/night lighting must be — but a *simulated* day
 * passes every real second (see `GameTime.kt`), and a planet spinning once a
 * second is a strobe, not a world. So one visible rotation is a fixed number of
 * simulated days instead: still the simulation's own clock, still frozen when
 * the simulation is, still correct across an absence, and slow enough to watch.
 */
const val VISUAL_ROTATION_SIMULATED_DAYS = 90.0

private val SIMULATED_SECONDS_PER_ROTATION =
    VISUAL_ROTATION_SIMULATED_DAYS * GAME_SECONDS_PER_REAL_SECOND

/**
 * Warming, in °C, at which the temperature overlay is fully saturated.
 *
 * Chosen against the game rather than the climate: a run spends most of its
 * length between 0 and about 20 °C of anomaly, and the endgame runs to
 * absurdity. Past this the visualization stops getting redder because there is
 * nowhere redder to go; the number beside it keeps climbing.
 */
const val TEMPERATURE_VISUAL_CEILING_C = 20.0

/** Radiative forcing, in W/m², at which the atmosphere overlay is fully saturated. */
const val FORCING_VISUAL_CEILING_WM2 = 12.0

private fun clamp01(value: Double): Float = min(1.0, max(0.0, value)).toFloat()

/**
 * Builds the render snapshot from the authoritative state.
 *
 * Pure and free of Android, so it is unit-tested without a GPU — which is the
 * point: whether the globe is showing the right planet is a question that can
 * be answered without looking at it.
 */
fun environmentalVisualizationOf(
    state: GameState,
    derived: DerivedState,
    nowMs: Long,
): EnvironmentalVisualizationState {
    val humidity = relativeHumidity(state.atmosphere)
    val wind = windStrength(state.temperatureAnomalyC, state.forcing.total)

    val totalForcing = max(0.0, state.forcing.total)
    val gases = GAS_LIST
        .map { gas ->
            val forcing = max(0.0, state.forcing.perGas[gas.id])
            GasContribution(
                gasId = gas.id,
                formula = gas.formula,
                forcingWm2 = forcing,
                share = if (totalForcing <= 0.0) 0f else clamp01(forcing / totalForcing),
            )
        }
        .filter { it.forcingWm2 > 0.0 }
        .sortedByDescending { it.forcingWm2 }

    return EnvironmentalVisualizationState(
        temperatureAnomalyC = state.temperatureAnomalyC,
        temperatureScale = clamp01(state.temperatureAnomalyC / TEMPERATURE_VISUAL_CEILING_C),
        humidity = clamp01(humidity),
        windStrength = clamp01(wind),
        atmosphericOpacity = clamp01(totalForcing / FORCING_VISUAL_CEILING_WM2),
        // Clouds follow humidity, which follows the water vapour the climate
        // model simulates — the cloud layer is therefore a reading of the
        // atmosphere rather than a decoration that happens to move.
        cloudCoverage = clamp01((humidity - 0.4) / 0.5),
        habitability = clamp01(state.habitability.fraction),
        stormRisk = clamp01(stormRiskOf(state)),
        rotationPhase = rotationPhaseOf(state.gameAgeSeconds),
        planetAgeSeconds = state.gameAgeSeconds,
        gasContributions = gases,
        storms = state.storms.storms.map(::toVisual),
        events = state.activeEvents.mapNotNull { active ->
            val definition = RANDOM_EVENT_BY_ID[active.eventDefId] ?: return@mapNotNull null
            val span = (active.endsAt - active.startedAt).coerceAtLeast(1L).toDouble()
            val elapsed = (nowMs - active.startedAt).coerceAtLeast(0L).toDouble()
            val placement = pseudoPlacement(active.id)
            EventVisual(
                id = active.id,
                icon = definition.icon,
                label = definition.displayName,
                latitudeDeg = placement.first,
                longitudeDeg = placement.second,
                isNegative = definition.isNegative,
                progress = clamp01(elapsed / span),
                accessibleSummary = "${definition.displayName}: ${definition.description}",
            )
        },
        collapsed = state.collapsed,
    )
}

/** Where the planet is in its turn, 0..1, from its own simulated age. */
fun rotationPhaseOf(gameAgeSeconds: Double): Float {
    if (gameAgeSeconds <= 0.0 || !gameAgeSeconds.isFinite()) return 0f
    val turns = gameAgeSeconds / SIMULATED_SECONDS_PER_ROTATION
    return (turns - kotlin.math.floor(turns)).toFloat()
}

private fun toVisual(storm: Storm): StormVisual {
    val contribution = stormContribution(storm)
    val branches = contribution.branchPenalties
        .entries
        .filter { it.value > 0.0005 }
        .sortedByDescending { it.value }
        .map { it.key.displayName to it.value }

    val effectSentence = buildString {
        if (contribution.globalPenalty > 0.0005) {
            append("all production ${formatPenalty(contribution.globalPenalty)}")
        }
        for ((branch, penalty) in branches) {
            if (isNotEmpty()) append(", ")
            append("$branch ${formatPenalty(penalty)}")
        }
        if (isEmpty()) append("no measurable effect yet")
    }

    return StormVisual(
        id = storm.id,
        displayName = storm.displayName,
        typeLabel = storm.type.displayName,
        icon = storm.type.icon,
        severityLabel = storm.severity.displayName,
        latitudeDeg = storm.latitudeDeg.toFloat(),
        longitudeDeg = storm.longitudeDeg.toFloat(),
        intensity = storm.intensity.toFloat(),
        radiusDeg = storm.radiusDeg.toFloat(),
        headingDeg = storm.headingDeg.toFloat(),
        remainingSeconds = storm.remainingSeconds,
        locationLabel = "${com.earthgame.idle.domain.storms.formatLatitude(storm.latitudeDeg)} " +
            com.earthgame.idle.domain.storms.formatLongitude(storm.longitudeDeg),
        globalPenalty = contribution.globalPenalty,
        branchPenalties = branches,
        accessibleSummary = "${storm.accessibleSummary}. Currently $effectSentence.",
    )
}

private fun formatPenalty(penalty: Double): String = "−${(penalty * 100).toInt()}%"

/**
 * A stable latitude/longitude for a string id.
 *
 * Deliberately trivial and deliberately in the presentation layer: it exists
 * only so a global event has somewhere to be drawn. Latitudes are kept inside
 * ±58° so a marker never lands on the pole where the projection is degenerate.
 */
internal fun pseudoPlacement(id: String): Pair<Float, Float> {
    var hash = 0x811C9DC5L
    for (character in id) {
        hash = (hash xor character.code.toLong()) * 0x01000193L
        hash = hash and 0xFFFFFFFFL
    }
    val latitude = ((hash % 117L) - 58L).toFloat()
    val longitude = (((hash / 117L) % 360L) - 180L).toFloat()
    return latitude to if (abs(longitude) > 180f) 0f else longitude
}
