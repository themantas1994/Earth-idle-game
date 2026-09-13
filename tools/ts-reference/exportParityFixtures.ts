/**
 * Golden-fixture generator for the Kotlin port.
 *
 * The frozen TypeScript engine in `./engine/` is the specification the native
 * Android game was ported from. This script runs that engine and writes its answers to JSON,
 * which the Kotlin test suite then asserts against — so "the Kotlin port
 * behaves like the reference" is checked by execution rather than by reading.
 *
 * Run with:  npm install && npm run fixtures   (from tools/ts-reference)
 * Output:    app/src/test/resources/parity/*.json
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { Decimal, D } from './engine/bignum';
import { formatNumber, formatDuration, formatGameAge, formatTemperature, formatPercent, NumberFormatMode } from './engine/format';
import { ALL_TECHNOLOGIES, TECH_BY_ID, maxAffordableQuantity, bulkPurchaseCost, nextPurchaseCost } from './engine/technologies';
import { GAS_LIST, GasId, halfLifeFractionRemaining, naturalRemovalRateConstant } from './engine/gases';
import {
  GAME_SECONDS_PER_REAL_SECOND,
  GAME_SECONDS_PER_YEAR,
  REAL_SECONDS_PER_GAME_YEAR,
  gameSecondsFor,
  gameYearsToSeconds,
} from './engine/gameTime';
import { RESOURCE_LIST, ResourceId } from './engine/resources';
import {
  computeSinkEfficiency,
  integrateGasConcentration,
  computeForcing,
  computeTemperatureAnomaly,
  computeWaterVaporFeedbackConcentration,
} from './engine/climate';
import {
  computeOceanPh,
  integrateSeaLevelRise,
  agriculturalOutputFactor,
  biodiversityFactor,
  temperatureFactor,
  oceanAcidityFactor,
  seaLevelFactor,
  computeHabitability,
} from './engine/habitability';
import { createNewGame, startNewRun, GameState } from './engine/gameState';
import { simulateStep, computeCivLevel, computeEffectiveMultipliers, computeProductionRates } from './engine/simulation';
import { purchaseTechnology, grantTechnology, effectiveCostAmount, nominalizeWallet } from './engine/economy';
import {
  PRESTIGE_UPGRADES,
  computePrestigeMultipliers,
  prestigeUpgradeCost,
  calculatePrestigeGain,
  speedMultiplier,
} from './engine/prestige';
import { ACHIEVEMENTS, checkAchievements } from './engine/achievements';
import { CHALLENGES, disabledTechIdsForChallenge, computeChallengeRewardEffects } from './engine/challenges';
import { RANDOM_EVENTS, rollRandomEvent, computeActiveEventMultipliers, applyInstantGasBurst } from './engine/events';
import { MILESTONES, checkMilestones } from './engine/milestones';
import { computeOfflineProgress, offlineCapSeconds } from './engine/offline';
import { serializeGameState, deserializeGameState } from './engine/save';
import { ownershipMultiplier, ownershipMilestonesCrossed, ownershipProgress, nextOwnershipMilestone } from './engine/ownership';
import { ladderTier, unlockCost, researchCost, generatorBaseCost, gasProduction, resourceProduction, generatorCostGrowth } from './engine/technologies/scaling';

const OUT_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '../../app/src/test/resources/parity');

/**
 * Decimals travel as their exact internal triple, so no precision is lost in
 * the fixture itself. `JSON.stringify` writes Infinity as `null`, which would
 * be indistinguishable from a missing field, so finiteness is carried
 * explicitly — division by zero is a real case the port has to match.
 */
function dec(value: Decimal): { s: number; m: number | null; e: number | null; finite: boolean; str: string } {
  const finite = value.isFinite();
  return {
    s: value.sign,
    m: finite ? value.mantissa : null,
    e: finite ? value.exponent : null,
    finite,
    str: value.toExponential(10),
  };
}

function decMap<T extends string>(map: Record<T, Decimal>): Record<string, ReturnType<typeof dec>> {
  const out: Record<string, ReturnType<typeof dec>> = {};
  for (const [k, v] of Object.entries(map)) out[k] = dec(v as Decimal);
  return out;
}

function write(name: string, data: unknown): void {
  mkdirSync(OUT_DIR, { recursive: true });
  writeFileSync(resolve(OUT_DIR, name), JSON.stringify(data, null, 2) + '\n', 'utf8');
  console.log(`wrote ${name}`);
}

// ---------------------------------------------------------------- decimal ---

const DECIMAL_OPERANDS = [
  '0', '1', '-1', '2', '10', '-10', '0.5', '-0.25', '3.14159', '1e-7', '-1e-7',
  '123456789', '1e15', '1e17', '1e100', '-1e100', '1e308', '1e309', '1e1000', '-1e1000',
  '9.99999e99', '1.000001e50', '7e-300', '1e-400',
];

function decimalFixture() {
  const unary: unknown[] = [];
  for (const a of DECIMAL_OPERANDS) {
    const x = D(a);
    unary.push({
      input: a,
      parsed: dec(x),
      negate: dec(x.negate()),
      abs: dec(x.abs()),
      sqrt: dec(x.abs().sqrt()),
      log10: x.isZero() ? null : x.log10(),
      ln: x.isZero() ? null : x.ln(),
      toNumber: Number.isFinite(x.toNumber()) ? x.toNumber() : null,
      isZero: x.isZero(),
      exponential2: x.toExponential(2),
      exponential6: x.toExponential(6),
      tuple: x.toTuple(),
    });
  }

  const binary: unknown[] = [];
  for (const a of DECIMAL_OPERANDS) {
    for (const b of DECIMAL_OPERANDS) {
      const x = D(a);
      const y = D(b);
      binary.push({
        a, b,
        add: dec(x.add(y)),
        sub: dec(x.sub(y)),
        mul: dec(x.mul(y)),
        div: dec(x.div(y)),
        cmp: x.cmp(y),
        max: dec(x.max(y)),
        min: dec(x.min(y)),
      });
    }
  }

  const powers: unknown[] = [];
  for (const a of ['2', '10', '1.15', '3.05', '0.5', '-2', '1e100', '1e-5']) {
    for (const e of [0, 1, 2, 3, 0.5, 1.5, -1, -2, 10, 100, 1000, 12345, 0.3333333333]) {
      powers.push({ base: a, exp: e, result: dec(D(a).pow(e)) });
    }
  }

  const roundTrip = DECIMAL_OPERANDS.map((a) => ({ input: a, revived: dec(Decimal.fromJSON(D(a).toTuple())) }));

  return { unary, binary, powers, roundTrip };
}

