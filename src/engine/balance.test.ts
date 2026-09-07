import { describe, it, expect } from 'vitest';
import { BALANCE } from './constants';
import { createNewGame } from './gameState';
import { computePrestigeMultipliers } from './prestige';
import { complexityCostMultiplier, purchaseTechnology } from './economy';
import { ALL_TECHNOLOGIES, TECH_BY_ID, nextPurchaseCost } from './technologies';
import { ladderTier, generatorBaseCost, gasProduction, resourceProduction } from './technologies/scaling';
import { simulateStep } from './simulation';
import { D } from './bignum';

const noPrestige = computePrestigeMultipliers({});

/**
 * These are guardrails on the pacing model rather than on any one number:
 * the constants are meant to be retuned (with `scripts/balanceSim.ts`), but
 * the *relationships* between them are what keep a run from collapsing back
 * into the half-hour blowout this balance pass was written to fix.
 */
describe('pacing invariants', () => {
  it('prices tiers faster than they produce, so the economy cannot run away', () => {
    expect(BALANCE.generatorCostGrowthPerTier).toBeGreaterThan(BALANCE.productionGrowthPerTier);
  });

  it('emits gas more slowly per tier than it earns, so the endgame is reachable before collapse', () => {
    expect(BALANCE.gasProductionGrowthPerTier).toBeLessThan(BALANCE.productionGrowthPerTier);
  });

  it('keeps research costs under the generator cost curve, so research paces but never blocks', () => {
    expect(BALANCE.researchCostGrowthPerTier).toBeLessThan(BALANCE.generatorCostGrowthPerTier);
  });
});

describe('ladderTier', () => {
  it('is the identity through the broad part of the tree', () => {
    for (let t = 0; t <= BALANCE.flattenLadderFromTier; t++) expect(ladderTier(t)).toBe(t);
  });

  it('compresses above the knee, but never inverts the ladder', () => {
    const knee = BALANCE.flattenLadderFromTier;
    expect(ladderTier(knee + 10)).toBeLessThan(knee + 10);
    expect(ladderTier(knee + 10)).toBeGreaterThan(ladderTier(knee + 5));
  });

  it('compresses cost and output together, so flattening prices cannot hand back a runaway', () => {
    const knee = BALANCE.flattenLadderFromTier;
    const costRatio = generatorBaseCost(knee + 12) / generatorBaseCost(knee);
    const outputRatio = resourceProduction(knee + 12) / resourceProduction(knee);
    // Costs still outrun output above the knee — just less steeply than below it.
    expect(costRatio).toBeGreaterThan(outputRatio);
  });
});

describe('complexityCostMultiplier', () => {
  it('is exactly 1 on a fresh Earth, which owns only its free starting fire', () => {
    expect(complexityCostMultiplier(createNewGame(0).techOwned)).toBeCloseTo(1, 10);
  });

  it('charges for breadth — each new distinct technology raises it', () => {
    const one = complexityCostMultiplier({ natural_fire: 1 });
    const three = complexityCostMultiplier({ natural_fire: 1, controlled_fire: 1, cooking: 1 });
    expect(three).toBeGreaterThan(one);
    expect(three).toBeCloseTo(Math.pow(BALANCE.complexityCostGrowth, 2), 10);
  });

  it('never charges for depth — extra units of what you already own are free of it', () => {
    expect(complexityCostMultiplier({ natural_fire: 500 })).toBeCloseTo(1, 10);
  });

  it('is reduced, and floored, by the prestige reduction', () => {
    const owned = { natural_fire: 1, controlled_fire: 1, cooking: 1, charcoal: 1, pottery: 1 };
    expect(complexityCostMultiplier(owned, 0.5)).toBeLessThan(complexityCostMultiplier(owned));
    // Capped at 0.75 — a full cancellation is never on offer.
    expect(complexityCostMultiplier(owned, 5)).toBeCloseTo(complexityCostMultiplier(owned, 0.75), 10);
  });

  it('is actually charged: the same technology costs more once the tree is broad', () => {
    const tech = TECH_BY_ID['controlled_fire'];
    const nominal = nextPurchaseCost(tech, 0)[0].amount;

    const broad = {
      ...createNewGame(0),
      techOwned: Object.fromEntries(ALL_TECHNOLOGIES.slice(0, 20).map((t) => [t.id, 1])),
      resources: { ...createNewGame(0).resources, energy: nominal },
    };
    // Enough for the sticker price, but not for the sticker price plus drag.
    expect(purchaseTechnology(broad, 'controlled_fire', 1, noPrestige).success).toBe(false);

    const narrow = { ...createNewGame(0), resources: { ...createNewGame(0).resources, energy: nominal } };
    expect(purchaseTechnology(narrow, 'controlled_fire', 1, noPrestige).success).toBe(true);
  });
});

