import { Decimal } from './bignum';
import { GameState, SAVE_VERSION } from './gameState';

export const SAVE_KEY = 'earth-idle-save';
export const BACKUP_SAVE_KEY = `${SAVE_KEY}.backup`;

/**
 * Storage is behind a tiny async interface so the same save logic works
 * against `localStorage` in the browser dev build and against Capacitor's
 * `Preferences` plugin in the packaged Android app, without any of the
 * serialization logic below needing to change.
 */
export interface StorageAdapter {
  getItem(key: string): string | null | Promise<string | null>;
  setItem(key: string, value: string): void | Promise<void>;
}

export const localStorageAdapter: StorageAdapter = {
  getItem(key) {
    try {
      return typeof localStorage !== 'undefined' ? localStorage.getItem(key) : null;
    } catch {
      return null;
    }
  },
  setItem(key, value) {
    try {
      if (typeof localStorage !== 'undefined') localStorage.setItem(key, value);
    } catch {
      // Storage full or unavailable (private browsing, etc.) — fail silently rather than crash the game.
    }
  },
};

interface SerializedDecimal {
  __decimal: [number, number, number];
}

function isSerializedDecimal(value: unknown): value is SerializedDecimal {
  return typeof value === 'object' && value !== null && '__decimal' in value;
}

function replacer(_key: string, value: unknown): unknown {
  if (value instanceof Decimal) {
    const serialized: SerializedDecimal = { __decimal: value.toTuple() };
    return serialized;
  }
  return value;
}

function reviver(_key: string, value: unknown): unknown {
  if (isSerializedDecimal(value)) return Decimal.fromJSON(value.__decimal);
  return value;
}

export function serializeGameState(state: GameState): string {
  return JSON.stringify(state, replacer);
}

/**
 * Structural sanity check run on every load. A save that fails this (from
 * disk corruption, a botched manual edit, or an incompatible future format)
 * is rejected rather than crashing the game or silently loading garbage.
 */
function isPlausibleGameState(value: unknown): value is GameState {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v.saveVersion === 'number' &&
    typeof v.runNumber === 'number' &&
    typeof v.resources === 'object' &&
    v.resources !== null &&
    typeof v.atmosphere === 'object' &&
    v.atmosphere !== null &&
    typeof v.techOwned === 'object' &&
    v.techOwned !== null &&
    typeof v.prestige === 'object' &&
    v.prestige !== null
  );
}

/** Upgrades an older save's shape to the current SAVE_VERSION. No-op today; a real migration chain slots in here later. */
function migrate(state: GameState): GameState {
  if (state.saveVersion === SAVE_VERSION) return state;
  // Future migrations: `if (state.saveVersion < 2) { ...patch fields...; state.saveVersion = 2; }`
  return { ...state, saveVersion: SAVE_VERSION };
}

export function deserializeGameState(json: string): GameState | null {
  try {
    const parsed: unknown = JSON.parse(json, reviver);
    if (!isPlausibleGameState(parsed)) return null;
    return migrate(parsed);
  } catch {
    return null;
  }
}

/**
 * Saves the current state, keeping the previous save as a backup first.
 * If a write is interrupted (app killed mid-save) the backup preserves the
 * last known-good state instead of the player losing everything.
 */
export async function saveGame(state: GameState, adapter: StorageAdapter = localStorageAdapter): Promise<void> {
  const json = serializeGameState(state);
  const previous = await adapter.getItem(SAVE_KEY);
  if (previous) await adapter.setItem(BACKUP_SAVE_KEY, previous);
  await adapter.setItem(SAVE_KEY, json);
}

/** Loads the current save, falling back to the backup slot if the primary save is missing or corrupted. */
export async function loadGame(adapter: StorageAdapter = localStorageAdapter): Promise<GameState | null> {
  const primaryJson = await adapter.getItem(SAVE_KEY);
  if (primaryJson) {
    const state = deserializeGameState(primaryJson);
    if (state) return state;
  }
  const backupJson = await adapter.getItem(BACKUP_SAVE_KEY);
  if (backupJson) {
    const state = deserializeGameState(backupJson);
    if (state) return state;
  }
  return null;
}
