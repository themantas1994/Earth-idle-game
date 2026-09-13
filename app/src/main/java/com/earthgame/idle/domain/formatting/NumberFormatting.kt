package com.earthgame.idle.domain.formatting

import com.earthgame.idle.domain.engine.GAME_SECONDS_PER_YEAR
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.formatExponent
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.engine.jsToFixed
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor
import kotlin.math.max

/** The four notations the player can pick between in Settings. */
enum class NumberFormatMode(val id: String) {
    COMPACT("compact"),
    SCIENTIFIC("scientific"),
    ENGINEERING("engineering"),
    FULL("full");

    companion object {
        fun fromId(id: String?): NumberFormatMode =
            entries.firstOrNull { it.id == id } ?: COMPACT
    }
}

/**
 * Short-scale suffixes, the standard incremental-game convention past "T".
 * Beyond the named list (tier 21, i.e. 1e63) formatting falls back to a stable
 * alphabetic scheme so it can never run out of names, however far a run goes.
 */
private val COMPACT_SUFFIXES = arrayOf(
    "", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No",
    "Dc", "UDc", "DDc", "TDc", "QaDc", "QiDc", "SxDc", "SpDc", "OcDc", "NoDc",
    "Vg",
)

/** Offsetting the fallback by a full alphabet is what skips the one-letter run. */
private const val SINGLE_LETTER_COUNT = 26

/**
 * The suffix for a magnitude tier, falling back past the named list.
 *
 * The fallback starts at two letters ("AA") rather than one, which is what
 * keeps it unambiguous: a single-letter fallback printed 1e69 as "1B" and 1e96
 * as "1K", colliding with billions and thousands — both of which a run passes
 * through on the way there, so a player could not tell 1e9 from 1e69 on the
 * only screen that shows it. Every named suffix is a single letter or mixed
 * case, so no all-caps pair can collide with one.
 */
private fun letterSuffixForTier(tier: Int): String {
    if (tier < COMPACT_SUFFIXES.size) return COMPACT_SUFFIXES[tier]
    var n = tier - COMPACT_SUFFIXES.size + SINGLE_LETTER_COUNT
    val builder = StringBuilder()
    do {
        builder.insert(0, ('A' + (n % 26)))
        n = n / 26 - 1
    } while (n >= 0)
    return builder.toString()
}

private fun trimTrailing(s: String): String {
    if (!s.contains('.')) return s
    return s.trimEnd('0').trimEnd('.')
}

private fun formatCompact(value: GameDecimal, precision: Int): String {
    if (value.isZero()) return "0"
    val sign = if (value.sign < 0) "-" else ""
    val abs = value.abs()
    if (abs.lt(gd(1000))) {
        val digits = if (abs.lt(gd(10))) precision else max(0, precision - 1)
        return sign + trimTrailing(jsToFixed(abs.toDouble(), digits))
    }
    val tier = floor(abs.exponent / 3.0).toInt()
    val scaled = abs / GameDecimal.TEN.pow(tier * 3)
    return "$sign${trimTrailing(jsToFixed(scaled.toDouble(), precision))}${letterSuffixForTier(tier)}"
}

private fun formatScientific(value: GameDecimal, precision: Int): String {
    if (value.isZero()) return jsToFixed(0.0, precision)
    return value.toExponential(precision)
}

/** Engineering notation: the exponent is always a multiple of 3, matching SI prefixes. */
private fun formatEngineering(value: GameDecimal, precision: Int): String {
    if (value.isZero()) return jsToFixed(0.0, precision) + "e+0"
    val sign = if (value.sign < 0) "-" else ""
    val abs = value.abs()
    val tier = floor(abs.exponent / 3.0)
    val scaled = abs / GameDecimal.TEN.pow(tier * 3.0)
    val expSign = if (tier >= 0) "+" else ""
    return "$sign${jsToFixed(scaled.toDouble(), precision)}e$expSign${formatExponent(tier * 3.0)}"
}

private fun formatFull(value: GameDecimal): String {
    if (value.isZero()) return "0"
    if (!value.isFinite()) return if (value.sign < 0) "-Infinity" else "Infinity"
    // Full digit expansion past this is unreadable, so fall back to scientific.
    if (value.exponent > 100) return formatScientific(value, 4)
    return groupWithCommas(value.toDouble())
}

/**
 * `Number.toLocaleString('en-US', { maximumFractionDigits: 2 })`: comma
 * grouping, at most two fraction digits, and trailing zeros dropped.
 *
 * Note this rounds the *shortest decimal that round-trips* to the double, not
 * the exact binary value — which is what `toLocaleString` does and what
 * `toFixed` (see `jsToFixed`) deliberately does not. Formatting 1e24 the other
 * way prints 999,999,999,999,999,983,222,784, which is technically the stored
 * value and visibly wrong to a player. Kotlin's `Double.toString` produces that
 * shortest representation on JDK 19+.
 */
