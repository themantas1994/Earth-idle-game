/**
 * Balance curves shared by every technology definition. Each function turns
 * a technology's `tier` into a cost or an output using the curve constants
 * in `constants.ts` — so the whole game's pacing is retuned by adjusting a
 * handful of numbers there rather than hundreds of hand-picked ones here.
 *
 * `tier` is a rough overall-progression index (not per-branch) used purely
 * for scaling: technologies unlocked around the same point in a normal
 * playthrough should share a similar tier, whichever branch they belong to.
 *
 * The `scale` argument on each function is the per-technology fudge factor:
 * a dirtier-than-average generator gets a gas `scale` above 1, a bargain
 * unlock gets a cost `scale` below 1. Keep those within roughly 0.3–3 or a
 * single technology starts distorting the pacing the curves are built on.
 */
import { BALANCE } from '../constants';

/**
 * Maps a technology's `tier` onto the position it occupies on the cost and
 * production ladders. The two are the same thing through the broad middle of
 * the tree and diverge above `BALANCE.flattenLadderFromTier`, where the tree
 * narrows to a single chain — see `lateTierCompression` for why.
 *
 * Every curve below runs on this rather than on the raw tier, so the two
 * ladders stay in lockstep: compressing costs without compressing output
 * would simply hand the late game back its runaway.
 */
export function ladderTier(tier: number): number {
  const knee = BALANCE.flattenLadderFromTier;
  if (tier <= knee) return tier;
  return knee + (tier - knee) * BALANCE.lateTierCompression;
}

/** One-time purchase cost (unlocks, multipliers, choices), in a resource's base unit. */
export function unlockCost(tier: number, scale = 1): number {
  return BALANCE.unlockBaseCost * Math.pow(BALANCE.unlockCostGrowthPerTier, ladderTier(tier)) * scale;
}

/** Research cost to unlock a tree node. */
export function researchCost(tier: number, scale = 1): number {
  return BALANCE.researchBaseCost * Math.pow(BALANCE.researchCostGrowthPerTier, ladderTier(tier)) * scale;
}

/** Base cost of a generator's first owned unit. */
export function generatorBaseCost(tier: number, scale = 1): number {
  return BALANCE.generatorBaseCost * Math.pow(BALANCE.generatorCostGrowthPerTier, ladderTier(tier)) * scale;
}

/** Per-unit gas production (kg/s) at 1 owned, before multipliers. */
export function gasProduction(tier: number, scale = 1): number {
  return BALANCE.gasProductionBase * Math.pow(BALANCE.gasProductionGrowthPerTier, ladderTier(tier)) * scale;
}

/** Per-unit resource production (units/s) at 1 owned, before multipliers. */
export function resourceProduction(tier: number, scale = 1): number {
  return BALANCE.resourceProductionBase * Math.pow(BALANCE.productionGrowthPerTier, ladderTier(tier)) * scale;
}

/** Standard per-owned-unit cost growth for generators. Slightly steeper at high tiers. */
export function generatorCostGrowth(tier: number): number {
  return BALANCE.unitCostGrowth + ladderTier(tier) * BALANCE.unitCostGrowthPerTier;
}
