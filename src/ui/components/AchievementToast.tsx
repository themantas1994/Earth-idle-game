import { useEffect, useState } from 'react';
import { useGameStore } from '../../store/useGameStore';
import { ACHIEVEMENT_BY_ID } from '../../engine/achievements';

export default function AchievementToast() {
  const queue = useGameStore((s) => s.newlyUnlockedAchievements);
  const clear = useGameStore((s) => s.clearNewAchievements);
  const [current, setCurrent] = useState<string | null>(null);

  useEffect(() => {
    if (queue.length > 0 && !current) {
      setCurrent(queue[0]);
      clear();
    }
  }, [queue, current, clear]);

  useEffect(() => {
    if (!current) return;
    const timer = setTimeout(() => setCurrent(null), 3500);
    return () => clearTimeout(timer);
  }, [current]);

  if (!current) return null;
  const achievement = ACHIEVEMENT_BY_ID[current];
  if (!achievement) return null;

  return (
    <div className="toast" style={{ top: 'calc(56px + var(--safe-top))', borderColor: 'var(--warning)' }}>
      🏆 Achievement Unlocked: {achievement.name}
    </div>
  );
}
