import { HABITABILITY } from './constants';
import { GAME_SECONDS_PER_YEAR } from './gameTime';

/**
 * Habitability is deliberately not a linear function of temperature alone.
 * It combines several independent 0..1 "goodness" factors — each mapped
 * from a different simulated planetary indicator — and multiplies them
 * together. Multiplying (rather than averaging) means any single factor
 * collapsing to zero drives habitability to zero even if others are still
 * fine, which mirrors the flavor of "one broken system can end
 * civilization" without requiring a fully coupled climate model.
 */
export interface PlanetaryIndicators {
  temperatureAnomalyC: number;
  oceanPh: number;
  seaLevelRiseMeters: number;
  agriculturalOutputFraction: number; // 0..1, already-decayed factor from temperature
  biodiversityFraction: number; // 0..1
}

function clamp01(x: number): number {
  return Math.min(1, Math.max(0, x));
}

export function temperatureFactor(tempAnomalyC: number): number {
  const t = Math.max(0, tempAnomalyC) / HABITABILITY.tempCollapseThreshold;
  return clamp01(1 - t * t);
}

export function computeOceanPh(co2ExtraPpm: number): number {
  return HABITABILITY.baselinePh - HABITABILITY.phDriftPerCo2Ppm * Math.max(0, co2ExtraPpm);
}

export function oceanAcidityFactor(ph: number): number {
  const range = HABITABILITY.baselinePh - HABITABILITY.phCollapseFloor;
  return clamp01((ph - HABITABILITY.phCollapseFloor) / range);
}

export function seaLevelFactor(seaLevelRiseMeters: number): number {
  return clamp01(1 - seaLevelRiseMeters / HABITABILITY.seaLevelCollapseMeters);
}

/** Sea level rise accumulates with sustained warming exposure over time (integral of temperature). */
/**
 * Deliberately still on the **real** clock. `seaLevelPerDegreeYear` is not a
 * physical rate: it was pre-scaled by about 1e5 precisely so a run's worth of
 * real seconds produces a meaningful rise, which is the same scaling the
 * simulated calendar now states explicitly. Running it on simulated years
 * *and* leaving the constant alone would apply that scaling twice; rescaling
 * the constant to compensate would be a change with no behavioural effect. So
 * the year here is a real one, and the only shared thing is its length.
 */
export function integrateSeaLevelRise(currentMeters: number, tempAnomalyC: number, dtSeconds: number): number {
  const dtYears = dtSeconds / GAME_SECONDS_PER_YEAR;
  const rise = HABITABILITY.seaLevelPerDegreeYear * Math.max(0, tempAnomalyC) * dtYears;
  return currentMeters + rise;
}

export function agriculturalOutputFactor(tempAnomalyC: number): number {
  const knee = HABITABILITY.agricultureKneeDegrees;
  if (tempAnomalyC <= knee) return 1 - 0.05 * (tempAnomalyC / knee);
  const excess = tempAnomalyC - knee;
  return clamp01(0.95 * Math.exp(-excess / 8));
}

export function biodiversityFactor(tempAnomalyC: number): number {
  const knee = HABITABILITY.biodiversityKneeDegrees;
  if (tempAnomalyC <= knee) return 1 - 0.03 * (tempAnomalyC / knee);
  const excess = tempAnomalyC - knee;
  return clamp01(0.97 * Math.exp(-excess / 6));
}

export interface HabitabilityResult {
  fraction: number; // 0..1
  factors: {
    temperature: number;
    oceanAcidity: number;
    seaLevel: number;
    agriculture: number;
    biodiversity: number;
  };
}

export function computeHabitability(indicators: PlanetaryIndicators): HabitabilityResult {
  const factors = {
    temperature: temperatureFactor(indicators.temperatureAnomalyC),
    oceanAcidity: oceanAcidityFactor(indicators.oceanPh),
    seaLevel: seaLevelFactor(indicators.seaLevelRiseMeters),
    agriculture: clamp01(indicators.agriculturalOutputFraction),
    biodiversity: clamp01(indicators.biodiversityFraction),
  };

  const fraction = clamp01(
    factors.temperature * factors.oceanAcidity * factors.seaLevel * factors.agriculture * factors.biodiversity,
  );

  return { fraction, factors };
}
