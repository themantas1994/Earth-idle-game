package com.earthgame.idle.domain.production

import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.technologies.TechBranch
import com.earthgame.idle.domain.technologies.Technology

/**
 * The economy's two kinds of building, made explicit.
 *
 * Before the production chains every building was a "generator": a thing that
 * made resources out of nothing. That is still true of half the economy — an
 * oil derrick really does pull oil out of the ground with no input but capital
 * — but it is a bad model for the other half. A refinery that produces fuel
 * whether or not there is any crude to crack is not an economy, it is a list of
 * numbers that go up.
 *
 * So the domain layer now names the two roles separately:
 *
 * - a [ProducerDefinition] turns **capital into output**. It has no inputs and
 *   always runs at 100%.
 * - a [ConsumerDefinition] turns **input flow into output flow**. It runs at
 *   the rate its scarcest input allows, and never produces anything it cannot
 *   pay for in inputs.
 *
 * Both are *projections* of a [Technology], not a parallel catalogue: ownership,
 * pricing, prerequisites, saving and the prestige system all continue to work on
 * technologies, so a building has exactly one identity and exactly one id. What
 * lives here is the part of a building that the *flow* engine cares about,
 * assembled once at startup by `ProductionGraph.kt` and validated by
 * `EconomyValidation.kt`.
 */
sealed interface BuildingDefinition {
    /** Same id as the underlying technology — a building has one identity. */
    val id: String
    val technology: Technology

    /** Resources produced per second per owned unit, before any multiplier. */
    val outputsPerUnit: Map<ResourceId, Double>

    /** Greenhouse gases emitted per second per owned unit, before any multiplier. */
    val emissionsPerUnit: Map<GasId, Double>

    /** Greenhouse gases actively removed per second per owned unit. */
    val removalPerUnit: Map<GasId, Double>

    val displayName: String get() = technology.displayName
    val description: String get() = technology.description
    val icon: String get() = technology.icon
    val branch: TechBranch get() = technology.branch
    val tier: Int get() = technology.tier
}

/**
 * A building that extracts, harvests or generates without consuming any
 * resource flow. Mines, wells, farms, dams, solar fields, the first campfire.
 */
data class ProducerDefinition(
    override val id: String,
    override val technology: Technology,
    override val outputsPerUnit: Map<ResourceId, Double>,
    override val emissionsPerUnit: Map<GasId, Double>,
    override val removalPerUnit: Map<GasId, Double>,
) : BuildingDefinition

/**
 * A building that transforms one or more input resources into an output.
 *
 * [inputsPerUnit] is the demand of a *single* unit running flat out. Owning
 * five of a consumer that wants 10 Oil/s means a demand of 50 Oil/s; if the
 * economy only supplies 25 Oil/s the consumer runs at 50% — consuming 25 Oil/s
 * and producing half its rated output — rather than running a deficit or
 * inventing the difference.
 *
 * A processor's **efficiency** — how much output it gets from the same intake —
 * is expressed where every other output tuning in this codebase is: the `scale`
 * argument on `resourceProduction(tier, scale)` in the definition itself. There
 * is deliberately no second efficiency field here, because two knobs that
 * multiply the same number are a knob and a bug waiting to disagree.
 */
data class ConsumerDefinition(
    override val id: String,
    override val technology: Technology,
    val inputsPerUnit: Map<ResourceId, Double>,
    override val outputsPerUnit: Map<ResourceId, Double>,
    override val emissionsPerUnit: Map<GasId, Double>,
    override val removalPerUnit: Map<GasId, Double>,
) : BuildingDefinition

/**
 * One edge of the economy graph, used by the validator, the diagnostics dump
 * and the wiki diagram generator.
 */
data class ProductionEdge(
    val fromResource: ResourceId?,
    val buildingId: String,
    val toResource: ResourceId?,
)
