package com.earthgame.idle.domain.storms

import com.earthgame.idle.domain.climate.windAt
import com.earthgame.idle.domain.engine.STORMS
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The storm simulation: formation, movement, intensification and dissipation.
 *
 * ## Why it steps at a fixed cadence
 *
 * Storms advance in whole [STORMS.STEP_SECONDS] steps with a carry
 * accumulator, never on whatever `dt` the caller happened to hand over. That
 * is what makes the live path and the offline path produce the *same* storm
 * timeline: 250 ms ticks and a single twelve-hour catch-up execute exactly the
 * same steps, at exactly the same step indices, against exactly the same
 * random draws. It is the storm system's version of the property the
 * closed-form gas integration gives the atmosphere — see
 * `docs/wiki/Offline-Progression.md`.
 *
 * ## Why the randomness is its own generator
 *
 * Every draw comes from [DeterministicRandom] seeded by
 * `(run seed, step index)`. Nothing here reads a shared mutable RNG, so
 * replaying a run from its seed reproduces the storms exactly, a save reloaded
 * mid-run continues the same sequence, and a test can assert on a specific
 * storm rather than on a distribution. Storms are probabilistic, but never
 * *arbitrary*: no storm forms unless the planet's own temperature and humidity
 * allow it.
 */

/** The climate inputs storm behaviour reads. All of them are derived from `GameState`. */
data class StormClimate(
    val temperatureAnomalyC: Double,
    /** 0..1, from `relativeHumidity`. */
    val humidity: Double,
    /** 0..1, from `windStrength`. */
    val windStrength: Double,
) {
    companion object {
        val CALM = StormClimate(0.0, 0.0, 0.0)
    }
}

/**
 * The saved storm state: the live storms plus the two counters that make the
 * timeline reproducible.
 */
data class StormField(
    val storms: List<Storm> = emptyList(),
    /** Whole steps executed since this Earth began. The per-step random anchor. */
    val stepsElapsed: Long = 0L,
    /** Simulated seconds not yet consumed by a whole step. */
    val carrySeconds: Double = 0.0,
) {
    companion object {
        val EMPTY = StormField()
    }
}

/** What happened to a storm during a step, for the news feed and the toasts. */
enum class StormBulletinKind { FORMED, INTENSIFIED, DISSIPATED }

data class StormBulletin(
    val kind: StormBulletinKind,
    val storm: Storm,
) {
    /**
     * Whether this is worth interrupting the player for — a headline, a toast,
     * the camera drifting toward it. A tropical storm quietly forming is not;
     * a superstorm reaching severe is.
     */
    val isMajor: Boolean
        get() = when (kind) {
            StormBulletinKind.INTENSIFIED -> true
            StormBulletinKind.FORMED ->
                storm.type == StormType.SUPERSTORM || storm.type == StormType.HURRICANE
            StormBulletinKind.DISSIPATED -> storm.peakIntensity >= 0.7
        }

    /** The headline the news feed shows. */
    val headline: String
        get() = when (kind) {
            StormBulletinKind.FORMED ->
                "${storm.displayName} forms over the ${oceanBasin(storm)}"
            StormBulletinKind.INTENSIFIED ->
                "${storm.displayName} intensifies to ${storm.severity.displayName.lowercase()}"
            StormBulletinKind.DISSIPATED ->
                "${storm.displayName} dissipates"
        }
}

/**
 * A coarse, entirely cosmetic basin name for a latitude/longitude, so a
 * headline reads like a headline. Nothing in the simulation reads it.
 */
private fun oceanBasin(storm: Storm): String {
    val lon = storm.longitudeDeg
    val northern = storm.latitudeDeg >= 0
    return when {
        lon < -100 -> if (northern) "eastern Pacific" else "southern Pacific"
        lon < -25 -> if (northern) "north Atlantic" else "south Atlantic"
        lon < 25 -> if (northern) "eastern Atlantic" else "south Atlantic"
        lon < 100 -> if (northern) "Arabian Sea" else "Indian Ocean"
        else -> if (northern) "western Pacific" else "Coral Sea"
    }
}

data class StormAdvanceResult(
    val field: StormField,
    val bulletins: List<StormBulletin>,
) {
    companion object {
        fun unchanged(field: StormField) = StormAdvanceResult(field, emptyList())
    }
}

// ------------------------------------------------------------- formation --

private fun clamp01(x: Double): Double = min(1.0, max(0.0, x))

