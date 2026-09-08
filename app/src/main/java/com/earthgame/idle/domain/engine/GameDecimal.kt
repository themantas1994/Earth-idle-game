package com.earthgame.idle.domain.engine

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.sign

/**
 * Arbitrary-scale numeric type for incremental-game math.
 *
 * A `Double` overflows to Infinity around 1.8e308, which this game's late-game
 * greenhouse-gas totals are designed to exceed — a finished run reaches values
 * past 1e400 and the prestige formula multiplies them further. [GameDecimal]
 * stores a value as `sign * mantissa * 10^exponent` with `mantissa` normalized
 * to `[1, 10)` and `exponent` an ordinary `Double`, so the representable range
 * is bounded only by how large a *power* a Double can hold: roughly
 * 10^(1.8e308), which is unreachable in this game.
 *
 * This is a direct port of the reference implementation's `bignum.ts`, itself
 * modeled on the `break_infinity.js` approach the incremental genre uses. The
 * port is deliberately literal — the same branch order, the same significance
 * cutoff, the same log-space exponentiation — because the whole economy's
 * numbers are defined by it. `GameDecimalParityTest` checks the arithmetic
 * against values captured from the reference engine.
 *
 * Instances are immutable; every operation returns a new value.
 */
class GameDecimal(sign: Int, val mantissa: Double, val exponent: Double) {

    /** -1, 0 or 1. Always 0 when [mantissa] is 0, whatever sign was requested. */
    val sign: Int = if (mantissa == 0.0) 0 else if (sign < 0) -1 else 1

