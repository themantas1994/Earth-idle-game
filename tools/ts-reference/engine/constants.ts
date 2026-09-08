/**
 * Central tuning/config file. Every "magic number" that shapes game balance
 * should live here (or in the data files under `technologies/`) rather than
 * scattered through simulation logic, so the game can be rebalanced without
 * touching engine code.
 */

/** Display name of the prestige currency. Change this one string to rebrand it. */
export const PRESTIGE_CURRENCY_NAME = 'Earth Points';
export const PRESTIGE_CURRENCY_SHORT = 'EP';

/** Display name for the run counter, e.g. "EARTH 7". */
export const RUN_LABEL = 'EARTH';

export const SIMULATION = {
  /** Minimum wall-clock ms between ticks while the app is foregrounded. */
  tickIntervalMs: 250,
  /** Autosave cadence while playing. */
  autosaveIntervalMs: 15_000,
  /**
   * Default offline progress cap, in seconds, before prestige upgrades extend
   * it. Twelve hours so a night's sleep is fully banked: coming back to a
   * stockpile big enough to buy a dozen things at once is the loop this game
   * is built around, and clipping it at eight hours meant the single biggest
   * payout of the day was routinely cut short.
   */
  baseOfflineCapSeconds: 12 * 3600,
  /** Offline simulation runs in coarse steps for performance on very long absences. */
  offlineStepSeconds: 300,
};

export const CLIMATE = {
  /** Pre-industrial baselines, used as the zero point for forcing calculations. */
  baseline: {
    co2Ppm: 280,
    ch4Ppb: 700,
    n2oPpb: 270,
    fluorinatedPpt: 0,
    o3Dobson: 300,
    h2oPpm: 0, // tracked purely as a feedback term above this baseline
  },
  /**
   * Simplified radiative-forcing coefficients, in W/m^2, loosely modeled on
   * real IPCC approximations (Myhre et al. 1998) but not intended to be
   * scientifically precise. CO2 and O3 use a logarithmic response since
   * their absorption bands are already partially saturated; CH4/N2O use a
   * square-root response (with an overlap-style baseline term dropped for
   * simplicity); the synthetic/fluorinated bucket is linear since trace
   * gases sit on the unsaturated part of their absorption bands.
   */
  forcing: {
    co2Alpha: 5.35, // W/m^2 per ln(C/C0)
    ch4Alpha: 0.036, // W/m^2 per sqrt(ppb) delta
    n2oAlpha: 0.12, // W/m^2 per sqrt(ppb) delta
    o3Alpha: 0.4, // W/m^2 per ln(Dobson/D0)
    fluorinatedAlphaPerPpt: 0.00015, // W/m^2 per ppt
    h2oAlphaPerPpm: 0.0007, // W/m^2 per ppm of feedback water vapor
  },
  /** Water vapor is a feedback, not a directly emitted gas: it rises with warming. */
  h2oFeedbackPpmPerDegree: 400,
  /**
   * Effective climate sensitivity: °C of warming per W/m^2 of forcing.
   * ~0.8 corresponds to a real-world-plausible ~3°C warming per CO2 doubling
   * (3.7 W/m^2). A mild super-linear term is added on top so extreme
   * lategame forcing produces the escalating, increasingly absurd
   * temperatures the game's endgame is going for.
   */
  climateSensitivity: 0.8,
  superLinearCoefficient: 0.02,
  superLinearExponent: 1.35,
};

export const CARBON_CYCLE = {
  /**
   * Fraction of atmospheric sink capacity lost per degree of warming, e.g.
   * ocean stratification and permafrost thaw weakening natural sinks.
   * sinkEfficiency = 1 / (1 + warmingSinkPenalty * tempAnomaly)
   */
  warmingSinkPenalty: 0.045,
  /** Sink efficiency never drops below this floor, keeping removal formulas stable. */
  minSinkEfficiency: 0.05,
};

