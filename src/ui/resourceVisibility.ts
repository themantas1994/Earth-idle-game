import { Decimal } from '../engine/bignum';
import { ResourceDefinition, ResourceId, RESOURCE_LIST } from '../engine/resources';

/**
 * The resources worth putting in front of the player: the ones they hold a
 * balance of, plus the ones currently being produced (so a freshly built
 * generator's output appears before the first unit lands). Resources the run
 * has never touched stay hidden — an opening screen listing five permanent
 * zeroes teaches nothing and costs the space the live numbers need.
 *
 * Shared by the Home "Economy" card and the header strip so the two can never
 * disagree about which resources exist.
 */
export function visibleResources(
  resources: Record<ResourceId, Decimal>,
  ratePerS: Record<ResourceId, Decimal>,
): ResourceDefinition[] {
  return RESOURCE_LIST.filter((res) => !resources[res.id].isZero() || !ratePerS[res.id].isZero());
}
