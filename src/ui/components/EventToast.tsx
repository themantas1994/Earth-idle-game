import { useEffect } from 'react';
import { useGameStore } from '../../store/useGameStore';
import { RANDOM_EVENT_BY_ID } from '../../engine/events';

export default function EventToast() {
  const eventId = useGameStore((s) => s.activeEventToast);
  const dismiss = useGameStore((s) => s.dismissEventToast);

  useEffect(() => {
    if (!eventId) return;
    const timer = setTimeout(dismiss, 4000);
    return () => clearTimeout(timer);
  }, [eventId, dismiss]);

  if (!eventId) return null;
  const def = RANDOM_EVENT_BY_ID[eventId];
  if (!def) return null;

  return (
    <div className="toast" style={{ borderColor: def.isNegative ? 'var(--danger)' : 'var(--accent)' }} onClick={dismiss}>
      {def.icon} {def.name} — {def.description}
    </div>
  );
}