export const HABITABILITY = {
  /** Temperature anomaly (°C) at which the temperature factor alone hits zero. */
  tempCollapseThreshold: 55,
  /** Ocean pH drift per ppm of CO2 above baseline (very simplified acidification). */
  phDriftPerCo2Ppm: 0.00035,
  baselinePh: 8.1,
  /** pH at which the acidity factor alone hits zero. */
  phCollapseFloor: 6.0,
  /**
   * Sea level rise (m) per degree of warming per year of exposure.
   *
   * This is a game-time rate, not a real-world one: a run lasts days, which
   * is a couple of hundredths of a simulated year, so the real-world figure
   * (~0.1 m per degree-century) produces a millimetre a run and leaves both
   * the sea-level habitability factor and every coastal consequence inert.
   * Scaled so that a run which cooks the planet also drowns its coastlines,
   * on the same timescale as everything else the player is doing.
   */
  seaLevelPerDegreeYear: 120,
  seaLevelCollapseMeters: 220,
  /** Agriculture and biodiversity decay faster once warming passes this knee. */
  agricultureKneeDegrees: 3,
  biodiversityKneeDegrees: 4,
};

/**
 * The pacing skeleton of the entire game.
 *
 * Every technology's cost and output is derived from its `tier` through the
 * curves in `technologies/scaling.ts`, and those curves read their numbers
 * from here. The single most important relationship is:
 *
 *     generatorCostGrowthPerTier / productionGrowthPerTier
 *
 * Call that ratio `r`. Because a tier's income scales with
 * `productionGrowthPerTier^tier` while the next tier's price scales with
 * `generatorCostGrowthPerTier^tier`, the wall-clock time to climb one tier is
 * multiplied by `r` every tier. Total run length is therefore roughly
 *
 *     firstTierSeconds * (r^tierCount - 1) / (r - 1)
 *
 * With `r < 1` the game runs away and finishes itself in minutes. Raising `r`
 * makes the whole game longer *and* back-loaded; raising the base costs below
 * slows the opening without changing the shape.
 *
 * These curves are the *only* thing that paces the game, and every one of
 * them is baked into a technology's listed price the moment it is shown. No
 * quoted price is ever revised upward afterwards — see the invariant at the
 * top of `economy.ts`.
 */
export const BALANCE = {
  /** Per-tier growth of a generator's per-unit *resource* output. This is the economy's engine. */
  productionGrowthPerTier: 2.0,
  /**
   * Per-tier growth of a generator's per-unit *gas* output, kept deliberately
   * below the resource curve. The gap is what makes the endgame reachable:
   * if emissions grew as fast as the economy, the planet would always
   * collapse a few tiers before the tree ran out and the last technologies
   * would be unreachable content. Widening the gap lengthens the endgame;
   * closing it makes the climate the binding constraint again.
   */
  gasProductionGrowthPerTier: 1.7,
  /** Per-tier growth of a generator's base (first-unit) cost. */
  generatorCostGrowthPerTier: 3.05,
  /** Per-tier growth of one-time unlock/multiplier/choice costs. */
  unlockCostGrowthPerTier: 3.05,
  /** Per-tier growth of research costs. Kept just under the cost curve so research paces, but never hard-blocks, progress. */
  researchCostGrowthPerTier: 2.85,

  /**
   * Above this tier the cost and production ladders are compressed (see
   * `lateTierCompression`). Set it at the point where the tech tree stops
   * branching outward and starts running as a single chain.
   */
  flattenLadderFromTier: 16,
  /**
   * How much of a tier step still counts, above `flattenLadderFromTier`.
   *
   * Below that point the tree is broad: ten branches produce at once, each
   * new tier adds several generators, and total income climbs steeply. Above
   * it the tree narrows to essentially one chain, so a new tier adds one
   * generator to a large static base — income barely moves while a full-size
   * tier step would still more than double the price. Left uncompressed,
   * that gap turns the last third of the tree into an unclimbable wall.
   *
   * Compressing the ladder there (not the tiers themselves, which still
   * order the tree and drive its narrative pacing) keeps late technologies
   * priced against what a late economy can actually earn.
   */
  lateTierCompression: 0.55,

  /**
   * First-unit cost of a tier-0 generator, against a tier-0 output of
   * `resourceProductionBase`. The two together set how long the very first
   * purchase of a brand-new Earth takes: at the shipped values the free
   * starting fire pays for a second one inside half a minute, which is the
   * whole opening hook.
   */
  generatorBaseCost: 10,
  /** Cost of a tier-0 one-time unlock. */
  unlockBaseCost: 9,
  /** Research cost of a tier-0 tree node. */
  researchBaseCost: 5,

  /** Gas output (kg/s) of a single tier-0 generator unit. Scales how fast the planet heats relative to how fast the tree is climbed. */
  gasProductionBase: 2,
  /** Resource output (units/s) of a single tier-0 generator unit. */
  resourceProductionBase: 0.5,

  /**
   * Cost growth per *unit already owned* of the same generator — the only
   * thing in the game that ever raises a price, and it only ever responds to
   * the player's own purchases of that exact building.
   *
   * It sets how many buildings one check-in buys: a shallower curve spends a
   * stockpile on a satisfying *pile* of them rather than on one, which is
   * most of what a check-in feels like. It also fixes the spacing of the
   * ownership bonuses below, which have to stay cheaper per threshold than
   * this curve makes them — so lowering it without widening
   * `OWNERSHIP_BONUS.everyUnits` is what would let depth outrun the tech
   * tree.
   */
  unitCostGrowth: 1.15,

  /**
   * Cost growth per *distinct technology already owned* used to live here as
   * `complexityCostGrowth`: a civilization-wide surcharge that made every
   * price climb as the tree grew. It paced the middle of the game by
   * re-pricing things the player had already been quoted, which is exactly
   * the feeling this economy is now built to never produce. Pacing is the
   * per-tier curves' job; nothing retroactively marks anything up.
   */

  /** Extra per-unit cost growth added per tier, so late generators saturate sooner. */
  unitCostGrowthPerTier: 0.0015,
};

