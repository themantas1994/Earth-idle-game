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
    title: 'Step 1 — Your First Fire',
    body: 'A lightning strike left a tree burning, and you have kept it alight. That Natural Fire is already producing Energy for you — nothing to tap, nothing to hold. Watch the Economy card fill up.',
    navigateTo: 'home',
    autoAdvanceWhen: (s) => s.resources.energy.gt(0),
    cta: 'Continue',
  },
  {
    title: 'Step 2 — Build More Fires',
    body: 'The Production tab is where you buy buildings. Spend your Energy on a second Natural Fire, then a Controlled Fire — every fire you light makes the next one affordable sooner.',
    navigateTo: 'production',
    autoAdvanceWhen: (s) => (s.techOwned.controlled_fire ?? 0) > 0,
    cta: 'Continue',
  },
  {
    title: 'First Emissions',
    body: 'Your fires are now producing CO₂ around the clock. Check the Atmosphere tab any time to see exactly how much heating each gas is responsible for.',
    cta: 'Got it',
  },
  {
    title: 'Step 3 — Research',
    body: 'The Technology tab is the research tree: one-time unlocks and permanent multipliers, paid for with Research. It never sells buildings — those always live on Production. Unlock Cooking to begin.',
    navigateTo: 'technology',
    autoAdvanceWhen: (s) => (s.techOwned.cooking ?? 0) > 0,
    cta: 'Continue',
  },
  {
    title: 'Step 4 — Two Tabs, One Loop',
    body: 'That is the whole game: research an unlock on Technology, then build what it unlocked on Production. Work toward Early Agriculture — farms bring methane from livestock and nitrous oxide from fertilizer.',
    navigateTo: 'technology',
    autoAdvanceWhen: (s) => (s.techOwned.early_agriculture ?? 0) > 0,
    cta: 'Continue',
  },
  {
    title: 'Step 5 — Industrial Revolution',
    body: 'Research the Steam Engine to enter the Industrial era, opening up Coal Mining, Factories, and far dirtier production than fire ever managed.',
    navigateTo: 'technology',
    autoAdvanceWhen: (s) => (s.techOwned.steam_engine ?? 0) > 0,
    cta: 'Continue',
  },
  {
    title: 'Step 6 — Fossil Fuels',
    body: 'Build Oil Drilling on the Production tab. Oil unlocks an entire branch of increasingly powerful — and increasingly dirty — technology.',
    navigateTo: 'production',
    autoAdvanceWhen: (s) => (s.techOwned.oil_drilling ?? 0) > 0,
    cta: 'Continue',
  },
  {
    title: 'Step 7 — Watch the World React',
    body: 'Your Radiative Forcing and Habitability are already moving. As they do, the World News feed on the Home screen will start reporting what your emissions are doing to the planet.',
    navigateTo: 'home',
    cta: 'Continue',
  },
  {
    title: 'Step 8 — The Long Game',
    body: 'This Earth will take a long time to kill — days of real time, not minutes. It keeps running while you are away, so check in, spend what has piled up, and let it burn. When habitability reaches zero you can RESET EARTH and bank Earth Points that make every future civilization faster.',
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

  // A non-blocking banner (not a modal overlay): several steps require the player to
  // press something in the game underneath (a Production or Technology card), so the
  // tutorial must never capture clicks meant for the game itself.
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
