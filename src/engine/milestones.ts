import { GameState } from './gameState';
import { gasDisplayConcentration } from './climate';

/**
 * Milestone news events.
 *
 * Where `events.ts` holds *random* events (short-lived multiplier swings
 * rolled by chance), this file holds *deterministic* ones: headlines that
 * fire the first time a run crosses a specific threshold. They carry no
 * mechanical effect at all — their whole job is to narrate what the player's
 * civilization is doing to the world, so a week-long run reads as a story of
 * consequences rather than a rising number.
 *
 * The set deliberately spans the entire run. Early headlines are triggered by
 * *technology*, because a handful of campfires genuinely cannot move a
 * planet's atmosphere and pretending otherwise would be a lie the rest of the
 * simulation doesn't tell; the consequences reported there are local and
 * regional. Climate headlines take over in the back half, once emissions are
 * large enough to show up in the global numbers, and they escalate from
 * treaty thresholds to obituaries.
 *
 * Each milestone fires at most once per run (tracked in
 * `state.milestonesTriggered`, which resets with every new Earth).
 */
export type MilestoneCategory = 'industry' | 'climate' | 'ecology' | 'society' | 'apocalypse';

export interface MilestoneDefinition {
  id: string;
  /** Newspaper-style headline shown in the feed and the toast. */
  headline: string;
  /** One or two sentences of wire copy underneath. */
  body: string;
  /** Dateline shown above the headline, e.g. "REUTERS · MANAUS". */
  source: string;
  icon: string;
  category: MilestoneCategory;
  /** Fires the first tick this returns true. */
  condition: (state: GameState) => boolean;
}

const co2Ppm = (s: GameState) => gasDisplayConcentration('co2', s.atmosphere);
const owns = (s: GameState, id: string) => (s.techOwned[id] ?? 0) > 0;

