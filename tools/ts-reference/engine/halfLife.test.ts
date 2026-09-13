import { describe, it, expect } from 'vitest';
import { D, Decimal } from './bignum';
import { integrateGasConcentration } from './climate';
import { GASES, halfLifeFractionRemaining, naturalRemovalRateConstant } from './gases';
import {
  GAME_SECONDS_PER_REAL_SECOND,
  REAL_SECONDS_PER_GAME_YEAR,
  gameSecondsFor,
  gameSecondsToYears,
  gameYearsToSeconds,
} from './gameTime';
import { createNewGame, startNewRun } from './gameState';
import { computePrestigeMultipliers } from './prestige';
import { simulateStep } from './simulation';
import { computeOfflineProgress } from './offline';

const noPrestige = computePrestigeMultipliers({});

/** Real seconds that a span of simulated years takes to elapse. */
const realSecondsForYears = (years: number) => years * REAL_SECONDS_PER_GAME_YEAR;

/** Decay-only integration of a stock over a span of simulated years. */
function decayOnly(start: number, halfLifeYears: number, years: number): number {
  const k = Math.LN2 / (halfLifeYears * REAL_SECONDS_PER_GAME_YEAR);
  return integrateGasConcentration(D(start), Decimal.ZERO, k, 1, Decimal.ZERO, realSecondsForYears(years)).toNumber();
}

describe('half-life decay', () => {
  it('halves a stock over one half-life', () => {
    expect(decayOnly(1000, 120, 120)).toBeCloseTo(500, 6);
  });

  it('quarters it over two', () => {
    expect(decayOnly(1000, 120, 240)).toBeCloseTo(250, 6);
  });

  it('eighths it over three', () => {
    expect(decayOnly(1000, 120, 360)).toBeCloseTo(125, 6);
  });

  it('follows 2^(-t/H) between half-lives', () => {
    expect(decayOnly(1000, 120, 60)).toBeCloseTo(1000 / Math.SQRT2, 6);
  });

  it('agrees with the closed-form law for every gas', () => {
    for (const gas of Object.values(GASES)) {
      if (!gas.decaysOnSimulatedClock) continue;
      const years = gas.halfLifeYears * 1.7;
      const expected = 1000 * halfLifeFractionRemaining(gas.halfLifeYears, gameYearsToSeconds(years));
      const k = naturalRemovalRateConstant(gas);
      const actual = integrateGasConcentration(
        D(1000), Decimal.ZERO, k, 1, Decimal.ZERO, realSecondsForYears(years),
      ).toNumber();
      expect(actual).toBeCloseTo(expected, 6);
    }
  });

  it('never goes negative, NaN or infinite at extreme spans', () => {
    for (const years of [0, 1e-9, 1e6, 1e12]) {
      const value = decayOnly(1e30, 0.03, years);
      expect(Number.isFinite(value)).toBe(true);
      expect(value).toBeGreaterThanOrEqual(0);
    }
  });
});

describe('production against decay', () => {
  const k = naturalRemovalRateConstant(GASES.co2);

  it('declines with no production', () => {
    const after = integrateGasConcentration(D(1000), Decimal.ZERO, k, 1, Decimal.ZERO, realSecondsForYears(30));
    expect(after.toNumber()).toBeLessThan(1000);
  });

  it('accumulates when production outruns decay', () => {
    const production = D(1000 * k * 4); // four times the rate the stock is losing
    const after = integrateGasConcentration(D(1000), production, k, 1, Decimal.ZERO, realSecondsForYears(30));
    expect(after.toNumber()).toBeGreaterThan(1000);
  });

  it('holds steady when production exactly balances decay', () => {
    const production = D(1000 * k);
    const after = integrateGasConcentration(D(1000), production, k, 1, Decimal.ZERO, realSecondsForYears(500));
    expect(after.toNumber()).toBeCloseTo(1000, 6);
  });
});

describe('the simulated clock', () => {
  it('advances the Earth by one simulated day per real second simulated', () => {
    const state = simulateStep(createNewGame(0), 1, noPrestige).state;
    expect(state.gameAgeSeconds).toBe(GAME_SECONDS_PER_REAL_SECOND);
  });

  it('starts a new game at age zero', () => {
    expect(createNewGame(0).gameAgeSeconds).toBe(0);
  });

  it('ages identically in one step and in many', () => {
    const base = createNewGame(0);
    const oneShot = simulateStep(base, 3600, noPrestige).state;
    let stepped = base;
    for (let i = 0; i < 14400; i++) stepped = simulateStep(stepped, 0.25, noPrestige).state;
    expect(stepped.gameAgeSeconds).toBeCloseTo(oneShot.gameAgeSeconds, 6);
  });

  it('ages through offline progression too', () => {
    const base = { ...createNewGame(0), lastTickAt: 0 };
    const result = computeOfflineProgress(base, 3600 * 1000, noPrestige, true);
    expect(result.state.gameAgeSeconds).toBeCloseTo(gameSecondsFor(3600), 6);
  });

  it('ages by the offline cap rather than the absence when capped', () => {
    const base = { ...createNewGame(0), lastTickAt: 0 };
    const week = 7 * 24 * 3600;
    const result = computeOfflineProgress(base, week * 1000, noPrestige, true);
    expect(result.state.gameAgeSeconds).toBeCloseTo(gameSecondsFor(result.simulatedSeconds), 6);
    expect(result.state.gameAgeSeconds).toBeLessThan(gameSecondsFor(week));
  });

  it('does not age at all when offline progress is switched off', () => {
    const base = { ...createNewGame(0), lastTickAt: 0 };
    const result = computeOfflineProgress(base, 3600 * 1000, noPrestige, false);
    expect(result.state.gameAgeSeconds).toBe(0);
  });

  it('resets the Earth age on prestige but keeps the lifetime total', () => {
    const aged = simulateStep(createNewGame(0), 7200, noPrestige).state;
    expect(aged.gameAgeSeconds).toBeGreaterThan(0);
    const fresh = startNewRun(aged, 1);
    expect(fresh.gameAgeSeconds).toBe(0);
    expect(fresh.lifetimeStats.totalSimulatedSeconds).toBe(aged.lifetimeStats.totalSimulatedSeconds);
  });

  it('survives very large ages without losing the year conversion', () => {
    const millionYears = gameYearsToSeconds(1e6);
    expect(gameSecondsToYears(millionYears)).toBeCloseTo(1e6, 3);
  });
});

describe('live and offline agree', () => {
  it('reaches the same atmosphere either way over the same span', () => {
    const base = { ...createNewGame(0), techOwned: { natural_fire: 6, controlled_fire: 14 }, lastTickAt: 0 };
    const span = 4 * 3600;

    let live = base;
    for (let i = 0; i < span / 0.25; i++) live = simulateStep(live, 0.25, noPrestige).state;

    const offline = computeOfflineProgress(base, span * 1000, noPrestige, true).state;

    for (const gas of Object.values(GASES)) {
      if (gas.id === 'h2o') continue; // documented: a feedback, one step behind
      const a = live.atmosphere[gas.id].toNumber();
      const b = offline.atmosphere[gas.id].toNumber();
      expect(Math.abs(a - b)).toBeLessThanOrEqual(Math.max(a, b) * 1e-9 + 1e-12);
    }
    expect(live.gameAgeSeconds).toBeCloseTo(offline.gameAgeSeconds, 6);
  });
});
