package com.earthgame.idle.domain.engine

import com.earthgame.idle.domain.achievements.AchievementContext
import com.earthgame.idle.domain.achievements.checkAchievements
import com.earthgame.idle.domain.challenges.CHALLENGE_BY_ID
import com.earthgame.idle.domain.challenges.computeChallengeRewardEffects
import com.earthgame.idle.domain.challenges.disabledTechIdsForChallenge
import com.earthgame.idle.domain.challenges.isChallengeGoalMet
import com.earthgame.idle.domain.challenges.isChallengeRestrictionViolated
import com.earthgame.idle.domain.economy.grantTechnology
import com.earthgame.idle.domain.economy.purchaseTechnology
import com.earthgame.idle.domain.events.applyInstantGasBurst
import com.earthgame.idle.domain.events.computeActiveEventMultipliers
import com.earthgame.idle.domain.events.removeExpiredEvents
import com.earthgame.idle.domain.events.rollRandomEvent
import com.earthgame.idle.domain.milestones.checkMilestones
import com.earthgame.idle.domain.model.ActiveEvent
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.NEWS_FEED_LIMIT
import com.earthgame.idle.domain.model.NewsItem
import com.earthgame.idle.domain.model.Settings
import com.earthgame.idle.domain.model.startNewRun
import com.earthgame.idle.domain.prestige.PRESTIGE_UPGRADE_BY_ID
import com.earthgame.idle.domain.prestige.PrestigeGainParams
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.domain.prestige.calculatePrestigeGain
import com.earthgame.idle.domain.prestige.computePrestigeMultipliers
import com.earthgame.idle.domain.prestige.prestigeUpgradeCost
import com.earthgame.idle.domain.storms.StormBulletin
import com.earthgame.idle.domain.storms.StormEffectSummary
import com.earthgame.idle.domain.technologies.TECH_BY_ID
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Values derived from [GameState] that both the tick and the UI need, computed
 * once per state change rather than per frame.
 *
 * Recomputing the technology tree's multipliers and production rates is the
 * most expensive thing the game does outside the tick itself, and a Compose
 * screen can read them dozens of times per recomposition.
 */
data class DerivedState(
    val civLevel: Int,
    val prestige: PrestigeMultipliers,
    val effective: EffectiveMultipliers,
    val productionRates: ProductionRates,
    val disabledTechIds: Set<String>,
    /** What the live storms are costing, already capped and de-stacked. */
    val storms: StormEffectSummary = StormEffectSummary.NONE,
) {
    companion object {
        val EMPTY = DerivedState(
            civLevel = 0,
            prestige = PrestigeMultipliers.NONE,
            effective = EffectiveMultipliers.IDENTITY,
            productionRates = ProductionRates.ZERO,
            disabledTechIds = emptySet(),
        )
    }
}

fun computeDerived(state: GameState, nowMs: Long): DerivedState {
    val challengeRewards = computeChallengeRewardEffects(state.challenges.completed)
    val prestige = computePrestigeMultipliers(state.prestige.upgradesOwned, challengeRewards)
    val eventMultipliers = computeActiveEventMultipliers(state.activeEvents, nowMs)
    // Storms are a transient multiplier layer exactly like an active event, so
    // the production rates the UI shows already have the weather priced in.
    val storms = stormEffectsOf(state)
    val effective = computeEffectiveMultipliers(
        state.techOwned,
        prestige,
        listOf(eventMultipliers, storms.asMultiplierContribution()),
    )
    val activeChallenge = state.challenges.activeId?.let { CHALLENGE_BY_ID[it] }

    return DerivedState(
        civLevel = computeCivLevel(state.techOwned),
        prestige = prestige,
        effective = effective,
        productionRates = computeProductionRates(state.techOwned, effective),
        disabledTechIds = disabledTechIdsForChallenge(activeChallenge),
        storms = storms,
    )
}