// ----------------------------------------------------------------- format ---

function formatFixture() {
  const modes: NumberFormatMode[] = ['compact', 'scientific', 'engineering', 'full'];
  const values = [
    '0', '1', '-1', '9.5', '10', '99.99', '100', '999', '1000', '1234', '999999',
    '1e6', '1.5e6', '-1.5e6', '1e9', '1.234e12', '1e15', '1e18', '1e21', '1e24', '1e30',
    '1e33', '1e60', '1e63', '1e66', '1e69', '1e100', '1e120', '1e303', '1e400', '1e1000',
    '-1e1000', '0.001', '0.5', '1e-9', '123456.789',
  ];
  const numbers: unknown[] = [];
  for (const v of values) {
    for (const mode of modes) {
      for (const precision of [2, 3]) {
        numbers.push({ value: v, mode, precision, out: formatNumber(v, { mode, precision }) });
      }
    }
  }

  const durations = [0, 1, 9, 59, 60, 61, 90, 599, 3599, 3600, 3661, 7200, 86399, 86400, 90061, 1234567]
    .map((s) => ({ seconds: s, out: formatDuration(s) }));

  const temperatures = [-3.14159, -0.001, 0, 0.5, 1.5, 15.239, 1234.5]
    .map((c) => ({ celsius: c, out2: formatTemperature(c), out0: formatTemperature(c, 0) }));

  const percents = [0, 0.0001, 0.125, 0.5, 0.99994, 1]
    .map((f) => ({ fraction: f, out1: formatPercent(f), out3: formatPercent(f, 3) }));

  const ages = [
    0, 1, 86_399, 86_400, 2_629_800, 5_259_600, 31_557_600, 63_115_200,
    389_263_500, 3.15576e11, 3.15576e14,
  ].map((s) => ({ gameSeconds: s, out: formatGameAge(s), outScientific: formatGameAge(s, 'scientific') }));

  return { numbers, durations, temperatures, percents, ages };
}

// ----------------------------------------------------------- technologies ---

function technologyFixture() {
  return {
    count: ALL_TECHNOLOGIES.length,
    // Order matters: ALL_TECHNOLOGIES is sorted by tier, and the UI walks it.
    order: ALL_TECHNOLOGIES.map((t) => t.id),
    technologies: ALL_TECHNOLOGIES.map((t) => ({
      id: t.id,
      name: t.name,
      branch: t.branch,
      kind: t.kind,
      tier: t.tier,
      icon: t.icon,
      description: t.description,
      requires: t.requires,
      cost: t.cost.map((c) => ({ resource: c.resource, baseAmount: c.baseAmount })),
      costGrowth: t.costGrowth,
      maxOwned: t.maxOwned === Infinity ? null : t.maxOwned,
      choiceGroup: t.choiceGroup ?? null,
      effect: {
        gasProductionPerUnit: t.effect.gasProductionPerUnit ?? null,
        resourceProductionPerUnit: t.effect.resourceProductionPerUnit ?? null,
        gasRemovalPerUnit: t.effect.gasRemovalPerUnit ?? null,
        globalProductionMultiplier: t.effect.globalProductionMultiplier ?? null,
        branchProductionMultiplier: t.effect.branchProductionMultiplier ?? null,
        gasProductionMultiplier: t.effect.gasProductionMultiplier ?? null,
        resourceProductionMultiplier: t.effect.resourceProductionMultiplier ?? null,
        researchMultiplier: t.effect.researchMultiplier ?? null,
      },
    })),
    scaling: Array.from({ length: 45 }, (_, tier) => ({
      tier,
      ladderTier: ladderTier(tier),
      unlockCost: unlockCost(tier),
      researchCost: researchCost(tier),
      generatorBaseCost: generatorBaseCost(tier),
      gasProduction: gasProduction(tier),
      resourceProduction: resourceProduction(tier),
      generatorCostGrowth: generatorCostGrowth(tier),
    })),
  };
}

// --------------------------------------------------------------- economy ---

function economyFixture() {
  const cases: unknown[] = [];
  const sampleTechs = ['natural_fire', 'controlled_fire', 'coal_mining', 'cooking', 'the_wheel', 'nuclear_power', 'stellar_energy'];
  for (const id of sampleTechs) {
    const tech = TECH_BY_ID[id];
    for (const owned of [0, 1, 5, 9, 10, 37, 100]) {
      cases.push({
        techId: id,
        owned,
        nextCost: nextPurchaseCost(tech, owned).map((c) => ({ resource: c.resource, amount: dec(c.amount) })),
        bulk1: bulkPurchaseCost(tech, owned, 1).map((c) => ({ resource: c.resource, amount: dec(c.amount) })),
        bulk10: bulkPurchaseCost(tech, owned, 10).map((c) => ({ resource: c.resource, amount: dec(c.amount) })),
        bulk100: bulkPurchaseCost(tech, owned, 100).map((c) => ({ resource: c.resource, amount: dec(c.amount) })),
      });
    }
  }

  const affordability: unknown[] = [];
  for (const id of ['natural_fire', 'coal_mining', 'nuclear_power']) {
    const tech = TECH_BY_ID[id];
    for (const owned of [0, 3, 25]) {
      for (const balance of ['0', '1', '10', '1000', '1e6', '1e12', '1e30', '1e120']) {
        const wallet: Record<string, Decimal> = {};
        for (const c of tech.cost) wallet[c.resource] = D(balance);
        const qty = maxAffordableQuantity(tech, owned, wallet, Number.MAX_SAFE_INTEGER);
        affordability.push({ techId: id, owned, balance, maxQuantity: null, affordable: qty });
        const capped = maxAffordableQuantity(tech, owned, wallet, 10);
        affordability.push({ techId: id, owned, balance, maxQuantity: 10, affordable: capped });
      }
    }
  }

  // The exactly-on-the-boundary case the affordability audit was about.
  const boundary: unknown[] = [];
  for (const id of ['natural_fire', 'coal_mining']) {
    const tech = TECH_BY_ID[id];
    for (const n of [1, 2, 3, 4, 5, 17, 40]) {
      const exact = bulkPurchaseCost(tech, 0, n);
      const wallet: Record<string, Decimal> = {};
      for (const c of exact) wallet[c.resource] = c.amount;
      boundary.push({
        techId: id,
        units: n,
        walletIsExactlyCostOf: n,
        affordable: maxAffordableQuantity(tech, 0, wallet, Number.MAX_SAFE_INTEGER),
      });
    }
  }

  const discounts: unknown[] = [];
  for (const discount of [0, 0.2, 0.4, 0.8, 0.9, 0.95, 1.2]) {
    for (const nominal of ['100', '1e12']) {
      discounts.push({ discount, nominal, charged: dec(effectiveCostAmount(D(nominal), discount)) });
    }
    const state = createNewGame(0);
    const wallet = nominalizeWallet(
      { ...state.resources, energy: D('1e6') },
      [{ resource: 'energy' }],
      discount,
    );
    discounts.push({ discount, nominalizedEnergyFrom1e6: dec(wallet.energy) });
  }

  const ownership = [0, 1, 9, 10, 11, 19, 20, 50, 99, 100, 1000].map((owned) => ({
    owned,
    multiplier: ownershipMultiplier(owned),
    progress: ownershipProgress(owned),
    nextMilestone: nextOwnershipMilestone(owned),
  }));

  const ownershipCrossings = [
    [0, 5], [0, 10], [0, 11], [5, 25], [9, 10], [10, 10], [12, 47], [0, 100],
  ].map(([before, after]) => ({ before, after, crossed: ownershipMilestonesCrossed(before, after) }));

  return { cases, affordability, boundary, discounts, ownership, ownershipCrossings };
}

