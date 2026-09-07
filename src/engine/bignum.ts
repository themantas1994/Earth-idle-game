/**
 * Arbitrary-scale numeric type for incremental-game math.
 *
 * A standard IEEE754 double overflows to Infinity around 1.8e308, which this
 * game's late-game greenhouse-gas totals are designed to exceed. Decimal
 * stores a value as `sign * mantissa * 10^exponent` where `mantissa` stays
 * normalized to [1, 10) and `exponent` is an ordinary JS number — so the
 * representable range is bounded only by how large a *power* Number can
 * hold (~1.8e308), which lets values reach roughly 10^(1.8e308). That is
 * vastly more headroom than this game will ever need.
 *
 * This is intentionally modeled after the break_infinity.js approach used
 * throughout the incremental-game genre, reimplemented locally so the
 * engine has no runtime dependency and every operation is easy to unit test.
 */

const MAX_SIGNIFICANT_DIGITS = 17;

function normalize(sign: number, mantissa: number, exponent: number): Decimal {
  if (mantissa === 0 || sign === 0) {
    return new Decimal(0, 0, 0);
  }
  if (!isFinite(mantissa) || !isFinite(exponent)) {
    return new Decimal(Math.sign(sign) || 1, Infinity, Infinity);
  }

  // Pull mantissa into [1, 10) by shifting the difference into the exponent.
  const shift = Math.floor(Math.log10(Math.abs(mantissa)));
  let m = mantissa / Math.pow(10, shift);
  let e = exponent + shift;

  // log10 rounding can occasionally push m to exactly 10 or just under 1.
  if (m >= 10) {
    m /= 10;
    e += 1;
  } else if (m < 1) {
    m *= 10;
    e -= 1;
  }

  return new Decimal(sign < 0 ? -1 : 1, m, e);
}

export class Decimal {
  readonly sign: number;
  readonly mantissa: number;
  readonly exponent: number;

  constructor(sign: number, mantissa: number, exponent: number) {
    this.sign = mantissa === 0 ? 0 : sign < 0 ? -1 : 1;
    this.mantissa = mantissa;
    this.exponent = exponent;
  }

  static readonly ZERO = new Decimal(0, 0, 0);
  static readonly ONE = new Decimal(1, 1, 0);

  static fromNumber(n: number): Decimal {
    if (n === 0 || Number.isNaN(n)) return Decimal.ZERO;
    if (!isFinite(n)) return new Decimal(Math.sign(n), Infinity, Infinity);
    return normalize(Math.sign(n), Math.abs(n), 0);
  }

  /** Parses `"1.23e45"`, `"-4.2e-3"`, or a plain decimal string. */
  static fromString(s: string): Decimal {
    const trimmed = s.trim();
    const eIdx = trimmed.toLowerCase().indexOf('e');
    if (eIdx === -1) {
      return Decimal.fromNumber(Number(trimmed));
    }
    const mantissaPart = Number(trimmed.slice(0, eIdx));
    const exponentPart = Number(trimmed.slice(eIdx + 1));
    if (mantissaPart === 0) return Decimal.ZERO;
    return normalize(Math.sign(mantissaPart), Math.abs(mantissaPart), exponentPart);
  }

  static from(value: Decimal | number | string): Decimal {
    if (value instanceof Decimal) return value;
    if (typeof value === 'number') return Decimal.fromNumber(value);
    return Decimal.fromString(value);
  }

  isZero(): boolean {
    return this.sign === 0;
  }

  isFinite(): boolean {
    return isFinite(this.exponent);
  }

  negate(): Decimal {
    return new Decimal(-this.sign, this.mantissa, this.exponent);
  }

  abs(): Decimal {
    return new Decimal(1, this.mantissa, this.exponent);
  }

  add(other: Decimal | number | string): Decimal {
    const b = Decimal.from(other);
    if (this.isZero()) return b;
    if (b.isZero()) return this;

    const [big, small] = this.exponent >= b.exponent ? [this, b] : [b, this];
    const expDiff = big.exponent - small.exponent;
    // Beyond ~17 orders of magnitude the smaller term cannot affect the
    // mantissa's double precision, so it is safely dropped.
    if (expDiff > MAX_SIGNIFICANT_DIGITS) return big;

    const bigSigned = big.sign * big.mantissa;
    const smallSigned = small.sign * small.mantissa / Math.pow(10, expDiff);
    const resultMantissa = bigSigned + smallSigned;
    if (resultMantissa === 0) return Decimal.ZERO;
    return normalize(Math.sign(resultMantissa), Math.abs(resultMantissa), big.exponent);
  }

