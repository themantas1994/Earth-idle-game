import { describe, it, expect } from 'vitest';
import { Decimal, D } from './bignum';

describe('Decimal', () => {
  it('constructs from plain numbers', () => {
    expect(D(1234).toNumber()).toBeCloseTo(1234);
    expect(D(0).isZero()).toBe(true);
    expect(D(-42).toNumber()).toBeCloseTo(-42);
  });

  it('parses scientific-notation strings', () => {
    const d = D('1.25e6');
    expect(d.toNumber()).toBeCloseTo(1.25e6);
    expect(D('-4.2e-3').toNumber()).toBeCloseTo(-4.2e-3);
  });

  it('adds and subtracts correctly', () => {
    expect(D(5).add(3).toNumber()).toBeCloseTo(8);
    expect(D(5).sub(3).toNumber()).toBeCloseTo(2);
    expect(D(3).sub(5).toNumber()).toBeCloseTo(-2);
    expect(D(0).add(0).isZero()).toBe(true);
  });

  it('drops insignificant terms in addition without precision blowup', () => {
    const huge = D('1e50');
    const tiny = D('1e10');
    expect(huge.add(tiny).eq(huge)).toBe(true);
  });

  it('multiplies and divides correctly', () => {
    expect(D(6).mul(7).toNumber()).toBeCloseTo(42);
    expect(D(100).div(4).toNumber()).toBeCloseTo(25);
    expect(D(5).mul(0).isZero()).toBe(true);
  });

  it('supports exponentiation far beyond double range', () => {
    const big = D(10).pow(400);
    expect(big.exponent).toBeCloseTo(400, 0);
    expect(big.isFinite()).toBe(true);
    // A plain double would overflow to Infinity here.
    expect(Math.pow(10, 400)).toBe(Infinity);
  });

  it('computes sqrt via pow(0.5)', () => {
    expect(D(16).sqrt().toNumber()).toBeCloseTo(4);
  });

  it('compares magnitudes correctly, including sign and huge exponents', () => {
    expect(D(5).cmp(3)).toBeGreaterThan(0);
    expect(D(3).cmp(5)).toBeLessThan(0);
    expect(D(5).cmp(5)).toBe(0);
    expect(D(-5).cmp(3)).toBeLessThan(0);
    expect(D('1e300').gt(D('1e299'))).toBe(true);
    expect(D('1e300').gt(D('9.9e300'))).toBe(false);
  });

  it('round-trips through JSON', () => {
    const original = D('4.821e97');
    const restored = Decimal.fromJSON(original.toTuple());
    expect(restored.eq(original)).toBe(true);
  });

  it('formats exponential notation with a stable sign', () => {
    expect(D('1.2e6').toExponential(2)).toBe('1.20e+6');
    expect(D('-3e-4').toExponential(1)).toBe('-3.0e-4');
  });

  it('clamps to a minimum value', () => {
    expect(D(-5).clampMin(0).isZero()).toBe(true);
    expect(D(10).clampMin(0).toNumber()).toBeCloseTo(10);
  });
});
