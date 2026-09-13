package com.earthgame.idle.domain.technologies

import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId

/**
 * Metallurgy and materials: the first place the player meets a **processor**.
 *
 * A mill is not a mine. It produces nothing at all unless ore and heat are
 * arriving, and the moment you own more mills than the mines can feed, the
 * utilization figure on the card starts falling and tells you exactly which
 * input to go and buy more of. That loop — build, bottleneck, expand — is the
 * whole point of the branch, and every consumer here is tuned so a comfortable
 * ratio is roughly two suppliers per processor.
 *
 * Input rates are deliberately a notch under the matching producer's output at
 * the same tier: a processor should be worth building the moment it unlocks,
 * not after three more mines.
 */
val MATERIALS_TECHS: List<Technology> = listOf(
    Technology(
        id = "blast_furnace_practice",
        displayName = "Blast Furnace Practice",
        branch = TechBranch.MATERIALS,
        kind = TechKind.UNLOCK,
        tier = 12,
        description = "Coke, limestone and a continuous hot blast. The first process that turns ore into metal at industrial scale.",
        icon = "🔥",
        requires = listOf("iron_mining", "steel_production"),
        cost = listOf(TechCost(ResourceId.RESEARCH, researchCost(11))),
        costGrowth = 1.0,
        maxOwned = 1,
    ),
    Technology(
        id = "steel_mill",
        displayName = "Steel Mill",
        branch = TechBranch.MATERIALS,
        kind = TechKind.CONSUMER,
        tier = 13,
        description = "Iron Ore in, Metals out, and an appetite for energy that never stops. Runs only as fast as its ore arrives.",
        icon = "🏭",
        requires = listOf("blast_furnace_practice"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(12)),
            TechCost(ResourceId.IRON, generatorBaseCost(10)),
        ),
        costGrowth = generatorCostGrowth(12),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            // Half again what one mine of the same era yields, so the first
            // processor a player ever owns is one they have to go and feed —
            // and so the lesson lands before the chains get complicated. See
            // the bottleneck note in `docs/wiki/Economy-and-Production.md`.
            inputsPerUnit = mapOf(
                ResourceId.IRON to resourceProduction(10, 1.4),
                ResourceId.ENERGY to resourceProduction(8),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(12)),
            resourceProductionPerUnit = mapOf(ResourceId.STEEL to resourceProduction(12)),
        ),
    ),
    Technology(
        id = "industrial_cement_kiln",
        displayName = "Industrial Cement Kiln",
        branch = TechBranch.MATERIALS,
        kind = TechKind.CONSUMER,
        tier = 14,
        description = "Limestone roasted at 1,450 °C. Half the carbon comes from the fuel and half straight out of the rock itself.",
        icon = "🧱",
        requires = listOf("steel_mill", "cement_production"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(13)),
            TechCost(ResourceId.COAL, generatorBaseCost(11)),
        ),
        costGrowth = generatorCostGrowth(13),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.COAL to resourceProduction(10),
                ResourceId.ENERGY to resourceProduction(10),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(14) * 1.2),
            resourceProductionPerUnit = mapOf(ResourceId.CONCRETE to resourceProduction(13)),
        ),
    ),
    Technology(
        id = "electric_arc_furnace",
        displayName = "Electric Arc Furnace",
        branch = TechBranch.MATERIALS,
        kind = TechKind.CONSUMER,
        tier = 16,
        description = "Melting steel with electricity instead of coke. Far more metal per tonne of ore — and a far larger bill in Energy.",
        icon = "⚡",
        requires = listOf("steel_mill", "grid_electricity"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(16)),
            TechCost(ResourceId.STEEL, generatorBaseCost(13)),
        ),
        costGrowth = generatorCostGrowth(16),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.IRON to resourceProduction(12),
                ResourceId.ENERGY to resourceProduction(14),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(14)),
            resourceProductionPerUnit = mapOf(ResourceId.STEEL to resourceProduction(16)),
        ),
    ),
    Technology(
        id = "alloy_metallurgy",
        displayName = "Alloy Metallurgy",
        branch = TechBranch.MATERIALS,
        kind = TechKind.MULTIPLIER,
        tier = 18,
        description = "Chromium, nickel, vanadium: small additions, enormous differences. +80% to every metal this civilization refines.",
        icon = "🔩",
        requires = listOf("electric_arc_furnace"),
        cost = listOf(TechCost(ResourceId.ENERGY, unlockCost(18))),
        costGrowth = 1.0,
        maxOwned = 1,
        effect = TechEffect(
            resourceProductionMultiplier = ResourceMultiplier(ResourceId.STEEL, 1.8),
        ),
    ),
    Technology(
        id = "recycling_plant",
        displayName = "Recycling Plant",
        branch = TechBranch.MATERIALS,
        kind = TechKind.CONSUMER,
        tier = 19,
        description = "Scrap back into feedstock. It needs no mine at all — only power — which makes it the one processor a starved ore chain can still run.",
        icon = "♻️",
        requires = listOf("alloy_metallurgy"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(19)),
            TechCost(ResourceId.STEEL, generatorBaseCost(16)),
        ),
        costGrowth = generatorCostGrowth(19),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(17)),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(16) * 0.5),
            resourceProductionPerUnit = mapOf(
                ResourceId.STEEL to resourceProduction(18),
                ResourceId.CONCRETE to resourceProduction(17),
            ),
        ),
    ),
    Technology(
        id = "composite_engineering",
        displayName = "Composite Engineering",
        branch = TechBranch.MATERIALS,
        kind = TechKind.UNLOCK,
        tier = 21,
        description = "Carbon fibre in a resin matrix: stronger than steel at a third the mass. Everything that flies is about to get lighter.",
        icon = "🧬",
        requires = listOf("alloy_metallurgy", "petrochemical_plant"),
        cost = listOf(TechCost(ResourceId.RESEARCH, researchCost(21))),
        costGrowth = 1.0,
        maxOwned = 1,
    ),
    Technology(
        id = "advanced_materials_plant",
        displayName = "Advanced Materials Plant",
        branch = TechBranch.MATERIALS,
        kind = TechKind.CONSUMER,
        tier = 22,
        description = "Superalloys and engineered ceramics, made out of Metals and Chemicals. Everything past this point is built from them.",
        icon = "🧪",
        requires = listOf("composite_engineering"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(22)),
            TechCost(ResourceId.STEEL, generatorBaseCost(19)),
        ),
        costGrowth = generatorCostGrowth(22),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.STEEL to resourceProduction(18),
                ResourceId.CHEMICALS to resourceProduction(18),
                ResourceId.ENERGY to resourceProduction(19),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(21)),
            resourceProductionPerUnit = mapOf(ResourceId.ADVANCED_MATERIALS to resourceProduction(22)),
        ),
    ),
    Technology(
        id = "metamaterials",
        displayName = "Metamaterials",
        branch = TechBranch.MATERIALS,
        kind = TechKind.MULTIPLIER,
        tier = 26,
        description = "Structures engineered below the wavelength of light, with properties no natural substance has. ×2 Advanced Materials.",
        icon = "🔮",
        requires = listOf("advanced_materials_plant", "nanofabrication"),
        cost = listOf(TechCost(ResourceId.ENERGY, unlockCost(25))),
        costGrowth = 1.0,
        maxOwned = 1,
        effect = TechEffect(
            resourceProductionMultiplier = ResourceMultiplier(ResourceId.ADVANCED_MATERIALS, 2.0),
        ),
    ),
    Technology(
        id = "molecular_foundry",
        displayName = "Molecular Foundry",
        branch = TechBranch.MATERIALS,
        kind = TechKind.CONSUMER,
        tier = 28,
        description = "Matter assembled to specification, atom by atom. The last word in materials, and it eats electronics to do it.",
        icon = "⚛️",
        requires = listOf("metamaterials", "autonomous_robotics"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(28)),
            TechCost(ResourceId.ELECTRONICS, generatorBaseCost(24)),
        ),
        costGrowth = generatorCostGrowth(28),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.ELECTRONICS to resourceProduction(22),
                ResourceId.CHEMICALS to resourceProduction(23),
                ResourceId.ENERGY to resourceProduction(25),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(27)),
            resourceProductionPerUnit = mapOf(ResourceId.ADVANCED_MATERIALS to resourceProduction(28)),
        ),
    ),
)
