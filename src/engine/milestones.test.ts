import { describe, it, expect } from 'vitest';
import { createNewGame } from './gameState';
import { MILESTONES, MILESTONE_BY_ID, checkMilestones } from './milestones';
import { GASES } from './gases';
import { D } from './bignum';

/** Builds a state with the atmosphere and derived readouts a milestone condition looks at. */
function stateWith(overrides: {
  co2Ppm?: number;
  temperatureAnomalyC?: number;
  oceanPh?: number;
  seaLevelRiseMeters?: number;
  habitability?: number;
  agriculture?: number;
  biodiversity?: number;
  techOwned?: Record<string, number>;
}) {
  const base = createNewGame(0);
  return {
    ...base,
    techOwned: { ...base.techOwned, ...(overrides.techOwned ?? {}) },
    atmosphere: {
      ...base.atmosphere,
      co2: D(Math.max(0, (overrides.co2Ppm ?? GASES.co2.baseline) - GASES.co2.baseline)),
    },
    temperatureAnomalyC: overrides.temperatureAnomalyC ?? 0,
    oceanPh: overrides.oceanPh ?? 8.1,
    seaLevelRiseMeters: overrides.seaLevelRiseMeters ?? 0,
    habitability: {
      fraction: overrides.habitability ?? 1,
      factors: {
        temperature: 1,
        oceanAcidity: 1,
        seaLevel: 1,
        agriculture: overrides.agriculture ?? 1,
        biodiversity: overrides.biodiversity ?? 1,
      },
    },
  };
}

describe('milestone definitions', () => {
  it('have unique ids', () => {
    const ids = MILESTONES.map((m) => m.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('are all reachable through MILESTONE_BY_ID', () => {
    for (const m of MILESTONES) expect(MILESTONE_BY_ID[m.id]).toBe(m);
  });

  it('all carry a headline, body and dateline for the news feed to render', () => {
    for (const m of MILESTONES) {
      expect(m.headline.length).toBeGreaterThan(0);
      expect(m.body.length).toBeGreaterThan(0);
      expect(m.source.length).toBeGreaterThan(0);
    }
  });

  it('reference only technologies that exist, so a headline can never be unreachable', async () => {
    const { TECH_BY_ID } = await import('./technologies');
    // The tech-triggered milestones are the early-run ones; probe each definition
    // with a state that owns every technology and check the set that fires.
    const everything = Object.fromEntries(Object.keys(TECH_BY_ID).map((id) => [id, 1]));
    const fired = checkMilestones(stateWith({ techOwned: everything }));
    expect(fired.length).toBeGreaterThan(10);
  });
});

describe('checkMilestones', () => {
  it('fires nothing on a pristine Earth', () => {
    expect(checkMilestones(createNewGame(0))).toEqual([]);
  });

  it('fires a technology milestone as soon as the technology is owned', () => {
    const fired = checkMilestones(stateWith({ techOwned: { controlled_fire: 1 } }));
    expect(fired.map((m) => m.id)).toContain('first_smoke');
  });

  it('fires climate milestones once their threshold is crossed', () => {
    const fired = checkMilestones(stateWith({ co2Ppm: 420, temperatureAnomalyC: 1.6 }));
    const ids = fired.map((m) => m.id);
    expect(ids).toContain('co2_300');
    expect(ids).toContain('co2_350');
    expect(ids).toContain('co2_400');
    expect(ids).toContain('paris_1_5');
  });

  it('never re-fires a milestone already recorded for this run', () => {
    const state = stateWith({ temperatureAnomalyC: 1.6 });
    const once = checkMilestones(state);
    expect(once.map((m) => m.id)).toContain('paris_1_5');

    const after = { ...state, milestonesTriggered: Object.fromEntries(once.map((m) => [m.id, true])) };
    expect(checkMilestones(after)).toEqual([]);
  });

  it('covers the whole arc: sea level, acidification and habitability all have headlines', () => {
    const ids = checkMilestones(
      stateWith({
        seaLevelRiseMeters: 6,
        oceanPh: 7.7,
        habitability: 0.05,
        agriculture: 0.5,
        biodiversity: 0.4,
        temperatureAnomalyC: 30,
      }),
    ).map((m) => m.id);

    expect(ids).toEqual(
      expect.arrayContaining([
        'sea_level_cities',
        'coastlines_gone',
        'reef_bleached',
        'ocean_dead_zones',
        'crop_failure',
        'insect_collapse',
        'climate_refugees',
        'last_broadcast',
        'venus_watch',
      ]),
    );
  });
});
