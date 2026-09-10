package com.earthgame.idle.domain.save

import com.earthgame.idle.domain.climate.ForcingBreakdown
import com.earthgame.idle.domain.climate.HabitabilityFactors
import com.earthgame.idle.domain.climate.HabitabilityResult
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.formatting.NumberFormatMode
import com.earthgame.idle.domain.model.ActiveEvent
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.GasAmounts
import com.earthgame.idle.domain.model.GasDoubles
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.LifetimeStats
import com.earthgame.idle.domain.model.NewsItem
import com.earthgame.idle.domain.model.PrestigeState
import com.earthgame.idle.domain.model.ChallengeState
import com.earthgame.idle.domain.model.ResourceAmounts
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.RunStats
import com.earthgame.idle.domain.model.SAVE_VERSION
import com.earthgame.idle.domain.model.Settings
import com.earthgame.idle.domain.model.ThemePreference
import com.earthgame.idle.domain.model.TutorialState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Versioned JSON serialization for [GameState].
 *
 * ## Why the format is what it is
 *
 * Every [GameDecimal] is written as a tagged triple, `{"__decimal": [sign,
 * mantissa, exponent]}`, rather than as a number or a string. Writing it as a
 * JSON number would round-trip through a Double and cap the save at 1.8e308,
 * losing a finished run's totals outright; writing it as a formatted string
 * would lose mantissa precision. The triple is the value's exact internal
 * representation, so a save round-trips bit-for-bit.
 *
 * That tag is also the shape the original web build wrote, and every field name
 * here matches it, so a save exported from that build loads in this app and
 * migrates forward through the same chain.
 *
 * ## Robustness
 *
 * Loading never throws. A field that is missing, of the wrong type, or
 * nonsensical falls back to its default rather than failing the whole load, so
 * a partially-damaged save costs the player one value rather than the run.
 * A save that fails [isPlausibleSave] outright — the structural check — is
 * rejected so the caller can fall back to the backup slot.
 */
object SaveSerialization {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val DECIMAL_TAG = "__decimal"

    // ------------------------------------------------------------- encoding --

    private fun encodeDecimal(value: GameDecimal): JsonObject = buildJsonObject {
        put(
            DECIMAL_TAG,
            buildJsonArray {
                add(JsonPrimitive(value.sign))
                // Infinity has no JSON representation; a saved value can only
                // reach it through a division by zero the engine already
                // guards, so it is stored as the largest finite magnitude
                // rather than as null, which would revive as zero.
                add(JsonPrimitive(if (value.mantissa.isFinite()) value.mantissa else Double.MAX_VALUE))
                add(JsonPrimitive(if (value.exponent.isFinite()) value.exponent else Double.MAX_VALUE))
            },
        )
    }

    private fun encodeGasAmounts(amounts: GasAmounts): JsonObject = buildJsonObject {
        for (gas in GasId.entries) put(gas.id, encodeDecimal(amounts[gas]))
    }

    private fun encodeResourceAmounts(amounts: ResourceAmounts): JsonObject = buildJsonObject {
        for (resource in ResourceId.entries) put(resource.id, encodeDecimal(amounts[resource]))
    }

    private fun encodeGasDoubles(values: GasDoubles): JsonObject = buildJsonObject {
        for (gas in GasId.entries) put(gas.id, JsonPrimitive(values[gas]))
    }

    private fun encodeIntMap(map: Map<String, Int>): JsonObject = buildJsonObject {
        for ((key, value) in map) put(key, JsonPrimitive(value))
    }

    private fun encodeBoolMap(map: Map<String, Boolean>): JsonObject = buildJsonObject {
        for ((key, value) in map) put(key, JsonPrimitive(value))
    }

