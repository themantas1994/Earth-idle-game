import { useState } from 'react';
import { ALL_TECHNOLOGIES, isTechAvailable } from '../../engine/technologies';
import { useGameStore } from '../../store/useGameStore';
import { useNumberFormat } from '../hooks';
import TechCard, { BuyQuantity } from '../components/TechCard';
import QuantitySelector from '../components/QuantitySelector';
import { GASES } from '../../engine/gases';
import { RESOURCES } from '../../engine/resources';
import { complexityCostMultiplier } from '../../engine/economy';
import { computeTechProductionRates } from '../../engine/simulation';

export default function ProductionScreen() {
  const [quantity, setQuantity] = useState<BuyQuantity>(1);
  const techOwned = useGameStore((s) => s.state.techOwned);
  const multipliers = useGameStore((s) => s.derived.effectiveMultipliers);
  const { format, formatRate } = useNumberFormat();
  const complexityReduction = useGameStore((s) => s.derived.prestigeMultipliers.complexityReduction);
  const complexity = complexityCostMultiplier(techOwned, complexityReduction);

  // Every generator in the game lives here — this is the only screen that
  // sells them. Ones whose prerequisites aren't met yet are still listed
  // (TechCard renders them locked, with their requirements) so the player can
  // see what the branch they are researching will actually unlock.
  const generators = ALL_TECHNOLOGIES.filter((t) => t.kind === 'generator');
  const unlockedCount = generators.filter((t) => (techOwned[t.id] ?? 0) > 0 || isTechAvailable(t, techOwned)).length;

  return (
    <div>
      <QuantitySelector value={quantity} onChange={setQuantity} />
      {complexity > 1.005 && (
        <div style={{ fontSize: '0.7rem', color: 'var(--text-faint)', margin: '-4px 0 10px', paddingLeft: 2 }}>
          Complexity surcharge on everything: ×{complexity < 100 ? complexity.toFixed(2) : format(complexity)}
        </div>
      )}
      {unlockedCount === 0 && (
        <div className="empty-hint">Nothing buildable yet — research a technology that unlocks one.</div>
      )}
      {generators.map((tech) => {
        const owned = techOwned[tech.id] ?? 0;
        const outputs: string[] = [];
        if (owned > 0) {
          // This building's own contribution, not the global rate for the gases
          // it happens to emit — those are on the Home and Atmosphere screens.
          const mine = computeTechProductionRates(tech, owned, multipliers);
          for (const gasId of Object.keys(tech.effect.gasProductionPerUnit ?? {})) {
            const rate = mine.gasGrossKgPerS[gasId as keyof typeof GASES];
            if (rate.gt(0)) outputs.push(formatRate(rate, `kg ${GASES[gasId as keyof typeof GASES].formula}/s`));
          }
          for (const resId of Object.keys(tech.effect.resourceProductionPerUnit ?? {})) {
            const rate = mine.resourcePerS[resId as keyof typeof RESOURCES];
            if (rate.gt(0)) outputs.push(formatRate(rate, `${RESOURCES[resId as keyof typeof RESOURCES].shortName}/s`));
          }
        }
        return (
          <div key={tech.id}>
            {owned > 0 && outputs.length > 0 && (
              <div style={{ fontSize: '0.7rem', color: 'var(--accent)', marginBottom: -4, paddingLeft: 2 }}>
                Producing {outputs.join(' · ')} from ×{owned}
              </div>
            )}
            <TechCard tech={tech} quantity={quantity} />
          </div>
        );
      })}
    </div>
  );
}
