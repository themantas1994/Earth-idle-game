import { Decimal, D } from './bignum';
import { GasId, GASES, GAS_LIST, naturalRemovalRateConstant } from './gases';
import { ResourceId, RESOURCE_LIST } from './resources';
import { ALL_TECHNOLOGIES, TechBranch, Technology } from './technologies';
import {
  computeSinkEfficiency,
  integrateGasConcentration,
  computeForcing,
  computeTemperatureAnomaly,
  computeWaterVaporFeedbackConcentration,
  gasDisplayConcentration,
} from './climate';
import {
  computeOceanPh,
  integrateSeaLevelRise,
  agriculturalOutputFactor,
  biodiversityFactor,
  computeHabitability,
} from './habitability';
import { GameState, GasTotals } from './gameState';
import { PrestigeMultipliers } from './prestige';
import { ownershipMultiplier } from './ownership';
import { gameSecondsFor } from './gameTime';

/** Aggregated, ready-to-apply multiplier bundle for one simulation step. */
export interface EffectiveMultipliers {
  global: number;
  perBranch: Partial<Record<TechBranch, number>>;
  perGas: Partial<Record<GasId, number>>;
  perResource: Partial<Record<ResourceId, number>>;
  research: number;
  allGas: number;
}

/** A transient multiplier layer (random events, challenge modifiers) folded in alongside prestige and tech effects. */
export interface MultiplierContribution {
  global?: number;
  research?: number;
  allGas?: number;
  perGas?: Partial<Record<GasId, number>>;
  perBranch?: Partial<Record<TechBranch, number>>;
}

/**
 * Folds every owned multiplier/choice technology, every owned prestige
 * upgrade, and any transient contributions (active random events, completed
 * challenge rewards) into one bundle of multipliers, so per-tick production
 * math is a single lookup rather than re-walking the tech tree.
 */
export function computeEffectiveMultipliers(
  techOwned: Record<string, number>,
  prestige: PrestigeMultipliers,
  extra: MultiplierContribution[] = [],
): EffectiveMultipliers {
  const result: EffectiveMultipliers = {
    global: prestige.global,
    perBranch: {},
    perGas: { ...prestige.perGas },
    perResource: {},
    research: prestige.research,
    allGas: prestige.allGas,
  };

  for (const contribution of extra) {
    if (contribution.global) result.global *= contribution.global;
    if (contribution.research) result.research *= contribution.research;
    if (contribution.allGas) result.allGas *= contribution.allGas;
    if (contribution.perGas) {
      for (const [gasId, multiplier] of Object.entries(contribution.perGas) as [GasId, number][]) {
        result.perGas[gasId] = (result.perGas[gasId] ?? 1) * multiplier;
      }
    }
    if (contribution.perBranch) {
      for (const [branch, multiplier] of Object.entries(contribution.perBranch) as [TechBranch, number][]) {
        result.perBranch[branch] = (result.perBranch[branch] ?? 1) * multiplier;
      }
    }
  }

  for (const tech of ALL_TECHNOLOGIES) {
    if ((techOwned[tech.id] ?? 0) <= 0) continue;
    const e = tech.effect;
    if (e.globalProductionMultiplier) result.global *= e.globalProductionMultiplier;
    if (e.researchMultiplier) result.research *= e.researchMultiplier;
    if (e.branchProductionMultiplier) {
      const { branch, multiplier } = e.branchProductionMultiplier;
      result.perBranch[branch] = (result.perBranch[branch] ?? 1) * multiplier;
    }
    if (e.gasProductionMultiplier) {
      const { gas, multiplier } = e.gasProductionMultiplier;
      result.perGas[gas] = (result.perGas[gas] ?? 1) * multiplier;
    }
    if (e.resourceProductionMultiplier) {
      const { resource, multiplier } = e.resourceProductionMultiplier;
      result.perResource[resource] = (result.perResource[resource] ?? 1) * multiplier;
    }
  }

  return result;
}

/**
 * Everything that scales one building's output: the global and per-branch
 * multipliers it shares with the rest of the civilization, times the
 * ownership bonus it has earned on its own (see `ownership.ts`).
 */
function techScale(tech: Technology, owned: number, multipliers: EffectiveMultipliers): number {
  return multipliers.global * (multipliers.perBranch[tech.branch] ?? 1) * ownershipMultiplier(owned);
}

export interface ProductionRates {
  gasGrossKgPerS: Record<GasId, Decimal>; // before removal — "how much civilization is emitting"
  gasRemovalKgPerS: Record<GasId, Decimal>; // engineered removal (carbon capture, reforestation, ...)
  resourcePerS: Record<ResourceId, Decimal>;
}

