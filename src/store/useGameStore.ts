import { create } from 'zustand';
import { Decimal, D } from '../engine/bignum';
import { GameState, createNewGame, startNewRun, RunStats } from '../engine/gameState';
import { simulateStep, computeCivLevel, computeEffectiveMultipliers, EffectiveMultipliers, ProductionRates, computeProductionRates } from '../engine/simulation';
import { computePrestigeMultipliers, PrestigeMultipliers, calculatePrestigeGain, PRESTIGE_UPGRADE_BY_ID, prestigeUpgradeCost } from '../engine/prestige';
import { computeOfflineProgress, OfflineProgressResult } from '../engine/offline';
import { applyManualTap, purchaseTechnology, grantTechnology } from '../engine/economy';
import { checkAchievements } from '../engine/achievements';
import {
  CHALLENGE_BY_ID,
  disabledTechIdsForChallenge,
  isChallengeRestrictionViolated,
  isChallengeGoalMet,
  computeChallengeRewardEffects,
} from '../engine/challenges';
import { rollRandomEvent, computeActiveEventMultipliers, removeExpiredEvents, applyInstantGasBurst, RANDOM_EVENT_BY_ID } from '../engine/events';
import { saveGame, loadGame, StorageAdapter, localStorageAdapter } from '../engine/save';
import { isNativePlatform, preferencesAdapter, onAppStateChange, hapticTap } from '../platform/native';
import { SIMULATION } from '../engine/constants';
import { Settings } from '../engine/gameState';

const TICK_INTERVAL_MS = SIMULATION.tickIntervalMs;
const AUTOSAVE_INTERVAL_MS = SIMULATION.autosaveIntervalMs;
/** A gap between ticks longer than this (tab backgrounded, phone locked, app closed) is treated as an offline absence. */
const OFFLINE_GAP_THRESHOLD_MS = 20_000;

export interface EarthCollapseSummary {
  runNumber: number;
  durationSeconds: number;
  maxCo2Ppm: number;
  maxTemperatureC: number;
  maxGasProductionRateKgPerS: Decimal;
  earthPointsEarned: Decimal;
}

export interface DerivedState {
  civLevel: number;
  prestigeMultipliers: PrestigeMultipliers;
  effectiveMultipliers: EffectiveMultipliers;
  productionRates: ProductionRates;
  disabledTechIds: Set<string>;
}

export interface GameStore {
  state: GameState;
  derived: DerivedState;
  loaded: boolean;
  offlineSummary: OfflineProgressResult | null;
  collapseSummary: EarthCollapseSummary | null;
  newlyUnlockedAchievements: string[];
  activeEventToast: string | null;

  init: () => Promise<void>;
  tick: (nowMs: number) => void;
  tap: () => void;
  buyTechnology: (techId: string, quantity: number | 'max') => void;
  buyPrestigeUpgrade: (id: string) => void;
  confirmResetEarth: () => void;
  requestResetEarth: () => boolean; // returns whether Earth is actually collapsed/eligible
  dismissOfflineSummary: () => void;
  dismissCollapseSummary: () => void;
  clearNewAchievements: () => void;
  dismissEventToast: () => void;
  startChallenge: (id: string) => void;
  abandonChallenge: () => void;
  updateSettings: (partial: Partial<Settings>) => void;
  advanceTutorial: () => void;
  skipTutorial: () => void;
  saveNow: () => void;
}

function computeDerived(state: GameState): DerivedState {
  const challengeRewards = computeChallengeRewardEffects(state.challenges.completed);
  const prestigeMultipliers = computePrestigeMultipliers(state.prestige.upgradesOwned, challengeRewards);
  const civLevel = computeCivLevel(state.techOwned);
  const eventMultipliers = computeActiveEventMultipliers(state.activeEvents, Date.now());
  const effectiveMultipliers = computeEffectiveMultipliers(state.techOwned, prestigeMultipliers, [eventMultipliers]);
  const productionRates = computeProductionRates(state.techOwned, effectiveMultipliers);
  const activeChallenge = state.challenges.activeId ? CHALLENGE_BY_ID[state.challenges.activeId] : null;
  const disabledTechIds = disabledTechIdsForChallenge(activeChallenge ?? null);

  return { civLevel, prestigeMultipliers, effectiveMultipliers, productionRates, disabledTechIds };
}

let tickHandle: ReturnType<typeof setInterval> | null = null;
let autosaveHandle: ReturnType<typeof setInterval> | null = null;
let nativeListenerDisposers: Array<() => void> = [];

