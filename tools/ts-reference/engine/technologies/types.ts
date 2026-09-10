import { GasId } from '../gases';
import { ResourceId } from '../resources';

export type TechBranch =
  | 'primitive'
  | 'agriculture'
  | 'industry'
  | 'electricity'
  | 'fossilFuels'
  | 'transportation'
  | 'construction'
  | 'chemistry'
  | 'globalization'
  | 'digital'
  | 'endgame';

export const BRANCH_META: Record<TechBranch, { name: string; icon: string }> = {
  primitive: { name: 'Primitive Civilization', icon: '🔥' },
  agriculture: { name: 'Agriculture', icon: '🌾' },
  industry: { name: 'Industrial Revolution', icon: '🏭' },
  electricity: { name: 'Electricity', icon: '⚡' },
  fossilFuels: { name: 'Fossil Fuels', icon: '🛢️' },
  transportation: { name: 'Transportation', icon: '🚗' },
  construction: { name: 'Construction', icon: '🏗️' },
  chemistry: { name: 'Chemical Industry', icon: '🧪' },
  globalization: { name: 'Globalization', icon: '🌐' },
  digital: { name: 'Digital Civilization', icon: '💻' },
  endgame: { name: 'Endgame', icon: '🚀' },
};

/**
 * - `unlock`: one-time tree node, gates later technologies but has no
 *   production of its own (e.g. "Controlled Fire").
 * - `generator`: repeatably purchasable, produces gas and/or resources per
 *   owned unit, cost scales with `costGrowth` per unit already owned.
 * - `multiplier`: one-time purchase that permanently scales production
 *   (globally, per-branch, or per-gas) for the rest of the run.
 * - `choice`: one-time purchase mutually exclusive with siblings sharing
 *   the same `choiceGroup` (branching strategic decisions).
 */
export type TechKind = 'unlock' | 'generator' | 'multiplier' | 'choice';

export interface TechCost {
  resource: ResourceId;
  baseAmount: number;
}

export interface TechEffect {
  gasProductionPerUnit?: Partial<Record<GasId, number>>;
  resourceProductionPerUnit?: Partial<Record<ResourceId, number>>;
  gasRemovalPerUnit?: Partial<Record<GasId, number>>;
  globalProductionMultiplier?: number;
  branchProductionMultiplier?: { branch: TechBranch; multiplier: number };
  gasProductionMultiplier?: { gas: GasId; multiplier: number };
  resourceProductionMultiplier?: { resource: ResourceId; multiplier: number };
  researchMultiplier?: number;
}

export interface Technology {
  id: string;
  name: string;
  branch: TechBranch;
  kind: TechKind;
  /** Overall civilization-progression index; drives cost/production scaling and UI ordering. */
  tier: number;
  description: string;
  icon: string;
  requires: string[];
  cost: TechCost[];
  costGrowth: number;
  maxOwned: number;
  effect: TechEffect;
  choiceGroup?: string;
}
