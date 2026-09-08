import { Decimal, D } from './bignum';
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

/**
 * Upgrades an older save's shape to the current SAVE_VERSION. Each step
 * migrates one version forward and they run in order, so a save from any
 * released version reaches the current shape by falling through the chain.
 */
function migrate(state: GameState): GameState {
  let migrated = state;

  if (migrated.saveVersion < 2) {
    // v2 removed manual tapping: Energy now comes from Natural Fire, which
    // every run owns from the start. Saves from v1 can be missing that
    // generator entirely (it used to be a zero-output unlock, and a run
    // begun before it was granted has nothing at all), which would leave the
    // player with no income and no way to earn any — so ensure it is owned.
    //
    // The "Tap Conditioning" prestige upgrade went with it. Refunding what
    // was spent on it (a geometric series at its old 1.8 growth from a base
    // of 50) is the only fair option: the player bought something the game
    // no longer contains.
    const upgradesOwned = { ...migrated.prestige.upgradesOwned };
    const tapLevels = upgradesOwned.tap_conditioning ?? 0;
    delete upgradesOwned.tap_conditioning;
    const refund = tapLevels > 0 ? D(50).mul(D(1.8).pow(tapLevels).sub(1)).div(0.8) : Decimal.ZERO;

    migrated = {
      ...migrated,
      techOwned: {
        ...migrated.techOwned,
        natural_fire: Math.max(1, migrated.techOwned.natural_fire ?? 0),
      },
      prestige: {
        ...migrated.prestige,
        earthPoints: migrated.prestige.earthPoints.add(refund),
        upgradesOwned,
      },
      lifetimeStats: {
        ...migrated.lifetimeStats,
        totalTechnologiesPurchased: migrated.lifetimeStats.totalTechnologiesPurchased ?? 0,
      },
      // v2 also added the milestone news feed.
      milestonesTriggered: migrated.milestonesTriggered ?? {},
      newsFeed: migrated.newsFeed ?? [],
      saveVersion: 2,
    };
  }

  if (migrated.saveVersion < 3) {
    // v3 rebuilt the economy. Two things in a v2 save are no longer worth what
    // they were, and both are about Earth Points rather than the run itself:
    // a run in progress only ever gets *cheaper* (the complexity surcharge is
    // gone and no price in the game rises any more), so it needs no fixing up.
    //
    // 1. The prestige payout was rescaled by roughly 10^5 — the old formula
    //    multiplied by raw civilization level, which runs into the thousands,
    //    and a single completed run paid out enough to buy the entire upgrade
    //    tree at once. Banked points are divided by the ratio between what a
    //    completed run used to pay and what one pays now, so a returning
    //    player's balance buys about what it bought before. It is a single
    //    ratio against two differently-shaped formulas, so it is an
    //    approximation — deliberately a generous one, since erring toward
    //    giving a player too much is the kinder failure.
    //
    // 2. "Institutional Memory" used to cancel the complexity surcharge, which
    //    no longer exists. Rather than delete it, it now grants +40%
    //    production per level, so levels already bought keep paying — and it
    //    is cheaper than it was, so nobody overpaid.
    const PRESTIGE_RESCALE = 250_000;
    migrated = {
      ...migrated,
      prestige: {
        ...migrated.prestige,
        earthPoints: migrated.prestige.earthPoints.div(PRESTIGE_RESCALE),
      },
      lifetimeStats: {
        ...migrated.lifetimeStats,
        totalEarthPointsEarned: migrated.lifetimeStats.totalEarthPointsEarned.div(PRESTIGE_RESCALE),
      },
      saveVersion: 3,
    };
  }

  return migrated.saveVersion === SAVE_VERSION ? migrated : { ...migrated, saveVersion: SAVE_VERSION };
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
