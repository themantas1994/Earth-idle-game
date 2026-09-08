import { Technology } from './types';
import { unlockCost, generatorBaseCost, gasProduction, resourceProduction, generatorCostGrowth } from './scaling';

/**
 * The last third of a run, and the stretch that decides whether the whole
 * thing is worth finishing.
 *
 * It is a single chain rather than a branching tree — by this point the
 * fiction has one direction left to go — which makes it the part of the game
 * most vulnerable to dead air: one node per tier gap means one purchase, then
 * hours of watching a bar fill. So the chain is deliberately *dense*, with a
 * node at every tier and no gaps, and it alternates generators (something to
 * keep buying, and to farm ownership doublings on) with multipliers (a single
 * loud payoff). Widening the tier gaps here is the fastest way to make the
 * endgame feel like homework again.
 */
export const ENDGAME_TECHS: Technology[] = [
  {
    id: 'advanced_manufacturing',
    name: 'Advanced Manufacturing',
    branch: 'endgame',
    kind: 'multiplier',
    tier: 28,
    description: 'Robotic, self-replicating factories need no rest, no wages, and no limits.',
    icon: '🦾',
    requires: ['hyperscale_data_centers'],
    cost: [{ resource: 'energy', baseAmount: unlockCost(28) }],
    costGrowth: 1,
    maxOwned: 1,
    effect: { globalProductionMultiplier: 1.75 },
  },
  {
    id: 'fusion_power',
    name: 'Fusion Power',
    branch: 'endgame',
    kind: 'generator',
    tier: 29,
    description:
      'Thirty years away for a century, and then suddenly not. Limitless clean power arrives — and gets spent, immediately and entirely, on running everything else harder.',
    icon: '⚛️',
    requires: ['advanced_manufacturing'],
    cost: [{ resource: 'energy', baseAmount: generatorBaseCost(29) }],
    costGrowth: generatorCostGrowth(29),
    maxOwned: Infinity,
    effect: {
      // Fusion itself is clean; what it powers is not. Its emissions are the
      // industry it makes affordable, so they stay a tier behind its output.
      gasProductionPerUnit: { co2: gasProduction(26) },
      resourceProductionPerUnit: { energy: resourceProduction(29), research: resourceProduction(27) },
    },
  },
  {
    id: 'orbital_industry',
    name: 'Orbital Industry',
    branch: 'endgame',
    kind: 'generator',
    tier: 30,
    description: 'Factories in orbit promise a cleaner future — powered by an awful lot of rocket fuel to get there.',
    icon: '🛰️',
    requires: ['fusion_power'],
    cost: [{ resource: 'energy', baseAmount: generatorBaseCost(30) }],
    costGrowth: generatorCostGrowth(30),
    maxOwned: Infinity,
    effect: {
      gasProductionPerUnit: { co2: gasProduction(30) },
      resourceProductionPerUnit: { energy: resourceProduction(29) },
    },
  },
  {
    id: 'space_elevator',
    name: 'Space Elevator',
    branch: 'endgame',
    kind: 'multiplier',
    tier: 31,
    description:
      'A ribbon of carbon from the equator to geostationary orbit. Lifting a tonne off Earth stops being an event and starts being a Tuesday.',
    icon: '🛗',
    requires: ['orbital_industry'],
    cost: [{ resource: 'energy', baseAmount: unlockCost(31) }],
    costGrowth: 1,
    maxOwned: 1,
    effect: { branchProductionMultiplier: { branch: 'endgame', multiplier: 2.5 } },
  },
  {
    id: 'moon_mining',
    name: 'Moon Mining',
    branch: 'endgame',
    kind: 'generator',
    tier: 32,
    description: 'Helium-3 and rare earths from the Moon feed an industry that never once slows down to ask why.',
    icon: '🌕',
    requires: ['space_elevator'],
    cost: [{ resource: 'energy', baseAmount: generatorBaseCost(32) }],
    costGrowth: generatorCostGrowth(32),
    maxOwned: Infinity,
    effect: {
      gasProductionPerUnit: { co2: gasProduction(31) },
      resourceProductionPerUnit: { steel: resourceProduction(31), concrete: resourceProduction(31) },
    },
  },
  {
    id: 'asteroid_capture',
    name: 'Asteroid Capture',
    branch: 'endgame',
    kind: 'generator',
    tier: 33,
    description:
      'Whole metallic asteroids are nudged into Earth orbit and taken apart. The commodity markets have never been calmer, or the sky busier.',
    icon: '☄️',
    requires: ['moon_mining'],
    cost: [{ resource: 'energy', baseAmount: generatorBaseCost(33) }],
    costGrowth: generatorCostGrowth(33),
    maxOwned: Infinity,
    effect: {
      gasProductionPerUnit: { co2: gasProduction(32) },
      resourceProductionPerUnit: { steel: resourceProduction(33), energy: resourceProduction(31) },
    },
  },
  {
    id: 'planetary_industry',
    name: 'Planetary Industry',
    branch: 'endgame',
    kind: 'generator',
    tier: 34,
    description: 'Every remaining hectare of the planet\'s surface is now industrial infrastructure of some kind.',
    icon: '🌍',
    requires: ['asteroid_capture'],
    cost: [{ resource: 'energy', baseAmount: generatorBaseCost(34) }],
    costGrowth: generatorCostGrowth(34),
    maxOwned: Infinity,
    effect: {
      gasProductionPerUnit: {
        co2: gasProduction(34),
        ch4: gasProduction(32),
        n2o: gasProduction(32),
        fluorinated: gasProduction(29) * 0.1,
      },
      resourceProductionPerUnit: { energy: resourceProduction(33) },
    },
  },
  {
    id: 'terraforming_engines',
    name: 'Terraforming Engines',
    branch: 'endgame',
    kind: 'generator',
    tier: 35,
    description:
      'Continent-scale machines built to put the climate back where it was. They run on the same industry that broke it, and they are losing.',
    icon: '🏗️',
    requires: ['planetary_industry'],
    cost: [{ resource: 'energy', baseAmount: generatorBaseCost(35) }],
    costGrowth: generatorCostGrowth(35),
    maxOwned: Infinity,
    effect: {
      // The joke has to cost something to be a joke: these genuinely scrub
      // CO2, and genuinely emit more than they scrub, because running them
      // takes an entire planetary industry.
      gasRemovalPerUnit: { co2: gasProduction(34) * 0.6 },
      gasProductionPerUnit: { co2: gasProduction(35), ch4: gasProduction(33) },
      resourceProductionPerUnit: { energy: resourceProduction(34) },
    },
  },
  {
    id: 'dyson_swarm_prototype',
    name: 'Dyson Swarm Prototype',
    branch: 'endgame',
    kind: 'generator',
    tier: 36,
    description: 'The first panels of a solar-collecting swarm go up. Harvesting a star, warming a planet — a footnote by comparison.',
    icon: '☀️',
    requires: ['terraforming_engines'],
    cost: [{ resource: 'energy', baseAmount: generatorBaseCost(36) }],
    costGrowth: generatorCostGrowth(36),
    maxOwned: Infinity,
    effect: {
      gasProductionPerUnit: { co2: gasProduction(36) },
      resourceProductionPerUnit: { energy: resourceProduction(36) },
    },
  },
  {
    id: 'matrioshka_brain',
    name: 'Matrioshka Brain',
    branch: 'endgame',
    kind: 'generator',
    tier: 37,
    description:
      'Nested shells of computation wrapped around the sun, thinking thoughts nobody on the surface can follow. The waste heat alone is a weather system.',
    icon: '🧠',
    requires: ['dyson_swarm_prototype'],
    cost: [{ resource: 'energy', baseAmount: generatorBaseCost(37) }],
    costGrowth: generatorCostGrowth(37),
    maxOwned: Infinity,
    effect: {
      gasProductionPerUnit: { co2: gasProduction(37), fluorinated: gasProduction(32) * 0.2 },
      resourceProductionPerUnit: { energy: resourceProduction(37), research: resourceProduction(36) },
    },
  },
  {
    id: 'stellar_energy',
    name: 'Stellar Energy',
    branch: 'endgame',
    kind: 'multiplier',
    tier: 38,
    description: 'Your civilization now answers its energy needs with a literal star. Ambition has entirely outrun restraint.',
    icon: '🌟',
    requires: ['matrioshka_brain'],
    cost: [{ resource: 'energy', baseAmount: unlockCost(38) }],
    costGrowth: 1,
    maxOwned: 1,
    effect: { globalProductionMultiplier: 3 },
  },
];
