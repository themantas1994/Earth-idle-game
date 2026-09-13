# EARTH Technology Audit

[← Documentation home](wiki/Home.md) · [Codebase audit](CODEBASE_AUDIT.md)

Every count below was produced by an independent, from-scratch paren-balanced
parser reading the 17 `*Technologies.kt` data files directly, then
cross-checked against a breadth-first reachability closure computed
independently of `TechnologyRegistry.kt`/`TechnologyParityTest`'s own logic.
Where they agree, both are cited.

## Headline numbers

| | Value |
| :-- | --: |
| Total technologies | **157** |
| Branches | **16** |
| Tier range | 0 – 39 |
| Producers (`GENERATOR`) | 78 |
| Processors (`CONSUMER`) | 26 |
| Multipliers | 28 |
| Unlocks | 23 |
| Choices | 2 (one `choiceGroup`: `energy_strategy`) |
| Duplicate technology IDs | **0** |
| Duplicate display names | **0** |
| Unreachable technologies (BFS from `natural_fire`) | **0** — all 157 reachable |
| Tier inversions (`requires` a higher-tier prerequisite) | **2**, both pre-existing and allow-listed (see below) |

This matches the reference-implementation-anchored count in
`TechnologyParityTest` (which separately asserts the *original* 99 remain
field-for-field identical to the frozen TypeScript oracle, in their
original relative order) and the production-chain PR's own stated 157 —
independently reproduced here, not merely re-quoted.

## The one choice group

`energy_strategy` (tier 14, both requiring `coal_power_plant`):

| ID | Effect |
| :-- | :-- |
| `coal_industrialization` | (mutually exclusive with the entry below) |
| `nuclear_industrialization` | (mutually exclusive with the entry above) |

Choosing one permanently locks out the other for the run
(`isRivalChoiceTaken` in `TechnologyRegistry.kt`, exercised by
`TechnologyParityTest`).

## Known, allow-listed tier inversions

Two technologies require a *higher-tier* prerequisite than their own tier —
normally a graph-integrity error, but both predate the production-chain
economy, are pinned by reference-parity fixtures against the frozen
TypeScript engine, and are explicitly named in an allow-list inside
`EconomyValidationTest` rather than silently tolerated:

| Technology (tier) | Requires (tier) |
| :-- | :-- |
| `concrete` (11) | `cement_production` (12) |
| `industrial_chemistry` (11) | `chemical_industry` (15) |

No new inversion may join this list without the test itself being edited —
i.e. any *future* inversion introduced by a careless edit would fail CI.

## Full technology-tree table, by branch

### Primitive (10)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 0 | `natural_fire` | Producer | — |
| 1 | `controlled_fire` | Producer | `natural_fire` |
| 2 | `cooking` | Unlock | `controlled_fire` |
| 3 | `charcoal` | Producer | `cooking` |
| 3 | `pottery` | Unlock | `cooking` |
| 4 | `metalworking` | Unlock | `charcoal` |
| 5 | `bronze` | Producer | `metalworking` |
| 6 | `iron` | Producer | `bronze` |
| 6 | `the_wheel` | Multiplier | `pottery`, `metalworking` |
| 7 | `early_agriculture` | Unlock | `pottery` |

### Agriculture (10)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 7 | `farming` | Producer | `early_agriculture` |
| 8 | `animal_domestication` | Producer | `farming` |
| 9 | `irrigation` | Unlock | `farming` |
| 10 | `rice_cultivation` | Producer | `irrigation` |
| 11 | `livestock` | Producer | `animal_domestication` |
| 12 | `fertilizer` | Unlock | `rice_cultivation`, `livestock` |
| 13 | `synthetic_fertilizer` | Producer | `fertilizer`, `ammonia` |
| 14 | `industrial_agriculture` | Unlock | `synthetic_fertilizer` |
| 15 | `mega_farms` | Producer | `industrial_agriculture` |
| 16 | `factory_farming` | Producer | `mega_farms` |

### Transportation (12)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 8 | `horse_and_cart` | Producer | `early_agriculture` |
| 10 | `steam_vehicle` | Producer | `steam_engine` |
| 12 | `automobile` | Producer | `oil_refinery` |
| 13 | `mass_production_cars` | Producer | `automobile`, `factories` |
| 13 | `diesel_engine` | Producer | `oil_refinery` |
| 14 | `trucks` | Producer | `diesel_engine` |
| 14 | `highways` | Multiplier | `mass_production_cars` |
| 15 | `jet_aircraft` | Producer | `diesel_engine`, `petrochemicals` |
| 16 | `commercial_aviation` | Producer | `jet_aircraft` |
| 16 | `container_ships` | Producer | `oil_refinery` |
| 17 | `global_logistics` | Multiplier | `container_ships`, `trucks` |
| 19 | `supersonic_aviation` | Producer | `commercial_aviation` |