function zeroGasMap(): Record<GasId, Decimal> {
  return Object.fromEntries(GAS_LIST.map((g) => [g.id, Decimal.ZERO])) as Record<GasId, Decimal>;
}
function zeroResourceMap(): Record<ResourceId, Decimal> {
  return Object.fromEntries(RESOURCE_LIST.map((r) => [r.id, Decimal.ZERO])) as Record<ResourceId, Decimal>;
}

/**
 * What one technology contributes at `owned` units, with every multiplier
 * applied. Split out from `computeProductionRates` so the Production screen
 * can show a building's own contribution rather than the global total for the
 * gases it happens to emit.
 */
export function computeTechProductionRates(
  tech: Technology,
  owned: number,
  multipliers: EffectiveMultipliers,
): ProductionRates {
  const gasGrossKgPerS = zeroGasMap();
  const gasRemovalKgPerS = zeroGasMap();
  const resourcePerS = zeroResourceMap();
  if (owned <= 0 || tech.kind !== 'generator') return { gasGrossKgPerS, gasRemovalKgPerS, resourcePerS };

  const scale = techScale(tech, owned, multipliers);

  if (tech.effect.gasProductionPerUnit) {
    for (const [gasId, perUnit] of Object.entries(tech.effect.gasProductionPerUnit) as [GasId, number][]) {
      const gasMultiplier = (multipliers.perGas[gasId] ?? 1) * multipliers.allGas;
      gasGrossKgPerS[gasId] = D(perUnit).mul(owned).mul(scale).mul(gasMultiplier);
    }
  }
  if (tech.effect.gasRemovalPerUnit) {
    for (const [gasId, perUnit] of Object.entries(tech.effect.gasRemovalPerUnit) as [GasId, number][]) {
      gasRemovalKgPerS[gasId] = D(perUnit).mul(owned).mul(scale);
    }
  }
  if (tech.effect.resourceProductionPerUnit) {
    for (const [resId, perUnit] of Object.entries(tech.effect.resourceProductionPerUnit) as [ResourceId, number][]) {
      const resMultiplier = (multipliers.perResource[resId] ?? 1) * (resId === 'research' ? multipliers.research : 1);
      resourcePerS[resId] = D(perUnit).mul(owned).mul(scale).mul(resMultiplier);
    }
  }

  return { gasGrossKgPerS, gasRemovalKgPerS, resourcePerS };
}

/** Sums every owned generator's per-unit output into total production rates, with all multipliers applied. */
export function computeProductionRates(
  techOwned: Record<string, number>,
  multipliers: EffectiveMultipliers,
): ProductionRates {
  const gasGrossKgPerS = zeroGasMap();
  const gasRemovalKgPerS = zeroGasMap();
  const resourcePerS = zeroResourceMap();

  for (const tech of ALL_TECHNOLOGIES) {
    const owned = techOwned[tech.id] ?? 0;
    if (owned <= 0 || tech.kind !== 'generator') continue;
    const contribution = computeTechProductionRates(tech, owned, multipliers);

    for (const gas of GAS_LIST) {
      gasGrossKgPerS[gas.id] = gasGrossKgPerS[gas.id].add(contribution.gasGrossKgPerS[gas.id]);
      gasRemovalKgPerS[gas.id] = gasRemovalKgPerS[gas.id].add(contribution.gasRemovalKgPerS[gas.id]);
    }
    for (const res of RESOURCE_LIST) {
      resourcePerS[res.id] = resourcePerS[res.id].add(contribution.resourcePerS[res.id]);
    }
  }

  return { gasGrossKgPerS, gasRemovalKgPerS, resourcePerS };
}

/** Overall civilization progress score: sum of (tier + 1) across every distinct owned technology. */
export function computeCivLevel(techOwned: Record<string, number>): number {
  let level = 0;
  for (const tech of ALL_TECHNOLOGIES) {
    if ((techOwned[tech.id] ?? 0) > 0) level += tech.tier + 1;
  }
  return level;
}

export interface SimulationStepResult {
  state: GameState;
  productionRates: ProductionRates;
}

/**
 * Advances the whole simulation by `dtSeconds` of **real** time. This is the
 * single source of truth for how gases, resources, temperature, and
 * habitability evolve — used identically for live 250ms ticks and for
 * coarse-stepped offline catch-up, so both paths are guaranteed consistent.
 *
 * It is also the only thing that ages the planet: every real second simulated
 * here advances `gameAgeSeconds` by `GAME_SECONDS_PER_REAL_SECOND`, and that
 * simulated age is the clock the atmosphere's half-lives decay on.
 */
