import { useGameStore } from '../../store/useGameStore';
import { useNumberFormat } from '../hooks';
import { formatDuration, formatTemperature } from '../../engine/format';

export default function UninhabitableModal() {
  const { format } = useNumberFormat();
  const collapsed = useGameStore((s) => s.state.collapsed);
  const collapseSummary = useGameStore((s) => s.collapseSummary);
  const runStats = useGameStore((s) => s.state.runStats);
  const runStartedAt = useGameStore((s) => s.state.runStartedAt);
  const confirmResetEarth = useGameStore((s) => s.confirmResetEarth);

  if (!collapsed || collapseSummary) return null;

  const survivedSeconds = (Date.now() - runStartedAt) / 1000;

  return (
    <div className="modal-overlay">
      <div className="modal-box">
        <h2>☠️ EARTH HAS BECOME UNINHABITABLE</h2>
        <p style={{ color: 'var(--text-dim)', fontSize: '0.88rem' }}>Human civilization has collapsed.</p>
        <div className="row"><span className="row__label">Total CO₂</span><span className="row__value">{format(runStats.peakCo2Ppm)} ppm</span></div>
        <div className="row"><span className="row__label">Temperature</span><span className="row__value">{formatTemperature(runStats.peakTemperatureC)}</span></div>
        <div className="row"><span className="row__label">Civilization survived</span><span className="row__value">{formatDuration(survivedSeconds)}</span></div>
        <div className="modal-actions">
          <button className="modal-btn modal-btn--primary" onClick={confirmResetEarth}>RESET EARTH</button>
        </div>
      </div>
    </div>
  );
}
