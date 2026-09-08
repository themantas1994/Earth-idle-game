import { describe, it, expect } from 'vitest';
import { D } from './bignum';
import { createNewGame } from './gameState';
import {
  computeEffectiveMultipliers,
  computeProductionRates,
  computeCivLevel,
  computeTechProductionRates,
  simulateStep,
} from './simulation';
import { TECH_BY_ID } from './technologies';
import { computePrestigeMultipliers } from './prestige';
import { OWNERSHIP_BONUS } from './constants';

const noPrestige = computePrestigeMultipliers({});

describe('computeEffectiveMultipliers', () => {
  it('defaults to all-1 multipliers with nothing owned', () => {
    const m = computeEffectiveMultipliers({}, noPrestige);
    expect(m.global).toBe(1);
    expect(m.research).toBe(1);
    expect(m.allGas).toBe(1);
  });

  it('applies a global multiplier tech once owned', () => {
    const m = computeEffectiveMultipliers({ mass_manufacturing: 1 }, noPrestige);
    expect(m.global).toBeCloseTo(1.25, 5);
  });

  it('combines multiple multiplier techs multiplicatively', () => {
    const m = computeEffectiveMultipliers({ mass_manufacturing: 1, grid_electricity: 1 }, noPrestige);
    expect(m.global).toBeCloseTo(1.25 * 1.2, 5);
  });

  it('folds in prestige multipliers on top of tech multipliers', () => {
    const prestige = computePrestigeMultipliers({ atmospheric_momentum: 2 });
    const m = computeEffectiveMultipliers({ mass_manufacturing: 1 }, prestige);
    expect(m.global).toBeCloseTo(1.25 * Math.pow(1.1, 2), 5);
  });
});

describe('computeProductionRates', () => {
  it('produces zero for an empty tech ownership map', () => {
    const rates = computeProductionRates({}, computeEffectiveMultipliers({}, noPrestige));
    expect(rates.gasGrossKgPerS.co2.isZero()).toBe(true);
    expect(rates.resourcePerS.energy.isZero()).toBe(true);
  });

  it('scales linearly with owned count below the first ownership threshold', () => {
    const multipliers = computeEffectiveMultipliers({ controlled_fire: 1 }, noPrestige);
    const rates1 = computeProductionRates({ controlled_fire: 1 }, multipliers);
    const rates9 = computeProductionRates({ controlled_fire: OWNERSHIP_BONUS.everyUnits - 1 }, multipliers);
    expect(rates9.gasGrossKgPerS.co2.div(rates1.gasGrossKgPerS.co2).toNumber()).toBeCloseTo(OWNERSHIP_BONUS.everyUnits - 1, 5);
  });

  it('layers the ownership bonus on top of that linear scaling once a threshold lands', () => {
    const multipliers = computeEffectiveMultipliers({ controlled_fire: 1 }, noPrestige);
    const rates1 = computeProductionRates({ controlled_fire: 1 }, multipliers);
    const atThreshold = computeProductionRates({ controlled_fire: OWNERSHIP_BONUS.everyUnits }, multipliers);
    // Units alone would give everyUnits×; the threshold doubling makes it more.
    expect(atThreshold.gasGrossKgPerS.co2.div(rates1.gasGrossKgPerS.co2).toNumber()).toBeCloseTo(
      OWNERSHIP_BONUS.everyUnits * OWNERSHIP_BONUS.multiplier,
      5,
    );
    // ...and it applies to resources and gases alike, so the emissions-per-Energy
    // ratio a technology was authored with is never quietly changed by depth.
    expect(atThreshold.resourcePerS.energy.div(rates1.resourcePerS.energy).toNumber()).toBeCloseTo(
      OWNERSHIP_BONUS.everyUnits * OWNERSHIP_BONUS.multiplier,
      5,
    );
  });

  it('applies a choice-group gas multiplier only to the targeted gas', () => {
    const owned = { coal_power_plant: 1, coal_industrialization: 1 };
    const multipliers = computeEffectiveMultipliers(owned, noPrestige);
    const withChoice = computeProductionRates(owned, multipliers);
    const withoutChoice = computeProductionRates(
      { coal_power_plant: 1 },
      computeEffectiveMultipliers({ coal_power_plant: 1 }, noPrestige),
    );
    const ratio = withChoice.gasGrossKgPerS.co2.div(withoutChoice.gasGrossKgPerS.co2).toNumber();
    expect(ratio).toBeCloseTo(10, 5);
    // Energy production (not targeted by the co2-only multiplier) should be unaffected.
    expect(withChoice.resourcePerS.energy.eq(withoutChoice.resourcePerS.energy)).toBe(true);
  });
});

