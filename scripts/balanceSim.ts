/**
 * Headless pacing harness.
 *
 * Plays a whole run with a simple "efficient player" policy and reports how
 * long it takes to reach collapse, when each technology is first bought, and
 * when each news milestone fires. This is what the numbers in
 * `constants.ts > BALANCE` were tuned against — pacing changes should be
 * measured with it rather than guessed at.
 *
 *   npx vite-node scripts/balanceSim.ts
 *   BALANCE='{"generatorCostGrowthPerTier":2.5}' npx vite-node scripts/balanceSim.ts
 *
 * `BALANCE` accepts a JSON patch over the shipped curve constants, which is
 * why the game modules are imported dynamically below: technology
 * definitions evaluate their costs at module load, so the patch has to land
 * before they are pulled in.
 */
const { BALANCE } = await import('../src/engine/constants');
if (process.env.BALANCE) Object.assign(BALANCE, JSON.parse(process.env.BALANCE));

const { createNewGame } = await import('../src/engine/gameState');
const { simulateStep, computeCivLevel } = await import('../src/engine/simulation');
const { computePrestigeMultipliers } = await import('../src/engine/prestige');
const { purchaseTechnology, grantTechnology } = await import('../src/engine/economy');
const { ALL_TECHNOLOGIES, isTechAvailable } = await import('../src/engine/technologies');
const { checkMilestones } = await import('../src/engine/milestones');
const { gasDisplayConcentration } = await import('../src/engine/climate');

type GameState = ReturnType<typeof createNewGame>;

const prestigeUpgrades = process.env.PRESTIGE ? JSON.parse(process.env.PRESTIGE) : {};
const prestige = computePrestigeMultipliers(prestigeUpgrades);

/**
 * Spend-down policy, modelled on what an engaged player actually does rather
 * than on what is locally optimal.
 *
 * Real players chase *new things* first — an unbought technology is a new
 * card, a new headline, a new branch — and only then pour whatever is left
 * into deepening what they already run. A policy that instead always buys the
 * highest tier it can afford tunnels down a single branch, leaves a third of
 * the tree unbought, and reports pacing nobody will experience.
 *
 * POLICY=greedy restores the old highest-tier-first behaviour, which is still
 * useful as an upper bound on how fast the tree *can* be rushed.
 */
const POLICY = process.env.POLICY ?? 'explorer';

let purchaseEvents = 0;

function spend(state: GameState): GameState {
  let s = state;
  for (let guard = 0; guard < 4000; guard++) {
    const available = ALL_TECHNOLOGIES.filter((t) => isTechAvailable(t, s.techOwned));

    // Pass one: anything never bought before, cheapest first, so breadth
    // opens up before depth soaks up the wallet.
    const frontier =
      POLICY === 'greedy'
        ? []
        : available.filter((t) => (s.techOwned[t.id] ?? 0) === 0).sort((a, b) => a.tier - b.tier);

    let bought = false;
    for (const tech of frontier) {
      const r = purchaseTechnology(s, tech.id, 1, prestige);
      if (r.success) {
        s = r.state;
        bought = true;
        purchaseEvents++;
        break;
      }
    }
    if (bought) continue;

    // Pass two: deepen. Buying the most advanced building affordable is what
    // a player does with the change, and because a generator's price climbs
    // with every unit owned the policy naturally falls back down the tiers
    // once the top one outruns the wallet.
    for (const tech of [...available].sort((a, b) => b.tier - a.tier)) {
      const r = purchaseTechnology(s, tech.id, 1, prestige);
      if (r.success) {
        s = r.state;
        bought = true;
        purchaseEvents++;
        break;
      }
    }
    if (!bought) break;
  }
  return s;
}

const DT = Number(process.env.DT ?? 10);
const MAX_SECONDS = Number(process.env.MAX_DAYS ?? 40) * 24 * 3600;
/** Hours per day the player is actually present to spend resources; the rest ticks unattended. */
const ACTIVE_HOURS_PER_DAY = Number(process.env.ACTIVE_HOURS ?? 24);
/**
 * Seconds between spending opportunities while present. Real players check in
 * a handful of times a day and buy in bulk; a policy that spends every tick
 * instead pours everything into whatever is cheapest and inflates its
 * per-unit costs, which reads as a much slower run than anyone will actually
 * have. Set this to something realistic (e.g. 3600) before trusting a number.
 */