/**
 * Per-generator ownership rewards. See `ownership.ts` for what these do and
 * why they are spaced the way they are.
 */
export const OWNERSHIP_BONUS = {
  /** A bonus lands on every multiple of this many units owned of one generator. */
  everyUnits: 10,
  /**
   * Output multiplier granted per threshold crossed, compounding. Must stay
   * below `unitCostGrowth ^ everyUnits` or depth outruns the price that buys
   * it and the tech tree stops mattering — see `ownership.ts`.
   */
  multiplier: 2,
};

export const PRESTIGE = {
  /**
   * Earth Points earned for one run:
   *
   *     (totalGasKg / baseDivisor)^exponent × forcing term × civilization term × speed
   *
   * The sub-1 exponent gives the headline mass term diminishing returns, and
   * the forcing and civilization terms take a square root of their inputs for
   * the same reason: civilization level runs into the thousands by the end of
   * a completed run, and multiplying by that raw was what let a single first
   * reset buy the entire prestige tree at once, leaving nothing to chase.
   *
   * The `speed` term is the one that makes prestige a loop rather than a
   * decoration. Every run in this game ends in the same place — habitability
   * zero, tech tree finished — so an outcome-only score pays a *stronger*
   * civilization *less*, because a stronger civilization kills the planet
   * sooner and therefore emits less in total before it does. Measured against
   * `referenceRunSeconds` and squared, the score instead tracks the one thing
   * prestige upgrades actually buy: how fast you got there. Beating your last
   * Earth is what pays for the next one.
   */
  gasWeight: 1,
  forcingWeight: 0.6,
  civLevelWeight: 0.4,
  baseDivisor: 1e12,
  exponent: 0.5,
  /** A run at exactly this pace scores ×1 for speed; faster scores more, slower less. */
  referenceRunSeconds: 3 * 24 * 3600,
  /** How sharply speed is rewarded. 2 makes halving your run time roughly quadruple the payout. */
  speedExponent: 2,
  /** Bounds on the speed term, so neither a crawl nor a record run distorts the whole economy. */
  minSpeedMultiplier: 0.25,
  maxSpeedMultiplier: 64,
};
