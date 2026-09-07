import { GameState } from './gameState';
import { ALL_TECHNOLOGIES, BRANCH_META, TechBranch } from './technologies';
import { D } from './bignum';

export interface AchievementContext {
  state: GameState;
  civLevel: number;
  justCollapsed: boolean;
  justReset: boolean;
  lastRunDurationSeconds: number | null;
  previousRunDurationSeconds: number | null;
}

export interface Achievement {
  id: string;
  name: string;
  description: string;
  icon: string;
  check: (ctx: AchievementContext) => boolean;
}

function maxGeneratorOwned(state: GameState): number {
  let max = 0;
  for (const tech of ALL_TECHNOLOGIES) {
    if (tech.kind !== 'generator') continue;
    max = Math.max(max, state.techOwned[tech.id] ?? 0);
  }
  return max;
}

function ownsAnyChoiceTech(state: GameState): boolean {
  return ALL_TECHNOLOGIES.some((t) => t.choiceGroup && (state.techOwned[t.id] ?? 0) > 0);
}

function ownsAtLeastOneTechInEveryBranch(state: GameState): boolean {
  const branches = new Set<TechBranch>(Object.keys(BRANCH_META) as TechBranch[]);
  for (const tech of ALL_TECHNOLOGIES) {
    if ((state.techOwned[tech.id] ?? 0) > 0) branches.delete(tech.branch);
  }
  return branches.size === 0;
}