private fun groupWithCommas(value: Double): String {
    if (!value.isFinite()) return if (value > 0) "∞" else "-∞"
    val rounded = BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
    val plain = rounded.toPlainString()
    val negative = plain.startsWith("-")
    val body = if (negative) plain.substring(1) else plain
    val dot = body.indexOf('.')
    val intPart = if (dot == -1) body else body.substring(0, dot)
    val fracPart = if (dot == -1) "" else body.substring(dot)

    val grouped = StringBuilder()
    for ((index, ch) in intPart.withIndex()) {
        if (index > 0 && (intPart.length - index) % 3 == 0) grouped.append(',')
        grouped.append(ch)
    }
    return (if (negative) "-" else "") + grouped + fracPart
}

/** Formats a value for display in the player's chosen notation. */
fun formatNumber(value: GameDecimal, mode: NumberFormatMode = NumberFormatMode.COMPACT, precision: Int = 2): String =
    when (mode) {
        NumberFormatMode.SCIENTIFIC -> formatScientific(value, precision)
        NumberFormatMode.ENGINEERING -> formatEngineering(value, precision)
        NumberFormatMode.FULL -> formatFull(value)
        NumberFormatMode.COMPACT -> formatCompact(value, precision)
    }

fun formatNumber(value: Double, mode: NumberFormatMode = NumberFormatMode.COMPACT, precision: Int = 2): String =
    formatNumber(gd(value), mode, precision)

fun formatNumber(value: Int, mode: NumberFormatMode = NumberFormatMode.COMPACT, precision: Int = 2): String =
    formatNumber(gd(value), mode, precision)

/** Formats a rate with a trailing unit, e.g. `"4.82M kg/s"`. */
fun formatRate(
    value: GameDecimal,
    unit: String,
    mode: NumberFormatMode = NumberFormatMode.COMPACT,
    precision: Int = 2,
): String = "${formatNumber(value, mode, precision)} $unit"

/** Formats seconds as a compact `"1d 2h"` / `"42m 03s"` duration. */
fun formatDuration(totalSeconds: Double): String {
    if (totalSeconds.isNaN()) return "0s"
    if (totalSeconds.isInfinite()) return "∞"
    val s = max(0.0, floor(totalSeconds)).toLong()
    val days = s / 86_400
    val hours = (s % 86_400) / 3_600
    val minutes = (s % 3_600) / 60
    val seconds = s % 60
    val parts = mutableListOf<String>()
    if (days > 0) parts += "${days}d"
    if (days > 0 || hours > 0) parts += "${hours}h"
    if (days == 0L && (hours > 0 || minutes > 0)) parts += "${minutes}m"
    if (days == 0L && hours == 0L) parts += "${seconds}s"
    return if (parts.isEmpty()) "0s" else parts.joinToString(" ")
}

/**
 * Formats a simulated age as `"12y 4m 12d"`.
 *
 * This is the Earth's own age on the simulated calendar, not how long the
 * player has been playing — see `GameTime.kt` for the two clocks. Months are
 * 1/12 of a Julian year (30.4375 days) rather than calendar months: a planet
 * has an age, not a calendar. Components above the largest non-zero one are
 * dropped, so a brand-new Earth reads `"0d"` rather than `"0y 0m 0d"`, and
 * past ten thousand years only the years are shown — by then the months are
 * noise next to the leading figure, and the leading figure is what the player
 * is watching.
 */
fun formatGameAge(gameAgeSeconds: Double, mode: NumberFormatMode = NumberFormatMode.COMPACT): String {
    if (gameAgeSeconds.isNaN()) return "0d"
    if (gameAgeSeconds.isInfinite()) return "∞"

    // Decomposed from seconds rather than from whole days: a Julian year is
    // 365.25 days, so flooring to days first loses the quarter and leaves an
    // Earth that has run for exactly one year reading "11m 30d".
    val secondsPerMonth = GAME_SECONDS_PER_YEAR / 12.0
    val total = max(0.0, gameAgeSeconds)

    val years = floor(total / GAME_SECONDS_PER_YEAR)
    val afterYears = total - years * GAME_SECONDS_PER_YEAR
    val months = floor(afterYears / secondsPerMonth)
    val days = floor((afterYears - months * secondsPerMonth) / 86_400.0)

    if (years >= 10_000) return "${formatNumber(gd(years), mode)}y"

    val parts = mutableListOf<String>()
    if (years > 0) parts += "${years.toLong()}y"
    if (years > 0 || months > 0) parts += "${months.toLong()}m"
    parts += "${days.toLong()}d"
    return parts.joinToString(" ")
}

fun formatTemperature(celsius: Double, precision: Int = 2): String {
    val sign = if (celsius >= 0) "+" else ""
    return "$sign${jsToFixed(celsius, precision)}°C"
}

fun formatPercent(fraction: Double, precision: Int = 1): String = "${jsToFixed(fraction * 100, precision)}%"

/** Fixed-decimal formatting for the physical readouts (W/m², pH, metres). */
fun formatFixed(value: Double, precision: Int): String = jsToFixed(value, precision)
