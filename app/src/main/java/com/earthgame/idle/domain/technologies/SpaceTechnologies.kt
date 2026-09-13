package com.earthgame.idle.domain.technologies

import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId

/**
 * Space industry: the top of the production graph, and the point where every
 * chain the player has built finally converges.
 *
 * A rocket factory needs Refined Fuel from the petroleum chain, Metals from the
 * ore chain and Electronics from the semiconductor chain, all at once. There is
 * no way to shortcut it — a civilization that has only ever built one chain
 * deep gets to look at a factory running at 12% and work out why. What comes
 * out is **Launch Capacity**, and everything beyond the atmosphere is priced
 * in it.
 */
val SPACE_TECHS: List<Technology> = listOf(
    Technology(
        id = "rocketry",
        displayName = "Rocketry",
        branch = TechBranch.SPACE,
        kind = TechKind.UNLOCK,
        tier = 25,
        description = "Liquid-fuelled staging, guidance, and the arithmetic that says orbit is a question of speed rather than height.",
        icon = "🚀",
        requires = listOf("supersonic_aviation", "supercomputing"),
        cost = listOf(TechCost(ResourceId.RESEARCH, researchCost(24))),
        costGrowth = 1.0,
        maxOwned = 1,
    ),
    Technology(
        id = "rocket_factory",
        displayName = "Rocket Factory",
        branch = TechBranch.SPACE,
        kind = TechKind.CONSUMER,
        tier = 27,
        description = "Fuel, Metals and Electronics, converted into tonnage that reaches orbit. Three chains have to be healthy for one of these to run.",
        icon = "🏗️",
        requires = listOf("rocketry", "autonomous_robotics"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(27)),
            TechCost(ResourceId.STEEL, generatorBaseCost(23)),
            TechCost(ResourceId.ELECTRONICS, generatorBaseCost(22)),
        ),
        costGrowth = generatorCostGrowth(27),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.FUEL to resourceProduction(22),
                ResourceId.STEEL to resourceProduction(22),
                ResourceId.ELECTRONICS to resourceProduction(21),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(26), GasId.O3 to gasProduction(22) * 0.3),
            resourceProductionPerUnit = mapOf(ResourceId.LAUNCH_CAPACITY to resourceProduction(27)),
        ),
    ),
    Technology(
        id = "reusable_launch",
        displayName = "Reusable Launch Vehicles",
        branch = TechBranch.SPACE,
        kind = TechKind.MULTIPLIER,
        tier = 29,
        description = "A booster that lands and flies again drops the price of orbit by an order of magnitude. ×3 Launch Capacity.",
        icon = "🛬",
        requires = listOf("rocket_factory"),
        cost = listOf(TechCost(ResourceId.ENERGY, unlockCost(29))),
        costGrowth = 1.0,
        maxOwned = 1,
        effect = TechEffect(
            resourceProductionMultiplier = ResourceMultiplier(ResourceId.LAUNCH_CAPACITY, 3.0),
        ),
    ),
    Technology(
        id = "orbital_shipyard",
        displayName = "Orbital Shipyard",
        branch = TechBranch.SPACE,
        kind = TechKind.CONSUMER,
        tier = 31,
        description = "Assembly in free fall, where nothing has to survive its own weight. Structures too large to launch get built where they will be used.",
        icon = "🛰️",
        requires = listOf("reusable_launch", "orbital_industry"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(31)),
            TechCost(ResourceId.LAUNCH_CAPACITY, generatorBaseCost(26)),
        ),
        costGrowth = generatorCostGrowth(31),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.LAUNCH_CAPACITY to resourceProduction(25),
                ResourceId.ADVANCED_MATERIALS to resourceProduction(25),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(30)),
            resourceProductionPerUnit = mapOf(
                ResourceId.ENERGY to resourceProduction(31),
                ResourceId.RESEARCH to resourceProduction(28),
            ),
        ),
    ),
    Technology(
        id = "asteroid_mining_facility",
        displayName = "Asteroid Mining Facility",
        branch = TechBranch.SPACE,
        kind = TechKind.CONSUMER,
        tier = 33,
        description = "A single metallic asteroid holds more iron than the crust has ever yielded. Getting there is the only hard part, and Launch Capacity is how.",
        icon = "☄️",
        requires = listOf("orbital_shipyard", "asteroid_capture"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(33)),
            TechCost(ResourceId.LAUNCH_CAPACITY, generatorBaseCost(28)),
        ),
        costGrowth = generatorCostGrowth(33),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(ResourceId.LAUNCH_CAPACITY to resourceProduction(27)),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(31)),
            resourceProductionPerUnit = mapOf(
                ResourceId.IRON to resourceProduction(33),
                ResourceId.RARE_EARTHS to resourceProduction(32),
            ),
        ),
    ),
    Technology(
        id = "space_based_solar",
        displayName = "Space-Based Solar",
        branch = TechBranch.SPACE,
        kind = TechKind.CONSUMER,
        tier = 34,
        description = "Collectors in permanent sunlight, beaming power down by microwave. No night, no weather, no atmosphere in the way.",
        icon = "🔆",
        requires = listOf("asteroid_mining_facility"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(34)),
            TechCost(ResourceId.ADVANCED_MATERIALS, generatorBaseCost(29)),
        ),
        costGrowth = generatorCostGrowth(34),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.LAUNCH_CAPACITY to resourceProduction(28),
                ResourceId.ADVANCED_MATERIALS to resourceProduction(28),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(30) * 0.1),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(34)),
        ),
    ),
    Technology(
        id = "dyson_swarm_assembly",
        displayName = "Dyson Swarm Assembly",
        branch = TechBranch.SPACE,
        kind = TechKind.CONSUMER,
        tier = 37,
        description = "Collector after collector, launched until the star is surrounded. The civilization's energy budget stops being a planetary question.",
        icon = "🌞",
        requires = listOf("space_based_solar", "dyson_swarm_prototype"),
        cost = listOf(
            TechCost(ResourceId.ENERGY, generatorBaseCost(37)),
            TechCost(ResourceId.LAUNCH_CAPACITY, generatorBaseCost(31)),
        ),
        costGrowth = generatorCostGrowth(37),
        maxOwned = Technology.UNLIMITED,
        effect = TechEffect(
            inputsPerUnit = mapOf(
                ResourceId.LAUNCH_CAPACITY to resourceProduction(30),
                ResourceId.ADVANCED_MATERIALS to resourceProduction(30),
            ),
            gasProductionPerUnit = mapOf(GasId.CO2 to gasProduction(34)),
            resourceProductionPerUnit = mapOf(ResourceId.ENERGY to resourceProduction(37)),
        ),
    ),
    Technology(
        id = "stellar_engineering",
        displayName = "Stellar Engineering",
        branch = TechBranch.SPACE,
        kind = TechKind.MULTIPLIER,
        tier = 39,
        description = "The output of a star, on tap, allocated by committee. ×4 to everything — and the planet underneath it all is still the one you started on.",
        icon = "✨",
        requires = listOf("dyson_swarm_assembly", "matrioshka_brain"),
        cost = listOf(TechCost(ResourceId.ENERGY, unlockCost(39))),
        costGrowth = 1.0,
        maxOwned = 1,
        effect = TechEffect(globalProductionMultiplier = 4.0),
    ),
)
