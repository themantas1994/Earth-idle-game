/**
 * The game's simulated calendar.
 *
 * Two clocks run side by side, and conflating them is the easiest mistake to
 * make in this engine, so they are named apart here and nowhere else:
 *
 * - **Real seconds.** What the device's clock measures, what `simulateStep`'s
 *   `dtSeconds` is denominated in, and what every production rate the UI
 *   prints as `/s` means. Ticks, offline gaps, event durations, autosave
 *   cadence and the prestige speed bonus are all real time.
 * - **Simulated seconds.** The Earth's own age. One real second of simulation
 *   advances the planet by `GAME_SECONDS_PER_REAL_SECOND` simulated seconds —
 *   one simulated day — so a simulated year passes every
 *   `REAL_SECONDS_PER_GAME_YEAR` (365.25) real seconds, a little over six
 *   minutes of play.
 *
 * ## Where that ratio comes from
 *
 * It is not new. The engine has always had an implied mapping, written into
 * the one rate constant quoted per *year*: sea level rises
 * `HABITABILITY.seaLevelPerDegreeYear = 120` metres per degree per year,
 * against a real-world figure of roughly 0.001 (0.1 m per degree-century).
 * That is a factor of about 1e5 — the engine was already treating one real
 * second as worth on the order of one simulated day. This file states the
 * mapping instead of leaving it baked into a scaled constant, and rounds it to
 * the obvious figure: **one real second is one simulated day.**
 */

/**
 * Seconds in one year, Julian (365.25 days). The only place this arithmetic is
 * written down; everything that needs a year reads it from here.
 */
export const GAME_SECONDS_PER_YEAR = 365.25 * 24 * 3600;

/**
 * Simulated seconds that pass per real second of simulation: one simulated
 * day. The single knob that sets how fast the Earth ages — and therefore how
 * hard the atmosphere's half-lives bite — without touching any half-life
 * value.
 */
export const GAME_SECONDS_PER_REAL_SECOND = 24 * 3600;

/** Real seconds in one simulated year: 365.25, a little over six minutes. */
export const REAL_SECONDS_PER_GAME_YEAR = GAME_SECONDS_PER_YEAR / GAME_SECONDS_PER_REAL_SECOND;

/** Simulated seconds advanced by `realSeconds` of simulation. Never negative. */
export function gameSecondsFor(realSeconds: number): number {
  return Math.max(0, realSeconds) * GAME_SECONDS_PER_REAL_SECOND;
}

/** Simulated years contained in `gameSeconds` of simulated time. */
export function gameSecondsToYears(gameSeconds: number): number {
  return gameSeconds / GAME_SECONDS_PER_YEAR;
}

/** Simulated seconds in `years` of simulated time. */
export function gameYearsToSeconds(years: number): number {
  return years * GAME_SECONDS_PER_YEAR;
}
