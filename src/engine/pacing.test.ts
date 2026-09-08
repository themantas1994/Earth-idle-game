import { describe, it, expect } from 'vitest';
import { createNewGame } from './gameState';
import { computePrestigeMultipliers } from './prestige';
import { purchaseTechnology } from './economy';
import { ALL_TECHNOLOGIES, isTechAvailable } from './technologies';
import { simulateStep } from './simulation';
import { ownershipMultiplier } from './ownership';

/**
 * A compressed headless playthrough, run as a test.
 *
 * `scripts/balanceSim.ts` is the tool the curve constants were actually tuned
 * with, and it reports far more than this does — but nothing runs it in CI, so
 * a retune that quietly reintroduced a half-hour game or an endgame priced out
 * of reach would ship unnoticed. This locks in only the outcomes that would
 * make the game a different (and worse) game, with wide enough bounds that
 * ordinary rebalancing passes straight through.
 */
const noPrestige = computePrestigeMultipliers({});

const DT = 60;
const SPEND_EVERY = 1800;
const MAX_SECONDS = 12 * 24 * 3600;

interface RunOutcome {
  collapsedAtSeconds: number | null;
  neverBought: string[];
  firstPurchaseAtSeconds: number | null;
  deepestStack: number;
}

/**
 * Plays with the same "chase new things first, then deepen" policy the balance
 * script uses, which is what an engaged player does and what the curves were
 * tuned against.
 */
function playThrough(): RunOutcome {
  let state = createNewGame(0);
  const bought = new Set<string>(Object.keys(state.techOwned));
  let firstPurchaseAtSeconds: number | null = null;

  for (let t = 0; t < MAX_SECONDS; t += DT) {
    if (t % SPEND_EVERY === 0) {
      for (let guard = 0; guard < 4000; guard++) {
        const available = ALL_TECHNOLOGIES.filter((tech) => isTechAvailable(tech, state.techOwned));
        const frontier = available.filter((tech) => (state.techOwned[tech.id] ?? 0) === 0).sort((a, b) => a.tier - b.tier);
        const deepen = [...available].sort((a, b) => b.tier - a.tier);

        let didBuy = false;
        for (const tech of [...frontier, ...deepen]) {
          const result = purchaseTechnology(state, tech.id, 1, noPrestige);
          if (!result.success) continue;
          state = result.state;
          bought.add(tech.id);
          if (firstPurchaseAtSeconds === null) firstPurchaseAtSeconds = t;
          didBuy = true;
          break;
        }
        if (!didBuy) break;
      }
    }

    state = simulateStep(state, DT, noPrestige).state;
    if (state.collapsed) {
      return {
        collapsedAtSeconds: t,
        neverBought: ALL_TECHNOLOGIES.filter((tech) => !bought.has(tech.id)).map((tech) => tech.id),
        firstPurchaseAtSeconds,
        deepestStack: Math.max(...Object.values(state.techOwned)),
      };
    }
  }

  return {
    collapsedAtSeconds: null,
    neverBought: ALL_TECHNOLOGIES.filter((tech) => !bought.has(tech.id)).map((tech) => tech.id),
    firstPurchaseAtSeconds,
    deepestStack: Math.max(...Object.values(state.techOwned)),
  };
}

describe('a full run under efficient play', () => {
  const outcome = playThrough();

  it('ends — the planet is always killable', () => {
    expect(outcome.collapsedAtSeconds).not.toBeNull();
  });

  it('takes days rather than an evening, and never a fortnight', () => {
    const days = (outcome.collapsedAtSeconds ?? MAX_SECONDS) / 86400;
    // Efficient play is the fastest honest route through a fresh Earth; a
    // casual player's first run runs appreciably longer than this.
    expect(days).toBeGreaterThan(1);
    expect(days).toBeLessThan(8);
  });

  it('leaves nothing in the tree priced out of a completed run', () => {
    // `nuclear_industrialization` is the road not taken: it is one half of a
    // mutually exclusive choice, so a single run can only ever own one of the
    // pair.
    expect(outcome.neverBought).toEqual(['nuclear_industrialization']);
  });

  it('is underway from the first spending opportunity, not saving up for one', () => {
    // The opening's real cadence (a purchase inside half a minute) is measured
    // tick-by-tick in balance.test.ts. This harness opens the shop every
    // SPEND_EVERY seconds starting at t=0, when a brand-new Earth has earned
    // nothing yet — so the earliest a purchase can land here is the *second*
    // visit, and landing later than that would mean a fresh Earth cannot
    // afford anything after half an hour of income.
    expect(outcome.firstPurchaseAtSeconds).toBe(SPEND_EVERY);
  });

  it('rewards depth enough for ownership bonuses to actually land', () => {
    // If a completed run never reaches the third doubling on anything, the
    // ownership track is decoration rather than a mechanic.
    expect(ownershipMultiplier(outcome.deepestStack)).toBeGreaterThanOrEqual(8);
  });
});
