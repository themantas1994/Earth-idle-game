import { Decimal, D } from '../bignum';
import { Technology } from './types';
import { PRIMITIVE_TECHS } from './primitive';
import { AGRICULTURE_TECHS } from './agriculture';
import { INDUSTRY_TECHS } from './industry';
import { ELECTRICITY_TECHS } from './electricity';
import { FOSSIL_FUEL_TECHS } from './fossilFuels';
import { TRANSPORTATION_TECHS } from './transportation';
import { CONSTRUCTION_TECHS } from './construction';
import { CHEMISTRY_TECHS } from './chemistry';
import { GLOBALIZATION_TECHS } from './globalization';
import { DIGITAL_TECHS } from './digital';
import { ENDGAME_TECHS } from './endgame';

export * from './types';

export const ALL_TECHNOLOGIES: Technology[] = [
  ...PRIMITIVE_TECHS,
  ...AGRICULTURE_TECHS,
  ...INDUSTRY_TECHS,
  ...ELECTRICITY_TECHS,
  ...FOSSIL_FUEL_TECHS,
  ...TRANSPORTATION_TECHS,
  ...CONSTRUCTION_TECHS,
  ...CHEMISTRY_TECHS,
  ...GLOBALIZATION_TECHS,
  ...DIGITAL_TECHS,
  ...ENDGAME_TECHS,
].sort((a, b) => a.tier - b.tier);

export const TECH_BY_ID: Record<string, Technology> = Object.fromEntries(
  ALL_TECHNOLOGIES.map((t) => [t.id, t]),
);

export function getTechnology(id: string): Technology {
  const tech = TECH_BY_ID[id];
  if (!tech) throw new Error(`Unknown technology id: ${id}`);
  return tech;
}

/** Whether every prerequisite of `tech` is owned/unlocked, given a map of owned counts. */
export function requirementsMet(tech: Technology, ownedCounts: Record<string, number>): boolean {
  return tech.requires.every((reqId) => (ownedCounts[reqId] ?? 0) > 0);
}

/** Whether `tech` should even be visible/purchasable: requirements met, not maxed, and no rival choice already taken. */
export function isTechAvailable(
  tech: Technology,
  ownedCounts: Record<string, number>,
): boolean {
  if (!requirementsMet(tech, ownedCounts)) return false;
  if ((ownedCounts[tech.id] ?? 0) >= tech.maxOwned) return false;
  if (tech.choiceGroup) {
    const rivalTaken = ALL_TECHNOLOGIES.some(
      (other) =>
        other.id !== tech.id &&
        other.choiceGroup === tech.choiceGroup &&
        (ownedCounts[other.id] ?? 0) > 0,
    );
    if (rivalTaken) return false;
  }
  return true;
}

/**
 * Cost of purchasing the next unit of `tech`, given how many are already
 * owned. Generators scale geometrically with `costGrowth`; one-time
 * purchases (unlock/multiplier/choice) always cost their base amount.
 */
export function nextPurchaseCost(tech: Technology, owned: number): { resource: string; amount: Decimal }[] {
  const growth = tech.maxOwned === 1 ? 1 : tech.costGrowth;
  return tech.cost.map((c) => ({
    resource: c.resource,
    amount: D(c.baseAmount).mul(D(growth).pow(owned)),
  }));
}

/** Total cost of buying `quantity` more units in one purchase (sum of a geometric series). */
export function bulkPurchaseCost(
  tech: Technology,
  owned: number,
  quantity: number,
): { resource: string; amount: Decimal }[] {
  if (tech.maxOwned === 1) return nextPurchaseCost(tech, owned);
  const growth = tech.costGrowth;
  return tech.cost.map((c) => {
    const base = D(c.baseAmount).mul(D(growth).pow(owned));
    // Sum of geometric series: base * (growth^quantity - 1) / (growth - 1)
    const total = Math.abs(growth - 1) < 1e-9
      ? base.mul(quantity)
      : base.mul(D(growth).pow(quantity).sub(1)).div(growth - 1);
    return { resource: c.resource, amount: total };
  });
}

/**
 * How many more units of `tech` can be afforded with `available` resources,
 * capped by `maxQuantity` (e.g. remaining room under maxOwned). Uses the
 * closed-form inverse of the geometric-series sum, so it works even for
 * huge quantities without looping.
 */
export function maxAffordableQuantity(
  tech: Technology,
  owned: number,
  available: Record<string, Decimal>,
  maxQuantity: number,
): number {
  if (maxQuantity <= 0) return 0;
  if (tech.maxOwned === 1) {
    const cost = nextPurchaseCost(tech, owned);
    const canAfford = cost.every((c) => (available[c.resource] ?? Decimal.ZERO).gte(c.amount));
    return canAfford ? 1 : 0;
  }

  let limit = maxQuantity;
  for (const c of tech.cost) {
    const bal = available[c.resource] ?? Decimal.ZERO;
    if (bal.isZero()) return 0;
    const base = D(c.baseAmount).mul(D(tech.costGrowth).pow(owned));
    const growth = tech.costGrowth;

    let affordableForThisResource: number;
    if (Math.abs(growth - 1) < 1e-9) {
      affordableForThisResource = Math.floor(bal.div(base).toNumber());
    } else {
      // Solve n from: bal >= base * (growth^n - 1) / (growth - 1)
      // => growth^n <= bal*(growth-1)/base + 1
      const rhs = bal.mul(growth - 1).div(base).add(1);
      if (rhs.lte(0)) {
        affordableForThisResource = 0;
      } else {
        affordableForThisResource = Math.floor(rhs.log10() / Math.log10(growth));
      }
    }
    limit = Math.min(limit, Math.max(0, affordableForThisResource));
  }
  return Math.max(0, Math.min(limit, maxQuantity));
}