const SPEND_INTERVAL = Number(process.env.SPEND_INTERVAL ?? DT);

/**
 * A fresh Earth as the *game* would hand it over, not as `createNewGame`
 * leaves it: post-reset the store grants every starting technology, generator
 * and resource the player's prestige upgrades have earned. Skipping that step
 * made every PRESTIGE=... run report a second Earth that started from
 * nothing, which is the one thing a second Earth never does.
 */
function freshRun() {
  let s = createNewGame(0);
  for (const techId of prestige.startingTechIds) s = grantTechnology(s, techId);
  for (const [techId, extraUnits] of Object.entries(prestige.startingGenerators)) {
    s = { ...s, techOwned: { ...s.techOwned, [techId]: (s.techOwned[techId] ?? 0) + extraUnits } };
  }
  for (const [resId, amount] of Object.entries(prestige.startingResources)) {
    s = { ...s, resources: { ...s.resources, [resId]: s.resources[resId as keyof typeof s.resources].add(amount ?? 0) } };
  }
  return s;
}

let state = freshRun();
const firstBuy: Record<string, number> = {};
const milestoneAt: Record<string, number> = {};
let t = 0;
let collapsedAt: number | null = null;
/** TRACE=<seconds> prints a climate row at that cadence, for sanity-checking the gas mix. */
const TRACE = Number(process.env.TRACE ?? 0);
const trace: string[] = [];
let nextTraceAt = 0;

for (; t < MAX_SECONDS; t += DT) {
  const hourOfDay = (t / 3600) % 24;
  if (hourOfDay < ACTIVE_HOURS_PER_DAY && t % SPEND_INTERVAL < DT) {
    const before = { ...state.techOwned };
    state = spend(state);
    for (const id of Object.keys(state.techOwned)) {
      if (!before[id] && firstBuy[id] === undefined) firstBuy[id] = t;
    }
  }

  state = simulateStep(state, DT, prestige).state;

  for (const m of checkMilestones(state)) {
    milestoneAt[m.id] = t;
    state = { ...state, milestonesTriggered: { ...state.milestonesTriggered, [m.id]: true } };
  }

  if (TRACE > 0 && t >= nextTraceAt) {
    nextTraceAt = t + TRACE;
    const f = state.forcing.perGas;
    trace.push(
      [
        `${(t / 86400).toFixed(2)}d`.padStart(7),
        `${state.temperatureAnomalyC.toFixed(2)}C`.padStart(8),
        `co2 ${gasDisplayConcentration('co2', state.atmosphere).toFixed(0)}ppm`.padStart(14),
        `ch4 ${gasDisplayConcentration('ch4', state.atmosphere).toFixed(0)}ppb`.padStart(14),
        `pH ${state.oceanPh.toFixed(2)}`.padStart(8),
        `sea +${state.seaLevelRiseMeters.toFixed(2)}m`.padStart(12),
        `hab ${(state.habitability.fraction * 100).toFixed(1)}%`.padStart(11),
        `forcing co2 ${f.co2.toFixed(2)} ch4 ${f.ch4.toFixed(2)} n2o ${f.n2o.toFixed(2)} o3 ${f.o3.toFixed(2)} f-gas ${f.fluorinated.toFixed(2)} h2o ${f.h2o.toFixed(2)}`,
      ].join('  '),
    );
  }

  if (state.collapsed) {
    collapsedAt = t;
    break;
  }
}

const hrs = (s: number) => (s >= 3600 * 48 ? `${(s / 86400).toFixed(2)}d` : `${(s / 3600).toFixed(2)}h`);

/**
 * The pacing question this game actually lives or dies on is not "how long is
 * a run" but "how long am I ever left with nothing to do". A dead zone is a
 * stretch where nothing new became purchasable, and a run with a six-hour one
 * in the middle is a run people put down.
 */
