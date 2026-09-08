package com.earthgame.idle.domain.achievements

import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.engine.ownershipMultiplier
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.technologies.ALL_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.TechBranch
import com.earthgame.idle.domain.technologies.TechKind

/**
 * Everything an achievement check can look at. Most read state alone; the
 * reset-flavoured ones need to know that a reset just happened and how the run
 * that ended compared with the one before it, which state does not record.
 */
data class AchievementContext(
    val state: GameState,
    val civLevel: Int,
    val justCollapsed: Boolean = false,
    val justReset: Boolean = false,
    val lastRunDurationSeconds: Double? = null,
    val previousRunDurationSeconds: Double? = null,
)

data class Achievement(
    val id: String,
    val displayName: String,
    val description: String,
    val icon: String,
    val check: (AchievementContext) -> Boolean,
)

private fun owned(state: GameState, techId: String): Int = state.techOwned[techId] ?: 0

private fun maxGeneratorOwned(state: GameState): Int {
    var maximum = 0
    for (tech in ALL_TECHNOLOGIES) {
        if (tech.kind != TechKind.GENERATOR) continue
        maximum = maxOf(maximum, owned(state, tech.id))
    }
    return maximum
}

private fun ownsAnyChoiceTech(state: GameState): Boolean =
    ALL_TECHNOLOGIES.any { it.choiceGroup != null && owned(state, it.id) > 0 }

private fun ownsAtLeastOneTechInEveryBranch(state: GameState): Boolean {
    val branches = TechBranch.entries.toMutableSet()
    for (tech in ALL_TECHNOLOGIES) {
        if (owned(state, tech.id) > 0) branches.remove(tech.branch)
    }
    return branches.isEmpty()
}

