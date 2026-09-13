package com.earthgame.idle.domain.engine

import com.earthgame.idle.domain.climate.relativeHumidity
import com.earthgame.idle.domain.climate.windStrength
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.NewsBulletin
import com.earthgame.idle.domain.storms.StormAdvanceResult
import com.earthgame.idle.domain.storms.StormBulletin
import com.earthgame.idle.domain.storms.StormBulletinKind
import com.earthgame.idle.domain.storms.StormClimate
import com.earthgame.idle.domain.storms.StormEffectSummary
import com.earthgame.idle.domain.storms.advanceStorms
import com.earthgame.idle.domain.storms.averageStormEffectsOverWindow
import com.earthgame.idle.domain.storms.computeStormEffects
import com.earthgame.idle.domain.storms.formatLatitude
import com.earthgame.idle.domain.storms.formatLongitude
import com.earthgame.idle.domain.storms.stormFormationPressure
import com.earthgame.idle.domain.storms.stormSustain

/**
 * Where the storm system meets the rest of the engine.
 *
 * The storm simulation itself knows nothing about [GameState]: it takes a
 * climate, a seed and a `dt`, and returns storms. This file is the only place
 * that connects the two, which keeps the storm maths testable on its own and
 * keeps `GameState` free of weather logic.
 *
 * ## Where storms sit in the simulation
 *
 * Exactly where random events sit, and for the same reason.
 * [simulateStep] is the reference-parity core — gases, resources, temperature,
 * habitability — and it is deliberately untouched by this feature: the numbers
 * it produces still match the numbers the original engine produced, which is
 * what the parity suite asserts. Storms are a layer above it, folded in as a
 * [MultiplierContribution] by the caller, exactly like an active event:
 *
 *     GameLoop.advance / computeOfflineProgress
 *       -> advance the storm timeline
 *       -> fold the resulting penalties into extraMultipliers
 *       -> simulateStep
 *
 * The renderer sits downstream of all of it and never feeds back in.
 *
 * ## Which storms are charged for a step
 *
 * The storms present at the **start** of a step, never the ones that formed
 * during it — again matching how event multipliers are read from the pre-tick
 * event list. On a 250 ms tick the distinction is invisible. Across an absence
 * it is the whole basis of the offline settlement: see
 * [stormMultipliersForAbsence].
 */

/** The climate a storm forms and lives in, read off the state the simulation already keeps. */
fun stormClimateOf(state: GameState): StormClimate = StormClimate(
    temperatureAnomalyC = state.temperatureAnomalyC,
    humidity = relativeHumidity(state.atmosphere),
    windStrength = windStrength(state.temperatureAnomalyC, state.forcing.total),
)

/** How ready this planet is to make a storm, 0..1. Shown to the player as "storm risk". */
fun stormRiskOf(state: GameState): Double = stormFormationPressure(stormClimateOf(state))

/** The production penalties the live storms are imposing right now. */
fun stormEffectsOf(state: GameState): StormEffectSummary = computeStormEffects(state.storms.storms)

/** Those penalties as the transient multiplier layer the simulation folds in. */
fun stormMultipliers(state: GameState): MultiplierContribution =
    stormEffectsOf(state).asMultiplierContribution()

/**
 * The penalties to settle an absence of [windowSeconds] with.
 *
 * Averaged across the window over each already-running storm's own intensity
 * curve — the analytic resolution an absence gets instead of replaying every
 * frame of it. A player who left under clear skies is charged nothing, which
 * is also what keeps the offline path numerically identical to the one the
 * parity fixtures were captured from.
 */
fun stormMultipliersForAbsence(state: GameState, windowSeconds: Double): MultiplierContribution {
    if (state.storms.storms.isEmpty()) return MultiplierContribution.NONE
    val sustain = stormSustain(stormRiskOf(state))
    return averageStormEffectsOverWindow(state.storms.storms, windowSeconds, sustain)
        .asMultiplierContribution()
}

/**
 * Advances the weather by [dtSeconds] and folds the result back into [state].
 *
 * The only mutation point for storms in the whole codebase. Both the live tick
 * and the offline catch-up go through here, so neither can drift from the
 * other.
 */
fun advanceStormsFor(state: GameState, dtSeconds: Double): StormAdvanceResult =
    advanceStorms(state.storms, state.stormSeed, stormClimateOf(state), dtSeconds)

/**
 * Turns a storm bulletin into a feed headline.
 *
 * Storms do not get a notification channel of their own: they write into the
 * same world-news feed the milestone headlines use, in the same shape, so a run
 * still reads as one story.
 */
fun StormBulletin.toNewsBulletin(): NewsBulletin {
    val storm = storm
    val position = "${formatLatitude(storm.latitudeDeg)} ${formatLongitude(storm.longitudeDeg)}"
    val body = when (kind) {
        StormBulletinKind.FORMED ->
            "A ${storm.type.displayName.lowercase()} has organised at $position. " +
                "Forecasters expect production in its path to suffer while it holds together."
        StormBulletinKind.INTENSIFIED ->
            "${storm.displayName} has strengthened to ${storm.severity.displayName.lowercase()} " +
                "at $position. Grid operators are reporting losses."
        StormBulletinKind.DISSIPATED ->
            "${storm.displayName} has broken up. Production in the affected regions is recovering."
    }
    return NewsBulletin(
        headline = headline,
        body = body,
        source = "STORM WATCH · ${position.uppercase()}",
        icon = storm.type.icon,
    )
}

/** Feed key for a storm headline. Unique per storm per kind, so nothing is written twice. */
fun StormBulletin.newsId(): String = "storm:${storm.id}:${kind.name.lowercase()}"
