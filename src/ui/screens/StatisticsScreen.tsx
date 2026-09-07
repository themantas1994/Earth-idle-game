import { useGameStore } from '../../store/useGameStore';
import { useNumberFormat } from '../hooks';
import { formatDuration, formatTemperature } from '../../engine/format';
import { GAS_LIST } from '../../engine/gases';
import { sumGasTotals } from '../../engine/simulation';

export default function StatisticsScreen() {
  const { format } = useNumberFormat();
  const lifetime = useGameStore((s) => s.state.lifetimeStats);
  const runNumber = useGameStore((s) => s.state.runNumber);

  const totalGhg = sumGasTotals(lifetime.totalGasProducedKg);

  return (
    <div>
      <div className="section-label">Lifetime Statistics</div>
      <div className="card">
        <div className="row"><span className="row__label">Current Earth</span><span className="row__value">#{runNumber}</span></div>
        <div className="row"><span className="row__label">Total Resets</span><span className="row__value">{lifetime.totalResets}</span></div>
        <div className="row"><span className="row__label">Total Play Time</span><span className="row__value">{formatDuration(lifetime.totalPlayTimeSeconds)}</span></div>
        <div className="row"><span className="row__label">Longest Run</span><span className="row__value">{formatDuration(lifetime.longestRunSeconds)}</span></div>
        <div className="row"><span className="row__label">Fastest Reset</span><span className="row__value">{lifetime.fastestResetSeconds !== null ? formatDuration(lifetime.fastestResetSeconds) : '—'}</span></div>
        <div className="row"><span className="row__label">Total Taps</span><span className="row__value">{format(lifetime.totalTaps)}</span></div>
        <div className="row"><span className="row__label">Total Earth Points Earned</span><span className="row__value">{format(lifetime.totalEarthPointsEarned)}</span></div>
      </div>

      <div className="section-label">Climate Records</div>
      <div className="card">
        <div className="row"><span className="row__label">Highest Temperature</span><span className="row__value">{formatTemperature(lifetime.highestTemperatureC)}</span></div>
        <div className="row"><span className="row__label">Highest CO₂ Concentration</span><span className="row__value">{format(lifetime.highestCo2Ppm)} ppm</span></div>
        <div className="row"><span className="row__label">Total Greenhouse Gas Produced</span><span className="row__value">{format(totalGhg)} kg</span></div>
        {GAS_LIST.filter((g) => g.directlyEmitted).map((gas) => (
          <div className="row" key={gas.id}>
            <span className="row__label">Total {gas.formula} Produced</span>
            <span className="row__value">{format(lifetime.totalGasProducedKg[gas.id])} kg</span>
          </div>
        ))}
      </div>
    </div>
  );
}
