package com.earthgame.idle.domain.climate

import com.earthgame.idle.domain.engine.ENVIRONMENT
import com.earthgame.idle.domain.model.GasId
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Humidity and wind, derived from the climate the simulation already runs.
 *
 * Neither is a new simulation. Both are pure functions of state that already
 * exists, computed on demand and never stored, so there is exactly one source
 * of truth for the planet's weather and it is the climate model.
 *
 * - **Humidity** is read off `GasId.H2O`, the water-vapour feedback term the
 *   climate model has always tracked (see `computeWaterVaporFeedbackConcentration`).
 *   No humidity state was invented for this: the number the globe draws and
 *   the number storms form out of is the simulated water vapour, mapped onto a
 *   0..1 scale a player can read.
 * - **Wind** has no counterpart in the climate model, so it is defined here as
 *   a deterministic three-cell zonal pattern — tropical easterlies, mid-latitude
 *   westerlies, weakening toward the poles — whose overall strength tracks
 *   warming and radiative forcing. It is a **gameplay wind field**, not a
 *   meteorological one: there is no pressure field, no Coriolis term and no
 *   advection anywhere in it. It exists so the wind overlay has something
 *   honest to draw and so storms drift somewhere sensible.
 *
 * See `docs/wiki/Environmental-Visualization.md`.
 */

private fun clamp01(x: Double): Double = min(1.0, max(0.0, x))

/**
 * Relative humidity as a 0..1 fraction, from the water vapour above baseline.
 *
 * Saturating rather than linear: the first thousand ppm of water vapour moves
 * the number a lot and the ten-thousandth barely at all, so a moderately warm
 * planet already reads as humid and an apocalyptic one does not run off the
 * end of the scale.
 */
fun relativeHumidity(waterVaporPpmAboveBaseline: Double): Double {
    val vapor = max(0.0, waterVaporPpmAboveBaseline)
    val saturation = vapor / (vapor + ENVIRONMENT.HUMIDITY_HALF_SATURATION_PPM)
    return clamp01(ENVIRONMENT.BASE_HUMIDITY + ENVIRONMENT.HUMIDITY_RANGE * saturation)
}

/** Relative humidity for an atmosphere, straight from its simulated water vapour. */
fun relativeHumidity(atmosphere: AtmosphereState): Double =
    relativeHumidity(atmosphere[GasId.H2O].toDouble())

/**
 * Overall wind strength, 0..1.
 *
 * Rises with both warming and radiative forcing — a hotter, more strongly
 * forced atmosphere moves more energy around — and is capped so the overlay
 * stays readable at the temperatures the endgame reaches.
 */
fun windStrength(temperatureAnomalyC: Double, totalForcingWm2: Double): Double {
    val thermal = ENVIRONMENT.WIND_PER_DEGREE * max(0.0, temperatureAnomalyC)
    val forced = ENVIRONMENT.WIND_PER_FORCING * max(0.0, totalForcingWm2)
    return min(ENVIRONMENT.MAX_WIND, ENVIRONMENT.BASE_WIND + thermal + forced)
}

/**
 * Wind at one latitude.
 *
 * [eastward] and [northward] are unit-ish components already scaled by
 * [strength]; positive eastward is toward increasing longitude, positive
 * northward toward the north pole.
 */
data class WindSample(val eastward: Double, val northward: Double) {
    val speed: Double get() = kotlin.math.sqrt(eastward * eastward + northward * northward)
}

/**
 * The three-cell zonal pattern, evaluated at [latitudeDeg].
 *
 * `-cos(3·|lat|)` puts easterlies over the tropics, reverses to westerlies
 * through the mid-latitudes and falls away toward the poles, which is the
 * shape of the real circulation at the only resolution this game needs. The
 * meridional term is a small equator-ward drift in the tropics and a
 * pole-ward one above them.
 */
fun windAt(latitudeDeg: Double, strength: Double): WindSample {
    val lat = latitudeDeg.coerceIn(-90.0, 90.0)
    val zonal = -cos(Math.toRadians(abs(lat) * 3.0))
    val meridional = -0.22 * sin(Math.toRadians(lat * 4.0))
    return WindSample(eastward = zonal * strength, northward = meridional * strength)
}