    fun encode(state: GameState): JsonObject = buildJsonObject {
        put("saveVersion", JsonPrimitive(state.saveVersion))
        put("runNumber", JsonPrimitive(state.runNumber))
        put("runStartedAt", JsonPrimitive(state.runStartedAt))
        put("lastTickAt", JsonPrimitive(state.lastTickAt))
        put("createdAt", JsonPrimitive(state.createdAt))

        put("resources", encodeResourceAmounts(state.resources))
        put("atmosphere", encodeGasAmounts(state.atmosphere))
        put("seaLevelRiseMeters", JsonPrimitive(state.seaLevelRiseMeters))
        put("previousTemperatureAnomalyC", JsonPrimitive(state.previousTemperatureAnomalyC))

        put("techOwned", encodeIntMap(state.techOwned))

        put("temperatureAnomalyC", JsonPrimitive(state.temperatureAnomalyC))
        put(
            "forcing",
            buildJsonObject {
                put("perGas", encodeGasDoubles(state.forcing.perGas))
                put("total", JsonPrimitive(state.forcing.total))
            },
        )
        put(
            "habitability",
            buildJsonObject {
                put("fraction", JsonPrimitive(state.habitability.fraction))
                put(
                    "factors",
                    buildJsonObject {
                        put("temperature", JsonPrimitive(state.habitability.factors.temperature))
                        put("oceanAcidity", JsonPrimitive(state.habitability.factors.oceanAcidity))
                        put("seaLevel", JsonPrimitive(state.habitability.factors.seaLevel))
                        put("agriculture", JsonPrimitive(state.habitability.factors.agriculture))
                        put("biodiversity", JsonPrimitive(state.habitability.factors.biodiversity))
                    },
                )
            },
        )
        put("oceanPh", JsonPrimitive(state.oceanPh))

        put(
            "runStats",
            buildJsonObject {
                put("startedAt", JsonPrimitive(state.runStats.startedAt))
                put("totalGasProducedKg", encodeGasAmounts(state.runStats.totalGasProducedKg))
                put("peakForcingWm2", JsonPrimitive(state.runStats.peakForcingWm2))
                put("peakTemperatureC", JsonPrimitive(state.runStats.peakTemperatureC))
                put("peakCo2Ppm", JsonPrimitive(state.runStats.peakCo2Ppm))
                put("peakGasProductionRateKgPerS", encodeDecimal(state.runStats.peakGasProductionRateKgPerS))
            },
        )

        put(
            "lifetimeStats",
            buildJsonObject {
                put("totalPlayTimeSeconds", JsonPrimitive(state.lifetimeStats.totalPlayTimeSeconds))
                put("totalResets", JsonPrimitive(state.lifetimeStats.totalResets))
                put("totalGasProducedKg", encodeGasAmounts(state.lifetimeStats.totalGasProducedKg))
                put("highestTemperatureC", JsonPrimitive(state.lifetimeStats.highestTemperatureC))
                put("highestCo2Ppm", JsonPrimitive(state.lifetimeStats.highestCo2Ppm))
                put(
                    "fastestResetSeconds",
                    state.lifetimeStats.fastestResetSeconds?.let { JsonPrimitive(it) } ?: JsonPrimitive(null as String?),
                )
                put("longestRunSeconds", JsonPrimitive(state.lifetimeStats.longestRunSeconds))
                put("totalTechnologiesPurchased", JsonPrimitive(state.lifetimeStats.totalTechnologiesPurchased))
                put("totalEarthPointsEarned", encodeDecimal(state.lifetimeStats.totalEarthPointsEarned))
            },
        )

        put(
            "prestige",
            buildJsonObject {
                put("earthPoints", encodeDecimal(state.prestige.earthPoints))
                put("upgradesOwned", encodeIntMap(state.prestige.upgradesOwned))
            },
        )

        put("achievementsUnlocked", encodeBoolMap(state.achievementsUnlocked))
        put(
            "challenges",
            buildJsonObject {
                put(
                    "activeId",
                    state.challenges.activeId?.let { JsonPrimitive(it) } ?: JsonPrimitive(null as String?),
                )
                put("completed", encodeBoolMap(state.challenges.completed))
            },
        )

        put(
            "activeEvents",
            buildJsonArray {
                for (event in state.activeEvents) {
                    add(
                        buildJsonObject {
                            put("id", JsonPrimitive(event.id))
                            put("eventDefId", JsonPrimitive(event.eventDefId))
                            put("startedAt", JsonPrimitive(event.startedAt))
                            put("endsAt", JsonPrimitive(event.endsAt))
                        },
                    )
                }
            },
        )

        put("milestonesTriggered", encodeBoolMap(state.milestonesTriggered))
        put(
            "newsFeed",
            buildJsonArray {
                for (item in state.newsFeed) {
                    add(
                        buildJsonObject {
                            put("milestoneId", JsonPrimitive(item.milestoneId))
                            put("at", JsonPrimitive(item.at))
                            put("runSeconds", JsonPrimitive(item.runSeconds))
                        },
                    )
                }
            },
        )

        put(
            "settings",
            buildJsonObject {
                put("numberFormat", JsonPrimitive(state.settings.numberFormat.id))
                put("soundEnabled", JsonPrimitive(state.settings.soundEnabled))
                put("musicEnabled", JsonPrimitive(state.settings.musicEnabled))
                put("vibrationEnabled", JsonPrimitive(state.settings.vibrationEnabled))
                put("reducedAnimations", JsonPrimitive(state.settings.reducedAnimations))
                put("darkMode", JsonPrimitive(state.settings.darkMode.id))
                put("confirmReset", JsonPrimitive(state.settings.confirmReset))
                put("offlineProgressEnabled", JsonPrimitive(state.settings.offlineProgressEnabled))
            },
        )

        put(
            "tutorial",
            buildJsonObject {
                put("step", JsonPrimitive(state.tutorial.step))
                put("completed", JsonPrimitive(state.tutorial.completed))
                put("skipped", JsonPrimitive(state.tutorial.skipped))
            },
        )

        put("collapsed", JsonPrimitive(state.collapsed))
    }

