import { Decimal, D } from './bignum';

export type NumberFormatMode = 'compact' | 'scientific' | 'engineering' | 'full';

// Short-scale suffixes, standard incremental-game convention beyond "T".
const COMPACT_SUFFIXES = [
  '', 'K', 'M', 'B', 'T', 'Qa', 'Qi', 'Sx', 'Sp', 'Oc', 'No',
  'Dc', 'UDc', 'DDc', 'TDc', 'QaDc', 'QiDc', 'SxDc', 'SpDc', 'OcDc', 'NoDc',
  'Vg',
];

function letterSuffixForTier(tier: number): string {
  // Beyond the named list (tier ~21, i.e. 1e66), fall back to a stable
  // alphabetic scheme (AA, AB, ... ) so formatting never runs out.
  if (tier < COMPACT_SUFFIXES.length) return COMPACT_SUFFIXES[tier];
  let n = tier - COMPACT_SUFFIXES.length;
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

export function formatTemperature(celsius: number, precision = 2): string {
  const sign = celsius >= 0 ? '+' : '';
  return `${sign}${celsius.toFixed(precision)}°C`;
}

export function formatPercent(fraction: number, precision = 1): string {
  return `${(fraction * 100).toFixed(precision)}%`;
}