### Industry (9)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 8 | `steam_engine` | Unlock | `iron` |
| 9 | `coal_mining` | Producer | `steam_engine` |
| 10 | `steam_locomotive` | Producer | `coal_mining` |
| 11 | `factories` | Producer | `steam_engine` |
| 12 | `steel_production` | Producer | `coal_mining`, `iron` |
| 12 | `cement_production` | Producer | `factories` |
| 13 | `industrial_furnaces` | Producer | `steel_production` |
| 14 | `mass_manufacturing` | Multiplier | `factories`, `cement_production` |
| 15 | `chemical_industry` | Unlock | `mass_manufacturing` |

### Mining (12)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 8 | `surface_prospecting` | Unlock | `metalworking` |
| 9 | `iron_mining` | Producer | `surface_prospecting` |
| 11 | `copper_mining` | Producer | `iron_mining` |
| 12 | `mechanised_mining` | Multiplier | `copper_mining`, `steam_engine` |
| 14 | `open_pit_mining` | Producer | `mechanised_mining` |
| 16 | `uranium_prospecting` | Unlock | `open_pit_mining`, `nuclear_power` |
| 17 | `uranium_mining` | Producer | `uranium_prospecting` |
| 17 | `rare_earth_processing` | Unlock | `open_pit_mining`, `industrial_chemistry` |
| 18 | `rare_earth_mining` | Producer | `rare_earth_processing` |
| 20 | `in_situ_leaching` | Producer | `rare_earth_mining`, `petrochemicals` |
| 23 | `deep_sea_mining` | Producer | `in_situ_leaching`, `containerization` |
| 26 | `autonomous_mining_fleets` | Multiplier | `deep_sea_mining`, `ai` |

### Electricity (16)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 9 | `generator_dynamo` | Producer | `steam_engine` |
| 11 | `coal_power_plant` | Producer | `generator_dynamo`, `coal_mining` |
| 12 | `coal_fired_station` | Processor | `coal_power_plant` |
| 12 | `grid_electricity` | Multiplier | `coal_power_plant` |
| 13 | `hydroelectricity` | Producer | `grid_electricity` |
| 13 | `gas_turbine` | Producer | `grid_electricity` |
| 14 | `coal_industrialization` | Choice | `coal_power_plant` |
| 14 | `nuclear_industrialization` | Choice | `coal_power_plant` |
| 15 | `fuel_power_station` | Processor | `refinery_complex`, `grid_electricity` |
| 15 | `nuclear_power` | Producer | `grid_electricity` |
| 16 | `geothermal_plant` | Producer | `grid_electricity`, `open_pit_mining` |
| 16 | `supercritical_coal` | Producer | `coal_power_plant` |
| 16 | `combined_cycle_gas` | Producer | `gas_turbine` |
| 17 | `mass_electrification` | Multiplier | `nuclear_power`, `combined_cycle_gas` |
| 19 | `wind_farm` | Producer | `rare_earth_mining`, `grid_electricity` |
| 22 | `solar_farm` | Producer | `wind_farm`, `integrated_circuits` |

### Fossil Fuels (12)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 9 | `oil_drilling` | Producer | `steam_engine` |
| 10 | `rotary_drilling` | Multiplier | `oil_drilling` |
| 10 | `oil_refinery` | Producer | `oil_drilling` |
| 11 | `fractional_distillation` | Unlock | `oil_refinery` |
| 12 | `refinery_complex` | Processor | `fractional_distillation` |
| 12 | `petrochemicals` | Unlock | `oil_refinery` |
| 12 | `natural_gas` | Producer | `oil_drilling` |
| 14 | `catalytic_cracking` | Multiplier | `refinery_complex` |
| 14 | `fracking` | Producer | `natural_gas` |
| 15 | `offshore_drilling` | Producer | `oil_refinery` |
| 16 | `oil_sands` | Producer | `offshore_drilling` |
| 17 | `coal_mega_mining` | Producer | `coal_mining`, `oil_sands` |

