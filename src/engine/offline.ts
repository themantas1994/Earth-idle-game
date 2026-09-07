import { Decimal } from './bignum';
import { GAS_LIST } from './gases';
import { RESOURCE_LIST } from './resources';
import { GameState, GasTotals, ResourceTotals } from './gameState';
import { simulateStep } from './simulation';
import { PrestigeMultipliers } from './prestige';
import { SIMULATION } from './constants';

export interface OfflineProgressSummary {
  gasGeneratedKg: GasTotals;
  resourcesGained: ResourceTotals;
  temperatureBeforeC: number;
  temperatureAfterC: number;
}

export interface OfflineProgressResult {
  awaySeconds: number;
  simulatedSeconds: number;
  cappedByLimit: boolean;
  offlineCapSeconds: number;
  state: GameState;
  summary: OfflineProgressSummary;
}

export function offlineCapSeconds(prestige: PrestigeMultipliers): number {
  return SIMULATION.baseOfflineCapSeconds * prestige.offlineCapMultiplier;
}

/**
 * Fast-forwards `state` from its last simulated tick up to `nowMs`. Because
 * `simulateStep`'s gas-concentration and resource math is step-size
 * independent (see simulation.test.ts), the whole offline gap can be
 * collapsed into a single step rather than looping — the result is
 * identical to having played it out live, minus any manual taps or
 * purchases you weren't there to make.
 */
export function computeOfflineProgress(
  state: GameState,
  nowMs: number,
  prestige: PrestigeMultipliers,
  offlineProgressEnabled: boolean,
): OfflineProgressResult {
  const awaySeconds = Math.max(0, (nowMs - state.lastTickAt) / 1000);
  const cap = offlineCapSeconds(prestige);

  if (!offlineProgressEnabled || awaySeconds <= 0) {
    return {
      awaySeconds,
      simulatedSeconds: 0,
      cappedByLimit: awaySeconds > cap,
      offlineCapSeconds: cap,
      state: { ...state, lastTickAt: nowMs },
      summary: emptySummary(state.temperatureAnomalyC),
    };
  }

  const simulatedSeconds = Math.min(awaySeconds, cap);
  const { state: nextState } = simulateStep(state, simulatedSeconds, prestige);
  const finalState = { ...nextState, lastTickAt: nowMs };

  const gasGeneratedKg = Object.fromEntries(
    GAS_LIST.map((g) => [g.id, nextState.runStats.totalGasProducedKg[g.id].sub(state.runStats.totalGasProducedKg[g.id])]),
  ) as GasTotals;
  const resourcesGained = Object.fromEntries(
    RESOURCE_LIST.map((r) => [r.id, nextState.resources[r.id].sub(state.resources[r.id])]),
  ) as ResourceTotals;

  return {
    awaySeconds,
    simulatedSeconds,
    cappedByLimit: awaySeconds > cap,
    offlineCapSeconds: cap,
    state: finalState,
    summary: {
      gasGeneratedKg,
      resourcesGained,
      temperatureBeforeC: state.temperatureAnomalyC,
      temperatureAfterC: nextState.temperatureAnomalyC,
    },
  };
}

function emptySummary(tempC: number): OfflineProgressSummary {
  return {
    gasGeneratedKg: Object.fromEntries(GAS_LIST.map((g) => [g.id, Decimal.ZERO])) as GasTotals,
    resourcesGained: Object.fromEntries(RESOURCE_LIST.map((r) => [r.id, Decimal.ZERO])) as ResourceTotals,
    temperatureBeforeC: tempC,
    temperatureAfterC: tempC,
  };
}