    companion object {
        /**
         * Beyond ~17 orders of magnitude a term cannot affect a Double
         * mantissa's precision, so addition drops it rather than rounding it
         * away. This is what keeps `1e300 + 1` from costing anything.
         */
        private const val MAX_SIGNIFICANT_DIGITS = 17.0

        val ZERO = GameDecimal(0, 0.0, 0.0)
        val ONE = GameDecimal(1, 1.0, 0.0)
        val TEN = GameDecimal(1, 1.0, 1.0)

        /**
         * Pulls `mantissa` into `[1, 10)` by shifting the difference into the
         * exponent. Every arithmetic result goes through here, so the invariant
         * holds for every value in circulation.
         */
        fun normalize(sign: Int, mantissa: Double, exponent: Double): GameDecimal {
            if (mantissa == 0.0 || sign == 0) return ZERO
            if (!mantissa.isFinite() || !exponent.isFinite()) {
                return GameDecimal(if (sign < 0) -1 else 1, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
            }

            val shift = floor(log10(abs(mantissa)))
            var m = mantissa / pow10(shift)
            var e = exponent + shift

            // log10 rounding can occasionally push m to exactly 10 or just under 1.
            if (m >= 10.0) {
                m /= 10.0
                e += 1.0
            } else if (m < 1.0) {
                m *= 10.0
                e -= 1.0
            }

            return GameDecimal(if (sign < 0) -1 else 1, m, e)
        }

        fun of(n: Double): GameDecimal {
            if (n == 0.0 || n.isNaN()) return ZERO
            if (!n.isFinite()) {
                return GameDecimal(if (n < 0) -1 else 1, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
            }
            return normalize(signOf(n), abs(n), 0.0)
        }

        fun of(n: Int): GameDecimal = of(n.toDouble())

        fun of(n: Long): GameDecimal = of(n.toDouble())

        /** Parses `"1.23e45"`, `"-4.2e-3"`, or a plain decimal string. */
        fun of(s: String): GameDecimal {
            val trimmed = s.trim()
            val eIdx = trimmed.indexOfFirst { it == 'e' || it == 'E' }
            if (eIdx == -1) return of(trimmed.toDoubleOrNull() ?: 0.0)
            val mantissaPart = trimmed.substring(0, eIdx).toDoubleOrNull() ?: 0.0
            val exponentPart = trimmed.substring(eIdx + 1).toDoubleOrNull() ?: 0.0
            if (mantissaPart == 0.0) return ZERO
            return normalize(signOf(mantissaPart), abs(mantissaPart), exponentPart)
        }

        /**
         * Rebuilds a value from the `[sign, mantissa, exponent]` triple the
         * save format stores. Re-normalizes, so a hand-edited or
         * older-format save cannot introduce an unnormalized value.
         */
        fun fromTriple(sign: Int, mantissa: Double, exponent: Double): GameDecimal {
            if (mantissa == 0.0) return ZERO
            return normalize(sign, abs(mantissa), exponent)
        }

        /** `Math.sign` semantics: -1, 0, 1 — never the Kotlin `Double.sign` NaN. */
        private fun signOf(n: Double): Int = when {
            n > 0 -> 1
            n < 0 -> -1
            else -> 0
        }

        /**
         * `10^e` for a Double exponent. Kept in one place because it is on the
         * hot path of every normalize, and because `Math.pow(10.0, e)`'s
         * behaviour at the extremes is part of the ported semantics.
         */
        private fun pow10(e: Double): Double = Math.pow(10.0, e)
    }

    fun isZero(): Boolean = sign == 0

    fun isFinite(): Boolean = exponent.isFinite()

    fun negate(): GameDecimal = GameDecimal(-sign, mantissa, exponent)

    fun abs(): GameDecimal = GameDecimal(1, mantissa, exponent)

    operator fun plus(other: GameDecimal): GameDecimal {
        if (isZero()) return other
        if (other.isZero()) return this

        val big: GameDecimal
        val small: GameDecimal
        if (exponent >= other.exponent) {
            big = this; small = other
        } else {
            big = other; small = this
        }
        val expDiff = big.exponent - small.exponent
        if (expDiff > MAX_SIGNIFICANT_DIGITS) return big

        val bigSigned = big.sign * big.mantissa
        val smallSigned = small.sign * small.mantissa / Math.pow(10.0, expDiff)
        val resultMantissa = bigSigned + smallSigned
        if (resultMantissa == 0.0) return ZERO
        return normalize(if (resultMantissa < 0) -1 else 1, abs(resultMantissa), big.exponent)
    }

    operator fun plus(other: Double): GameDecimal = this + of(other)

    operator fun plus(other: Int): GameDecimal = this + of(other.toDouble())

    operator fun minus(other: GameDecimal): GameDecimal = this + other.negate()

    operator fun minus(other: Double): GameDecimal = this + of(other).negate()

    operator fun minus(other: Int): GameDecimal = this + of(other.toDouble()).negate()

    operator fun times(other: GameDecimal): GameDecimal {
        if (isZero() || other.isZero()) return ZERO
        return normalize(sign * other.sign, mantissa * other.mantissa, exponent + other.exponent)
    }

    operator fun times(other: Double): GameDecimal = this * of(other)

    operator fun times(other: Int): GameDecimal = this * of(other.toDouble())

    operator fun div(other: GameDecimal): GameDecimal {
        if (other.isZero()) {
            return GameDecimal(if (sign != 0) sign else 1, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
        }
        if (isZero()) return ZERO
        return normalize(sign * other.sign, mantissa / other.mantissa, exponent - other.exponent)
    }

    operator fun div(other: Double): GameDecimal = this / of(other)

    operator fun div(other: Int): GameDecimal = this / of(other.toDouble())

    /**
     * Exponentiation in log space: `(m * 10^e)^p = 10^(p * (log10(m) + e))`.
     * Doing it this way is what keeps `1e100.pow(1000)` finite.
     */
    fun pow(exp: Double): GameDecimal {
        if (exp == 0.0) return ONE
        if (isZero()) return ZERO
        val logValue = exp * (log10(mantissa) + exponent)
        val resultSign = if (sign < 0 && abs(exp % 2.0) == 1.0) -1 else 1
        val newExponent = floor(logValue)
        val newMantissa = Math.pow(10.0, logValue - newExponent)
        return normalize(resultSign, newMantissa, newExponent)
    }

    fun pow(exp: Int): GameDecimal = pow(exp.toDouble())

    fun sqrt(): GameDecimal = pow(0.5)

    /** log10 of the absolute value; -Infinity for zero. */
    fun log10(): Double {
        if (isZero()) return Double.NEGATIVE_INFINITY
        return exponent + log10(mantissa)
    }

    fun ln(): Double = log10() * LN_10

    fun cmp(other: GameDecimal): Int {
        if (sign != other.sign) return if (sign < other.sign) -1 else 1
        if (isZero() && other.isZero()) return 0
        val magnitude = if (exponent == other.exponent) {
            (mantissa - other.mantissa).let { if (it > 0) 1 else if (it < 0) -1 else 0 }
        } else if (exponent < other.exponent) -1 else 1
        return if (sign < 0) -magnitude else magnitude
    }

    fun cmp(other: Double): Int = cmp(of(other))

    fun eq(other: GameDecimal): Boolean = cmp(other) == 0
    fun lt(other: GameDecimal): Boolean = cmp(other) < 0
    fun lte(other: GameDecimal): Boolean = cmp(other) <= 0
    fun gt(other: GameDecimal): Boolean = cmp(other) > 0
    fun gte(other: GameDecimal): Boolean = cmp(other) >= 0

    fun eq(other: Double): Boolean = cmp(other) == 0
    fun lt(other: Double): Boolean = cmp(other) < 0
    fun lte(other: Double): Boolean = cmp(other) <= 0
    fun gt(other: Double): Boolean = cmp(other) > 0
    fun gte(other: Double): Boolean = cmp(other) >= 0

    fun max(other: GameDecimal): GameDecimal = if (gte(other)) this else other
    fun min(other: GameDecimal): GameDecimal = if (lte(other)) this else other

    fun clampMin(minimum: GameDecimal): GameDecimal = max(minimum)
    fun clampMin(minimum: Double): GameDecimal = max(of(minimum))
    fun clamp(minimum: GameDecimal, maximum: GameDecimal): GameDecimal = max(minimum).min(maximum)

    /** Best-effort conversion back to a Double for UI-only maths; may be Infinity. */
    fun toDouble(): Double {
        if (isZero()) return 0.0
        if (exponent > 308) return sign * Double.POSITIVE_INFINITY
        return sign * mantissa * Math.pow(10.0, exponent)
    }

    fun toExponential(digits: Int = 2): String {
        if (isZero()) return jsToFixed(0.0, digits) + "e+0"
        val signPart = if (sign < 0) "-" else ""
        val expPart = if (exponent >= 0) "+" else ""
        return "$signPart${jsToFixed(mantissa, digits)}e$expPart${formatExponent(exponent)}"
    }

    /** The `[sign, mantissa, exponent]` triple the save format stores. */
    fun toTriple(): Triple<Int, Double, Double> = Triple(sign, mantissa, exponent)

    override fun toString(): String = toExponential(6)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameDecimal) return false
        return sign == other.sign && mantissa == other.mantissa && exponent == other.exponent
    }

    override fun hashCode(): Int {
        var result = sign
        result = 31 * result + mantissa.hashCode()
        result = 31 * result + exponent.hashCode()
        return result
    }
}

private const val LN_10 = 2.302585092994046

/**
 * The exponent is carried as a Double so it can exceed Int range, but it is
 * always integral in practice and has to print as an integer — `1e+45`, never
 * `1e+45.0`.
 */
internal fun formatExponent(exponent: Double): String {
    if (!exponent.isFinite()) return if (exponent > 0) "Infinity" else "-Infinity"
    if (exponent == floor(exponent) && abs(exponent) < 1e18) return exponent.toLong().toString()
    return exponent.toString()
}

/**
 * ECMAScript `Number.prototype.toFixed`, which the reference implementation's
 * output was written against.
 *
 * It rounds the *exact binary value* of the double half-away-from-zero — not
 * the shortest decimal string that would round-trip to it — so `(1.005)
 * .toFixed(2)` is `"1.00"`, because the double nearest 1.005 is slightly
 * below it. Java's `String.format("%.2f", …)` follows the same rule for
 * positive values, but going through BigDecimal's exact constructor states
 * the intent and removes any dependence on Locale.
 */
internal fun jsToFixed(value: Double, digits: Int): String {
    if (value.isNaN()) return "NaN"
    if (!value.isFinite()) return if (value > 0) "Infinity" else "-Infinity"
    val negative = value < 0 || (value == 0.0 && 1.0 / value < 0)
    val magnitude = BigDecimal(kotlin.math.abs(value)).setScale(digits, RoundingMode.HALF_UP).toPlainString()
    // ECMAScript prints -0 as "0.00", and negates only a non-zero result.
    return if (negative && magnitude.any { it in '1'..'9' }) "-$magnitude" else magnitude
}

/** Shorthand mirroring the reference engine's `D(...)` helper. */
fun gd(value: Double): GameDecimal = GameDecimal.of(value)
fun gd(value: Int): GameDecimal = GameDecimal.of(value)
fun gd(value: Long): GameDecimal = GameDecimal.of(value)
fun gd(value: String): GameDecimal = GameDecimal.of(value)

operator fun Double.times(other: GameDecimal): GameDecimal = GameDecimal.of(this) * other
operator fun Int.times(other: GameDecimal): GameDecimal = GameDecimal.of(this) * other
