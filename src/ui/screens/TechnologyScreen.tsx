import { useState } from 'react';
import { ALL_TECHNOLOGIES, BRANCH_META, TechBranch } from '../../engine/technologies';
import TechCard from '../components/TechCard';

const BRANCH_ORDER = Object.keys(BRANCH_META) as TechBranch[];

export default function TechnologyScreen() {
  const [activeBranch, setActiveBranch] = useState<TechBranch | 'all'>('all');

  // Generators are deliberately absent here: this screen is the *research tree*
  // — the one-time unlocks, permanent multipliers and branching choices that
  // decide what a civilization is capable of. Anything you buy repeatedly to
  // raise output lives on the Production screen instead, so the two tabs never
  // show the same card twice and neither becomes an undifferentiated wall.
  //
  // Locked nodes stay visible (with their cost, requirements and effects shown)
  // so the full tree is browsable from the start.
  const visibleTechs = ALL_TECHNOLOGIES.filter(
    (t) => t.kind !== 'generator' && (activeBranch === 'all' || t.branch === activeBranch),
  );

  return (
    <div>
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

      {visibleTechs.length === 0 && (
        <div className="empty-hint">No research nodes in this branch — its buildings are on the Production tab.</div>
      )}
      {visibleTechs.map((tech) => (
        <TechCard key={tech.id} tech={tech} quantity={1} />
      ))}
    </div>
  );
}