/**
 * How ready the planet is to make a storm, 0..1.
 *
 * Zero below [STORMS.FORMATION_TEMPERATURE_FLOOR_C] of warming, which is what
 * keeps the early game storm-free: the opening hours of a run cannot produce
 * one however the dice fall. Above that it climbs with warming and with the
 * water vapour the climate model is already tracking, and saturates — an
 * extreme planet is not arbitrarily more stormy than a very hot one, it is
 * merely at the top of the curve.
 */
fun stormFormationPressure(climate: StormClimate): Double {
    val excess = climate.temperatureAnomalyC - STORMS.FORMATION_TEMPERATURE_FLOOR_C
    if (excess <= 0.0) return 0.0
    // Logarithmic, so the curve still has resolution at +2 °C and has not run
    // out of room at +200 °C. See STORMS.FORMATION_TEMPERATURE_SCALE_C.
    val thermal = clamp01(ln(1.0 + excess) / ln(1.0 + STORMS.FORMATION_TEMPERATURE_SCALE_C))
    if (thermal <= 0.0) return 0.0
    val moisture = clamp01(
        (climate.humidity - STORMS.FORMATION_HUMIDITY_FLOOR) / STORMS.FORMATION_HUMIDITY_SPAN,
    )
    val blended = thermal * ((1.0 - STORMS.MOISTURE_SHARE) + STORMS.MOISTURE_SHARE * moisture)
    return clamp01(blended)
}

/** The chance, per storm step, that a storm forms under [climate]. */
fun stormFormationChancePerStep(climate: StormClimate): Double {
    val pressure = stormFormationPressure(climate)
    if (pressure <= 0.0) return 0.0
    return STORMS.MAX_FORMATION_CHANCE_PER_STEP * pressure.pow(STORMS.PRESSURE_EXPONENT)
}

/** The categories [climate] can currently produce, in declaration order. */
fun eligibleStormTypes(climate: StormClimate): List<StormType> =
    StormType.entries.filter {
        climate.temperatureAnomalyC >= it.minTemperatureAnomalyC && climate.humidity >= it.minHumidity
    }

// -------------------------------------------------------------- stepping --

/**
 * Advances [field] by [dtSeconds] of simulated time under [climate].
 *
 * Pure: same inputs, same outputs, every time, on any device. The caller owns
 * where the result is stored and what it does with the bulletins.
 */
fun advanceStorms(
    field: StormField,
    seed: Long,
    climate: StormClimate,
    dtSeconds: Double,
): StormAdvanceResult {
    if (dtSeconds <= 0.0) return StormAdvanceResult.unchanged(field)

    val available = field.carrySeconds + dtSeconds
    val wholeSteps = floor(available / STORMS.STEP_SECONDS).toLong()
    if (wholeSteps <= 0L) {
        return StormAdvanceResult.unchanged(field.copy(carrySeconds = available))
    }

    // Defensive only: the offline cap keeps a real absence far below this, so
    // reaching it means a timestamp nothing should trust.
    val steps = min(wholeSteps, STORMS.MAX_STEPS_PER_CATCH_UP.toLong())
    val carry = available - wholeSteps * STORMS.STEP_SECONDS

    var storms = field.storms
    var stepIndex = field.stepsElapsed
    val bulletins = mutableListOf<StormBulletin>()

    val pressure = stormFormationPressure(climate)
    val formationChance = stormFormationChancePerStep(climate)
    val eligible = eligibleStormTypes(climate)

    var remaining = steps
    while (remaining > 0L) {
        storms = stepOnce(storms, stepIndex, seed, climate, pressure, formationChance, eligible, bulletins)
        stepIndex++
        remaining--
    }

    return StormAdvanceResult(
        field = StormField(storms = storms, stepsElapsed = stepIndex, carrySeconds = carry),
        bulletins = bulletins,
    )
}

