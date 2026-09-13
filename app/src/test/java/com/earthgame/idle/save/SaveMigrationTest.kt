package com.earthgame.idle.save

import com.earthgame.idle.domain.engine.GAME_SECONDS_PER_REAL_SECOND
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.PrestigeState
import com.earthgame.idle.domain.model.ResourceAmounts
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.resourceOf
import com.earthgame.idle.domain.model.SAVE_VERSION
import com.earthgame.idle.domain.model.createInitialRunStats
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.domain.save.SaveSerialization
import com.earthgame.idle.domain.save.migrate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The migration chain end to end, and the ways a damaged or foreign save can be
 * handed to it.
 *
 * `SaveParityTest` already checks each individual migration against the
 * *reference implementation's* recorded output. This checks the properties the
 * chain has to hold on its own: that it is idempotent, that running it on a
 * save from a newer build cannot corrupt it, and that a decode never lets an
 * impossible value into the engine.
 */
class SaveMigrationTest {

    private val now = 1_700_000_000_000L

    private fun stateAt(version: Int, transform: (GameState) -> GameState = { it }): GameState =
        transform(createNewGame(now).copy(saveVersion = version))

    // ------------------------------------------------------------ the chain --

    @Test
    fun `every released version reaches the current shape`() {
        for (version in 1..SAVE_VERSION) {
            val migrated = migrate(stateAt(version))
            assertEquals(
                "a v$version save must migrate to v$SAVE_VERSION",
                SAVE_VERSION,
                migrated.saveVersion,
            )
        }
    }

    @Test
    fun `migrating twice is the same as migrating once`() {
        // The chain runs on every load, and a save is written back at the
        // current version — but a migration that is not idempotent would still
        // double-apply the moment anything replays it.
        val v1 = stateAt(1) {
            it.copy(
                techOwned = emptyMap(),
                prestige = PrestigeState(
                    earthPoints = gd("1e9"),
                    upgradesOwned = mapOf("tap_conditioning" to 4),
                ),
            )
        }

        val once = migrate(v1)
        val twice = migrate(once)

        assertEquals(once, twice)
    }

    @Test
    fun `the v1 chain runs both steps, not just the first`() {
        val v1 = stateAt(1) {
            it.copy(prestige = PrestigeState(earthPoints = gd(250_000.0 * 8)))
        }

        val migrated = migrate(v1)

        assertEquals(SAVE_VERSION, migrated.saveVersion)
        assertTrue("Natural Fire must be granted by v1→v2", (migrated.techOwned["natural_fire"] ?: 0) >= 1)
        assertEquals(
            "and the v2→v3 rescale must also have run",
            8.0,
            migrated.prestige.earthPoints.toDouble(),
            1e-6,
        )
    }

    @Test
    fun `the v3 migration starts the Earth at age zero rather than inventing one`() {
        // A v3 save knows when the run started in wall-clock terms but not how
        // much of that was simulated — an absence past the offline cap banks
        // only the cap, and a player with offline progress off banks none of
        // it. Crediting the difference would retroactively decay an atmosphere
        // through centuries the planet never lived.
        val v3 = stateAt(3) {
            it.copy(
                runStartedAt = now - 30L * 24 * 3600 * 1000,
                lastTickAt = now,
                lifetimeStats = it.lifetimeStats.copy(totalPlayTimeSeconds = 7200.0),
            )
        }

        val migrated = migrate(v3)

        assertEquals("a migrated Earth starts at age zero", 0.0, migrated.gameAgeSeconds, 0.0)
        assertEquals(
            "the lifetime total is derived exactly from simulated play time",
            7200.0 * GAME_SECONDS_PER_REAL_SECOND,
            migrated.lifetimeStats.totalSimulatedSeconds,
            1e-6,
        )
        assertEquals(
            "and play time itself is untouched",
            7200.0,
            migrated.lifetimeStats.totalPlayTimeSeconds,
            0.0,
        )
    }

    @Test
    fun `a v3 save with no play time gets no simulated time either`() {
        val migrated = migrate(stateAt(3))
        assertEquals(0.0, migrated.gameAgeSeconds, 0.0)
        assertEquals(0.0, migrated.lifetimeStats.totalSimulatedSeconds, 0.0)
    }

    @Test
    fun `a v3 save loads and keeps playing`() {
        // The whole point of the migration: an existing player's save opens.
        // `stateAt` stamps the version, and the encoder writes whatever the
        // state carries, so this really is a v3 document on disk.
        val v3Json = SaveSerialization.serialize(stateAt(3) { it.copy(techOwned = mapOf("natural_fire" to 4)) })
        assertTrue("the fixture must actually be v3", v3Json.contains("\"saveVersion\":3"))

        val loaded = SaveSerialization.deserialize(v3Json, now)

        assertNotNull("a v3 save must still load", loaded)
        assertEquals(SAVE_VERSION, loaded!!.saveVersion)
        assertEquals(4, loaded.techOwned["natural_fire"])
    }

