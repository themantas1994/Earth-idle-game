package com.earthgame.idle.domain.storms

import com.earthgame.idle.domain.engine.MultiplierContribution
import com.earthgame.idle.domain.engine.STORMS
import com.earthgame.idle.domain.technologies.TechBranch
import kotlin.math.max
import kotlin.math.min

/**
 * What storms cost the player, and the single place that arithmetic happens.
 *
 * Two rules shape all of it, and both exist to stop storms from becoming a
 * spiral the player cannot climb out of:
 *
 * 1. **Diminishing returns.** Within one penalty channel the storms are sorted
 *    strongest-first and each subsequent one counts for only
 *    [STORMS.STACKING_FALLOFF] of the one before it. Six storms are worse than
 *    one, but nowhere near six times worse.
 * 2. **A hard cap.** Whatever is running, the global penalty never exceeds
 *    [STORMS.MAX_GLOBAL_PENALTY] and no branch penalty exceeds
 *    [STORMS.MAX_BRANCH_PENALTY]. Production can be badly hurt; it can never be
 *    zeroed, and it always recovers completely when the weather passes.
 *
 * Nothing a storm does is permanent. Storms take a slice off production while
 * they are on the board and nothing at all once they are gone — no destroyed
 * buildings, no lost resources, no progress a player has to earn back. That is
 * a deliberate fit with the rest of the game, where prices never rise and no
 * multiplier is ever taken away.
 */

/** The penalties one storm is currently contributing, already scaled by its intensity. */
data class StormContribution(
    val storm: Storm,
    val globalPenalty: Double,
    val branchPenalties: Map<TechBranch, Double>,
) {
    val isNeutral: Boolean get() = globalPenalty <= 0.0 && branchPenalties.values.all { it <= 0.0 }
}

/** The combined, capped effect of every live storm. */
data class StormEffectSummary(
    /** Production multiplier applied to every generator, in (0, 1]. */
    val globalMultiplier: Double,
    /** Extra per-branch multipliers, in (0, 1]. Absent means 1.0. */
    val branchMultipliers: Map<TechBranch, Double>,
) {
    val globalPenalty: Double get() = 1.0 - globalMultiplier

    fun branchPenalty(branch: TechBranch): Double = 1.0 - (branchMultipliers[branch] ?: 1.0)

    val isNeutral: Boolean
        get() = globalMultiplier >= 1.0 && branchMultipliers.values.all { it >= 1.0 }

    /** The transient multiplier layer the simulation folds in, alongside random events. */
    fun asMultiplierContribution(): MultiplierContribution =
        if (isNeutral) {
            MultiplierContribution.NONE
        } else {
            MultiplierContribution(
                global = globalMultiplier.takeIf { it < 1.0 },
                perBranch = branchMultipliers.filterValues { it < 1.0 },
            )
        }

    companion object {
        val NONE = StormEffectSummary(1.0, emptyMap())
    }
}

/** What one storm is costing right now, for its own information card. */
fun stormContribution(storm: Storm): StormContribution = stormContribution(storm, storm.intensity)

private fun stormContribution(storm: Storm, intensity: Double): StormContribution {
    val scale = intensity.coerceIn(0.0, 1.0)
    val profile = storm.type.effect
    return StormContribution(
        storm = storm,
        globalPenalty = profile.globalPenalty * scale,
        branchPenalties = profile.branchPenalties.mapValues { (_, penalty) -> penalty * scale },
    )
}

/** The combined effect of [storms] at their current intensities. */
fun computeStormEffects(storms: List<Storm>): StormEffectSummary {
    if (storms.isEmpty()) return StormEffectSummary.NONE
    return combine(storms.map(::stormContribution))
}

/**
 * The combined effect of [storms] averaged across an absence of
 * [windowSeconds] — how an offline gap is settled.
 *
 * Only the storms that were *already running* when the player left are
 * counted, and each one only for the part of the window it was alive for. A
 * storm that forms during an absence costs nothing until the player is back:
 * it is on the board when they return, doing exactly what the simulation says
 * it should be doing, but the time they were not there is not billed to them.
 * That is the same bargain the random-event system already makes, and it is
 * what keeps `computeOfflineProgress` bit-identical to a live session for a
 * player who left with clear skies.
 *
 * The average is taken over each storm's own intensity curve rather than its
 * intensity at the moment of leaving, so a storm that was about to peak is
 * charged for its peak and one that was already dying is not.
 */
fun averageStormEffectsOverWindow(
    storms: List<Storm>,
    windowSeconds: Double,
    sustain: Double,
): StormEffectSummary {
    if (storms.isEmpty() || windowSeconds <= 0.0) return StormEffectSummary.NONE
    val contributions = storms.map { storm ->
        stormContribution(storm, averageIntensityOverWindow(storm, windowSeconds, sustain))
    }
    return combine(contributions)
}

/**
 * The mean intensity [storm] holds over the next [windowSeconds], counting the
 * part of the window it is dead for as zero.
 *
 * Sampled rather than integrated in closed form: the intensity profile is a
 * piecewise smoothstep, and a fixed midpoint sum over [INTENSITY_SAMPLES] is
 * accurate to well under a percent while staying a handful of multiplications
 * and, crucially, identical on every device.
 */
fun averageIntensityOverWindow(storm: Storm, windowSeconds: Double, sustain: Double): Double {
    val alive = min(windowSeconds, storm.remainingSeconds)
    if (alive <= 0.0 || storm.lifetimeSeconds <= 0.0) return 0.0

    var total = 0.0
    for (sample in 0 until INTENSITY_SAMPLES) {
        val offset = (sample + 0.5) / INTENSITY_SAMPLES * alive
        total += intensityProfile((storm.ageSeconds + offset) / storm.lifetimeSeconds)
    }
    val meanProfile = total / INTENSITY_SAMPLES

    // Scaled by the share of the window the storm was actually alive for.
    val aliveShare = alive / windowSeconds
    return (storm.targetIntensity * meanProfile * sustain * aliveShare).coerceIn(0.0, 1.0)
}

private const val INTENSITY_SAMPLES = 24

/**
 * Folds per-storm penalties into one capped summary.
 *
 * Each channel is treated on its own: strongest first, each further storm
 * discounted by [STORMS.STACKING_FALLOFF], and the running total clamped.
 */
private fun combine(contributions: List<StormContribution>): StormEffectSummary {
    val live = contributions.filterNot { it.isNeutral }
    if (live.isEmpty()) return StormEffectSummary.NONE

    val global = stackPenalties(
        live.map { it.globalPenalty }.filter { it > 0.0 },
        STORMS.MAX_GLOBAL_PENALTY,
    )

    val branches = mutableMapOf<TechBranch, Double>()
    for (branch in TechBranch.entries) {
        val penalties = live.mapNotNull { it.branchPenalties[branch]?.takeIf { p -> p > 0.0 } }
        if (penalties.isEmpty()) continue
        val stacked = stackPenalties(penalties, STORMS.MAX_BRANCH_PENALTY)
        if (stacked > 0.0) branches[branch] = 1.0 - stacked
    }

    return StormEffectSummary(
        globalMultiplier = 1.0 - global,
        branchMultipliers = branches.toMap(),
    )
}

/** Strongest first, each subsequent penalty discounted, total clamped to [cap]. */
private fun stackPenalties(penalties: List<Double>, cap: Double): Double {
    if (penalties.isEmpty()) return 0.0
    var weight = 1.0
    var total = 0.0
    for (penalty in penalties.sortedDescending()) {
        total += penalty * weight
        weight *= STORMS.STACKING_FALLOFF
    }
    return min(cap, max(0.0, total))
}