// --------------------------------------------------------------- climate ---

function climateFixture() {
  const sink = [-5, 0, 0.5, 1, 5, 15, 50, 500, 5000].map((t) => ({ tempC: t, efficiency: computeSinkEfficiency(t) }));

  const removalRates = GAS_LIST.map((g) => ({
    gas: g.id,
    k: naturalRemovalRateConstant(g),
    massPerUnit: g.massPerUnit,
    halfLifeYears: g.halfLifeYears,
    decaysOnSimulatedClock: g.decaysOnSimulatedClock,
  }));

  // The half-life law on its own, and the same law reached through the
  // integrator with no production — the two have to agree, and 1,000 at a
  // 120-year half-life has to read 500 / 250 / 125 at 120 / 240 / 360
  // simulated years.
  const halfLife: unknown[] = [];
  for (const halfLifeYears of [0.03, 0.06, 12, 114, 120, 3200]) {
    for (const elapsedYears of [0, 1, 60, 120, 240, 360, 1200, 100_000]) {
      const dtGameSeconds = gameYearsToSeconds(elapsedYears);
      const k = Math.LN2 / (halfLifeYears * REAL_SECONDS_PER_GAME_YEAR);
      halfLife.push({
        halfLifeYears,
        elapsedYears,
        dtGameSeconds,
        fractionRemaining: halfLifeFractionRemaining(halfLifeYears, dtGameSeconds),
        // The same elapsed span expressed in the real seconds the integrator takes.
        integrated: dec(
          integrateGasConcentration(D('1000'), Decimal.ZERO, k, 1, Decimal.ZERO, elapsedYears * REAL_SECONDS_PER_GAME_YEAR),
        ),
      });
    }
  }

  const clock = {
    gameSecondsPerYear: GAME_SECONDS_PER_YEAR,
    gameSecondsPerRealSecond: GAME_SECONDS_PER_REAL_SECOND,
    realSecondsPerGameYear: REAL_SECONDS_PER_GAME_YEAR,
    gameSecondsFor: [0, 0.25, 1, 300, 3600, 43200].map((s) => ({ realSeconds: s, gameSeconds: gameSecondsFor(s) })),
  };

  const integration: unknown[] = [];
  for (const gas of GAS_LIST) {
    const k = naturalRemovalRateConstant(gas);
    for (const start of ['0', '1', '1e6']) {
      for (const production of ['0', '1e-9', '1e-3', '1', '1e9']) {
        for (const removal of ['0', '1e-4']) {
          for (const eff of [1, 0.5, 0.05]) {
            for (const dt of [0.25, 1, 300, 3600, 43200, 86400]) {
              integration.push({
                gas: gas.id,
                start, production, removal, sinkEfficiency: eff, dtSeconds: dt,
                result: dec(integrateGasConcentration(D(start), D(production), k, eff, D(removal), dt)),
              });
            }
          }
        }
      }
    }
  }

  const forcing: unknown[] = [];
  for (const co2Extra of [0, 20, 120, 720, 5000, 1e6]) {
    for (const ch4Extra of [0, 300, 1e5]) {
      const atmosphere = Object.fromEntries(GAS_LIST.map((g) => [g.id, Decimal.ZERO])) as Record<GasId, Decimal>;
      atmosphere.co2 = D(co2Extra);
      atmosphere.ch4 = D(ch4Extra);
      atmosphere.n2o = D(ch4Extra / 10);
      atmosphere.o3 = D(co2Extra / 20);
      atmosphere.fluorinated = D(co2Extra * 3);
      atmosphere.h2o = D(co2Extra);
      const f = computeForcing(atmosphere);
      forcing.push({ co2Extra, ch4Extra, perGas: f.perGas, total: f.total });
    }
  }

  const negativeOzone = (() => {
    const atmosphere = Object.fromEntries(GAS_LIST.map((g) => [g.id, Decimal.ZERO])) as Record<GasId, Decimal>;
    atmosphere.o3 = D(-299.5);
    const f = computeForcing(atmosphere);
    return { o3Extra: -299.5, perGas: f.perGas, total: f.total };
  })();

  const temperature = [0, -1, 0.1, 1, 3.7, 10, 100, 1e4, 1e8].map((f) => ({
    forcing: f,
    anomaly: computeTemperatureAnomaly(f),
  }));

  const waterVapor = [-1, 0, 0.5, 3, 40].map((t) => ({ prevTempC: t, ppm: computeWaterVaporFeedbackConcentration(t) }));

  const habitability: unknown[] = [];
  for (const temp of [0, 1, 3, 4, 5, 10, 25, 54.9, 55, 60]) {
    for (const co2Extra of [0, 100, 1000, 6000]) {
      for (const sea of [0, 1, 50, 219, 220, 400]) {
        const ph = computeOceanPh(co2Extra);
        const h = computeHabitability({
          temperatureAnomalyC: temp,
          oceanPh: ph,
          seaLevelRiseMeters: sea,
          agriculturalOutputFraction: agriculturalOutputFactor(temp),
          biodiversityFraction: biodiversityFactor(temp),
        });
        habitability.push({
          tempC: temp, co2Extra, seaLevelM: sea, oceanPh: ph,
          temperatureFactor: temperatureFactor(temp),
          oceanAcidityFactor: oceanAcidityFactor(ph),
          seaLevelFactor: seaLevelFactor(sea),
          agricultureFactor: agriculturalOutputFactor(temp),
          biodiversityFactor: biodiversityFactor(temp),
          fraction: h.fraction,
          factors: h.factors,
        });
      }
    }
  }

  const seaLevel: unknown[] = [];
  for (const current of [0, 1.5, 100]) {
    for (const temp of [-1, 0, 2, 40]) {
      for (const dt of [0.25, 3600, 86400]) {
        seaLevel.push({ current, tempC: temp, dtSeconds: dt, result: integrateSeaLevelRise(current, temp, dt) });
      }
    }
  }

  return { sink, removalRates, halfLife, clock, integration, forcing, negativeOzone, temperature, waterVapor, habitability, seaLevel };
}

