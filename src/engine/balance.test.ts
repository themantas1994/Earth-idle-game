import { describe, it, expect } from 'vitest';
import { BALANCE } from './constants';
import { createNewGame } from './gameState';
import { computePrestigeMultipliers } from './prestige';
import { purchaseTechnology, effectiveCostAmount } from './economy';
import { ownershipMultiplier, nextOwnershipMilestone, ownershipMilestonesCrossed } from './ownership';
import { OWNERSHIP_BONUS } from './constants';
import { ALL_TECHNOLOGIES, TECH_BY_ID, Technology, nextPurchaseCost } from './technologies';
import { ladderTier, generatorBaseCost, gasProduction, resourceProduction } from './technologies/scaling';
import { simulateStep } from './simulation';
import { D } from './bignum';

const noPrestige = computePrestigeMultipliers({});

/**
 * What `purchaseTechnology` actually deducts for one unit of `tech`, played
 * out for real against a wallet stocked from a whole world of other owned
 * technologies. Going through the live purchase path (rather than re-deriving
 * the price) is the point: a regression that reintroduced a world-dependent
 * surcharge would show up here as a different number, not just as a different
 * formula.
 */
function priceCharged(tech: Technology, ownedOfThis: number, world: Record<string, number>): number[] {
  const funded = { ...createNewGame(0) };
  funded.techOwned = { ...world, ...requirementsOf(tech), [tech.id]: ownedOfThis };
  funded.resources = { ...funded.resources };
  // Stock the wallet far beyond any plausible price so the purchase is never
  // the thing under test — only the amount it takes.
  for (const c of tech.cost) funded.resources[c.resource] = D(c.baseAmount).mul('1e6');

  const result = purchaseTechnology(funded, tech.id, 1, noPrestige);
  expect(result.success).toBe(true);
  return tech.cost.map((c) => funded.resources[c.resource].sub(result.state.resources[c.resource]).toNumber());
}

/**
 * Prices span 1 to 1e9 across the tree and round-trip through `Decimal`'s
 * limited mantissa, so an exact-digits comparison would be testing float
 * formatting rather than pricing. A part-per-billion relative tolerance is
 * many orders of magnitude tighter than any surcharge could hide in.
 */
function expectSamePrice(actual: number, expected: number): void {
  expect(Math.abs(actual - expected) / Math.max(1, Math.abs(expected))).toBeLessThan(1e-9);
}

/**
 * `world` minus anything that would make `tech` unpurchasable for a reason
 * other than price — namely a rival in its mutually-exclusive choice group.
 */
function withoutRivalsOf(tech: Technology, world: Record<string, number>): Record<string, number> {
  const trimmed = { ...world, [tech.id]: 0 };
  if (tech.choiceGroup) {
    for (const other of ALL_TECHNOLOGIES) {
      if (other.choiceGroup === tech.choiceGroup) trimmed[other.id] = 0;
    }
  }
  return trimmed;
}

/** Every prerequisite of `tech`, owned, so availability never masks a pricing difference. */
function requirementsOf(tech: Technology): Record<string, number> {
  return Object.fromEntries(tech.requires.map((id) => [id, 1]));
}

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

/**
 * The promise the whole economy is built on: a price you have been quoted is
 * the price you pay, whenever you come back for it. Nothing about the rest of
 * the civilization — how broad the tree has grown, what else is owned, how
 * long the run has gone on — may ever revise a number upward.
 */