/** Things that happened during a step which the UI should surface. */
data class StepEvents(
    val newAchievements: List<String> = emptyList(),
    val firedMilestones: List<String> = emptyList(),
    val startedEventId: String? = null,
    val challengeCompleted: String? = null,
    val challengeFailed: String? = null,
    val ownershipMilestone: OwnershipMilestone? = null,
    val justCollapsed: Boolean = false,
    val offlineProgress: OfflineProgressResult? = null,
    /** Storms that formed, intensified or dissipated during the step. */
    val stormBulletins: List<StormBulletin> = emptyList(),
) {
    /**
     * The one storm development worth interrupting the player for, if any.
     *
     * A busy sky produces several bulletins a minute at extreme warming and
     * most of them are routine; only a major one raises a toast or draws the
     * camera. See `docs/wiki/Storm-System.md`.
     */
    val majorStormBulletin: StormBulletin? get() = stormBulletins.lastOrNull { it.isMajor }
}

/** A generator crossing an ownership threshold — "Coal Mining ×50, output doubled". */
data class OwnershipMilestone(val techId: String, val atUnits: Int, val multiplier: Double)

data class StepResult(val state: GameState, val events: StepEvents)

/**
 * The game's rules of motion, above [simulateStep].
 *
 * `simulateStep` is the physics: gases, resources, temperature, habitability.
 * This is everything layered on top that a *session* needs — deciding whether a
 * gap counts as an absence, rolling random events, firing milestone headlines,
 * awarding achievements, settling challenges — kept here rather than in the
 * ViewModel so all of it is testable without Android, and so the offline path
 * and the live path can share it exactly.
 *
 * [random] is injected rather than drawn from a global so event rolls are
 * reproducible: seed it and the same sequence of events happens every time.
 */
class GameLoop(private val random: Random = Random.Default) {

    /**
     * Advances [state] to [nowMs].
     *
     * A gap longer than [SIMULATION.OFFLINE_GAP_THRESHOLD_MS] — the phone
     * locked, the app backgrounded, the process killed — is settled through the
     * offline path and reported so the UI can show a welcome-back summary.
     * Anything shorter is an ordinary tick.
     */
    fun advance(state: GameState, nowMs: Long, derived: DerivedState): StepResult {
        val elapsedMs = nowMs - state.lastTickAt
        if (elapsedMs == 0L) return StepResult(state, StepEvents())

        if (elapsedMs < 0) {
            // The device clock moved backwards — a manual change, a time-zone
            // edit, or an NTP correction — leaving `lastTickAt` in the future.
            // Nothing is simulated (time must never run backwards: no resource
            // is un-produced and no gas un-emitted), but the tick is re-anchored
            // to now. Without that the game is frozen until the wall clock
            // catches back up, which for an hour-long correction means an hour
            // of a player watching a dead planet.
            return StepResult(state.copy(lastTickAt = nowMs), StepEvents())
        }

        val dtSeconds = elapsedMs / 1000.0

        if (elapsedMs > SIMULATION.OFFLINE_GAP_THRESHOLD_MS) {
            val offline = computeOfflineProgress(
                state,
                nowMs,
                derived.prestige,
                state.settings.offlineProgressEnabled,
            )
            val caughtUp = offline.state.copy(
                activeEvents = removeExpiredEvents(offline.state.activeEvents, nowMs),
            )
            val withStormNews = appendStormNews(caughtUp, offline.stormBulletins, nowMs)
            val bookkept = applyBookkeeping(state, withStormNews, nowMs)
            // A blink-and-you-missed-it gap is not worth a modal. In practice
            // this only ever suppresses the summary for a player who has turned
            // offline progress off — the gap threshold above is already well
            // clear of this floor, so every real absence is reported.
            val worthReporting = offline.simulatedSeconds > MIN_REPORTABLE_ABSENCE_SECONDS
            return StepResult(
                bookkept.state,
                bookkept.events.copy(
                    offlineProgress = if (worthReporting) offline else null,
                    stormBulletins = offline.stormBulletins,
                ),
            )
        }

        val eventMultipliers = computeActiveEventMultipliers(state.activeEvents, nowMs)
        // The storms the tick is charged for are the ones that were already on
        // the board when it began — the same reading the event multipliers get
        // one line above, so a storm never bills for time before it existed.
        val stormMultipliers = stormMultipliers(state)
        val stepped = simulateStep(
            state,
            dtSeconds,
            derived.prestige,
            listOf(eventMultipliers, stormMultipliers),
        ).state

        // ...and only then does the weather move, against the climate this step
        // just produced.
        val weather = advanceStormsFor(stepped, dtSeconds)
        var next = stepped.copy(
            lastTickAt = nowMs,
            activeEvents = removeExpiredEvents(stepped.activeEvents, nowMs),
            storms = weather.field,
        )
        next = appendStormNews(next, weather.bulletins, nowMs)

        var startedEventId: String? = null
        // Random events pause during a challenge and while three are already
        // running, so neither a focused run nor a lucky streak turns into noise.
        if (next.challenges.activeId == null && next.activeEvents.size < MAX_CONCURRENT_EVENTS) {
            val rollChance = dtSeconds * EVENT_ROLL_CHANCE_PER_SECOND
            if (random.nextDouble() < rollChance) {
                val civLevel = computeCivLevel(next.techOwned)
                val activeIds = next.activeEvents.map { it.eventDefId }.toSet()
                val picked = rollRandomEvent(civLevel, activeIds, random.nextDouble())
                if (picked != null) {
                    next = next.copy(
                        atmosphere = applyInstantGasBurst(next.atmosphere, picked),
                        activeEvents = next.activeEvents + ActiveEvent(
                            id = "${picked.id}-$nowMs",
                            eventDefId = picked.id,
                            startedAt = nowMs,
                            endsAt = nowMs + picked.durationSeconds * 1000L,
                        ),
                    )
                    startedEventId = picked.id
                }
            }
        }

        val bookkept = applyBookkeeping(state, next, nowMs)
        return StepResult(
            bookkept.state,
            bookkept.events.copy(
                startedEventId = startedEventId,
                stormBulletins = weather.bulletins,
            ),
        )
    }

