import { describe, it, expect } from 'vitest';
import { D } from './bignum';
import { createNewGame } from './gameState';
import { applyManualTap, purchaseTechnology, grantTechnology } from './economy';
import { computePrestigeMultipliers } from './prestige';
import { nextPurchaseCost, TECH_BY_ID } from './technologies';

const noPrestige = computePrestigeMultipliers({});

describe('applyManualTap', () => {
  it('adds energy and increments the tap counter', () => {
    const state = createNewGame(0);
    const next = applyManualTap(state, noPrestige);
    expect(next.resources.energy.gt(0)).toBe(true);
    expect(next.lifetimeStats.totalTaps).toBe(1);
  });

  it('scales with the prestige tap-power multiplier', () => {
    const state = createNewGame(0);
    const boosted = computePrestigeMultipliers({ tap_conditioning: 2 });
    const normal = applyManualTap(state, noPrestige).resources.energy;
    const withBoost = applyManualTap(state, boosted).resources.energy;
    expect(withBoost.gt(normal)).toBe(true);
  });
});

describe('purchaseTechnology', () => {
  it('fails when requirements are not met', () => {
    const state = createNewGame(0);
    const result = purchaseTechnology(state, 'controlled_fire', 1, noPrestige);
    expect(result.success).toBe(false);
  });

  it('fails when the player cannot afford even one unit', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { natural_fire: 1 } };
    const result = purchaseTechnology(state, 'controlled_fire', 1, noPrestige);
    expect(result.success).toBe(false);
    expect(result.purchasedQuantity).toBe(0);
  });

  it('buys one unit, deducting the exact cost', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { natural_fire: 1 } };
    const cost = nextPurchaseCost(TECH_BY_ID['controlled_fire'], 0)[0].amount;
    state = { ...state, resources: { ...state.resources, energy: cost } };

    const result = purchaseTechnology(state, 'controlled_fire', 1, noPrestige);
    expect(result.success).toBe(true);
    expect(result.purchasedQuantity).toBe(1);
    expect(result.state.techOwned.controlled_fire).toBe(1);
    expect(result.state.resources.energy.toNumber()).toBeCloseTo(0, 6);
  });

  it('buys as many as affordable when requesting more than the wallet allows', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { natural_fire: 1 }, resources: { ...state.resources, energy: D(1000) } };
    const result = purchaseTechnology(state, 'controlled_fire', 1000, noPrestige);
    expect(result.success).toBe(true);
    expect(result.purchasedQuantity).toBeGreaterThan(0);
    expect(result.purchasedQuantity).toBeLessThan(1000);
    expect(result.state.resources.energy.gte(0)).toBe(true);
  });

  it('applies the prestige tech cost discount', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { natural_fire: 1 } };
    const nominalCost = nextPurchaseCost(TECH_BY_ID['controlled_fire'], 0)[0].amount;
    const discounted = computePrestigeMultipliers({ civilizational_acceleration: 1 });
    state = { ...state, resources: { ...state.resources, energy: nominalCost.mul(0.85) } };
    const result = purchaseTechnology(state, 'controlled_fire', 1, discounted);
    expect(result.success).toBe(true);
  });

  it('rejects purchases of a technology disabled by an active challenge', () => {
    let state = createNewGame(0);
    state = {
      ...state,
      techOwned: { natural_fire: 1 },
      resources: { ...state.resources, energy: D(1e9) },
    };
    const result = purchaseTechnology(state, 'controlled_fire', 1, noPrestige, new Set(['controlled_fire']));
    expect(result.success).toBe(false);
  });

  it('respects mutually exclusive choice groups', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { coal_power_plant: 1 }, resources: { ...state.resources, energy: D(1e9) } };
    const first = purchaseTechnology(state, 'coal_industrialization', 1, noPrestige);
    expect(first.success).toBe(true);
    const second = purchaseTechnology(first.state, 'nuclear_industrialization', 1, noPrestige);
    expect(second.success).toBe(false);
  });
});

describe('grantTechnology', () => {
  it('grants ownership without charging any cost', () => {
    const state = createNewGame(0);
    const next = grantTechnology(state, 'steam_engine');
    expect(next.techOwned.steam_engine).toBe(1);
    expect(next.resources.energy.isZero()).toBe(true);
  });

  it('is a no-op if already owned', () => {
    let state = createNewGame(0);
    state = grantTechnology(state, 'steam_engine');
    const again = grantTechnology(state, 'steam_engine');
    expect(again).toBe(state);
  });
});
