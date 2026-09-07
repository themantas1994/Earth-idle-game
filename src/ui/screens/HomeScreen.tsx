import { useGameStore } from '../../store/useGameStore';
import { useNumberFormat } from '../hooks';
import { ALL_TECHNOLOGIES, isTechAvailable } from '../../engine/technologies';
import { GAS_LIST } from '../../engine/gases';
import { RESOURCES } from '../../engine/resources';
import { ScreenId } from '../components/BottomNav';
import { formatPercent } from '../../engine/format';

function useObjective(): string {
  const techOwned = useGameStore((s) => s.state.techOwned);
  const disabledTechIds = useGameStore((s) => s.derived.disabledTechIds);
  const collapsed = useGameStore((s) => s.state.collapsed);

  if (collapsed) return 'Earth is uninhabitable. Reset to begin the next civilization.';

  // Suggest the next tech the player has never bought at all, not one they already own
  // some of — otherwise an owned generator (infinitely repurchasable) would stay "next"
  // forever and the hint would never move on to newer technology.
  const next = ALL_TECHNOLOGIES.find(
    (t) => (techOwned[t.id] ?? 0) === 0 && isTechAvailable(t, techOwned) && !disabledTechIds.has(t.id),
  );
  if (!next) return 'Keep producing — your economy is growing.';
  return `Next: ${next.name}`;
}

export default function HomeScreen({ onNavigate }: { onNavigate: (id: ScreenId) => void }) {
  const { format, formatRate } = useNumberFormat();
  const resources = useGameStore((s) => s.state.resources);
  const productionRates = useGameStore((s) => s.derived.productionRates);
  const forcing = useGameStore((s) => s.state.forcing);
  const habitability = useGameStore((s) => s.state.habitability);
  const collapsed = useGameStore((s) => s.state.collapsed);
  const tap = useGameStore((s) => s.tap);
  const confirmResetEarth = useGameStore((s) => s.confirmResetEarth);
  const objective = useObjective();

  const habitabilityColor = habitability.fraction > 0.5 ? 'var(--good)' : habitability.fraction > 0.15 ? 'var(--warning)' : 'var(--danger)';

  return (
    <div>
      <div className="card" onClick={() => onNavigate('technology')} style={{ cursor: 'pointer' }}>
        <div className="card__title">🎯 Objective</div>
        <div style={{ fontSize: '0.88rem', color: 'var(--text-dim)' }}>{objective}</div>
      </div>

      <div className="card">
        <div className="card__title">🌡️ Planetary Status</div>
        <div className="row"><span className="row__label">Radiative Forcing</span><span className="row__value">+{forcing.total.toFixed(2)} W/m²</span></div>
        <div className="row"><span className="row__label">Habitability</span><span className="row__value" style={{ color: habitabilityColor }}>{formatPercent(habitability.fraction)}</span></div>
        <div className="habitability-track">
          <div className="habitability-fill" style={{ width: `${Math.max(2, habitability.fraction * 100)}%`, background: habitabilityColor }} />
        </div>
      </div>

      <div className="card">
        <div className="card__title">⚙️ Economy</div>
        {Object.values(RESOURCES).map((res) => {
          const rate = productionRates.resourcePerS[res.id];
          if (resources[res.id].isZero() && rate.isZero()) return null;
          return (
            <div className="row" key={res.id}>
              <span className="row__label">{res.name}</span>
              <span className="row__value">{format(resources[res.id])} <span style={{ color: 'var(--text-faint)', fontWeight: 400 }}>({formatRate(rate, '/s')})</span></span>
            </div>
          );
        })}
      </div>

      <div className="card">
        <div className="card__title">☁️ Major Greenhouse Gases</div>
        {GAS_LIST.filter((g) => g.directlyEmitted).map((gas) => {
          const rate = productionRates.gasGrossKgPerS[gas.id];
          if (rate.isZero()) return null;
          return (
            <div className="row" key={gas.id}>
              <span className="row__label">{gas.formula}</span>
              <span className="row__value">{formatRate(rate, 'kg/s')}</span>
            </div>
          );
        })}
        {GAS_LIST.every((g) => productionRates.gasGrossKgPerS[g.id].isZero()) && (
          <div className="empty-hint">Tap to earn Energy, then unlock Controlled Fire on the Technology tab.</div>
        )}
      </div>

      <button className="tap-button" onClick={tap}>👆 Tap to Produce Energy</button>

      <button
        className={`reset-button ${collapsed ? 'reset-button--ready' : 'reset-button--disabled'}`}
        disabled={!collapsed}
        onClick={() => collapsed && confirmResetEarth()}
      >
        {collapsed ? '☠️ RESET EARTH' : 'Reset available once Earth collapses'}
      </button>
    </div>
  );
}