    /**
     * Writes the storm headlines worth printing into the world-news feed.
     *
     * Only the major ones. An extremely warmed planet produces a bulletin every
     * few seconds — every formation, every intensification, every dissipation —
     * and printing all of them would bury the milestone headlines the feed
     * exists for under a weather ticker.
     */
    private fun appendStormNews(
        state: GameState,
        bulletins: List<StormBulletin>,
        nowMs: Long,
    ): GameState {
        if (bulletins.isEmpty()) return state
        val worthPrinting = bulletins.filter { it.isMajor }
        if (worthPrinting.isEmpty()) return state

        val runSeconds = max(0.0, (nowMs - state.runStartedAt) / 1000.0)
        val items = worthPrinting.map {
            NewsItem(
                milestoneId = it.newsId(),
                at = nowMs,
                runSeconds = runSeconds,
                bulletin = it.toNewsBulletin(),
            )
        }
        return state.copy(
            newsFeed = (items.reversed() + state.newsFeed).take(NEWS_FEED_LIMIT),
        )
    }

    /**
     * Settles the active challenge, fires milestone headlines and awards
     * achievements after a step.
     *
     * Deliberately outside [simulateStep]: the pure simulation stays free of
     * bookkeeping, and running this on the offline path too means a night away
     * reports the same headlines a live session would have shown.
     */
    private fun applyBookkeeping(previous: GameState, input: GameState, nowMs: Long): StepResult {
        var state = input
        var challengeCompleted: String? = null
        var challengeFailed: String? = null

        state.challenges.activeId?.let { activeId ->
            val challenge = CHALLENGE_BY_ID[activeId]
            if (challenge != null) {
                if (isChallengeRestrictionViolated(challenge, state)) {
                    challengeFailed = activeId
                    state = state.copy(challenges = state.challenges.copy(activeId = null))
                } else if (isChallengeGoalMet(challenge, state, computeCivLevel(state.techOwned))) {
                    challengeCompleted = activeId
                    state = state.copy(
                        challenges = state.challenges.copy(
                            activeId = null,
                            completed = state.challenges.completed + (challenge.id to true),
                        ),
                    )
                }
            }
        }

        val fired = checkMilestones(state)
        if (fired.isNotEmpty()) {
            val runSeconds = max(0.0, (nowMs - state.runStartedAt) / 1000.0)
            state = state.copy(
                milestonesTriggered = state.milestonesTriggered + fired.associate { it.id to true },
                newsFeed = (
                    fired.reversed().map { NewsItem(it.id, nowMs, runSeconds) } + state.newsFeed
                    ).take(NEWS_FEED_LIMIT),
            )
        }

        val justCollapsed = !previous.collapsed && state.collapsed
        val newAchievements = checkAchievements(
            AchievementContext(
                state = state,
                civLevel = computeCivLevel(state.techOwned),
                justCollapsed = justCollapsed,
            ),
            state.achievementsUnlocked,
        )
        if (newAchievements.isNotEmpty()) {
            state = state.copy(
                achievementsUnlocked = state.achievementsUnlocked + newAchievements.associateWith { true },
            )
        }

        return StepResult(
            state,
            StepEvents(
                newAchievements = newAchievements,
                firedMilestones = fired.map { it.id },
                challengeCompleted = challengeCompleted,
                challengeFailed = challengeFailed,
                justCollapsed = justCollapsed,
            ),
        )
    }

