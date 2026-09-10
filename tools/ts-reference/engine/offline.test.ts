import { describe, it, expect } from 'vitest';
import { createNewGame } from './gameState';
import { computeOfflineProgress, offlineCapSeconds } from './offline';
import { computePrestigeMultipliers } from './prestige';
import { SIMULATION } from './constants';

const noPrestige = computePrestigeMultipliers({});

describe('offlineCapSeconds', () => {
  it('matches the base cap with no prestige upgrades', () => {
    expect(offlineCapSeconds(noPrestige)).toBe(SIMULATION.baseOfflineCapSeconds);
  });
  it('scales with the extended-endurance prestige upgrade', () => {
    const prestige = computePrestigeMultipliers({ extended_endurance: 1 });
    expect(offlineCapSeconds(prestige)).toBe(SIMULATION.baseOfflineCapSeconds * 2);
  });
});

describe('computeOfflineProgress', () => {
  it('does nothing if no time has passed', () => {
    const state = createNewGame(1000);
    const result = computeOfflineProgress(state, 1000, noPrestige, true);
    expect(result.simulatedSeconds).toBe(0);
    expect(result.state.lastTickAt).toBe(1000);
  });

  it('does nothing if offline progress is disabled in settings', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { natural_fire: 1, controlled_fire: 10 } };
    const result = computeOfflineProgress(state, 3600_000, noPrestige, false);
    expect(result.simulatedSeconds).toBe(0);
    expect(result.state.resources.energy.isZero()).toBe(true);
    expect(result.state.lastTickAt).toBe(3600_000);
  });

  it('simulates production for the elapsed wall-clock time, capped at the offline limit', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { natural_fire: 1, controlled_fire: 10 } };
    const twoHoursMs = 2 * 3600 * 1000;
    const result = computeOfflineProgress(state, twoHoursMs, noPrestige, true);
    expect(result.awaySeconds).toBeCloseTo(2 * 3600, 0);
    expect(result.simulatedSeconds).toBeCloseTo(2 * 3600, 0);
    expect(result.cappedByLimit).toBe(false);
    expect(result.state.resources.research.gt(0)).toBe(true);
    expect(result.summary.gasGeneratedKg.co2.gt(0)).toBe(true);
  });

  it('caps simulated time at the offline cap for very long absences', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { natural_fire: 1, controlled_fire: 10 } };
    const thirtyHoursMs = 30 * 3600 * 1000;
    const result = computeOfflineProgress(state, thirtyHoursMs, noPrestige, true);
    expect(result.awaySeconds).toBeCloseTo(30 * 3600, 0);
    expect(result.simulatedSeconds).toBe(SIMULATION.baseOfflineCapSeconds);
    expect(result.cappedByLimit).toBe(true);
  });

  it('always advances lastTickAt to now, even when capped', () => {
    let state = createNewGame(0);
    const thirtyHoursMs = 30 * 3600 * 1000;
    const result = computeOfflineProgress(state, thirtyHoursMs, noPrestige, true);
    expect(result.state.lastTickAt).toBe(thirtyHoursMs);
  });

  it('reports a summary of resources and gases gained while away', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { natural_fire: 1, controlled_fire: 10 } };
    const result = computeOfflineProgress(state, 3600_000, noPrestige, true);
    expect(result.summary.resourcesGained.research.isZero()).toBe(false);
    expect(result.summary.temperatureAfterC).toBeGreaterThanOrEqual(result.summary.temperatureBeforeC);
  });
});
