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
import { BALANCE } from './constants';

/**
 * Cost multiplier charged by the sheer breadth of the civilization: every
 * *distinct* technology already owned makes the next purchase dearer. See
 * `BALANCE.complexityCostGrowth` for why this exists and what it is for.
 *
 * Extra units of something already owned are free of it — the count is of
 * distinct technologies, not of buildings — so this taxes expansion into new
 * frontiers, never investment in what you already run.
 */
export function complexityCostMultiplier(techOwned: Record<string, number>, reduction = 0): number {
  let distinct = 0;
  for (const owned of Object.values(techOwned)) {
    if (owned > 0) distinct++;
  }
  // The technology every run is handed for free shouldn't already be charging
  // the player drag, so a fresh Earth starts at exactly ×1.
  const charged = Math.max(0, distinct - 1) * (1 - Math.min(0.75, Math.max(0, reduction)));
  return Math.pow(BALANCE.complexityCostGrowth, charged);
}

/** The price actually charged for `nominal`, after complexity drag and any prestige discount. */
export function effectiveCostAmount(nominal: Decimal, complexity: number, discount: number): Decimal {
  const withComplexity = complexity === 1 ? nominal : nominal.mul(complexity);
  return discount <= 0 ? withComplexity : withComplexity.mul(1 - discount);
}

/**
 * Restates a wallet in the "nominal" units the cost curves are written in, so
 * the closed-form affordability maths in `technologies/index.ts` needs no
 * knowledge of discounts or drag: divide what the player holds by exactly the
 * factors that will multiply the price.
 *
 * Exported because the UI has to answer the same question the engine does —
 * running both off this keeps a Buy button from ever offering a purchase
 * `purchaseTechnology` would then refuse.
 */
export function nominalizeWallet(
  resources: ResourceTotals,
  costs: { resource: string }[],
  complexity: number,
  discount: number,
): Record<string, Decimal> {
  const scaled: Record<string, Decimal> = {};
  for (const c of costs) {
    let balance = resources[c.resource as ResourceId] ?? Decimal.ZERO;
    if (discount > 0) balance = balance.div(1 - discount);
    if (complexity !== 1) balance = balance.div(complexity);
    scaled[c.resource] = balance;
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

  const complexity = complexityCostMultiplier(state.techOwned, prestige.complexityReduction);
  const scaledAvailable = nominalizeWallet(state.resources, tech.cost, complexity, prestige.techCostDiscount);

  const cappedQuantity = Math.min(requestedQuantity, roomLeft === Infinity ? Number.MAX_SAFE_INTEGER : roomLeft);
  const quantity = maxAffordableQuantity(tech, owned, scaledAvailable, cappedQuantity);
  if (quantity <= 0) return { state, purchasedQuantity: 0, success: false };

  const totalCost = bulkPurchaseCost(tech, owned, quantity);
  const newResources = { ...state.resources };
  for (const c of totalCost) {
    const charged = effectiveCostAmount(c.amount, complexity, prestige.techCostDiscount);
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
