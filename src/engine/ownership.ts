/**
 * Ownership bonuses: the reward for going *deep* on one generator.
 *
 * Every generator's price climbs with each unit you own of it, which on its
 * own makes buying the eleventh copy of something strictly worse than buying
 * the first copy of something newer. That is correct pacing but miserable
 * feedback: the number goes up, the bar does not, and nothing ever
 * *happens*.
 *
 * So every generator carries its own little progress track. Every tenth copy
 * doubles that building's entire output, permanently, for the rest of the
 * run — and the card immediately starts counting toward the next one. It is
 * the same purchase the player was already making; it just now lands
 * somewhere visible, roughly every minute or two of active play.
 *
 * The spacing is what keeps it honest. Across one ten-unit span a
 * generator's unit price grows by `unitCostGrowth^10` — about ×4 at the
 * shipped value — against a single ×2 from the bonus, so the value of each
 * further copy still falls and broadening into new technology still wins in
 * the long run. Depth is a satisfying detour, not a replacement for the tech
 * tree. Tightening `everyUnits` far enough to invert that (a doubling
 * cheaper than the price growth that buys it) would turn the whole tree into
 * decoration; `balance.test.ts` guards the relationship.
 */
import { OWNERSHIP_BONUS } from './constants';

/** How many ownership thresholds `owned` units have crossed. */
export function ownershipMilestonesReached(owned: number): number {
  return Math.floor(Math.max(0, owned) / OWNERSHIP_BONUS.everyUnits);
}

/** Production multiplier a generator has earned purely from how many of it are owned. */
export function ownershipMultiplier(owned: number): number {
  const reached = ownershipMilestonesReached(owned);
  return reached <= 0 ? 1 : Math.pow(OWNERSHIP_BONUS.multiplier, reached);
}

/** Unit count at which the next doubling lands, for the "next bonus at ×N" readout. */
export function nextOwnershipMilestone(owned: number): number {
  return (ownershipMilestonesReached(owned) + 1) * OWNERSHIP_BONUS.everyUnits;
}

/** Where `owned` sits between the previous threshold and the next, in [0,1] — drives the card's progress bar. */
export function ownershipProgress(owned: number): number {
  return Math.min(1, Math.max(0, (Math.max(0, owned) % OWNERSHIP_BONUS.everyUnits) / OWNERSHIP_BONUS.everyUnits));
}

/**
 * Thresholds crossed by going from `before` to `after` units, in order.
 * Computed arithmetically rather than by walking the units, since a single
 * "buy max" can land thousands of copies at once.
 */
export function ownershipMilestonesCrossed(before: number, after: number): number[] {
  if (after <= before) return [];
  const crossed: number[] = [];
  const step = OWNERSHIP_BONUS.everyUnits;
  for (let n = ownershipMilestonesReached(before) + 1; n <= ownershipMilestonesReached(after); n++) {
    crossed.push(n * step);
  }
  return crossed;
}
