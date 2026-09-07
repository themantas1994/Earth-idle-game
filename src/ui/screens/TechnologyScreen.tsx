import { useState } from 'react';
import { ALL_TECHNOLOGIES, BRANCH_META, TechBranch } from '../../engine/technologies';
import TechCard, { BuyQuantity } from '../components/TechCard';
import QuantitySelector from '../components/QuantitySelector';

const BRANCH_ORDER = Object.keys(BRANCH_META) as TechBranch[];

export default function TechnologyScreen() {
  const [quantity, setQuantity] = useState<BuyQuantity>(1);
  const [activeBranch, setActiveBranch] = useState<TechBranch | 'all'>('all');

  // Locked technologies stay visible (with their cost/requirements/effects shown) so the
  // full tree is browsable, per the game's "Technology" screen design.
  const visibleTechs = ALL_TECHNOLOGIES.filter((t) => activeBranch === 'all' || t.branch === activeBranch);

  return (
    <div>
      <QuantitySelector value={quantity} onChange={setQuantity} />
      <div style={{ display: 'flex', gap: 6, overflowX: 'auto', marginBottom: 12, paddingBottom: 4 }}>
        <button
          className={`qty-btn ${activeBranch === 'all' ? 'qty-btn--active' : ''}`}
          style={{ flex: '0 0 auto', padding: '6px 12px' }}
          onClick={() => setActiveBranch('all')}
        >
          All
        </button>
        {BRANCH_ORDER.map((branch) => (
          <button
            key={branch}
            className={`qty-btn ${activeBranch === branch ? 'qty-btn--active' : ''}`}
            style={{ flex: '0 0 auto', padding: '6px 12px' }}
            onClick={() => setActiveBranch(branch)}
          >
            {BRANCH_META[branch].icon} {BRANCH_META[branch].name}
          </button>
        ))}
      </div>

      {visibleTechs.map((tech) => (
        <TechCard key={tech.id} tech={tech} quantity={quantity} />
      ))}
    </div>
  );
}