describe('computeCivLevel', () => {
  it('is zero with nothing owned', () => {
    expect(computeCivLevel({})).toBe(0);
  });

  it('increases with each distinct owned technology, independent of owned count', () => {
    const level1 = computeCivLevel({ natural_fire: 1 });
    const level2 = computeCivLevel({ natural_fire: 1, controlled_fire: 1 });
    const level2Again = computeCivLevel({ natural_fire: 1, controlled_fire: 500 });
    expect(level2).toBeGreaterThan(level1);
    expect(level2Again).toBe(level2);
  });
});

describe('simulateStep', () => {
  it('does nothing over zero time', () => {
    const state = createNewGame(0);
    const { state: next } = simulateStep(state, 0, noPrestige);
    expect(next.atmosphere.co2.isZero()).toBe(true);
  });

  it('accumulates CO2 and resources over time when a generator is owned', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { natural_fire: 1, controlled_fire: 5 } };
    const { state: next } = simulateStep(state, 100, noPrestige);
    expect(next.atmosphere.co2.gt(0)).toBe(true);
    expect(next.resources.research.gt(0)).toBe(true);
    expect(next.runStats.totalGasProducedKg.co2.gt(0)).toBe(true);
    expect(next.lifetimeStats.totalGasProducedKg.co2.gt(0)).toBe(true);
  });

  it('raises temperature and lowers habitability as CO2 accumulates', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { natural_fire: 1, controlled_fire: 5000 } };
    const { state: next } = simulateStep(state, 1e9, noPrestige); // long enough to matter
    expect(next.temperatureAnomalyC).toBeGreaterThan(0);
    expect(next.habitability.fraction).toBeLessThan(1);
  });

  it('marks the run collapsed once habitability reaches zero', () => {
    let state = createNewGame(0);
    // An absurd amount of production, over an absurdly long time, to force collapse deterministically.
    state = { ...state, techOwned: { natural_fire: 1, controlled_fire: 1_000_000 } };
    let current = state;
    for (let i = 0; i < 20; i++) {
      current = simulateStep(current, 1e10, noPrestige).state;
    }
    expect(current.habitability.fraction).toBeLessThan(0.01);
    expect(current.collapsed).toBe(true);
  });

  it('is step-size independent for a fixed total elapsed time (supports offline catch-up)', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { natural_fire: 1, controlled_fire: 20 } };

    let stepped = state;
    for (let i = 0; i < 100; i++) {
      stepped = simulateStep(stepped, 36, noPrestige).state; // 100 x 36s = 3600s
    }
    const oneShot = simulateStep(state, 3600, noPrestige).state;

    expect(stepped.atmosphere.co2.toNumber()).toBeCloseTo(oneShot.atmosphere.co2.toNumber(), 6);
    expect(stepped.resources.energy.toNumber()).toBeCloseTo(oneShot.resources.energy.toNumber(), 6);
  });

  it('tracks peak stats even if the underlying value later stays flat', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { natural_fire: 1, controlled_fire: 100 } };
    const afterGrowth = simulateStep(state, 1e8, noPrestige).state;
    const peak = afterGrowth.runStats.peakTemperatureC;
    const afterMore = simulateStep({ ...afterGrowth, techOwned: {} }, 1e6, noPrestige).state;
    expect(afterMore.runStats.peakTemperatureC).toBeGreaterThanOrEqual(peak);
  });
});

describe('computeTechProductionRates', () => {
  it('reports one technology\'s own contribution, not the global rate for its gases', () => {
    const multipliers = computeEffectiveMultipliers({}, computePrestigeMultipliers({}));
    const owned = { natural_fire: 4, controlled_fire: 3 };

    const total = computeProductionRates(owned, multipliers);
    const fromNaturalFire = computeTechProductionRates(TECH_BY_ID['natural_fire'], 4, multipliers);
    const fromControlledFire = computeTechProductionRates(TECH_BY_ID['controlled_fire'], 3, multipliers);

    expect(fromNaturalFire.gasGrossKgPerS.co2.lt(total.gasGrossKgPerS.co2)).toBe(true);
    expect(
      fromNaturalFire.gasGrossKgPerS.co2
        .add(fromControlledFire.gasGrossKgPerS.co2)
        .sub(total.gasGrossKgPerS.co2)
        .abs()
        .lt(D('1e-9')),
    ).toBe(true);
  });

  it('is zero for a technology that is not an owned generator', () => {
    const multipliers = computeEffectiveMultipliers({}, computePrestigeMultipliers({}));
    expect(computeTechProductionRates(TECH_BY_ID['cooking'], 1, multipliers).resourcePerS.energy.isZero()).toBe(true);
    expect(computeTechProductionRates(TECH_BY_ID['natural_fire'], 0, multipliers).resourcePerS.energy.isZero()).toBe(true);
  });
});
