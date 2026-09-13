package com.earthgame.idle.domain.model

import com.earthgame.idle.domain.climate.AtmosphereState
import com.earthgame.idle.domain.climate.ForcingBreakdown
import com.earthgame.idle.domain.climate.HabitabilityResult
import com.earthgame.idle.domain.climate.createInitialAtmosphere
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.storms.StormField
import com.earthgame.idle.domain.storms.deriveStormSeed
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
    /**
     * Simulated time across every Earth ever played, in simulated seconds.
     *
     * The lifetime counterpart of [GameState.gameAgeSeconds], which resets with
     * each new Earth. Kept here precisely so a prestige never destroys it.
     */
    val totalSimulatedSeconds: Double = 0.0,
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

/**
 * How much work the Home screen's globe is allowed to do.
 *
 * Purely a rendering budget. **No setting here changes the simulation**: the
 * same storms form, move and cost the same production whichever one is
 * selected, and every number the globe draws is also printed as text beside it.
 * Turning the effects down changes what the player *sees*, never what happens.
 */
enum class GraphicsQuality(
    val id: String,
    val displayName: String,
    val description: String,
) {
    HIGH("high", "Full", "Clouds, wind particles, storm effects and auto-rotation."),
    MEDIUM("medium", "Reduced", "Fewer particles, no cloud layer. Easier on the battery."),
    LOW("low", "Minimal", "A still globe with overlays and markers. Lowest power."),
    ;

    /** Whether the animated cloud shell is drawn. */
    val cloudsEnabled: Boolean get() = this == HIGH

    /** Whether the globe drifts on its own when nobody is touching it. */
    val autoRotate: Boolean get() = this != LOW

    /** Upper bound on wind streamline particles. */
    val windParticleBudget: Int
        get() = when (this) {
            HIGH -> 220
            MEDIUM -> 90
            LOW -> 0
        }

    /** Whether storms flash and pulse, as opposed to being drawn as static markers. */
    val animatedStorms: Boolean get() = this != LOW

    companion object {
        fun fromId(id: String?): GraphicsQuality = entries.firstOrNull { it.id == id } ?: HIGH
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
    val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
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
 * A headline whose text is not in a definition table because it names something
 * that only existed at the moment it fired — currently a storm, which has a
 * name, a category and a location the milestone table cannot know in advance.
 *
 * Written into the feed rather than reconstructed on read, so a headline about
 * Hurricane Iris still reads correctly long after Iris has dissipated.
 */
data class NewsBulletin(
    val headline: String,
    val body: String,
    /** Dateline, matching the milestone feed's style, e.g. "STORM WATCH · NORTH ATLANTIC". */
    val source: String,
    val icon: String,
)

/**
 * One headline in the world-news feed. Milestones write these as the planet
 * crosses thresholds, so a run has a readable narrative of what the player's
 * emissions actually did rather than only a temperature readout.
 *
 * The storm system writes them too, through the same feed rather than a second
 * notification channel of its own — see [bulletin].
 */
data class NewsItem(
    /**
     * Milestone definition id, or — for a [bulletin] — a unique id for this one
     * headline. Either way it is the feed's key.
     */
    val milestoneId: String,
    /** Wall-clock ms the headline fired at. */
    val at: Long,
    /** Seconds into the run, so the feed still reads correctly after a reload. */
    val runSeconds: Double,
    /** Set when the headline's text was written at the time rather than looked up. */
    val bulletin: NewsBulletin? = null,
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

    /**
     * How old this Earth is, in **simulated** seconds — the planet's own age,
     * not the player's time at the controls.
     *
     * Advanced by [com.earthgame.idle.domain.engine.simulateStep] alone, live
     * ticks and offline catch-up alike, at
     * [com.earthgame.idle.domain.engine.GAME_SECONDS_PER_REAL_SECOND] per real
     * second of simulation. It is therefore a count of time the world was
     * actually simulated for: it does not advance while the app merely exists,
     * and an absence longer than the offline cap ages the planet by the cap,
     * not by the absence. Reset to zero by [startNewRun]; the lifetime total
     * lives on in [LifetimeStats.totalSimulatedSeconds].
     *
     * This is the clock every gas half-life runs on.
     */
    val gameAgeSeconds: Double = 0.0,

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

    /**
     * The weather: every storm currently on the planet, plus the two counters
     * that make the storm timeline reproducible. Advanced by
     * [com.earthgame.idle.domain.engine.advanceStormsFor] on the live path and
     * the offline path alike, and saved in full — see
     * `docs/wiki/Storm-System.md`.
     */
    val storms: StormField = StormField.EMPTY,

    /**
     * This Earth's weather seed. Fixed when the Earth begins and never changed
     * while it runs, so the same planet always gets the same storms — through a
     * save, a reload, and an absence. A new Earth gets a new one.
     */
    val stormSeed: Long = 0L,

    /** Milestone ids already fired this run (each headline fires at most once per Earth). */
    val milestonesTriggered: Map<String, Boolean> = emptyMap(),
    /** Newest-first world-news headlines for the current run. */
    val newsFeed: List<NewsItem> = emptyList(),

    val settings: Settings = Settings.DEFAULT,
    val tutorial: TutorialState = TutorialState(),

    val collapsed: Boolean = false,
)

const val SAVE_VERSION = 5

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
    stormSeed = deriveStormSeed(now, 0),
)

/**
 * Starts a fresh run after a reset, preserving exactly what carries between
 * Earths: prestige, achievements, challenge completions, lifetime stats,
 * settings and tutorial progress. Everything else — resources, atmosphere,
 * technology, per-run stats, the Earth's age, news, events — starts over.
 */
fun startNewRun(previous: GameState, now: Long): GameState = previous.copy(
    runNumber = previous.runNumber + 1,
    runStartedAt = now,
    lastTickAt = now,

    // A new Earth is a new planet: age zero, with only the prestige bonuses
    // carried over. The lifetime simulated total in lifetimeStats is untouched.
    gameAgeSeconds = 0.0,

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

    // A new Earth gets new weather. Storms belong to the planet that made
    // them: none of them survive its end, and the next Earth's timeline
    // starts from step zero under its own seed, so no storm can be carried
    // across a reset and no two Earths share a forecast.
    storms = StormField.EMPTY,
    stormSeed = deriveStormSeed(now, previous.runNumber + 1),

    milestonesTriggered = emptyMap(),
    newsFeed = emptyList(),
    collapsed = false,
)
