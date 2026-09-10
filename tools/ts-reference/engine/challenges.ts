import { GameState } from './gameState';
import { ALL_TECHNOLOGIES, TECH_BY_ID, Technology } from './technologies';
import { GasId } from './gases';
import { PrestigeUpgradeEffect } from './prestige';

export interface ChallengeRestriction {
  /** Technologies that cannot be purchased while this challenge is active. */
  disabledTechIds?: string[];
  /** Technologies may not exceed this tier (e.g. "Primitive": stop at the Iron Age). */
  maxTechTier?: number;
  /** The run fails (restriction violated) if temperature ever exceeds this. */
  maxTemperatureC?: number;
  /** Only technologies producing exclusively these gases (plus gasless unlocks) may be purchased. */
  onlyGasIds?: GasId[];
}

export interface ChallengeGoal {
  description: string;
  check: (state: GameState, civLevel: number) => boolean;
}

export interface Challenge {
  id: string;
  name: string;
  description: string;
  icon: string;
  restriction: ChallengeRestriction;
  goal: ChallengeGoal;
  rewardDescription: string;
  reward: PrestigeUpgradeEffect;
}

function techProducesOnly(tech: Technology, allowedGases: GasId[]): boolean {
  const produced = Object.keys(tech.effect.gasProductionPerUnit ?? {}) as GasId[];
  return produced.every((g) => allowedGases.includes(g));
}

export const CHALLENGES: Challenge[] = [
  {
    id: 'ice_age',
    name: 'Ice Age',
    description: 'Reach a strong industrial base while keeping temperature below +2°C.',
    icon: '🧊',
    restriction: { maxTemperatureC: 2 },
    goal: { description: 'Reach civilization level 40 without exceeding +2°C.', check: (_s, civLevel) => civLevel >= 40 },
    rewardDescription: '+10% permanent production',
    reward: { globalProductionMultiplier: 1.1 },
  },
  {
    id: 'low_carbon',
    name: 'Low Carbon',
    description: 'Coal technologies are forbidden for the entire run.',
    icon: '🚫',
    restriction: {
      disabledTechIds: ALL_TECHNOLOGIES.filter((t) => t.id.includes('coal')).map((t) => t.id),
    },
    goal: { description: 'Accumulate 1 trillion (1e12) total Energy produced.', check: (s) => s.resources.energy.gte('1e12') },
    rewardDescription: '+25% Research, permanently',
    reward: { researchMultiplier: 1.25 },
  },
  {
    id: 'no_oil',
    name: 'No Oil',
    description: 'Oil and its downstream technologies are off-limits.',
    icon: '🛢️',
    restriction: {
      disabledTechIds: ALL_TECHNOLOGIES.filter((t) => t.branch === 'fossilFuels' || ['automobile', 'diesel_engine', 'jet_aircraft', 'petrochemicals'].includes(t.id)).map((t) => t.id),
    },
    goal: { description: 'Reach civilization level 35 without any oil technology.', check: (_s, civLevel) => civLevel >= 35 },
    // The copy here advertised a Transportation branch bonus that the effect
    // below never granted. The effect is the shipped balance and is left as it
    // is; the text is what was wrong, so the text is what changed. Kept in
    // step with the Kotlin port, which pins this string in ContentParityTest.
    rewardDescription: '+5% permanent production',
    reward: { globalProductionMultiplier: 1.05 },
  },
  {
    id: 'primitive',
    name: 'Primitive',
    description: 'Never advance past the Iron Age.',
    icon: '⛏️',
    restriction: { maxTechTier: 6 },
    goal: { description: 'Reach 400 ppm atmospheric CO₂ using only Primitive-era technology.', check: (s) => s.atmosphere.co2.gte(120) },
    rewardDescription: 'Start every future run with extra starting Energy',
    reward: { startingResources: { energy: 500 } },
  },
  {
    id: 'speedrun',
    name: 'Speedrun',
    description: 'Reset Earth as quickly as possible.',
    icon: '⏱️',
    restriction: {},
    goal: { description: 'Reset within 15 minutes of starting the run.', check: (s) => (Date.now() - s.runStartedAt) / 1000 <= 900 },
    // A x1.0 multiplier is not a bonus, and this copy promised one anyway:
    // there is no 'Earth Points earned' field in the effect shape for it to
    // have been wired to. Described for what it is rather than inventing a
    // balance change. See docs/CODEBASE_AUDIT.md in the Android repository.
    rewardDescription: 'Bragging rights \u2014 this one pays no permanent bonus',
    reward: { globalProductionMultiplier: 1.0 },
  },
  {
    id: 'single_gas',
    name: 'Single Gas',
    description: 'Only technologies that emit CO₂ (or nothing) may be used.',
    icon: '☁️',
    restriction: {
      disabledTechIds: ALL_TECHNOLOGIES.filter((t) => !techProducesOnly(t, ['co2'])).map((t) => t.id),
    },
    goal: { description: 'Reach civilization level 30 using only CO₂-producing technology.', check: (_s, civLevel) => civLevel >= 30 },
    rewardDescription: '+30% CO₂ production, permanently',
    reward: { gasProductionMultiplier: { gas: 'co2', multiplier: 1.3 } },
  },
  {
    id: 'methane_world_challenge',
    name: 'Methane World',
    description: 'Make CH₄ the dominant greenhouse gas by the time you reset.',
    icon: '🐄',
    restriction: {},
    goal: {
      description: 'Reset while CH₄ contributes more forcing than any other gas.',
      check: (s) => s.forcing.perGas.ch4 > s.forcing.perGas.co2 && s.forcing.perGas.ch4 > 0,
    },
    rewardDescription: '+50% CH₄ production, permanently',
    reward: { gasProductionMultiplier: { gas: 'ch4', multiplier: 1.5 } },
  },
  {
    id: 'zero_emissions',
    name: 'Zero Emissions',
    description: 'Build a strong civilization while keeping gross emissions minimal.',
    icon: '🌱',
    restriction: { disabledTechIds: ['coal_power_plant', 'supercritical_coal', 'coal_mega_mining', 'oil_sands'] },
    goal: {
      description: 'Reach civilization level 25 while total gross gas production stays under 1e6 kg/s.',
      check: (s, civLevel) => civLevel >= 25,
    },
    rewardDescription: '+20% Research, permanently',
    reward: { researchMultiplier: 1.2 },
  },
];

