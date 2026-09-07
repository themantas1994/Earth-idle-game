import { describe, it, expect } from 'vitest';
import { formatNumber, formatDuration, formatTemperature, formatPercent } from './format';

describe('formatNumber', () => {
  it('formats small numbers plainly in compact mode', () => {
    expect(formatNumber(0)).toBe('0');
    expect(formatNumber(42)).toBe('42');
    expect(formatNumber(999)).toBe('999');
  });

  it('applies suffixes in compact mode', () => {
    expect(formatNumber(1200, { mode: 'compact' })).toBe('1.2K');
    expect(formatNumber(4.82e12, { mode: 'compact' })).toBe('4.82T');
  });

  it('formats scientific notation', () => {
    expect(formatNumber(1.25e6, { mode: 'scientific', precision: 2 })).toBe('1.25e+6');
  });

  it('formats engineering notation with exponents as multiples of 3', () => {
    const result = formatNumber('4.2e7', { mode: 'engineering', precision: 2 });
    expect(result).toMatch(/e\+6$/);
  });

  it('formats full numbers with locale separators for moderate magnitudes', () => {
    expect(formatNumber(1234567, { mode: 'full' })).toBe('1,234,567');
  });
});

describe('formatDuration', () => {
  it('formats seconds under a minute', () => {
    expect(formatDuration(45)).toBe('45s');
  });
  it('formats minutes and hours', () => {
    expect(formatDuration(3600 * 2 + 60 * 5)).toBe('2h 5m');
  });
  it('formats days', () => {
    expect(formatDuration(86400 + 3600)).toBe('1d 1h');
  });
});

describe('formatTemperature', () => {
  it('always shows a sign', () => {
    expect(formatTemperature(1.42)).toBe('+1.42°C');
    expect(formatTemperature(-2)).toBe('-2.00°C');
  });
});

describe('formatPercent', () => {
  it('converts fractions to percentages', () => {
    expect(formatPercent(0.42)).toBe('42.0%');
    expect(formatPercent(1)).toBe('100.0%');
  });
});