const unlockOrder = Object.entries(firstBuy).sort((a, b) => a[1] - b[1]);
const gaps = unlockOrder
  .slice(1)
  .map(([id, at], i) => ({ gapSeconds: at - unlockOrder[i][1], from: unlockOrder[i][0], to: id, at: unlockOrder[i][1] }))
  .sort((a, b) => b.gapSeconds - a.gapSeconds);
const worstGap = gaps[0]?.gapSeconds ?? 0;
const worstGapAt = gaps[0]?.at ?? 0;

console.log('=== technologies (time of first purchase) ===');
for (const tech of ALL_TECHNOLOGIES) {
  const at = firstBuy[tech.id];
  console.log(`  t${String(tech.tier).padStart(2)}  ${(at === undefined ? '—' : hrs(at)).padStart(9)}  ${tech.id}`);
}

if (trace.length > 0) {
  console.log('\n=== climate trace ===');
  for (const row of trace) console.log(row);
}

console.log('\n=== milestones ===');
for (const [id, at] of Object.entries(milestoneAt)) console.log(`  ${hrs(at).padStart(9)}  ${id}`);

const unreached = ALL_TECHNOLOGIES.filter((tech) => firstBuy[tech.id] === undefined);
console.log('\n=== outcome ===');
console.log('collapsed at:  ', collapsedAt === null ? `NOT within ${hrs(MAX_SECONDS)}` : hrs(collapsedAt));
console.log('never bought:  ', `${unreached.length}/${ALL_TECHNOLOGIES.length}`, unreached.map((u) => u.id).join(', ') || '(none)');
console.log('temp anomaly:  ', state.temperatureAnomalyC.toFixed(2), '°C');
console.log('habitability:  ', (state.habitability.fraction * 100).toFixed(3), '%');
console.log('forcing:       ', state.forcing.total.toFixed(2), 'W/m²');
console.log('civ level:     ', computeCivLevel(state.techOwned));
console.log('milestones hit:', Object.keys(milestoneAt).length, '/', (await import('../src/engine/milestones')).MILESTONES.length);
console.log('purchases made:', purchaseEvents, `(one every ${hrs((collapsedAt ?? t) / Math.max(1, purchaseEvents))})`);
console.log('longest dead zone:', hrs(worstGap), 'starting at', hrs(worstGapAt));
console.log('worst content gaps:');
for (const g of gaps.slice(0, 5)) {
  console.log(`  ${hrs(g.gapSeconds).padStart(8)} waiting at ${hrs(g.at).padStart(8)} — after ${g.from}, next was ${g.to}`);
}
const deepest = ALL_TECHNOLOGIES.map((tech) => [tech.id, state.techOwned[tech.id] ?? 0] as const)
  .sort((a, b) => b[1] - a[1])
  .slice(0, 5);
console.log('deepest stacks: ', deepest.map(([id, n]) => `${id}×${n}`).join(', '));
console.log('final energy:  ', state.resources.energy.toString());
console.log('news feed len: ', state.newsFeed.length);

const { calculatePrestigeGain } = await import('../src/engine/prestige');
const { PRESTIGE_UPGRADES } = await import('../src/engine/prestige');
const earned = calculatePrestigeGain({
  totalGasProducedKg: state.runStats.totalGasProducedKg,
  peakForcingWm2: state.runStats.peakForcingWm2,
  civLevel: computeCivLevel(state.techOwned),
  runDurationSeconds: collapsedAt ?? t,
});
const { sumGasTotals } = await import('../src/engine/simulation');
console.log('total gas kg:  ', sumGasTotals(state.runStats.totalGasProducedKg).toString());
console.log('peak forcing:  ', state.runStats.peakForcingWm2.toFixed(2));
console.log('peak gas rate: ', state.runStats.peakGasProductionRateKgPerS.toString(), 'kg/s');
console.log('earth points:  ', earned.toString());
const affordable = PRESTIGE_UPGRADES.filter((u) => earned.gte(u.baseCost)).map((u) => u.id);
console.log('  affords now: ', affordable.join(', ') || '(nothing)');
