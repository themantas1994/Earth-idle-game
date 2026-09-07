import { ACHIEVEMENTS } from '../../engine/achievements';
import { useGameStore } from '../../store/useGameStore';

export default function AchievementsScreen() {
  const unlocked = useGameStore((s) => s.state.achievementsUnlocked);
  const unlockedCount = ACHIEVEMENTS.filter((a) => unlocked[a.id]).length;

  return (
    <div>
      <div className="card" style={{ textAlign: 'center' }}>
        <div className="card__title" style={{ justifyContent: 'center' }}>🏆 Achievements</div>
        <div style={{ fontSize: '1.4rem', fontWeight: 800 }}>{unlockedCount} / {ACHIEVEMENTS.length}</div>
      </div>
      {ACHIEVEMENTS.map((a) => {
        const done = !!unlocked[a.id];
        return (
          <div className="tech-card" key={a.id} style={{ opacity: done ? 1 : 0.55 }}>
            <div className="tech-card__top">
              <span className="tech-card__icon">{done ? a.icon : '🔒'}</span>
              <span className="tech-card__name">{a.name}</span>
              {done && <span className="badge badge--good" style={{ marginLeft: 'auto' }}>UNLOCKED</span>}
            </div>
            <div className="tech-card__desc">{a.description}</div>
          </div>
        );
      })}
    </div>
  );
}