val ACHIEVEMENTS: List<Achievement> = listOf(
    Achievement("first_spark", "First Spark", "Discover Controlled Fire.", "🔥") {
        owned(it.state, "controlled_fire") > 0
    },
    Achievement("first_emission", "First Emission", "Produce your first greenhouse gas.", "💨") {
        it.state.lifetimeStats.totalGasProducedKg[GasId.CO2].gt(GameDecimal.ZERO)
    },
    Achievement("rise_of_agriculture", "Rise of Agriculture", "Unlock Early Agriculture.", "🌱") {
        owned(it.state, "early_agriculture") > 0
    },
    Achievement("industrial_revolution", "Industrial Revolution", "Build your first Factory.", "🏭") {
        owned(it.state, "factories") > 0
    },
    Achievement("let_there_be_light", "Let There Be Light", "Generate your first electricity.", "💡") {
        owned(it.state, "generator_dynamo") > 0
    },
    Achievement("fossil_fever", "Fossil Fever", "Start drilling for oil.", "🛢️") {
        owned(it.state, "oil_drilling") > 0
    },
    Achievement("carbon_age", "Carbon Age", "Reach 1,000 ppm atmospheric CO₂.", "☁️") {
        it.state.lifetimeStats.highestCo2Ppm >= 1000
    },
    Achievement("hot", "Hot", "Reach +5°C global temperature anomaly.", "🌡️") {
        it.state.lifetimeStats.highestTemperatureC >= 5
    },
    Achievement("very_hot", "Very Hot", "Reach +10°C global temperature anomaly.", "🌡️") {
        it.state.lifetimeStats.highestTemperatureC >= 10
    },
    Achievement("scorching", "Scorching", "Reach +25°C global temperature anomaly.", "🔥") {
        it.state.lifetimeStats.highestTemperatureC >= 25
    },
    Achievement("apocalyptic_heat", "Apocalyptic Heat", "Reach +50°C global temperature anomaly.", "☠️") {
        it.state.lifetimeStats.highestTemperatureC >= 50
    },
    Achievement("oops", "Oops", "Make Earth uninhabitable.", "💀") { it.justCollapsed },
    Achievement("again", "Again?", "Reset Earth for the first time.", "🔄") {
        it.justReset && it.state.lifetimeStats.totalResets == 1
    },
    Achievement("faster_this_time", "Faster This Time", "Reset faster than your previous run.", "⏱️") {
        it.justReset &&
            it.lastRunDurationSeconds != null &&
            it.previousRunDurationSeconds != null &&
            it.lastRunDurationSeconds < it.previousRunDurationSeconds
    },
    // A first run runs to several days; twelve hours is what a deep prestige
    // stack and a well-drilled route can do to that, not a target for a fresh
    // Earth.
    Achievement("civilization_speedrun", "Civilization Speedrun", "Reach the reset condition in under 12 hours.", "⚡") {
        it.justReset && it.lastRunDurationSeconds != null && it.lastRunDurationSeconds <= 12 * 3600
    },
    // 100 rather than a rounder 1,000: per-unit costs grow ~15% a unit, so the
    // thousandth copy of anything costs some 10^60 times the first and no run
    // will ever buy it. A completed run tops out somewhere under 100 of its
    // favourite building, which makes this a real target rather than a
    // decoration — and it lands on the tenth ownership doubling.
    Achievement("industrial_monster", "Industrial Monster", "Own 100 of any single generator.", "🏗️") {
        maxGeneratorOwned(it.state) >= 100
    },
    Achievement("economies_of_scale", "Economies of Scale", "Push a single building to a ×32 ownership bonus.", "⚡") {
        ownershipMultiplier(maxGeneratorOwned(it.state)) >= 32
    },
    Achievement("bigger_than_earth", "Bigger Than Earth", "Accumulate 1 quintillion (1e18) of any resource.", "🌌") { ctx ->
        ctx.state.resources.any { amount -> amount.gte(gd("1e18")) }
    },
    Achievement("methane_world", "Methane World", "Make CH₄ your atmosphere's dominant warming contributor.", "🐄") {
        it.state.forcing.perGas[GasId.CH4] > it.state.forcing.perGas[GasId.CO2] &&
            it.state.forcing.perGas[GasId.CH4] > 0
    },
    Achievement("ozone_hole", "Ozone Hole", "Deplete the ozone layer with CFCs.", "🕳️") {
        it.state.atmosphere[GasId.O3].lt(gd(-50))
    },
    Achievement("rising_tides", "Rising Tides", "Cause 50m of sea level rise.", "🌊") {
        it.state.seaLevelRiseMeters >= 50
    },
    Achievement("mass_extinction", "Mass Extinction", "Reduce biodiversity below 10%.", "🦴") {
        it.state.habitability.factors.biodiversity <= 0.1
    },
    Achievement("acid_ocean", "Acid Ocean", "Drop ocean pH to 7.0 or below.", "🧪") {
        it.state.oceanPh <= 7.0
    },
    Achievement("new_beginning", "New Beginning", "Earn your first Earth Points.", "✨") {
        it.state.prestige.earthPoints.gt(GameDecimal.ZERO)
    },
    Achievement("wealthy_civilization", "Wealthy Civilization", "Accumulate 1,000,000 Earth Points.", "💰") {
        it.state.prestige.earthPoints.gte(gd(1_000_000))
    },
    Achievement("hearth_keeper", "Hearth Keeper", "Keep 50 Natural Fires burning at once.", "🔥") {
        owned(it.state, "natural_fire") >= 50
    },
    Achievement("front_page", "Front Page", "Make the world news 10 times in a single run.", "📰") {
        it.state.newsFeed.size >= 10
    },
    Achievement("road_not_taken", "The Road Not Taken", "Commit to a strategic technology choice.", "🔀") {
        ownsAnyChoiceTech(it.state)
    },
    Achievement("digital_age", "Digital Age", "Build your first Computer.", "💻") {
        owned(it.state, "computers") > 0
    },
    Achievement("into_the_absurd", "Into the Absurd", "Harness Stellar Energy.", "🌟") {
        owned(it.state, "stellar_energy") > 0
    },
    Achievement("serial_destroyer", "Serial Destroyer", "Reset Earth 10 times.", "♻️") {
        it.state.lifetimeStats.totalResets >= 10
    },
    Achievement("renaissance_civilization", "Renaissance Civilization", "Own at least one technology from every branch.", "🎓") {
        ownsAtLeastOneTechInEveryBranch(it.state)
    },
)

val ACHIEVEMENT_BY_ID: Map<String, Achievement> = ACHIEVEMENTS.associateBy { it.id }

/** The ids of achievements that just became unlocked (not already in [unlockedSoFar]). */
fun checkAchievements(context: AchievementContext, unlockedSoFar: Map<String, Boolean>): List<String> {
    var newlyUnlocked: MutableList<String>? = null
    for (achievement in ACHIEVEMENTS) {
        if (unlockedSoFar[achievement.id] == true) continue
        if (achievement.check(context)) {
            (newlyUnlocked ?: mutableListOf<String>().also { newlyUnlocked = it }).add(achievement.id)
        }
    }
    return newlyUnlocked ?: emptyList()
}
