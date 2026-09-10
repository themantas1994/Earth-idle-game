package com.earthgame.idle.domain.save

import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.SAVE_VERSION
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