// -------------------------------------------------------------- prestige ---

function prestigeFixture() {
  const upgrades = PRESTIGE_UPGRADES.map((u) => ({
    id: u.id,
    name: u.name,
    description: u.description,
    icon: u.icon,
    baseCost: u.baseCost,
    costGrowth: u.costGrowth,
    maxLevel: u.maxLevel === Infinity ? null : u.maxLevel,
    effect: u.effect,
    costs: Array.from({ length: 8 }, (_, level) => dec(prestigeUpgradeCost(u, level))),
  }));

  const bundles: unknown[] = [];
  const ownershipSets: Record<string, number>[] = [
    {},
    { atmospheric_momentum: 1 },
    { atmospheric_momentum: 7 },
    { rapid_research: 5, institutional_memory: 6 },
    { civilizational_acceleration: 4 },
    { civilizational_acceleration: 4, anthropocene_mastery: 1 },
    { eternal_flame: 10, automated_industry: 1, extended_endurance: 3 },
    { head_start: 1, deep_foundations: 1, industrial_memory: 1 },
    // Everything, at max, which is where the discount cap has to hold.
    Object.fromEntries(PRESTIGE_UPGRADES.map((u) => [u.id, u.maxLevel === Infinity ? 25 : u.maxLevel])),
  ];
  for (const owned of ownershipSets) {
    for (const completedChallenges of [[], ['ice_age', 'low_carbon', 'single_gas', 'methane_world_challenge', 'primitive']]) {
      const rewards = computeChallengeRewardEffects(Object.fromEntries(completedChallenges.map((c) => [c, true])));
      const m = computePrestigeMultipliers(owned, rewards);
      bundles.push({
        upgradesOwned: owned,
        completedChallenges,
        global: m.global,
        research: m.research,
        allGas: m.allGas,
        perGas: m.perGas,
        techCostDiscount: m.techCostDiscount,
        offlineCapMultiplier: m.offlineCapMultiplier,
        startingTechIds: m.startingTechIds,
        startingGenerators: m.startingGenerators,
        startingResources: m.startingResources,
        offlineCapSeconds: offlineCapSeconds(m),
      });
    }
  }

  const speeds = [-1, 0, 1, 60, 3600, 43200, 259200, 1e6, 1e9].map((s) => ({ runSeconds: s, multiplier: speedMultiplier(s) }));

  const gains: unknown[] = [];
  for (const gasKg of ['0', '1e9', '1e12', '1e15', '1e20', '1e40', '1e120']) {
    for (const peakForcing of [0, 5, 200, 1e6]) {
      for (const civLevel of [0, 10, 400, 2500]) {
        for (const runSeconds of [900, 43200, 259200, 1e6]) {
          const totals = Object.fromEntries(GAS_LIST.map((g) => [g.id, g.id === 'co2' ? D(gasKg) : Decimal.ZERO]));
          gains.push({
            gasKg, peakForcing, civLevel, runSeconds,
            gain: dec(calculatePrestigeGain({
              totalGasProducedKg: totals as never,
              peakForcingWm2: peakForcing,
              civLevel,
              runDurationSeconds: runSeconds,
            })),
          });
        }
      }
    }
  }

  return { upgrades, bundles, speeds, gains };
}

// ---------------------------------------------------- achievements etc. ---

function contentFixture() {
  return {
    achievements: ACHIEVEMENTS.map((a) => ({ id: a.id, name: a.name, description: a.description, icon: a.icon })),
    challenges: CHALLENGES.map((c) => ({
      id: c.id,
      name: c.name,
      description: c.description,
      icon: c.icon,
      restriction: {
        disabledTechIds: c.restriction.disabledTechIds ?? null,
        maxTechTier: c.restriction.maxTechTier ?? null,
        maxTemperatureC: c.restriction.maxTemperatureC ?? null,
        onlyGasIds: c.restriction.onlyGasIds ?? null,
      },
      goalDescription: c.goal.description,
      rewardDescription: c.rewardDescription,
      reward: c.reward,
      // The computed disabled set is what actually gates purchases.
      disabledTechIds: [...disabledTechIdsForChallenge(c)].sort(),
    })),
    events: RANDOM_EVENTS.map((e) => ({
      id: e.id,
      name: e.name,
      description: e.description,
      icon: e.icon,
      minCivLevel: e.minCivLevel,
      durationSeconds: e.durationSeconds,
      weight: e.weight,
      isNegative: e.isNegative ?? false,
      effect: e.effect,
      instantGasBurstKg: e.instantGasBurstKg ?? null,
    })),
    milestones: MILESTONES.map((m) => ({
      id: m.id,
      headline: m.headline,
      body: m.body,
      source: m.source,
      icon: m.icon,
      category: m.category,
    })),
    gases: GAS_LIST.map((g) => ({
      id: g.id, name: g.name, formula: g.formula, unit: g.unit, baseline: g.baseline,
      massPerUnit: g.massPerUnit, halfLifeYears: g.halfLifeYears,
      decaysOnSimulatedClock: g.decaysOnSimulatedClock, directlyEmitted: g.directlyEmitted,
    })),
    resources: RESOURCE_LIST.map((r) => ({ id: r.id, name: r.name, shortName: r.shortName, description: r.description })),
  };
}