describe('the opening of a run', () => {
  it('earns Energy from the very first tick with no player input at all', () => {
    const state = createNewGame(0);
    const { state: after } = simulateStep(state, 60, noPrestige);
    expect(after.resources.energy.gt(0)).toBe(true);
  });

  it('reaches its first purchase in minutes, not hours', () => {
    let state = createNewGame(0);
    let purchased = false;
    for (let t = 0; t < 30 * 60 && !purchased; t += 10) {
      state = simulateStep(state, 10, noPrestige).state;
      const result = purchaseTechnology(state, 'natural_fire', 1, noPrestige);
      if (result.success) {
        state = result.state;
        purchased = true;
      }
    }
    expect(purchased).toBe(true);
  });

  it('does not heat the planet measurably in its first hour — a campfire is only a campfire', () => {
    const state = createNewGame(0);
    const { state: after } = simulateStep(state, 3600, noPrestige);
    expect(after.temperatureAnomalyC).toBeLessThan(0.01);
  });
});

describe('the shape of the tree', () => {
  it('gives the late game gas output that still grows, so a finished tree can still kill the planet', () => {
    const knee = BALANCE.flattenLadderFromTier;
    expect(gasProduction(knee + 20)).toBeGreaterThan(gasProduction(knee + 10));
  });

  it('sells no generator on the Technology screen — every repeatable purchase is a Production one', () => {
    const generators = ALL_TECHNOLOGIES.filter((t) => t.kind === 'generator');
    expect(generators.length).toBeGreaterThan(30);
    for (const g of generators) expect(g.maxOwned).toBe(Infinity);
  });

  it('keeps every one-time node genuinely one-time', () => {
    for (const t of ALL_TECHNOLOGIES) {
      if (t.kind !== 'generator') expect(t.maxOwned).toBe(1);
    }
  });

  it('makes the whole tree reachable — every technology has a path back to the starting fire', () => {
    const owned: Record<string, number> = { natural_fire: 1 };
    // Repeatedly grant anything whose requirements are already satisfied.
    for (let pass = 0; pass < ALL_TECHNOLOGIES.length; pass++) {
      for (const tech of ALL_TECHNOLOGIES) {
        if (tech.requires.every((r) => (owned[r] ?? 0) > 0)) owned[tech.id] = 1;
      }
    }
    const unreachable = ALL_TECHNOLOGIES.filter((t) => !owned[t.id]);
    expect(unreachable.map((t) => t.id)).toEqual([]);
  });
});

describe('a fresh run can afford its own first steps', () => {
  it('prices the first generator within reach of the starting fire\'s output', () => {
    const state = createNewGame(0);
    const perSecond = simulateStep(state, 1, noPrestige).productionRates.resourcePerS.energy;
    const firstCost = nextPurchaseCost(TECH_BY_ID['natural_fire'], 1)[0].amount;
    // Under ten minutes of the opening income.
    expect(firstCost.div(perSecond).lte(D(600))).toBe(true);
  });
});
