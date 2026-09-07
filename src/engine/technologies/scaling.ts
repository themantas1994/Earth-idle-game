/**
 * Balance curves shared by every technology definition. Centralizing these
 * means the whole game's pacing can be retuned by adjusting a handful of
 * constants here rather than hundreds of hand-picked numbers.
 *
 * `tier` is a rough overall-progression index (not per-branch) used purely
 * for scaling: technologies unlocked around the same point in a normal
 * playthrough should share a similar tier, whichever branch they belong to.
 */

/** One-time purchase cost (unlocks, multipliers, choices), in a resource's base unit. */
export function unlockCost(tier: number, scale = 1): number {
  return 8 * Math.pow(1.55, tier) * scale;
}

/** Research cost to unlock a tree node. */
export function researchCost(tier: number, scale = 1): number {
  return 4 * Math.pow(1.5, tier) * scale;
}

/** Base cost of a generator's first owned unit. */
export function generatorBaseCost(tier: number, scale = 1): number {
  return 12 * Math.pow(1.62, tier) * scale;
}

/** Per-unit gas production (kg/s) at 1 owned, before multipliers. */
export function gasProduction(tier: number, scale = 1): number {
  return 0.03 * Math.pow(2.25, tier) * scale;
}

/** Per-unit resource production (units/s) at 1 owned, before multipliers. */
export function resourceProduction(tier: number, scale = 1): number {
  return 0.08 * Math.pow(2.05, tier) * scale;
}

/** Standard per-owned-unit cost growth for generators. Slightly steeper at high tiers. */
export function generatorCostGrowth(tier: number): number {
  return 1.12 + tier * 0.001;
}
