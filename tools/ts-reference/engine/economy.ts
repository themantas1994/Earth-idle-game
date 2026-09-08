import { Decimal } from './bignum';
import { ResourceId } from './resources';
import { ResourceTotals } from './gameState';
import { GameState } from './gameState';
import {
  ALL_TECHNOLOGIES,
  TECH_BY_ID,
  isTechAvailable,
  bulkPurchaseCost,
  maxAffordableQuantity,
} from './technologies';
import { PrestigeMultipliers } from './prestige';

/**
 * Pricing rule of the whole game, and the one invariant every part of this
 * file exists to protect:
 *
 *   **A price never rises for any reason other than your own purchases of
 *   that exact thing.**
 *
 * Concretely, the amount charged for a technology is a pure function of
 * `(technology, how many of that technology you already own)`, scaled down —
 * never up — by the prestige discount. Nothing about the rest of the
 * civilization enters into it. So:
 *
 * - a one-time unlock, multiplier or choice is quoted once and costs exactly
 *   that forever, however large the tree around it grows;
 * - a generator's *first* unit always costs its listed base price, and only
 *   the units you have personally bought of it make the next one dearer.
 *
 * There used to be a civilization-complexity surcharge here that multiplied
 * every price by the number of distinct technologies owned. It paced the
 * middle of the game, but it did so by quietly re-pricing things the player
 * had already been shown — every new frontier made every *other* frontier
 * more expensive, so the reward for expanding was a bigger bill. Pacing now
 * comes entirely from the tier curves in `constants.ts > BALANCE`, which are
 * baked into each technology's listed price up front and never move.
 */

/** The price actually charged for `nominal`, after any prestige discount. Only ever ≤ `nominal`. */
export function effectiveCostAmount(nominal: Decimal, discount: number): Decimal {
  return discount <= 0 ? nominal : nominal.mul(1 - Math.min(0.9, discount));
}

/**
 * Restates a wallet in the "nominal" units the cost curves are written in, so
 * the closed-form affordability maths in `technologies/index.ts` needs no
 * knowledge of the prestige discount: divide what the player holds by exactly
 * the factor that will multiply the price.
 *
 * Exported because the UI has to answer the same question the engine does —
 * running both off this keeps a Buy button from ever offering a purchase
 * `purchaseTechnology` would then refuse.
 */
export function nominalizeWallet(
  resources: ResourceTotals,
  costs: { resource: string }[],
  discount: number,
): Record<string, Decimal> {
  const scaled: Record<string, Decimal> = {};
  const factor = 1 - Math.min(0.9, Math.max(0, discount));
  for (const c of costs) {
    const balance = resources[c.resource as ResourceId] ?? Decimal.ZERO;
    scaled[c.resource] = factor >= 1 ? balance : balance.div(factor);
  }
  return scaled;
}

export interface PurchaseResult {
  state: GameState;
  purchasedQuantity: number;
  success: boolean;
}

/**
 * Buys up to `requestedQuantity` more units of a technology (1, 10, 100, or
 * `Infinity` for "buy max"). Fails atomically — either the affordable
 * quantity (capped at what's requested) is purchased and paid for in one
 * step, or nothing changes.
 */
export function purchaseTechnology(
  state: GameState,
  techId: string,
  requestedQuantity: number,
  prestige: PrestigeMultipliers,
  disabledTechIds: ReadonlySet<string> = new Set(),
): PurchaseResult {
  const tech = TECH_BY_ID[techId];
  if (!tech || disabledTechIds.has(techId)) return { state, purchasedQuantity: 0, success: false };
  if (!isTechAvailable(tech, state.techOwned)) return { state, purchasedQuantity: 0, success: false };

  const owned = state.techOwned[techId] ?? 0;
  const roomLeft = tech.maxOwned === Infinity ? Infinity : tech.maxOwned - owned;
  if (roomLeft <= 0) return { state, purchasedQuantity: 0, success: false };

  const scaledAvailable = nominalizeWallet(state.resources, tech.cost, prestige.techCostDiscount);

  const cappedQuantity = Math.min(requestedQuantity, roomLeft === Infinity ? Number.MAX_SAFE_INTEGER : roomLeft);
  const quantity = maxAffordableQuantity(tech, owned, scaledAvailable, cappedQuantity);
  if (quantity <= 0) return { state, purchasedQuantity: 0, success: false };

  const totalCost = bulkPurchaseCost(tech, owned, quantity);
  const newResources = { ...state.resources };
  for (const c of totalCost) {
    const charged = effectiveCostAmount(c.amount, prestige.techCostDiscount);
    newResources[c.resource as ResourceId] = newResources[c.resource as ResourceId].sub(charged).clampMin(0);
  }

  const newTechOwned = { ...state.techOwned, [techId]: owned + quantity };

  return {
    state: { ...state, resources: newResources, techOwned: newTechOwned },
    purchasedQuantity: quantity,
    success: true,
  };
}

/** Grants a technology for free (owned = max(current, 1)) — used for prestige "starting tech" bonuses. */
export function grantTechnology(state: GameState, techId: string): GameState {
  const owned = state.techOwned[techId] ?? 0;
  if (owned > 0) return state;
  return { ...state, techOwned: { ...state.techOwned, [techId]: 1 } };
}

export function listAvailableTechnologies(state: GameState) {
  return ALL_TECHNOLOGIES.filter((t) => isTechAvailable(t, state.techOwned) || (state.techOwned[t.id] ?? 0) > 0);
}
