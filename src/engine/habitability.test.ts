import { describe, it, expect } from 'vitest';
import {
  temperatureFactor,
  computeOceanPh,
  oceanAcidityFactor,
  seaLevelFactor,
  integrateSeaLevelRise,
  agriculturalOutputFactor,
  biodiversityFactor,
  computeHabitability,
} from './habitability';

describe('temperatureFactor', () => {
  it('is 1 at zero anomaly and 0 at the collapse threshold', () => {
    expect(temperatureFactor(0)).toBeCloseTo(1);
    expect(temperatureFactor(55)).toBeCloseTo(0, 5);
  });
  it('never goes negative past the threshold', () => {
    expect(temperatureFactor(1000)).toBe(0);
  });
});

describe('ocean acidity', () => {
  it('ph decreases as CO2 rises', () => {
    const ph0 = computeOceanPh(0);
    const ph1000 = computeOceanPh(1000);
    expect(ph1000).toBeLessThan(ph0);
  });
  it('acidity factor is 1 at baseline ph and 0 at the collapse floor', () => {
    expect(oceanAcidityFactor(8.1)).toBeCloseTo(1);
    expect(oceanAcidityFactor(6.0)).toBeCloseTo(0);
  });
});

describe('seaLevelFactor', () => {
  it('decreases linearly to zero at the collapse threshold', () => {
    expect(seaLevelFactor(0)).toBeCloseTo(1);
    expect(seaLevelFactor(220)).toBeCloseTo(0);
  });
});

describe('integrateSeaLevelRise', () => {
  it('accumulates over time proportional to temperature', () => {
    const oneYear = 365.25 * 24 * 3600;
    const risen = integrateSeaLevelRise(0, 10, oneYear);
    expect(risen).toBeGreaterThan(0);
    const risenMore = integrateSeaLevelRise(0, 20, oneYear);
    expect(risenMore).toBeGreaterThan(risen);
  });
  it('does not rise with zero temperature anomaly', () => {
    expect(integrateSeaLevelRise(5, 0, 1e9)).toBeCloseTo(5);
  });
});

describe('agriculturalOutputFactor / biodiversityFactor', () => {
  it('stay near 1 at low temperatures and decay at high temperatures', () => {
    expect(agriculturalOutputFactor(0)).toBeGreaterThan(0.9);
    expect(agriculturalOutputFactor(30)).toBeLessThan(0.1);
    expect(biodiversityFactor(0)).toBeGreaterThan(0.9);
    expect(biodiversityFactor(30)).toBeLessThan(0.1);
  });
});

describe('computeHabitability', () => {
  it('is near 1 for a pristine planet', () => {
    const result = computeHabitability({
      temperatureAnomalyC: 0,
      oceanPh: 8.1,
      seaLevelRiseMeters: 0,
      agriculturalOutputFraction: 1,
      biodiversityFraction: 1,
    });
    expect(result.fraction).toBeGreaterThan(0.9);
  });

  it('collapses to zero when any single factor collapses', () => {
    const result = computeHabitability({
      temperatureAnomalyC: 55, // temperature factor hits 0
      oceanPh: 8.1,
      seaLevelRiseMeters: 0,
      agriculturalOutputFraction: 1,
      biodiversityFraction: 1,
    });
    expect(result.fraction).toBeCloseTo(0, 5);
  });

  it('degrades smoothly as temperature rises', () => {
    const mild = computeHabitability({
      temperatureAnomalyC: 5,
      oceanPh: 8.0,
      seaLevelRiseMeters: 5,
      agriculturalOutputFraction: 0.9,
      biodiversityFraction: 0.9,
    });
    const severe = computeHabitability({
      temperatureAnomalyC: 25,
      oceanPh: 7.2,
      seaLevelRiseMeters: 80,
      agriculturalOutputFraction: 0.3,
      biodiversityFraction: 0.2,
    });
    expect(mild.fraction).toBeGreaterThan(severe.fraction);
  });
});
