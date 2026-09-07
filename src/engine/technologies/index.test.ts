import { describe, it, expect } from 'vitest';
import { Decimal, D } from '../bignum';
import {
  ALL_TECHNOLOGIES,
  TECH_BY_ID,
  requirementsMet,
  isTechAvailable,
  nextPurchaseCost,
  bulkPurchaseCost,
  maxAffordableQuantity,
} from './index';

describe('technology data integrity', () => {
  it('has unique ids', () => {
    const ids = ALL_TECHNOLOGIES.map((t) => t.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('has no dangling requirement references', () => {
    for (const tech of ALL_TECHNOLOGIES) {
      for (const req of tech.requires) {
        expect(TECH_BY_ID[req], `${tech.id} requires unknown tech "${req}"`).toBeDefined();
      }
    }
  });

  it('has no requirement cycles (every prerequisite chain terminates)', () => {
    const visit = (id: string, stack: Set<string>) => {
      expect(stack.has(id), `cycle detected involving ${id}`).toBe(false);
      const tech = TECH_BY_ID[id];
      const nextStack = new Set(stack).add(id);
      for (const req of tech.requires) visit(req, nextStack);
    };
    for (const tech of ALL_TECHNOLOGIES) visit(tech.id, new Set());
  });

  it('every non-root technology requires at least one prerequisite', () => {
    const roots = ALL_TECHNOLOGIES.filter((t) => t.requires.length === 0);
    // Exactly one true starting point in this game's design: Natural Fire.
    expect(roots.map((t) => t.id)).toEqual(['natural_fire']);
  });

  it('generators have maxOwned Infinity and one-time techs have maxOwned 1', () => {
    for (const tech of ALL_TECHNOLOGIES) {
      if (tech.kind === 'generator') expect(tech.maxOwned).toBe(Infinity);
      else expect(tech.maxOwned).toBe(1);
    }
  });

  it('every choice-group has at least two mutually exclusive members', () => {
    const groups = new Map<string, number>();
    for (const tech of ALL_TECHNOLOGIES) {
      if (tech.choiceGroup) groups.set(tech.choiceGroup, (groups.get(tech.choiceGroup) ?? 0) + 1);
    }
    for (const [group, count] of groups) {
      expect(count, `choice group "${group}" has fewer than 2 options`).toBeGreaterThanOrEqual(2);
    }
  });
});

describe('requirementsMet / isTechAvailable', () => {
  it('the root tech is always available', () => {
    const root = TECH_BY_ID['natural_fire'];
    expect(isTechAvailable(root, {})).toBe(true);
  });

  it('a dependent tech is unavailable until its prerequisite is owned', () => {
    const fire = TECH_BY_ID['controlled_fire'];
    expect(isTechAvailable(fire, {})).toBe(false);
    expect(isTechAvailable(fire, { natural_fire: 1 })).toBe(true);
  });

  it('choice-group siblings become unavailable once one is taken', () => {
    const coal = TECH_BY_ID['coal_industrialization'];
    const nuclear = TECH_BY_ID['nuclear_industrialization'];
    const owned = { coal_power_plant: 1, coal_industrialization: 1 };
    expect(isTechAvailable(coal, owned)).toBe(false); // already owned, maxOwned 1
    expect(isTechAvailable(nuclear, owned)).toBe(false); // rival taken
  });

  it('a maxed-out one-time tech is no longer available', () => {
    const tech = TECH_BY_ID['cooking'];
    expect(isTechAvailable(tech, { controlled_fire: 1, cooking: 1 })).toBe(false);
  });
});

describe('nextPurchaseCost / bulkPurchaseCost', () => {
  it('one-time techs always cost their base amount regardless of "owned"', () => {
    const tech = TECH_BY_ID['grid_electricity'];
    const cost0 = nextPurchaseCost(tech, 0);
    const cost5 = nextPurchaseCost(tech, 5); // shouldn't normally happen (maxOwned=1) but must stay stable
    expect(cost0[0].amount.eq(cost5[0].amount)).toBe(true);
  });

  it('generator costs grow geometrically with owned count', () => {
    const tech = TECH_BY_ID['controlled_fire'];
    const cost0 = nextPurchaseCost(tech, 0)[0].amount;
    const cost1 = nextPurchaseCost(tech, 1)[0].amount;
    expect(cost1.div(cost0).toNumber()).toBeCloseTo(tech.costGrowth, 5);
  });

  it('bulk cost of quantity=1 matches nextPurchaseCost', () => {
    const tech = TECH_BY_ID['coal_mining'];
    const single = nextPurchaseCost(tech, 3)[0].amount;
    const bulk = bulkPurchaseCost(tech, 3, 1)[0].amount;
    expect(bulk.eq(single)).toBe(true);
  });

  it('bulk cost equals the sum of sequential individual purchases', () => {
    const tech = TECH_BY_ID['coal_mining'];
    let owned = 0;
    let sequentialTotal = Decimal.ZERO;
    for (let i = 0; i < 10; i++) {
      sequentialTotal = sequentialTotal.add(nextPurchaseCost(tech, owned)[0].amount);
      owned++;
    }
    const bulkTotal = bulkPurchaseCost(tech, 0, 10)[0].amount;
    expect(bulkTotal.toNumber()).toBeCloseTo(sequentialTotal.toNumber(), 2);
  });
});

describe('maxAffordableQuantity', () => {
  it('returns 0 when the player cannot afford even one unit', () => {
    const tech = TECH_BY_ID['coal_mining'];
    const affordable = maxAffordableQuantity(tech, 0, { energy: D(0) }, Infinity);
    expect(affordable).toBe(0);
  });

  it('returns 1 for a one-time tech when affordable', () => {
    const tech = TECH_BY_ID['grid_electricity'];
    const cost = nextPurchaseCost(tech, 0)[0].amount;
    const affordable = maxAffordableQuantity(tech, 0, { energy: cost.mul(2) }, Infinity);
    expect(affordable).toBe(1);
  });

  it('computes the correct affordable count for a generator, matching bulk cost math', () => {
    const tech = TECH_BY_ID['coal_mining'];
    // A tiny epsilon above the exact cost avoids floating-point boundary flakiness.
    const wallet = bulkPurchaseCost(tech, 0, 25)[0].amount.mul(1.0000001);
    const affordable = maxAffordableQuantity(tech, 0, { energy: wallet }, Infinity);
    expect(affordable).toBeGreaterThanOrEqual(25);
    // One unit more should be unaffordable (or only marginally due to rounding).
    const costForOneMore = bulkPurchaseCost(tech, 0, affordable + 1)[0].amount;
    expect(costForOneMore.gt(wallet)).toBe(true);
  });

  it('is capped by maxQuantity even with unlimited resources', () => {
    const tech = TECH_BY_ID['coal_mining'];
    const affordable = maxAffordableQuantity(tech, 0, { energy: D('1e300') }, 7);
    expect(affordable).toBe(7);
  });
});
