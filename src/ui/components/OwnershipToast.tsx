import { useEffect } from 'react';
import { useGameStore } from '../../store/useGameStore';
import { TECH_BY_ID } from '../../engine/technologies';
import { useNumberFormat } from '../hooks';

/**
 * Celebration for crossing a generator's ownership threshold. Short-lived and
 * loud: it exists purely so that the purchase which earned the doubling is
 * the one the player sees it land on.
 */
export default function OwnershipToast() {
  const toast = useGameStore((s) => s.activeOwnershipToast);
  const dismiss = useGameStore((s) => s.dismissOwnershipToast);
  const { format } = useNumberFormat();

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(dismiss, 3200);
    return () => clearTimeout(timer);
  }, [toast, dismiss]);

  if (!toast) return null;
  const tech = TECH_BY_ID[toast.techId];
  if (!tech) return null;

  return (
    <div className="toast toast--bonus" onClick={dismiss}>
      {tech.icon} {tech.name} ×{format(toast.atUnits)} — output doubled (×{format(toast.multiplier)} total)
    </div>
  );
}
