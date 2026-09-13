package com.earthgame.idle.domain.technologies

import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId

/**
 * Electronics: the branch that turns raw metals into *control*.
 *
 * Everything here is a three-input processor — copper, rare earths and power —
 * which makes it the first point in a run where the player has to keep three
 * supply lines balanced at once instead of one. The payoff is Electronics,
 * which is the gate on automation, on space, and on the research rates the late
 * tree needs.
 */
val ELECTRONICS_TECHS: List<Technology> = listOf(
    Technology(
        id = "semiconductors",
        displayName = "Semiconductors",
        branch = TechBranch.ELECTRONICS,
        kind = TechKind.UNLOCK,
        tier = 20,
        description = "Doped silicon that conducts only when told to. Every switch in the world is about to stop being mechanical.",
        icon = "🔲",
        requires = listOf("rare_earth_mining", "computers"),
        cost = listOf(TechCost(ResourceId.RESEARCH, researchCost(17))),
        costGrowth = 1.0,
        maxOwned = 1,
    ),
    Technology(
        id = "integrated_circuits",
        displayName = "Integrated Circuits",
        branch = TechBranch.ELECTRONICS,
        kind = TechKind.CONSUMER,
        tier = 21,
        description = "Thousands of transistors photolithographed onto one wafer. Copper, rare earths and clean power in; Electronics out.",
        icon = "🔌",
        requires = listOf("semiconductors", "copper_mining"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(18)),
            TechCost(ResourceId.COPPER, generatorBaseCost(15)),
        ),
        costGrowth = generatorCostGrowth(18),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.COPPER to resourceProduction(14),
                ResourceId.RARE_EARTHS to resourceProduction(13),
                ResourceId.ENERGY to resourceProduction(15),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(17), GasId.FLUORINATED to gasProduction(14) * 0.5),
            resourceProductionPerUnit = mapOf(ResourceId.ELECTRONICS to resourceProduction(18)),
        ),
    ),
    Technology(
        id = "photolithography",
        displayName = "Deep-UV Photolithography",
        branch = TechBranch.ELECTRONICS,
        kind = TechKind.MULTIPLIER,
        tier = 22,
        description = "Printing features smaller than the light used to print them. ×2.2 Electronics from the same fab.",
        icon = "🔬",
        requires = listOf("integrated_circuits"),
        cost = listOf(TechCost(ResourceId.ENERGY, unlockCost(20))),
        costGrowth = 1.0,
        maxOwned = 1,
        effect = TechEffect(resourceProductionMultiplier = ResourceMultiplier(ResourceId.ELECTRONICS, 2.2)),
    ),
    Technology(
        id = "battery_gigafactory",
        displayName = "Battery Gigafactory",
        branch = TechBranch.ELECTRONICS,
        kind = TechKind.CONSUMER,
        tier = 22,
        description = "Cells by the million, out of rare earths and industrial chemistry. Storage is what finally lets a grid run on weather.",
        icon = "🔋",
        requires = listOf("integrated_circuits", "petrochemical_plant"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(21)),
            TechCost(ResourceId.RARE_EARTHS, generatorBaseCost(17)),
        ),
        costGrowth = generatorCostGrowth(21),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.RARE_EARTHS to resourceProduction(16),
                ResourceId.CHEMICALS to resourceProduction(17),
                ResourceId.ENERGY to resourceProduction(18),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(20)),
            resourceProductionPerUnit = mapOf(ResourceId.ELECTRONICS to resourceProduction(20)),
        ),
    ),
    Technology(
        id = "supercomputing",
        displayName = "Supercomputing",
        branch = TechBranch.ELECTRONICS,
        kind = TechKind.CONSUMER,
        tier = 24,
        description = "Racks of processors solving problems no single machine could hold. It answers questions faster than anything else — and draws power like a small city.",
        icon = "🖥️",
        requires = listOf("photolithography", "data_centers"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(23)),
            TechCost(ResourceId.ELECTRONICS, generatorBaseCost(19)),
        ),
        costGrowth = generatorCostGrowth(23),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.ELECTRONICS to resourceProduction(18),
                ResourceId.ENERGY to resourceProduction(21),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(22)),
            resourceProductionPerUnit = mapOf(ResourceId.RESEARCH to resourceProduction(24)),
        ),
    ),
    Technology(
        id = "nanofabrication",
        displayName = "Nanofabrication",
        branch = TechBranch.ELECTRONICS,
        kind = TechKind.UNLOCK,
        tier = 25,
        description = "Building at the scale of tens of atoms, where the rules stop being mechanical and start being quantum.",
        icon = "🧿",
        requires = listOf("supercomputing", "composite_engineering"),
        cost = listOf(TechCost(ResourceId.RESEARCH, researchCost(25))),
        costGrowth = 1.0,
        maxOwned = 1,
        effect = TechEffect(researchMultiplier = 1.3),
    ),
    Technology(
        id = "autonomous_robotics",
        displayName = "Autonomous Robotics",
        branch = TechBranch.ELECTRONICS,
        kind = TechKind.CONSUMER,
        tier = 26,
        description = "Machines that build machines, unsupervised. Metals and Electronics go in; an industrial base that no longer needs people comes out.",
        icon = "🦾",
        requires = listOf("nanofabrication", "ai"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(26)),
            TechCost(ResourceId.ELECTRONICS, generatorBaseCost(22)),
        ),
        costGrowth = generatorCostGrowth(26),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.STEEL to resourceProduction(21),
                ResourceId.ELECTRONICS to resourceProduction(20),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(25)),
            resourceProductionPerUnit = mapOf(
                ResourceId.ENERGY to resourceProduction(26),
                ResourceId.RESEARCH to resourceProduction(23),
            ),
        ),
    ),
    Technology(
        id = "industrial_automation",
        displayName = "Industrial Automation",
        branch = TechBranch.ELECTRONICS,
        kind = TechKind.MULTIPLIER,
        tier = 27,
        description = "Every line in the civilization under closed-loop control, everywhere at once. ×1.8 to all production.",
        icon = "⚙️",
        requires = listOf("autonomous_robotics"),
        cost = listOf(TechCost(ResourceId.ENERGY, unlockCost(27))),
        costGrowth = 1.0,
        maxOwned = 1,
        effect = TechEffect(globalProductionMultiplier = 1.8),
    ),
)
