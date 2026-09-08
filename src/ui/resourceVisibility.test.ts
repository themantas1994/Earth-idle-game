import { describe, it, expect } from 'vitest';
import { D, Decimal } from '../engine/bignum';
import { ResourceId, RESOURCE_LIST } from '../engine/resources';
import { visibleResources } from './resourceVisibility';

function map(values: Partial<Record<ResourceId, number>>): Record<ResourceId, Decimal> {
  return Object.fromEntries(
    RESOURCE_LIST.map((r) => [r.id, D(values[r.id] ?? 0)]),
  ) as Record<ResourceId, Decimal>;
}

describe('visibleResources', () => {
  it('hides resources the run has never touched', () => {
    expect(visibleResources(map({}), map({}))).toEqual([]);
  });

  it('shows a resource the player holds a balance of', () => {
    const visible = visibleResources(map({ energy: 12 }), map({}));
    expect(visible.map((r) => r.id)).toEqual(['energy']);
  });

  it('shows a resource that is only being produced, before the first unit lands', () => {
    const visible = visibleResources(map({}), map({ research: 0.5 }));
    expect(visible.map((r) => r.id)).toEqual(['research']);
  });

  it('keeps the canonical resource order regardless of which are visible', () => {
    const visible = visibleResources(map({ steel: 3, energy: 1 }), map({ coal: 2 }));
    expect(visible.map((r) => r.id)).toEqual(['energy', 'coal', 'steel']);
  });
});
