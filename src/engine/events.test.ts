import { describe, it, expect } from 'vitest';
import { createInitialAtmosphere } from './climate';
import {
  RANDOM_EVENTS,
  RANDOM_EVENT_BY_ID,
  rollRandomEvent,
  computeActiveEventMultipliers,
  removeExpiredEvents,
  applyInstantGasBurst,
} from './events';

describe('random event data integrity', () => {
  it('has unique ids', () => {
    const ids = RANDOM_EVENTS.map((e) => e.id);
    expect(new Set(ids).size).toBe(ids.length);
  });
});

describe('rollRandomEvent', () => {
  it('returns null when nothing is eligible at civ level 0 and everything is already active', () => {
    const allIds = new Set(RANDOM_EVENTS.filter((e) => e.minCivLevel === 0).map((e) => e.id));
    const result = rollRandomEvent(0, allIds, 0.5);
    expect(result).toBeNull();
  });

  it('only returns events whose minCivLevel is satisfied', () => {
    for (let i = 0; i < 20; i++) {
      const result = rollRandomEvent(4, new Set(), i / 20);
      expect(result).not.toBeNull();
      expect(result!.minCivLevel).toBeLessThanOrEqual(4);
    }
  });

  it('excludes events already active', () => {
    const eligibleAtZero = RANDOM_EVENTS.filter((e) => e.minCivLevel <= 0);
    const activeIds = new Set(eligibleAtZero.map((e) => e.id));
    const result = rollRandomEvent(0, activeIds, 0.5);
    expect(result).toBeNull();
  });
});

describe('computeActiveEventMultipliers', () => {
  it('is empty with no active events', () => {
    const result = computeActiveEventMultipliers([], 1000);
    expect(result.global).toBeUndefined();
  });

  it('applies a non-expired event\'s multiplier', () => {
    const result = computeActiveEventMultipliers(
      [{ id: 'a', eventDefId: 'industrial_boom', startedAt: 0, endsAt: 5000 }],
      1000,
    );
    expect(result.global).toBe(RANDOM_EVENT_BY_ID['industrial_boom'].effect.global);
  });

  it('ignores expired events', () => {
    const result = computeActiveEventMultipliers(
      [{ id: 'a', eventDefId: 'industrial_boom', startedAt: 0, endsAt: 500 }],
      1000,
    );
    expect(result.global).toBeUndefined();
  });

  it('combines multiple simultaneous events multiplicatively', () => {
    const result = computeActiveEventMultipliers(
      [
        { id: 'a', eventDefId: 'industrial_boom', startedAt: 0, endsAt: 5000 },
        { id: 'b', eventDefId: 'el_nino', startedAt: 0, endsAt: 5000 },
      ],
      1000,
    );
    const expected = RANDOM_EVENT_BY_ID['industrial_boom'].effect.global! * 1; // el_nino has no `global` field
    expect(result.global).toBe(expected);
    expect(result.allGas).toBe(RANDOM_EVENT_BY_ID['el_nino'].effect.allGas);
  });
});

describe('removeExpiredEvents', () => {
  it('drops events whose endsAt has passed', () => {
    const events = [
      { id: 'a', eventDefId: 'industrial_boom', startedAt: 0, endsAt: 500 },
      { id: 'b', eventDefId: 'el_nino', startedAt: 0, endsAt: 5000 },
    ];
    const remaining = removeExpiredEvents(events, 1000);
    expect(remaining.map((e) => e.id)).toEqual(['b']);
  });
});

describe('applyInstantGasBurst', () => {
  it('adds the burst mass converted into the gas\'s native concentration unit', () => {
    const atmosphere = createInitialAtmosphere();
    const def = RANDOM_EVENT_BY_ID['volcanic_eruption'];
    const next = applyInstantGasBurst(atmosphere, def);
    expect(next.co2.gt(0)).toBe(true);
    expect(atmosphere.co2.isZero()).toBe(true); // original untouched
  });

  it('is a no-op for events with no instant burst', () => {
    const atmosphere = createInitialAtmosphere();
    const def = RANDOM_EVENT_BY_ID['industrial_boom'];
    const next = applyInstantGasBurst(atmosphere, def);
    expect(next).toBe(atmosphere);
  });
});