### Chemistry (13)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 11 | `industrial_chemistry` | Unlock | `chemical_industry` |
| 12 | `ammonia` | Producer | `industrial_chemistry` |
| 14 | `petrochemical_plant` | Processor | `refinery_complex`, `petrochemicals` |
| 14 | `refrigeration` | Producer | `ammonia` |
| 16 | `ammonia_synthesis_plant` | Processor | `petrochemical_plant`, `ammonia` |
| 16 | `cfcs` | Producer | `refrigeration` |
| 18 | `synthetic_fuel_plant` | Processor | `petrochemical_plant`, `coal_mega_mining` |
| 18 | `hfcs` | Producer | `cfcs` |
| 20 | `industrial_catalysis` | Multiplier | `ammonia_synthesis_plant` |
| 20 | `industrial_fluorinated_gases` | Producer | `hfcs` |
| 21 | `advanced_chemical_manufacturing` | Multiplier | `industrial_fluorinated_gases` |
| 22 | `chemical_megaplex` | Processor | `industrial_catalysis`, `advanced_chemical_manufacturing` |
| 24 | `carbon_capture_plant` | Processor | `industrial_catalysis`, `advanced_materials_plant` |

### Construction (7)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 11 | `concrete` | Producer | `cement_production` |
| 13 | `steel_buildings` | Producer | `steel_production`, `concrete` |
| 15 | `skyscrapers` | Producer | `steel_buildings` |
| 16 | `airports` | Producer | `concrete`, `commercial_aviation` |
| 17 | `megacities` | Producer | `skyscrapers` |
| 18 | `mega_infrastructure` | Multiplier | `megacities`, `airports` |
| 19 | `global_urbanization` | Multiplier | `mega_infrastructure` |

### Materials (10)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 12 | `blast_furnace_practice` | Unlock | `iron_mining`, `steel_production` |
| 13 | `steel_mill` | Processor | `blast_furnace_practice` |
| 14 | `industrial_cement_kiln` | Processor | `steel_mill`, `cement_production` |
| 16 | `electric_arc_furnace` | Processor | `steel_mill`, `grid_electricity` |
| 18 | `alloy_metallurgy` | Multiplier | `electric_arc_furnace` |
| 19 | `recycling_plant` | Processor | `alloy_metallurgy` |
| 21 | `composite_engineering` | Unlock | `alloy_metallurgy`, `petrochemical_plant` |
| 22 | `advanced_materials_plant` | Processor | `composite_engineering` |
| 26 | `metamaterials` | Multiplier | `advanced_materials_plant`, `nanofabrication` |
| 28 | `molecular_foundry` | Processor | `metamaterials`, `autonomous_robotics` |

### Globalization (7)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 17 | `international_trade` | Unlock | `container_ships` |
| 18 | `containerization` | Producer | `international_trade` |
| 19 | `global_supply_chains` | Multiplier | `containerization`, `global_logistics` |
| 20 | `consumer_economy` | Producer | `global_supply_chains` |
| 21 | `global_aviation_networks` | Producer | `commercial_aviation` |
| 22 | `247_manufacturing` | Producer | `consumer_economy` |
| 23 | `megacorporations` | Multiplier | `247_manufacturing`, `global_aviation_networks` |

### Nuclear (5)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 18 | `nuclear_fission_plant` | Processor | `uranium_mining` |
| 20 | `uranium_enrichment` | Multiplier | `nuclear_fission_plant` |
| 23 | `fast_breeder_reactor` | Processor | `uranium_enrichment`, `advanced_materials_plant` |
| 26 | `fusion_research` | Unlock | `fast_breeder_reactor`, `supercomputing` |
| 30 | `fusion_reactor_complex` | Processor | `fusion_power`, `fusion_research`, `advanced_materials_plant` |

### Digital (7)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 20 | `computers` | Unlock | `mass_electrification` |
| 22 | `internet` | Multiplier | `computers`, `global_supply_chains` |
| 23 | `data_centers` | Producer | `internet` |
| 24 | `cloud_computing` | Producer | `data_centers` |
| 25 | `ai` | Producer | `cloud_computing` |
| 26 | `mass_ai_infrastructure` | Producer | `ai` |
| 27 | `hyperscale_data_centers` | Multiplier | `mass_ai_infrastructure` |

### Electronics (8)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 20 | `semiconductors` | Unlock | `rare_earth_mining`, `computers` |
| 21 | `integrated_circuits` | Processor | `semiconductors`, `copper_mining` |
| 22 | `photolithography` | Multiplier | `integrated_circuits` |
| 22 | `battery_gigafactory` | Processor | `integrated_circuits`, `petrochemical_plant` |
| 24 | `supercomputing` | Processor | `photolithography`, `data_centers` |
| 25 | `nanofabrication` | Unlock | `supercomputing`, `composite_engineering` |
| 26 | `autonomous_robotics` | Processor | `nanofabrication`, `ai` |
| 27 | `industrial_automation` | Multiplier | `autonomous_robotics` |

