import { Decimal, D } from './bignum';
import { GAME_SECONDS_PER_YEAR } from './gameTime';

export type NumberFormatMode = 'compact' | 'scientific' | 'engineering' | 'full';

// Short-scale suffixes, standard incremental-game convention beyond "T".
const COMPACT_SUFFIXES = [
  '', 'K', 'M', 'B', 'T', 'Qa', 'Qi', 'Sx', 'Sp', 'Oc', 'No',
  'Dc', 'UDc', 'DDc', 'TDc', 'QaDc', 'QiDc', 'SxDc', 'SpDc', 'OcDc', 'NoDc',
  'Vg',
];

/** Offsetting the fallback by a full alphabet is what skips the one-letter run. */
const SINGLE_LETTER_COUNT = 26;

function letterSuffixForTier(tier: number): string {
  // Beyond the named list (tier 21, i.e. 1e63) fall back to a stable
  // alphabetic scheme so formatting never runs out.
  //
  // The fallback starts at two letters ("AA") rather than one, which is what
  // keeps it unambiguous: a single-letter fallback would have printed 1e69 as
  // "1B" and 1e96 as "1K", colliding with billions and thousands — both of
  // which a run passes through on the way there. Every named suffix is either a
  // single letter or mixed case, so no all-caps pair can collide with one.
  if (tier < COMPACT_SUFFIXES.length) return COMPACT_SUFFIXES[tier];
  let n = tier - COMPACT_SUFFIXES.length + SINGLE_LETTER_COUNT;
  let s = '';
  do {
    s = String.fromCharCode(65 + (n % 26)) + s;
    n = Math.floor(n / 26) - 1;
  } while (n >= 0);
  return s;
}

function formatCompact(value: Decimal, precision: number): string {
  if (value.isZero()) return '0';
  const sign = value.sign < 0 ? '-' : '';
  const abs = value.abs();
  if (abs.lt(1000)) {
    return sign + trimTrailing(abs.toNumber().toFixed(abs.lt(10) ? precision : Math.max(0, precision - 1)));
  }
  const tier = Math.floor(abs.exponent / 3);
  const scaled = abs.div(D(10).pow(tier * 3));
  const suffix = letterSuffixForTier(tier);
  return `${sign}${trimTrailing(scaled.toNumber().toFixed(precision))}${suffix}`;
}

function trimTrailing(s: string): string {
  return s.includes('.') ? s.replace(/0+$/, '').replace(/\.$/, '') : s;
}

function formatScientific(value: Decimal, precision: number): string {
  if (value.isZero()) return (0).toFixed(precision);
  return value.toExponential(precision);
}

/** Engineering notation: exponent is always a multiple of 3 (matches SI prefixes). */
function formatEngineering(value: Decimal, precision: number): string {
  if (value.isZero()) return (0).toFixed(precision) + 'e+0';
  const sign = value.sign < 0 ? '-' : '';
  const abs = value.abs();
  const tier = Math.floor(abs.exponent / 3);
  const scaled = abs.div(D(10).pow(tier * 3));
  return `${sign}${scaled.toNumber().toFixed(precision)}e${tier >= 0 ? '+' : ''}${tier * 3}`;
}

function formatFull(value: Decimal): string {
  if (value.isZero()) return '0';
  if (!value.isFinite()) return value.sign < 0 ? '-Infinity' : 'Infinity';
  if (value.exponent > 100) {
    // Full digit expansion beyond this is unreadable; fall back to scientific.
    return formatScientific(value, 4);
  }
  const n = value.toNumber();
  return n.toLocaleString('en-US', { maximumFractionDigits: 2 });
}

export interface FormatOptions {
  mode?: NumberFormatMode;
  precision?: number;
}

/** Formats a Decimal for display according to the player's chosen notation mode. */
export function formatNumber(value: Decimal | number | string, options: FormatOptions = {}): string {
  const d = D(value);
  const precision = options.precision ?? 2;
  switch (options.mode ?? 'compact') {
    case 'scientific':
      return formatScientific(d, precision);
    case 'engineering':
      return formatEngineering(d, precision);
    case 'full':
      return formatFull(d);
    case 'compact':
    default:
      return formatCompact(d, precision);
  }
}

/** Formats a rate value with a trailing "/s" unit suffix, e.g. "4.82M/s". */
export function formatRate(value: Decimal | number | string, unit: string, options: FormatOptions = {}): string {
  return `${formatNumber(value, options)} ${unit}`;
}

/** Formats seconds as a compact "1h 42m 03s" duration string. */
export function formatDuration(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const days = Math.floor(s / 86400);
  const hours = Math.floor((s % 86400) / 3600);
  const minutes = Math.floor((s % 3600) / 60);
  const seconds = s % 60;
  const parts: string[] = [];
  if (days > 0) parts.push(`${days}d`);
  if (days > 0 || hours > 0) parts.push(`${hours}h`);
  if (days === 0 && (hours > 0 || minutes > 0)) parts.push(`${minutes}m`);
  if (days === 0 && hours === 0) parts.push(`${seconds}s`);
  return parts.join(' ') || '0s';
}

/**
 * Formats a simulated age as `"12y 4m 12d"`.
 *
 * This is the Earth's own age on the simulated calendar, not how long the
 * player has been playing — see `gameTime.ts` for the two clocks. Months are
 * 1/12 of a Julian year (30.4375 days) rather than calendar months: a planet
 * has an age, not a calendar. Components above the largest non-zero one are
 * dropped, so a brand-new Earth reads `"0d"` rather than `"0y 0m 0d"`, and
 * past ten thousand years only the years are shown.
 */
export function formatGameAge(gameAgeSeconds: number, mode: NumberFormatMode = 'compact'): string {
  if (Number.isNaN(gameAgeSeconds)) return '0d';
  if (!Number.isFinite(gameAgeSeconds)) return '∞';

  // Decomposed from seconds rather than from whole days: a Julian year is
  // 365.25 days, so flooring to days first loses the quarter and leaves an
  // Earth that has run for exactly one year reading "11m 30d".
  const secondsPerMonth = GAME_SECONDS_PER_YEAR / 12;
  const total = Math.max(0, gameAgeSeconds);

  const years = Math.floor(total / GAME_SECONDS_PER_YEAR);
  const afterYears = total - years * GAME_SECONDS_PER_YEAR;
  const months = Math.floor(afterYears / secondsPerMonth);
  const days = Math.floor((afterYears - months * secondsPerMonth) / 86400);

  if (years >= 10_000) return `${formatNumber(years, { mode })}y`;

  const parts: string[] = [];
  if (years > 0) parts.push(`${years}y`);
  if (years > 0 || months > 0) parts.push(`${months}m`);
  parts.push(`${days}d`);
  return parts.join(' ');
}

export function formatTemperature(celsius: number, precision = 2): string {
  const sign = celsius >= 0 ? '+' : '';
  return `${sign}${celsius.toFixed(precision)}°C`;
}

export function formatPercent(fraction: number, precision = 1): string {
  return `${(fraction * 100).toFixed(precision)}%`;
}
