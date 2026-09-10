package com.earthgame.idle.domain.challenges

import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.prestige.PrestigeUpgradeEffect
import com.earthgame.idle.domain.technologies.ALL_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.TECH_BY_ID
import com.earthgame.idle.domain.technologies.TechBranch
import com.earthgame.idle.domain.technologies.Technology

data class ChallengeRestriction(
    /** Technologies that cannot be purchased while this challenge is active. */
    val disabledTechIds: Set<String> = emptySet(),
    /** Technologies may not exceed this tier (e.g. "Primitive": stop at the Iron Age). */
    val maxTechTier: Int? = null,
    /** The run fails if temperature ever exceeds this. */
    val maxTemperatureC: Double? = null,
    /** Only technologies producing exclusively these gases (plus gasless unlocks) may be purchased. */
    val onlyGasIds: Set<GasId>? = null,
)

data class ChallengeGoal(
    val description: String,
    val check: (state: GameState, civLevel: Int) -> Boolean,
)

data class Challenge(
    val id: String,
    val displayName: String,
    val description: String,
    val icon: String,
    val restriction: ChallengeRestriction,
    val goal: ChallengeGoal,
    val rewardDescription: String,
    val reward: PrestigeUpgradeEffect,
)

private fun techProducesOnly(tech: Technology, allowedGases: Set<GasId>): Boolean =
    tech.effect.gasProductionPerUnit.keys.all { it in allowedGases }

val CHALLENGES: List<Challenge> = listOf(
    Challenge(
        id = "ice_age",
        displayName = "Ice Age",
        description = "Reach a strong industrial base while keeping temperature below +2°C.",
        icon = "🧊",
        restriction = ChallengeRestriction(maxTemperatureC = 2.0),
        goal = ChallengeGoal("Reach civilization level 40 without exceeding +2°C.") { _, civLevel -> civLevel >= 40 },
        rewardDescription = "+10% permanent production",
        reward = PrestigeUpgradeEffect(globalProductionMultiplier = 1.1),
    ),
    Challenge(
        id = "low_carbon",
        displayName = "Low Carbon",
        description = "Coal technologies are forbidden for the entire run.",
        icon = "🚫",
        restriction = ChallengeRestriction(
            disabledTechIds = ALL_TECHNOLOGIES.filter { it.id.contains("coal") }.map { it.id }.toSet(),
        ),
        goal = ChallengeGoal("Accumulate 1 trillion (1e12) total Energy produced.") { state, _ ->
            state.resources[ResourceId.ENERGY].gte(gd("1e12"))
        },
        rewardDescription = "+25% Research, permanently",
        reward = PrestigeUpgradeEffect(researchMultiplier = 1.25),
    ),
    Challenge(
        id = "no_oil",
        displayName = "No Oil",
        description = "Oil and its downstream technologies are off-limits.",
        icon = "🛢️",
        restriction = ChallengeRestriction(
            disabledTechIds = ALL_TECHNOLOGIES.filter {
                it.branch == TechBranch.FOSSIL_FUELS ||
                    it.id in setOf("automobile", "diesel_engine", "jet_aircraft", "petrochemicals")
            }.map { it.id }.toSet(),
        ),
        goal = ChallengeGoal("Reach civilization level 35 without any oil technology.") { _, civLevel -> civLevel >= 35 },
        // The reference implementation's copy here advertised a Transportation
        // branch bonus that its own effect never granted. The effect is the
        // shipped balance and is left exactly as it is; the text is what was
        // wrong, so the text is what changed.
        rewardDescription = "+5% permanent production",
        reward = PrestigeUpgradeEffect(globalProductionMultiplier = 1.05),
    ),
    Challenge(
        id = "primitive",
        displayName = "Primitive",
        description = "Never advance past the Iron Age.",
        icon = "⛏️",
        restriction = ChallengeRestriction(maxTechTier = 6),
        goal = ChallengeGoal("Reach 400 ppm atmospheric CO₂ using only Primitive-era technology.") { state, _ ->
            state.atmosphere[GasId.CO2].gte(gd(120))
        },
        rewardDescription = "Start every future run with extra starting Energy",
        reward = PrestigeUpgradeEffect(startingResources = mapOf(ResourceId.ENERGY to 500.0)),
    ),
    Challenge(
        id = "speedrun",
        displayName = "Speedrun",
        description = "Reset Earth as quickly as possible.",
        icon = "⏱️",
        restriction = ChallengeRestriction(),
        // Scored against the run's own elapsed time rather than a wall clock
        // read inside the check, so the same state always gives the same answer.
        goal = ChallengeGoal("Reset within 15 minutes of starting the run.") { state, _ ->
            (state.lastTickAt - state.runStartedAt) / 1000.0 <= 900
        },
        // A ×1.0 multiplier is not a bonus, and the reference implementation's
        // copy promised one anyway: there is no "Earth Points earned" field in
        // PrestigeUpgradeEffect for it to have been wired to. Rather than
        // invent a balance change, the reward is described for what it is —
        // adding that field is tracked in docs/CODEBASE_AUDIT.md.
        rewardDescription = "Bragging rights — this one pays no permanent bonus",
        reward = PrestigeUpgradeEffect(globalProductionMultiplier = 1.0),
    ),
    Challenge(
        id = "single_gas",
        displayName = "Single Gas",
        description = "Only technologies that emit CO₂ (or nothing) may be used.",
        icon = "☁️",
        restriction = ChallengeRestriction(
            disabledTechIds = ALL_TECHNOLOGIES
                .filterNot { techProducesOnly(it, setOf(GasId.CO2)) }
                .map { it.id }.toSet(),
        ),
        goal = ChallengeGoal("Reach civilization level 30 using only CO₂-producing technology.") { _, civLevel -> civLevel >= 30 },
        rewardDescription = "+30% CO₂ production, permanently",
        reward = PrestigeUpgradeEffect(gasProductionMultiplier = GasId.CO2 to 1.3),
    ),
    Challenge(
        id = "methane_world_challenge",
        displayName = "Methane World",
        description = "Make CH₄ the dominant greenhouse gas by the time you reset.",
        icon = "🐄",
        restriction = ChallengeRestriction(),
        goal = ChallengeGoal("Reset while CH₄ contributes more forcing than any other gas.") { state, _ ->
            state.forcing.perGas[GasId.CH4] > state.forcing.perGas[GasId.CO2] &&
                state.forcing.perGas[GasId.CH4] > 0
        },
        rewardDescription = "+50% CH₄ production, permanently",
        reward = PrestigeUpgradeEffect(gasProductionMultiplier = GasId.CH4 to 1.5),
    ),
    Challenge(
        id = "zero_emissions",
        displayName = "Zero Emissions",
        description = "Build a strong civilization while keeping gross emissions minimal.",
        icon = "🌱",
        restriction = ChallengeRestriction(
            disabledTechIds = setOf("coal_power_plant", "supercritical_coal", "coal_mega_mining", "oil_sands"),
        ),
        // The goal text is the reference implementation's and is pinned by
        // `ContentParityTest`; the emission ceiling it mentions is enforced by
        // the disabled-technology list above rather than by a live check on the
        // production rate. See docs/CODEBASE_AUDIT.md.
        goal = ChallengeGoal("Reach civilization level 25 while total gross gas production stays under 1e6 kg/s.") { _, civLevel ->
            civLevel >= 25
        },
        rewardDescription = "+20% Research, permanently",
        reward = PrestigeUpgradeEffect(researchMultiplier = 1.2),
    ),
)

