import { useState } from 'react';
import { ALL_TECHNOLOGIES, isTechAvailable } from '../../engine/technologies';
import { useGameStore } from '../../store/useGameStore';
import { useNumberFormat } from '../hooks';
import TechCard, { BuyQuantity } from '../components/TechCard';
import QuantitySelector from '../components/QuantitySelector';
import { GASES } from '../../engine/gases';
import { RESOURCES } from '../../engine/resources';

export default function ProductionScreen() {
  const [quantity, setQuantity] = useState<BuyQuantity>(1);
  const techOwned = useGameStore((s) => s.state.techOwned);
  const productionRates = useGameStore((s) => s.derived.productionRates);
  const { formatRate } = useNumberFormat();

  const generators = ALL_TECHNOLOGIES.filter(
    (t) => t.kind === 'generator' && ((techOwned[t.id] ?? 0) > 0 || isTechAvailable(t, techOwned)),
  );

  return (
    <div>
      <QuantitySelector value={quantity} onChange={setQuantity} />
      {generators.length === 0 && <div className="empty-hint">No generators available yet. Unlock Controlled Fire on the Technology tab.</div>}
      {generators.map((tech) => {
        const owned = techOwned[tech.id] ?? 0;
        const outputs: string[] = [];
        if (owned > 0) {
          for (const gasId of Object.keys(tech.effect.gasProductionPerUnit ?? {})) {
            const rate = productionRates.gasGrossKgPerS[gasId as keyof typeof GASES];
            if (rate.gt(0)) outputs.push(formatRate(rate, `kg ${GASES[gasId as keyof typeof GASES].formula}/s`));
          }
          for (const resId of Object.keys(tech.effect.resourceProductionPerUnit ?? {})) {
            const rate = productionRates.resourcePerS[resId as keyof typeof RESOURCES];
            if (rate.gt(0)) outputs.push(formatRate(rate, `${RESOURCES[resId as keyof typeof RESOURCES].shortName}/s`));
          }
        }
        return (
          <div key={tech.id}>
            {owned > 0 && outputs.length > 0 && (
              <div style={{ fontSize: '0.7rem', color: 'var(--accent)', marginBottom: -4, paddingLeft: 2 }}>
                Current total: {outputs.join(' · ')}
              </div>
            )}
            <TechCard tech={tech} quantity={quantity} />
          </div>
        );
      })}
    </div>
  );
}