export function simulateStep(
  state: GameState,
  dtSeconds: number,
  prestige: PrestigeMultipliers,
  extraMultipliers: MultiplierContribution[] = [],
): SimulationStepResult {
  if (dtSeconds <= 0) {
    return {
      state,
      productionRates: computeProductionRates(state.techOwned, computeEffectiveMultipliers(state.techOwned, prestige, extraMultipliers)),
    };
  }

  const multipliers = computeEffectiveMultipliers(state.techOwned, prestige, extraMultipliers);
  const rates = computeProductionRates(state.techOwned, multipliers);
  const sinkEfficiency = computeSinkEfficiency(state.temperatureAnomalyC);

  const newAtmosphere = { ...state.atmosphere };
  const totalGasProducedKg = { ...state.runStats.totalGasProducedKg };
  const lifetimeGasProducedKg = { ...state.lifetimeStats.totalGasProducedKg };

  for (const gas of GAS_LIST) {
    const k = naturalRemovalRateConstant(gas);
    let productionRate = rates.gasGrossKgPerS[gas.id].div(gas.massPerUnit);
    const removalRate = rates.gasRemovalKgPerS[gas.id].div(gas.massPerUnit);

    if (gas.id === 'h2o') {
      // Water vapor is feedback, not accumulation: drive it toward an
      // equilibrium set by the *previous* temperature anomaly, using its
      // own (very short) lifetime as the relaxation rate.
      const equilibrium = computeWaterVaporFeedbackConcentration(state.temperatureAnomalyC);
      productionRate = D(equilibrium * k);
    }

    newAtmosphere[gas.id] = integrateGasConcentration(
      state.atmosphere[gas.id],
      productionRate,
      k,
      sinkEfficiency,
      removalRate,
      dtSeconds,
    );

    if (gas.directlyEmitted) {
      const producedThisStep = rates.gasGrossKgPerS[gas.id].mul(dtSeconds);
      totalGasProducedKg[gas.id] = totalGasProducedKg[gas.id].add(producedThisStep);
      lifetimeGasProducedKg[gas.id] = lifetimeGasProducedKg[gas.id].add(producedThisStep);
    }
  }

  const newResources = { ...state.resources };
  for (const res of RESOURCE_LIST) {
    newResources[res.id] = newResources[res.id].add(rates.resourcePerS[res.id].mul(dtSeconds));
  }

  const forcing = computeForcing(newAtmosphere);
  const newTemp = computeTemperatureAnomaly(forcing.total);
  const avgTempForSeaLevel = (state.temperatureAnomalyC + newTemp) / 2;
  const newSeaLevel = integrateSeaLevelRise(state.seaLevelRiseMeters, avgTempForSeaLevel, dtSeconds);
  const co2Ppm = gasDisplayConcentration('co2', newAtmosphere);
  const oceanPh = computeOceanPh(newAtmosphere.co2.toNumber());
  const agFactor = agriculturalOutputFactor(newTemp);
  const bioFactor = biodiversityFactor(newTemp);

  const habitability = computeHabitability({
    temperatureAnomalyC: newTemp,
    oceanPh,
    seaLevelRiseMeters: newSeaLevel,
    agriculturalOutputFraction: agFactor,
    biodiversityFraction: bioFactor,
  });

  const totalGrossRate = GAS_LIST.reduce((sum, g) => sum.add(rates.gasGrossKgPerS[g.id]), Decimal.ZERO);
  const gameSecondsElapsed = gameSecondsFor(dtSeconds);

  const newState: GameState = {
    ...state,
    gameAgeSeconds: state.gameAgeSeconds + gameSecondsElapsed,
    atmosphere: newAtmosphere,
    resources: newResources,
    seaLevelRiseMeters: newSeaLevel,
    previousTemperatureAnomalyC: state.temperatureAnomalyC,
    temperatureAnomalyC: newTemp,
    forcing,
    habitability,
    oceanPh,
    runStats: {
      ...state.runStats,
      totalGasProducedKg,
      peakForcingWm2: Math.max(state.runStats.peakForcingWm2, forcing.total),
      peakTemperatureC: Math.max(state.runStats.peakTemperatureC, newTemp),
      peakCo2Ppm: Math.max(state.runStats.peakCo2Ppm, co2Ppm),
      peakGasProductionRateKgPerS: state.runStats.peakGasProductionRateKgPerS.max(totalGrossRate),
    },
    lifetimeStats: {
      ...state.lifetimeStats,
      totalPlayTimeSeconds: state.lifetimeStats.totalPlayTimeSeconds + dtSeconds,
      totalSimulatedSeconds: state.lifetimeStats.totalSimulatedSeconds + gameSecondsElapsed,
      totalGasProducedKg: lifetimeGasProducedKg,
      highestTemperatureC: Math.max(state.lifetimeStats.highestTemperatureC, newTemp),
      highestCo2Ppm: Math.max(state.lifetimeStats.highestCo2Ppm, co2Ppm),
    },
    collapsed: state.collapsed || habitability.fraction <= 0.0001,
  };

  return { state: newState, productionRates: rates };
}

export function sumGasTotals(totals: GasTotals): Decimal {
  return GAS_LIST.reduce((sum, gas) => sum.add(totals[gas.id]), Decimal.ZERO);
}
