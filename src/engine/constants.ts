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
  /** Sea level rise (m) scales with cumulative warming exposure over time. */
  seaLevelPerDegreeYear: 0.12,
  seaLevelCollapseMeters: 220,
  /** Agriculture and biodiversity decay faster once warming passes this knee. */
  agricultureKneeDegrees: 3,
  biodiversityKneeDegrees: 4,
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