### Space (8)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 25 | `rocketry` | Unlock | `supersonic_aviation`, `supercomputing` |
| 27 | `rocket_factory` | Processor | `rocketry`, `autonomous_robotics` |
| 29 | `reusable_launch` | Multiplier | `rocket_factory` |
| 31 | `orbital_shipyard` | Processor | `reusable_launch`, `orbital_industry` |
| 33 | `asteroid_mining_facility` | Processor | `orbital_shipyard`, `asteroid_capture` |
| 34 | `space_based_solar` | Processor | `asteroid_mining_facility` |
| 37 | `dyson_swarm_assembly` | Processor | `space_based_solar`, `dyson_swarm_prototype` |
| 39 | `stellar_engineering` | Multiplier | `dyson_swarm_assembly`, `matrioshka_brain` |

### Endgame (11)

| Tier | ID | Kind | Requires |
| --: | :-- | :-- | :-- |
| 28 | `advanced_manufacturing` | Multiplier | `hyperscale_data_centers` |
| 29 | `fusion_power` | Producer | `advanced_manufacturing` |
| 30 | `orbital_industry` | Producer | `fusion_power` |
| 31 | `space_elevator` | Multiplier | `orbital_industry` |
| 32 | `moon_mining` | Producer | `space_elevator` |
| 33 | `asteroid_capture` | Producer | `moon_mining` |
| 34 | `planetary_industry` | Producer | `asteroid_capture` |
| 35 | `terraforming_engines` | Producer | `planetary_industry` |
| 36 | `dyson_swarm_prototype` | Producer | `terraforming_engines` |
| 37 | `matrioshka_brain` | Producer | `dyson_swarm_prototype` |
| 38 | `stellar_energy` | Multiplier | `matrioshka_brain` |

## Problems checked for and not found

- **Duplicate IDs**: none.
- **Duplicate names**: none.
- **Unreachable technologies**: none — independent BFS closure from
  `natural_fire` over the `requires` graph reaches all 157.
- **Technologies whose prerequisites cannot be reached**: none (a
  consequence of the above — every `requires` entry names a technology
  that is itself reachable, transitively, from the start).
- **Orphan technologies** (technology nobody's `requires` list ever
  points at, i.e. nothing downstream depends on it): expected and common
  at leaf nodes (most producers/processors have no downstream tech
  requirement — they are consumed by the *resource* economy, not the tech
  graph). Not a defect; "unlocks nothing further" is the normal shape of
  most producers and every endgame terminal node
  (`matrioshka_brain`/`stellar_energy`).
- **Technologies purchasable "too early"**: not found as a graph property
  — every processor's inputs are supplied by a producer reachable no later
  than the processor itself (`EconomyValidationTest`'s
  `consumer-unlocks-before-its-supply` rule, 0 violations).
- **Permanently inaccessible technologies**: none.

## Kind/branch cross-tab

| Branch | Producers | Processors | Multipliers | Unlocks | Choices | Total |
| :-- | --: | --: | --: | --: | --: | --: |
| Agriculture | 7 | 0 | 0 | 3 | 0 | 10 |
| Chemistry | 5 | 5 | 2 | 1 | 0 | 13 |
| Construction | 5 | 0 | 2 | 0 | 0 | 7 |
| Digital | 4 | 0 | 2 | 1 | 0 | 7 |
| Electricity | 10 | 2 | 2 | 0 | 2 | 16 |
| Electronics | 0 | 4 | 2 | 2 | 0 | 8 |
| Endgame | 8 | 0 | 3 | 0 | 0 | 11 |
| Fossil Fuels | 7 | 1 | 2 | 2 | 0 | 12 |
| Globalization | 4 | 0 | 2 | 1 | 0 | 7 |
| Industry | 6 | 0 | 1 | 2 | 0 | 9 |
| Materials | 0 | 6 | 2 | 2 | 0 | 10 |
| Mining | 7 | 0 | 2 | 3 | 0 | 12 |
| Nuclear | 0 | 3 | 1 | 1 | 0 | 5 |
| Primitive | 5 | 0 | 1 | 4 | 0 | 10 |
| Space | 0 | 5 | 2 | 1 | 0 | 8 |
| Transportation | 10 | 0 | 2 | 0 | 0 | 12 |
| **Total** | 78 | 26 | 28 | 23 | 2 | 157 |
