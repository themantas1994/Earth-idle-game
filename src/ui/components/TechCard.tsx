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
import { RESOURCES, ResourceId } from '../../engine/resources';
import { GASES } from '../../engine/gases';
import { effectiveCostAmount, nominalizeWallet } from '../../engine/economy';
import { ownershipMultiplier, nextOwnershipMilestone, ownershipProgress } from '../../engine/ownership';
import { OWNERSHIP_BONUS } from '../../engine/constants';
import { formatDuration } from '../../engine/format';
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

/**
 * Seconds until the player can afford `cost`, at their current income. A
 * price the player is saving toward is far more motivating with a clock on
 * it than without one — and because prices in this game never move, the
 * clock is a promise rather than an estimate: wait that long and the thing
 * is yours at exactly the number on the button.
 *
 * Returns `null` when nothing is producing the resource, which the caller
 * renders as "no income" rather than as an infinite countdown.
 */
function secondsUntilAffordable(
  cost: { resource: string; amount: Decimal }[],
  resources: Record<string, Decimal>,
  perSecond: Record<string, Decimal>,
): number | null {
  let worst = 0;
  for (const c of cost) {
    const held = resources[c.resource] ?? Decimal.ZERO;
    const shortfall = c.amount.sub(held);
    if (shortfall.lte(0)) continue;
    const rate = perSecond[c.resource] ?? Decimal.ZERO;
    if (rate.lte(0)) return null;
    worst = Math.max(worst, shortfall.div(rate).toNumber());
  }
  return worst;
}

export default function TechCard({ tech, quantity }: { tech: Technology; quantity: BuyQuantity }) {
  const { format } = useNumberFormat();
  const techOwned = useGameStore((s) => s.state.techOwned);
  const resources = useGameStore((s) => s.state.resources);
  const productionRates = useGameStore((s) => s.derived.productionRates);
  const disabledTechIds = useGameStore((s) => s.derived.disabledTechIds);
  const buyTechnology = useGameStore((s) => s.buyTechnology);

  const prestige = useGameStore((s) => s.derived.prestigeMultipliers);

  const owned = techOwned[tech.id] ?? 0;
  const discount = prestige.techCostDiscount;
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

  // Costs are curve-nominal; the prestige discount is the only thing applied
  // on top, and it only ever makes them smaller. Asking the engine's own
  // helper to restate the wallet keeps this check identical to the one
  // `purchaseTechnology` performs, so the button never promises a purchase
  // the engine then refuses.
  const nominalWallet = nominalizeWallet(resources, tech.cost, discount);

  const affordableQty = reqsMet && !locked && !isMaxedOneTime && !rivalTaken
    ? maxAffordableQuantity(tech, owned, nominalWallet, cappedQty)
    : 0;

  const displayQty = tech.maxOwned === 1 ? 1 : Math.max(1, quantity === 'max' ? Math.max(affordableQty, 1) : cappedQty);
  const nominalCost = tech.maxOwned === 1 ? nextPurchaseCost(tech, owned) : bulkPurchaseCost(tech, owned, displayQty);
  const cost = nominalCost.map((c) => ({ ...c, amount: effectiveCostAmount(c.amount, discount) }));

  const buyLabel = tech.maxOwned === 1 ? 'Unlock' : quantity === 'max' ? `Buy Max${affordableQty > 0 ? ` (${affordableQty})` : ''}` : `Buy ${displayQty}`;
  const canBuy = !locked && !isMaxedOneTime && !rivalTaken && affordableQty > 0;

  const isGenerator = tech.kind === 'generator';
  const bonus = ownershipMultiplier(owned);
  const nextBonusAt = nextOwnershipMilestone(owned);
  const bonusProgress = ownershipProgress(owned);

  const waitSeconds = canBuy || locked ? null : secondsUntilAffordable(cost, resources, productionRates.resourcePerS as Record<string, Decimal>);

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
        {bonus > 1 && <span className="tech-card__bonus">×{format(bonus)} output</span>}
        {owned > 0 && tech.maxOwned === Infinity && <span className="tech-card__owned">×{format(owned)}</span>}
      </div>
      <div className="tech-card__desc">{tech.description}</div>
      <div className="tech-card__meta">
        {effectSummary(tech, format).map((line, i) => (
          <span key={i}>{line}</span>
        ))}
      </div>

      {isGenerator && reqsMet && !isDisabledByChallenge && (
        <div className="bonus-track">
          <div className="bonus-track__bar">
            <div className="bonus-track__fill" style={{ width: `${Math.max(1, bonusProgress * 100)}%` }} />
          </div>
          <div className="bonus-track__label">
            ×{OWNERSHIP_BONUS.multiplier} output at {format(nextBonusAt)} owned
            <span className="bonus-track__count"> · {format(nextBonusAt - owned)} to go</span>
          </div>
        </div>
      )}

      {!reqsMet && (
        <div className="tech-card__meta">
          Requires: {tech.requires.map((id) => TECH_BY_ID[id]?.name ?? id).join(', ')}
        </div>
      )}
      {isDisabledByChallenge && <div className="tech-card__meta">Disabled by active challenge</div>}
      {reqsMet && !isDisabledByChallenge && (
        <div className="tech-card__buy-row">
          <button className={tech.kind === 'choice' ? 'choice-btn' : 'buy-btn'} disabled={!canBuy} onClick={() => buyTechnology(tech.id, quantity === 'max' ? 'max' : displayQty)}>
            {buyLabel} — {cost.map((c) => `${format(c.amount)} ${RESOURCES[c.resource as ResourceId].shortName}`).join(', ')}
          </button>
          {waitSeconds !== null && waitSeconds > 0 && (
            <div className="tech-card__eta">affordable in {formatDuration(waitSeconds)}</div>
          )}
          {waitSeconds === null && !canBuy && (
            <div className="tech-card__eta">no income for this yet</div>
          )}
        </div>
      )}
    </div>
  );
}