export const ACHIEVEMENTS: Achievement[] = [
  { id: 'first_spark', name: 'First Spark', description: 'Discover Controlled Fire.', icon: '🔥', check: (c) => (c.state.techOwned.controlled_fire ?? 0) > 0 },
  { id: 'first_emission', name: 'First Emission', description: 'Produce your first greenhouse gas.', icon: '💨', check: (c) => c.state.lifetimeStats.totalGasProducedKg.co2.gt(0) },
  { id: 'rise_of_agriculture', name: 'Rise of Agriculture', description: 'Unlock Early Agriculture.', icon: '🌱', check: (c) => (c.state.techOwned.early_agriculture ?? 0) > 0 },
  { id: 'industrial_revolution', name: 'Industrial Revolution', description: 'Build your first Factory.', icon: '🏭', check: (c) => (c.state.techOwned.factories ?? 0) > 0 },
  { id: 'let_there_be_light', name: 'Let There Be Light', description: 'Generate your first electricity.', icon: '💡', check: (c) => (c.state.techOwned.generator_dynamo ?? 0) > 0 },
  { id: 'fossil_fever', name: 'Fossil Fever', description: 'Start drilling for oil.', icon: '🛢️', check: (c) => (c.state.techOwned.oil_drilling ?? 0) > 0 },
  { id: 'carbon_age', name: 'Carbon Age', description: 'Reach 1,000 ppm atmospheric CO₂.', icon: '☁️', check: (c) => c.state.lifetimeStats.highestCo2Ppm >= 1000 },
  { id: 'hot', name: 'Hot', description: 'Reach +5°C global temperature anomaly.', icon: '🌡️', check: (c) => c.state.lifetimeStats.highestTemperatureC >= 5 },
  { id: 'very_hot', name: 'Very Hot', description: 'Reach +10°C global temperature anomaly.', icon: '🌡️', check: (c) => c.state.lifetimeStats.highestTemperatureC >= 10 },
  { id: 'scorching', name: 'Scorching', description: 'Reach +25°C global temperature anomaly.', icon: '🔥', check: (c) => c.state.lifetimeStats.highestTemperatureC >= 25 },
  { id: 'apocalyptic_heat', name: 'Apocalyptic Heat', description: 'Reach +50°C global temperature anomaly.', icon: '☠️', check: (c) => c.state.lifetimeStats.highestTemperatureC >= 50 },
  { id: 'oops', name: 'Oops', description: 'Make Earth uninhabitable.', icon: '💀', check: (c) => c.justCollapsed },
  { id: 'again', name: 'Again?', description: 'Reset Earth for the first time.', icon: '🔄', check: (c) => c.justReset && c.state.lifetimeStats.totalResets === 1 },
  {
    id: 'faster_this_time',
    name: 'Faster This Time',
    description: 'Reset faster than your previous run.',
    icon: '⏱️',
    check: (c) =>
      c.justReset &&
      c.lastRunDurationSeconds !== null &&
      c.previousRunDurationSeconds !== null &&
      c.lastRunDurationSeconds < c.previousRunDurationSeconds,
  },
  {
    id: 'civilization_speedrun',
    name: 'Civilization Speedrun',
    description: 'Reach the reset condition in under 12 hours.',
    icon: '⚡',
    // A first run is a week's work; twelve hours is what a deep prestige stack
    // and a well-drilled route can do to that, not a target for a fresh Earth.
    check: (c) => c.justReset && c.lastRunDurationSeconds !== null && c.lastRunDurationSeconds <= 12 * 3600,
  },
  // 250 rather than a rounder 1,000: per-unit costs grow 16% a unit, so the
  // thousandth copy of anything costs some 10^64 times the first and no run will
  // ever buy it.
  { id: 'industrial_monster', name: 'Industrial Monster', description: 'Own 250 of any single generator.', icon: '🏗️', check: (c) => maxGeneratorOwned(c.state) >= 250 },
  {
    id: 'bigger_than_earth',
    name: 'Bigger Than Earth',
    description: 'Accumulate 1 septillion (1e24) of any resource.',
    icon: '🌌',
    check: (c) => Object.values(c.state.resources).some((amount) => amount.gte(D('1e24'))),
  },
  {
    id: 'methane_world',
    name: 'Methane World',
    description: "Make CH₄ your atmosphere's dominant warming contributor.",
    icon: '🐄',
    check: (c) => c.state.forcing.perGas.ch4 > c.state.forcing.perGas.co2 && c.state.forcing.perGas.ch4 > 0,
  },
  {
    id: 'ozone_hole',
    name: 'Ozone Hole',
    description: 'Deplete the ozone layer with CFCs.',
    icon: '🕳️',
    check: (c) => c.state.atmosphere.o3.lt(-50),
  },
  { id: 'rising_tides', name: 'Rising Tides', description: 'Cause 50m of sea level rise.', icon: '🌊', check: (c) => c.state.seaLevelRiseMeters >= 50 },
  { id: 'mass_extinction', name: 'Mass Extinction', description: 'Reduce biodiversity below 10%.', icon: '🦴', check: (c) => c.state.habitability.factors.biodiversity <= 0.1 },
  { id: 'acid_ocean', name: 'Acid Ocean', description: 'Drop ocean pH to 7.0 or below.', icon: '🧪', check: (c) => c.state.oceanPh <= 7.0 },
  { id: 'new_beginning', name: 'New Beginning', description: 'Earn your first Earth Points.', icon: '✨', check: (c) => c.state.prestige.earthPoints.gt(0) },
  { id: 'wealthy_civilization', name: 'Wealthy Civilization', description: 'Accumulate 1,000,000 Earth Points.', icon: '💰', check: (c) => c.state.prestige.earthPoints.gte(1_000_000) },
  { id: 'hearth_keeper', name: 'Hearth Keeper', description: 'Keep 50 Natural Fires burning at once.', icon: '🔥', check: (c) => (c.state.techOwned.natural_fire ?? 0) >= 50 },
  { id: 'front_page', name: 'Front Page', description: 'Make the world news 10 times in a single run.', icon: '📰', check: (c) => c.state.newsFeed.length >= 10 },
  { id: 'road_not_taken', name: 'The Road Not Taken', description: 'Commit to a strategic technology choice.', icon: '🔀', check: (c) => ownsAnyChoiceTech(c.state) },
  { id: 'digital_age', name: 'Digital Age', description: 'Build your first Computer.', icon: '💻', check: (c) => (c.state.techOwned.computers ?? 0) > 0 },
  { id: 'into_the_absurd', name: 'Into the Absurd', description: 'Harness Stellar Energy.', icon: '🌟', check: (c) => (c.state.techOwned.stellar_energy ?? 0) > 0 },
  { id: 'serial_destroyer', name: 'Serial Destroyer', description: 'Reset Earth 10 times.', icon: '♻️', check: (c) => c.state.lifetimeStats.totalResets >= 10 },
  { id: 'renaissance_civilization', name: 'Renaissance Civilization', description: 'Own at least one technology from every branch.', icon: '🎓', check: (c) => ownsAtLeastOneTechInEveryBranch(c.state) },
];

export const ACHIEVEMENT_BY_ID: Record<string, Achievement> = Object.fromEntries(ACHIEVEMENTS.map((a) => [a.id, a]));

/** Returns the ids of achievements that just became unlocked (not already in `unlockedSoFar`). */
export function checkAchievements(ctx: AchievementContext, unlockedSoFar: Record<string, boolean>): string[] {
  const newlyUnlocked: string[] = [];
  for (const achievement of ACHIEVEMENTS) {
    if (unlockedSoFar[achievement.id]) continue;
    if (achievement.check(ctx)) newlyUnlocked.push(achievement.id);
  }
  return newlyUnlocked;
}
