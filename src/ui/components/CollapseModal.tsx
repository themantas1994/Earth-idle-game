import { useGameStore } from '../../store/useGameStore';
import { useNumberFormat } from '../hooks';
import { formatDuration, formatTemperature } from '../../engine/format';
import { RUN_LABEL } from '../../engine/constants';
import { PRESTIGE_CURRENCY_NAME } from '../../engine/prestige';

export default function CollapseModal() {
  const { format } = useNumberFormat();
  const summary = useGameStore((s) => s.collapseSummary);
  const dismiss = useGameStore((s) => s.dismissCollapseSummary);

  if (!summary) return null;

  return (
    <div className="modal-overlay">
      <div className="modal-box">
        <h2>🌍 {RUN_LABEL} {summary.runNumber} COMPLETE</h2>
        <div className="row"><span className="row__label">Civilization Duration</span><span className="row__value">{formatDuration(summary.durationSeconds)}</span></div>
        <div className="row"><span className="row__label">Maximum CO₂</span><span className="row__value">{format(summary.maxCo2Ppm)} ppm</span></div>
        <div className="row"><span className="row__label">Maximum Temperature</span><span className="row__value">{formatTemperature(summary.maxTemperatureC)}</span></div>
        <div className="row"><span className="row__label">Peak GHG Production</span><span className="row__value">{format(summary.maxGasProductionRateKgPerS)} kg/s</span></div>
        <div className="card" style={{ marginTop: 12, textAlign: 'center', background: 'var(--bg-card)' }}>
          <div className="section-label" style={{ margin: 0 }}>{PRESTIGE_CURRENCY_NAME} Earned</div>
          <div style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--accent)' }}>+{format(summary.earthPointsEarned)}</div>
        </div>
        <div className="modal-actions">
          <button className="modal-btn modal-btn--primary" onClick={dismiss}>Begin {RUN_LABEL} {summary.runNumber + 1}</button>
        </div>
      </div>
    </div>
  );
}
