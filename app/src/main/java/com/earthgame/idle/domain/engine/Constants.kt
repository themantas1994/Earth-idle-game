package com.earthgame.idle.domain.engine

/**
 * Central tuning file. Every "magic number" that shapes game balance lives
 * here (or in the technology data files) rather than scattered through engine
 * logic, so the game can be rebalanced without touching simulation code.
 *
 * The values are the reference implementation's, unchanged. `BalanceParityTest`
 * asserts they still match the numbers captured from it.
 */

/** Display name of the prestige currency. Change this one string to rebrand it. */
const val PRESTIGE_CURRENCY_NAME = "Earth Points"
const val PRESTIGE_CURRENCY_SHORT = "EP"

/** Display name for the run counter, e.g. "EARTH 7". */
const val RUN_LABEL = "EARTH"

object SIMULATION {
    /** Minimum wall-clock ms between ticks while the app is foregrounded. */
    const val TICK_INTERVAL_MS = 250L

    /** Autosave cadence while playing. */
    const val AUTOSAVE_INTERVAL_MS = 15_000L

    /**
     * Default offline progress cap, in seconds, before prestige upgrades
     * extend it. Twelve hours so a night's sleep is fully banked: coming back
     * to a stockpile big enough to buy a dozen things at once is the loop this
     * game is built around.
     */
    const val BASE_OFFLINE_CAP_SECONDS = 12.0 * 3600

    /**
     * A gap between ticks longer than this — phone locked, app backgrounded,
     * process killed — is treated as an absence and settled through the
     * offline path rather than as one very long live tick.
     */
    const val OFFLINE_GAP_THRESHOLD_MS = 20_000L
}

object CLIMATE {
    // Pre-industrial baselines, the zero point for forcing calculations.
    const val baselineCo2Ppm = 280.0
    const val baselineCh4Ppb = 700.0
    const val baselineN2oPpb = 270.0
    const val baselineFluorinatedPpt = 0.0
    const val baselineO3Dobson = 300.0
    const val baselineH2oPpm = 0.0 // tracked purely as a feedback term above this baseline

    /**
     * Simplified radiative-forcing coefficients in W/m², loosely modeled on
     * real IPCC approximations (Myhre et al. 1998) but not intended to be
     * scientifically precise. CO2 and O3 use a logarithmic response since
     * their absorption bands are already partially saturated; CH4/N2O use a
     * square-root response; the synthetic/fluorinated bucket is linear since
     * trace gases sit on the unsaturated part of their absorption bands.
     */
    const val co2Alpha = 5.35 // W/m² per ln(C/C0)
    const val ch4Alpha = 0.036 // W/m² per sqrt(ppb) delta
    const val n2oAlpha = 0.12 // W/m² per sqrt(ppb) delta
    const val o3Alpha = 0.4 // W/m² per ln(Dobson/D0)
    const val fluorinatedAlphaPerPpt = 0.00015 // W/m² per ppt
    const val h2oAlphaPerPpm = 0.0007 // W/m² per ppm of feedback water vapor

    /** Water vapor is a feedback, not a directly emitted gas: it rises with warming. */
    const val h2oFeedbackPpmPerDegree = 400.0

    /**
     * Effective climate sensitivity: °C of warming per W/m². ~0.8 corresponds
     * to a real-world-plausible ~3 °C per CO2 doubling (3.7 W/m²). A mild
     * super-linear term is added on top so extreme lategame forcing produces
     * the escalating, increasingly absurd temperatures the endgame is going
     * for instead of flattening out.
     */
    const val climateSensitivity = 0.8
    const val superLinearCoefficient = 0.02
    const val superLinearExponent = 1.35
}

object CARBON_CYCLE {
    /**
     * Fraction of atmospheric sink capacity lost per degree of warming — ocean
     * stratification and permafrost thaw weakening natural sinks.
     * `sinkEfficiency = 1 / (1 + warmingSinkPenalty * tempAnomaly)`
     */
    const val warmingSinkPenalty = 0.045

    /** Sink efficiency never drops below this floor, keeping removal formulas stable. */
    const val minSinkEfficiency = 0.05
}

object HABITABILITY {
    /** Temperature anomaly (°C) at which the temperature factor alone hits zero. */
    const val tempCollapseThreshold = 55.0

    /** Ocean pH drift per ppm of CO2 above baseline (very simplified acidification). */
    const val phDriftPerCo2Ppm = 0.00035
    const val baselinePh = 8.1

    /** pH at which the acidity factor alone hits zero. */
    const val phCollapseFloor = 6.0

    /**
     * Sea level rise (m) per degree of warming per year of exposure.
     *
     * A game-time rate, not a real-world one: a run lasts days, which is a
     * couple of hundredths of a simulated year, so the real-world figure
     * (~0.1 m per degree-century) produces a millimetre a run and leaves both
     * the sea-level habitability factor and every coastal consequence inert.
     */
    const val seaLevelPerDegreeYear = 120.0
    const val seaLevelCollapseMeters = 220.0

    /** Agriculture and biodiversity decay faster once warming passes these knees. */
    const val agricultureKneeDegrees = 3.0
    const val biodiversityKneeDegrees = 4.0
}