function eventRollFixture() {
  const rolls: unknown[] = [];
  for (const civLevel of [0, 1, 3, 6, 10, 20, 30, 45]) {
    for (const active of [[], ['good_harvest'], ['good_harvest', 'volcanic_eruption', 'wildfire']]) {
      for (const r of [0, 0.05, 0.17, 0.33, 0.5, 0.66, 0.8, 0.99, 0.999999]) {
        const picked = rollRandomEvent(civLevel, new Set(active), r);
        rolls.push({ civLevel, active, random: r, picked: picked?.id ?? null });
      }
    }
  }

  const multipliers: unknown[] = [];
  const now = 1_000_000;
  const combos = [
    [],
    [{ id: 'a', eventDefId: 'good_harvest', startedAt: 0, endsAt: now + 1000 }],
    [
      { id: 'a', eventDefId: 'good_harvest', startedAt: 0, endsAt: now + 1000 },
      { id: 'b', eventDefId: 'industrial_boom', startedAt: 0, endsAt: now + 1000 },
      { id: 'c', eventDefId: 'methane_release', startedAt: 0, endsAt: now + 1000 },
      { id: 'd', eventDefId: 'green_revolution', startedAt: 0, endsAt: now + 1000 },
      { id: 'expired', eventDefId: 'stellar_flare', startedAt: 0, endsAt: now - 1 },
    ],
  ];
  for (const events of combos) {
    multipliers.push({ events, nowMs: now, result: computeActiveEventMultipliers(events, now) });
  }

  const bursts: unknown[] = [];
  for (const id of ['volcanic_eruption', 'wildfire', 'good_harvest']) {
    const def = RANDOM_EVENTS.find((e) => e.id === id)!;
    const atmosphere = Object.fromEntries(GAS_LIST.map((g) => [g.id, D('7')])) as Record<GasId, Decimal>;
    bursts.push({ eventId: id, before: decMap(atmosphere), after: decMap(applyInstantGasBurst(atmosphere, def)) });
  }

  return { rolls, multipliers, bursts };
}

// ------------------------------------------------------------ simulation ---

/** A snapshot of everything a parity test should compare after a step. */
function snapshot(state: GameState, label: string) {
  const prestige = computePrestigeMultipliers(state.prestige.upgradesOwned, computeChallengeRewardEffects(state.challenges.completed));
  const multipliers = computeEffectiveMultipliers(state.techOwned, prestige);
  const rates = computeProductionRates(state.techOwned, multipliers);
  return {
    label,
    gameAgeSeconds: state.gameAgeSeconds,
    techOwned: state.techOwned,
    resources: decMap(state.resources),
    atmosphere: decMap(state.atmosphere),
    temperatureAnomalyC: state.temperatureAnomalyC,
    previousTemperatureAnomalyC: state.previousTemperatureAnomalyC,
    forcingTotal: state.forcing.total,
    forcingPerGas: state.forcing.perGas,
    habitability: state.habitability.fraction,
    habitabilityFactors: state.habitability.factors,
    oceanPh: state.oceanPh,
    seaLevelRiseMeters: state.seaLevelRiseMeters,
    collapsed: state.collapsed,
    civLevel: computeCivLevel(state.techOwned),
    runStats: {
      totalGasProducedKg: decMap(state.runStats.totalGasProducedKg),
      peakForcingWm2: state.runStats.peakForcingWm2,
      peakTemperatureC: state.runStats.peakTemperatureC,
      peakCo2Ppm: state.runStats.peakCo2Ppm,
      peakGasProductionRateKgPerS: dec(state.runStats.peakGasProductionRateKgPerS),
    },
    lifetimeStats: {
      totalPlayTimeSeconds: state.lifetimeStats.totalPlayTimeSeconds,
      totalSimulatedSeconds: state.lifetimeStats.totalSimulatedSeconds,
      totalGasProducedKg: decMap(state.lifetimeStats.totalGasProducedKg),
      highestTemperatureC: state.lifetimeStats.highestTemperatureC,
      highestCo2Ppm: state.lifetimeStats.highestCo2Ppm,
      totalResets: state.lifetimeStats.totalResets,
      totalTechnologiesPurchased: state.lifetimeStats.totalTechnologiesPurchased,
      totalEarthPointsEarned: dec(state.lifetimeStats.totalEarthPointsEarned),
    },
    multipliers: {
      global: multipliers.global,
      research: multipliers.research,
      allGas: multipliers.allGas,
      perGas: multipliers.perGas,
      perBranch: multipliers.perBranch,
      perResource: multipliers.perResource,
    },
    productionRates: {
      gasGrossKgPerS: decMap(rates.gasGrossKgPerS),
      gasRemovalKgPerS: decMap(rates.gasRemovalKgPerS),
      resourcePerS: decMap(rates.resourcePerS),
    },
    milestonesTriggered: Object.keys(state.milestonesTriggered).sort(),
    achievementsUnlocked: Object.keys(state.achievementsUnlocked).sort(),
    newsFeed: state.newsFeed.map((n) => n.milestoneId),
  };
}

type Action =
  | { kind: 'advance'; seconds: number }
  | { kind: 'buy'; techId: string; quantity: number }
  | { kind: 'grantTech'; techId: string }
  | { kind: 'grantCheat'; resource: ResourceId; amount: string };

/**
 * A scripted playthrough. Fixed start time, fixed actions, no randomness — the
 * Kotlin engine replays exactly this and must land on the same numbers.
 */
