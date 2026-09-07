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
const { purchaseTechnology } = await import('../src/engine/economy');
const { ALL_TECHNOLOGIES, isTechAvailable } = await import('../src/engine/technologies');
const { checkMilestones } = await import('../src/engine/milestones');
const { gasDisplayConcentration } = await import('../src/engine/climate');

type GameState = ReturnType<typeof createNewGame>;

const prestigeUpgrades = process.env.PRESTIGE ? JSON.parse(process.env.PRESTIGE) : {};
const prestige = computePrestigeMultipliers(prestigeUpgrades);

/**
 * Spend-down policy: repeatedly buy the highest-tier affordable technology
 * until nothing is affordable. Buying the most advanced thing you can pay for
 * is what an engaged player does, and because a generator's price climbs with
 * every unit owned, the policy naturally falls back to cheaper tiers once the
 * top one outruns the wallet.
 */
function spend(state: GameState): GameState {
  let s = state;
  for (let guard = 0; guard < 2000; guard++) {
    const candidates = ALL_TECHNOLOGIES.filter((t) => isTechAvailable(t, s.techOwned)).sort((a, b) => b.tier - a.tier);
    let bought = false;
    for (const tech of candidates) {
      const r = purchaseTechnology(s, tech.id, 1, prestige);
      if (r.success) {
        s = r.state;
        bought = true;
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

let state = createNewGame(0);
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
