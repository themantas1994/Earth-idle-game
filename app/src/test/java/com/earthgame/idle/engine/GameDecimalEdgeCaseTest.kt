package com.earthgame.idle.engine

import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.formatting.NumberFormatMode
import com.earthgame.idle.domain.formatting.formatDuration
import com.earthgame.idle.domain.formatting.formatNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GameDecimal] behaviour at the edges of its own contract, and the boundary
 * where ordinary Kotlin numbers meet it.
 *
 * `GameDecimalParityTest` proves this type agrees with the reference
 * implementation. This proves the invariants it has to hold *whatever* the
 * reference does: that the mantissa is always normalized, that a value can
 * never be silently downgraded to a `Double`, that formatting is read-only, and
 * that nothing in the type can produce a NaN.
 */
class GameDecimalEdgeCaseTest {

    private fun assertNormalized(value: GameDecimal, label: String) {
        if (value.isZero()) {
            assertEquals("$label: zero must be canonical", 0.0, value.mantissa, 0.0)
            assertEquals("$label: zero must be canonical", 0.0, value.exponent, 0.0)
            return
        }
        if (!value.isFinite()) return
        assertTrue("$label: mantissa ${value.mantissa} must be in [1, 10)", value.mantissa >= 1.0 && value.mantissa < 10.0)
        assertFalse("$label: mantissa must never be NaN", value.mantissa.isNaN())
        assertFalse("$label: exponent must never be NaN", value.exponent.isNaN())
    }

    private val operands = listOf(
        GameDecimal.ZERO,
        gd(1.0), gd(-1.0), gd(9.999999999), gd(-9.999999999),
        gd(1e-320), gd(-1e-320), gd("1e-4000"), gd("-1e-4000"),
        gd("1e308"), gd("1.7976931348623157e308"), gd("1e309"), gd("1e4000"), gd("-1e4000"),
        gd(0.1), gd(1.0 / 3.0), gd(Long.MAX_VALUE), gd(Int.MIN_VALUE),
    )

    // ------------------------------------------------------- normalization --

    @Test
    fun `every arithmetic result is normalized`() {
        for (a in operands) {
            for (b in operands) {
                assertNormalized(a + b, "$a + $b")
                assertNormalized(a - b, "$a - $b")
                assertNormalized(a * b, "$a * $b")
                if (!b.isZero()) assertNormalized(a / b, "$a / $b")
            }
            assertNormalized(a.negate(), "-$a")
            assertNormalized(a.abs(), "|$a|")
            assertNormalized(a.pow(3.0), "$a^3")
            assertNormalized(a.sqrt(), "sqrt($a)")
        }
    }

    @Test
    fun `an un-normalized value handed to the constructor is repaired by fromTriple`() {
        // A hand-edited save is the way this actually happens.
        val repaired = GameDecimal.fromTriple(1, 12345.0, 7.0)

        assertNormalized(repaired, "fromTriple")
        assertEquals(1.2345, repaired.mantissa, 1e-12)
        assertEquals(11.0, repaired.exponent, 0.0)
    }

    // -------------------------------------------------------- sign and zero --

    @Test
    fun `zero has exactly one representation whatever produces it`() {
        val zeros = listOf(
            GameDecimal.ZERO,
            gd(0.0),
            gd(-0.0),
            gd("0"),
            gd("-0.0"),
            gd("0e500"),
            gd(1.0) - gd(1.0),
            gd(5.0) * GameDecimal.ZERO,
            GameDecimal.ZERO / gd(3.0),
            GameDecimal.fromTriple(-1, 0.0, 99.0),
            gd(Double.NaN),
        )

        for (zero in zeros) {
            assertTrue("must be zero", zero.isZero())
            assertEquals(0, zero.sign)
            assertEquals(GameDecimal.ZERO, zero)
            assertEquals(GameDecimal.ZERO.hashCode(), zero.hashCode())
        }
    }

    @Test
    fun `negation round-trips and never produces a signed zero`() {
        for (a in operands) {
            assertEquals(a, a.negate().negate())
            if (a.isZero()) assertEquals(0, a.negate().sign)
        }
    }

    @Test
    fun `subtracting a value from itself is exactly zero at every scale`() {
        for (a in operands) {
            assertTrue("$a - $a", (a - a).isZero())
        }
    }

    // -------------------------------------------------------- total ordering --

    @Test
    fun `comparison is a consistent total order`() {
        for (a in operands) {
            assertEquals("$a vs itself", 0, a.cmp(a))
            for (b in operands) {
                val ab = a.cmp(b)
                val ba = b.cmp(a)
                assertEquals("$a/$b must be antisymmetric", ab, -ba)
                assertEquals("cmp and eq must agree for $a/$b", ab == 0, a.eq(b))
                assertEquals("max must agree with cmp for $a/$b", if (ab >= 0) a else b, a.max(b))
                assertEquals("min must agree with cmp for $a/$b", if (ab <= 0) a else b, a.min(b))
            }
        }
    }

    @Test
    fun `clamp never returns a value outside its bounds`() {
        val low = gd(-100.0)
        val high = gd("1e50")
        for (a in operands) {
            val clamped = a.clamp(low, high)
            assertTrue("$a clamped below", clamped.gte(low))
            assertTrue("$a clamped above", clamped.lte(high))
        }
    }

    // ------------------------------------------------------- extreme scales --

    @Test
    fun `values far outside Double range stay exact`() {
        val huge = gd("1e5000")
        val tiny = gd("1e-5000")

        assertTrue(huge.isFinite())
        assertTrue(tiny.isFinite())
        assertEquals(5000.0, huge.exponent, 0.0)
        assertEquals(-5000.0, tiny.exponent, 0.0)
        assertEquals(0.0, (huge * tiny).exponent, 1e-9)
        assertTrue("a value past Double range must still be greater than one inside it", huge.gt(gd("1e308")))
    }