export const CHALLENGE_BY_ID: Record<string, Challenge> = Object.fromEntries(CHALLENGES.map((c) => [c.id, c]));

/** Whether purchasing `techId` would violate the active challenge's restriction. */
export function isTechRestrictedByChallenge(challenge: Challenge, techId: string): boolean {
  const tech = TECH_BY_ID[techId];
  if (!tech) return false;
  if (challenge.restriction.disabledTechIds?.includes(techId)) return true;
  if (challenge.restriction.maxTechTier !== undefined && tech.tier > challenge.restriction.maxTechTier) return true;
  if (challenge.restriction.onlyGasIds && tech.effect.gasProductionPerUnit) {
    if (!techProducesOnly(tech, challenge.restriction.onlyGasIds)) return true;
  }
  return false;
}

export function disabledTechIdsForChallenge(challenge: Challenge | null): Set<string> {
  if (!challenge) return new Set();
  return new Set(ALL_TECHNOLOGIES.filter((t) => isTechRestrictedByChallenge(challenge, t.id)).map((t) => t.id));
}

/** Whether the challenge's live restriction (e.g. a temperature ceiling) has been broken this run. */
export function isChallengeRestrictionViolated(challenge: Challenge, state: GameState): boolean {
  if (challenge.restriction.maxTemperatureC !== undefined && state.temperatureAnomalyC > challenge.restriction.maxTemperatureC) {
    return true;
  }
  return false;
}

export function isChallengeGoalMet(challenge: Challenge, state: GameState, civLevel: number): boolean {
  return challenge.goal.check(state, civLevel);
}

/** Aggregates every completed challenge's permanent reward into one effect bundle, in the same shape prestige upgrades use. */
export function computeChallengeRewardEffects(completed: Record<string, boolean>): PrestigeUpgradeEffect[] {
  return CHALLENGES.filter((c) => completed[c.id]).map((c) => c.reward);
}