/**
 * The packaged Android app persists to Capacitor `Preferences`
 * (SharedPreferences), which survives a WebView data clear; the browser build
 * keeps using `localStorage`. Chosen once, here, so no engine code needs to
 * know which platform it is running on.
 */
const storage: StorageAdapter = isNativePlatform() ? preferencesAdapter : localStorageAdapter;

export const useGameStore = create<GameStore>((set, get) => ({
  state: createNewGame(),
  derived: computeDerived(createNewGame()),
  loaded: false,
  offlineSummary: null,
  collapseSummary: null,
  newlyUnlockedAchievements: [],
  activeEventToast: null,

  init: async () => {
    const loadedState = await loadGame(storage);
    const now = Date.now();
    const baseState = loadedState ?? createNewGame(now);

    let workingState = baseState;
    let offlineSummary: OfflineProgressResult | null = null;

    if (loadedState) {
      const prestigeMultipliers = computePrestigeMultipliers(
        loadedState.prestige.upgradesOwned,
        computeChallengeRewardEffects(loadedState.challenges.completed),
      );
      const result = computeOfflineProgress(loadedState, now, prestigeMultipliers, loadedState.settings.offlineProgressEnabled);
      workingState = result.state;
      if (result.simulatedSeconds > 5) offlineSummary = result;
    } else {
      workingState = { ...workingState, lastTickAt: now };
    }

    set({ state: workingState, derived: computeDerived(workingState), loaded: true, offlineSummary });

    if (tickHandle) clearInterval(tickHandle);
    tickHandle = setInterval(() => get().tick(Date.now()), TICK_INTERVAL_MS);

    if (autosaveHandle) clearInterval(autosaveHandle);
    autosaveHandle = setInterval(() => get().saveNow(), AUTOSAVE_INTERVAL_MS);

    // `init` is idempotent (React StrictMode double-invokes effects in dev):
    // drop any listeners a previous call registered before adding new ones,
    // otherwise every remount would stack another copy of each handler.
    for (const dispose of nativeListenerDisposers) dispose();
    nativeListenerDisposers = [];

    if (typeof document !== 'undefined') {
      const onVisibility = () => {
        if (document.visibilityState === 'hidden') get().saveNow();
        else get().tick(Date.now());
      };
      document.addEventListener('visibilitychange', onVisibility);
      nativeListenerDisposers.push(() => document.removeEventListener('visibilitychange', onVisibility));
    }
    if (typeof window !== 'undefined') {
      const onUnload = () => get().saveNow();
      window.addEventListener('beforeunload', onUnload);
      nativeListenerDisposers.push(() => window.removeEventListener('beforeunload', onUnload));
    }

    // On Android `beforeunload` is not guaranteed to run when the OS kills a
    // backgrounded app, so the authoritative save point is the app going
    // inactive; coming back foreground re-ticks immediately to collect offline
    // progress rather than waiting for the next interval.
    void onAppStateChange((isActive) => {
      if (isActive) get().tick(Date.now());
      else get().saveNow();
    }).then((dispose) => nativeListenerDisposers.push(dispose));
  },

  tick: (nowMs) => {
    const { state, derived } = get();
    const dtSeconds = (nowMs - state.lastTickAt) / 1000;
    if (dtSeconds <= 0) return;

    if (nowMs - state.lastTickAt > OFFLINE_GAP_THRESHOLD_MS) {
      const result = computeOfflineProgress(state, nowMs, derived.prestigeMultipliers, state.settings.offlineProgressEnabled);
      const withEvents = { ...result.state, activeEvents: removeExpiredEvents(result.state.activeEvents, nowMs) };
      applyPostStepBookkeeping(set, get, state, withEvents, dtSeconds);
      if (result.simulatedSeconds > 5) set({ offlineSummary: result });
      return;
    }

    const eventMultipliers = computeActiveEventMultipliers(state.activeEvents, nowMs);
    const { state: stepped } = simulateStep(state, dtSeconds, derived.prestigeMultipliers, [eventMultipliers]);
    let nextState = { ...stepped, lastTickAt: nowMs, activeEvents: removeExpiredEvents(stepped.activeEvents, nowMs) };

    // Occasionally roll for a new random event (skip while a challenge or the tutorial is active, to keep those focused).
    if (!nextState.challenges.activeId && nextState.activeEvents.length < 2) {
      const rollChance = dtSeconds * 0.003; // ~ once every ~5-6 minutes on average, before eligibility filtering
      if (Math.random() < rollChance) {
        const civLevel = computeCivLevel(nextState.techOwned);
        const activeIds = new Set(nextState.activeEvents.map((e) => e.eventDefId));
        const picked = rollRandomEvent(civLevel, activeIds, Math.random());
        if (picked) {
          const startedAt = nowMs;
          nextState = {
            ...nextState,
            atmosphere: applyInstantGasBurst(nextState.atmosphere, picked),
            activeEvents: [...nextState.activeEvents, { id: `${picked.id}-${startedAt}`, eventDefId: picked.id, startedAt, endsAt: startedAt + picked.durationSeconds * 1000 }],
          };
          set({ activeEventToast: picked.id });
        }
      }
    }

    applyPostStepBookkeeping(set, get, state, nextState, dtSeconds);
  },

  tap: () => {
    const { state, derived } = get();
    if (state.settings.vibrationEnabled) hapticTap();
    const nextState = applyManualTap(state, derived.prestigeMultipliers);
    finalizeStateUpdate(set, get, state, nextState);
  },

  buyTechnology: (techId, quantity) => {
    const { state, derived } = get();
    const q = quantity === 'max' ? Number.MAX_SAFE_INTEGER : quantity;
    const result = purchaseTechnology(state, techId, q, derived.prestigeMultipliers, derived.disabledTechIds);
    if (!result.success) return;
    finalizeStateUpdate(set, get, state, result.state);
    get().saveNow();
  },

  buyPrestigeUpgrade: (id) => {
    const { state } = get();
    const upgrade = PRESTIGE_UPGRADE_BY_ID[id];
    if (!upgrade) return;
    const level = state.prestige.upgradesOwned[id] ?? 0;
    if (level >= upgrade.maxLevel) return;
    const cost = prestigeUpgradeCost(upgrade, level);
    if (state.prestige.earthPoints.lt(cost)) return;

    const nextState: GameState = {
      ...state,
      prestige: {
        ...state.prestige,
        earthPoints: state.prestige.earthPoints.sub(cost),
        upgradesOwned: { ...state.prestige.upgradesOwned, [id]: level + 1 },
      },
    };
    finalizeStateUpdate(set, get, state, nextState);
    get().saveNow();
  },

  requestResetEarth: () => {
    return get().state.collapsed;
  },

  confirmResetEarth: () => {
    const { state, derived } = get();
    if (!state.collapsed) return;

    const runDurationSeconds = (Date.now() - state.runStartedAt) / 1000;
    const earned = calculatePrestigeGain({
      totalGasProducedKg: state.runStats.totalGasProducedKg,
      peakForcingWm2: state.runStats.peakForcingWm2,
      civLevel: derived.civLevel,
      runDurationSeconds,
    });

    const collapseSummary: EarthCollapseSummary = {
      runNumber: state.runNumber,
      durationSeconds: runDurationSeconds,
      maxCo2Ppm: state.runStats.peakCo2Ppm,
      maxTemperatureC: state.runStats.peakTemperatureC,
      maxGasProductionRateKgPerS: state.runStats.peakGasProductionRateKgPerS,
      earthPointsEarned: earned,
    };

    const previousRunDurationSeconds = state.lifetimeStats.longestRunSeconds > 0 ? state.lifetimeStats.longestRunSeconds : null;

    let fresh = startNewRun(state, Date.now());
    fresh = {
      ...fresh,
      prestige: { ...fresh.prestige, earthPoints: fresh.prestige.earthPoints.add(earned) },
      lifetimeStats: {
        ...fresh.lifetimeStats,
        totalResets: fresh.lifetimeStats.totalResets + 1,
        fastestResetSeconds:
          fresh.lifetimeStats.fastestResetSeconds === null
            ? runDurationSeconds
            : Math.min(fresh.lifetimeStats.fastestResetSeconds, runDurationSeconds),
        longestRunSeconds: Math.max(fresh.lifetimeStats.longestRunSeconds, runDurationSeconds),
        totalEarthPointsEarned: fresh.lifetimeStats.totalEarthPointsEarned.add(earned),
      },
    };

    // Apply "starting tech" / "starting resources" prestige bonuses for the new run.
    const prestigeMultipliers = computePrestigeMultipliers(fresh.prestige.upgradesOwned, computeChallengeRewardEffects(fresh.challenges.completed));
    for (const techId of prestigeMultipliers.startingTechIds) fresh = grantTechnology(fresh, techId);
    for (const [resId, amount] of Object.entries(prestigeMultipliers.startingResources)) {
      fresh = { ...fresh, resources: { ...fresh.resources, [resId]: fresh.resources[resId as keyof typeof fresh.resources].add(amount ?? 0) } };
    }

    const achievementIds = checkAchievements(
      { state: fresh, civLevel: 0, justCollapsed: true, justReset: true, lastRunDurationSeconds: runDurationSeconds, previousRunDurationSeconds },
      fresh.achievementsUnlocked,
    );
    const achievementsUnlocked = { ...fresh.achievementsUnlocked };
    for (const id of achievementIds) achievementsUnlocked[id] = true;
    fresh = { ...fresh, achievementsUnlocked };

    set({
      state: fresh,
      derived: computeDerived(fresh),
      collapseSummary,
      newlyUnlockedAchievements: [...get().newlyUnlockedAchievements, ...achievementIds],
    });
    get().saveNow();
  },

  dismissOfflineSummary: () => set({ offlineSummary: null }),
  dismissCollapseSummary: () => set({ collapseSummary: null }),
  clearNewAchievements: () => set({ newlyUnlockedAchievements: [] }),
  dismissEventToast: () => set({ activeEventToast: null }),

  startChallenge: (id) => {
    const { state } = get();
    if (!CHALLENGE_BY_ID[id]) return;
    const fresh = startNewRun(state, Date.now());
    const nextState = { ...fresh, challenges: { ...fresh.challenges, activeId: id } };
    finalizeStateUpdate(set, get, state, nextState);
    get().saveNow();
  },

  abandonChallenge: () => {
    const { state } = get();
    const nextState = { ...state, challenges: { ...state.challenges, activeId: null } };
    finalizeStateUpdate(set, get, state, nextState);
  },

  updateSettings: (partial) => {
    const { state } = get();
    const nextState = { ...state, settings: { ...state.settings, ...partial } };
    finalizeStateUpdate(set, get, state, nextState);
    get().saveNow();
  },

  advanceTutorial: () => {
    const { state } = get();
    const nextState = { ...state, tutorial: { ...state.tutorial, step: state.tutorial.step + 1 } };
    finalizeStateUpdate(set, get, state, nextState);
  },

  skipTutorial: () => {
    const { state } = get();
    const nextState = { ...state, tutorial: { ...state.tutorial, completed: true, skipped: true } };
    finalizeStateUpdate(set, get, state, nextState);
    get().saveNow();
  },

  saveNow: () => {
    void saveGame(get().state, storage);
  },
}));

