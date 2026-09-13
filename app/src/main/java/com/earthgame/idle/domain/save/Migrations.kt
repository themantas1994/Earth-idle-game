package com.earthgame.idle.domain.save

import com.earthgame.idle.domain.engine.GAME_SECONDS_PER_REAL_SECOND
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.SAVE_VERSION
import com.earthgame.idle.domain.storms.StormField
import com.earthgame.idle.domain.storms.deriveStormSeed
import kotlin.math.max

/**
 * Save migrations.
 *
 * Each step moves a save one version forward, and they run in order, so a save
 * written by any released version reaches the current shape by falling through
 * the chain. The chain is inherited from the web build — its version numbers
 * and its fix-ups — because a save exported from that build has to keep
 * working.
 *
 * Adding a version means: bump [SAVE_VERSION], add one `if (version < n)` block
 * here, and add a case to `SaveMigrationTest`.
 *
 * A save from a *newer* build than this one keeps its own version number rather
 * than being stamped down to [SAVE_VERSION]. Stamping it down would mean that
 * upgrading back to the newer build re-ran migrations the save had already been
 * through — running `migrateV2ToV3`'s rescale twice would divide a player's
 * banked Earth Points by 250,000 a second time. Fields this build does not know
 * about are still dropped on the next write, which is unavoidable on a
 * downgrade; corrupting the ones it does know about is not.
 */
fun migrate(state: GameState): GameState {
    var migrated = state

    if (migrated.saveVersion < 2) {
        migrated = migrateV1ToV2(migrated)
    }

    if (migrated.saveVersion < 3) {
        migrated = migrateV2ToV3(migrated)
    }

    if (migrated.saveVersion < 4) {
        migrated = migrateV3ToV4(migrated)
    }

    if (migrated.saveVersion < 5) {
        migrated = migrateV4ToV5(migrated)
    }

    // A v5 save written before this build knew about weather — or one whose
    // seed field was damaged — would run every storm step against a seed of
    // zero, which collapses the per-step mixing and gives every such planet the
    // same forecast. Derived here rather than defaulted, so it is stable for a
    // given Earth.
    if (migrated.stormSeed == 0L) {
        migrated = migrated.copy(stormSeed = deriveStormSeed(migrated.createdAt, migrated.runNumber))
    }

    return if (migrated.saveVersion >= SAVE_VERSION) migrated else migrated.copy(saveVersion = SAVE_VERSION)
}

/**
 * v2 removed manual tapping. Energy now comes from Natural Fire, which every
 * run owns from the start, and a v1 save can be missing that generator entirely
 * — it used to be a zero-output unlock, and a run begun before it was granted
 * has nothing at all. Left alone the player would have no income and no way to
 * earn any, so it is granted here.
 *
 * The "Tap Conditioning" prestige upgrade went with it. Refunding what was
 * spent on it — a geometric series at its old 1.8 growth from a base of 50 — is
 * the only fair option: the player bought something the game no longer
 * contains.
 */
private fun migrateV1ToV2(state: GameState): GameState {
    val upgrades = state.prestige.upgradesOwned.toMutableMap()
    val tapLevels = upgrades.remove("tap_conditioning") ?: 0
    val refund = if (tapLevels > 0) {
        gd(50.0) * (gd(1.8).pow(tapLevels) - GameDecimal.ONE) / 0.8
    } else {
        GameDecimal.ZERO
    }

    return state.copy(
        techOwned = state.techOwned + ("natural_fire" to max(1, state.techOwned["natural_fire"] ?: 0)),
        prestige = state.prestige.copy(
            earthPoints = state.prestige.earthPoints + refund,
            upgradesOwned = upgrades.toMap(),
        ),
        saveVersion = 2,
    )
}

/**
 * v3 rebuilt the economy. Two things in a v2 save are no longer worth what they
 * were, and both are about Earth Points rather than the run itself — a run in
 * progress only ever gets *cheaper*, since the complexity surcharge is gone and
 * no price rises any more, so it needs no fixing up.
 *
 * 1. The prestige payout was rescaled by roughly 10^5. The old formula
 *    multiplied by raw civilization level, which runs into the thousands, and a
 *    single completed run paid out enough to buy the entire upgrade tree at
 *    once. Banked points are divided by the ratio between what a completed run
 *    used to pay and what one pays now, so a returning player's balance buys
 *    about what it bought before. It is a single ratio against two
 *    differently-shaped formulas, so it is an approximation — deliberately a
 *    generous one, since erring toward giving a player too much is the kinder
 *    failure.
 * 2. "Institutional Memory" used to cancel the complexity surcharge, which no
 *    longer exists. Rather than delete it, it now grants +40% production per
 *    level, so levels already bought keep paying — and it is cheaper than it
 *    was, so nobody overpaid.
 */
private fun migrateV2ToV3(state: GameState): GameState {
    val prestigeRescale = 250_000.0

    return state.copy(
        prestige = state.prestige.copy(
            earthPoints = state.prestige.earthPoints / prestigeRescale,
        ),
        lifetimeStats = state.lifetimeStats.copy(
            totalEarthPointsEarned = state.lifetimeStats.totalEarthPointsEarned / prestigeRescale,
        ),
        saveVersion = 3,
    )
}

/**
 * v4 gave the Earth an age: `gameAgeSeconds`, the simulated time this planet
 * has been running for, and the clock every gas half-life now decays on.
 *
 * **The current Earth starts at age zero.** A v3 save records when the run
 * began in wall-clock terms (`runStartedAt`) but not how much of that wall
 * clock was actually simulated — an absence past the offline cap is banked at
 * the cap, and a player with offline progress switched off banks none of it —
 * so `lastTickAt - runStartedAt` would routinely credit an Earth with
 * centuries it never lived through, and with the decay those centuries would
 * retroactively drain its atmosphere. There is no honest reconstruction, so
 * nothing is invented: the planet keeps its atmosphere and starts ageing from
 * here.
 *
 * The **lifetime** total is a different matter and is migrated exactly.
 * `totalPlayTimeSeconds` is not wall-clock time: `simulateStep` advances it by
 * precisely the `dt` it simulated, live and offline alike, which is the same
 * `dt` that now also advances the simulated clock. Converting it is arithmetic
 * on a figure the save already holds, not a guess.
 */
private fun migrateV3ToV4(state: GameState): GameState = state.copy(
    gameAgeSeconds = 0.0,
    lifetimeStats = state.lifetimeStats.copy(
        totalSimulatedSeconds = state.lifetimeStats.totalPlayTimeSeconds * GAME_SECONDS_PER_REAL_SECOND,
    ),
    saveVersion = 4,
)

/**
 * v5 gave the planet weather: storms, and the two counters that make their
 * timeline reproducible.
 *
 * **A returning player's Earth starts with clear skies.** A v4 save records
 * nothing about weather, and there is no honest way to reconstruct what storms
 * a run "should" have had — the timeline depends on a seed that did not exist
 * and on a step count that was never kept. So nothing is invented: the storm
 * field starts empty at step zero, the Earth gets the seed it would have been
 * given at creation, and the next warm spell produces its first storm the same
 * way a fresh run would.
 *
 * Nothing else in the save is touched. Storms take a slice off production while
 * they run and nothing at all once they are gone, so an Earth arriving without
 * any is not owed a correction.
 */
private fun migrateV4ToV5(state: GameState): GameState = state.copy(
    storms = StormField.EMPTY,
    stormSeed = deriveStormSeed(state.createdAt, state.runNumber),
    saveVersion = 5,
)
