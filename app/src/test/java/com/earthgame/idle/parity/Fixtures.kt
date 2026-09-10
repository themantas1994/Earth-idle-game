package com.earthgame.idle.parity

import com.earthgame.idle.domain.engine.GameDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs

/**
 * Loader and comparison helpers for the golden fixtures under
 * `src/test/resources/parity/`.
 *
 * Those files were produced by running the original TypeScript engine (see
 * `tools/ts-reference/exportParityFixtures.ts`), so every test that reads them
 * is comparing this port's answers against the reference implementation's
 * actual output rather than against a re-reading of its source.
 *
 * ## Tolerance
 *
 * The two engines run the same formulas on IEEE-754 doubles, but through
 * different `Math.pow`/`Math.log10`/`Math.exp` implementations, which are
 * permitted to differ in the last ulp. Errors therefore accumulate slightly
 * differently across a long chain of operations. The tolerances below are the
 * documented contract:
 *
 * - [TOLERANCE_EXACT] — integers, counts, ids, booleans, and any value that is
 *   a copy rather than a computation. No drift is acceptable.
 * - [TOLERANCE_TIGHT] — a single arithmetic operation or a short chain.
 * - [TOLERANCE_LOOSE] — the end of a long simulated trace, where thousands of
 *   dependent operations have compounded.
 *
 * Comparisons on [GameDecimal] are made in log space, so "relative difference"
 * is meaningful across the whole representable range: a value near 1e1000 is
 * held to the same relative accuracy as one near 1.
 */
object Fixtures {

    /** Relative tolerance for a value that must match bit-for-bit in practice. */
    const val TOLERANCE_EXACT = 0.0

    /** Relative tolerance for one arithmetic operation or a short chain of them. */
    const val TOLERANCE_TIGHT = 1e-9

    /** Relative tolerance at the end of a long dependent chain (a full simulated run). */
    const val TOLERANCE_LOOSE = 1e-6

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val cache = mutableMapOf<String, JsonElement>()

    fun load(name: String): JsonElement = cache.getOrPut(name) {
        val stream = Fixtures::class.java.classLoader!!.getResourceAsStream("parity/$name.json")
            ?: error(
                "Missing parity fixture parity/$name.json. Regenerate with " +
                    "`npm run fixtures` in tools/ts-reference.",
            )
        stream.bufferedReader().use { json.parseToJsonElement(it.readText()) }
    }

    fun obj(name: String): JsonObject = load(name).jsonObject

    fun array(name: String): JsonArray = load(name).jsonArray
}

/** A fixture-encoded [GameDecimal]: its exact internal triple plus a printed form. */
fun JsonElement.asGameDecimal(): GameDecimal {
    val o = jsonObject
    val sign = o.getValue("s").jsonPrimitive.int
    if (o["finite"]?.jsonPrimitive?.boolean == false) {
        // Division by zero. The reference produces a signed infinity rather
        // than throwing, and so must the port.
        return GameDecimal(sign, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
    }
    return GameDecimal.fromTriple(
        sign,
        o.getValue("m").jsonPrimitive.double,
        o.getValue("e").jsonPrimitive.double,
    )
}

fun JsonElement.doubleOrNull(): Double? =
    (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.double

// Named `getX` rather than `x` so they cannot shadow kotlinx.serialization's
// own JsonPrimitive.int / .double properties at the use site.
fun JsonObject.getDouble(key: String): Double = getValue(key).jsonPrimitive.double
fun JsonObject.getInt(key: String): Int = getValue(key).jsonPrimitive.int
fun JsonObject.getString(key: String): String = getValue(key).jsonPrimitive.content
fun JsonObject.getStringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
fun JsonObject.getBoolean(key: String): Boolean = getValue(key).jsonPrimitive.boolean

/**
 * Asserts two [GameDecimal]s agree to within [tolerance] *relative* difference,
 * compared in log space so the check is scale-independent.
 */
fun assertDecimalNear(
    message: String,
    expected: GameDecimal,
    actual: GameDecimal,
    tolerance: Double = Fixtures.TOLERANCE_TIGHT,
) {
    if (expected.isZero() && actual.isZero()) return
    assertEquals("$message: sign", expected.sign, actual.sign)

    if (!expected.isFinite() || !actual.isFinite()) {
        assertEquals("$message: finiteness", expected.isFinite(), actual.isFinite())
        return
    }

    // log10 difference d means a relative difference of 10^d - 1; for the small
    // d we care about that is d * ln(10), so compare against tolerance/ln(10).
    val logDelta = abs(expected.log10() - actual.log10())
    val allowed = tolerance / 2.302585092994046
    assertTrue(
        "$message: expected ${expected.toExponential(12)} but was ${actual.toExponential(12)} " +
            "(log10 delta $logDelta > $allowed)",
        logDelta <= allowed || (tolerance == 0.0 && expected == actual),
    )
}

/** Asserts two doubles agree to within [tolerance] relative difference, handling zero and infinity. */
fun assertDoubleNear(
    message: String,
    expected: Double,
    actual: Double,
    tolerance: Double = Fixtures.TOLERANCE_TIGHT,
) {
    if (expected.isNaN() || actual.isNaN()) {
        assertEquals("$message: NaN-ness", expected.isNaN(), actual.isNaN())
        return
    }
    if (expected.isInfinite() || actual.isInfinite()) {
        assertEquals("$message: infinity", expected, actual, 0.0)
        return
    }
    if (expected == actual) return
    if (tolerance == 0.0) {
        assertEquals(message, expected, actual, 0.0)
        return
    }
    val scale = maxOf(abs(expected), abs(actual))
    val allowed = if (scale == 0.0) tolerance else scale * tolerance
    assertTrue(
        "$message: expected $expected but was $actual (delta ${abs(expected - actual)} > $allowed)",
        abs(expected - actual) <= allowed,
    )
}
