import { D } from './bignum';
import { GasId, GAS_LIST } from './gases';
import { AtmosphereState } from './climate';
import { MultiplierContribution } from './simulation';
import { ActiveEvent } from './gameState';

export interface RandomEventDefinition {
  id: string;
  name: string;
  description: string;
  icon: string;
  /** Random events only start rolling once the civilization has reached roughly this progress level. */
  minCivLevel: number;
  durationSeconds: number;
  /** Relative selection weight among currently-eligible events. */
  weight: number;
  isNegative?: boolean;
  effect: MultiplierContribution;
  /** A one-time atmospheric mass injection applied the instant the event triggers, in kg. */
  instantGasBurstKg?: Partial<Record<GasId, number>>;
}

/**
 * Short-lived multiplier swings rolled by chance, roughly every few minutes.
 *
 * The set skews hard toward the positive on purpose. A negative event in an
 * idle game is a tax on being away — you were not there, you could not react,
 * and all it did was quietly make the bar fill slower. The two that remain
 * are short, mild, and exist so that a green banner means something; every
 * other roll is a gift. Weights and durations are tuned so the *positive*
 * ones are the ones long enough to notice and act on.
 */
export const RANDOM_EVENTS: RandomEventDefinition[] = [
  {
    id: 'good_harvest',
    name: 'Bumper Harvest',
    description: 'A season goes exactly right. Everything you run produces triple for a while.',
    icon: '🌻',
    minCivLevel: 1,
    durationSeconds: 90,
    weight: 4,
    effect: { global: 3 },
  },
  {
    id: 'master_craftsman',
    name: 'Master Craftsman',
    description: 'Someone works out a better way to do it, and tells everyone. Research floods in.',
    icon: '🔨',
    minCivLevel: 3,
    durationSeconds: 90,
    weight: 4,
    effect: { research: 8 },
  },
  {
    id: 'volcanic_eruption',
    name: 'Volcanic Eruption',
    description: 'A major eruption injects a pulse of CO₂ into the atmosphere.',
    icon: '🌋',
    minCivLevel: 0,
    durationSeconds: 30,
    weight: 3,
    effect: {},
    instantGasBurstKg: { co2: 5e10 },
  },
  {
    id: 'wildfire',
    name: 'Wildfire',
    description: 'Vast wildfires release a large burst of carbon dioxide.',
    icon: '🔥',
    minCivLevel: 3,
    durationSeconds: 30,
    weight: 3,
    effect: {},
    instantGasBurstKg: { co2: 2e11 },
  },
  {
    id: 'el_nino',
    name: 'El Niño',
    description: 'Shifting ocean currents temporarily amplify warming feedback loops.',
    icon: '🌊',
    minCivLevel: 5,
    durationSeconds: 120,
    weight: 3,
    effect: { allGas: 2 },
  },
  {
    id: 'methane_release',
    name: 'Methane Release',
    description: 'Thawing permafrost vents a burst of trapped methane.',
    icon: '🧊',
    minCivLevel: 8,
    durationSeconds: 120,
    weight: 3,
    effect: { perGas: { ch4: 5 } },
  },
  {
    id: 'industrial_boom',
    name: 'Industrial Boom',
    description: "A surge of investment sends every factory's output soaring.",
    icon: '📈',
    minCivLevel: 6,
    durationSeconds: 120,
    weight: 4,
    effect: { global: 8 },
  },
  {
    id: 'economic_crash',
    name: 'Economic Crash',
    description: 'Markets collapse. Industrial output slows to a crawl.',
    icon: '📉',
    minCivLevel: 6,
    durationSeconds: 30,
    weight: 1,
    isNegative: true,
    effect: { global: 0.7 },
  },
  {
    id: 'green_revolution',
    name: 'Green Revolution',
    description: 'New techniques send agricultural output through the roof.',
    icon: '🌾',
    minCivLevel: 7,
    durationSeconds: 120,
    weight: 3,
    effect: { perBranch: { agriculture: 15 } },
  },
  {
    id: 'technological_breakthrough',
    name: 'Technological Breakthrough',
    description: 'A sudden insight accelerates research dramatically.',
    icon: '💡',
    minCivLevel: 10,
    durationSeconds: 120,
    weight: 4,
    effect: { research: 30 },
  },
  {
    id: 'supply_chain_shock',
    name: 'Supply Chain Shock',
    description: 'Global logistics seize up, throttling every branch at once.',
    icon: '🚧',
    minCivLevel: 18,
    durationSeconds: 30,
    weight: 1,
    isNegative: true,
    effect: { global: 0.75 },
  },
  {
    id: 'agi_breakthrough',
    name: 'AGI Breakthrough',
    description: 'Machines start optimizing the optimizers. Research goes vertical.',
    icon: '🤖',
    minCivLevel: 26,
    durationSeconds: 150,
    weight: 4,
    effect: { research: 50, global: 4 },
  },
  {
    id: 'stellar_flare',
    name: 'Stellar Flare',
    description: "Your Dyson swarm catches a solar flare at full charge — a brief, absurd surge of everything.",
    icon: '☀️',
    minCivLevel: 40,
    durationSeconds: 150,
    weight: 4,
    effect: { global: 20, allGas: 3 },
  },
];