    @Test
    fun `Metals is persisted under the steel id so existing balances survive the rename`() {
        // The player-facing name changed; the serialized key deliberately did
        // not, because every save ever written contains it and a cosmetic
        // rename must not cost anyone their balance.
        assertEquals("steel", ResourceId.STEEL.id)
        assertEquals("Metals", resourceOf(ResourceId.STEEL).displayName)

        val withBalance = createNewGame(now).let {
            it.copy(resources = it.resources.with(ResourceId.STEEL, gd("1.5e9")))
        }
        val json = SaveSerialization.serialize(withBalance)
        assertTrue("the save must still key it as steel", json.contains("\"steel\""))
        assertFalse("and must not have grown a metals key", json.contains("\"metals\""))

        val loaded = SaveSerialization.deserialize(json, now)
        assertNotNull(loaded)
        assertEquals(
            "1.5e9",
            gd("1.5e9").toDouble(),
            loaded!!.resources[ResourceId.STEEL].toDouble(),
            0.0,
        )
    }

    // ------------------------------------------------ saves from the future --

    @Test
    fun `a save from a newer build keeps its own version`() {
        // Stamping it down to the current version means a later upgrade back to
        // that build re-runs migrations the save has already been through.
        val future = stateAt(SAVE_VERSION + 2)

        assertEquals(SAVE_VERSION + 2, migrate(future).saveVersion)
    }

    @Test
    fun `a future save is not rescaled a second time by the v3 migration`() {
        val banked = gd("1e6")
        val future = stateAt(SAVE_VERSION + 1) { it.copy(prestige = PrestigeState(earthPoints = banked)) }

        assertEquals(
            "banked points must survive a downgrade untouched",
            banked.toDouble(),
            migrate(future).prestige.earthPoints.toDouble(),
            0.0,
        )
    }

    // ------------------------------------------------- hostile save content --

    private fun decodeOrNull(json: String): GameState? = SaveSerialization.deserialize(json, now)

    private fun serialized(state: GameState): String = SaveSerialization.serialize(state)

    @Test
    fun `an infinite value in a save is rejected rather than poisoning the run`() {
        // JSON has no Infinity literal, but 1e999 is a legal token that parses
        // to one — and a single infinite temperature turns every later value
        // into NaN for the rest of the run.
        val damaged = serialized(createNewGame(now))
            .replace("\"temperatureAnomalyC\":0.0", "\"temperatureAnomalyC\":1e999")

        val loaded = decodeOrNull(damaged)

        assertNotNull("the save is still structurally valid and must load", loaded)
        assertTrue(
            "the impossible value must not survive",
            loaded!!.temperatureAnomalyC.isFinite(),
        )
    }

    @Test
    fun `an infinite GameDecimal triple decodes as zero`() {
        val damaged = serialized(createNewGame(now).copy(prestige = PrestigeState(earthPoints = gd(500.0))))
            .replace("\"__decimal\":[1,5.0,2.0]", "\"__decimal\":[1,1e999,1e999]")

        val loaded = decodeOrNull(damaged)

        assertNotNull(loaded)
        assertTrue("no infinite balance may reach the engine", loaded!!.prestige.earthPoints.isFinite())
    }

    @Test
    fun `a negative owned count is dropped rather than fed to the cost curves`() {
        val damaged = serialized(createNewGame(now))
            .replace("\"techOwned\":{\"natural_fire\":1}", "\"techOwned\":{\"natural_fire\":1,\"coal_mining\":-5}")

        val loaded = decodeOrNull(damaged)

        assertNotNull(loaded)
        assertNull("a negative quantity is not a quantity", loaded!!.techOwned["coal_mining"])
        assertEquals(1, loaded.techOwned["natural_fire"])
    }

    @Test
    fun `a save with no recognisable structure is refused so the backup can be tried`() {
        assertNull(decodeOrNull("{}"))
        assertNull(decodeOrNull("[]"))
        assertNull(decodeOrNull("not json at all"))
        assertNull(decodeOrNull(""))
        assertNull(
            "a version that is not a number is not a save",
            decodeOrNull("""{"saveVersion":"three","runNumber":0,"resources":{},"atmosphere":{},"techOwned":{},"prestige":{}}"""),
        )
    }

    @Test
    fun `a save missing every optional field still loads at its defaults`() {
        val minimal = """
            {"saveVersion":3,"runNumber":0,"resources":{},"atmosphere":{},
             "techOwned":{"natural_fire":1},"prestige":{}}
        """.trimIndent()

        val loaded = decodeOrNull(minimal)

        assertNotNull(loaded)
        assertEquals(ResourceAmounts.ZERO, loaded!!.resources)
        assertEquals(GameDecimal.ZERO, loaded.prestige.earthPoints)
        assertEquals("the fallback clock is used for a missing timestamp", now, loaded.lastTickAt)
        assertFalse(loaded.collapsed)
        assertTrue("settings fall back to the defaults", loaded.settings.offlineProgressEnabled)
    }

    @Test
    fun `unknown fields from a newer build are ignored rather than failing the load`() {
        val withExtras = serialized(createNewGame(now))
            .replaceFirst("{", """{"someFutureField":{"nested":[1,2,3]},""")

        assertNotNull(decodeOrNull(withExtras))
    }

    @Test
    fun `a full state survives the encode-migrate-decode round trip`() {
        val rich = createNewGame(now).copy(
            runNumber = 7,
            techOwned = mapOf("natural_fire" to 40, "coal_mining" to 123),
            resources = ResourceAmounts.ZERO.with(ResourceId.ENERGY, gd("1.2345678901234e900")),
            prestige = PrestigeState(gd("9.87654321e42"), mapOf("atmospheric_momentum" to 12)),
            runStats = createInitialRunStats(now).copy(peakTemperatureC = 612.5),
            achievementsUnlocked = mapOf("oops" to true),
        )

        val loaded = decodeOrNull(serialized(rich))

        assertEquals(rich, loaded)
    }
}
