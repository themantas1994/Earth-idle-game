import { CHALLENGES } from '../../engine/challenges';
import { useGameStore } from '../../store/useGameStore';

export default function ChallengesScreen() {
  const completed = useGameStore((s) => s.state.challenges.completed);
  const activeId = useGameStore((s) => s.state.challenges.activeId);
  const startChallenge = useGameStore((s) => s.startChallenge);
  const abandonChallenge = useGameStore((s) => s.abandonChallenge);

  return (
    <div>
      {activeId && (
        <div className="card" style={{ borderColor: 'var(--accent)' }}>
          <div className="card__title">🎯 Active Challenge</div>
          <div className="tech-card__desc">
            You're currently running <strong>{CHALLENGES.find((c) => c.id === activeId)?.name}</strong>. Its restriction applies
            until you complete the goal or reset.
          </div>
          <button className="buy-btn" style={{ background: 'var(--danger)', color: 'white' }} onClick={abandonChallenge}>
            Abandon Challenge
          </button>
        </div>
      )}

      <div className="section-label">Available Challenges</div>
      {CHALLENGES.map((c) => {
        const done = !!completed[c.id];
        const isActive = activeId === c.id;
        return (
          <div className="tech-card" key={c.id}>
            <div className="tech-card__top">
              <span className="tech-card__icon">{c.icon}</span>
              <span className="tech-card__name">{c.name}</span>
              {done && <span className="badge badge--good" style={{ marginLeft: 'auto' }}>COMPLETED</span>}
            </div>
            <div className="tech-card__desc">{c.description}</div>
            <div className="tech-card__meta">
              <span>Goal: {c.goal.description}</span>
            </div>
            <div className="tech-card__meta">
              <span>Reward: {c.rewardDescription}</span>
            </div>
            {!isActive && !activeId && (
              <button className="buy-btn" onClick={() => startChallenge(c.id)}>
                {done ? 'Run Again' : 'Start Challenge'} (resets current Earth)
              </button>
            )}
            {isActive && <span className="badge">IN PROGRESS</span>}
          </div>
        );
      })}
    </div>
  );
}
