import { Decimal, D } from './bignum';
import { CLIMATE } from './constants';

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
  /** Mean atmospheric lifetime in years; drives the first-order natural-removal rate. */
  lifetimeYears: number;
  /** Whether technologies can directly emit this gas (false = pure feedback, e.g. H2O). */
  directlyEmitted: boolean;
  /** Forcing contribution given current & baseline concentration (W/m^2). */
  forcing: (concentration: number, baseline: number) => number;
}

export type GasId = 'co2' | 'ch4' | 'n2o' | 'h2o' | 'o3' | 'fluorinated';

const SECONDS_PER_YEAR = 365.25 * 24 * 3600;

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
    lifetimeYears: 120,
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
    lifetimeYears: 12,
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
    lifetimeYears: 114,
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
    lifetimeYears: 0.03, // ~11 days: fast feedback, not a stock the player builds up
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
    lifetimeYears: 0.06, // ~3 weeks
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
    lifetimeYears: 3200, // SF6-scale: extremely long-lived, essentially permanent on game timescales
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

/** Per-second first-order natural removal rate constant (fraction of stock removed per second). */
export function naturalRemovalRateConstant(gas: GasDefinition): number {
  return 1 / (gas.lifetimeYears * SECONDS_PER_YEAR);
}
