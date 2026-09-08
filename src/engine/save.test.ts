import { describe, it, expect, beforeEach } from 'vitest';
import { D } from './bignum';
import { createNewGame, GameState, SAVE_VERSION } from './gameState';
import {
  serializeGameState,
  deserializeGameState,
  saveGame,
  loadGame,
  StorageAdapter,
  SAVE_KEY,
  BACKUP_SAVE_KEY,
} from './save';

function createMemoryAdapter(): StorageAdapter & { store: Map<string, string> } {
  const store = new Map<string, string>();
  return {
    store,
    getItem: (key) => store.get(key) ?? null,
    setItem: (key, value) => {
      store.set(key, value);
    },
  };
}

describe('serializeGameState / deserializeGameState', () => {
  it('round-trips a fresh game state exactly', () => {
    const state = createNewGame(12345);
    const json = serializeGameState(state);
    const restored = deserializeGameState(json);
    expect(restored).not.toBeNull();
    expect(restored!.runNumber).toBe(0);
    expect(restored!.createdAt).toBe(12345);
  });

  it('round-trips large Decimal values without precision loss', () => {
    let state = createNewGame(0);
    state = { ...state, resources: { ...state.resources, energy: D('4.821e97') }, prestige: { ...state.prestige, earthPoints: D('1.5e12') } };
    const restored = deserializeGameState(serializeGameState(state));
    expect(restored!.resources.energy.eq(D('4.821e97'))).toBe(true);
    expect(restored!.prestige.earthPoints.eq(D('1.5e12'))).toBe(true);
  });

  it('round-trips tech ownership and atmosphere state', () => {
    let state = createNewGame(0);
    state = { ...state, techOwned: { controlled_fire: 42 }, atmosphere: { ...state.atmosphere, co2: D(120) } };
    const restored = deserializeGameState(serializeGameState(state));
    expect(restored!.techOwned.controlled_fire).toBe(42);
    expect(restored!.atmosphere.co2.eq(D(120))).toBe(true);
  });

  it('rejects malformed JSON without throwing', () => {
    expect(deserializeGameState('{not valid json')).toBeNull();
  });

  it('rejects well-formed JSON that is not a plausible game state', () => {
    expect(deserializeGameState(JSON.stringify({ hello: 'world' }))).toBeNull();
  });

  it('rejects an empty string', () => {
    expect(deserializeGameState('')).toBeNull();
  });
});

describe('saveGame / loadGame', () => {
  it('saves and loads a round trip through a storage adapter', async () => {
    const adapter = createMemoryAdapter();
    const state = createNewGame(999);
    await saveGame(state, adapter);
    const loaded = await loadGame(adapter);
    expect(loaded).not.toBeNull();
    expect(loaded!.createdAt).toBe(999);
  });

  it('returns null when there is nothing saved yet', async () => {
    const adapter = createMemoryAdapter();
    const loaded = await loadGame(adapter);
    expect(loaded).toBeNull();
  });

  it('keeps the previous save as a backup on the next save', async () => {
    const adapter = createMemoryAdapter();
    await saveGame(createNewGame(1), adapter);
    await saveGame(createNewGame(2), adapter);
    expect(adapter.store.get(BACKUP_SAVE_KEY)).toBeDefined();
    const backupState = deserializeGameState(adapter.store.get(BACKUP_SAVE_KEY)!);
    expect(backupState!.createdAt).toBe(1);
  });

  it('falls back to the backup save if the primary save is corrupted', async () => {
    const adapter = createMemoryAdapter();
    await saveGame(createNewGame(1), adapter);
    await saveGame(createNewGame(2), adapter);
    adapter.store.set(SAVE_KEY, '{ this is corrupted json');
    const loaded = await loadGame(adapter);
    expect(loaded).not.toBeNull();
    expect(loaded!.createdAt).toBe(1);
  });
});

describe('v2 -> v3 migration', () => {
  it('rescales banked Earth Points onto the new prestige curve and keeps owned upgrades', () => {
    const current = createNewGame(0);
    const v2 = {
      ...current,
      saveVersion: 2,
      prestige: { earthPoints: D('1e9'), upgradesOwned: { institutional_memory: 3, atmospheric_momentum: 4 } },
      lifetimeStats: { ...current.lifetimeStats, totalEarthPointsEarned: D('4e9') },
    } as unknown as GameState;

    const state = deserializeGameState(serializeGameState(v2)) as GameState;
    expect(state.saveVersion).toBe(SAVE_VERSION);
    // The payout scale moved by about five orders of magnitude, so a v2
    // balance that used to buy the whole tree at once must not still do so.
    expect(state.prestige.earthPoints.lt(D('1e9'))).toBe(true);
    expect(state.prestige.earthPoints.gt(0)).toBe(true);
    expect(state.lifetimeStats.totalEarthPointsEarned.lt(D('4e9'))).toBe(true);
    // Levels already bought are kept — Institutional Memory changed what it
    // does, not whether you own it.
    expect(state.prestige.upgradesOwned.institutional_memory).toBe(3);
    expect(state.prestige.upgradesOwned.atmospheric_momentum).toBe(4);
  });

  it('leaves a fresh save untouched, since it has nothing banked', () => {
    const state = deserializeGameState(serializeGameState(createNewGame(0))) as GameState;
    expect(state.prestige.earthPoints.toNumber()).toBe(0);
  });
});

describe('v1 -> v2 migration', () => {
  it('grants Natural Fire, refunds Tap Conditioning, and seeds the news feed', () => {
    const current = createNewGame(0);
    const v1 = {
      ...current,
      saveVersion: 1,
      techOwned: {},
      prestige: { earthPoints: D(1000), upgradesOwned: { tap_conditioning: 3, atmospheric_momentum: 2 } },
    } as unknown as GameState;
    // A v1 save has none of the fields v2 added.
    const asRecord = v1 as unknown as Record<string, unknown>;
    delete asRecord.milestonesTriggered;
    delete asRecord.newsFeed;

    const migrated = deserializeGameState(serializeGameState(v1));
    expect(migrated).not.toBeNull();
    const state = migrated as GameState;

    expect(state.saveVersion).toBe(SAVE_VERSION);
    // Energy now comes from a generator, so a save without one would be a dead run.
    expect(state.techOwned.natural_fire).toBeGreaterThanOrEqual(1);
    // The removed upgrade is gone, and what was spent on it comes back.
    expect(state.prestige.upgradesOwned.tap_conditioning).toBeUndefined();
    expect(state.prestige.upgradesOwned.atmospheric_momentum).toBe(2);
    // Compared against the same save without the refunded upgrade, rather than
    // against a fixed number: the v3 step rescales every balance afterwards,
    // and this test is about the refund, not about that ratio.
    const withoutTapUpgrade = { ...v1, prestige: { ...v1.prestige, upgradesOwned: { atmospheric_momentum: 2 } } };
    const baseline = deserializeGameState(serializeGameState(withoutTapUpgrade)) as GameState;
    expect(state.prestige.earthPoints.gt(baseline.prestige.earthPoints)).toBe(true);
    // New per-run state is present and empty.
    expect(state.milestonesTriggered).toEqual({});
    expect(state.newsFeed).toEqual([]);
  });

  it('leaves a save already at the current version alone', () => {
    const current = createNewGame(0);
    const migrated = deserializeGameState(serializeGameState(current)) as GameState;
    expect(migrated.saveVersion).toBe(SAVE_VERSION);
    expect(migrated.prestige.earthPoints.toNumber()).toBe(0);
  });
});
