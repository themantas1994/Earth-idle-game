import { Decimal } from './bignum';
import { GasId, GAS_LIST } from './gases';
import { ResourceId, RESOURCE_LIST } from './resources';
import { AtmosphereState, createInitialAtmosphere } from './climate';
import { ForcingBreakdown } from './climate';
import { HabitabilityResult } from './habitability';

export type GasTotals = Record<GasId, Decimal>;
export type ResourceTotals = Record<ResourceId, Decimal>;

function zeroGasTotals(): GasTotals {
  return Object.fromEntries(GAS_LIST.map((g) => [g.id, Decimal.ZERO])) as GasTotals;
}

function zeroResourceTotals(): ResourceTotals {
  return Object.fromEntries(RESOURCE_LIST.map((r) => [r.id, Decimal.ZERO])) as ResourceTotals;
}

/** Stats that reset to zero at the start of every run (used for the prestige/collapse summary). */
export interface RunStats {
  startedAt: number;
  totalGasProducedKg: GasTotals;
  peakForcingWm2: number;
  peakTemperatureC: number;
  peakCo2Ppm: number;
  peakGasProductionRateKgPerS: Decimal;
}

/** Stats that persist forever across resets, shown on the Statistics screen. */
export interface LifetimeStats {
  totalPlayTimeSeconds: number;
  totalResets: number;
  totalGasProducedKg: GasTotals;
  highestTemperatureC: number;
  highestCo2Ppm: number;
  fastestResetSeconds: number | null;
  longestRunSeconds: number;
  totalTaps: number;
  totalEarthPointsEarned: Decimal;
}

export interface Settings {
  numberFormat: 'compact' | 'scientific' | 'engineering' | 'full';
  soundEnabled: boolean;
  musicEnabled: boolean;
  vibrationEnabled: boolean;
  reducedAnimations: boolean;
  darkMode: 'system' | 'light' | 'dark';
  confirmReset: boolean;
  offlineProgressEnabled: boolean;
}

export const DEFAULT_SETTINGS: Settings = {
  numberFormat: 'compact',
  soundEnabled: true,
  musicEnabled: true,
  vibrationEnabled: true,
  reducedAnimations: false,
  darkMode: 'system',
  confirmReset: true,
  offlineProgressEnabled: true,
};

export interface ActiveEvent {
  id: string;
  eventDefId: string;
  startedAt: number;
  endsAt: number;
}

export interface GameState {
  saveVersion: number;
  runNumber: number; // 0 = "EARTH", 1 = "EARTH 1", 2 = "EARTH 2", ...
  runStartedAt: number;
  lastTickAt: number;
  createdAt: number;

  resources: ResourceTotals;
  atmosphere: AtmosphereState;
  seaLevelRiseMeters: number;
  previousTemperatureAnomalyC: number;

  techOwned: Record<string, number>;

  // Cached derived values, recomputed every tick, kept on state for cheap UI reads.
  temperatureAnomalyC: number;
  forcing: ForcingBreakdown;
  habitability: HabitabilityResult;
  oceanPh: number;

  runStats: RunStats;
  lifetimeStats: LifetimeStats;

  prestige: {
    earthPoints: Decimal;
    upgradesOwned: Record<string, number>;
  };

  achievementsUnlocked: Record<string, boolean>;
  challenges: {
    activeId: string | null;
    completed: Record<string, boolean>;
  };

  activeEvents: ActiveEvent[];

  settings: Settings;

  tutorial: {
    step: number;
    completed: boolean;
    skipped: boolean;
  };

  collapsed: boolean;
}

export const SAVE_VERSION = 1;

/**
 * Technologies every run starts with already "owned". Natural Fire predates
 * any deliberate human action (wildfires exist on their own) and every
 * later technology's cost is denominated in resources produced by
 * technology — so if it were purchasable like everything else, it would be
 * an unaffordable dead end at the very start of the game. Modeling it as a
 * starting condition rather than a purchase avoids that soft-lock.
 */
export const STARTING_TECH_IDS = ['natural_fire'];

function initialTechOwned(): Record<string, number> {
  return Object.fromEntries(STARTING_TECH_IDS.map((id) => [id, 1]));
}

export function createInitialRunStats(now: number): RunStats {
  return {
    startedAt: now,
    totalGasProducedKg: zeroGasTotals(),
    peakForcingWm2: 0,
    peakTemperatureC: 0,
    peakCo2Ppm: 280,
    peakGasProductionRateKgPerS: Decimal.ZERO,
  };
}

export function createInitialLifetimeStats(): LifetimeStats {
  return {
    totalPlayTimeSeconds: 0,
    totalResets: 0,
    totalGasProducedKg: zeroGasTotals(),
    highestTemperatureC: 0,
    highestCo2Ppm: 280,
    fastestResetSeconds: null,
    longestRunSeconds: 0,
    totalTaps: 0,
    totalEarthPointsEarned: Decimal.ZERO,
  };
}

export function createNewGame(now: number = Date.now()): GameState {
  return {
    saveVersion: SAVE_VERSION,
    runNumber: 0,
    runStartedAt: now,
    lastTickAt: now,
    createdAt: now,

    resources: zeroResourceTotals(),
    atmosphere: createInitialAtmosphere(),
    seaLevelRiseMeters: 0,
    previousTemperatureAnomalyC: 0,

    techOwned: initialTechOwned(),

    temperatureAnomalyC: 0,
    forcing: { perGas: Object.fromEntries(GAS_LIST.map((g) => [g.id, 0])) as Record<GasId, number>, total: 0 },
    habitability: {
      fraction: 1,
      factors: { temperature: 1, oceanAcidity: 1, seaLevel: 1, agriculture: 1, biodiversity: 1 },
    },
    oceanPh: 8.1,

    runStats: createInitialRunStats(now),
    lifetimeStats: createInitialLifetimeStats(),

    prestige: {
      earthPoints: Decimal.ZERO,
      upgradesOwned: {},
    },

    achievementsUnlocked: {},
    challenges: {
      activeId: null,
      completed: {},
    },

    activeEvents: [],

    settings: { ...DEFAULT_SETTINGS },

    tutorial: {
      step: 0,
      completed: false,
      skipped: false,
    },

    collapsed: false,
  };
}

/** Starts a fresh run (post-reset), preserving prestige, achievements, challenges, lifetime stats, and settings. */
export function startNewRun(previous: GameState, now: number = Date.now()): GameState {
  return {
    ...previous,
    runNumber: previous.runNumber + 1,
    runStartedAt: now,
    lastTickAt: now,

    resources: zeroResourceTotals(),
    atmosphere: createInitialAtmosphere(),
    seaLevelRiseMeters: 0,
    previousTemperatureAnomalyC: 0,

    techOwned: initialTechOwned(),

    temperatureAnomalyC: 0,
    forcing: { perGas: Object.fromEntries(GAS_LIST.map((g) => [g.id, 0])) as Record<GasId, number>, total: 0 },
    habitability: {
      fraction: 1,
      factors: { temperature: 1, oceanAcidity: 1, seaLevel: 1, agriculture: 1, biodiversity: 1 },
    },
    oceanPh: 8.1,

    runStats: createInitialRunStats(now),

    activeEvents: [],
    collapsed: false,
  };
}
