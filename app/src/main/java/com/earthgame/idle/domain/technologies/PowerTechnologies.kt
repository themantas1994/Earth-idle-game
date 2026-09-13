package com.earthgame.idle.domain.technologies

import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId

/**
 * Power generation as a **consumer** of fuel rather than a source of free
 * energy.
 *
 * The legacy power generators still work the way they always did — they are the
 * civilization's baseline supply and nothing about them changes. What this file
 * adds is the honest version: a station that burns something, whose output is
 * capped by how much of that something arrives, and whose emissions fall when
 * it is starved.
 *
 * It also contains the branch's real strategic decision. Coal, gas, fission and
 * fusion all make Energy; what separates them is how much carbon comes with it,
 * and a civilization that wants to reach the end of the tree has to notice that
 * before the atmosphere does.
 *
 * None of these consume Energy. A power station that ate its own product would
 * be a loop that mints energy out of nothing — see the cycle rules in
 * `domain/production/EconomyValidation.kt`.
 */
val POWER_TECHS: List<Technology> = listOf(
    Technology(
        id = "coal_fired_station",
        displayName = "Coal-Fired Station",
        branch = TechBranch.ELECTRICITY,
        kind = TechKind.CONSUMER,
        tier = 12,
        description = "Pulverised coal into a boiler, steam into a turbine, soot into the sky. The cheapest energy anyone has ever had.",
        icon = "🏭",
        requires = listOf("coal_power_plant"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(12)),
            TechCost(ResourceId.COAL, generatorBaseCost(10)),
        ),
        costGrowth = generatorCostGrowth(12),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(ResourceId.COAL to resourceProduction(10)),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(13) * 1.4),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(13)),
        ),
    ),
    Technology(
        id = "fuel_power_station",
        displayName = "Fuel Power Station",
        branch = TechBranch.ELECTRICITY,
        kind = TechKind.CONSUMER,
        tier = 15,
        description = "Refined Fuel burns hotter and cleaner than raw coal, and a turbine running on it starts in minutes rather than hours.",
        icon = "⚡",
        requires = listOf("refinery_complex", "grid_electricity"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(15)),
            TechCost(ResourceId.FUEL, generatorBaseCost(12)),
        ),
        costGrowth = generatorCostGrowth(15),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(ResourceId.FUEL to resourceProduction(12)),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(15)),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(16)),
        ),
    ),
    Technology(
        id = "geothermal_plant",
        displayName = "Geothermal Plant",
        branch = TechBranch.ELECTRICITY,
        kind = TechKind.GENERATOR,
        tier = 16,
        description = "Heat that was already there, tapped where the crust is thin. Nearly carbon-free, and it never stops.",
        icon = "🌋",
        requires = listOf("grid_electricity", "open_pit_mining"),
        cost = listOf(TechCost(ResourceId.ENERGY, generatorBaseCost(16))),
        costGrowth = generatorCostGrowth(16),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(13) * 0.12),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(15)),
        ),
    ),
    Technology(
        id = "wind_farm",
        displayName = "Wind Farm",
        branch = TechBranch.ELECTRICITY,
        kind = TechKind.GENERATOR,
        tier = 19,
        description = "No fuel, no flue, no emissions once the towers are up — only the rare earths in the generators and the wind's own schedule.",
        icon = "💨",
        requires = listOf("rare_earth_mining", "grid_electricity"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(18)),
            TechCost(ResourceId.RARE_EARTHS, generatorBaseCost(15)),
        ),
        costGrowth = generatorCostGrowth(18),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(14) * 0.05),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(17)),
        ),
    ),
    Technology(
        id = "solar_farm",
        displayName = "Solar Farm",
        branch = TechBranch.ELECTRICITY,
        kind = TechKind.GENERATOR,
        tier = 22,
        description = "Silicon in the desert. The cheapest electricity in history, on a planet that is getting sunnier for the worst possible reason.",
        icon = "☀️",
        requires = listOf("wind_farm", "integrated_circuits"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(19)),
            TechCost(ResourceId.ELECTRONICS, generatorBaseCost(15)),
        ),
        costGrowth = generatorCostGrowth(19),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(15) * 0.05),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(19)),
        ),
    ),
    Technology(
        id = "nuclear_fission_plant",
        displayName = "Nuclear Fission Plant",
        branch = TechBranch.NUCLEAR,
        kind = TechKind.CONSUMER,
        tier = 18,
        description = "Uranium in, gigawatts out, and almost no carbon at all. What it leaves behind instead will outlast every city on the map.",
        icon = "☢️",
        requires = listOf("uranium_mining"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(18)),
            TechCost(ResourceId.URANIUM, generatorBaseCost(15)),
            TechCost(ResourceId.CONCRETE, generatorBaseCost(15)),
        ),
        costGrowth = generatorCostGrowth(18),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(ResourceId.URANIUM to resourceProduction(14)),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(15) * 0.08),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(18)),
        ),
    ),
    Technology(
        id = "uranium_enrichment",
        displayName = "Uranium Enrichment",
        branch = TechBranch.NUCLEAR,
        kind = TechKind.MULTIPLIER,
        tier = 20,
        description = "Cascades of centrifuges raising the fissile fraction from 0.7% to 5%. ×2.2 to everything the nuclear branch generates.",
        icon = "🌀",
        requires = listOf("nuclear_fission_plant"),
        cost = listOf(TechCost(ResourceId.ENERGY, unlockCost(20))),
        costGrowth = 1.0,
        maxOwned = 1,
        effect = TechEffect(branchProductionMultiplier = BranchMultiplier(TechBranch.NUCLEAR, 2.2)),
    ),
    Technology(
        id = "fast_breeder_reactor",
        displayName = "Fast Breeder Reactor",
        branch = TechBranch.NUCLEAR,
        kind = TechKind.CONSUMER,
        tier = 23,
        description = "A reactor that makes more fuel than it burns — sixty times the energy from the same ore, cooled by liquid sodium and the nerves of its operators.",
        icon = "⚛️",
        requires = listOf("uranium_enrichment", "advanced_materials_plant"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(23)),
            TechCost(ResourceId.ADVANCED_MATERIALS, generatorBaseCost(19)),
        ),
        costGrowth = generatorCostGrowth(23),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.URANIUM to resourceProduction(16),
                ResourceId.ADVANCED_MATERIALS to resourceProduction(18),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(18) * 0.06),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(23)),
        ),
    ),
    Technology(
        id = "fusion_research",
        displayName = "Fusion Research",
        branch = TechBranch.NUCLEAR,
        kind = TechKind.UNLOCK,
        tier = 26,
        description = "Confining a plasma hotter than the sun's core for longer than it wants to be confined. Thirty years away, as always.",
        icon = "🔆",
        requires = listOf("fast_breeder_reactor", "supercomputing"),
        cost = listOf(TechCost(ResourceId.RESEARCH, researchCost(26))),
        costGrowth = 1.0,
        maxOwned = 1,
    ),
    Technology(
        id = "fusion_reactor_complex",
        displayName = "Fusion Reactor Complex",
        branch = TechBranch.NUCLEAR,
        kind = TechKind.CONSUMER,
        tier = 30,
        description = "Net power at last, from a machine built entirely out of materials that did not exist a generation ago. Effectively carbon-free — far too late to matter.",
        icon = "🌟",
        requires = listOf("fusion_power", "fusion_research", "advanced_materials_plant"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(30)),
            TechCost(ResourceId.ADVANCED_MATERIALS, generatorBaseCost(26)),
        ),
        costGrowth = generatorCostGrowth(30),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.ADVANCED_MATERIALS to resourceProduction(24),
                ResourceId.CHEMICALS to resourceProduction(25),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(24) * 0.04),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(30)),
        ),
    ),
)
