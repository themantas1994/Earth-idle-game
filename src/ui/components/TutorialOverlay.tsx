import { useEffect } from 'react';
import { useGameStore } from '../../store/useGameStore';
import { GameState } from '../../engine/gameState';
import { ScreenId } from './BottomNav';

interface TutorialStep {
  title: string;
  body: string;
  navigateTo?: ScreenId;
  /** When provided, the step auto-advances once this becomes true; otherwise it waits for the CTA button. */
  autoAdvanceWhen?: (state: GameState) => boolean;
  cta: string;
}

const STEPS: TutorialStep[] = [
  {
    title: 'WELCOME TO EARTH',
    body: 'Your objective is simple. Build civilization. Advance technology. Produce greenhouse gases. Heat the planet. When Earth becomes uninhabitable... start again. Each destroyed Earth makes the next civilization faster.',
    cta: "Let's begin",
  },
  {
    title: 'Step 1 — Tap to Produce',
    body: 'Tap the glowing button on the Home screen to gather Energy by hand. Every civilization starts with a single spark.',
    autoAdvanceWhen: (s) => s.resources.energy.gt(0),
    cta: 'I tapped it',
  },
  {
    title: 'Step 2 — Discover Fire',
    body: 'Open the Technology tab and unlock Controlled Fire. This is humanity\'s first deliberate act of combustion — and its first greenhouse gas emission.',
    navigateTo: 'technology',
    autoAdvanceWhen: (s) => (s.techOwned.controlled_fire ?? 0) > 0,
    cta: 'Continue',
  },
  {
    title: 'First Emissions',
    body: 'Controlled Fire is now producing CO₂ around the clock. Check the Atmosphere tab any time to see exactly how much heating each gas is responsible for.',
    cta: 'Got it',
  },
  {
    title: 'Step 3 — Agriculture',
    body: 'Unlock Early Agriculture, then Farming. Agriculture introduces two more greenhouse gases: methane (CH₄) from livestock and rice paddies, and nitrous oxide (N₂O) from fertilizer.',
    navigateTo: 'technology',
    autoAdvanceWhen: (s) => (s.techOwned.early_agriculture ?? 0) > 0,
    cta: 'Continue',
  },
  {
    title: 'Step 4 — Industrial Revolution',
    body: 'Unlock the Steam Engine to enter the Industrial era, opening up Coal Mining, Factories, and much faster production.',
    navigateTo: 'technology',
    autoAdvanceWhen: (s) => (s.techOwned.steam_engine ?? 0) > 0,
    cta: 'Continue',
  },
  {
    title: 'Step 5 — Build a Factory',
    body: 'Unlock Factories. From here on, most of your production comes from buildings, not taps.',
    navigateTo: 'technology',
    autoAdvanceWhen: (s) => (s.techOwned.factories ?? 0) > 0,
    cta: 'Continue',
  },
  {
    title: 'Step 6 — Electricity',
    body: 'Unlock the Generator and a Coal Power Plant to electrify your civilization — and burn through coal even faster.',
    navigateTo: 'technology',
    autoAdvanceWhen: (s) => (s.techOwned.coal_power_plant ?? 0) > 0,
    cta: 'Continue',
  },
  {
    title: 'Step 7 — Fossil Fuels',
    body: 'Start Oil Drilling. Oil unlocks an entire branch of increasingly powerful (and increasingly dirty) technology.',
    navigateTo: 'technology',
    autoAdvanceWhen: (s) => (s.techOwned.oil_drilling ?? 0) > 0,
    cta: 'Continue',
  },
  {
    title: 'Step 8 — Watch the Temperature',
    body: 'Check the Home screen: your Radiative Forcing and Habitability are changing. Every technology you unlock pushes them further.',
    navigateTo: 'home',
    cta: 'Continue',
  },
  {
    title: 'Step 9 — The Reset',
    body: 'Keep growing your civilization. Eventually Earth\'s habitability will reach zero and you\'ll be able to RESET EARTH — earning Earth Points that make your next civilization faster. How fast can you destroy this one?',
    cta: 'Start playing',
  },
];

export default function TutorialOverlay({ onNavigate }: { onNavigate: (id: ScreenId) => void }) {
  const state = useGameStore((s) => s.state);
  const advanceTutorial = useGameStore((s) => s.advanceTutorial);
  const skipTutorial = useGameStore((s) => s.skipTutorial);

  const step = STEPS[state.tutorial.step];
  const done = state.tutorial.completed || state.tutorial.skipped || !step;

  useEffect(() => {
    if (done) return;
    if (step.navigateTo) onNavigate(step.navigateTo);
  }, [state.tutorial.step, done]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (done || !step.autoAdvanceWhen) return;
    if (step.autoAdvanceWhen(state)) {
      const timer = setTimeout(advanceTutorial, 600);
      return () => clearTimeout(timer);
    }
  }, [state, done, step, advanceTutorial]);

  if (done) return null;

  const isLast = state.tutorial.step >= STEPS.length - 1;
  const waitingOnAction = !!step.autoAdvanceWhen && !step.autoAdvanceWhen(state);

  // A non-blocking banner (not a modal overlay): several steps require the player to tap
  // something in the game underneath (the tap button, a Technology card), so the tutorial
  // must never capture clicks meant for the game itself.
  return (
    <div className="tutorial-banner">
      <div className="section-label" style={{ margin: '0 0 4px' }}>
        Tutorial · {state.tutorial.step + 1}/{STEPS.length}
      </div>
      <h2 style={{ fontSize: '1rem', margin: '0 0 6px' }}>{step.title}</h2>
      <p style={{ fontSize: '0.82rem', color: 'var(--text-dim)', lineHeight: 1.45, margin: '0 0 10px' }}>{step.body}</p>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        {!waitingOnAction && (
          <button className="modal-btn modal-btn--primary" onClick={isLast ? skipTutorial : advanceTutorial}>
            {step.cta}
          </button>
        )}
        {waitingOnAction && (
          <div style={{ flex: 1, fontSize: '0.76rem', color: 'var(--text-faint)' }}>Waiting for you to do it in-game…</div>
        )}
        <button onClick={skipTutorial} style={{ background: 'none', border: 'none', color: 'var(--text-faint)', fontSize: '0.72rem' }}>
          Skip
        </button>
      </div>
    </div>
  );
}
