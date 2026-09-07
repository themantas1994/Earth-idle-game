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

const ITEMS: { id: ScreenId; label: string; icon: string }[] = [
  { id: 'home', label: 'Home', icon: '🌍' },
  { id: 'atmosphere', label: 'Atmosphere', icon: '☁️' },
  { id: 'technology', label: 'Tech', icon: '🔬' },
  { id: 'production', label: 'Production', icon: '🏭' },
  { id: 'prestige', label: 'Prestige', icon: '✨' },
  { id: 'challenges', label: 'Challenges', icon: '🎯' },
  { id: 'achievements', label: 'Awards', icon: '🏆' },
  { id: 'statistics', label: 'Stats', icon: '📊' },
  { id: 'settings', label: 'Settings', icon: '⚙️' },
];

export default function BottomNav({ active, onChange }: { active: ScreenId; onChange: (id: ScreenId) => void }) {
  return (
    <nav className="bottom-nav">
      {ITEMS.map((item) => (
        <button
          key={item.id}
          className={`bottom-nav__item ${active === item.id ? 'bottom-nav__item--active' : ''}`}
          onClick={() => onChange(item.id)}
        >
          <span className="bottom-nav__icon">{item.icon}</span>
          {item.label}
        </button>
      ))}
    </nav>
  );
}
