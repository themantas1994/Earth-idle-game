package com.earthgame.idle.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.earthgame.idle.domain.formatting.formatDuration
import com.earthgame.idle.domain.formatting.formatNumber
import com.earthgame.idle.domain.formatting.formatTemperature
import com.earthgame.idle.domain.model.GAS_LIST
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.presentation.components.GameCard
import com.earthgame.idle.presentation.components.SectionLabel
import com.earthgame.idle.presentation.components.StatRow
import com.earthgame.idle.presentation.theme.Dimens

@Composable
fun StatisticsScreen(
    state: GameState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val format = state.settings.numberFormat
    val lifetime = state.lifetimeStats
    val totalGhg = lifetime.totalGasProducedKg.sum()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        item { SectionLabel("Lifetime Statistics") }
        item {
            GameCard {
                StatRow("Current Earth", "#${state.runNumber}")
                StatRow("Total Resets", formatNumber(lifetime.totalResets, format))
                StatRow("Total Play Time", formatDuration(lifetime.totalPlayTimeSeconds))
                StatRow("Longest Run", formatDuration(lifetime.longestRunSeconds))
                StatRow(
                    "Fastest Reset",
                    lifetime.fastestResetSeconds?.let { formatDuration(it) } ?: "—",
                )
                StatRow("Technologies Purchased", formatNumber(lifetime.totalTechnologiesPurchased, format))
                StatRow("Total Earth Points Earned", formatNumber(lifetime.totalEarthPointsEarned, format))
            }
        }

        item { SectionLabel("Climate Records") }
        item {
            GameCard {
                StatRow("Highest Temperature", formatTemperature(lifetime.highestTemperatureC))
                StatRow("Highest CO₂ Concentration", "${formatNumber(lifetime.highestCo2Ppm, format)} ppm")
                StatRow("Total Greenhouse Gas Produced", "${formatNumber(totalGhg, format)} kg")
                for (gas in GAS_LIST.filter { it.directlyEmitted }) {
                    StatRow(
                        "Total ${gas.formula} Produced",
                        "${formatNumber(lifetime.totalGasProducedKg[gas.id], format)} kg",
                    )
                }
            }
        }

        item { SectionLabel("This Earth") }
        item {
            GameCard {
                StatRow("Peak Temperature", formatTemperature(state.runStats.peakTemperatureC))
                StatRow("Peak CO₂", "${formatNumber(state.runStats.peakCo2Ppm, format)} ppm")
                StatRow(
                    "Peak Emission Rate",
                    "${formatNumber(state.runStats.peakGasProductionRateKgPerS, format)} kg/s",
                )
                StatRow("Peak Radiative Forcing", "${formatNumber(state.runStats.peakForcingWm2, format)} W/m²")
                StatRow("Headlines This Run", formatNumber(state.newsFeed.size, format))
            }
        }
    }
}