const SCRIPT: Action[] = [
  { kind: 'advance', seconds: 1 },
  { kind: 'advance', seconds: 30 },
  { kind: 'buy', techId: 'natural_fire', quantity: 1 },
  { kind: 'advance', seconds: 60 },
  { kind: 'buy', techId: 'natural_fire', quantity: 5 },
  { kind: 'advance', seconds: 300 },
  { kind: 'grantCheat', resource: 'energy', amount: '1e6' },
  { kind: 'grantCheat', resource: 'research', amount: '1e6' },
  { kind: 'buy', techId: 'controlled_fire', quantity: 12 },
  { kind: 'advance', seconds: 0.25 },
  { kind: 'advance', seconds: 0.25 },
  { kind: 'buy', techId: 'cooking', quantity: 1 },
  { kind: 'buy', techId: 'charcoal', quantity: 10 },
  { kind: 'advance', seconds: 3600 },
  { kind: 'buy', techId: 'pottery', quantity: 1 },
  { kind: 'buy', techId: 'metalworking', quantity: 1 },
  { kind: 'grantCheat', resource: 'energy', amount: '1e12' },
  { kind: 'grantCheat', resource: 'coal', amount: '1e12' },
  { kind: 'buy', techId: 'bronze', quantity: 20 },
  { kind: 'buy', techId: 'the_wheel', quantity: 1 },
  { kind: 'advance', seconds: 43200 },
  { kind: 'buy', techId: 'iron', quantity: 30 },
  { kind: 'buy', techId: 'early_agriculture', quantity: 1 },
  { kind: 'grantCheat', resource: 'research', amount: '1e15' },
  { kind: 'buy', techId: 'steam_engine', quantity: 1 },
  { kind: 'buy', techId: 'coal_mining', quantity: 40 },
  { kind: 'advance', seconds: 86400 },
  { kind: 'grantCheat', resource: 'energy', amount: '1e30' },
  { kind: 'buy', techId: 'generator_dynamo', quantity: 100 },
  { kind: 'buy', techId: 'coal_power_plant', quantity: 50 },
  { kind: 'buy', techId: 'grid_electricity', quantity: 1 },
  { kind: 'buy', techId: 'coal_industrialization', quantity: 1 },
  // Rival choice already taken: this must be refused, changing nothing.
  { kind: 'buy', techId: 'nuclear_industrialization', quantity: 1 },
  { kind: 'advance', seconds: 86400 },
  { kind: 'grantCheat', resource: 'energy', amount: '1e60' },
  { kind: 'buy', techId: 'oil_drilling', quantity: 60 },
  { kind: 'advance', seconds: 604800 },
  { kind: 'advance', seconds: 604800 },

  // Endgame: force the tree open and push the planet all the way to collapse,
  // so the parity trace covers the extreme-magnitude paths (huge Decimals,
  // saturating habitability factors, the super-linear temperature term) and
  // the milestone/achievement thresholds that only fire up there.
  { kind: 'grantTech', techId: 'oil_refinery' },
  { kind: 'grantTech', techId: 'petrochemicals' },
  { kind: 'grantTech', techId: 'factories' },
  { kind: 'grantTech', techId: 'steel_production' },
  { kind: 'grantTech', techId: 'cement_production' },
  { kind: 'grantTech', techId: 'concrete' },
  { kind: 'grantTech', techId: 'mass_manufacturing' },
  { kind: 'grantTech', techId: 'chemical_industry' },
  { kind: 'grantTech', techId: 'industrial_chemistry' },
  { kind: 'grantTech', techId: 'ammonia' },
  { kind: 'grantTech', techId: 'refrigeration' },
  { kind: 'grantTech', techId: 'cfcs' },
  { kind: 'grantTech', techId: 'hfcs' },
  { kind: 'grantTech', techId: 'gas_turbine' },
  { kind: 'grantTech', techId: 'nuclear_power' },
  { kind: 'grantTech', techId: 'combined_cycle_gas' },
  { kind: 'grantTech', techId: 'mass_electrification' },
  { kind: 'grantTech', techId: 'container_ships' },
  { kind: 'grantTech', techId: 'trucks' },
  { kind: 'grantTech', techId: 'diesel_engine' },
  { kind: 'grantTech', techId: 'global_logistics' },
  { kind: 'grantTech', techId: 'international_trade' },
  { kind: 'grantTech', techId: 'containerization' },
  { kind: 'grantTech', techId: 'global_supply_chains' },
  { kind: 'grantTech', techId: 'computers' },
  { kind: 'grantTech', techId: 'internet' },
  { kind: 'grantTech', techId: 'data_centers' },
  { kind: 'grantTech', techId: 'cloud_computing' },
  { kind: 'grantTech', techId: 'ai' },
  { kind: 'grantTech', techId: 'mass_ai_infrastructure' },
  { kind: 'grantTech', techId: 'hyperscale_data_centers' },
  { kind: 'grantTech', techId: 'advanced_manufacturing' },
  { kind: 'grantTech', techId: 'fusion_power' },
  { kind: 'grantTech', techId: 'orbital_industry' },
  { kind: 'grantTech', techId: 'space_elevator' },
  { kind: 'grantTech', techId: 'moon_mining' },
  { kind: 'grantTech', techId: 'asteroid_capture' },
  { kind: 'grantTech', techId: 'planetary_industry' },
  { kind: 'grantTech', techId: 'terraforming_engines' },
  { kind: 'grantTech', techId: 'dyson_swarm_prototype' },
  { kind: 'grantTech', techId: 'matrioshka_brain' },
  { kind: 'grantTech', techId: 'stellar_energy' },
  { kind: 'advance', seconds: 60 },
  { kind: 'grantCheat', resource: 'energy', amount: '1e120' },
  { kind: 'buy', techId: 'matrioshka_brain', quantity: 40 },
  { kind: 'buy', techId: 'planetary_industry', quantity: 25 },
  { kind: 'buy', techId: 'terraforming_engines', quantity: 15 },
  { kind: 'advance', seconds: 3600 },
  { kind: 'advance', seconds: 43200 },
  { kind: 'advance', seconds: 86400 },
  { kind: 'advance', seconds: 604800 },
  { kind: 'advance', seconds: 2592000 },
];