private fun stepOnce(
    storms: List<Storm>,
    stepIndex: Long,
    seed: Long,
    climate: StormClimate,
    pressure: Double,
    formationChance: Double,
    eligible: List<StormType>,
    bulletins: MutableList<StormBulletin>,
): List<Storm> {
    val random = DeterministicRandom.forStep(seed, stepIndex)
    val next = ArrayList<Storm>(storms.size + 1)

    for (storm in storms) {
        val advanced = advanceOne(storm, climate, pressure)
        if (advanced == null) {
            bulletins += StormBulletin(StormBulletinKind.DISSIPATED, storm)
            continue
        }
        if (advanced.severity != storm.severity &&
            advanced.severity.ordinal > storm.severity.ordinal &&
            advanced.severity.ordinal >= StormSeverity.SEVERE.ordinal
        ) {
            bulletins += StormBulletin(StormBulletinKind.INTENSIFIED, advanced)
        }
        next += advanced
    }

    if (eligible.isNotEmpty() && next.size < STORMS.MAX_ACTIVE && formationChance > 0.0) {
        if (random.nextDouble() < formationChance) {
            val formed = formStorm(random, eligible, pressure, next, stepIndex)
            next += formed
            bulletins += StormBulletin(StormBulletinKind.FORMED, formed)
        }
    }

    return next
}

/** One storm's step. Returns null when it has dissipated. */
private fun advanceOne(storm: Storm, climate: StormClimate, pressure: Double): Storm? {
    val age = storm.ageSeconds + STORMS.STEP_SECONDS
    if (age >= storm.lifetimeSeconds) return null

    val life = age / storm.lifetimeSeconds
    val intensity = clamp01(storm.targetIntensity * intensityProfile(life) * stormSustain(pressure))

    val wind = windAt(storm.latitudeDeg, climate.windStrength)
    val poleward = STORMS.POLEWARD_BIAS * (if (storm.latitudeDeg >= 0.0) 1.0 else -1.0)
    var dirEast = wind.eastward
    var dirNorth = wind.northward + poleward
    val magnitude = sqrt(dirEast * dirEast + dirNorth * dirNorth)
    if (magnitude < 1e-9) {
        dirEast = 1.0
        dirNorth = 0.0
    } else {
        dirEast /= magnitude
        dirNorth /= magnitude
    }

    val arcDegrees = storm.type.driftDegreesPerSecond *
        (0.45 + 0.55 * intensity) *
        (0.55 + 0.75 * climate.windStrength) *
        STORMS.STEP_SECONDS

    val latitude = (storm.latitudeDeg + dirNorth * arcDegrees).coerceIn(-89.0, 89.0)
    if (abs(latitude) >= STORMS.DISSIPATION_LATITUDE) return null

    // A degree of longitude is a shorter arc away from the equator; the floor
    // stops the correction exploding as a storm approaches the pole.
    val longitudeScale = 1.0 / max(0.25, cos(Math.toRadians(latitude)))
    val longitude = wrapLongitude(storm.longitudeDeg + dirEast * arcDegrees * longitudeScale)

    return storm.copy(
        latitudeDeg = latitude,
        longitudeDeg = longitude,
        intensity = intensity,
        peakIntensity = max(storm.peakIntensity, intensity),
        ageSeconds = age,
        headingDeg = wrapHeading(Math.toDegrees(atan2(dirEast, dirNorth))),
    )
}

/**
 * Strength over a storm's life, 0..1 of its target: a spin-up, a plateau, and
 * a wind-down to nothing. Smoothstepped at both ends so a storm neither pops
 * into existence at full strength nor snaps off at the end.
 */
fun intensityProfile(lifeFraction: Double): Double {
    val t = clamp01(lifeFraction)
    return when {
        t < STORMS.SPIN_UP_FRACTION -> 0.12 + 0.88 * smoothstep(t / STORMS.SPIN_UP_FRACTION)
        t < STORMS.DECAY_FROM_FRACTION -> 1.0
        else -> 1.0 - smoothstep((t - STORMS.DECAY_FROM_FRACTION) / (1.0 - STORMS.DECAY_FROM_FRACTION))
    }
}

/**
 * How much of its target intensity a storm can hold under the current climate.
 *
 * A storm that formed under a stormier sky than the one it now sits under
 * winds down early, so improving the climate visibly calms the weather rather
 * than only slowing the arrival of the next storm. Never below 0.75, because a
 * storm already spinning does not simply stop when the forecast improves.
 */
fun stormSustain(formationPressure: Double): Double = 0.75 + 0.25 * clamp01(formationPressure)

private fun smoothstep(x: Double): Double {
    val t = clamp01(x)
    return t * t * (3.0 - 2.0 * t)
}

