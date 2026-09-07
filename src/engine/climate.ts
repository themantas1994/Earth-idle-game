import { Decimal } from './bignum';
import { GasId, GASES, GAS_LIST } from './gases';
import { CLIMATE, CARBON_CYCLE } from './constants';

/**
 * Atmospheric state tracks each gas as the concentration *above* its
 * pre-industrial baseline (in the gas's native unit: ppm/ppb/ppt/DU). The
 * baseline itself is assumed to be a natural steady state (equal natural
 * emission and removal flux) and isn't simulated explicitly — only the
 * anthropogenic perturbation is. This keeps the numbers meaningful
 * (extra === 0 at game start) without having to model pre-industrial
 * natural gas cycles.
 */
export type AtmosphereState = Record<GasId, Decimal>;

export function createInitialAtmosphere(): AtmosphereState {
  const state = {} as AtmosphereState;
  for (const gas of GAS_LIST) {
    state[gas.id] = Decimal.ZERO;
  }
  return state;
}

/**
 * Natural + engineered sinks weaken as the planet warms (permafrost thaw,
 * ocean stratification reducing mixing, forest dieback, etc.), floored so
 * removal never fully stops.
 */
export function computeSinkEfficiency(tempAnomalyC: number): number {
  const eff = 1 / (1 + CARBON_CYCLE.warmingSinkPenalty * Math.max(0, tempAnomalyC));
  return Math.max(CARBON_CYCLE.minSinkEfficiency, eff);
}

/**
 * Advances one gas's atmospheric concentration by `dtSeconds`, using the
 * exact analytic solution to dE/dt = production - k*E (a first-order decay
 * toward the equilibrium production/k). Because this is a closed-form
 * solution rather than a fixed small-step Euler integration, it stays
 * accurate even for very large `dtSeconds` (e.g. hours of offline time
 * collapsed into a single call) without needing many substeps.
 *
 * @param extraConcentration current concentration above baseline, in the gas's native unit
 * @param productionPerSecond emission rate, in the gas's native unit per second
 * @param removalRateConstant natural per-second removal fraction (1 / lifetime), already unit-consistent
 * @param sinkEfficiency multiplier in (0,1] representing weakened sinks under warming
 * @param extraRemovalPerSecond additional engineered removal (e.g. Direct Air Capture), in native unit/s
 */
export function integrateGasConcentration(
  extraConcentration: Decimal,
  productionPerSecond: Decimal,
  removalRateConstant: number,
  sinkEfficiency: number,
  extraRemovalPerSecond: Decimal,
  dtSeconds: number,
): Decimal {
  const k = removalRateConstant * sinkEfficiency;
  if (dtSeconds <= 0) return extraConcentration;

  // Engineered removal acts as a flat subtraction from the net production term.
  const netProduction = productionPerSecond.sub(extraRemovalPerSecond);

  if (k <= 0) {
    // No natural decay (shouldn't happen given the sink floor, but stay safe): linear growth.
    return extraConcentration.add(netProduction.mul(dtSeconds)).clampMin(0);
  }

  const decayFactor = Math.exp(-k * dtSeconds);
  const equilibrium = netProduction.div(k);
  const result = extraConcentration.mul(decayFactor).add(equilibrium.mul(1 - decayFactor));
  return result.clampMin(0);
}

export interface ForcingBreakdown {
  perGas: Record<GasId, number>;
  total: number;
}

/** Computes each gas's radiative forcing contribution (W/m^2) and the total. */
export function computeForcing(atmosphere: AtmosphereState): ForcingBreakdown {
  const perGas = {} as Record<GasId, number>;
  let total = 0;
  for (const gas of GAS_LIST) {
    const concentration = gas.baseline + atmosphere[gas.id].toNumber();
    const contribution = gas.forcing(concentration, gas.baseline);
    perGas[gas.id] = contribution;
    total += contribution;
  }
  return { perGas, total };
}

/**
 * Water vapor is modeled as feedback rather than direct emission: its
 * equilibrium concentration rises with the *other* gases' warming. The
 * caller feeds the previous temperature anomaly in; this closes the loop
 * one simulation step behind, which is stable and avoids solving forcing
 * and temperature simultaneously.
 */
export function computeWaterVaporFeedbackConcentration(previousTempAnomalyC: number): number {
  return Math.max(0, previousTempAnomalyC) * CLIMATE.h2oFeedbackPpmPerDegree;
}

/**
 * Converts total radiative forcing into a temperature anomaly. Linear in
 * the climate-sensitivity term (as in real simplified energy-balance
 * models), with a mild super-linear kicker so extreme lategame forcing
 * produces escalating, increasingly absurd temperatures rather than
 * flattening out.
 */
export function computeTemperatureAnomaly(totalForcingWm2: number): number {
  const forcing = Math.max(0, totalForcingWm2);
  const linear = CLIMATE.climateSensitivity * forcing;
  const superLinear = CLIMATE.superLinearCoefficient * Math.pow(forcing, CLIMATE.superLinearExponent);
  return linear + superLinear;
}

export function gasDisplayConcentration(gasId: GasId, atmosphere: AtmosphereState): number {
  const gas = GASES[gasId];
  return gas.baseline + atmosphere[gasId].toNumber();
}
