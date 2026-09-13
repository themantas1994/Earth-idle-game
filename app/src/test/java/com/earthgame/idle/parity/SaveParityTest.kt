package com.earthgame.idle.parity

import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.formatting.NumberFormatMode
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.SAVE_VERSION
import com.earthgame.idle.domain.model.ThemePreference
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.save.SaveSerialization
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The save format, checked against saves the reference implementation actually
 * wrote.
 *
 * Two things are being proved here. First, that a save written by the original
 * web build loads in this app — the field names, the `__decimal` tagging and
 * the migration chain all have to match. Second, that the v1 → v2 → v3
 * migrations produce the same corrected state the reference produces, so a
 * returning player's Earth Points and refunds come out the same.
 */
class SaveParityTest {

    private val fixture = Fixtures.obj("save")
    private val now = 1_700_000_000_000L

    @Test
    fun `a save written by the reference implementation loads`() {
        val state = SaveSerialization.deserialize(fixture.getString("v4Json"), now)
        assertNotNull("the reference's v4 save should load", state)
        state!!

        val expected = fixture.getValue("roundTripped").jsonObject
        // The reference implementation's chain ends at v4. This build has
        // versions it never had, so a reference save is migrated past the
        // version it was written at — every *value* below still has to match.
        assertEquals("saveVersion", SAVE_VERSION, state.saveVersion)
        assertTrue(
            "the fixture must still be the v4 save the reference wrote",
            expected.getInt("saveVersion") <= SAVE_VERSION,
        )

        for ((key, value) in expected.getValue("resources").jsonObject) {
            val resource = ResourceId.entries.first { it.id == key }
            assertDecimalNear("resources[$key]", value.asGameDecimal(), state.resources[resource], Fixtures.TOLERANCE_EXACT)
        }
        for ((key, value) in expected.getValue("atmosphere").jsonObject) {
            val gas = GasId.entries.first { it.id == key }
            assertDecimalNear("atmosphere[$key]", value.asGameDecimal(), state.atmosphere[gas], Fixtures.TOLERANCE_EXACT)
        }

        assertEquals(
            "techOwned",
            expected.getValue("techOwned").jsonObject.mapValues { it.value.jsonPrimitive.int },
            state.techOwned,
        )
        assertDecimalNear(
            "earthPoints",
            expected.getValue("earthPoints").asGameDecimal(),
            state.prestige.earthPoints,
            Fixtures.TOLERANCE_EXACT,
        )

        val settings = expected.getValue("settings").jsonObject
        assertEquals("numberFormat", settings.getString("numberFormat"), state.settings.numberFormat.id)
        assertEquals("darkMode", settings.getString("darkMode"), state.settings.darkMode.id)
        assertEquals("vibrationEnabled", settings.getBoolean("vibrationEnabled"), state.settings.vibrationEnabled)

        assertDoubleNear("seaLevelRiseMeters", expected.getDouble("seaLevelRiseMeters"), state.seaLevelRiseMeters)
        assertDoubleNear("gameAgeSeconds", expected.getDouble("gameAgeSeconds"), state.gameAgeSeconds)
        assertDoubleNear(
            "lifetime totalSimulatedSeconds",
            expected.getDouble("lifetimeTotalSimulatedSeconds"),
            state.lifetimeStats.totalSimulatedSeconds,
        )
        assertDoubleNear(
            "lifetime fastestResetSeconds",
            expected.getDouble("lifetimeFastestResetSeconds"),
            state.lifetimeStats.fastestResetSeconds!!,
        )

        // Structured collections survive the round trip too.
        assertEquals("active events", 1, state.activeEvents.size)
        assertEquals("good_harvest", state.activeEvents.first().eventDefId)
        assertEquals("news feed", listOf("co2_300", "first_smoke"), state.newsFeed.map { it.milestoneId })
        assertEquals("tutorial step", 5, state.tutorial.step)
    }

    @Test
    fun `the v1 migration grants Natural Fire and refunds the removed upgrade`() {
        val state = SaveSerialization.deserialize(fixture.getString("v1Json"), now)
        assertNotNull("a v1 save should still load", state)
        state!!

        val expected = fixture.getValue("migratedFromV1").jsonObject
        assertEquals("migrated to current version", SAVE_VERSION, state.saveVersion)

        // A v1 save can be missing the only unconditional source of Energy.
        assertEquals("Natural Fire granted", expected.getInt("naturalFireOwned"), state.techOwned["natural_fire"])
        assertTrue("Natural Fire must be owned", (state.techOwned["natural_fire"] ?: 0) >= 1)

        // Tap Conditioning no longer exists and its spend is refunded.
        assertNull("the removed upgrade should be gone", state.prestige.upgradesOwned["tap_conditioning"])
        assertEquals(
            "surviving upgrades",
            expected.getValue("upgradesOwned").jsonObject.mapValues { it.value.jsonPrimitive.int },
            state.prestige.upgradesOwned,
        )
        assertDecimalNear(
            "refunded and rescaled Earth Points",
            expected.getValue("earthPoints").asGameDecimal(),
            state.prestige.earthPoints,
        )
    }

