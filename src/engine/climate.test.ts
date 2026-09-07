import { describe, it, expect } from 'vitest';
import { D } from './bignum';
import {
  createInitialAtmosphere,
  computeSinkEfficiency,
  integrateGasConcentration,
  computeForcing,
  computeTemperatureAnomaly,
  gasDisplayConcentration,
} from './climate';
import { GASES, naturalRemovalRateConstant } from './gases';

describe('createInitialAtmosphere', () => {
  it('starts every gas at zero anthropogenic addition', () => {
    const atmo = createInitialAtmosphere();
    for (const id of Object.keys(GASES)) {
      expect(atmo[id as keyof typeof atmo].isZero()).toBe(true);
    }
  });
});

describe('computeSinkEfficiency', () => {
  it('is 1 at zero warming', () => {
    expect(computeSinkEfficiency(0)).toBeCloseTo(1);
  });
  it('decreases as warming increases, but never below the floor', () => {
    const eff10 = computeSinkEfficiency(10);
    const eff50 = computeSinkEfficiency(50);
    expect(eff10).toBeLessThan(1);
    expect(eff50).toBeLessThan(eff10);
    expect(computeSinkEfficiency(10000)).toBeGreaterThanOrEqual(0.05);
  });
});

describe('integrateGasConcentration', () => {
  it('grows toward equilibrium (production/removal) over a long step', () => {
    const k = naturalRemovalRateConstant(GASES.co2); // per second
    const production = D(1000); // native-unit/s
    const equilibrium = production.toNumber() / k;
    // A very long step should land close to equilibrium regardless of step size,
    // which is the point of using the closed-form solution instead of Euler steps.
    const result = integrateGasConcentration(D(0), production, k, 1, D(0), 2000 * 365.25 * 24 * 3600);
    expect(result.toNumber()).toBeCloseTo(equilibrium, -6);
  });

  it('decays toward zero with no production', () => {
    const k = naturalRemovalRateConstant(GASES.ch4);
    const start = D(1000);
    const oneLifetimeSeconds = 1 / k;
    const result = integrateGasConcentration(start, D(0), k, 1, D(0), oneLifetimeSeconds);
    // After one lifetime (1/e survival), value should have dropped substantially.
    expect(result.toNumber()).toBeLessThan(start.toNumber() * 0.4);
    expect(result.toNumber()).toBeGreaterThan(0);
  });

  it('is stable for both tiny and huge timesteps (no step-size dependence)', () => {
    const k = naturalRemovalRateConstant(GASES.co2);
    const production = D(500);
    let stepped = D(0);
    for (let i = 0; i < 1000; i++) {
      stepped = integrateGasConcentration(stepped, production, k, 1, D(0), 3600); // 1000 hourly steps
    }
    const oneShot = integrateGasConcentration(D(0), production, k, 1, D(0), 1000 * 3600);
    expect(stepped.toNumber()).toBeCloseTo(oneShot.toNumber(), 0);
  });

  it('never goes negative when engineered removal exceeds production', () => {
    const k = naturalRemovalRateConstant(GASES.co2);
    const result = integrateGasConcentration(D(10), D(1), k, 1, D(1000), 3600);
    expect(result.toNumber()).toBeGreaterThanOrEqual(0);
  });
});

describe('computeForcing', () => {
  it('is zero at baseline concentrations', () => {
    const atmo = createInitialAtmosphere();
    const { total, perGas } = computeForcing(atmo);
    expect(total).toBeCloseTo(0, 5);
    expect(perGas.co2).toBeCloseTo(0, 5);
  });

  it('increases with CO2 concentration', () => {
    const atmo = createInitialAtmosphere();
    atmo.co2 = D(280); // doubles CO2 (280 -> 560 ppm)
    const { perGas } = computeForcing(atmo);
    expect(perGas.co2).toBeGreaterThan(3); // real-world doubling ~3.7 W/m^2; alpha=5.35*ln(2)=3.7
  });
});

describe('computeTemperatureAnomaly', () => {
  it('is zero at zero forcing', () => {
    expect(computeTemperatureAnomaly(0)).toBeCloseTo(0);
  });
  it('increases monotonically with forcing', () => {
    const low = computeTemperatureAnomaly(2);
    const high = computeTemperatureAnomaly(10);
    expect(high).toBeGreaterThan(low);
  });
  it('grows super-linearly at extreme forcing (endgame escalation)', () => {
    const t100 = computeTemperatureAnomaly(100);
    const t200 = computeTemperatureAnomaly(200);
    const linearOnlyRatio = 200 / 100;
    expect(t200 / t100).toBeGreaterThan(linearOnlyRatio);
  });
});

describe('gasDisplayConcentration', () => {
  it('adds the anthropogenic extra onto the baseline', () => {
    const atmo = createInitialAtmosphere();
    atmo.co2 = D(120);
    expect(gasDisplayConcentration('co2', atmo)).toBeCloseTo(400); // 280 baseline + 120
  });
});
