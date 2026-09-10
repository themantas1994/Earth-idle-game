# Glossary

[← Documentation home](Home.md)

Terms the game and the codebase use, in one place. Game terms first.

## Game terms

| Term | Means |
| :-- | :-- |
| **Earth** / **run** | One playthrough, from a fresh planet to collapse. `runNumber` counts them: 0 is "EARTH", 1 is "EARTH 1". |
| **Earth Points (EP)** | The prestige currency. Earned when a run ends, spent on permanent upgrades, never reset. |
| **Prestige** | The reset loop: end a run, bank Earth Points, start over stronger. |
| **Collapse** | Habitability reaching zero. Latches `GameState.collapsed`, which unlocks the Reset button. |
| **Habitability** | A 0–1 score: five factors multiplied. The game's fail condition. |
| **Generator** | A repeatably purchasable technology that produces gas and/or resources per owned unit. 68 of them. |
| **Research node** | A one-time technology — unlock, multiplier or choice. 31 of them. |
| **Ownership bonus** | Every 10th copy of one generator doubles that building's output, permanently, for the rest of the run. |
| **Civilization level** | `Σ (tier + 1)` over distinct owned technologies. The overall progress score. |
| **Forcing** | Radiative forcing in W/m² — how hard the atmosphere is pushing the temperature. |
| **Anomaly** | Temperature *above* the pre-industrial baseline, in °C. The game never shows an absolute temperature. |
| **Baseline** | A gas's pre-industrial concentration. The atmosphere tracks only the amount *above* it. |
| **Sink** | Natural removal of a gas from the atmosphere. Weakens as the planet warms. |
| **Engineered removal** | Deliberate removal — carbon capture, reforestation, terraforming engines. |
| **Milestone / headline** | A deterministic news item that fires once per run and does nothing mechanically. 39 of them. |
| **Random event** | A short, chance-rolled multiplier swing. 13 of them, only two negative. |
| **Challenge** | A run with a live restriction, for a permanent reward. 8 of them. |
| **Offline cap** | The most time a single absence can bank. 12 hours base, up to 8 days with upgrades. |

## Codebase terms

| Term | Means |
| :-- | :-- |
| **`GameState`** | The immutable snapshot that is one playthrough. [Reference](Game-State.md). |
| **`GameDecimal`** | The arbitrary-scale number type: `sign × mantissa × 10^exponent`. [Reference](GameDecimal.md). |
| **`simulateStep(state, dt)`** | The physics. Pure, deterministic, step-size independent. [Reference](Simulation.md). |
| **`GameLoop`** | The session rules above the physics: absences, events, headlines, achievements, purchases, prestige. [Reference](Game-Engine.md). |
| **`DerivedState`** | Everything expensive both the tick and the UI need, computed once per state change. |
| **`EffectiveMultipliers`** | Prestige + technology + event multipliers folded into one bundle. |
| **`ProductionRates`** | Gross gas, engineered removal, and resources — all per second. |
| **`PrestigeMultipliers`** | Aggregated prestige upgrades and challenge rewards. |
| **`MultiplierContribution`** | A transient multiplier layer — an event or a challenge reward. |
| **`GameUiState`** | `GameState` + `DerivedState` + the transient UI slots. The single `StateFlow` value. |
| **`LoadResult`** | `Empty` / `Loaded` / `RecoveredFromBackup` / `Corrupted`. Provenance matters to the UI. |
| **`SAVE_VERSION`** | The save format version (currently 3). Unrelated to `versionCode`. |
| **`stateLock`** | The lock serialising the tick against player actions in the ViewModel. |
| **`ladderTier(tier)`** | The compressed tier used by every cost and production curve above the knee. |
| **Nominalized wallet** | Balances restated in the units the cost curves use, so affordability maths need not know the prestige discount. |
| **Parity test** | A test asserting agreement with the TypeScript reference's captured output. [Reference](Reference-Parity.md). |
| **Fixture** | A committed JSON file of the reference engine's actual answers. |
| **The reference / the oracle** | `tools/ts-reference/` — the frozen original engine. |
| **Step-size independence** | One call with `dt = 8h` equalling 115,200 calls with `dt = 250ms`. The property offline progress rests on. |
| **The price-stability invariant** | A price never rises except through your own purchases of that exact thing. [Reference](Economy-and-Production.md#the-one-invariant). |
| **`VisualEra`** | The header's temperature-driven background tint. |
| **Strong skipping** | The Compose compiler behaviour that lets a composable with unstable parameters still skip, comparing by reference identity. |

## Units

| | |
| :-- | :-- |
| **ppm / ppb / ppt** | Parts per million / billion / trillion, by volume. CO₂ and H₂O in ppm; CH₄ and N₂O in ppb; fluorinated gases in ppt. |
| **DU** | Dobson units, for ozone. |
| **W/m²** | Watts per square metre — radiative forcing. |
| **kg/s** | The unit generators emit in. Converted to concentration through each gas's `massPerUnit`. |
| **K, M, B, T, Qa, … Vg, AA, AB …** | Compact suffixes. Named short-scale to `Vg` (1e63), then two-letter alphabetic. |

---

**Next:** [FAQ](FAQ.md) · [Documentation home](Home.md)
