import { describe, it, expect } from 'vitest';
import { createNewGame } from './gameState';
import { checkAchievements, ACHIEVEMENTS, AchievementContext } from './achievements';

function baseCtx(overrides: Partial<AchievementContext> = {}): AchievementContext {
  return {
    state: createNewGame(0),
    civLevel: 0,
    justCollapsed: false,
    justReset: false,
    lastRunDurationSeconds: null,
    previousRunDurationSeconds: null,
    ...overrides,
  };
}

describe('achievement data integrity', () => {
  it('has unique ids', () => {
    const ids = ACHIEVEMENTS.map((a) => a.id);
    expect(new Set(ids).size).toBe(ids.length);
  });
});

describe('checkAchievements', () => {
  it('unlocks nothing for a fresh game', () => {
    const unlocked = checkAchievements(baseCtx(), {});
    expect(unlocked).toEqual([]);
  });

  it('unlocks "first_spark" once fire is discovered', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { controlled_fire: 1 } };
    const unlocked = checkAchievements(baseCtx({ state }), {});
    expect(unlocked).toContain('first_spark');
  });

  it('does not re-unlock an already-unlocked achievement', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { controlled_fire: 1 } };
    const unlocked = checkAchievements(baseCtx({ state }), { first_spark: true });
    expect(unlocked).not.toContain('first_spark');
  });

  it('unlocks "oops" only on the justCollapsed event', () => {
    const notCollapsed = checkAchievements(baseCtx({ justCollapsed: false }), {});
    const collapsed = checkAchievements(baseCtx({ justCollapsed: true }), {});
    expect(notCollapsed).not.toContain('oops');
    expect(collapsed).toContain('oops');
  });

  it('unlocks "faster_this_time" only when the new run beat the previous one', () => {
    const slower = checkAchievements(
      baseCtx({ justReset: true, lastRunDurationSeconds: 500, previousRunDurationSeconds: 400 }),
      {},
    );
    const faster = checkAchievements(
      baseCtx({ justReset: true, lastRunDurationSeconds: 300, previousRunDurationSeconds: 400 }),
      {},
    );
    expect(slower).not.toContain('faster_this_time');
    expect(faster).toContain('faster_this_time');
  });

  it('unlocks temperature-threshold achievements based on lifetime highs', () => {
    let state = createNewGame(0);
    state = { ...state, lifetimeStats: { ...state.lifetimeStats, highestTemperatureC: 12 } };
    const unlocked = checkAchievements(baseCtx({ state }), {});
    expect(unlocked).toContain('hot');
    expect(unlocked).toContain('very_hot');
    expect(unlocked).not.toContain('scorching');
  });
});
