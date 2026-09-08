package com.earthgame.idle.parity

import com.earthgame.idle.domain.engine.computeOfflineProgress
import com.earthgame.idle.domain.engine.offlineCapSeconds
import com.earthgame.idle.domain.engine.simulateStep
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.prestige.PrestigeMultipliers
import com.earthgame.idle.domain.prestige.computePrestigeMultipliers
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline catch-up: the cap, what happens when it is exceeded, what the
 * "welcome back" summary reports, and the settings toggle — all against the
 * reference, for both an un-upgraded player and one with the offline-cap
 * upgrades maxed.
 */
class OfflineParityTest {

    private val noPrestige = PrestigeMultipliers.NONE
    private val extended = computePrestigeMultipliers(
        mapOf("extended_endurance" to 3, "automated_industry" to 1),
    )

    private fun baseState() = createNewGame(0).copy(
        techOwned = mapOf("natural_fire" to 8, "controlled_fire" to 20, "coal_mining" to 15),
        lastTickAt = 0,
    )

    @Test
    fun `offline progress matches the reference across absences and settings`() {
        var checked = 0
        for (case in Fixtures.obj("offline").getValue("cases").jsonArray) {
            val o = case.jsonObject
            val prestige = if (o.getString("prestige") == "extended") extended else noPrestige
            val awaySeconds = o.getDouble("awaySeconds")
            val enabled = o.getBoolean("enabled")
            val label = "away=${awaySeconds}s prestige=${o.getString("prestige")} enabled=$enabled"

            val result = computeOfflineProgress(baseState(), (awaySeconds * 1000).toLong(), prestige, enabled)

            assertDoubleNear("$label simulatedSeconds", o.getDouble("simulatedSeconds"), result.simulatedSeconds)
            assertEquals("$label cappedByLimit", o.getBoolean("cappedByLimit"), result.cappedByLimit)
            assertDoubleNear("$label offlineCapSeconds", o.getDouble("offlineCapSeconds"), result.offlineCapSeconds)
            assertEquals("$label lastTickAt", o.getDouble("lastTickAt").toLong(), result.state.lastTickAt)

            for ((key, value) in o.getValue("resources").jsonObject) {
                val resource = ResourceId.entries.first { it.id == key }
                assertDecimalNear("$label resources[$key]", value.asGameDecimal(), result.state.resources[resource])
            }
            for ((key, value) in o.getValue("atmosphere").jsonObject) {
                val gas = GasId.entries.first { it.id == key }
                assertDecimalNear("$label atmosphere[$key]", value.asGameDecimal(), result.state.atmosphere[gas])
            }
            assertDoubleNear("$label temperature", o.getDouble("temperatureAnomalyC"), result.state.temperatureAnomalyC)

            for ((key, value) in o.getValue("gasGeneratedKg").jsonObject) {
                val gas = GasId.entries.first { it.id == key }
                assertDecimalNear("$label summary.gas[$key]", value.asGameDecimal(), result.summary.gasGeneratedKg[gas])
            }
            for ((key, value) in o.getValue("resourcesGained").jsonObject) {
                val resource = ResourceId.entries.first { it.id == key }
                assertDecimalNear("$label summary.resources[$key]", value.asGameDecimal(), result.summary.resourcesGained[resource])
            }
            assertDoubleNear("$label summary.tempBefore", o.getDouble("temperatureBeforeC"), result.summary.temperatureBeforeC)
            assertDoubleNear("$label summary.tempAfter", o.getDouble("temperatureAfterC"), result.summary.temperatureAfterC)
            checked++
        }
        assertTrue("expected the full offline matrix, got $checked", checked >= 32)
    }

    @Test
    fun `the offline cap is twelve hours, extended by upgrades`() {
        assertEquals(12.0 * 3600, offlineCapSeconds(noPrestige), 0.0)
        // extended_endurance x3 (x2 each) and automated_industry (x2) => x16
        assertEquals(12.0 * 3600 * 16, offlineCapSeconds(extended), 0.0)
    }

    @Test
    fun `an absence longer than the cap banks exactly the cap`() {
        val week = computeOfflineProgress(baseState(), 604_800_000L, noPrestige, true)
        val capped = computeOfflineProgress(baseState(), (12 * 3600 * 1000).toLong(), noPrestige, true)

        assertTrue("a week away should report being capped", week.cappedByLimit)
        assertEquals("...and should bank exactly the cap", 12.0 * 3600, week.simulatedSeconds, 0.0)
        for (resource in ResourceId.entries) {
            assertDecimalNear(
                "a week away earns the same as exactly the cap for $resource",
                capped.state.resources[resource],
                week.state.resources[resource],
            )
        }
    }

    @Test
    fun `the clock always advances, even when nothing is simulated`() {
        // Otherwise a player with offline progress disabled would bank the
        // entire absence the moment they re-enabled it.
        val disabled = computeOfflineProgress(baseState(), 999_999_000L, noPrestige, false)
        assertEquals(999_999_000L, disabled.state.lastTickAt)
        assertEquals(0.0, disabled.simulatedSeconds, 0.0)
        assertEquals(
            "no resources should accrue while disabled",
            baseState().resources,
            disabled.state.resources,
        )
    }

    @Test
    fun `offline catch-up equals having played the same time live`() {
        val base = baseState()
        val offline = computeOfflineProgress(base, 4 * 3600 * 1000L, noPrestige, true)
        val live = simulateStep(base, 4.0 * 3600, noPrestige).state

        for (resource in ResourceId.entries) {
            assertDecimalNear(
                "offline vs live for $resource",
                live.resources[resource],
                offline.state.resources[resource],
                Fixtures.TOLERANCE_EXACT,
            )
        }
        for (gas in GasId.entries) {
            assertDecimalNear(
                "offline vs live for $gas",
                live.atmosphere[gas],
                offline.state.atmosphere[gas],
                Fixtures.TOLERANCE_EXACT,
            )
        }
    }
}