/**
 * The pacing skeleton of the entire game.
 *
 * Every technology's cost and output is derived from its tier through the
 * curves in `Scaling.kt`, and those curves read their numbers from here. The
 * single most important relationship is
 *
 *     generatorCostGrowthPerTier / productionGrowthPerTier
 *
 * Call that ratio `r`. A tier's income scales with `productionGrowthPerTier^tier`
 * while the next tier's price scales with `generatorCostGrowthPerTier^tier`, so
 * the wall-clock time to climb one tier is multiplied by `r` every tier and the
 * total run length is roughly `firstTierSeconds * (r^tierCount - 1) / (r - 1)`.
 * With `r < 1` the game runs away and finishes itself in minutes.
 *
 * These curves are the *only* thing that paces the game, and every one of them
 * is baked into a technology's listed price the moment it is shown. No quoted
 * price is ever revised upward afterwards — see the invariant in `Economy.kt`.
 */
object BALANCE {
    /** Per-tier growth of a generator's per-unit *resource* output. The economy's engine. */
    const val productionGrowthPerTier = 2.0

    /**
     * Per-tier growth of a generator's per-unit *gas* output, kept deliberately
     * below the resource curve. The gap is what makes the endgame reachable: if
     * emissions grew as fast as the economy, the planet would always collapse a
     * few tiers before the tree ran out and the last technologies would be
     * unreachable content.
     */
    const val gasProductionGrowthPerTier = 1.7

    /** Per-tier growth of a generator's base (first-unit) cost. */
    const val generatorCostGrowthPerTier = 3.05

    /** Per-tier growth of one-time unlock/multiplier/choice costs. */
    const val unlockCostGrowthPerTier = 3.05

    /** Per-tier growth of research costs, kept just under the cost curve so research paces but never blocks. */
    const val researchCostGrowthPerTier = 2.85

    /**
     * Above this tier the cost and production ladders are compressed together.
     * Set at the point where the tech tree stops branching outward and starts
     * running as a single chain.
     */
    const val flattenLadderFromTier = 16.0

    /**
     * How much of a tier step still counts above [flattenLadderFromTier].
     *
     * Below that point the tree is broad: ten branches produce at once and
     * total income climbs steeply. Above it the tree narrows to one chain, so a
     * new tier adds one generator to a large static base — income barely moves
     * while a full-size tier step would more than double the price. Left
     * uncompressed, that gap turns the last third of the tree into an
     * unclimbable wall.
     */
    const val lateTierCompression = 0.55

    /**
     * First-unit cost of a tier-0 generator, against a tier-0 output of
     * [resourceProductionBase]. Together they set how long the very first
     * purchase of a brand-new Earth takes: at these values the free starting
     * fire pays for a second one inside half a minute, which is the opening
     * hook.
     */
    const val generatorBaseCost = 10.0

    /** Cost of a tier-0 one-time unlock. */
    const val unlockBaseCost = 9.0

    /** Research cost of a tier-0 tree node. */
    const val researchBaseCost = 5.0

    /** Gas output (kg/s) of a single tier-0 generator unit. */
    const val gasProductionBase = 2.0

    /** Resource output (units/s) of a single tier-0 generator unit. */
    const val resourceProductionBase = 0.5

    /**
     * Cost growth per *unit already owned* of the same generator — the only
     * thing in the game that ever raises a price, and only in response to the
     * player's own purchases of that exact building.
     *
     * It sets how many buildings one check-in buys, and it fixes the spacing of
     * the ownership bonuses, which have to stay cheaper per threshold than this
     * curve makes them.
     */
    const val unitCostGrowth = 1.15

    /** Extra per-unit cost growth added per tier, so late generators saturate sooner. */
    const val unitCostGrowthPerTier = 0.0015
}

/**
 * Per-generator ownership rewards. See `Ownership.kt` for what these do and why
 * they are spaced the way they are.
 */
object OWNERSHIP_BONUS {
    /** A bonus lands on every multiple of this many units owned of one generator. */
    const val everyUnits = 10

    /**
     * Output multiplier granted per threshold crossed, compounding. Must stay
     * below `unitCostGrowth ^ everyUnits` or depth outruns the price that buys
     * it and the tech tree stops mattering.
     */
    const val multiplier = 2.0
}

object PRESTIGE {
    /**
     * Earth Points earned for one run:
     *
     *     (totalGasKg / baseDivisor)^exponent × forcing term × civilization term × speed
     *
     * The sub-1 exponent gives the headline mass term diminishing returns, and
     * the forcing and civilization terms take a square root of their inputs for
     * the same reason: civilization level runs into the thousands by the end of
     * a completed run, and multiplying by that raw was what let a single first
     * reset buy the entire prestige tree at once.
     *
     * The speed term is what makes prestige a loop rather than a decoration.
     * Every run ends in the same place — habitability zero, tech tree finished
     * — so an outcome-only score pays a *stronger* civilization *less*, because
     * a stronger civilization kills the planet sooner and emits less in total
     * before it does. Scoring speed instead tracks the one thing prestige
     * upgrades actually buy.
     */
    const val gasWeight = 1.0
    const val forcingWeight = 0.6
    const val civLevelWeight = 0.4
    const val baseDivisor = 1e12
    const val exponent = 0.5

    /** A run at exactly this pace scores ×1 for speed; faster scores more, slower less. */
    const val referenceRunSeconds = 3.0 * 24 * 3600

    /** How sharply speed is rewarded. 2 makes halving your run time roughly quadruple the payout. */
    const val speedExponent = 2.0

    /** Bounds on the speed term, so neither a crawl nor a record run distorts the whole economy. */
    const val minSpeedMultiplier = 0.25
    const val maxSpeedMultiplier = 64.0
}