private fun formStorm(
    random: DeterministicRandom,
    eligible: List<StormType>,
    pressure: Double,
    active: List<Storm>,
    stepIndex: Long,
): Storm {
    val type = pickWeighted(random, eligible)

    val absLatitude = random.nextDouble(
        type.formationLatitudeRange.start,
        type.formationLatitudeRange.endInclusive,
    )
    val latitude = if (random.nextDouble() < 0.5) absLatitude else -absLatitude
    val longitude = random.nextDouble(-180.0, 180.0)

    // A stormier planet makes stronger storms within the category's own band —
    // never outside it, so a category always means what its name says.
    val peakFraction = clamp01((0.3 + 0.7 * pressure) * random.nextDouble(0.7, 1.0))
    val target = type.peakIntensityRange.start +
        peakFraction * (type.peakIntensityRange.endInclusive - type.peakIntensityRange.start)

    val lifetime = random.nextDouble(
        type.lifetimeSecondsRange.start,
        type.lifetimeSecondsRange.endInclusive,
    )

    val startIntensity = clamp01(target * intensityProfile(0.0))

    return Storm(
        id = "storm-$stepIndex",
        type = type,
        name = pickName(stepIndex, active),
        latitudeDeg = latitude,
        longitudeDeg = longitude,
        intensity = startIntensity,
        targetIntensity = target,
        peakIntensity = startIntensity,
        ageSeconds = 0.0,
        lifetimeSeconds = lifetime,
        headingDeg = if (latitude >= 0) 315.0 else 225.0,
        speedDegPerSecond = type.driftDegreesPerSecond,
        formedAtStep = stepIndex,
    )
}

private fun pickWeighted(random: DeterministicRandom, types: List<StormType>): StormType {
    val total = types.sumOf { it.weight }
    var threshold = random.nextDouble() * total
    for (type in types) {
        threshold -= type.weight
        if (threshold <= 0.0) return type
    }
    return types.last()
}

/**
 * The first name in the pool not already taken by a live storm, starting from
 * a position fixed by the formation step. Deterministic, and two storms can
 * never share a name while both are on the board.
 */
private fun pickName(stepIndex: Long, active: List<Storm>): String {
    val used = active.mapTo(HashSet()) { it.name }
    val start = stepIndex.mod(STORM_NAMES.size)
    for (offset in STORM_NAMES.indices) {
        val candidate = STORM_NAMES[(start + offset) % STORM_NAMES.size]
        if (candidate !in used) return candidate
    }
    return STORM_NAMES[start]
}

fun wrapLongitude(longitudeDeg: Double): Double {
    var value = longitudeDeg
    while (value > 180.0) value -= 360.0
    while (value < -180.0) value += 360.0
    return value
}

private fun wrapHeading(headingDeg: Double): Double {
    var value = headingDeg
    while (value < 0.0) value += 360.0
    while (value >= 360.0) value -= 360.0
    return value
}

/**
 * SplitMix64, seeded per storm step.
 *
 * Written out rather than taken from `kotlin.random` because storm behaviour
 * has to be reproducible from a seed *forever*, including across a Kotlin
 * upgrade that might retune the standard generator. The algorithm is a few
 * lines, has no state beyond a `Long`, and cannot be changed by accident.
 */
class DeterministicRandom(private var state: Long) {

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_A
        z = (z xor (z ushr 27)) * MIX_B
        return z xor (z ushr 31)
    }

    /** Uniform in [0, 1). */
    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() / TWO_POW_53

    /** Uniform in [from, until). */
    fun nextDouble(from: Double, until: Double): Double = from + nextDouble() * (until - from)

    companion object {
        private val GOLDEN_GAMMA = 0x9E3779B97F4A7C15uL.toLong()
        private val MIX_A = 0xBF58476D1CE4E5B9uL.toLong()
        private val MIX_B = 0x94D049BB133111EBuL.toLong()
        private const val TWO_POW_53 = 9007199254740992.0

        /**
         * The generator for one step of one run. Mixing the step index through
         * the gamma constant before combining keeps consecutive steps from
         * producing correlated first draws.
         */
        fun forStep(seed: Long, stepIndex: Long): DeterministicRandom =
            DeterministicRandom(seed xor (stepIndex * GOLDEN_GAMMA))
    }
}

/**
 * The storm seed for one Earth.
 *
 * Derived from when the Earth began and which number it is, so every run gets
 * its own weather and a given run replays identically — including through a
 * save, a reload and an absence. Never zero, which would collapse the mixing
 * for the first step.
 */
fun deriveStormSeed(createdAtMs: Long, runNumber: Int): Long {
    val mixed = createdAtMs * 0x5DEECE66DL + runNumber * 0x9E3779B1L + 0x2545F4914F6CDD1DL
    return if (mixed == 0L) 0x2545F4914F6CDD1DL else mixed
}
