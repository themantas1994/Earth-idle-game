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
  /** Technologies auto-granted (owned = 1) at the start of every future run. */
  grantsStartingTechIds?: string[];
  /** Extra owned units of a generator granted at the start of every future run, per level. */
  startingGenerators?: Record<string, number>;
  /** Flat resources granted at the start of every future run. */
  startingResources?: Partial<Record<ResourceId, number>>;
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
    id: 'institutional_memory',
    name: 'Institutional Memory',
    description: 'Every civilization inherits the last one\'s blueprints. Every building you own produces +40% more, per level.',
    icon: '🏛️',
    baseCost: 150_000,
    costGrowth: 4,
    maxLevel: 6,
    effect: { globalProductionMultiplier: 1.4 },
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
    baseCost: 25_000_000,
    costGrowth: 1,
    maxLevel: 1,
    effect: { allGasProductionMultiplier: 10 },
  },
  {
    id: 'eternal_flame',
    name: 'Eternal Flame',
    description: 'An ember carried between worlds. Every future Earth starts with its fires already burning: +2 Natural Fire per level.',
    icon: '🔥',
    baseCost: 50,
    costGrowth: 1.8,
    maxLevel: 10,
    effect: { startingGenerators: { natural_fire: 2 } },
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
    description: 'Robots keep the lines running while you sleep. +100% offline progress cap, and every future Earth starts with 25 Natural Fires already lit.',
    icon: '🤖',
    baseCost: 200_000,
    costGrowth: 1,
    maxLevel: 1,
    effect: { offlineCapMultiplier: 2, startingGenerators: { natural_fire: 25 } },
  },
  {
    id: 'deep_foundations',
    name: 'Deep Foundations',
    description: 'Start every future Earth with a Research head start, so the tree opens immediately instead of after the first hour.',
    icon: '🧱',
    baseCost: 40_000,
    costGrowth: 1,
    maxLevel: 1,
    effect: { startingResources: { research: 5_000, energy: 25_000 } },
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
  startingTechIds: string[];
  startingGenerators: Record<string, number>;
  startingResources: Partial<Record<ResourceId, number>>;
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
  if (e.grantsStartingTechIds) result.startingTechIds.push(...e.grantsStartingTechIds);
  if (e.startingGenerators) {
    for (const [techId, count] of Object.entries(e.startingGenerators)) {
      result.startingGenerators[techId] = (result.startingGenerators[techId] ?? 0) + count * level;
    }
  }
  if (e.startingResources) {
    for (const [res, amount] of Object.entries(e.startingResources)) {
      result.startingResources[res as ResourceId] = (result.startingResources[res as ResourceId] ?? 0) + (amount ?? 0);
    }
  }
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
    startingTechIds: [],
    startingGenerators: {},
    startingResources: {},
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
 * How much faster than the reference pace this run was, as a multiplier on
 * the payout. This is the term that makes each Earth worth more than the
 * last: prestige upgrades buy speed, and speed is what is scored.
 *
 * A run of zero (or nonsensical) length is treated as the reference pace
 * rather than as infinitely fast, so a corrupted clock cannot mint points.
 */
export function speedMultiplier(runDurationSeconds: number): number {
  if (!Number.isFinite(runDurationSeconds) || runDurationSeconds <= 0) return 1;
  const ratio = PRESTIGE.referenceRunSeconds / runDurationSeconds;
  const scaled = Math.pow(ratio, PRESTIGE.speedExponent);
  return Math.min(PRESTIGE.maxSpeedMultiplier, Math.max(PRESTIGE.minSpeedMultiplier, scaled));
}

/**
 * Converts one run's outcome into Earth Points: how much gas the civilization
 * produced, scaled by how hard it pushed the atmosphere, how far up the tech
 * tree it got, and how quickly it managed all three. Every tunable lives in
 * `constants.ts > PRESTIGE`, which also explains why each term is shaped the
 * way it is.
 */
export function calculatePrestigeGain(params: PrestigeGainParams): Decimal {
  const totalGasKg = sumAllGasKg(params.totalGasProducedKg);
  const gasScore = totalGasKg.div(PRESTIGE.baseDivisor).clampMin(0);
  const gasComponent = gasScore.pow(PRESTIGE.exponent).mul(PRESTIGE.gasWeight);

  const forcingMultiplier = 1 + PRESTIGE.forcingWeight * Math.sqrt(Math.max(0, params.peakForcingWm2));
  const civMultiplier = 1 + PRESTIGE.civLevelWeight * Math.sqrt(Math.max(0, params.civLevel));

  return gasComponent
    .mul(forcingMultiplier)
    .mul(civMultiplier)
    .mul(speedMultiplier(params.runDurationSeconds));
}