    fun serialize(state: GameState): String = json.encodeToString(JsonObject.serializer(), encode(state))

    // ------------------------------------------------------------- decoding --

    private fun JsonObject.obj(key: String): JsonObject? = (this[key] as? JsonObject)
    private fun JsonObject.arr(key: String): JsonArray? = (this[key] as? JsonArray)

    private fun JsonElement?.asDoubleOr(default: Double): Double =
        (this as? JsonPrimitive)?.takeIf { it.isString.not() && it.content != "null" }
            ?.content?.toDoubleOrNull() ?: default

    private fun JsonElement?.asDoubleOrNull(): Double? =
        (this as? JsonPrimitive)?.content?.toDoubleOrNull()

    private fun JsonElement?.asLongOr(default: Long): Long =
        (this as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() ?: default

    private fun JsonElement?.asIntOr(default: Int): Int =
        (this as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: default

    private fun JsonElement?.asBoolOr(default: Boolean): Boolean =
        (this as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: default

    private fun JsonElement?.asStringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun decodeDecimal(element: JsonElement?): GameDecimal {
        val triple = (element as? JsonObject)?.get(DECIMAL_TAG) as? JsonArray ?: return GameDecimal.ZERO
        if (triple.size < 3) return GameDecimal.ZERO
        val sign = triple[0].asIntOr(0)
        val mantissa = triple[1].asDoubleOr(0.0)
        val exponent = triple[2].asDoubleOr(0.0)
        if (sign == 0 || mantissa == 0.0) return GameDecimal.ZERO
        return GameDecimal.fromTriple(sign, mantissa, exponent)
    }

    private fun decodeGasAmounts(element: JsonElement?): GasAmounts {
        val o = element as? JsonObject ?: return GasAmounts.ZERO
        return GasAmounts.build { builder ->
            for (gas in GasId.entries) builder[gas] = decodeDecimal(o[gas.id])
        }
    }

    private fun decodeResourceAmounts(element: JsonElement?): ResourceAmounts {
        val o = element as? JsonObject ?: return ResourceAmounts.ZERO
        return ResourceAmounts.build { builder ->
            for (resource in ResourceId.entries) builder[resource] = decodeDecimal(o[resource.id])
        }
    }

    private fun decodeGasDoubles(element: JsonElement?): GasDoubles {
        val o = element as? JsonObject ?: return GasDoubles.ZERO
        return GasDoubles.build { builder ->
            for (gas in GasId.entries) builder[gas] = o[gas.id].asDoubleOr(0.0)
        }
    }

    private fun decodeIntMap(element: JsonElement?): Map<String, Int> {
        val o = element as? JsonObject ?: return emptyMap()
        return o.mapNotNull { (key, value) ->
            val count = (value as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
            key to count
        }.toMap()
    }

    private fun decodeBoolMap(element: JsonElement?): Map<String, Boolean> {
        val o = element as? JsonObject ?: return emptyMap()
        return o.mapNotNull { (key, value) ->
            val flag = (value as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: return@mapNotNull null
            if (flag) key to true else null
        }.toMap()
    }

    /**
     * Structural sanity check run on every load. A save that fails this — from
     * disk corruption, a botched manual edit, or an incompatible future format
     * — is rejected rather than crashing the game or silently loading garbage,
     * so the caller can fall back to the backup slot.
     */
    fun isPlausibleSave(root: JsonObject): Boolean {
        val version = root["saveVersion"] as? JsonPrimitive ?: return false
        if (version.isString) return false
        if (version.content.toIntOrNull() == null) return false
        if (root["runNumber"] !is JsonPrimitive) return false
        if (root["resources"] !is JsonObject) return false
        if (root["atmosphere"] !is JsonObject) return false
        if (root["techOwned"] !is JsonObject) return false
        if (root["prestige"] !is JsonObject) return false
        return true
    }

    fun decode(root: JsonObject, fallbackNow: Long): GameState {
        val runStatsJson = root.obj("runStats")
        val lifetimeJson = root.obj("lifetimeStats")
        val prestigeJson = root.obj("prestige")
        val challengesJson = root.obj("challenges")
        val settingsJson = root.obj("settings")
        val tutorialJson = root.obj("tutorial")
        val forcingJson = root.obj("forcing")
        val habitabilityJson = root.obj("habitability")
        val factorsJson = habitabilityJson?.obj("factors")

        val runStartedAt = root["runStartedAt"].asLongOr(fallbackNow)

        return GameState(
            saveVersion = root["saveVersion"].asIntOr(SAVE_VERSION),
            runNumber = root["runNumber"].asIntOr(0),
            runStartedAt = runStartedAt,
            lastTickAt = root["lastTickAt"].asLongOr(fallbackNow),
            createdAt = root["createdAt"].asLongOr(fallbackNow),

            resources = decodeResourceAmounts(root["resources"]),
            atmosphere = decodeGasAmounts(root["atmosphere"]),
            seaLevelRiseMeters = root["seaLevelRiseMeters"].asDoubleOr(0.0),
            previousTemperatureAnomalyC = root["previousTemperatureAnomalyC"].asDoubleOr(0.0),

            techOwned = decodeIntMap(root["techOwned"]),

            temperatureAnomalyC = root["temperatureAnomalyC"].asDoubleOr(0.0),
            forcing = ForcingBreakdown(
                perGas = decodeGasDoubles(forcingJson?.get("perGas")),
                total = forcingJson?.get("total").asDoubleOr(0.0),
            ),
            habitability = HabitabilityResult(
                fraction = habitabilityJson?.get("fraction").asDoubleOr(1.0),
                factors = HabitabilityFactors(
                    temperature = factorsJson?.get("temperature").asDoubleOr(1.0),
                    oceanAcidity = factorsJson?.get("oceanAcidity").asDoubleOr(1.0),
                    seaLevel = factorsJson?.get("seaLevel").asDoubleOr(1.0),
                    agriculture = factorsJson?.get("agriculture").asDoubleOr(1.0),
                    biodiversity = factorsJson?.get("biodiversity").asDoubleOr(1.0),
                ),
            ),
            oceanPh = root["oceanPh"].asDoubleOr(8.1),

            runStats = RunStats(
                startedAt = runStatsJson?.get("startedAt").asLongOr(runStartedAt),
                totalGasProducedKg = decodeGasAmounts(runStatsJson?.get("totalGasProducedKg")),
                peakForcingWm2 = runStatsJson?.get("peakForcingWm2").asDoubleOr(0.0),
                peakTemperatureC = runStatsJson?.get("peakTemperatureC").asDoubleOr(0.0),
                peakCo2Ppm = runStatsJson?.get("peakCo2Ppm").asDoubleOr(280.0),
                peakGasProductionRateKgPerS = decodeDecimal(runStatsJson?.get("peakGasProductionRateKgPerS")),
            ),

            lifetimeStats = LifetimeStats(
                totalPlayTimeSeconds = lifetimeJson?.get("totalPlayTimeSeconds").asDoubleOr(0.0),
                totalResets = lifetimeJson?.get("totalResets").asIntOr(0),
                totalGasProducedKg = decodeGasAmounts(lifetimeJson?.get("totalGasProducedKg")),
                highestTemperatureC = lifetimeJson?.get("highestTemperatureC").asDoubleOr(0.0),
                highestCo2Ppm = lifetimeJson?.get("highestCo2Ppm").asDoubleOr(280.0),
                fastestResetSeconds = lifetimeJson?.get("fastestResetSeconds").asDoubleOrNull(),
                longestRunSeconds = lifetimeJson?.get("longestRunSeconds").asDoubleOr(0.0),
                totalTechnologiesPurchased = lifetimeJson?.get("totalTechnologiesPurchased").asIntOr(0),
                totalEarthPointsEarned = decodeDecimal(lifetimeJson?.get("totalEarthPointsEarned")),
            ),

            prestige = PrestigeState(
                earthPoints = decodeDecimal(prestigeJson?.get("earthPoints")),
                upgradesOwned = decodeIntMap(prestigeJson?.get("upgradesOwned")),
            ),

            achievementsUnlocked = decodeBoolMap(root["achievementsUnlocked"]),
            challenges = ChallengeState(
                activeId = challengesJson?.get("activeId").asStringOrNull(),
                completed = decodeBoolMap(challengesJson?.get("completed")),
            ),

            activeEvents = root.arr("activeEvents").orEmpty().mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                val eventDefId = o["eventDefId"].asStringOrNull() ?: return@mapNotNull null
                ActiveEvent(
                    id = o["id"].asStringOrNull() ?: eventDefId,
                    eventDefId = eventDefId,
                    startedAt = o["startedAt"].asLongOr(0),
                    endsAt = o["endsAt"].asLongOr(0),
                )
            },

            milestonesTriggered = decodeBoolMap(root["milestonesTriggered"]),
            newsFeed = root.arr("newsFeed").orEmpty().mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                val milestoneId = o["milestoneId"].asStringOrNull() ?: return@mapNotNull null
                NewsItem(
                    milestoneId = milestoneId,
                    at = o["at"].asLongOr(0),
                    runSeconds = o["runSeconds"].asDoubleOr(0.0),
                )
            },

            settings = Settings(
                numberFormat = NumberFormatMode.fromId(settingsJson?.get("numberFormat").asStringOrNull()),
                soundEnabled = settingsJson?.get("soundEnabled").asBoolOr(true),
                musicEnabled = settingsJson?.get("musicEnabled").asBoolOr(true),
                vibrationEnabled = settingsJson?.get("vibrationEnabled").asBoolOr(true),
                reducedAnimations = settingsJson?.get("reducedAnimations").asBoolOr(false),
                darkMode = ThemePreference.fromId(settingsJson?.get("darkMode").asStringOrNull()),
                confirmReset = settingsJson?.get("confirmReset").asBoolOr(true),
                offlineProgressEnabled = settingsJson?.get("offlineProgressEnabled").asBoolOr(true),
            ),

            tutorial = TutorialState(
                step = tutorialJson?.get("step").asIntOr(0),
                completed = tutorialJson?.get("completed").asBoolOr(false),
                skipped = tutorialJson?.get("skipped").asBoolOr(false),
            ),

            collapsed = root["collapsed"].asBoolOr(false),
        )
    }

    /**
     * Parses and migrates a save, or returns null if it is unusable. Never
     * throws: the caller's next move on null is to try the backup slot.
     */
    fun deserialize(text: String, now: Long): GameState? {
        return try {
            val root = json.parseToJsonElement(text) as? JsonObject ?: return null
            if (!isPlausibleSave(root)) return null
            migrate(decode(root, now))
        } catch (_: Exception) {
            // Malformed JSON, a truncated write, or a value of an unexpected
            // shape. All of them mean the same thing to the caller.
            null
        }
    }
}
