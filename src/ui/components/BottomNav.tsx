export type ScreenId =
  | 'home'
  | 'atmosphere'
  | 'technology'
  | 'production'
  | 'prestige'
  | 'achievements'
  | 'challenges'
  | 'statistics'
  | 'settings';

/**
 * Nine destinations have to fit across the narrowest Android phone (360dp), so
 * labels are kept short enough to render at the resulting ~40dp column width.
 * `fullLabel` carries the unabbreviated name for screen readers.
 */
const ITEMS: { id: ScreenId; label: string; fullLabel: string; icon: string }[] = [
  { id: 'home', label: 'Home', fullLabel: 'Home', icon: '🌍' },
  { id: 'atmosphere', label: 'Air', fullLabel: 'Atmosphere', icon: '☁️' },
  { id: 'technology', label: 'Tech', fullLabel: 'Technology', icon: '🔬' },
  { id: 'production', label: 'Output', fullLabel: 'Production', icon: '🏭' },
  { id: 'prestige', label: 'Reset', fullLabel: 'Prestige', icon: '✨' },
  { id: 'challenges', label: 'Trials', fullLabel: 'Challenges', icon: '🎯' },
  { id: 'achievements', label: 'Awards', fullLabel: 'Achievements', icon: '🏆' },
  { id: 'statistics', label: 'Stats', fullLabel: 'Statistics', icon: '📊' },
  { id: 'settings', label: 'Setup', fullLabel: 'Settings', icon: '⚙️' },
];

export default function BottomNav({ active, onChange }: { active: ScreenId; onChange: (id: ScreenId) => void }) {
  return (
    <nav className="bottom-nav" aria-label="Primary">
      {ITEMS.map((item) => (
        <button
          key={item.id}
          type="button"
          data-screen={item.id}
          aria-label={item.fullLabel}
          aria-current={active === item.id ? 'page' : undefined}
          className={`bottom-nav__item ${active === item.id ? 'bottom-nav__item--active' : ''}`}
          onClick={() => onChange(item.id)}
        >
          <span className="bottom-nav__icon" aria-hidden="true">{item.icon}</span>
          <span className="bottom-nav__label">{item.label}</span>
        </button>
      ))}
    </nav>
  );
}
