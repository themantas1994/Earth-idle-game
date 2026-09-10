package com.earthgame.idle.parity

import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.formatting.NumberFormatMode
import com.earthgame.idle.domain.formatting.formatDuration
import com.earthgame.idle.domain.formatting.formatNumber
import com.earthgame.idle.domain.formatting.formatPercent
import com.earthgame.idle.domain.formatting.formatTemperature
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Formatting is compared as *strings*, not numbers: the player reads these, and
 * a port that rounds "1.005" the other way or drops a thousands separator is
 * visibly different even though the arithmetic underneath is identical.
 *
 * This is also where the port's `jsToFixed` earns its keep — ECMAScript's
 * `toFixed` rounds the exact binary value half-away-from-zero, which is not
 * what a naive `String.format` gives you for every input.
 */
class FormattingParityTest {

    @Test
    fun `every notation mode matches the reference for every magnitude`() {
        var checked = 0
        for (case in Fixtures.obj("format").getValue("numbers").jsonArray) {
            val o = case.jsonObject
            val value = o.getString("value")
            val mode = NumberFormatMode.fromId(o.getString("mode"))
            val precision = o.getInt("precision")
            assertEquals(
                "formatNumber($value, $mode, precision=$precision)",
                o.getString("out"),
                formatNumber(gd(value), mode, precision),
            )
            checked++
        }
        assertEquals("expected every value x mode x precision combination", 36 * 4 * 2, checked)
    }

    @Test
    fun `durations match the reference`() {
        for (case in Fixtures.obj("format").getValue("durations").jsonArray) {
            val o = case.jsonObject
            val seconds = o.getDouble("seconds")
            assertEquals("formatDuration($seconds)", o.getString("out"), formatDuration(seconds))
        }
    }

    @Test
    fun `temperatures always carry a sign`() {
        for (case in Fixtures.obj("format").getValue("temperatures").jsonArray) {
            val o = case.jsonObject
            val celsius = o.getDouble("celsius")
            assertEquals("formatTemperature($celsius)", o.getString("out2"), formatTemperature(celsius))
            assertEquals("formatTemperature($celsius, 0)", o.getString("out0"), formatTemperature(celsius, 0))
        }
    }

    @Test
    fun `percentages match the reference`() {
        for (case in Fixtures.obj("format").getValue("percents").jsonArray) {
            val o = case.jsonObject
            val fraction = o.getDouble("fraction")
            assertEquals("formatPercent($fraction)", o.getString("out1"), formatPercent(fraction))
            assertEquals("formatPercent($fraction, 3)", o.getString("out3"), formatPercent(fraction, 3))
        }
    }

    @Test
    fun `compact suffixes keep going past the named list`() {
        // 1e63 is the last named suffix ("Vg"); beyond it the alphabetic
        // fallback has to produce something, forever, rather than crash or
        // print an empty suffix.
        assertEquals("1CA", formatNumber(gd("1e300"), NumberFormatMode.COMPACT))
        assertEquals("1AKQ", formatNumber(gd("1e3000"), NumberFormatMode.COMPACT))
    }
}
