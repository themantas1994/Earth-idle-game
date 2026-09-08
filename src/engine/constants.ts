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
  /** Default offline progress cap, in seconds, before prestige upgrades extend it. */
  baseOfflineCapSeconds: 8 * 3600,
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
 *     costGrowthPerTier / productionGrowthPerTier
 *
 * Call that ratio `r`. Because a tier's income scales with
 * `productionGrowthPerTier^tier` while the next tier's price scales with
 * `costGrowthPerTier^tier`, the wall-clock time to climb one tier is
 * multiplied by `r` every tier. Total run length is therefore roughly
 *
 *     firstTierSeconds * (r^tierCount - 1) / (r - 1)
 *
 * With `r < 1` the game runs away and finishes itself in minutes; with
 * `r ≈ 1.2` across ~45 tiers a first run lands in the region of a week of
 * real time, which is the pacing this game is tuned for. Raising `r` makes
 * the whole game longer *and* back-loaded; raising `firstTierSeconds`
 * (i.e. the base costs below) slows the opening without changing the shape.
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
  gasProductionGrowthPerTier: 1.75,
  /** Per-tier growth of a generator's base (first-unit) cost. */
  generatorCostGrowthPerTier: 2.15,
  /** Per-tier growth of one-time unlock/multiplier/choice costs. */
  unlockCostGrowthPerTier: 2.15,
  /** Per-tier growth of research costs. Kept just under the cost curve so research paces, but never hard-blocks, progress. */
  researchCostGrowthPerTier: 2.05,

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
   * tier step would still double the price. Left uncompressed, that gap
   * turns the last third of the tree into an unclimbable wall, which is
   * exactly what it used to be.
   *
   * Compressing the ladder there (not the tiers themselves, which still
   * order the tree and drive its narrative pacing) keeps late technologies
   * priced against what a late economy can actually earn.
   */
  lateTierCompression: 0.35,

  /** First-unit cost of a tier-0 generator. */
  generatorBaseCost: 20,
  /** Cost of a tier-0 one-time unlock. */
  unlockBaseCost: 16,
  /** Research cost of a tier-0 tree node. */
  researchBaseCost: 10,

  /** Gas output (kg/s) of a single tier-0 generator unit. Scales how fast the planet heats relative to how fast the tree is climbed. */
  gasProductionBase: 2,
  /** Resource output (units/s) of a single tier-0 generator unit. */
  resourceProductionBase: 0.25,

  /**
   * Cost growth per *unit already owned* of the same generator. This is what
   * stops a single cheap generator from being spammed forever, and it is
   * deliberately steep enough that broadening into new technologies always
   * beats deepening into an old one.
   */
  unitCostGrowth: 1.16,

  /**
   * Cost growth per *distinct technology already owned*, applied to every
   * purchase in the game. This is the pacing tool that the per-tier curves
   * above cannot provide on their own.
   *
   * The tech tree is not a uniform ladder: through the middle of a run ten
   * branches produce in parallel, and every one of them compounds into the
   * others, while the endgame narrows to a single chain. Tuning only the
   * per-tier curves therefore forces a choice between a mid-game that
   * evaporates in an afternoon and an endgame nobody can reach. Charging for
   * *breadth* fixes the shape directly: a sprawling civilization pays more
   * for each further step, so the middle of the tree slows down sharply
   * while the thin endgame — where the owned count barely moves — is left
   * almost untouched.
   *
   * In fiction this is the bureaucratic drag of a large civilization; in
   * practice it is what spreads a first run across a week instead of an
   * evening. Owning more *units* of something you already have is
   * deliberately exempt, so depth stays cheap and only new frontiers cost.
   */
  complexityCostGrowth: 1.09,
  /** Extra per-unit cost growth added per tier, so late generators saturate sooner. */
  unitCostGrowthPerTier: 0.002,
};

export const PRESTIGE = {
  /**
   * Prestige earned scales with total greenhouse gas mass ever produced,
   * peak atmospheric forcing, civilization level, and run duration, with
   * diminishing returns from the outer sqrt/log terms. See prestige.ts.
   */
  gasWeight: 1,
  forcingWeight: 0.6,
  civLevelWeight: 0.4,
  baseDivisor: 1e6,
  exponent: 0.5,
  durationBonusHalfLifeSeconds: 1800,
};
