import { Decimal, D } from './bignum';
import { CLIMATE } from './constants';
import { GAME_SECONDS_PER_YEAR, REAL_SECONDS_PER_GAME_YEAR } from './gameTime';

/**
 * Every greenhouse gas the simulation tracks is described by one of these
 * records. Adding a new gas to the game means adding one entry here (plus
 * technologies that produce it) — no engine code needs to change.
 */
export interface GasDefinition {
  id: GasId;
  name: string;
  formula: string;
  /** Real-world-flavored display unit for atmospheric concentration. */
  unit: 'ppm' | 'ppb' | 'ppt' | 'DU';
  /** Pre-industrial baseline concentration, in `unit`. */
  baseline: number;
  /** Atmospheric mass (kg) equivalent to one unit of concentration. Derived from real-world reference ratios, scaled for gameplay. */
  massPerUnit: number;
  /**
   * Atmospheric half-life, in **simulated** years: how long an undisturbed
   * stock of this gas takes to fall to half. Drives the first-order natural
   * decay constant `lambda = ln 2 / halfLife`.
   *
   * These are the numbers the game has always shipped with, unchanged. What
   * changed is that they are now half-lives operating over simulated time
   * (`gameTime.ts`) rather than mean lifetimes over real time, so the decay
   * they describe is something a run actually feels.
   */
  halfLifeYears: number;
  /**
   * Whether this gas is an accumulating **stock** that decays with
   * `halfLifeYears` on the simulated calendar.
   *
   * True for every gas the player can build up. False only for H2O, which is
   * not a stock at all: it is a diagnostic feedback relaxing toward an
   * equilibrium that warming sets, and `halfLifeYears` is read as the mean
   * residence time governing how fast it tracks that equilibrium — on the real
   * clock, exactly as before. Putting the feedback on the simulated clock
   * would let it reach equilibrium within a run, which through the documented
   * sink-efficiency quirk amplifies late-game warming several-fold and prices
   * the last two endgame technologies out of a completed run.
   */
  decaysOnSimulatedClock: boolean;
  /** Whether technologies can directly emit this gas (false = pure feedback, e.g. H2O). */
  directlyEmitted: boolean;
  /** Forcing contribution given current & baseline concentration (W/m^2). */
  forcing: (concentration: number, baseline: number) => number;
}

export type GasId = 'co2' | 'ch4' | 'n2o' | 'h2o' | 'o3' | 'fluorinated';

