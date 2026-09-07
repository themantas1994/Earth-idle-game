import { describe, it, expect } from 'vitest';
import { D, Decimal } from './bignum';
import {
  PRESTIGE_UPGRADE_BY_ID,
  prestigeUpgradeCost,
  computePrestigeMultipliers,
  calculatePrestigeGain,
} from './prestige';
import { GAS_LIST, GasId } from './gases';
import { GasTotals } from './gameState';

function zeroGasTotals(): GasTotals {
  return Object.fromEntries(GAS_LIST.map((g) => [g.id, Decimal.ZERO])) as GasTotals;
}

describe('prestigeUpgradeCost', () => {
  it('scales geometrically with level', () => {
    const upgrade = PRESTIGE_UPGRADE_BY_ID['atmospheric_momentum'];
    const cost0 = prestigeUpgradeCost(upgrade, 0);
    const cost1 = prestigeUpgradeCost(upgrade, 1);
    expect(cost1.div(cost0).toNumber()).toBeCloseTo(upgrade.costGrowth, 5);
  });
});

describe('computePrestigeMultipliers', () => {
  it('is a no-op with nothing owned', () => {
    const m = computePrestigeMultipliers({});
    expect(m.global).toBe(1);
    expect(m.research).toBe(1);
    expect(m.allGas).toBe(1);
    expect(m.startingTechIds).toEqual([]);
  });

  it('stacks global production multiplier multiplicatively per level', () => {
    const m = computePrestigeMultipliers({ atmospheric_momentum: 3 });
    expect(m.global).toBeCloseTo(Math.pow(1.1, 3), 6);
  });

  it('grants starting tech ids and starting resources', () => {
    const m = computePrestigeMultipliers({ industrial_memory: 1, head_start: 1 });
    expect(m.startingTechIds).toContain('steam_engine');
    expect(m.startingResources.energy).toBe(1000);
  });

  it('applies the anthropocene mastery x10 gas multiplier only to allGas', () => {
    const m = computePrestigeMultipliers({ anthropocene_mastery: 1 });
    expect(m.allGas).toBe(10);
    expect(m.global).toBe(1);
  });

  it('caps tech cost discount at 0.9', () => {
    const m = computePrestigeMultipliers({ civilizational_acceleration: 4 });
    expect(m.techCostDiscount).toBeLessThanOrEqual(0.9);
  });
});

describe('calculatePrestigeGain', () => {
  it('is zero when nothing was produced', () => {
    const gain = calculatePrestigeGain({
      totalGasProducedKg: zeroGasTotals(),
      peakForcingWm2: 0,
      civLevel: 0,
      runDurationSeconds: 0,
    });
    expect(gain.isZero()).toBe(true);
  });

  it('increases with total gas produced', () => {
    const totals = zeroGasTotals();
    const low = { ...totals, co2: D(1e10) };
    const high = { ...totals, co2: D(1e15) };
    const gainLow = calculatePrestigeGain({ totalGasProducedKg: low, peakForcingWm2: 1, civLevel: 1, runDurationSeconds: 600 });
    const gainHigh = calculatePrestigeGain({ totalGasProducedKg: high, peakForcingWm2: 1, civLevel: 1, runDurationSeconds: 600 });
    expect(gainHigh.gt(gainLow)).toBe(true);
  });

  it('has diminishing returns (10x mass gives less than 10x prestige)', () => {
    const totals = zeroGasTotals();
    const base = { ...totals, co2: D(1e12) };
    const tenX = { ...totals, co2: D(1e13) };
    const gainBase = calculatePrestigeGain({ totalGasProducedKg: base, peakForcingWm2: 5, civLevel: 5, runDurationSeconds: 1800 });
    const gainTenX = calculatePrestigeGain({ totalGasProducedKg: tenX, peakForcingWm2: 5, civLevel: 5, runDurationSeconds: 1800 });
    const ratio = gainTenX.div(gainBase).toNumber();
    expect(ratio).toBeLessThan(10);
    expect(ratio).toBeGreaterThan(1);
  });

  it('rewards higher peak forcing and civilization level', () => {
    const totals = { ...zeroGasTotals(), co2: D(1e12) };
    const weak = calculatePrestigeGain({ totalGasProducedKg: totals, peakForcingWm2: 1, civLevel: 1, runDurationSeconds: 600 });
    const strong = calculatePrestigeGain({ totalGasProducedKg: totals, peakForcingWm2: 20, civLevel: 30, runDurationSeconds: 600 });
    expect(strong.gt(weak)).toBe(true);
  });
});
