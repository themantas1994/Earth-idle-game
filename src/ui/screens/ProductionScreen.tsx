import { useState } from 'react';
import { ALL_TECHNOLOGIES, isTechAvailable } from '../../engine/technologies';
import { useGameStore } from '../../store/useGameStore';
import { useNumberFormat } from '../hooks';
import TechCard, { BuyQuantity } from '../components/TechCard';
import QuantitySelector from '../components/QuantitySelector';
import { GASES } from '../../engine/gases';
import { RESOURCES } from '../../engine/resources';
import { OWNERSHIP_BONUS } from '../../engine/constants';
import { computeTechProductionRates } from '../../engine/simulation';

export default function ProductionScreen() {
  // Defaults to Max: the loop this game is built on is "come back to a
  // stockpile, spend all of it at once", and making that the one-tap default
  // is most of what makes a check-in feel good.
  const [quantity, setQuantity] = useState<BuyQuantity>('max');
  const techOwned = useGameStore((s) => s.state.techOwned);
  const multipliers = useGameStore((s) => s.derived.effectiveMultipliers);
  const { formatRate } = useNumberFormat();

  // Every generator in the game lives here — this is the only screen that
  // sells them. Ones whose prerequisites aren't met yet are still listed
  // (TechCard renders them locked, with their requirements) so the player can
  // see what the branch they are researching will actually unlock.
  const generators = ALL_TECHNOLOGIES.filter((t) => t.kind === 'generator');
  const unlockedCount = generators.filter((t) => (techOwned[t.id] ?? 0) > 0 || isTechAvailable(t, techOwned)).length;

  return (
    <div>
      <QuantitySelector value={quantity} onChange={setQuantity} />
      <div className="screen-note">
        Every building's price is fixed the moment you see it. Only the copies
        you buy of a building make <em>that</em> building's next copy dearer —
        and every {OWNERSHIP_BONUS.everyUnits}th copy doubles its output.
      </div>
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
