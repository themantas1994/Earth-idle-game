import { describe, it, expect } from 'vitest';
import { D } from './bignum';
import { Technology } from './technologies/types';
import { maxAffordableQuantity, bulkPurchaseCost, getTechnology } from './technologies';
import { purchaseTechnology } from './economy';
import { createNewGame } from './gameState';
import type { PrestigeMultipliers } from './prestige';

function generator(overrides: Partial<Technology> = {}): Technology {
  return {
    id: 'test_gen',
    name: 'Test Generator',
    branch: 'industry',
    kind: 'generator',
    tier: 1,
    description: '',
    icon: '⚙️',
    requires: [],
    cost: [{ resource: 'energy', baseAmount: 10 }],
    costGrowth: 1.15,
    maxOwned: Infinity,
    effect: { gasProductionPerUnit: { co2: 1 } },
    ...overrides,
  };
}

/**
 * The closed-form solver inverts a geometric series through a logarithm, so
 * exact-boundary balances are precisely where floating point can hand back
 * n+1. That over-count used to be charged against the wallet and clamped at
 * zero — i.e. a free unit — so it is worth pinning down directly.
 */
describe('maxAffordableQuantity boundary behaviour', () => {
  it('never reports a quantity the balance cannot actually pay for', () => {
    const tech = generator();
    for (let owned = 0; owned < 40; owned++) {
      for (let n = 1; n <= 25; n++) {
        const exact = bulkPurchaseCost(tech, owned, n)[0].amount;
        for (const wallet of [exact, exact.mul(0.999999999), exact.mul(1.000000001)]) {
          const quantity = maxAffordableQuantity(tech, owned, { energy: wallet }, 1_000_000);
          if (quantity > 0) {
            const charged = bulkPurchaseCost(tech, owned, quantity)[0].amount;
            expect(charged.lte(wallet)).toBe(true);
          }
        }
      }
    }
  });

  it('buys exactly n units when the balance is exactly the cost of n units', () => {
    const tech = generator();
    for (let n = 1; n <= 30; n++) {
      const exact = bulkPurchaseCost(tech, 0, n)[0].amount;
      expect(maxAffordableQuantity(tech, 0, { energy: exact }, 1_000_000)).toBe(n);
    }
  });

  it('leaves nothing affordable on the table for buy-max', () => {
    const tech = generator();
    const wallet = D('1e12');
    const quantity = maxAffordableQuantity(tech, 0, { energy: wallet }, Number.MAX_SAFE_INTEGER);
    expect(bulkPurchaseCost(tech, 0, quantity)[0].amount.lte(wallet)).toBe(true);
    expect(bulkPurchaseCost(tech, 0, quantity + 1)[0].amount.gt(wallet)).toBe(true);
  });

  it('handles a flat (growth === 1) cost curve', () => {
    const tech = generator({ costGrowth: 1 });
    expect(maxAffordableQuantity(tech, 0, { energy: D(100) }, 1_000)).toBe(10);
    expect(maxAffordableQuantity(tech, 0, { energy: D(99) }, 1_000)).toBe(9);
  });

  it('respects a multi-resource cost, limited by the scarcest resource', () => {
    const tech = generator({
      cost: [
        { resource: 'energy', baseAmount: 10 },
        { resource: 'steel', baseAmount: 5 },
      ],
      costGrowth: 1,
    });
    const quantity = maxAffordableQuantity(tech, 0, { energy: D(1000), steel: D(20) }, 1_000);
    expect(quantity).toBe(4);
  });
});

describe('purchaseTechnology never overdraws a resource', () => {
  const noPrestige: PrestigeMultipliers = {
    global: 1,
    research: 1,
    allGas: 1,
    perGas: {},
    startingGenerators: {},
    offlineCapMultiplier: 1,
    techCostDiscount: 0,
    complexityReduction: 0,
    startingTechIds: [],
    startingResources: {},
    unlocksAutoBuyMax: false,
  };

  it('buy-max against a real registry generator spends at most the wallet', () => {
    const tech = getTechnology('controlled_fire');

    for (let n = 1; n <= 20; n++) {
      const wallet = bulkPurchaseCost(tech, 0, n)[0].amount;
      const state = createNewGame(0);
      state.resources.energy = wallet;

      const result = purchaseTechnology(state, 'controlled_fire', Number.MAX_SAFE_INTEGER, noPrestige);

      expect(result.success).toBe(true);
      // Exactly-affordable wallets must buy exactly n, not n-1 (money left on
      // the table) and not n+1 (a unit paid for by the clamp-at-zero).
      expect(result.purchasedQuantity).toBe(n);
      expect(result.state.resources.energy.gte(0)).toBe(true);

      const spent = wallet.sub(result.state.resources.energy);
      expect(spent.lte(wallet)).toBe(true);
    }
  });

  it('refuses a purchase that the wallet cannot cover at all', () => {
    const state = createNewGame(0);
    state.resources.energy = D(0);
    const result = purchaseTechnology(state, 'controlled_fire', 1, noPrestige);
    expect(result.success).toBe(false);
    expect(result.state.techOwned.controlled_fire ?? 0).toBe(0);
  });
});
