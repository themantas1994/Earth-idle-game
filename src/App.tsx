import { useCallback, useEffect, useRef, useState } from 'react';
import { useGameStore } from './store/useGameStore';
import BottomNav, { ScreenId } from './ui/components/BottomNav';
import HomeScreen from './ui/screens/HomeScreen';
import AtmosphereScreen from './ui/screens/AtmosphereScreen';
import TechnologyScreen from './ui/screens/TechnologyScreen';
import ProductionScreen from './ui/screens/ProductionScreen';
import PrestigeScreen from './ui/screens/PrestigeScreen';
import AchievementsScreen from './ui/screens/AchievementsScreen';
import ChallengesScreen from './ui/screens/ChallengesScreen';
import StatisticsScreen from './ui/screens/StatisticsScreen';
import SettingsScreen from './ui/screens/SettingsScreen';
import OfflineModal from './ui/components/OfflineModal';
import UninhabitableModal from './ui/components/UninhabitableModal';
import CollapseModal from './ui/components/CollapseModal';
import EventToast from './ui/components/EventToast';
import MilestoneToast from './ui/components/MilestoneToast';
import AchievementToast from './ui/components/AchievementToast';
import TutorialOverlay from './ui/components/TutorialOverlay';
import { RUN_LABEL } from './engine/constants';
import { formatTemperature, formatPercent } from './engine/format';
import { onHardwareBack, exitApp, applyStatusBarTheme, hideSplash } from './platform/native';

export default function App() {
  const loaded = useGameStore((s) => s.loaded);
  const init = useGameStore((s) => s.init);
  const runNumber = useGameStore((s) => s.state.runNumber);
  const temperature = useGameStore((s) => s.state.temperatureAnomalyC);
  const habitability = useGameStore((s) => s.state.habitability.fraction);
  const darkMode = useGameStore((s) => s.state.settings.darkMode);
  const collapsed = useGameStore((s) => s.state.collapsed);
  const [screen, setScreen] = useState<ScreenId>('home');

  // Screens the player navigated away from, most recent last. Android's back
  // gesture unwinds this before it is allowed to leave the app.
  const historyRef = useRef<ScreenId[]>([]);

  const navigate = useCallback((next: ScreenId) => {
    setScreen((current) => {
      if (next === current) return current;
      historyRef.current.push(current);
      return next;
    });
  }, []);

  useEffect(() => {
    init();
  }, [init]);

  useEffect(() => {
    hideSplash();
  }, []);

  // Without this, Android's back button closes the app from any screen — the
  // player loses their place on a mis-swipe. Back now walks the screen history
  // and only exits from Home.
  useEffect(() => {
    let disposed = false;
    let dispose: (() => void) | undefined;

    void onHardwareBack(() => {
      const previous = historyRef.current.pop();
      if (previous) setScreen(previous);
      else exitApp();
    }).then((d) => {
      if (disposed) d();
      else dispose = d;
    });

    return () => {
      disposed = true;
      dispose?.();
    };
  }, []);

  useEffect(() => {
    if (darkMode === 'system') delete document.documentElement.dataset.theme;
    else document.documentElement.dataset.theme = darkMode;

    const isDark =
      darkMode === 'dark' ||
      (darkMode === 'system' &&
        typeof window !== 'undefined' &&
        window.matchMedia?.('(prefers-color-scheme: dark)').matches);
    void applyStatusBarTheme(Boolean(isDark));
  }, [darkMode]);

  // Visual progression: the planet's palette shifts from pristine green through
  // industrial haze to a burning endgame red as global temperature climbs.
  const era = collapsed ? 'collapsed' : temperature < 0.5 ? 'pristine' : temperature < 3 ? 'industrial' : temperature < 15 ? 'hot' : 'extreme';

  if (!loaded) {
    return (
      <div className="app-shell" style={{ alignItems: 'center', justifyContent: 'center', display: 'flex' }}>
        <div style={{ color: 'var(--text-dim)' }}>Loading Earth…</div>
      </div>
    );
  }

  const habitabilityColor = habitability > 0.5 ? 'var(--good)' : habitability > 0.15 ? 'var(--warning)' : 'var(--danger)';

  return (
    <div className="app-shell" data-era={era}>
      <header className="app-header">
        <div className="app-header__title">
          🌍 {RUN_LABEL}{runNumber > 0 ? ` ${runNumber}` : ''}
          <span className="app-header__run">Run #{runNumber}</span>
        </div>
        <div className="app-header__stats">
          <div className="stat-chip">
            <div className="stat-chip__label">Temp</div>
            <div className="stat-chip__value">{formatTemperature(temperature)}</div>
          </div>
          <div className="stat-chip">
            <div className="stat-chip__label">Habitability</div>
            <div className="stat-chip__value" style={{ color: habitabilityColor }}>{formatPercent(habitability)}</div>
          </div>
          <div className="stat-chip">
            <div className="stat-chip__label">Status</div>
            <div className="stat-chip__value" style={{ color: habitabilityColor }}>
              {habitability > 0.5 ? 'Stable' : habitability > 0.15 ? 'Strained' : habitability > 0 ? 'Critical' : 'Collapsed'}
            </div>
          </div>
        </div>
      </header>

      <TutorialOverlay onNavigate={navigate} />

      <main className="app-main">
        {screen === 'home' && <HomeScreen onNavigate={navigate} />}
        {screen === 'atmosphere' && <AtmosphereScreen />}
        {screen === 'technology' && <TechnologyScreen />}
        {screen === 'production' && <ProductionScreen />}
        {screen === 'prestige' && <PrestigeScreen />}
        {screen === 'achievements' && <AchievementsScreen />}
        {screen === 'challenges' && <ChallengesScreen />}
        {screen === 'statistics' && <StatisticsScreen />}
        {screen === 'settings' && <SettingsScreen />}
      </main>

      <BottomNav active={screen} onChange={navigate} />

      <OfflineModal />
      <UninhabitableModal />
      <CollapseModal />
      <EventToast />
      <MilestoneToast />
      <AchievementToast />
    </div>
  );
}