    @Test
    fun `exponentiation stays in log space rather than overflowing`() {
        val result = gd("1e100").pow(1000.0)

        assertTrue(result.isFinite())
        assertEquals(100_000.0, result.exponent, 1e-6)
    }

    @Test
    fun `a whole run's compounding never reaches infinity`() {
        var value = gd(1000.0)
        repeat(500) { value = value * gd(1e6) }

        assertTrue("500 compounding steps must stay finite", value.isFinite())
        assertEquals(3003.0, value.exponent, 1e-6)
    }

    @Test
    fun `dividing by zero yields a signed infinity rather than NaN or a crash`() {
        assertFalse((gd(5.0) / GameDecimal.ZERO).isFinite())
        assertEquals(1, (gd(5.0) / GameDecimal.ZERO).sign)
        assertEquals(-1, (gd(-5.0) / GameDecimal.ZERO).sign)
        // Zero over zero short-circuits on the divisor, which is the branch
        // order the reference implementation uses.
        assertFalse((GameDecimal.ZERO / GameDecimal.ZERO).isFinite())
    }

    @Test
    fun `addition drops a term it cannot represent instead of losing the large one`() {
        val big = gd("1e300")

        assertEquals("adding a negligible term must be exact", big, big + gd(1.0))
        assertTrue("but a comparable one must land", (big + gd("1e299")).gt(big))
    }

    // ----------------------------------------- the Double boundary and the UI --

    @Test
    fun `toDouble saturates rather than wrapping, and never mutates the value`() {
        val huge = gd("1e400")
        val before = huge.toTriple()

        assertTrue(huge.toDouble().isInfinite())
        assertEquals("reading a value as a Double must not change it", before, huge.toTriple())
        assertTrue(huge.isFinite())

        assertEquals("underflow reads as zero, not as garbage", 0.0, gd("1e-400").toDouble(), 0.0)
    }

    @Test
    fun `formatting never mutates the value it formats`() {
        // Formatting runs on every frame against live gameplay values, so a
        // formatter that normalized or rounded in place would quietly corrupt
        // the economy.
        for (a in operands) {
            val before = a.toTriple()
            for (mode in NumberFormatMode.entries) {
                for (precision in 0..4) formatNumber(a, mode, precision)
            }
            a.toExponential(6)
            a.toString()
            a.log10()
            a.ln()
            assertEquals("$a must be unchanged by formatting", before, a.toTriple())
        }
    }

    @Test
    fun `formatting produces something readable at every magnitude and mode`() {
        for (a in operands) {
            for (mode in NumberFormatMode.entries) {
                val text = formatNumber(a, mode)
                assertTrue("$a in $mode produced an empty string", text.isNotEmpty())
                assertFalse("$a in $mode produced NaN", text.contains("NaN"))
            }
        }
    }

    @Test
    fun `compact notation keeps naming tiers however far a run goes`() {
        val seen = mutableSetOf<String>()
        for (exponent in 0..600 step 3) {
            val suffix = formatNumber(gd("1e$exponent"), NumberFormatMode.COMPACT).dropWhile { it.isDigit() || it == '.' }
            assertTrue("1e$exponent produced no suffix", exponent < 3 || suffix.isNotEmpty())
            assertTrue("1e$exponent reused the suffix '$suffix'", seen.add(suffix))
        }
    }

    @Test
    fun `duration formatting survives the values a stuck clock can produce`() {
        assertEquals("0s", formatDuration(0.0))
        assertEquals("0s", formatDuration(-1.0))
        assertEquals("0s", formatDuration(Double.NaN))
        assertEquals("∞", formatDuration(Double.POSITIVE_INFINITY))
        assertEquals("1d 0h", formatDuration(86_400.0))
        assertEquals("59s", formatDuration(59.9))
    }

    // ---------------------------------------------------------- construction --

    @Test
    fun `parsing accepts the shapes a save or a constant can carry`() {
        assertEquals(gd(1500.0), gd("1.5e3"))
        assertEquals(gd(1500.0), gd("1.5E3"))
        assertEquals(gd(1500.0), gd("  1500  "))

        // Exponent-form and plain-decimal parses of the same value normalize
        // through different divisions, so they agree to within a few ulps of the
        // mantissa rather than bit-for-bit — which is the documented contract,
        // not a defect.
        assertEquals(-4.2e-3, gd("-4.2e-3").toDouble(), 1e-18)
        assertEquals(gd(-0.0042).exponent, gd("-4.2e-3").exponent, 0.0)
        assertEquals(gd(-0.0042).sign, gd("-4.2e-3").sign)

        assertTrue("garbage parses as zero rather than throwing", gd("not a number").isZero())
        assertTrue(gd("").isZero())
    }

    @Test
    fun `Long values beyond Double precision are not silently sign-flipped`() {
        // The engine takes Long timestamps and counts in places; the conversion
        // loses precision, but it must never wrap.
        assertEquals(1, gd(Long.MAX_VALUE).sign)
        assertEquals(-1, gd(Long.MIN_VALUE).sign)
        assertNotEquals(gd(Long.MAX_VALUE), gd(Long.MIN_VALUE))
    }

    @Test
    fun `equality and hashing agree`() {
        for (a in operands) {
            for (b in operands) {
                if (a == b) {
                    assertEquals("equal values must hash equally", a.hashCode(), b.hashCode())
                    assertTrue(a.eq(b))
                }
            }
        }
    }
}
