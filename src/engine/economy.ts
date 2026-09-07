import { Decimal, D } from './bignum';
import { ResourceId } from './resources';
import { GameState } from './gameState';
import {
  ALL_TECHNOLOGIES,
  TECH_BY_ID,
  isTechAvailable,
  bulkPurchaseCost,
  maxAffordableQuantity,
} from './technologies';
import { PrestigeMultipliers } from './prestige';

export const MANUAL_TAP_BASE_ENERGY = 1;

/** Manual "tap to produce" action — always available, funds the earliest purchases. */
export function applyManualTap(state: GameState, prestige: PrestigeMultipliers): GameState {
  const amount = D(MANUAL_TAP_BASE_ENERGY).mul(prestige.tapPowerMultiplier);
  return {
    ...state,
    resources: { ...state.resources, energy: state.resources.energy.add(amount) },
    lifetimeStats: { ...state.lifetimeStats, totalTaps: state.lifetimeStats.totalTaps + 1 },
  };
}

function applyCostDiscount(amount: Decimal, discount: number): Decimal {
  if (discount <= 0) return amount;
  return amount.mul(1 - discount);
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

  const discountedAvailable: Record<string, Decimal> = {};
  for (const c of tech.cost) {
    discountedAvailable[c.resource] = state.resources[c.resource as ResourceId];
  }

  // maxAffordableQuantity works against nominal (non-discounted) costs, so
  // scale the wallet up by the inverse discount to get an equivalent
  // undiscounted affordability check.
  const scaledAvailable: Record<string, Decimal> = {};
  for (const [res, bal] of Object.entries(discountedAvailable)) {
    scaledAvailable[res] = prestige.techCostDiscount > 0 ? bal.div(1 - prestige.techCostDiscount) : bal;
  }

  const cappedQuantity = Math.min(requestedQuantity, roomLeft === Infinity ? Number.MAX_SAFE_INTEGER : roomLeft);
  const quantity = maxAffordableQuantity(tech, owned, scaledAvailable, cappedQuantity);
  if (quantity <= 0) return { state, purchasedQuantity: 0, success: false };

  const totalCost = bulkPurchaseCost(tech, owned, quantity);
  const newResources = { ...state.resources };
  for (const c of totalCost) {
    const discounted = applyCostDiscount(c.amount, prestige.techCostDiscount);
    newResources[c.resource as ResourceId] = newResources[c.resource as ResourceId].sub(discounted).clampMin(0);
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
