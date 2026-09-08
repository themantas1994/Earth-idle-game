package com.earthgame.idle.parity

import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.events.RANDOM_EVENT_BY_ID
import com.earthgame.idle.domain.events.applyInstantGasBurst
import com.earthgame.idle.domain.events.computeActiveEventMultipliers
import com.earthgame.idle.domain.events.removeExpiredEvents
import com.earthgame.idle.domain.events.rollRandomEvent
import com.earthgame.idle.domain.model.ActiveEvent
import com.earthgame.idle.domain.model.GasAmounts
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.technologies.TechBranch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Random events, checked for the property the brief asks for: given the same
 * deterministic draw, the same event is chosen.
 *
 * The engine never draws its own random number — the caller supplies it — which
 * is what makes event selection reproducible here and identical to the
 * reference for every eligibility state and every draw in [0,1).
 */
class EventsParityTest {

    private val fixture = Fixtures.obj("events")

    @Test
    fun `a given draw picks the same event as the reference`() {
        var checked = 0
        for (case in fixture.getValue("rolls").jsonArray) {
            val o = case.jsonObject
            val civLevel = o.getInt("civLevel")
            val active = o.getValue("active").jsonArray.map { it.jsonPrimitive.content }.toSet()
            val random = o.getDouble("random")
            val expected = o.getValue("picked")

            val picked = rollRandomEvent(civLevel, active, random)
            if (expected is JsonNull) {
                assertNull("civ=$civLevel active=$active r=$random should pick nothing", picked)
            } else {
                assertEquals(
                    "civ=$civLevel active=$active r=$random",
                    expected.jsonPrimitive.content,
                    picked?.id,
                )
            }
            checked++
        }
        assertTrue("expected the full roll matrix, got $checked", checked > 200)
    }

    @Test
    fun `active event multipliers compose the same way`() {
        for (case in fixture.getValue("multipliers").jsonArray) {
            val o = case.jsonObject
            val nowMs = o.getValue("nowMs").jsonPrimitive.long
            val events = o.getValue("events").jsonArray.map {
                val e = it.jsonObject
                ActiveEvent(
                    id = e.getString("id"),
                    eventDefId = e.getString("eventDefId"),
                    startedAt = e.getValue("startedAt").jsonPrimitive.long,
                    endsAt = e.getValue("endsAt").jsonPrimitive.long,
                )
            }
            val label = "events=${events.map { it.eventDefId }}"
            val result = computeActiveEventMultipliers(events, nowMs)
            val expected = o.getValue("result").jsonObject

            assertNullableDouble("$label global", expected["global"], result.global)
            assertNullableDouble("$label research", expected["research"], result.research)
            assertNullableDouble("$label allGas", expected["allGas"], result.allGas)

            val expectedPerGas = expected["perGas"]
            if (expectedPerGas == null || expectedPerGas is JsonNull) {
                assertTrue("$label perGas should be empty", result.perGas.isEmpty())
            } else {
                for ((key, value) in expectedPerGas.jsonObject) {
                    val gasId = GasId.entries.first { it.id == key }
                    assertDoubleNear("$label perGas[$key]", value.jsonPrimitive.double, result.perGas.getValue(gasId))
                }
            }

            val expectedPerBranch = expected["perBranch"]
            if (expectedPerBranch == null || expectedPerBranch is JsonNull) {
                assertTrue("$label perBranch should be empty", result.perBranch.isEmpty())
            } else {
                for ((key, value) in expectedPerBranch.jsonObject) {
                    val branch = TechBranch.entries.first { it.id == key }
                    assertDoubleNear("$label perBranch[$key]", value.jsonPrimitive.double, result.perBranch.getValue(branch))
                }
            }
        }
    }

    private fun assertNullableDouble(message: String, expected: kotlinx.serialization.json.JsonElement?, actual: Double?) {
        if (expected == null || expected is JsonNull) {
            assertNull("$message should be unset but was $actual", actual)
        } else {
            assertTrue("$message should be set", actual != null)
            assertDoubleNear(message, expected.jsonPrimitive.double, actual!!)
        }
    }

    @Test
    fun `instant gas bursts land the same mass in the atmosphere`() {
        for (case in fixture.getValue("bursts").jsonArray) {
            val o = case.jsonObject
            val definition = RANDOM_EVENT_BY_ID.getValue(o.getString("eventId"))
            val before = GasAmounts.build { builder ->
                for (gas in GasId.entries) builder[gas] = gd("7")
            }
            val after = applyInstantGasBurst(before, definition)
            val expected = o.getValue("after").jsonObject
            for ((key, value) in expected) {
                val gasId = GasId.entries.first { it.id == key }
                assertDecimalNear("${definition.id} burst -> $key", value.asGameDecimal(), after[gasId])
            }
        }
    }

    @Test
    fun `expired events stop contributing and get pruned`() {
        val now = 1_000L
        val events = listOf(
            ActiveEvent("live", "good_harvest", 0, now + 1),
            ActiveEvent("expired", "industrial_boom", 0, now),
        )
        val multipliers = computeActiveEventMultipliers(events, now)
        assertEquals("only the live event should count", 3.0, multipliers.global!!, 1e-12)

        val pruned = removeExpiredEvents(events, now)
        assertEquals(listOf("live"), pruned.map { it.id })
    }

    @Test
    fun `an event already running is not rolled again`() {
        // Otherwise a lucky streak could stack the same bonus with itself.
        val all = RANDOM_EVENT_BY_ID.keys
        val picked = rollRandomEvent(100, all, 0.5)
        assertNull("nothing should be eligible when everything is active", picked)
    }
}
