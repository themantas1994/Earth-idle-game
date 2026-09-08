import { useEffect, useState } from 'react';
import { useGameStore } from '../../store/useGameStore';
import { NumberFormatMode } from '../../engine/format';
import { Settings } from '../../engine/gameState';
import { isAdConsentFormAvailable, showAdPrivacyOptions } from '../../platform/ads';

function Switch({ on, onToggle }: { on: boolean; onToggle: () => void }) {
  return (
    <button className={`switch ${on ? 'switch--on' : ''}`} onClick={onToggle}>
      <span className="switch__knob" />
    </button>
  );
}

const FORMAT_OPTIONS: { id: NumberFormatMode; label: string }[] = [
  { id: 'compact', label: 'Compact (1.2M)' },
  { id: 'scientific', label: 'Scientific (1.20e+6)' },
  { id: 'engineering', label: 'Engineering (1.20e+6)' },
  { id: 'full', label: 'Full (1,200,000)' },
];

export default function SettingsScreen() {
  const settings = useGameStore((s) => s.state.settings);
  const updateSettings = useGameStore((s) => s.updateSettings);

  // AdMob expects players who were shown a consent message to be able to come
  // back and change it. Only players who got one have a form to reopen, so the
  // row is hidden everywhere else rather than offering a button that fails.
  const [canEditAdConsent, setCanEditAdConsent] = useState(false);
  useEffect(() => setCanEditAdConsent(isAdConsentFormAvailable()), []);

  const toggle = (key: keyof Settings) => updateSettings({ [key]: !settings[key] } as Partial<Settings>);

  return (
    <div>
      <div className="section-label">Number Format</div>
      <div className="card">
        {FORMAT_OPTIONS.map((opt) => (
          <button
            key={opt.id}
            className={`qty-btn ${settings.numberFormat === opt.id ? 'qty-btn--active' : ''}`}
            style={{ width: '100%', marginBottom: 6, textAlign: 'left', padding: '10px 12px' }}
            onClick={() => updateSettings({ numberFormat: opt.id })}
          >
            {opt.label}
          </button>
        ))}
      </div>

      <div className="section-label">Appearance</div>
      <div className="card">
        {(['system', 'light', 'dark'] as const).map((mode) => (
          <button
            key={mode}
            className={`qty-btn ${settings.darkMode === mode ? 'qty-btn--active' : ''}`}
            style={{ width: '100%', marginBottom: 6, textAlign: 'left', padding: '10px 12px', textTransform: 'capitalize' }}
            onClick={() => updateSettings({ darkMode: mode })}
          >
            {mode}
          </button>
        ))}
        <div className="toggle-row">
          <span>Reduced Animations</span>
          <Switch on={settings.reducedAnimations} onToggle={() => toggle('reducedAnimations')} />
        </div>
      </div>

      <div className="section-label">Gameplay</div>
      <div className="card">
        <div className="toggle-row">
          <span>Confirm Before Reset</span>
          <Switch on={settings.confirmReset} onToggle={() => toggle('confirmReset')} />
        </div>
        <div className="toggle-row">
          <span>Offline Progress</span>
          <Switch on={settings.offlineProgressEnabled} onToggle={() => toggle('offlineProgressEnabled')} />
        </div>
      </div>

      <div className="section-label">Audio & Feedback</div>
      <div className="card">
        <div className="toggle-row">
          <span>Sound Effects</span>
          <Switch on={settings.soundEnabled} onToggle={() => toggle('soundEnabled')} />
        </div>
        <div className="toggle-row">
          <span>Music</span>
          <Switch on={settings.musicEnabled} onToggle={() => toggle('musicEnabled')} />
        </div>
        <div className="toggle-row">
          <span>Vibration</span>
          <Switch on={settings.vibrationEnabled} onToggle={() => toggle('vibrationEnabled')} />
        </div>
      </div>

      {canEditAdConsent && (
        <>
          <div className="section-label">Privacy</div>
          <div className="card">
            <button
              className="qty-btn"
              style={{ width: '100%', textAlign: 'left', padding: '10px 12px' }}
              onClick={() => void showAdPrivacyOptions()}
            >
              Ad Privacy Choices
            </button>
          </div>
        </>
      )}
    </div>
  );
}