    @Test
    fun `the v2 migration rescales banked Earth Points onto the new curve`() {
        val state = SaveSerialization.deserialize(fixture.getString("v2Json"), now)
        assertNotNull("a v2 save should still load", state)
        state!!

        val expected = fixture.getValue("migratedFromV2").jsonObject
        assertEquals("migrated version", SAVE_VERSION, state.saveVersion)
        assertDecimalNear(
            "rescaled Earth Points",
            expected.getValue("earthPoints").asGameDecimal(),
            state.prestige.earthPoints,
        )
        assertDecimalNear(
            "rescaled lifetime Earth Points",
            expected.getValue("totalEarthPointsEarned").asGameDecimal(),
            state.lifetimeStats.totalEarthPointsEarned,
        )
    }

    @Test
    fun `the v3 migration gives the Earth an age without inventing one`() {
        val state = SaveSerialization.deserialize(fixture.getString("v3Json"), now)
        assertNotNull("a v3 save should still load", state)
        state!!

        val expected = fixture.getValue("migratedFromV3").jsonObject
        assertEquals("migrated version", SAVE_VERSION, state.saveVersion)

        // The current Earth cannot be reconstructed, so it starts at zero
        // rather than being credited with centuries it never simulated.
        assertDoubleNear("gameAgeSeconds", expected.getDouble("gameAgeSeconds"), state.gameAgeSeconds)
        assertEquals("a migrated Earth starts at age zero", 0.0, state.gameAgeSeconds, 0.0)

        // The lifetime total is derived exactly from simulated play time.
        assertDoubleNear(
            "totalSimulatedSeconds",
            expected.getDouble("totalSimulatedSeconds"),
            state.lifetimeStats.totalSimulatedSeconds,
        )
        assertDoubleNear(
            "totalPlayTimeSeconds is untouched",
            expected.getDouble("totalPlayTimeSeconds"),
            state.lifetimeStats.totalPlayTimeSeconds,
        )

        // v3's economy fix-ups must not run a second time.
        assertDecimalNear(
            "Earth Points untouched",
            expected.getValue("earthPoints").asGameDecimal(),
            state.prestige.earthPoints,
        )
    }

    @Test
    fun `corrupted saves are rejected rather than loaded as garbage`() {
        for (corrupt in fixture.getValue("corrupt").jsonArray) {
            val text = corrupt.jsonPrimitive.content
            assertNull("should reject: $text", SaveSerialization.deserialize(text, now))
        }
    }

    @Test
    fun `a truncated save is rejected rather than throwing`() {
        val full = SaveSerialization.serialize(createNewGame(now))
        for (fraction in listOf(0.1, 0.5, 0.9, 0.99)) {
            val truncated = full.take((full.length * fraction).toInt())
            assertNull("truncated at $fraction should be rejected", SaveSerialization.deserialize(truncated, now))
        }
    }

    // --- Round-tripping this port's own saves ---

    @Test
    fun `a state round-trips through serialization unchanged`() {
        val original = createNewGame(now).let { base ->
            base.copy(
                runNumber = 7,
                techOwned = mapOf("natural_fire" to 42, "coal_mining" to 1337),
                resources = base.resources
                    .with(ResourceId.ENERGY, gd("9.87654321e123"))
                    .with(ResourceId.RESEARCH, gd("1e-7")),
                atmosphere = base.atmosphere.with(GasId.CO2, gd("412.5")),
                seaLevelRiseMeters = 3.25,
                temperatureAnomalyC = 4.5,
                oceanPh = 7.82,
                prestige = base.prestige.copy(
                    earthPoints = gd("1.2345e42"),
                    upgradesOwned = mapOf("atmospheric_momentum" to 9),
                ),
                achievementsUnlocked = mapOf("first_spark" to true, "hot" to true),
                settings = base.settings.copy(
                    numberFormat = NumberFormatMode.ENGINEERING,
                    darkMode = ThemePreference.LIGHT,
                    vibrationEnabled = false,
                    offlineProgressEnabled = false,
                ),
                collapsed = true,
            )
        }

        val revived = SaveSerialization.deserialize(SaveSerialization.serialize(original), now)
        assertNotNull(revived)
        assertEquals("a save must round-trip exactly", original, revived)
    }

    @Test
    fun `enormous values survive serialization without precision loss`() {
        // The whole reason Decimals are stored as a triple rather than a number.
        val huge = gd("9.87654321098765e2500")
        val state = createNewGame(now).let {
            it.copy(resources = it.resources.with(ResourceId.ENERGY, huge))
        }
        val revived = SaveSerialization.deserialize(SaveSerialization.serialize(state), now)!!
        val loaded = revived.resources[ResourceId.ENERGY]

        assertEquals("mantissa must survive exactly", huge.mantissa, loaded.mantissa, 0.0)
        assertEquals("exponent must survive exactly", huge.exponent, loaded.exponent, 0.0)
        assertTrue("and stay finite", loaded.isFinite())
    }

    @Test
    fun `an unfinished write is not mistaken for a valid save`() {
        val plausible = SaveSerialization.serialize(createNewGame(now))
        val root = Json.parseToJsonElement(plausible).jsonObject
        assertTrue("a real save is plausible", SaveSerialization.isPlausibleSave(root))

        val missingResources = JsonObject(root - "resources")
        assertFalse("a save missing resources is not", SaveSerialization.isPlausibleSave(missingResources))

        val missingPrestige = JsonObject(root - "prestige")
        assertFalse("a save missing prestige is not", SaveSerialization.isPlausibleSave(missingPrestige))
    }
}
