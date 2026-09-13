package com.earthgame.idle.domain.model

import com.earthgame.idle.domain.engine.CLIMATE
import com.earthgame.idle.domain.engine.GAME_SECONDS_PER_YEAR
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.REAL_SECONDS_PER_GAME_YEAR
import com.earthgame.idle.domain.engine.gd
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
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
    /**
     * Atmospheric half-life, in **simulated** years: how long an undisturbed
     * stock of this gas takes to fall to half. Drives the first-order natural
     * decay constant `λ = ln 2 / halfLife`.
     *
     * These are the numbers the game has always shipped with, unchanged. What
     * changed is that they are now half-lives operating over simulated time
     * (`GameTime.kt`) rather than mean lifetimes over real time, so the decay
     * they describe is something a run actually feels.
     */
    val halfLifeYears: Double,
    /**
     * Whether this gas is an accumulating **stock** that decays with
     * [halfLifeYears] on the simulated calendar.
     *
     * True for every gas the player can build up. False only for H2O, which is
     * not a stock at all: it is a diagnostic feedback relaxing toward an
     * equilibrium that warming sets, and [halfLifeYears] is read as the mean
     * residence time governing how fast it tracks that equilibrium — on the
     * real clock, exactly as before. Putting the feedback on the simulated
     * clock would let it reach equilibrium within a run, which through the
     * documented sink-efficiency quirk amplifies late-game warming several-fold
     * and prices the last two endgame technologies out of a completed run. See
     * `docs/wiki/Atmospheric-Half-Life.md`.
     */
    val decaysOnSimulatedClock: Boolean,
    /** False for pure feedback gases (H2O) that technologies cannot emit directly. */
    val directlyEmitted: Boolean,
    /** Forcing contribution (W/m²) at a given concentration, against its baseline. */
    val forcing: (concentration: Double, baseline: Double) -> Double,
)

private val LN_2 = ln(2.0)

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
        halfLifeYears = 120.0,
        decaysOnSimulatedClock = true,
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
        halfLifeYears = 12.0,
        decaysOnSimulatedClock = true,
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
        halfLifeYears = 114.0,
        decaysOnSimulatedClock = true,
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
        // ~11 days. Not a half-life: H2O is a feedback, and this is the mean
        // residence time it relaxes toward its equilibrium with. See
        // `decaysOnSimulatedClock`.
        halfLifeYears = 0.03,
        decaysOnSimulatedClock = false,
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
        halfLifeYears = 0.06, // ~3 weeks
        decaysOnSimulatedClock = true,
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
        halfLifeYears = 3200.0, // SF6-scale: essentially permanent on game timescales
        decaysOnSimulatedClock = true,
        directlyEmitted = true,
        forcing = { c, base -> CLIMATE.fluorinatedAlphaPerPpt * max(c - base, 0.0) },
    ),
).associateBy { it.id }

/** Declaration order, which is also the order every gas list in the UI uses. */
val GAS_LIST: List<GasDefinition> = GasId.entries.map { GASES.getValue(it) }

fun gasOf(id: GasId): GasDefinition = GASES.getValue(id)

/**
 * Fraction of an undisturbed stock of a gas still present after
 * [dtGameSeconds] of simulated time, straight from the half-life law
 *
 *     C(t) = C0 × 2^(-t / H)
 *
 * A stock of 1,000 with a 120-year half-life is 500 after 120 simulated years,
 * 250 after 240, and 125 after 360. This is the definition the decay constant
 * below is derived from, not a second implementation of it: `e^(-λ·t)` with
 * `λ = ln 2 / H` is the same curve, and `AtmosphericHalfLifeTest` asserts the
 * two agree.
 */
fun halfLifeFractionRemaining(halfLifeYears: Double, dtGameSeconds: Double): Double {
    if (halfLifeYears <= 0.0 || dtGameSeconds <= 0.0) return if (dtGameSeconds <= 0.0) 1.0 else 0.0
    return 2.0.pow(-(dtGameSeconds / GAME_SECONDS_PER_YEAR) / halfLifeYears)
}

/**
 * First-order natural decay constant λ, per second of **real** time — which is
 * what [com.earthgame.idle.domain.engine.simulateStep] integrates in.
 *
 * `λ = ln 2 / halfLife` is the half-life law written as a rate, and dividing
 * the half-life by [REAL_SECONDS_PER_GAME_YEAR] rather than by a year of real
 * seconds is the whole of the conversion between the two clocks: it happens
 * once, here, so nothing downstream has to know which clock it is on.
 *
 * H2O is the documented exception ([GasDefinition.decaysOnSimulatedClock]): it
 * is a feedback rather than a stock, and keeps the real-time relaxation rate it
 * has always had.
 */
fun naturalRemovalRateConstant(gas: GasDefinition): Double =
    if (gas.decaysOnSimulatedClock) {
        LN_2 / (gas.halfLifeYears * REAL_SECONDS_PER_GAME_YEAR)
    } else {
        1.0 / (gas.halfLifeYears * GAME_SECONDS_PER_YEAR)
    }


fun massToConcentration(gas: GasDefinition, massKg: GameDecimal): Double = (massKg / gas.massPerUnit).toDouble()

fun concentrationToMass(gas: GasDefinition, concentration: Double): GameDecimal = gd(concentration) * gas.massPerUnit