function simulationFixture() {
  const startMs = 1_700_000_000_000;
  let state = createNewGame(startMs);
  const noPrestige = computePrestigeMultipliers({});
  const steps: unknown[] = [snapshot(state, 'initial')];

  for (const [index, action] of SCRIPT.entries()) {
    if (action.kind === 'advance') {
      state = simulateStep(state, action.seconds, noPrestige).state;
      // The store fires milestones outside simulateStep; mirror that here so
      // the fixture records the same news feed a real session would build.
      const fired = checkMilestones(state);
      if (fired.length > 0) {
        state = {
          ...state,
          milestonesTriggered: { ...state.milestonesTriggered, ...Object.fromEntries(fired.map((m) => [m.id, true])) },
          newsFeed: [...fired.map((m) => ({ milestoneId: m.id, at: 0, runSeconds: 0 })).reverse(), ...state.newsFeed].slice(0, 40),
        };
      }
      const newAchievements = checkAchievements(
        { state, civLevel: computeCivLevel(state.techOwned), justCollapsed: false, justReset: false, lastRunDurationSeconds: null, previousRunDurationSeconds: null },
        state.achievementsUnlocked,
      );
      if (newAchievements.length > 0) {
        state = { ...state, achievementsUnlocked: { ...state.achievementsUnlocked, ...Object.fromEntries(newAchievements.map((a) => [a, true])) } };
      }
      steps.push(snapshot(state, `${index}:advance:${action.seconds}`));
    } else if (action.kind === 'buy') {
      const result = purchaseTechnology(state, action.techId, action.quantity, noPrestige);
      state = result.success
        ? {
            ...result.state,
            lifetimeStats: {
              ...result.state.lifetimeStats,
              totalTechnologiesPurchased: result.state.lifetimeStats.totalTechnologiesPurchased + result.purchasedQuantity,
            },
          }
        : result.state;
      const snap = snapshot(state, `${index}:buy:${action.techId}x${action.quantity}`) as Record<string, unknown>;
      snap.purchaseSucceeded = result.success;
      snap.purchasedQuantity = result.purchasedQuantity;
      steps.push(snap);
    } else if (action.kind === 'grantTech') {
      state = grantTechnology(state, action.techId);
      steps.push(snapshot(state, `${index}:grantTech:${action.techId}`));
    } else {
      state = { ...state, resources: { ...state.resources, [action.resource]: state.resources[action.resource].add(D(action.amount)) } };
      steps.push(snapshot(state, `${index}:grant:${action.resource}`));
    }
  }

  // Prestige: score the run, reset, and apply starting bonuses the way the store does.
  const runDurationSeconds = 259200;
  const earned = calculatePrestigeGain({
    totalGasProducedKg: state.runStats.totalGasProducedKg,
    peakForcingWm2: state.runStats.peakForcingWm2,
    civLevel: computeCivLevel(state.techOwned),
    runDurationSeconds,
  });
  let fresh = startNewRun(state, startMs + runDurationSeconds * 1000);
  fresh = {
    ...fresh,
    prestige: { ...fresh.prestige, earthPoints: fresh.prestige.earthPoints.add(earned), upgradesOwned: { eternal_flame: 3, head_start: 1, atmospheric_momentum: 2 } },
    lifetimeStats: {
      ...fresh.lifetimeStats,
      totalResets: fresh.lifetimeStats.totalResets + 1,
      fastestResetSeconds: runDurationSeconds,
      longestRunSeconds: runDurationSeconds,
      totalEarthPointsEarned: fresh.lifetimeStats.totalEarthPointsEarned.add(earned),
    },
  };
  const postPrestige = computePrestigeMultipliers(fresh.prestige.upgradesOwned);
  for (const techId of postPrestige.startingTechIds) fresh = grantTechnology(fresh, techId);
  for (const [techId, extra] of Object.entries(postPrestige.startingGenerators)) {
    fresh = { ...fresh, techOwned: { ...fresh.techOwned, [techId]: (fresh.techOwned[techId] ?? 0) + extra } };
  }
  for (const [resId, amount] of Object.entries(postPrestige.startingResources)) {
    fresh = { ...fresh, resources: { ...fresh.resources, [resId]: fresh.resources[resId as ResourceId].add(amount ?? 0) } };
  }
  const afterPrestige = snapshot(fresh, 'after-prestige') as Record<string, unknown>;
  afterPrestige.earthPointsEarned = dec(earned);
  afterPrestige.earthPointsBalance = dec(fresh.prestige.earthPoints);
  afterPrestige.runNumber = fresh.runNumber;
  steps.push(afterPrestige);

  // ...and the new run advances with the prestige bonuses applied.
  let bonusState = fresh;
  for (const seconds of [1, 600, 3600]) {
    bonusState = simulateStep(bonusState, seconds, postPrestige).state;
    steps.push(snapshot(bonusState, `post-prestige:advance:${seconds}`));
  }

  return { startMs, script: SCRIPT, steps };
}

/** Step-size independence: the property the analytic integration exists to provide. */
function stepIndependenceFixture() {
  const noPrestige = computePrestigeMultipliers({});
  const base = { ...createNewGame(0), techOwned: { natural_fire: 5, controlled_fire: 12, coal_mining: 30 }, resources: { ...createNewGame(0).resources } };

  const oneShot = simulateStep(base, 3600, noPrestige).state;

  let stepped = base;
  for (let i = 0; i < 14400; i++) stepped = simulateStep(stepped, 0.25, noPrestige).state;

  return {
    oneShot: {
      atmosphere: decMap(oneShot.atmosphere), resources: decMap(oneShot.resources),
      temperatureAnomalyC: oneShot.temperatureAnomalyC, gameAgeSeconds: oneShot.gameAgeSeconds,
    },
    stepped: {
      atmosphere: decMap(stepped.atmosphere), resources: decMap(stepped.resources),
      temperatureAnomalyC: stepped.temperatureAnomalyC, gameAgeSeconds: stepped.gameAgeSeconds,
    },
  };
}

// --------------------------------------------------------------- offline ---

function offlineFixture() {
  const cases: unknown[] = [];
  const noPrestige = computePrestigeMultipliers({});
  const extended = computePrestigeMultipliers({ extended_endurance: 3, automated_industry: 1 });

  for (const [name, prestige] of [['none', noPrestige], ['extended', extended]] as const) {
    for (const awaySeconds of [0, 5, 300, 3600, 43200, 43201, 86400, 604800]) {
      for (const enabled of [true, false]) {
        const base: GameState = {
          ...createNewGame(0),
          techOwned: { natural_fire: 8, controlled_fire: 20, coal_mining: 15 },
          lastTickAt: 0,
        };
        const result = computeOfflineProgress(base, awaySeconds * 1000, prestige, enabled);
        cases.push({
          prestige: name, awaySeconds, enabled,
          simulatedSeconds: result.simulatedSeconds,
          cappedByLimit: result.cappedByLimit,
          offlineCapSeconds: result.offlineCapSeconds,
          lastTickAt: result.state.lastTickAt,
          gameAgeSeconds: result.state.gameAgeSeconds,
          resources: decMap(result.state.resources),
          atmosphere: decMap(result.state.atmosphere),
          temperatureAnomalyC: result.state.temperatureAnomalyC,
          gasGeneratedKg: decMap(result.summary.gasGeneratedKg),
          resourcesGained: decMap(result.summary.resourcesGained),
          temperatureBeforeC: result.summary.temperatureBeforeC,
          temperatureAfterC: result.summary.temperatureAfterC,
        });
      }
    }
  }
  return { cases };
}

// ------------------------------------------------------------------ save ---