    /** Buys technology, counting the purchase and reporting any ownership threshold crossed. */
    fun purchase(
        state: GameState,
        techId: String,
        quantity: Int,
        derived: DerivedState,
    ): StepResult {
        val result = purchaseTechnology(state, techId, quantity, derived.prestige, derived.disabledTechIds)
        if (!result.success) return StepResult(state, StepEvents())

        val before = state.techOwned[techId] ?: 0
        val after = result.state.techOwned[techId] ?: 0
        val crossed = ownershipMilestonesCrossed(before, after)

        val counted = result.state.copy(
            lifetimeStats = result.state.lifetimeStats.copy(
                totalTechnologiesPurchased = result.state.lifetimeStats.totalTechnologiesPurchased +
                    result.purchasedQuantity,
            ),
        )

        // A buy-max can cross several thresholds at once. Celebrate the highest
        // one crossed rather than the final owned count, so the toast names an
        // actual threshold; the multiplier alongside it is the generator's new
        // total, which is what the card already shows.
        val milestone = crossed.lastOrNull()?.let {
            OwnershipMilestone(techId, it, ownershipMultiplier(after))
        }

        return StepResult(counted, StepEvents(ownershipMilestone = milestone))
    }

    fun buyPrestigeUpgrade(state: GameState, upgradeId: String): GameState {
        val upgrade = PRESTIGE_UPGRADE_BY_ID[upgradeId] ?: return state
        val level = state.prestige.upgradesOwned[upgradeId] ?: 0
        if (level >= upgrade.maxLevel) return state
        val cost = prestigeUpgradeCost(upgrade, level)
        if (state.prestige.earthPoints.lt(cost)) return state

        return state.copy(
            prestige = state.prestige.copy(
                earthPoints = state.prestige.earthPoints - cost,
                upgradesOwned = state.prestige.upgradesOwned + (upgradeId to level + 1),
            ),
        )
    }

    /** The payout and summary for a run that has ended, without applying the reset. */
    fun scoreRun(state: GameState, nowMs: Long, derived: DerivedState): CollapseSummary {
        val runDurationSeconds = max(0.0, (nowMs - state.runStartedAt) / 1000.0)
        val earned = calculatePrestigeGain(
            PrestigeGainParams(
                totalGasProducedKg = state.runStats.totalGasProducedKg,
                peakForcingWm2 = state.runStats.peakForcingWm2,
                civLevel = derived.civLevel,
                runDurationSeconds = runDurationSeconds,
            ),
        )
        return CollapseSummary(
            runNumber = state.runNumber,
            durationSeconds = runDurationSeconds,
            maxCo2Ppm = state.runStats.peakCo2Ppm,
            maxTemperatureC = state.runStats.peakTemperatureC,
            maxGasProductionRateKgPerS = state.runStats.peakGasProductionRateKgPerS,
            earthPointsEarned = earned,
        )
    }

