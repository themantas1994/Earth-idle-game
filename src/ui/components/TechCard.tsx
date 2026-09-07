import {
  Technology,
  requirementsMet,
  TECH_BY_ID,
  nextPurchaseCost,
  bulkPurchaseCost,
  maxAffordableQuantity,
} from '../../engine/technologies';
import { useGameStore } from '../../store/useGameStore';
import { useNumberFormat } from '../hooks';
import { RESOURCES } from '../../engine/resources';
import { GASES } from '../../engine/gases';
import { Decimal } from '../../engine/bignum';

export type BuyQuantity = 1 | 10 | 100 | 'max';

function effectSummary(tech: Technology, format: (v: any) => string): string[] {
  const lines: string[] = [];
  const e = tech.effect;
  if (e.gasProductionPerUnit) {
    for (const [gasId, amount] of Object.entries(e.gasProductionPerUnit)) {
      lines.push(`+${format(amount)} kg ${GASES[gasId as keyof typeof GASES].formula}/s`);
    }
  }
  if (e.gasRemovalPerUnit) {
    for (const [gasId, amount] of Object.entries(e.gasRemovalPerUnit)) {
      lines.push(`−${format(amount)} kg ${GASES[gasId as keyof typeof GASES].formula}/s`);
    }
  }
  if (e.resourceProductionPerUnit) {
    for (const [resId, amount] of Object.entries(e.resourceProductionPerUnit)) {
      lines.push(`+${format(amount)} ${RESOURCES[resId as keyof typeof RESOURCES].shortName}/s`);
    }
  }
  if (e.globalProductionMultiplier) lines.push(`×${e.globalProductionMultiplier} all production`);
  if (e.branchProductionMultiplier) lines.push(`×${e.branchProductionMultiplier.multiplier} ${e.branchProductionMultiplier.branch} production`);
  if (e.gasProductionMultiplier) lines.push(`×${e.gasProductionMultiplier.multiplier} ${GASES[e.gasProductionMultiplier.gas].formula} production`);
  if (e.resourceProductionMultiplier) lines.push(`×${e.resourceProductionMultiplier.multiplier} ${RESOURCES[e.resourceProductionMultiplier.resource].shortName} production`);
  if (e.researchMultiplier) lines.push(`×${e.researchMultiplier} Research`);
  return lines;
}

export default function TechCard({ tech, quantity }: { tech: Technology; quantity: BuyQuantity }) {
  const { format } = useNumberFormat();
  const techOwned = useGameStore((s) => s.state.techOwned);
  const resources = useGameStore((s) => s.state.resources);
  const disabledTechIds = useGameStore((s) => s.derived.disabledTechIds);
  const buyTechnology = useGameStore((s) => s.buyTechnology);

  const owned = techOwned[tech.id] ?? 0;
  const reqsMet = requirementsMet(tech, techOwned);
  const isDisabledByChallenge = disabledTechIds.has(tech.id);
  const isMaxedOneTime = tech.maxOwned === 1 && owned > 0;
  const rivalTaken =
    !!tech.choiceGroup &&
    Object.entries(techOwned).some(([id, count]) => id !== tech.id && count > 0 && TECH_BY_ID[id]?.choiceGroup === tech.choiceGroup);

  const locked = !reqsMet || isDisabledByChallenge;
  const qtyNumber = quantity === 'max' ? Number.MAX_SAFE_INTEGER : quantity;
  const roomLeft = tech.maxOwned === Infinity ? Infinity : tech.maxOwned - owned;
  const cappedQty = Math.min(qtyNumber, roomLeft === Infinity ? Number.MAX_SAFE_INTEGER : roomLeft);

  const affordableQty = reqsMet && !locked && !isMaxedOneTime && !rivalTaken
    ? maxAffordableQuantity(tech, owned, resources as unknown as Record<string, Decimal>, cappedQty)
    : 0;

  const displayQty = tech.maxOwned === 1 ? 1 : Math.max(1, quantity === 'max' ? Math.max(affordableQty, 1) : cappedQty);
  const cost = tech.maxOwned === 1 ? nextPurchaseCost(tech, owned) : bulkPurchaseCost(tech, owned, displayQty);

  const buyLabel = tech.maxOwned === 1 ? 'Unlock' : quantity === 'max' ? `Buy Max${affordableQty > 0 ? ` (${affordableQty})` : ''}` : `Buy ${displayQty}`;
  const canBuy = !locked && !isMaxedOneTime && !rivalTaken && affordableQty > 0;

  if (isMaxedOneTime || rivalTaken) {
    return (
      <div className="tech-card tech-card--maxed-choice">
        <div className="tech-card__top">
          <span className="tech-card__icon">{tech.icon}</span>
          <span className="tech-card__name">{tech.name}</span>
          <span className="tech-card__owned">{isMaxedOneTime ? 'OWNED' : 'NOT CHOSEN'}</span>
        </div>
      </div>
    );
  }

  return (
    <div className={`tech-card ${locked ? 'tech-card--locked' : ''}`}>
      <div className="tech-card__top">
        <span className="tech-card__icon">{tech.icon}</span>
        <span className="tech-card__name">{tech.name}</span>
        {owned > 0 && tech.maxOwned === Infinity && <span className="tech-card__owned">×{format(owned)}</span>}
      </div>
      <div className="tech-card__desc">{tech.description}</div>
      <div className="tech-card__meta">
        {effectSummary(tech, format).map((line, i) => (
          <span key={i}>{line}</span>
        ))}
      </div>
      {!reqsMet && (
        <div className="tech-card__meta">
          Requires: {tech.requires.map((id) => TECH_BY_ID[id]?.name ?? id).join(', ')}
        </div>
      )}
      {isDisabledByChallenge && <div className="tech-card__meta">Disabled by active challenge</div>}
      {reqsMet && !isDisabledByChallenge && (
        <div className="tech-card__buy-row">
          <button className={tech.kind === 'choice' ? 'choice-btn' : 'buy-btn'} disabled={!canBuy} onClick={() => buyTechnology(tech.id, quantity === 'max' ? 'max' : displayQty)}>
            {buyLabel} — {cost.map((c) => `${format(c.amount)} ${RESOURCES[c.resource as keyof typeof RESOURCES].shortName}`).join(', ')}
          </button>
        </div>
      )}
    </div>
  );
}
