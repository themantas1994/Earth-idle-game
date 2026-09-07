import { describe, it, expect, beforeEach } from 'vitest';
import { D } from './bignum';
import { createNewGame } from './gameState';
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
