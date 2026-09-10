package com.earthgame.idle.domain.technologies

import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId

/**
 * The last third of a run, and the stretch that decides whether the whole
 * thing is worth finishing.
 *
 * It is a single chain rather than a branching tree — by this point the
 * fiction has one direction left to go — which makes it the part of the game
 * most vulnerable to dead air: one node per tier gap means one purchase, then
 * hours of watching a bar fill. So the chain is deliberately *dense*, with a
 * node at every tier and no gaps, and it alternates generators (something to
 * keep buying, and to farm ownership doublings on) with multipliers (a single
 * loud payoff). Widening the tier gaps here is the fastest way to make the
 * endgame feel like homework again.
 */
val ENDGAME_TECHS: List<Technology> = listOf(
    Technology(
        id = "advanced_manufacturing",
        displayName = "Advanced Manufacturing",
        branch = TechBranch.ENDGAME,
        kind = TechKind.MULTIPLIER,
        tier = 28,
        description = "Robotic, self-replicating factories need no rest, no wages, and no limits.",
        icon = "🦾",
        requires = listOf("hyperscale_data_centers"),
        cost = listOf(TechCost(ResourceId.ENERGY, unlockCost(28))),
        costGrowth = 1.0,
        maxOwned = 1,
        effect = TechEffect(
            globalProductionMultiplier = 1.75,
        ),
    ),
    Technology(
        id = "fusion_power",
        displayName = "Fusion Power",
        branch = TechBranch.ENDGAME,
        kind = TechKind.GENERATOR,
        tier = 29,
        description = "Thirty years away for a century, and then suddenly not. Limitless clean power arrives — and gets spent, immediately and entirely, on running everything else harder.",
        icon = "⚛️",
        requires = listOf("advanced_manufacturing"),
        cost = listOf(TechCost(ResourceId.ENERGY, generatorBaseCost(29))),
        costGrowth = generatorCostGrowth(29),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(26)),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(29), ResourceId.RESEARCH to resourceProduction(27)),
        ),
    ),
    Technology(
        id = "orbital_industry",
        displayName = "Orbital Industry",
        branch = TechBranch.ENDGAME,
        kind = TechKind.GENERATOR,
        tier = 30,
        description = "Factories in orbit promise a cleaner future — powered by an awful lot of rocket fuel to get there.",
        icon = "🛰️",
        requires = listOf("fusion_power"),
        cost = listOf(TechCost(ResourceId.ENERGY, generatorBaseCost(30))),
        costGrowth = generatorCostGrowth(30),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(30)),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(29)),
        ),
    ),
    Technology(
        id = "space_elevator",
        displayName = "Space Elevator",
        branch = TechBranch.ENDGAME,
        kind = TechKind.MULTIPLIER,
        tier = 31,
        description = "A ribbon of carbon from the equator to geostationary orbit. Lifting a tonne off Earth stops being an event and starts being a Tuesday.",
        icon = "🛗",
        requires = listOf("orbital_industry"),
        cost = listOf(TechCost(ResourceId.ENERGY, unlockCost(31))),
        costGrowth = 1.0,
        maxOwned = 1,
        effect = TechEffect(
            branchProductionMultiplier = BranchMultiplier(TechBranch.ENDGAME, 2.5),
        ),
    ),
    Technology(
        id = "moon_mining",
        displayName = "Moon Mining",
        branch = TechBranch.ENDGAME,
        kind = TechKind.GENERATOR,
        tier = 32,
        description = "Helium-3 and rare earths from the Moon feed an industry that never once slows down to ask why.",
        icon = "🌕",
        requires = listOf("space_elevator"),
        cost = listOf(TechCost(ResourceId.ENERGY, generatorBaseCost(32))),
        costGrowth = generatorCostGrowth(32),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(31)),
            resourceProductionPerUnit = mapOf(ResourceId.STEEL to resourceProduction(31), ResourceId.CONCRETE to resourceProduction(31)),
        ),
    ),
    Technology(
        id = "asteroid_capture",
        displayName = "Asteroid Capture",
        branch = TechBranch.ENDGAME,
        kind = TechKind.GENERATOR,
        tier = 33,
        description = "Whole metallic asteroids are nudged into Earth orbit and taken apart. The commodity markets have never been calmer, or the sky busier.",
        icon = "☄️",
        requires = listOf("moon_mining"),
        cost = listOf(TechCost(ResourceId.ENERGY, generatorBaseCost(33))),
        costGrowth = generatorCostGrowth(33),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(32)),
            resourceProductionPerUnit = mapOf(ResourceId.STEEL to resourceProduction(33), ResourceId.ENERGY to resourceProduction(31)),
        ),
    ),
    Technology(
        id = "planetary_industry",
        displayName = "Planetary Industry",
        branch = TechBranch.ENDGAME,
        kind = TechKind.GENERATOR,
        tier = 34,
        description = "Every remaining hectare of the planet's surface is now industrial infrastructure of some kind.",
        icon = "🌍",
        requires = listOf("asteroid_capture"),
        cost = listOf(TechCost(ResourceId.ENERGY, generatorBaseCost(34))),
        costGrowth = generatorCostGrowth(34),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(34), GasId.CH4 to gasProduction(32), GasId.N2O to gasProduction(32), GasId.FLUORINATED to gasProduction(29) * 0.1),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(33)),
        ),
    ),
    Technology(
        id = "terraforming_engines",
        displayName = "Terraforming Engines",
        branch = TechBranch.ENDGAME,
        kind = TechKind.GENERATOR,
        tier = 35,
        description = "Continent-scale machines built to put the climate back where it was. They run on the same industry that broke it, and they are losing.",
        icon = "🏗️",
        requires = listOf("planetary_industry"),
        cost = listOf(TechCost(ResourceId.ENERGY, generatorBaseCost(35))),
        costGrowth = generatorCostGrowth(35),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            gasRemovalPerUnit = mapOf(GasId.CO2 to gasProduction(34) * 0.6),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(35), GasId.CH4 to gasProduction(33)),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(34)),
        ),
    ),
    Technology(
        id = "dyson_swarm_prototype",
        displayName = "Dyson Swarm Prototype",
        branch = TechBranch.ENDGAME,
        kind = TechKind.GENERATOR,
        tier = 36,
        description = "The first panels of a solar-collecting swarm go up. Harvesting a star, warming a planet — a footnote by comparison.",
        icon = "☀️",
        requires = listOf("terraforming_engines"),
        cost = listOf(TechCost(ResourceId.ENERGY, generatorBaseCost(36))),
        costGrowth = generatorCostGrowth(36),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(36)),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(36)),
        ),
    ),
    Technology(
        id = "matrioshka_brain",
        displayName = "Matrioshka Brain",
        branch = TechBranch.ENDGAME,
        kind = TechKind.GENERATOR,
        tier = 37,
        description = "Nested shells of computation wrapped around the sun, thinking thoughts nobody on the surface can follow. The waste heat alone is a weather system.",
        icon = "🧠",
        requires = listOf("dyson_swarm_prototype"),
        cost = listOf(TechCost(ResourceId.ENERGY, generatorBaseCost(37))),
        costGrowth = generatorCostGrowth(37),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(37), GasId.FLUORINATED to gasProduction(32) * 0.2),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(37), ResourceId.RESEARCH to resourceProduction(36)),
        ),
    ),
    Technology(
        id = "stellar_energy",
        displayName = "Stellar Energy",
        branch = TechBranch.ENDGAME,
        kind = TechKind.MULTIPLIER,
        tier = 38,
        description = "Your civilization now answers its energy needs with a literal star. Ambition has entirely outrun restraint.",
        icon = "🌟",
        requires = listOf("matrioshka_brain"),
        cost = listOf(TechCost(ResourceId.ENERGY, unlockCost(38))),
        costGrowth = 1.0,
        maxOwned = 1,
        effect = TechEffect(
            globalProductionMultiplier = 3.0,
        ),
    ),
)