export const RANDOM_EVENT_BY_ID: Record<string, RandomEventDefinition> = Object.fromEntries(
  RANDOM_EVENTS.map((e) => [e.id, e]),
);

/** Simple deterministic-friendly weighted pick; caller supplies the random draw in [0,1) for testability. */
export function rollRandomEvent(civLevel: number, activeEventDefIds: Set<string>, random: number): RandomEventDefinition | null {
  const eligible = RANDOM_EVENTS.filter((e) => e.minCivLevel <= civLevel && !activeEventDefIds.has(e.id));
  if (eligible.length === 0) return null;
  const totalWeight = eligible.reduce((sum, e) => sum + e.weight, 0);
  let threshold = random * totalWeight;
  for (const event of eligible) {
    threshold -= event.weight;
    if (threshold <= 0) return event;
  }
  return eligible[eligible.length - 1];
}

/** Sums the still-active (non-expired) events into one multiplier contribution for the current tick. */
export function computeActiveEventMultipliers(activeEvents: ActiveEvent[], nowMs: number): MultiplierContribution {
  const result: MultiplierContribution = {};
  for (const active of activeEvents) {
    if (active.endsAt <= nowMs) continue;
    const def = RANDOM_EVENT_BY_ID[active.eventDefId];
    if (!def) continue;
    const e = def.effect;
    if (e.global) result.global = (result.global ?? 1) * e.global;
    if (e.research) result.research = (result.research ?? 1) * e.research;
    if (e.allGas) result.allGas = (result.allGas ?? 1) * e.allGas;
    if (e.perGas) {
      result.perGas = result.perGas ?? {};
      for (const [gasId, mult] of Object.entries(e.perGas) as [GasId, number][]) {
        result.perGas[gasId] = (result.perGas[gasId] ?? 1) * mult;
      }
    }
    if (e.perBranch) {
      result.perBranch = result.perBranch ?? {};
      for (const [branch, mult] of Object.entries(e.perBranch) as [keyof typeof result.perBranch, number][]) {
        result.perBranch[branch] = (result.perBranch[branch] ?? 1) * mult;
      }
    }
  }
  return result;
}

export function removeExpiredEvents(activeEvents: ActiveEvent[], nowMs: number): ActiveEvent[] {
  return activeEvents.filter((e) => e.endsAt > nowMs);
}

/** Applies an event's one-time atmospheric burst directly to the atmosphere state. */
export function applyInstantGasBurst(atmosphere: AtmosphereState, def: RandomEventDefinition): AtmosphereState {
  if (!def.instantGasBurstKg) return atmosphere;
  const next = { ...atmosphere };
  for (const gas of GAS_LIST) {
    const burstKg = def.instantGasBurstKg[gas.id];
    if (!burstKg) continue;
    next[gas.id] = next[gas.id].add(D(burstKg).div(gas.massPerUnit));
  }
  return next;
}
