package com.earthgame.idle.domain.engine

import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.GasAmounts
import com.earthgame.idle.domain.model.ResourceAmounts
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.domain.storms.StormBulletin
import com.earthgame.idle.domain.storms.StormBulletinKind
import kotlin.math.max
import kotlin.math.min

data class OfflineProgressSummary(
    val gasGeneratedKg: GasAmounts,
    val resourcesGained: ResourceAmounts,
    val temperatureBeforeC: Double,
    val temperatureAfterC: Double,
    /** Storms that formed while the player was away. */
    val stormsFormed: Int = 0,
    /** Storms still on the board on their return. */
    val stormsActive: Int = 0,
)

data class OfflineProgressResult(
    val awaySeconds: Double,
    val simulatedSeconds: Double,
    val cappedByLimit: Boolean,
    val offlineCapSeconds: Double,
    val state: GameState,
    val summary: OfflineProgressSummary,
    /** Everything the weather did during the absence, for the news feed. */
    val stormBulletins: List<StormBulletin> = emptyList(),
)

fun offlineCapSeconds(prestige: PrestigeMultipliers): Double =
    SIMULATION.BASE_OFFLINE_CAP_SECONDS * prestige.offlineCapMultiplier

/**
 * Fast-forwards [state] from its last simulated tick up to [nowMs].
 *
 * Because [simulateStep]'s gas-concentration and resource maths is step-size
 * independent, the whole offline gap collapses into a single step rather than a
 * loop — the result is identical to having played it out live, minus the
 * purchases the player was not there to make. That property is what makes
 * offline progress one calculation instead of a stepped simulation, and it is
 * asserted directly by `SimulationParityTest.step size independence`.
 */
fun computeOfflineProgress(
    state: GameState,
    nowMs: Long,
    prestige: PrestigeMultipliers,
    offlineProgressEnabled: Boolean,
): OfflineProgressResult {
    val awaySeconds = max(0.0, (nowMs - state.lastTickAt) / 1000.0)
    val cap = offlineCapSeconds(prestige)

    if (!offlineProgressEnabled || awaySeconds <= 0) {
        return OfflineProgressResult(
            awaySeconds = awaySeconds,
            simulatedSeconds = 0.0,
            cappedByLimit = awaySeconds > cap,
            offlineCapSeconds = cap,
            state = state.copy(lastTickAt = nowMs),
            summary = emptySummary(state.temperatureAnomalyC),
        )
    }

    val simulatedSeconds = min(awaySeconds, cap)

    // The weather is settled in two passes, and the order matters.
    //
    // First the storms that were *already running* when the player left are
    // priced, averaged across the window over their own intensity curves —
    // the analytic settlement an absence gets instead of a replayed frame
    // loop. Then production runs with that penalty folded in. Only afterwards
    // does the storm timeline itself advance, against the climate the step
    // produced, which is what lets a storm form, intensify, move and dissipate
    // entirely while the app was closed.
    //
    // A player who left under clear skies is charged exactly nothing, and the
    // call below is then bit-for-bit the one this function has always made.
    val stormPenalty = stormMultipliersForAbsence(state, simulatedSeconds)
    val extras = if (stormPenalty.isEmpty) emptyList() else listOf(stormPenalty)

    val stepped = simulateStep(state, simulatedSeconds, prestige, extras).state
    val weather = advanceStormsFor(stepped, simulatedSeconds)
    val finalState = stepped.copy(lastTickAt = nowMs, storms = weather.field)

    return OfflineProgressResult(
        awaySeconds = awaySeconds,
        simulatedSeconds = simulatedSeconds,
        cappedByLimit = awaySeconds > cap,
        offlineCapSeconds = cap,
        state = finalState,
        summary = OfflineProgressSummary(
            gasGeneratedKg = stepped.runStats.totalGasProducedKg.minus(state.runStats.totalGasProducedKg),
            resourcesGained = stepped.resources.minus(state.resources),
            temperatureBeforeC = state.temperatureAnomalyC,
            temperatureAfterC = stepped.temperatureAnomalyC,
            stormsFormed = weather.bulletins.count { it.kind == StormBulletinKind.FORMED },
            stormsActive = finalState.storms.storms.size,
        ),
        stormBulletins = weather.bulletins,
    )
}

private fun emptySummary(tempC: Double) = OfflineProgressSummary(
    gasGeneratedKg = GasAmounts.ZERO,
    resourcesGained = ResourceAmounts.ZERO,
    temperatureBeforeC = tempC,
    temperatureAfterC = tempC,
)