describe('prices never rise except from your own purchases', () => {
  it('charges the same for every technology from an empty world and a fully-built one', () => {
    const empty: Record<string, number> = {};
    // A civilization that owns forty of everything — the state under which the
    // old complexity surcharge inflated every price by several orders of
    // magnitude.
    const sprawling = Object.fromEntries(ALL_TECHNOLOGIES.map((t) => [t.id, 40]));

    for (const tech of ALL_TECHNOLOGIES) {
      // Held fixed at zero owned *of this technology* in both worlds, so the
      // only difference between them is everything else the player owns.
      const fromEmpty = priceCharged(tech, 0, empty);
      const fromSprawling = priceCharged(tech, 0, withoutRivalsOf(tech, sprawling));
      for (const [i, charged] of fromSprawling.entries()) expectSamePrice(charged, fromEmpty[i]);
      // And it is exactly the base amount the technology was authored with.
      for (const [i, charged] of fromEmpty.entries()) expectSamePrice(charged, tech.cost[i].baseAmount);
    }
  });

  it('re-quotes a one-time node at its original price for the whole run', () => {
    for (const tech of ALL_TECHNOLOGIES) {
      if (tech.maxOwned !== 1) continue;
      // maxOwned === 1 nodes carry no per-unit growth at all: the price is
      // frozen the moment the node is authored.
      expect(tech.costGrowth).toBe(1);
      const charged = priceCharged(tech, 0, {});
      for (const [i, amount] of charged.entries()) expectSamePrice(amount, tech.cost[i].baseAmount);
    }
  });

  it("charges a generator's first unit exactly its listed base price", () => {
    for (const tech of ALL_TECHNOLOGIES) {
      if (tech.kind !== 'generator') continue;
      for (const [i, c] of nextPurchaseCost(tech, 0).entries()) {
        expectSamePrice(c.amount.toNumber(), tech.cost[i].baseAmount);
      }
    }
  });

  it('makes the same purchase cost the same from a narrow tree and a broad one', () => {
    const tech = TECH_BY_ID['controlled_fire'];
    const sticker = nextPurchaseCost(tech, 0)[0].amount;

    const narrow = { ...createNewGame(0), resources: { ...createNewGame(0).resources, energy: sticker } };
    expect(purchaseTechnology(narrow, 'controlled_fire', 1, noPrestige).success).toBe(true);

    // Exactly the sticker price, from a civilization that owns twenty other
    // technologies. This used to fail: breadth was charged as a surcharge.
    const broad = {
      ...createNewGame(0),
      techOwned: { ...Object.fromEntries(ALL_TECHNOLOGIES.slice(0, 20).map((t) => [t.id, 1])), controlled_fire: 0 },
      resources: { ...createNewGame(0).resources, energy: sticker },
    };
    expect(purchaseTechnology(broad, 'controlled_fire', 1, noPrestige).success).toBe(true);
  });

  it('only ever lets the prestige discount move a price, and only downward', () => {
    const nominal = D(1000);
    expect(effectiveCostAmount(nominal, 0).toNumber()).toBe(1000);
    expect(effectiveCostAmount(nominal, 0.25).toNumber()).toBeCloseTo(750, 6);
    // Even an absurd discount is floored rather than inverted.
    expect(effectiveCostAmount(nominal, 5).toNumber()).toBeGreaterThan(0);
    expect(effectiveCostAmount(nominal, 5).lte(nominal)).toBe(true);
  });
});

describe('ownership bonuses', () => {
  it('grants nothing until the first threshold', () => {
    for (let owned = 0; owned < OWNERSHIP_BONUS.everyUnits; owned++) {
      expect(ownershipMultiplier(owned)).toBe(1);
    }
    expect(ownershipMultiplier(OWNERSHIP_BONUS.everyUnits)).toBe(OWNERSHIP_BONUS.multiplier);
  });

  it('doubles again on every subsequent threshold', () => {
    const step = OWNERSHIP_BONUS.everyUnits;
    for (const n of [2, 3, 7, 12]) {
      expect(ownershipMultiplier(n * step)).toBe(Math.pow(OWNERSHIP_BONUS.multiplier, n));
      // ...and holds flat until the next one actually lands.
      expect(ownershipMultiplier(n * step + step - 1)).toBe(Math.pow(OWNERSHIP_BONUS.multiplier, n));
    }
  });

  it('never lets the bonus outrun the price it is paid for', () => {
    // Over one threshold span, a generator's unit price grows by
    // unitCostGrowth^everyUnits. If a single doubling ever exceeded that,
    // buying depth would strictly dominate and the tech tree would be
    // decoration.
    const priceGrowthPerSpan = Math.pow(BALANCE.unitCostGrowth, OWNERSHIP_BONUS.everyUnits);
    expect(OWNERSHIP_BONUS.multiplier).toBeLessThan(priceGrowthPerSpan);
  });

  it('always names a threshold ahead of where you are', () => {
    for (const owned of [0, 9, 10, 24, 25, 26, 99, 100, 4321]) {
      expect(nextOwnershipMilestone(owned)).toBeGreaterThan(owned);
    }
  });

  it('reports every threshold a single bulk purchase crossed', () => {
    const step = OWNERSHIP_BONUS.everyUnits;
    expect(ownershipMilestonesCrossed(0, step - 1)).toEqual([]);
    expect(ownershipMilestonesCrossed(0, step)).toEqual([step]);
    expect(ownershipMilestonesCrossed(0, 3 * step)).toEqual([step, 2 * step, 3 * step]);
    expect(ownershipMilestonesCrossed(step, 2 * step)).toEqual([2 * step]);
    expect(ownershipMilestonesCrossed(50, 50)).toEqual([]);
    // A "buy max" that lands thousands of units still terminates promptly.
    expect(ownershipMilestonesCrossed(0, 10_000).length).toBe(10_000 / step);
  });
});

describe('the opening of a run', () => {
  it('earns Energy from the very first tick with no player input at all', () => {
    const state = createNewGame(0);
    const { state: after } = simulateStep(state, 60, noPrestige);
    expect(after.resources.energy.gt(0)).toBe(true);
  });

  it('reaches its first purchase within a minute — the opening hook cannot be a wait', () => {
    let state = createNewGame(0);
    let purchased = false;
    for (let t = 0; t < 60 && !purchased; t += 1) {
      state = simulateStep(state, 1, noPrestige).state;
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
    // Under a minute of the opening income.
    expect(firstCost.div(perSecond).lte(D(60))).toBe(true);
  });
});
