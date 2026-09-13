package com.earthgame.idle.domain.technologies

import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId

/**
 * Petroleum and chemistry: the middle of the production graph.
 *
 * The crude that derricks pull out of the ground is useless until it is split
 * into fractions, and every branch downstream of here — combustion, aviation,
 * plastics, fertiliser, composites, rocketry — is really a customer of this
 * one. It is also where the game's central irony lives: refining is what makes
 * a barrel worth having, and refining is what turns it into carbon dioxide.
 */
val REFINING_TECHS: List<Technology> = listOf(
    Technology(
        id = "rotary_drilling",
        displayName = "Rotary Drilling",
        branch = TechBranch.FOSSIL_FUELS,
        kind = TechKind.MULTIPLIER,
        tier = 10,
        description = "A rotating bit and circulating mud go where cable tools never could. +70% to everything the fossil branch extracts.",
        icon = "🔩",
        requires = listOf("oil_drilling"),
        cost = listOf(TechCost(ResourceId.ENERGY, unlockCost(10))),
        costGrowth = 1.0,
        maxOwned = 1,
        effect = TechEffect(branchProductionMultiplier = BranchMultiplier(TechBranch.FOSSIL_FUELS, 1.7)),
    ),
    Technology(
        id = "fractional_distillation",
        displayName = "Fractional Distillation",
        branch = TechBranch.FOSSIL_FUELS,
        kind = TechKind.UNLOCK,
        tier = 11,
        description = "A column that separates crude by boiling point: gas at the top, bitumen at the bottom, everything useful in between.",
        icon = "🗼",
        requires = listOf("oil_refinery"),
        cost = listOf(TechCost(ResourceId.RESEARCH, researchCost(11))),
        costGrowth = 1.0,
        maxOwned = 1,
    ),
    Technology(
        id = "refinery_complex",
        displayName = "Refinery Complex",
        branch = TechBranch.FOSSIL_FUELS,
        kind = TechKind.CONSUMER,
        tier = 12,
        description = "Crude in, Refined Fuel out. Owning more of these than the derricks can supply is the first bottleneck most civilizations hit.",
        icon = "🏭",
        requires = listOf("fractional_distillation"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(12)),
            TechCost(ResourceId.OIL, generatorBaseCost(10)),
        ),
        costGrowth = generatorCostGrowth(12),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(ResourceId.OIL to resourceProduction(10)),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(12)),
            resourceProductionPerUnit = mapOf(ResourceId.FUEL to resourceProduction(12)),
        ),
    ),
    Technology(
        id = "catalytic_cracking",
        displayName = "Catalytic Cracking",
        branch = TechBranch.FOSSIL_FUELS,
        kind = TechKind.MULTIPLIER,
        tier = 14,
        description = "A zeolite catalyst breaks heavy fractions into light ones, roughly doubling the petrol a barrel yields. ×2 Refined Fuel.",
        icon = "⚗️",
        requires = listOf("refinery_complex"),
        cost = listOf(TechCost(ResourceId.ENERGY, unlockCost(14))),
        costGrowth = 1.0,
        maxOwned = 1,
        effect = TechEffect(resourceProductionMultiplier = ResourceMultiplier(ResourceId.FUEL, 2.0)),
    ),
    Technology(
        id = "petrochemical_plant",
        displayName = "Petrochemical Plant",
        branch = TechBranch.CHEMISTRY,
        kind = TechKind.CONSUMER,
        tier = 14,
        description = "Oil and power become solvents, polymers and fertiliser feedstock. Nothing modern gets built without what comes out of here.",
        icon = "🧪",
        requires = listOf("refinery_complex", "petrochemicals"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(14)),
            TechCost(ResourceId.OIL, generatorBaseCost(12)),
        ),
        costGrowth = generatorCostGrowth(14),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.OIL to resourceProduction(11),
                ResourceId.ENERGY to resourceProduction(11),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(14), GasId.CH4 to gasProduction(11) * 0.3),
            resourceProductionPerUnit = mapOf(ResourceId.CHEMICALS to resourceProduction(14)),
        ),
    ),
    Technology(
        id = "ammonia_synthesis_plant",
        displayName = "Ammonia Synthesis Plant",
        branch = TechBranch.CHEMISTRY,
        kind = TechKind.CONSUMER,
        tier = 16,
        description = "Nitrogen out of the air under enormous pressure. It feeds half the planet, and every field it fertilises breathes nitrous oxide back.",
        icon = "🌾",
        requires = listOf("petrochemical_plant", "ammonia"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(16)),
            TechCost(ResourceId.CHEMICALS, generatorBaseCost(13)),
        ),
        costGrowth = generatorCostGrowth(16),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.CHEMICALS to resourceProduction(13),
                ResourceId.ENERGY to resourceProduction(14),
            ),
            gasProductionPerUnit = mapOf(GasId.N2O to gasProduction(15), GasId.CO2 to gasProduction(14)),
            resourceProductionPerUnit = mapOf(ResourceId.RESEARCH to resourceProduction(15)),
        ),
    ),
    Technology(
        id = "synthetic_fuel_plant",
        displayName = "Synthetic Fuel Plant",
        branch = TechBranch.CHEMISTRY,
        kind = TechKind.CONSUMER,
        tier = 18,
        description = "Coal to liquids, by way of syngas. It works, it scales, and it emits roughly twice what the crude it replaces would have.",
        icon = "🛢️",
        requires = listOf("petrochemical_plant", "coal_mega_mining"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(18)),
            TechCost(ResourceId.COAL, generatorBaseCost(15)),
        ),
        costGrowth = generatorCostGrowth(18),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.COAL to resourceProduction(15),
                ResourceId.ENERGY to resourceProduction(15),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(19) * 1.6),
            resourceProductionPerUnit = mapOf(ResourceId.FUEL to resourceProduction(18)),
        ),
    ),
    Technology(
        id = "industrial_catalysis",
        displayName = "Industrial Catalysis",
        branch = TechBranch.CHEMISTRY,
        kind = TechKind.MULTIPLIER,
        tier = 20,
        description = "The right catalyst turns a reaction that needs a furnace into one that needs a warm afternoon. ×2 Chemicals.",
        icon = "🥼",
        requires = listOf("ammonia_synthesis_plant"),
        cost = listOf(TechCost(ResourceId.ENERGY, unlockCost(20))),
        costGrowth = 1.0,
        maxOwned = 1,
        effect = TechEffect(resourceProductionMultiplier = ResourceMultiplier(ResourceId.CHEMICALS, 2.0)),
    ),
    Technology(
        id = "chemical_megaplex",
        displayName = "Chemical Megaplex",
        branch = TechBranch.CHEMISTRY,
        kind = TechKind.CONSUMER,
        tier = 22,
        description = "A site the size of a city that cracks, reforms and polymerises around the clock. Everything advanced the civilization builds is waiting on what comes out of it.",
        icon = "🏭",
        requires = listOf("industrial_catalysis", "advanced_chemical_manufacturing"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(22)),
            TechCost(ResourceId.OIL, generatorBaseCost(19)),
        ),
        costGrowth = generatorCostGrowth(22),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.OIL to resourceProduction(19),
                ResourceId.ENERGY to resourceProduction(19),
            ),
            gasProductionPerUnit = mapOf(
                GasId.CO2 to gasProduction(21),
                GasId.CH4 to gasProduction(17) * 0.4,
            ),
            resourceProductionPerUnit = mapOf(ResourceId.CHEMICALS to resourceProduction(22)),
        ),
    ),
    Technology(
        id = "carbon_capture_plant",
        displayName = "Carbon Capture Plant",
        branch = TechBranch.CHEMISTRY,
        kind = TechKind.CONSUMER,
        tier = 24,
        description = "Amine scrubbers pulling CO₂ straight out of the air, using power and solvent to do it. It works. It does not work anywhere near fast enough.",
        icon = "🌬️",
        requires = listOf("industrial_catalysis", "advanced_materials_plant"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(24)),
            TechCost(ResourceId.CHEMICALS, generatorBaseCost(21)),
        ),
        costGrowth = generatorCostGrowth(24),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.CHEMICALS to resourceProduction(20),
                ResourceId.ENERGY to resourceProduction(22),
            ),
            gasRemovalPerUnit = mapOf(GasId.CO2 to gasProduction(24) * 1.4),
            resourceProductionPerUnit = mapOf(ResourceId.RESEARCH to resourceProduction(21)),
        ),
    ),
)
