import { useGameStore } from '../../store/useGameStore';
import { useNumberFormat } from '../hooks';
import { formatDuration, formatTemperature } from '../../engine/format';
import { GAS_LIST } from '../../engine/gases';
import { RESOURCE_LIST } from '../../engine/resources';

export default function OfflineModal() {
  const { format } = useNumberFormat();
  const summary = useGameStore((s) => s.offlineSummary);
  const dismiss = useGameStore((s) => s.dismissOfflineSummary);

  if (!summary) return null;

  const tempDelta = summary.summary.temperatureAfterC - summary.summary.temperatureBeforeC;

  return (
    <div className="modal-overlay">
      <div className="modal-box">
        <h2>👋 Welcome Back</h2>
        <p style={{ color: 'var(--text-dim)', fontSize: '0.88rem' }}>
          You were away for {formatDuration(summary.awaySeconds)}
          {summary.cappedByLimit && ` (capped at ${formatDuration(summary.offlineCapSeconds)} of progress)`}.
        </p>
        <div className="section-label">During your absence</div>
        {GAS_LIST.filter((g) => g.directlyEmitted && summary.summary.gasGeneratedKg[g.id].gt(0)).map((gas) => (
          <div className="row" key={gas.id}>
            <span className="row__label">{gas.name} generated</span>
            <span className="row__value">+{format(summary.summary.gasGeneratedKg[gas.id])} kg</span>
          </div>
        ))}
        {RESOURCE_LIST.filter((r) => summary.summary.resourcesGained[r.id].gt(0)).map((res) => (
          <div className="row" key={res.id}>
            <span className="row__label">{res.name} generated</span>
            <span className="row__value">+{format(summary.summary.resourcesGained[res.id])}</span>
          </div>
        ))}
        <div className="row"><span className="row__label">Temperature</span><span className="row__value">{formatTemperature(tempDelta)}</span></div>
        <div className="modal-actions">
          <button className="modal-btn modal-btn--primary" onClick={dismiss}>Continue</button>
        </div>
      </div>
    </div>
  );
}
