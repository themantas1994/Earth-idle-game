import { useEffect } from 'react';
import { useGameStore } from '../../store/useGameStore';
import { MILESTONE_BY_ID } from '../../engine/milestones';

/**
 * Breaking-news banner for a milestone headline. Longer-lived than the
 * random-event toast because there is an actual sentence to read, and
 * dismissable by tap for players who would rather read it in the feed.
 */
export default function MilestoneToast() {
  const milestoneId = useGameStore((s) => s.activeMilestoneToast);
  const dismiss = useGameStore((s) => s.dismissMilestoneToast);

  useEffect(() => {
    if (!milestoneId) return;
    const timer = setTimeout(dismiss, 9000);
    return () => clearTimeout(timer);
  }, [milestoneId, dismiss]);

  if (!milestoneId) return null;
  const def = MILESTONE_BY_ID[milestoneId];
  if (!def) return null;

  return (
    <div className="news-toast" onClick={dismiss}>
      <div className="news-toast__kicker">📰 Breaking · {def.source}</div>
      <div className="news-toast__headline">{def.icon} {def.headline}</div>
      <div className="news-toast__body">{def.body}</div>
    </div>
  );
}
