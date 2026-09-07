import { Decimal, D } from './bignum';
import { GasId, GAS_LIST } from './gases';
import { ResourceId } from './resources';
import { GasTotals } from './gameState';
import { PRESTIGE, PRESTIGE_CURRENCY_NAME, PRESTIGE_CURRENCY_SHORT } from './constants';

export { PRESTIGE_CURRENCY_NAME, PRESTIGE_CURRENCY_SHORT };

export interface PrestigeUpgradeEffect {
  /** Multiplies all resource + gas production, stacking per level. */
  globalProductionMultiplier?: number;
  /** Multiplies Research income specifically, stacking per level. */
  researchMultiplier?: number;
  /** Multiplies production of one specific gas, stacking per level. */
  gasProductionMultiplier?: { gas: GasId; multiplier: number };
  /** Multiplies ALL greenhouse-gas production at once (distinct from resource currencies). */
  allGasProductionMultiplier?: number;
  /** Fractional discount applied to every technology's cost (0.1 = 10% cheaper), stacking additively across levels, capped at 0.9. */
  techCostDiscount?: number;
  /** Multiplies the base offline-progress cap, stacking per level. */
  offlineCapMultiplier?: number;
  /** Multiplies the amount of resources earned per manual tap, stacking per level. */
  tapPowerMultiplier?: number;
  /** Technologies auto-granted (owned = 1) at the start of every future run. */
  grantsStartingTechIds?: string[];
  /** Flat resources granted at the start of every future run. */
  startingResources?: Partial<Record<ResourceId, number>>;
  /** Unlocks the "Buy Max" auto-affordability helper from run start (otherwise a mid-run milestone). */
  unlocksAutoBuyMax?: boolean;
}

export interface PrestigeUpgrade {
  id: string;
  name: string;
  description: string;
  icon: string;
  baseCost: number; // in Earth Points
  costGrowth: number; // per level; 1 for one-time upgrades
  maxLevel: number;
  effect: PrestigeUpgradeEffect;
}

export const PRESTIGE_UPGRADES: PrestigeUpgrade[] = [
  {
    id: 'atmospheric_momentum',
    name: 'Atmospheric Momentum',
    description: 'Each civilization remembers a little more than the last. +10% production per level.',
    icon: '🌬️',
    baseCost: 100,
    costGrowth: 2.2,
    maxLevel: Infinity,
    effect: { globalProductionMultiplier: 1.1 },
  },
  {
    id: 'industrial_memory',
    name: 'Industrial Memory',
    description: 'Start every future Earth already knowing the Steam Engine.',
    icon: '⚙️',
    baseCost: 1_000,
    costGrowth: 1,
    maxLevel: 1,
    effect: { grantsStartingTechIds: ['steam_engine'] },
  },
  {
    id: 'rapid_research',
    name: 'Rapid Research',
    description: 'Institutional knowledge survives the reset. +50% Research per level.',
    icon: '📚',
    baseCost: 10_000,
    costGrowth: 3,
    maxLevel: 5,
    effect: { researchMultiplier: 1.5 },
  },
  {
    id: 'civilizational_acceleration',
    name: 'Civilizational Acceleration',
    description: 'Blueprints from past civilizations make every technology 20% cheaper per level.',
    icon: '📐',
    baseCost: 1_000_000,
    costGrowth: 4,
    maxLevel: 4,
    effect: { techCostDiscount: 0.2 },
  },
  {
    id: 'anthropocene_mastery',
    name: 'Anthropocene Mastery',
    description: 'You\'ve done this before. ×10 all greenhouse-gas production.',
    icon: '🌋',
    baseCost: 1_000_000_000,
    costGrowth: 1,
    maxLevel: 1,
    effect: { allGasProductionMultiplier: 10 },
  },
  {
    id: 'tap_conditioning',
    name: 'Tap Conditioning',
    description: 'Muscle memory from a thousand collapsed civilizations. +50% manual tap output per level.',
    icon: '👆',
    baseCost: 50,
    costGrowth: 1.8,
    maxLevel: Infinity,
    effect: { tapPowerMultiplier: 1.5 },
  },
  {
    id: 'extended_endurance',
    name: 'Extended Endurance',
    description: 'Your civilizations keep running longer without you watching. +100% offline progress cap per level.',
    icon: '⏳',
    baseCost: 5_000,
    costGrowth: 3.5,
    maxLevel: 3,
    effect: { offlineCapMultiplier: 2 },
  },
  {
    id: 'head_start',
    name: 'Head Start',
    description: 'Begin each Earth with a stockpile of Energy, skipping the slowest opening minutes.',
    icon: '🚩',
    baseCost: 20_000,
    costGrowth: 1,
    maxLevel: 1,
    effect: { startingResources: { energy: 1000 } },
  },
  {
    id: 'automated_industry',
    name: 'Automated Industry',
    description: 'Unlock the Buy Max helper from the very start of the run.',
    icon: '🤖',
    baseCost: 250_000,
    costGrowth: 1,
    maxLevel: 1,
    effect: { unlocksAutoBuyMax: true },
  },
];

