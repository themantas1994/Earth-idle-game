package com.earthgame.idle.domain.model

import com.earthgame.idle.domain.engine.CLIMATE
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.gd
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The greenhouse gases the simulation tracks.
 *
 * Kept as an enum rather than a string id so the compiler enforces
 * exhaustiveness and so the per-gas containers below can be flat arrays
 * indexed by [ordinal] — the tick loop touches every gas of every owned
 * generator, and a hash lookup per touch is not free.
 *
 * The persisted id strings match the reference implementation's, so a save
 * written by the web build still loads.
 */
enum class GasId(val id: String) {
    CO2("co2"),
    CH4("ch4"),
    N2O("n2o"),
    H2O("h2o"),
    O3("o3"),
    FLUORINATED("fluorinated");

    companion object {
        fun fromId(id: String): GasId? = entries.firstOrNull { it.id == id }
    }
}

/** Display unit for an atmospheric concentration. */
enum class GasUnit(val label: String) {
    PPM("ppm"), PPB("ppb"), PPT("ppt"), DOBSON("DU")
}

/**
 * Everything the simulation needs to know about one gas. Adding a gas to the
 * game means adding an entry here plus technologies that emit it — no engine
 * code changes.
 */
data class GasDefinition(
    val id: GasId,
    val displayName: String,
    val formula: String,
    val unit: GasUnit,
    /** Pre-industrial baseline concentration, in [unit]. */
    val baseline: Double,
    /** Atmospheric mass (kg) equivalent to one unit of concentration. */
    val massPerUnit: Double,
    /** Mean atmospheric lifetime in years; drives the first-order natural-removal rate. */
    val lifetimeYears: Double,
    /** False for pure feedback gases (H2O) that technologies cannot emit directly. */
    val directlyEmitted: Boolean,
    /** Forcing contribution (W/m²) at a given concentration, against its baseline. */
    val forcing: (concentration: Double, baseline: Double) -> Double,
)

private const val SECONDS_PER_YEAR = 365.25 * 24 * 3600

val GASES: Map<GasId, GasDefinition> = listOf(
    GasDefinition(
        id = GasId.CO2,
        displayName = "Carbon Dioxide",
        formula = "CO₂",
        unit = GasUnit.PPM,
        baseline = CLIMATE.baselineCo2Ppm,
        // Kilograms per ppm, scaled down from the real-world ~7.8e12 kg/ppm.
        // CO2 is the gas this game is *about*: at the real ratio a whole run's
        // combustion moves the needle by a couple of hundred ppm and the
        // marquee gas ends up a rounding error next to the synthetic ones.
        massPerUnit = 2e12,
        lifetimeYears = 120.0,
        directlyEmitted = true,
        forcing = { c, base -> CLIMATE.co2Alpha * ln(max(c, 1e-6) / base) },
    ),
    GasDefinition(
        id = GasId.CH4,
        displayName = "Methane",
        formula = "CH₄",
        unit = GasUnit.PPB,
        baseline = CLIMATE.baselineCh4Ppb,
        massPerUnit = 2.75e9,
        lifetimeYears = 12.0,
        directlyEmitted = true,
        forcing = { c, base -> CLIMATE.ch4Alpha * (sqrt(max(c, 0.0)) - sqrt(base)) },
    ),
    GasDefinition(
        id = GasId.N2O,
        displayName = "Nitrous Oxide",
        formula = "N₂O",
        unit = GasUnit.PPB,
        baseline = CLIMATE.baselineN2oPpb,
        massPerUnit = 1.6e10,
        lifetimeYears = 114.0,
        directlyEmitted = true,
        forcing = { c, base -> CLIMATE.n2oAlpha * (sqrt(max(c, 0.0)) - sqrt(base)) },
    ),
    GasDefinition(
        id = GasId.H2O,
        displayName = "Water Vapor",
        formula = "H₂O",
        unit = GasUnit.PPM,
        baseline = CLIMATE.baselineH2oPpm,
        massPerUnit = 1e13,
        lifetimeYears = 0.03, // ~11 days: a fast feedback, not a stock the player builds up
        directlyEmitted = false,
        forcing = { c, base -> CLIMATE.h2oAlphaPerPpm * max(c - base, 0.0) },
    ),
    GasDefinition(
        id = GasId.O3,
        displayName = "Tropospheric Ozone",
        formula = "O₃",
        unit = GasUnit.DOBSON,
        baseline = CLIMATE.baselineO3Dobson,
        massPerUnit = 5e9,
        lifetimeYears = 0.06, // ~3 weeks
        directlyEmitted = true,
        forcing = { c, base -> CLIMATE.o3Alpha * ln(max(c, 1.0) / base) },
    ),
    GasDefinition(
        id = GasId.FLUORINATED,
        displayName = "Fluorinated Gases",
        formula = "CFCs/HFCs/PFCs/SF₆",
        unit = GasUnit.PPT,
        baseline = CLIMATE.baselineFluorinatedPpt,
        // Trace gases are absurdly potent per kilogram, and with a linear
        // forcing response and a 3200-year lifetime an unscaled value lets one
        // late branch out-heat every fire, furnace and engine in the run
        // combined. Scaled so f-gases stay a nasty lategame accelerant rather
        // than the whole apocalypse.
        massPerUnit = 3e7,
        lifetimeYears = 3200.0, // SF6-scale: essentially permanent on game timescales
        directlyEmitted = true,
        forcing = { c, base -> CLIMATE.fluorinatedAlphaPerPpt * max(c - base, 0.0) },
    ),
).associateBy { it.id }

/** Declaration order, which is also the order every gas list in the UI uses. */
val GAS_LIST: List<GasDefinition> = GasId.entries.map { GASES.getValue(it) }

fun gasOf(id: GasId): GasDefinition = GASES.getValue(id)

/** Per-second first-order natural removal rate (fraction of stock removed per second). */
fun naturalRemovalRateConstant(gas: GasDefinition): Double = 1.0 / (gas.lifetimeYears * SECONDS_PER_YEAR)

fun massToConcentration(gas: GasDefinition, massKg: GameDecimal): Double = (massKg / gas.massPerUnit).toDouble()

fun concentrationToMass(gas: GasDefinition, concentration: Double): GameDecimal = gd(concentration) * gas.massPerUnit
