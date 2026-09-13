package com.earthgame.idle.domain.technologies

import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId

enum class TechBranch(val id: String, val displayName: String, val icon: String) {
    PRIMITIVE("primitive", "Primitive Civilization", "🔥"),
    AGRICULTURE("agriculture", "Agriculture", "🌾"),
    INDUSTRY("industry", "Industrial Revolution", "🏭"),
    ELECTRICITY("electricity", "Electricity", "⚡"),
    FOSSIL_FUELS("fossilFuels", "Fossil Fuels", "🛢️"),
    TRANSPORTATION("transportation", "Transportation", "🚗"),
    CONSTRUCTION("construction", "Construction", "🏗️"),
    CHEMISTRY("chemistry", "Chemical Industry", "🧪"),
    GLOBALIZATION("globalization", "Globalization", "🌐"),
    DIGITAL("digital", "Digital Civilization", "💻"),
    ENDGAME("endgame", "Endgame", "🚀"),

    // --- Branches added with the production-chain economy. ---
    MINING("mining", "Mining & Extraction", "⛏️"),
    MATERIALS("materials", "Materials & Metallurgy", "🧱"),
    NUCLEAR("nuclear", "Nuclear", "☢️"),
    ELECTRONICS("electronics", "Electronics & Computing", "🔌"),
    SPACE("space", "Space Industry", "🛰️");

    companion object {
        fun fromId(id: String): TechBranch? = entries.firstOrNull { it.id == id }
    }
}

/**
 * - [UNLOCK]: one-time tree node, gates later technologies but produces
 *   nothing of its own (e.g. "Cooking").
 * - [GENERATOR]: a **producer** — repeatably purchasable, takes no resource
 *   input and produces gas and/or resources per owned unit; cost scales with
 *   `costGrowth` per unit already owned. Everything it makes, it makes out of
 *   the planet.
 * - [CONSUMER]: a **processor** — repeatably purchasable like a generator, but
 *   it consumes a per-unit *flow* of one or more resources and can only run at
 *   the rate its scarcest input allows. See `domain/production/`.
 * - [MULTIPLIER]: one-time purchase that permanently scales production —
 *   globally, per-branch, per-gas or per-resource.
 * - [CHOICE]: one-time purchase mutually exclusive with siblings sharing a
 *   [Technology.choiceGroup] (branching strategic decisions).
 */
enum class TechKind(val id: String) {
    UNLOCK("unlock"),
    GENERATOR("generator"),
    MULTIPLIER("multiplier"),
    CHOICE("choice"),
    CONSUMER("consumer");

    /**
     * Whether this kind is a *building*: something bought repeatedly that shows
     * up on the Production screen and contributes to production rates. The
     * simulation tests this rather than comparing against [GENERATOR], so
     * producers and processors are handled by the same code path without either
     * being the special case.
     */
    val isBuilding: Boolean get() = this == GENERATOR || this == CONSUMER

    companion object {
        fun fromId(id: String): TechKind? = entries.firstOrNull { it.id == id }
    }
}

data class TechCost(val resource: ResourceId, val baseAmount: Double)

data class BranchMultiplier(val branch: TechBranch, val multiplier: Double)
data class GasMultiplier(val gas: GasId, val multiplier: Double)
data class ResourceMultiplier(val resource: ResourceId, val multiplier: Double)

/**
 * What owning a technology does. All fields are optional; a generator uses the
 * per-unit production maps, a multiplier uses the multiplier fields, and an
 * unlock may use neither.
 */
data class TechEffect(
    /**
     * Resources consumed **per second per owned unit** when running at full
     * capacity. Empty for a producer; non-empty exactly for a
     * [TechKind.CONSUMER]. A processor whose inputs cannot all be supplied runs
     * at reduced utilization rather than conjuring its output — see
     * `domain/production/ResourceFlow.kt`.
     */
    val inputsPerUnit: Map<ResourceId, Double> = emptyMap(),
    val gasProductionPerUnit: Map<GasId, Double> = emptyMap(),
    val resourceProductionPerUnit: Map<ResourceId, Double> = emptyMap(),
    val gasRemovalPerUnit: Map<GasId, Double> = emptyMap(),
    val globalProductionMultiplier: Double? = null,
    val branchProductionMultiplier: BranchMultiplier? = null,
    val gasProductionMultiplier: GasMultiplier? = null,
    val resourceProductionMultiplier: ResourceMultiplier? = null,
    val researchMultiplier: Double? = null,
)

/**
 * One node of the technology tree, defined as data. Adding technology #100
 * means adding one of these to a branch file — no engine code changes.
 */
data class Technology(
    val id: String,
    val displayName: String,
    val branch: TechBranch,
    val kind: TechKind,
    /** Overall civilization-progression index; drives cost/production scaling and UI ordering. */
    val tier: Int,
    val description: String,
    val icon: String,
    val requires: List<String> = emptyList(),
    val cost: List<TechCost>,
    val costGrowth: Double,
    /** [UNLIMITED] for generators; 1 for one-time nodes. */
    val maxOwned: Int,
    val effect: TechEffect = TechEffect(),
    val choiceGroup: String? = null,
) {
    val isOneTime: Boolean get() = maxOwned == 1

    /** Repeatably purchasable and part of the production economy. */
    val isBuilding: Boolean get() = kind.isBuilding

    /** A processor: it consumes a flow of resources to make its output. */
    val isConsumer: Boolean get() = kind == TechKind.CONSUMER

    companion object {
        /**
         * Generators have no ceiling. `Int.MAX_VALUE` stands in for the
         * reference implementation's `Infinity`: per-unit costs grow ~15% a
         * unit, so the two-billionth copy of anything would cost some 10^139
         * times the first and no run will ever approach it.
         */
        const val UNLIMITED = Int.MAX_VALUE
    }
}
