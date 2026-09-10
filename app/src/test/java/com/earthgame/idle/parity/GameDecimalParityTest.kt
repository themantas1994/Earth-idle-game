package com.earthgame.idle.parity

import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.gd
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every arithmetic operation of [GameDecimal], checked against the values the
 * reference engine produced for the same inputs.
 *
 * The operand set spans the whole range the game uses and then some: zero,
 * negatives, sub-normal-ish 1e-400, the Double overflow boundary at 1e308, and
 * 1e1000 — the region that motivates the type existing at all.
 */
class GameDecimalParityTest {

    @Test
    fun `unary operations match the reference`() {
        for (case in Fixtures.obj("decimal").getValue("unary").jsonArray) {
            val o = case.jsonObject
            val input = o.getString("input")
            val x = gd(input)

            assertDecimalNear("$input parsed", o.getValue("parsed").asGameDecimal(), x, Fixtures.TOLERANCE_EXACT)
            assertDecimalNear("$input negate", o.getValue("negate").asGameDecimal(), x.negate(), Fixtures.TOLERANCE_EXACT)
            assertDecimalNear("$input abs", o.getValue("abs").asGameDecimal(), x.abs(), Fixtures.TOLERANCE_EXACT)
            assertDecimalNear("$input sqrt", o.getValue("sqrt").asGameDecimal(), x.abs().sqrt())

            assertEquals("$input isZero", o.getBoolean("isZero"), x.isZero())

            o.getValue("log10").doubleOrNull()?.let {
                assertDoubleNear("$input log10", it, x.log10())
            }
            o.getValue("ln").doubleOrNull()?.let {
                assertDoubleNear("$input ln", it, x.ln())
            }
            o.getValue("toNumber").doubleOrNull()?.let {
                assertDoubleNear("$input toNumber", it, x.toDouble())
            }

            assertEquals("$input toExponential(2)", o.getString("exponential2"), x.toExponential(2))
            assertEquals("$input toExponential(6)", o.getString("exponential6"), x.toExponential(6))

            val tuple = o.getValue("tuple").jsonArray
            assertEquals("$input tuple sign", tuple[0].jsonPrimitive.int, x.sign)
            assertEquals("$input tuple mantissa", tuple[1].jsonPrimitive.double, x.mantissa, 0.0)
            assertEquals("$input tuple exponent", tuple[2].jsonPrimitive.double, x.exponent, 0.0)
        }
    }

    @Test
    fun `binary operations match the reference`() {
        var checked = 0
        for (case in Fixtures.obj("decimal").getValue("binary").jsonArray) {
            val o = case.jsonObject
            val a = o.getString("a")
            val b = o.getString("b")
            val x = gd(a)
            val y = gd(b)
            val label = "$a op $b"

            assertDecimalNear("$label add", o.getValue("add").asGameDecimal(), x + y)
            assertDecimalNear("$label sub", o.getValue("sub").asGameDecimal(), x - y)
            assertDecimalNear("$label mul", o.getValue("mul").asGameDecimal(), x * y)
            assertDecimalNear("$label div", o.getValue("div").asGameDecimal(), x / y)
            assertEquals("$label cmp", o.getInt("cmp"), x.cmp(y))
            assertDecimalNear("$label max", o.getValue("max").asGameDecimal(), x.max(y), Fixtures.TOLERANCE_EXACT)
            assertDecimalNear("$label min", o.getValue("min").asGameDecimal(), x.min(y), Fixtures.TOLERANCE_EXACT)
            checked++
        }
        assertTrue("expected a substantial operand matrix, got $checked", checked > 500)
    }

    @Test
    fun `exponentiation matches the reference far beyond Double range`() {
        for (case in Fixtures.obj("decimal").getValue("powers").jsonArray) {
            val o = case.jsonObject
            val base = o.getString("base")
            val exp = o.getDouble("exp")
            assertDecimalNear("$base ^ $exp", o.getValue("result").asGameDecimal(), gd(base).pow(exp))
        }
    }

    @Test
    fun `the save triple round-trips`() {
        for (case in Fixtures.obj("decimal").getValue("roundTrip").jsonArray) {
            val o = case.jsonObject
            val input = o.getString("input")
            val x = gd(input)
            val (sign, mantissa, exponent) = x.toTriple()
            val revived = GameDecimal.fromTriple(sign, mantissa, exponent)
            assertDecimalNear("$input round trip", o.getValue("revived").asGameDecimal(), revived, Fixtures.TOLERANCE_EXACT)
            assertDecimalNear("$input round trip identity", x, revived, Fixtures.TOLERANCE_EXACT)
        }
    }

    // --- Properties the fixtures cannot express, checked directly ---

    @Test
    fun `values far past Double range never become infinite`() {
        var value = gd("1e300")
        repeat(20) { value = value * value }
        assertTrue("1e300 squared 20 times should stay finite", value.isFinite())
        assertTrue("and should be enormous", value.log10() > 1e8)
    }

    @Test
    fun `mantissa stays normalized through long chains`() {
        var value = GameDecimal.ONE
        repeat(500) { value = value * gd(7.3) + gd(11.0) }
        assertTrue("mantissa ${value.mantissa} out of [1,10)", value.mantissa >= 1.0 && value.mantissa < 10.0)
    }

    @Test
    fun `division by zero yields a signed infinity rather than a crash`() {
        assertTrue((gd(5.0) / GameDecimal.ZERO).isFinite().not())
        assertEquals(1, (gd(5.0) / GameDecimal.ZERO).sign)
        assertEquals(-1, (gd(-5.0) / GameDecimal.ZERO).sign)
        assertTrue((GameDecimal.ZERO / GameDecimal.ZERO).isFinite().not())
    }

    @Test
    fun `zero is canonical however it is reached`() {
        assertTrue((gd(5.0) - gd(5.0)).isZero())
        assertTrue((gd(0.0) * gd("1e500")).isZero())
        assertTrue(gd("-0").isZero())
        assertEquals(0, (gd(5.0) - gd(5.0)).sign)
    }

    @Test
    fun `addition drops terms below Double significance rather than losing the large one`() {
        val big = gd("1e300")
        assertDecimalNear("1e300 + 1", big, big + GameDecimal.ONE, Fixtures.TOLERANCE_EXACT)
        assertDecimalNear("1e300 + 1e280", big, big + gd("1e280"), Fixtures.TOLERANCE_EXACT)
        // ...but a term within significance still counts.
        assertTrue((big + gd("1e290")).gt(big))
    }

    @Test
    fun `comparison is a total order across signs and magnitudes`() {
        val values = listOf("-1e1000", "-1e10", "-1", "0", "1e-400", "1", "1e10", "1e1000").map { gd(it) }
        for (i in values.indices) {
            for (j in values.indices) {
                val expected = i.compareTo(j)
                assertEquals(
                    "${values[i]} vs ${values[j]}",
                    expected,
                    values[i].cmp(values[j]),
                )
            }
        }
    }
}