export const PRESTIGE_UPGRADE_BY_ID: Record<string, PrestigeUpgrade> = Object.fromEntries(
  PRESTIGE_UPGRADES.map((u) => [u.id, u]),
);

export function prestigeUpgradeCost(upgrade: PrestigeUpgrade, currentLevel: number): Decimal {
  return D(upgrade.baseCost).mul(D(upgrade.costGrowth).pow(currentLevel));
}

export interface PrestigeMultipliers {
  global: number;
  research: number;
  allGas: number;
  perGas: Partial<Record<GasId, number>>;
  techCostDiscount: number;
  offlineCapMultiplier: number;
  tapPowerMultiplier: number;
  startingTechIds: string[];
  startingResources: Partial<Record<ResourceId, number>>;
  unlocksAutoBuyMax: boolean;
}

function applyEffect(result: PrestigeMultipliers, e: PrestigeUpgradeEffect, level: number): void {
  if (e.globalProductionMultiplier) result.global *= Math.pow(e.globalProductionMultiplier, level);
  if (e.researchMultiplier) result.research *= Math.pow(e.researchMultiplier, level);
  if (e.allGasProductionMultiplier) result.allGas *= Math.pow(e.allGasProductionMultiplier, level);
  if (e.gasProductionMultiplier) {
    const { gas, multiplier } = e.gasProductionMultiplier;
    result.perGas[gas] = (result.perGas[gas] ?? 1) * Math.pow(multiplier, level);
  }
  if (e.techCostDiscount) result.techCostDiscount = Math.min(0.9, result.techCostDiscount + e.techCostDiscount * level);
  if (e.offlineCapMultiplier) result.offlineCapMultiplier *= Math.pow(e.offlineCapMultiplier, level);
  if (e.tapPowerMultiplier) result.tapPowerMultiplier *= Math.pow(e.tapPowerMultiplier, level);
  if (e.grantsStartingTechIds) result.startingTechIds.push(...e.grantsStartingTechIds);
  if (e.startingResources) {
    for (const [res, amount] of Object.entries(e.startingResources)) {
      result.startingResources[res as ResourceId] = (result.startingResources[res as ResourceId] ?? 0) + (amount ?? 0);
    }
  }
  if (e.unlocksAutoBuyMax) result.unlocksAutoBuyMax = true;
}

/**
 * Aggregates every owned prestige upgrade (at every level) plus any
 * permanent challenge rewards into one multiplier bundle. Challenge
 * rewards use the same effect shape as prestige upgrades and are folded in
 * at a flat level of 1 (they don't stack with themselves — a challenge is
 * either completed once or not).
 */
export function computePrestigeMultipliers(
  upgradesOwned: Record<string, number>,
  challengeRewardEffects: PrestigeUpgradeEffect[] = [],
): PrestigeMultipliers {
  const result: PrestigeMultipliers = {
    global: 1,
    research: 1,
    allGas: 1,
    perGas: {},
    techCostDiscount: 0,
    offlineCapMultiplier: 1,
    tapPowerMultiplier: 1,
    startingTechIds: [],
    startingResources: {},
    unlocksAutoBuyMax: false,
  };

  for (const upgrade of PRESTIGE_UPGRADES) {
    const level = upgradesOwned[upgrade.id] ?? 0;
    if (level <= 0) continue;
    applyEffect(result, upgrade.effect, level);
  }

  for (const rewardEffect of challengeRewardEffects) {
    applyEffect(result, rewardEffect, 1);
  }

  return result;
}

export interface PrestigeGainParams {
  totalGasProducedKg: GasTotals;
  peakForcingWm2: number;
  civLevel: number;
  runDurationSeconds: number;
}

function sumAllGasKg(totals: GasTotals): Decimal {
  return GAS_LIST.reduce((sum, gas) => sum.add(totals[gas.id]), Decimal.ZERO);
}

/**
 * Converts one run's outcome into Earth Points. Diminishing returns come
 * from the sub-1 exponent on total emitted mass; the forcing, civilization
 * level, and duration terms then scale that base up, so late-run
 * "buttoning up" a strong civilization matters more than raw mass alone.
 * See constants.ts `PRESTIGE` for every tunable in this formula.
 */
export function calculatePrestigeGain(params: PrestigeGainParams): Decimal {
  const totalGasKg = sumAllGasKg(params.totalGasProducedKg);
  const gasScore = totalGasKg.div(PRESTIGE.baseDivisor).clampMin(0);
  const gasComponent = gasScore.pow(PRESTIGE.exponent);

  const forcingMultiplier = 1 + PRESTIGE.forcingWeight * Math.max(0, params.peakForcingWm2);
  const civMultiplier = 1 + PRESTIGE.civLevelWeight * Math.max(0, params.civLevel);
  const durationFactor = clamp(
    0.5 + 0.5 * Math.sqrt(Math.max(0, params.runDurationSeconds) / PRESTIGE.durationBonusHalfLifeSeconds),
    0.5,
    1.5,
  );

  return gasComponent.mul(forcingMultiplier).mul(civMultiplier).mul(durationFactor);
}

function clamp(x: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, x));
}
