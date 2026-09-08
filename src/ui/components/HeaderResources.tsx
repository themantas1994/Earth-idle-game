import { useGameStore } from '../../store/useGameStore';
import { useNumberFormat } from '../hooks';
import { visibleResources } from '../resourceVisibility';

/**
 * The Home screen's "Economy" card, condensed into the header. Every other tab
 * asks the player to spend resources — Technology and Production most of all —
 * so having the balances live only on Home meant bouncing back and forth to
 * answer "can I afford this yet?". Rendered on non-Home screens only: Home
 * already shows the full card right below, and duplicating it there would just
 * eat vertical space.
 */
export default function HeaderResources() {
  const { format, formatRate } = useNumberFormat();
  const resources = useGameStore((s) => s.state.resources);
  const resourcePerS = useGameStore((s) => s.derived.productionRates.resourcePerS);
  const visible = visibleResources(resources, resourcePerS);

  if (visible.length === 0) return null;

  return (
    <div className="app-header__resources" aria-label="Resources">
      {visible.map((res) => (
        <div className="stat-chip stat-chip--resource" key={res.id}>
          <div className="stat-chip__label">{res.shortName}</div>
          <div className="stat-chip__value">
            {format(resources[res.id])}{' '}
            <span className="stat-chip__rate">{formatRate(resourcePerS[res.id], '/s')}</span>
          </div>
        </div>
      ))}
    </div>
  );
}
