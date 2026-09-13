package com.earthgame.idle.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.earthgame.idle.domain.climate.computeSinkEfficiency
import com.earthgame.idle.domain.climate.gasDisplayConcentration
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.formatting.formatFixed
import com.earthgame.idle.domain.formatting.formatNumber
import com.earthgame.idle.domain.formatting.formatRate
import com.earthgame.idle.domain.model.GAS_LIST
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.presentation.components.CardTitle
import com.earthgame.idle.presentation.components.ColorDot
import com.earthgame.idle.presentation.components.GameCard
import com.earthgame.idle.presentation.components.ProgressBar
import com.earthgame.idle.presentation.components.SectionLabel
import com.earthgame.idle.presentation.components.StatRow
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Per-gas concentrations, what each is doing to the temperature, and the ocean. */
@Composable
fun AtmosphereScreen(
    state: GameState,
    derived: DerivedState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val format = state.settings.numberFormat

    val maxAbsForcing = max(0.001, GAS_LIST.maxOf { abs(state.forcing.perGas[it.id]) })
    val sinkEfficiency = computeSinkEfficiency(state.temperatureAnomalyC)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        item { SectionLabel("Gas Concentrations") }

        items(GAS_LIST, key = { it.id.id }) { gas ->
            val forcing = state.forcing.perGas[gas.id]
            val production = derived.productionRates.gasGrossKgPerS[gas.id]
            val removal = derived.productionRates.gasRemovalKgPerS[gas.id]

            GameCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(colors.forGas(gas.id))
                    Spacer(Modifier.width(7.dp))
                    CardTitle("${gas.displayName} (${gas.formula})")
                }

                StatRow(
                    "Concentration",
                    "${formatNumber(gasDisplayConcentration(gas.id, state.atmosphere), format, precision = 3)} ${gas.unit.label}",
                )

                if (gas.directlyEmitted) {
                    StatRow("Production", formatRate(production, "kg/s", format))
                    if (removal.gt(GameDecimal.ZERO)) {
                        StatRow("Engineered Removal", formatRate(removal, "kg/s", format))
                    }
                    // Simulated years — the half-life is the span over which
                    // an untended stock of this gas halves, which is the number
                    // that decides whether emissions accumulate or drain.
                    StatRow("Half-Life", "${formatNumber(gas.halfLifeYears, format)} yr")
                } else {
                    StatRow("Source", "Warming feedback")
                }

                StatRow(
                    "Heating Contribution",
                    "${if (forcing >= 0) "+" else ""}${formatFixed(forcing, 3)} W/m²",
                )
                ProgressBar(
                    progress = min(1.0, abs(forcing) / maxAbsForcing).toFloat(),
                    color = colors.forGas(gas.id),
                    height = 6.dp,
                )
            }
        }

        item { SectionLabel("Atmospheric Heating") }
        item {
            GameCard {
                for (gas in GAS_LIST) {
                    val forcing = state.forcing.perGas[gas.id]
                    StatRow(gas.formula, "${if (forcing >= 0) "+" else ""}${formatFixed(forcing, 2)} W/m²")
                }
                StatRow(
                    label = "Total Radiative Forcing",
                    value = "+${formatFixed(state.forcing.total, 2)} W/m²",
                    valueColor = colors.accent,
                )
            }
        }

        item { SectionLabel("Carbon Cycle") }
        item {
            GameCard {
                StatRow("Natural Sink Efficiency", "${formatFixed(sinkEfficiency * 100, 1)}%")
                Text(
                    text = "Oceans, soil and vegetation absorb a shrinking share of emissions " +
                        "as the planet warms — sinks weaken as temperature rises.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                )
            }
        }

        item { SectionLabel("Ocean & Sea Level") }
        item {
            GameCard {
                StatRow("Ocean pH", formatFixed(state.oceanPh, 2))
                StatRow("Sea Level Rise", "+${formatFixed(state.seaLevelRiseMeters, 2)} m")
            }
        }

        item { SectionLabel("This Earth, in the News") }
        item { NewsFeedCard(state = state) }
    }
}