function finalizeStateUpdate(
  set: (partial: Partial<GameStore>) => void,
  _get: () => GameStore,
  _previous: GameState,
  nextState: GameState,
) {
  set({ state: nextState, derived: computeDerived(nextState) });
}

function applyPostStepBookkeeping(
  set: (partial: Partial<GameStore>) => void,
  get: () => GameStore,
  previous: GameState,
  nextState: GameState,
  _dtSeconds: number,
) {
  // Auto-fail an active challenge whose live restriction (e.g. a temperature ceiling) was just broken.
  if (nextState.challenges.activeId) {
    const challenge = CHALLENGE_BY_ID[nextState.challenges.activeId];
    if (challenge && isChallengeRestrictionViolated(challenge, nextState)) {
      nextState = { ...nextState, challenges: { ...nextState.challenges, activeId: null } };
    } else if (challenge) {
      const civLevel = computeCivLevel(nextState.techOwned);
      if (isChallengeGoalMet(challenge, nextState, civLevel)) {
        nextState = {
          ...nextState,
          challenges: {
            ...nextState.challenges,
            activeId: null,
            completed: { ...nextState.challenges.completed, [challenge.id]: true },
          },
        };
      }
    }
  }

  const civLevel = computeCivLevel(nextState.techOwned);
  const wasCollapsed = previous.collapsed;
  const justCollapsed = !wasCollapsed && nextState.collapsed;

  const achievementIds = checkAchievements(
    { state: nextState, civLevel, justCollapsed, justReset: false, lastRunDurationSeconds: null, previousRunDurationSeconds: null },
    nextState.achievementsUnlocked,
  );
  if (achievementIds.length > 0) {
    const achievementsUnlocked = { ...nextState.achievementsUnlocked };
    for (const id of achievementIds) achievementsUnlocked[id] = true;
    nextState = { ...nextState, achievementsUnlocked };
  }

  set({
    state: nextState,
    derived: computeDerived(nextState),
    ...(achievementIds.length > 0 ? { newlyUnlockedAchievements: [...get().newlyUnlockedAchievements, ...achievementIds] } : {}),
  });
}