    /**
     * Ends the run: banks Earth Points, starts a fresh Earth, and applies every
     * "starting" prestige bonus to it.
     */
    fun resetEarth(state: GameState, nowMs: Long, derived: DerivedState): StepResult {
        if (!state.collapsed) return StepResult(state, StepEvents())

        val summary = scoreRun(state, nowMs, derived)
        val previousRunDurationSeconds =
            state.lifetimeStats.longestRunSeconds.takeIf { it > 0 }

        var fresh = startNewRun(state, nowMs)
        fresh = fresh.copy(
            prestige = fresh.prestige.copy(
                earthPoints = fresh.prestige.earthPoints + summary.earthPointsEarned,
            ),
            lifetimeStats = fresh.lifetimeStats.copy(
                totalResets = fresh.lifetimeStats.totalResets + 1,
                fastestResetSeconds = fresh.lifetimeStats.fastestResetSeconds
                    ?.let { min(it, summary.durationSeconds) }
                    ?: summary.durationSeconds,
                longestRunSeconds = max(fresh.lifetimeStats.longestRunSeconds, summary.durationSeconds),
                totalEarthPointsEarned = fresh.lifetimeStats.totalEarthPointsEarned + summary.earthPointsEarned,
            ),
        )

        val prestige = computePrestigeMultipliers(
            fresh.prestige.upgradesOwned,
            computeChallengeRewardEffects(fresh.challenges.completed),
        )
        for (techId in prestige.startingTechIds) {
            if (TECH_BY_ID.containsKey(techId)) fresh = grantTechnology(fresh, techId)
        }
        for ((techId, extraUnits) in prestige.startingGenerators) {
            fresh = fresh.copy(
                techOwned = fresh.techOwned + (techId to (fresh.techOwned[techId] ?: 0) + extraUnits),
            )
        }
        for ((resource, amount) in prestige.startingResources) {
            fresh = fresh.copy(resources = fresh.resources.with(resource, fresh.resources[resource] + gd(amount)))
        }

        val newAchievements = checkAchievements(
            AchievementContext(
                state = fresh,
                civLevel = 0,
                justCollapsed = true,
                justReset = true,
                lastRunDurationSeconds = summary.durationSeconds,
                previousRunDurationSeconds = previousRunDurationSeconds,
            ),
            fresh.achievementsUnlocked,
        )
        if (newAchievements.isNotEmpty()) {
            fresh = fresh.copy(
                achievementsUnlocked = fresh.achievementsUnlocked + newAchievements.associateWith { true },
            )
        }

        return StepResult(fresh, StepEvents(newAchievements = newAchievements))
    }

    /** Abandons the current Earth to start a challenge run on a fresh one. */
    fun startChallenge(state: GameState, challengeId: String, nowMs: Long): GameState {
        if (!CHALLENGE_BY_ID.containsKey(challengeId)) return state
        val fresh = startNewRun(state, nowMs)
        return fresh.copy(challenges = fresh.challenges.copy(activeId = challengeId))
    }

    fun abandonChallenge(state: GameState): GameState =
        state.copy(challenges = state.challenges.copy(activeId = null))

    fun updateSettings(state: GameState, settings: Settings): GameState = state.copy(settings = settings)

    fun advanceTutorial(state: GameState): GameState =
        state.copy(tutorial = state.tutorial.copy(step = state.tutorial.step + 1))

    fun skipTutorial(state: GameState): GameState =
        state.copy(tutorial = state.tutorial.copy(completed = true, skipped = true))

    companion object {
        /**
         * Per second of simulated time. Averages out to roughly one roll every
         * three minutes before eligibility filtering.
         */
        const val EVENT_ROLL_CHANCE_PER_SECOND = 0.006

        const val MAX_CONCURRENT_EVENTS = 3

        /** Below this an absence is settled silently rather than shown as a summary. */
        const val MIN_REPORTABLE_ABSENCE_SECONDS = 5.0
    }
}

/** What an Earth's ending is worth, shown once on the collapse screen. */
data class CollapseSummary(
    val runNumber: Int,
    val durationSeconds: Double,
    val maxCo2Ppm: Double,
    val maxTemperatureC: Double,
    val maxGasProductionRateKgPerS: GameDecimal,
    val earthPointsEarned: GameDecimal,
)