val CHALLENGE_BY_ID: Map<String, Challenge> = CHALLENGES.associateBy { it.id }

/** Whether purchasing [techId] would violate [challenge]'s restriction. */
fun isTechRestrictedByChallenge(challenge: Challenge, techId: String): Boolean {
    val tech = TECH_BY_ID[techId] ?: return false
    if (techId in challenge.restriction.disabledTechIds) return true
    challenge.restriction.maxTechTier?.let { if (tech.tier > it) return true }
    val onlyGases = challenge.restriction.onlyGasIds
    if (onlyGases != null && tech.effect.gasProductionPerUnit.isNotEmpty()) {
        if (!techProducesOnly(tech, onlyGases)) return true
    }
    return false
}

/**
 * The full set of technologies an active challenge locks out, precomputed once
 * per state change rather than per card render.
 */
fun disabledTechIdsForChallenge(challenge: Challenge?): Set<String> {
    if (challenge == null) return emptySet()
    return ALL_TECHNOLOGIES.filter { isTechRestrictedByChallenge(challenge, it.id) }.map { it.id }.toSet()
}

/** Whether the challenge's live restriction (e.g. a temperature ceiling) has been broken this run. */
fun isChallengeRestrictionViolated(challenge: Challenge, state: GameState): Boolean {
    val ceiling = challenge.restriction.maxTemperatureC ?: return false
    return state.temperatureAnomalyC > ceiling
}

fun isChallengeGoalMet(challenge: Challenge, state: GameState, civLevel: Int): Boolean =
    challenge.goal.check(state, civLevel)

/** Every completed challenge's permanent reward, in the shape prestige upgrades use. */
fun computeChallengeRewardEffects(completed: Map<String, Boolean>): List<PrestigeUpgradeEffect> =
    CHALLENGES.filter { completed[it.id] == true }.map { it.reward }
