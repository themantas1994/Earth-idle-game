package com.earthgame.idle.domain.model

import com.earthgame.idle.domain.climate.AtmosphereState
import com.earthgame.idle.domain.climate.ForcingBreakdown
import com.earthgame.idle.domain.climate.HabitabilityResult
import com.earthgame.idle.domain.climate.createInitialAtmosphere
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.formatting.NumberFormatMode

/** Stats that reset to zero at the start of every run (the prestige/collapse summary). */
data class RunStats(
    val startedAt: Long,
    val totalGasProducedKg: GasAmounts = GasAmounts.ZERO,
    val peakForcingWm2: Double = 0.0,
    val peakTemperatureC: Double = 0.0,
    val peakCo2Ppm: Double = 280.0,
    val peakGasProductionRateKgPerS: GameDecimal = GameDecimal.ZERO,
)

/** Stats that persist forever across resets, shown on the Statistics screen. */
data class LifetimeStats(
    val totalPlayTimeSeconds: Double = 0.0,
    val totalResets: Int = 0,
    val totalGasProducedKg: GasAmounts = GasAmounts.ZERO,
    val highestTemperatureC: Double = 0.0,
    val highestCo2Ppm: Double = 280.0,
    val fastestResetSeconds: Double? = null,
    val longestRunSeconds: Double = 0.0,
    val totalTechnologiesPurchased: Int = 0,
    val totalEarthPointsEarned: GameDecimal = GameDecimal.ZERO,
)

enum class ThemePreference(val id: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromId(id: String?): ThemePreference = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

data class Settings(
    val numberFormat: NumberFormatMode = NumberFormatMode.COMPACT,
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val reducedAnimations: Boolean = false,
    val darkMode: ThemePreference = ThemePreference.SYSTEM,
    val confirmReset: Boolean = true,
    val offlineProgressEnabled: Boolean = true,
) {
    companion object {
        val DEFAULT = Settings()
    }
}

/** A random event currently running, with the wall-clock window it applies over. */
data class ActiveEvent(
    val id: String,
    val eventDefId: String,
    val startedAt: Long,
    val endsAt: Long,
)

/**
 * One headline in the world-news feed. Milestones write these as the planet
 * crosses thresholds, so a run has a readable narrative of what the player's
 * emissions actually did rather than only a temperature readout.
 */
data class NewsItem(
    /** Milestone definition id — also the per-run dedupe key. */
    val milestoneId: String,
    /** Wall-clock ms the headline fired at. */
    val at: Long,
    /** Seconds into the run, so the feed still reads correctly after a reload. */
    val runSeconds: Double,
)

/** Newest-first cap on the per-run news feed, so a long run cannot grow the save without bound. */
const val NEWS_FEED_LIMIT = 40

data class PrestigeState(
    val earthPoints: GameDecimal = GameDecimal.ZERO,
    val upgradesOwned: Map<String, Int> = emptyMap(),
)

data class ChallengeState(
    val activeId: String? = null,
    val completed: Map<String, Boolean> = emptyMap(),
)

data class TutorialState(
    val step: Int = 0,
    val completed: Boolean = false,
    val skipped: Boolean = false,
)

/**
 * The complete state of a playthrough. Everything the simulation reads or
 * writes lives here, and everything here is serialized into the save.
 *
 * It is an immutable data class: [com.earthgame.idle.domain.engine.simulateStep]
 * and every action take one and return the next, which is what makes the engine
 * testable without a device and what lets the UI diff cheaply between frames.
 */
data class GameState(
    val saveVersion: Int = SAVE_VERSION,
    /** 0 = "EARTH", 1 = "EARTH 1", 2 = "EARTH 2", ... */
    val runNumber: Int = 0,
    val runStartedAt: Long,
    val lastTickAt: Long,
    val createdAt: Long,

    val resources: ResourceAmounts = ResourceAmounts.ZERO,
    val atmosphere: AtmosphereState = createInitialAtmosphere(),
    val seaLevelRiseMeters: Double = 0.0,
    val previousTemperatureAnomalyC: Double = 0.0,

    val techOwned: Map<String, Int> = emptyMap(),

    // Cached derived values, recomputed every tick, kept on state for cheap UI reads.
    val temperatureAnomalyC: Double = 0.0,
    val forcing: ForcingBreakdown = ForcingBreakdown.ZERO,
    val habitability: HabitabilityResult = HabitabilityResult.PRISTINE,
    val oceanPh: Double = 8.1,

    val runStats: RunStats,
    val lifetimeStats: LifetimeStats = LifetimeStats(),

    val prestige: PrestigeState = PrestigeState(),

    val achievementsUnlocked: Map<String, Boolean> = emptyMap(),
    val challenges: ChallengeState = ChallengeState(),

    val activeEvents: List<ActiveEvent> = emptyList(),

    /** Milestone ids already fired this run (each headline fires at most once per Earth). */
    val milestonesTriggered: Map<String, Boolean> = emptyMap(),
    /** Newest-first world-news headlines for the current run. */
    val newsFeed: List<NewsItem> = emptyList(),

    val settings: Settings = Settings.DEFAULT,
    val tutorial: TutorialState = TutorialState(),

    val collapsed: Boolean = false,
)

const val SAVE_VERSION = 3

/**
 * Technologies every run starts with already owned. Natural Fire predates any
 * deliberate human action — a lightning strike, not an invention — and it is
 * the game's only unconditional source of Energy. Since every purchase is
 * denominated in resources that generators produce, the player has to be handed
 * one running generator or the economy can never start; that first burning tree
 * is it.
 */
val STARTING_TECH_IDS = listOf("natural_fire")

private fun initialTechOwned(): Map<String, Int> = STARTING_TECH_IDS.associateWith { 1 }

fun createInitialRunStats(now: Long): RunStats = RunStats(startedAt = now)

fun createNewGame(now: Long): GameState = GameState(
    runStartedAt = now,
    lastTickAt = now,
    createdAt = now,
    techOwned = initialTechOwned(),
    runStats = createInitialRunStats(now),
)

/**
 * Starts a fresh run after a reset, preserving exactly what carries between
 * Earths: prestige, achievements, challenge completions, lifetime stats,
 * settings and tutorial progress. Everything else — resources, atmosphere,
 * technology, per-run stats, news, events — starts over.
 */
fun startNewRun(previous: GameState, now: Long): GameState = previous.copy(
    runNumber = previous.runNumber + 1,
    runStartedAt = now,
    lastTickAt = now,

    resources = ResourceAmounts.ZERO,
    atmosphere = createInitialAtmosphere(),
    seaLevelRiseMeters = 0.0,
    previousTemperatureAnomalyC = 0.0,

    techOwned = initialTechOwned(),

    temperatureAnomalyC = 0.0,
    forcing = ForcingBreakdown.ZERO,
    habitability = HabitabilityResult.PRISTINE,
    oceanPh = 8.1,

    runStats = createInitialRunStats(now),

    activeEvents = emptyList(),
    milestonesTriggered = emptyMap(),
    newsFeed = emptyList(),
    collapsed = false,
)
