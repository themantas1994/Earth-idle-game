package com.earthgame.idle.domain.engine

import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.GasAmounts
import com.earthgame.idle.domain.model.ResourceAmounts
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import kotlin.math.max
import kotlin.math.min

data class OfflineProgressSummary(
    val gasGeneratedKg: GasAmounts,
    val resourcesGained: ResourceAmounts,
    val temperatureBeforeC: Double,
    val temperatureAfterC: Double,
)

data class OfflineProgressResult(
    val awaySeconds: Double,
    val simulatedSeconds: Double,
    val cappedByLimit: Boolean,
    val offlineCapSeconds: Double,
    val state: GameState,
    val summary: OfflineProgressSummary,
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
    val stepped = simulateStep(state, simulatedSeconds, prestige).state
    val finalState = stepped.copy(lastTickAt = nowMs)

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
        ),
    )
}

private fun emptySummary(tempC: Double) = OfflineProgressSummary(
    gasGeneratedKg = GasAmounts.ZERO,
    resourcesGained = ResourceAmounts.ZERO,
    temperatureBeforeC = tempC,
    temperatureAfterC = tempC,
)