function saveFixture() {
  const state: GameState = {
    ...createNewGame(1_700_000_000_000),
    runNumber: 4,
    gameAgeSeconds: 4_567_890.5,
    techOwned: { natural_fire: 17, controlled_fire: 9, cooking: 1, coal_mining: 123 },
    resources: {
      energy: D('1.2345e42'), research: D('9.87e17'), coal: D('1000'),
      oil: Decimal.ZERO, steel: D('3.5'), concrete: D('1e-7'),
    },
    seaLevelRiseMeters: 12.75,
    previousTemperatureAnomalyC: 3.25,
    temperatureAnomalyC: 3.5,
    oceanPh: 7.92,
    prestige: { earthPoints: D('4.44e9'), upgradesOwned: { atmospheric_momentum: 3, head_start: 1 } },
    achievementsUnlocked: { first_spark: true, hot: true },
    challenges: { activeId: 'ice_age', completed: { low_carbon: true } },
    activeEvents: [{ id: 'e1', eventDefId: 'good_harvest', startedAt: 5, endsAt: 95_000 }],
    milestonesTriggered: { first_smoke: true, co2_300: true },
    newsFeed: [{ milestoneId: 'co2_300', at: 1_700_000_100_000, runSeconds: 100 }, { milestoneId: 'first_smoke', at: 1_700_000_050_000, runSeconds: 50 }],
    tutorial: { step: 5, completed: false, skipped: false },
    collapsed: false,
  };
  state.atmosphere.co2 = D('412.5');
  state.atmosphere.ch4 = D('1.5e3');
  state.runStats.totalGasProducedKg.co2 = D('6.66e22');
  state.lifetimeStats.totalGasProducedKg.co2 = D('9.99e33');
  state.lifetimeStats.totalEarthPointsEarned = D('1e11');
  state.lifetimeStats.fastestResetSeconds = 12345.5;
  state.lifetimeStats.totalResets = 4;
  state.lifetimeStats.totalSimulatedSeconds = 9_876_543.25;
  state.settings = { ...state.settings, numberFormat: 'scientific', darkMode: 'dark', vibrationEnabled: false };

  const serialized = serializeGameState(state);

  // A v1 save (pre-Natural-Fire, with the removed Tap Conditioning upgrade)
  // and a v2 save, so the Kotlin migration chain is checked against the same
  // inputs the TypeScript one is.
  const v1 = JSON.parse(serialized);
  v1.saveVersion = 1;
  delete v1.techOwned.natural_fire;
  v1.prestige.upgradesOwned = { tap_conditioning: 4, atmospheric_momentum: 3 };
  delete v1.milestonesTriggered;
  delete v1.newsFeed;
  const v1Json = JSON.stringify(v1);

  const v2 = JSON.parse(serialized);
  v2.saveVersion = 2;
  const v2Json = JSON.stringify(v2);

  // A v3 save: written before the Earth had an age, so the field is absent
  // entirely rather than zero. `totalPlayTimeSeconds` is present and is what
  // the lifetime simulated total is derived from.
  const v3 = JSON.parse(serialized);
  v3.saveVersion = 3;
  delete v3.gameAgeSeconds;
  delete v3.lifetimeStats.totalSimulatedSeconds;
  v3.lifetimeStats.totalPlayTimeSeconds = 7200;
  const v3Json = JSON.stringify(v3);

  const roundTripped = deserializeGameState(serialized)!;
  const migratedV1 = deserializeGameState(v1Json)!;
  const migratedV2 = deserializeGameState(v2Json)!;
  const migratedV3 = deserializeGameState(v3Json)!;

  return {
    v4Json: serialized,
    v1Json,
    v2Json,
    v3Json,
    roundTripped: {
      saveVersion: roundTripped.saveVersion,
      resources: decMap(roundTripped.resources),
      atmosphere: decMap(roundTripped.atmosphere),
      techOwned: roundTripped.techOwned,
      earthPoints: dec(roundTripped.prestige.earthPoints),
      settings: roundTripped.settings,
      newsFeed: roundTripped.newsFeed,
      activeEvents: roundTripped.activeEvents,
      tutorial: roundTripped.tutorial,
      seaLevelRiseMeters: roundTripped.seaLevelRiseMeters,
      gameAgeSeconds: roundTripped.gameAgeSeconds,
      lifetimeTotalSimulatedSeconds: roundTripped.lifetimeStats.totalSimulatedSeconds,
      lifetimeFastestResetSeconds: roundTripped.lifetimeStats.fastestResetSeconds,
    },
    migratedFromV1: {
      saveVersion: migratedV1.saveVersion,
      naturalFireOwned: migratedV1.techOwned.natural_fire,
      upgradesOwned: migratedV1.prestige.upgradesOwned,
      earthPoints: dec(migratedV1.prestige.earthPoints),
      totalEarthPointsEarned: dec(migratedV1.lifetimeStats.totalEarthPointsEarned),
      milestonesTriggered: migratedV1.milestonesTriggered,
      newsFeed: migratedV1.newsFeed,
    },
    migratedFromV2: {
      saveVersion: migratedV2.saveVersion,
      earthPoints: dec(migratedV2.prestige.earthPoints),
      totalEarthPointsEarned: dec(migratedV2.lifetimeStats.totalEarthPointsEarned),
      gameAgeSeconds: migratedV2.gameAgeSeconds,
      totalSimulatedSeconds: migratedV2.lifetimeStats.totalSimulatedSeconds,
    },
    migratedFromV3: {
      saveVersion: migratedV3.saveVersion,
      // No honest reconstruction exists for the current Earth, so it starts at zero...
      gameAgeSeconds: migratedV3.gameAgeSeconds,
      // ...while the lifetime total is derived exactly from simulated play time.
      totalSimulatedSeconds: migratedV3.lifetimeStats.totalSimulatedSeconds,
      totalPlayTimeSeconds: migratedV3.lifetimeStats.totalPlayTimeSeconds,
      // Untouched by this migration: v3's economy fix-ups must not run again.
      earthPoints: dec(migratedV3.prestige.earthPoints),
    },
    corrupt: ['', '{', 'null', '[]', '{"saveVersion":4}', '{"saveVersion":"x","runNumber":0,"resources":{},"atmosphere":{},"techOwned":{},"prestige":{}}'],
  };
}

// ------------------------------------------------------------------ main ---

write('decimal.json', decimalFixture());
write('format.json', formatFixture());
write('technologies.json', technologyFixture());
write('economy.json', economyFixture());
write('climate.json', climateFixture());
write('prestige.json', prestigeFixture());
write('content.json', contentFixture());
write('events.json', eventRollFixture());
write('simulation.json', simulationFixture());
write('stepIndependence.json', stepIndependenceFixture());
write('offline.json', offlineFixture());
write('save.json', saveFixture());
console.log('\nParity fixtures written to', OUT_DIR);
