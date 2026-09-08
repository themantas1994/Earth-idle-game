import { useGameStore } from '../../store/useGameStore';
import { useNumberFormat } from '../hooks';
import { GAS_LIST } from '../../engine/gases';
import { gasDisplayConcentration, computeSinkEfficiency } from '../../engine/climate';
import NewsFeed from '../components/NewsFeed';

const GAS_COLORS: Record<string, string> = {
  co2: 'var(--co2)',
  ch4: 'var(--ch4)',
  n2o: 'var(--n2o)',
  h2o: 'var(--h2o)',
  o3: 'var(--o3)',
  fluorinated: 'var(--fluor)',
};

export default function AtmosphereScreen() {
  const { format, formatRate } = useNumberFormat();
  const atmosphere = useGameStore((s) => s.state.atmosphere);
  const forcing = useGameStore((s) => s.state.forcing);
  const productionRates = useGameStore((s) => s.derived.productionRates);
  const temperature = useGameStore((s) => s.state.temperatureAnomalyC);
  const oceanPh = useGameStore((s) => s.state.oceanPh);
  const seaLevel = useGameStore((s) => s.state.seaLevelRiseMeters);

  const maxAbsForcing = Math.max(0.001, ...GAS_LIST.map((g) => Math.abs(forcing.perGas[g.id])));
  const sinkEfficiency = computeSinkEfficiency(temperature);

  return (
    <div>
      <div className="section-label">Gas Concentrations</div>
      {GAS_LIST.map((gas) => {
        const concentration = gasDisplayConcentration(gas.id, atmosphere);
        const production = productionRates.gasGrossKgPerS[gas.id];
        const removal = productionRates.gasRemovalKgPerS[gas.id];
        return (
          <div className="card" key={gas.id}>
            <div className="card__title">
              <span style={{ color: GAS_COLORS[gas.id] }}>●</span> {gas.name} ({gas.formula})
            </div>
            <div className="row"><span className="row__label">Concentration</span><span className="row__value">{format(concentration, { precision: 3 })} {gas.unit}</span></div>
            {gas.directlyEmitted && (
              <>
                <div className="row"><span className="row__label">Production</span><span className="row__value">{formatRate(production, 'kg/s')}</span></div>
                {removal.gt(0) && <div className="row"><span className="row__label">Engineered Removal</span><span className="row__value">{formatRate(removal, 'kg/s')}</span></div>}
                <div className="row"><span className="row__label">Natural Lifetime</span><span className="row__value">{gas.lifetimeYears.toLocaleString()} yr</span></div>
              </>
            )}
            {!gas.directlyEmitted && <div className="row"><span className="row__label">Source</span><span className="row__value">Warming feedback</span></div>}
            <div className="row"><span className="row__label">Heating Contribution</span><span className="row__value">{forcing.perGas[gas.id] >= 0 ? '+' : ''}{forcing.perGas[gas.id].toFixed(3)} W/m²</span></div>
            <div className="gas-bar-track">
              <div
                className="gas-bar-fill"
                style={{
                  width: `${Math.min(100, (Math.abs(forcing.perGas[gas.id]) / maxAbsForcing) * 100)}%`,
                  background: GAS_COLORS[gas.id],
                }}
              />
            </div>
          </div>
        );
      })}

      <div className="section-label">Atmospheric Heating</div>
      <div className="card">
        {GAS_LIST.map((gas) => (
          <div className="row" key={gas.id}>
            <span className="row__label">{gas.formula}</span>
            <span className="row__value">{forcing.perGas[gas.id] >= 0 ? '+' : ''}{forcing.perGas[gas.id].toFixed(2)} W/m²</span>
          </div>
        ))}
        <div className="row" style={{ borderTop: '1px solid var(--border)', marginTop: 6, paddingTop: 10, fontWeight: 800 }}>
          <span className="row__label">Total Radiative Forcing</span>
          <span className="row__value">+{forcing.total.toFixed(2)} W/m²</span>
        </div>
      </div>

      <div className="section-label">Carbon Cycle</div>
      <div className="card">
        <div className="row"><span className="row__label">Natural Sink Efficiency</span><span className="row__value">{(sinkEfficiency * 100).toFixed(1)}%</span></div>
        <div className="tech-card__desc">
          Oceans, soil, and vegetation absorb a shrinking share of emissions as the planet warms — sinks weaken as temperature rises.
        </div>
      </div>

      <div className="section-label">Ocean & Sea Level</div>
      <div className="card">
        <div className="row"><span className="row__label">Ocean pH</span><span className="row__value">{oceanPh.toFixed(2)}</span></div>
        <div className="row"><span className="row__label">Sea Level Rise</span><span className="row__value">+{seaLevel.toFixed(2)} m</span></div>
      </div>

      <div className="section-label">This Earth, in the News</div>
      <NewsFeed />
    </div>
  );
}
