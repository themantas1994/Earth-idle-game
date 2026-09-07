import { PRESTIGE_UPGRADES, prestigeUpgradeCost, PRESTIGE_CURRENCY_NAME } from '../../engine/prestige';
import { useGameStore } from '../../store/useGameStore';
import { useNumberFormat } from '../hooks';

export default function PrestigeScreen() {
  const { format } = useNumberFormat();
  const earthPoints = useGameStore((s) => s.state.prestige.earthPoints);
  const upgradesOwned = useGameStore((s) => s.state.prestige.upgradesOwned);
  const buyPrestigeUpgrade = useGameStore((s) => s.buyPrestigeUpgrade);

  return (
    <div>
      <div className="card" style={{ textAlign: 'center' }}>
        <div className="card__title" style={{ justifyContent: 'center' }}>✨ {PRESTIGE_CURRENCY_NAME}</div>
        <div style={{ fontSize: '1.8rem', fontWeight: 800 }}>{format(earthPoints)}</div>
        <div style={{ fontSize: '0.75rem', color: 'var(--text-faint)', marginTop: 4 }}>
          Earned by destroying Earths. Spend permanently to accelerate every future run.
        </div>
      </div>

      <div className="section-label">Permanent Upgrades</div>
      {PRESTIGE_UPGRADES.map((upgrade) => {
        const level = upgradesOwned[upgrade.id] ?? 0;
        const maxed = level >= upgrade.maxLevel;
        const cost = prestigeUpgradeCost(upgrade, level);
        const canAfford = earthPoints.gte(cost);
        return (
          <div className="tech-card" key={upgrade.id}>
            <div className="tech-card__top">
              <span className="tech-card__icon">{upgrade.icon}</span>
              <span className="tech-card__name">{upgrade.name}</span>
              {level > 0 && <span className="tech-card__owned">Lv.{level}{upgrade.maxLevel !== Infinity ? `/${upgrade.maxLevel}` : ''}</span>}
            </div>
            <div className="tech-card__desc">{upgrade.description}</div>
            {!maxed ? (
              <button className="buy-btn" disabled={!canAfford} onClick={() => buyPrestigeUpgrade(upgrade.id)}>
                Buy — {format(cost)} EP
              </button>
            ) : (
              <span className="badge badge--good">MAXED</span>
            )}
          </div>
        );
      })}
    </div>
  );
}