/** Ordered by roughly when they fire, which is also the order the feed seeds them in. */
export const MILESTONES: MilestoneDefinition[] = [
  // --- Act one: a civilization gets going. Local consequences, local press. ---
  {
    id: 'first_smoke',
    headline: 'Smoke on the horizon',
    body: 'A column of smoke rises from the valley for the first time by somebody\'s deliberate choice. Nobody is around to write it down, but the atmosphere is keeping score.',
    source: 'PREHISTORY · THE VALLEY',
    icon: '🔥',
    category: 'industry',
    condition: (s) => owns(s, 'controlled_fire'),
  },
  {
    id: 'the_first_field',
    headline: 'The first field is cleared',
    body: 'A stretch of forest is burned back and planted. It feeds more people than the forest did — and it is the first time anyone has decided what a landscape is for.',
    source: 'PREHISTORY · THE FLOODPLAIN',
    icon: '🌾',
    category: 'industry',
    condition: (s) => owns(s, 'farming'),
  },
  {
    id: 'industrial_era',
    headline: 'Steam and iron remake the world',
    body: 'Mills that never sleep, cities black with soot, and coal hauled out of the ground faster than anyone can count it. Progress, they are calling it.',
    source: 'THE TIMES · MANCHESTER',
    icon: '🏭',
    category: 'industry',
    condition: (s) => owns(s, 'steam_engine'),
  },
  {
    id: 'coal_smog',
    headline: 'Coal smog kills thousands in a week',
    body: 'A temperature inversion has trapped a city\'s chimney smoke at street level. Physicians report the death toll is highest among the men who dig the coal.',
    source: 'THE ILLUSTRATED NEWS · LONDON',
    icon: '🌫️',
    category: 'society',
    condition: (s) => owns(s, 'coal_mining'),
  },
  {
    id: 'river_ran_black',
    headline: 'River declared "biologically dead" below the mills',
    body: 'Steelworks upstream have discharged for a decade into water that fishermen worked for a thousand years. An inquiry has been promised, then postponed.',
    source: 'THE CHRONICLE · THE RIVER',
    icon: '🏭',
    category: 'ecology',
    condition: (s) => owns(s, 'steel_production'),
  },
  {
    id: 'assembly_line',
    headline: 'Factories switch to continuous production',
    body: 'The line no longer stops at night. Output has doubled twice in a year, and the coal to run it is being burned as fast as it can be shovelled.',
    source: 'BUSINESS WIRE · DETROIT',
    icon: '⚙️',
    category: 'industry',
    condition: (s) => owns(s, 'factories'),
  },
  {
    id: 'grid_switched_on',
    headline: 'City lights up for the first time',
    body: 'A coal-fired station on the edge of town has electrified a whole district. Crowds gathered to watch. Nobody looked at the chimney.',
    source: 'EVENING STANDARD · THE CITY',
    icon: '💡',
    category: 'industry',
    condition: (s) => owns(s, 'coal_power_plant'),
  },
  {
    id: 'oil_age',
    headline: 'Oil strike opens a new age of fuel',
    body: 'Crude is coming out of the ground faster than it can be barrelled. Analysts predict the supply is, for practical purposes, infinite.',
    source: 'WALL STREET JOURNAL · TEXAS',
    icon: '🛢️',
    category: 'industry',
    condition: (s) => owns(s, 'oil_drilling'),
  },
  {
    id: 'the_automobile',
    headline: 'A car in every driveway',
    body: 'Private motoring has gone from a curiosity to an expectation in a generation. Cities are being rebuilt around it, and the exhaust is being described as the smell of prosperity.',
    source: 'LIFE · LOS ANGELES',
    icon: '🚗',
    category: 'society',
    condition: (s) => owns(s, 'automobile'),
  },
  {
    id: 'ozone_hole',
    headline: 'A hole is found in the ozone layer',
    body: 'Refrigerant gases invented to be harmless turn out to destroy stratospheric ozone on contact. Industry has called the finding "premature".',
    source: 'NEW SCIENTIST · HALLEY BAY',
    icon: '🧴',
    category: 'ecology',
    condition: (s) => owns(s, 'cfcs'),
  },
  {
    id: 'megacity',
    headline: 'World\'s first 30-million-person city',
    body: 'Concrete, steel and air conditioning have made a metropolis possible where the climate never was. Both the concrete and the cooling are enormously carbon-intensive; neither is mentioned at the ribbon-cutting.',
    source: 'AP · THE DELTA',
    icon: '🏙️',
    category: 'society',
    condition: (s) => owns(s, 'megacities'),
  },
  {
    id: 'fracking_boom',
    headline: 'Fracking unlocks "a century of cheap gas"',
    body: 'Shale wells are producing at rates nobody forecast. Residents downwind report they can set their tap water alight; the industry disputes the connection.',
    source: 'BLOOMBERG · NORTH DAKOTA',
    icon: '⛽',
    category: 'industry',
    condition: (s) => owns(s, 'fracking'),
  },
  {
    id: 'factory_farms',
    headline: 'Livestock population passes human population',
    body: 'Industrial husbandry now runs more cattle than there are people to eat them. Methane from their digestion has become a measurable share of global emissions.',
    source: 'FAO · GLOBAL',
    icon: '🐄',
    category: 'ecology',
    condition: (s) => owns(s, 'factory_farming'),
  },
  {
    id: 'container_trade',
    headline: 'Global shipping now moves more than nations produce',
    body: 'Standardised steel boxes have made distance almost free. Freighters burn the heaviest, dirtiest fraction of the barrel to do it.',
    source: 'FINANCIAL TIMES · ROTTERDAM',
    icon: '🚢',
    category: 'industry',
    condition: (s) => owns(s, 'container_ships'),
  },
  {
    id: 'the_internet',
    headline: 'The world connects itself',
    body: 'Everyone alive can now be told anything, instantly. Among the first things widely circulated is the evidence for what the burning is doing.',
    source: 'WIRED · GLOBAL',
    icon: '🌐',
    category: 'society',
    condition: (s) => owns(s, 'internet'),
  },
  {
    id: 'data_centers',
    headline: 'Data centres overtake aviation for electricity use',
    body: 'The buildings that hold the world\'s information draw more power than the world\'s aeroplanes. Almost all of it is generated by burning something.',
    source: 'IEA · PARIS',
    icon: '🖥️',
    category: 'industry',
    condition: (s) => owns(s, 'data_centers'),
  },
  {
    id: 'jet_age',
    headline: 'Mass air travel becomes routine',
    body: 'Millions are now airborne at any moment, each of them emitting at an altitude where it does the most harm.',
    source: 'REUTERS · HEATHROW',
    icon: '✈️',
    category: 'society',
    condition: (s) => owns(s, 'commercial_aviation'),
  },

  // --- Act two: the planet starts answering back. ---
  {
    id: 'co2_300',
    headline: 'CO₂ higher than at any point in 800,000 years',
    body: 'Ice cores hold a continuous record of the atmosphere going back eight hundred millennia. This week\'s reading is off the top of it.',
    source: 'AP · MAUNA LOA OBSERVATORY',
    icon: '📈',
    category: 'climate',
    condition: (s) => co2Ppm(s) >= 300,
  },
  {
    id: 'co2_350',
    headline: 'CO₂ passes 350 ppm — "the safe ceiling"',
    body: 'Climatologists have long called 350 parts per million the upper limit for a stable climate. The needle went through it this morning and kept going.',
    source: 'AP · MAUNA LOA OBSERVATORY',
    icon: '🌡️',
    category: 'climate',
    condition: (s) => co2Ppm(s) >= 350,
  },
  {
    id: 'paris_1_5',
    headline: 'World blows past the 1.5 °C limit',
    body: 'The threshold at the heart of every climate treaty ever signed has been crossed. Delegates are expected to negotiate a new, higher threshold.',
    source: 'REUTERS · PARIS',
    icon: '🌍',
    category: 'climate',
    condition: (s) => s.temperatureAnomalyC >= 1.5,
  },
  {
    id: 'co2_400',
    headline: 'Carbon dioxide breaks 400 ppm',
    body: 'The last time the atmosphere held this much CO₂, there were forests near the South Pole. Officials have described the reading as "a milestone" and declined to say of what.',
    source: 'BBC · GENEVA',
    icon: '📊',
    category: 'climate',
    condition: (s) => co2Ppm(s) >= 400,
  },
  {
    id: 'amazon_burning',
    headline: 'The Amazon is on fire',
    body: 'Record heat and record drought have turned the world\'s largest rainforest into kindling. Smoke plumes are visible from orbit; the forest that spent ten thousand years absorbing carbon is now releasing it.',
    source: 'REUTERS · MANAUS, BRAZIL',
    icon: '🌳',
    category: 'ecology',
    condition: (s) => s.temperatureAnomalyC >= 2,
  },
  {
    id: 'reef_bleached',
    headline: 'Great Barrier Reef declared functionally dead',
    body: 'Marine biologists confirm that ocean heat and acidification have bleached the last resilient reef sections. "We are no longer studying an ecosystem," one researcher said. "We are studying a ruin."',
    source: 'ABC · QUEENSLAND, AUSTRALIA',
    icon: '🪸',
    category: 'ecology',
    condition: (s) => s.oceanPh <= 8.05,
  },
  {
    id: 'arctic_ice_free',
    headline: 'Arctic recorded ice-free in summer',
    body: 'For the first time in human history, satellites found open water at the North Pole in September. Shipping companies have called the news "an opportunity".',
    source: 'NSIDC · BOULDER, COLORADO',
    icon: '🧊',
    category: 'climate',
    condition: (s) => s.temperatureAnomalyC >= 3,
  },
  {
    id: 'crop_failure',
    headline: 'Simultaneous harvest failure across three continents',
    body: 'Heat and drought have hit the world\'s breadbaskets in the same season. Grain prices have tripled and export bans are spreading faster than aid.',
    source: 'FAO · ROME',
    icon: '🌾',
    category: 'society',
    condition: (s) => s.habitability.factors.agriculture <= 0.75,
  },
  {
    id: 'wet_bulb',
    headline: 'Deadly wet-bulb conditions strike South Asia',
    body: 'Humid heat has passed the limit at which a human body can cool itself. Authorities have advised 400 million people to remain indoors, where there is no power for cooling.',
    source: 'AFP · KARACHI',
    icon: '🥵',
    category: 'society',
    condition: (s) => s.temperatureAnomalyC >= 4,
  },
  {
    id: 'sea_level_cities',
    headline: 'Miami, Jakarta and Alexandria begin permanent evacuation',
    body: 'Sea walls have been overtopped for the third year running. Three coastal megacities are being abandoned rather than defended — the first of many, planners concede.',
    source: 'GUARDIAN · JAKARTA',
    icon: '🌊',
    category: 'society',
    condition: (s) => s.seaLevelRiseMeters >= 1,
  },
  {
    id: 'permafrost_thaw',
    headline: 'Siberian permafrost is venting methane',
    body: 'Ground frozen for forty thousand years is collapsing into craters and releasing the carbon inside it. The thaw now adds more greenhouse gas each year than it can be stopped from adding.',
    source: 'TASS · YAKUTSK, SIBERIA',
    icon: '💨',
    category: 'climate',
    condition: (s) => s.temperatureAnomalyC >= 5,
  },
  {
    id: 'insect_collapse',
    headline: 'Global insect populations collapse',
    body: 'Pollinator numbers have fallen below the level agriculture depends on. Orchards in four countries are being pollinated by hand.',
    source: 'NATURE · CAMBRIDGE',
    icon: '🐝',
    category: 'ecology',
    condition: (s) => s.habitability.factors.biodiversity <= 0.6,
  },
  {
    id: 'amazon_savanna',
    headline: 'Amazon rainforest passes point of no return',
    body: 'Ecologists confirm the dieback is self-sustaining: what is left of the forest is now drying itself out. The basin is becoming savanna, and every dying tree adds to the problem.',
    source: 'REUTERS · BRASÍLIA',
    icon: '🪵',
    category: 'ecology',
    condition: (s) => s.temperatureAnomalyC >= 6,
  },
  {
    id: 'co2_500',
    headline: 'CO₂ passes 500 ppm; monitoring stations begin closing',
    body: 'Two long-running observatories have gone offline this month, one to fire and one to funding. The remaining stations report the curve is steepening.',
    source: 'WMO · GENEVA',
    icon: '📉',
    category: 'climate',
    condition: (s) => co2Ppm(s) >= 500,
  },
  {
    id: 'gulf_stream',
    headline: 'Atlantic overturning circulation has stalled',
    body: 'The current that keeps northern Europe temperate has slowed to a stop. Meteorologists warn of winters no living person has experienced, in a world that is otherwise cooking.',
    source: 'DER SPIEGEL · HAMBURG',
    icon: '🌀',
    category: 'climate',
    condition: (s) => s.temperatureAnomalyC >= 8,
  },
  {
    id: 'climate_refugees',
    headline: 'One billion people are now displaced',
    body: 'The UN refugee agency has stopped publishing a running total, saying the figure "changes faster than it can be verified".',
    source: 'UNHCR · GENEVA',
    icon: '🚶',
    category: 'society',
    condition: (s) => s.habitability.fraction <= 0.4,
  },
  {
    id: 'co2_1000',
    headline: 'CO₂ passes 1,000 ppm — beyond every published projection',
    body: 'The concentration has left the range any climate model was built to handle. Researchers are extrapolating, with the caveat that extrapolation is all anyone has left.',
    source: 'WMO · GENEVA',
    icon: '🔥',
    category: 'climate',
    condition: (s) => co2Ppm(s) >= 1000,
  },
  {
    id: 'ocean_dead_zones',
    headline: 'Oceans declared largely lifeless',
    body: 'Acidification has dissolved the base of the marine food web. Fisheries worldwide have closed; the sea, for the first time, is quiet.',
    source: 'NOAA · SILVER SPRING',
    icon: '🐟',
    category: 'ecology',
    condition: (s) => s.oceanPh <= 7.8,
  },
  {
    id: 'coastlines_gone',
    headline: 'Five metres of sea level rise; coastlines redrawn',
    body: 'Cartographers have begun issuing world maps annually rather than by edition. Most of the world\'s ports, and the industry that fed them, are underwater.',
    source: 'REUTERS · THE COAST',
    icon: '🗺️',
    category: 'society',
    condition: (s) => s.seaLevelRiseMeters >= 5,
  },
  {
    id: 'grid_failure',
    headline: 'Heat forces cascading failure of the global grid',
    body: 'Transmission lines sag, transformers fail, and the cooling systems fail with them. Industry continues regardless, on generators.',
    source: 'BLOOMBERG · SINGAPORE',
    icon: '⚡',
    category: 'society',
    condition: (s) => s.temperatureAnomalyC >= 15,
  },
  {
    id: 'last_broadcast',
    headline: 'Final scheduled news broadcast airs',
    body: 'With habitability in single digits, the last continuously operating newsroom has gone dark. Its closing line: "Whatever it was you were doing, it worked."',
    source: 'WORLD SERVICE · LOCATION WITHHELD',
    icon: '📻',
    category: 'apocalypse',
    condition: (s) => s.habitability.fraction <= 0.08,
  },
  {
    id: 'venus_watch',
    headline: 'Astronomers begin comparing Earth to Venus',
    body: 'At current forcing, the paper notes, the distinction is "increasingly a matter of degree". Peer review has been waived for lack of reviewers.',
    source: 'ARXIV · PREPRINT',
    icon: '🪐',
    category: 'apocalypse',
    condition: (s) => s.temperatureAnomalyC >= 25,
  },
];

export const MILESTONE_BY_ID: Record<string, MilestoneDefinition> = Object.fromEntries(
  MILESTONES.map((m) => [m.id, m]),
);

/**
 * Returns every milestone whose condition is met and which has not already
 * fired this run, in definition order. Pure — the caller decides what to do
 * with them (append to the feed, raise a toast).
 */
export function checkMilestones(state: GameState): MilestoneDefinition[] {
  return MILESTONES.filter((m) => !state.milestonesTriggered[m.id] && m.condition(state));
}