export const GASES: Record<GasId, GasDefinition> = {
  co2: {
    id: 'co2',
    name: 'Carbon Dioxide',
    formula: 'CO₂',
    unit: 'ppm',
    baseline: CLIMATE.baseline.co2Ppm,
    // Kilograms per ppm. Derived from the real-world figure (~7.8e12 kg/ppm)
    // and then scaled down, because CO2 is the gas this game is *about*: at the
    // real ratio a whole run's combustion moves the needle by a couple of
    // hundred ppm and the marquee gas ends up a rounding error next to the
    // synthetic ones. See the note on `fluorinated` for the other half of this
    // trade.
    massPerUnit: 2e12,
    halfLifeYears: 120,
    decaysOnSimulatedClock: true,
    directlyEmitted: true,
    forcing: (c, base) => CLIMATE.forcing.co2Alpha * Math.log(Math.max(c, 1e-6) / base),
  },
  ch4: {
    id: 'ch4',
    name: 'Methane',
    formula: 'CH₄',
    unit: 'ppb',
    baseline: CLIMATE.baseline.ch4Ppb,
    massPerUnit: 2.75e9,
    halfLifeYears: 12,
    decaysOnSimulatedClock: true,
    directlyEmitted: true,
    forcing: (c, base) => CLIMATE.forcing.ch4Alpha * (Math.sqrt(Math.max(c, 0)) - Math.sqrt(base)),
  },
  n2o: {
    id: 'n2o',
    name: 'Nitrous Oxide',
    formula: 'N₂O',
    unit: 'ppb',
    baseline: CLIMATE.baseline.n2oPpb,
    massPerUnit: 1.6e10,
    halfLifeYears: 114,
    decaysOnSimulatedClock: true,
    directlyEmitted: true,
    forcing: (c, base) => CLIMATE.forcing.n2oAlpha * (Math.sqrt(Math.max(c, 0)) - Math.sqrt(base)),
  },
  h2o: {
    id: 'h2o',
    name: 'Water Vapor',
    formula: 'H₂O',
    unit: 'ppm',
    baseline: CLIMATE.baseline.h2oPpm,
    massPerUnit: 1e13,
    // ~11 days. Not a half-life: H2O is a feedback, and this is the mean
    // residence time it relaxes toward its equilibrium with. See
    // `decaysOnSimulatedClock`.
    halfLifeYears: 0.03,
    decaysOnSimulatedClock: false,
    directlyEmitted: false,
    forcing: (c, base) => CLIMATE.forcing.h2oAlphaPerPpm * Math.max(c - base, 0),
  },
  o3: {
    id: 'o3',
    name: 'Tropospheric Ozone',
    formula: 'O₃',
    unit: 'DU',
    baseline: CLIMATE.baseline.o3Dobson,
    massPerUnit: 5e9,
    halfLifeYears: 0.06, // ~3 weeks
    decaysOnSimulatedClock: true,
    directlyEmitted: true,
    forcing: (c, base) => CLIMATE.forcing.o3Alpha * Math.log(Math.max(c, 1) / base),
  },
  fluorinated: {
    id: 'fluorinated',
    name: 'Fluorinated Gases',
    formula: 'CFCs/HFCs/PFCs/SF₆',
    unit: 'ppt',
    baseline: CLIMATE.baseline.fluorinatedPpt,
    // Trace gases are absurdly potent per kilogram, and with a linear forcing
    // response and a 3200-year lifetime an unscaled value lets one late branch
    // out-heat every fire, furnace and engine in the run combined. Scaled so
    // f-gases stay what they should be — a nasty lategame accelerant rather
    // than the whole apocalypse.
    massPerUnit: 3e7,
    halfLifeYears: 3200, // SF6-scale: extremely long-lived, essentially permanent on game timescales
    decaysOnSimulatedClock: true,
    directlyEmitted: true,
    forcing: (c, base) => CLIMATE.forcing.fluorinatedAlphaPerPpt * Math.max(c - base, 0),
  },
};

export const GAS_LIST: GasDefinition[] = Object.values(GASES);

export function massToConcentration(gas: GasDefinition, massKg: Decimal): number {
  return massKg.div(gas.massPerUnit).toNumber();
}

export function concentrationToMass(gas: GasDefinition, concentration: number): Decimal {
  return D(concentration).mul(gas.massPerUnit);
}

/**
 * Fraction of an undisturbed stock of a gas still present after
 * `dtGameSeconds` of simulated time, straight from the half-life law
 *
 *     C(t) = C0 * 2^(-t / H)
 *
 * A stock of 1,000 with a 120-year half-life is 500 after 120 simulated years,
 * 250 after 240, and 125 after 360. This is the definition the decay constant
 * below is derived from, not a second implementation of it: `e^(-lambda*t)`
 * with `lambda = ln 2 / H` is the same curve.
 */
export function halfLifeFractionRemaining(halfLifeYears: number, dtGameSeconds: number): number {
  if (dtGameSeconds <= 0) return 1;
  if (halfLifeYears <= 0) return 0;
  return Math.pow(2, -(dtGameSeconds / GAME_SECONDS_PER_YEAR) / halfLifeYears);
}

/**
 * First-order natural decay constant lambda, per second of **real** time —
 * which is what `simulateStep` integrates in.
 *
 * `lambda = ln 2 / halfLife` is the half-life law written as a rate, and
 * dividing the half-life by `REAL_SECONDS_PER_GAME_YEAR` rather than by a year
 * of real seconds is the whole of the conversion between the two clocks: it
 * happens once, here, so nothing downstream has to know which clock it is on.
 *
 * H2O is the documented exception (`decaysOnSimulatedClock`): it is a feedback
 * rather than a stock, and keeps the real-time relaxation rate it has always
 * had.
 */
export function naturalRemovalRateConstant(gas: GasDefinition): number {
  return gas.decaysOnSimulatedClock
    ? Math.LN2 / (gas.halfLifeYears * REAL_SECONDS_PER_GAME_YEAR)
    : 1 / (gas.halfLifeYears * GAME_SECONDS_PER_YEAR);
}