  sub(other: Decimal | number | string): Decimal {
    return this.add(Decimal.from(other).negate());
  }

  mul(other: Decimal | number | string): Decimal {
    const b = Decimal.from(other);
    if (this.isZero() || b.isZero()) return Decimal.ZERO;
    return normalize(this.sign * b.sign, this.mantissa * b.mantissa, this.exponent + b.exponent);
  }

  div(other: Decimal | number | string): Decimal {
    const b = Decimal.from(other);
    if (b.isZero()) return new Decimal(this.sign || 1, Infinity, Infinity);
    if (this.isZero()) return Decimal.ZERO;
    return normalize(this.sign * b.sign, this.mantissa / b.mantissa, this.exponent - b.exponent);
  }

  pow(exp: number): Decimal {
    if (exp === 0) return Decimal.ONE;
    if (this.isZero()) return Decimal.ZERO;
    // log-space exponentiation: (m * 10^e)^exp = 10^(exp * (log10(m) + e))
    const logValue = exp * (Math.log10(this.mantissa) + this.exponent);
    const sign = this.sign < 0 && Math.abs(exp % 2) === 1 ? -1 : 1;
    const newExponent = Math.floor(logValue);
    const newMantissa = Math.pow(10, logValue - newExponent);
    return normalize(sign, newMantissa, newExponent);
  }

  sqrt(): Decimal {
    return this.pow(0.5);
  }

  /** log10 of the absolute value; -Infinity for zero. */
  log10(): number {
    if (this.isZero()) return -Infinity;
    return this.exponent + Math.log10(this.mantissa);
  }

  ln(): number {
    return this.log10() * Math.LN10;
  }

  cmp(other: Decimal | number | string): number {
    const b = Decimal.from(other);
    if (this.sign !== b.sign) return this.sign < b.sign ? -1 : 1;
    if (this.isZero() && b.isZero()) return 0;
    const magnitude = this.exponent === b.exponent
      ? Math.sign(this.mantissa - b.mantissa)
      : this.exponent < b.exponent ? -1 : 1;
    return this.sign < 0 ? -magnitude : magnitude;
  }

  eq(other: Decimal | number | string): boolean {
    return this.cmp(other) === 0;
  }
  lt(other: Decimal | number | string): boolean {
    return this.cmp(other) < 0;
  }
  lte(other: Decimal | number | string): boolean {
    return this.cmp(other) <= 0;
  }
  gt(other: Decimal | number | string): boolean {
    return this.cmp(other) > 0;
  }
  gte(other: Decimal | number | string): boolean {
    return this.cmp(other) >= 0;
  }

  max(other: Decimal | number | string): Decimal {
    const b = Decimal.from(other);
    return this.gte(b) ? this : b;
  }
  min(other: Decimal | number | string): Decimal {
    const b = Decimal.from(other);
    return this.lte(b) ? this : b;
  }
  clampMin(min: Decimal | number | string): Decimal {
    return this.max(min);
  }

  /** Best-effort conversion back to a JS number; may be Infinity. */
  toNumber(): number {
    if (this.isZero()) return 0;
    if (this.exponent > 308) return this.sign * Infinity;
    return this.sign * this.mantissa * Math.pow(10, this.exponent);
  }

  toExponential(digits = 2): string {
    if (this.isZero()) return (0).toFixed(digits) + 'e+0';
    const sign = this.sign < 0 ? '-' : '';
    return `${sign}${this.mantissa.toFixed(digits)}e${this.exponent >= 0 ? '+' : ''}${this.exponent}`;
  }

  /**
   * Deliberately NOT named `toJSON`: JSON.stringify auto-invokes a method
   * with that exact name *before* handing the value to a replacer, which
   * would silently unwrap this into a plain array and defeat the
   * `instanceof Decimal` tagging that save.ts's replacer depends on.
   */
  toTuple(): [number, number, number] {
    return [this.sign, this.mantissa, this.exponent];
  }

  static fromJSON(data: [number, number, number] | undefined | null): Decimal {
    if (!data) return Decimal.ZERO;
    const [sign, mantissa, exponent] = data;
    if (mantissa === 0) return Decimal.ZERO;
    return normalize(sign, Math.abs(mantissa), exponent);
  }

  toString(): string {
    return this.toExponential(6);
  }
}

export function D(value: Decimal | number | string): Decimal {
  return Decimal.from(value);
}
