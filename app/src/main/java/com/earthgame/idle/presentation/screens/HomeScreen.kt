package com.earthgame.idle.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.events.RANDOM_EVENT_BY_ID
import com.earthgame.idle.domain.formatting.formatFixed
import com.earthgame.idle.domain.formatting.formatNumber
import com.earthgame.idle.domain.formatting.formatPercent
import com.earthgame.idle.domain.formatting.formatRate
import com.earthgame.idle.domain.model.GAS_LIST
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.RESOURCE_LIST
import com.earthgame.idle.domain.technologies.ALL_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.TECH_BY_ID
import com.earthgame.idle.domain.technologies.TechKind
import com.earthgame.idle.domain.technologies.isTechAvailable
import com.earthgame.idle.presentation.components.CardTitle
import com.earthgame.idle.presentation.components.EmptyHint
import com.earthgame.idle.presentation.components.GameCard
import com.earthgame.idle.presentation.components.ProgressBar
import com.earthgame.idle.presentation.components.StatRow
import com.earthgame.idle.presentation.navigation.Destination
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors

/**
 * The dashboard: what to do next, how the planet is doing, what the economy is
 * earning, and the latest world news.
 */
@Composable
fun HomeScreen(
    state: GameState,
    derived: DerivedState,
    onNavigate: (Destination) -> Unit,
    onResetEarth: () -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val format = state.settings.numberFormat
    val habitabilityColor = colors.forHabitability(state.habitability.fraction)

    // The next technology never bought at all, rather than one already owned:
    // an infinitely repurchasable generator would otherwise stay "next" forever.
    val nextTech = ALL_TECHNOLOGIES.firstOrNull {
        (state.techOwned[it.id] ?: 0) == 0 &&
            isTechAvailable(it, state.techOwned) &&
            it.id !in derived.disabledTechIds
    }
    val objectiveScreen = if (nextTech?.kind == TechKind.GENERATOR) {
        Destination.PRODUCTION
    } else {
        Destination.TECHNOLOGY
    }
    val objective = when {
        state.collapsed -> "Earth is uninhabitable. Reset to begin the next civilization."
        nextTech == null -> "Keep producing — your economy is growing."
        // Generators sell on Production and everything else on Technology, so
        // the hint has to name the right tab or it sends the player to an empty
        // list.
        else -> "Next: ${nextTech.displayName} — ${objectiveScreen.title} tab"
    }

    // Resources the run has never touched stay hidden: an opening screen
    // listing five permanent zeroes teaches nothing and costs the space the
    // live numbers need.
    val visibleResources = remember(state.resources, derived.productionRates.resourcePerS) {
        RESOURCE_LIST.filter {
            !state.resources[it.id].isZero() || !derived.productionRates.resourcePerS[it.id].isZero()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        item {
            GameCard(onClick = { onNavigate(objectiveScreen) }) {
                CardTitle("🎯 Objective")
                Text(objective, style = MaterialTheme.typography.bodySmall, color = colors.textDim)
            }
        }

        item {
            GameCard {
                CardTitle("🌡️ Planetary Status")
                StatRow("Radiative Forcing", "+${formatFixed(state.forcing.total, 2)} W/m²")
                StatRow(
                    "Habitability",
                    formatPercent(state.habitability.fraction),
                    valueColor = habitabilityColor,
                )
                ProgressBar(
                    progress = state.habitability.fraction.toFloat().coerceAtLeast(0.02f),
                    color = habitabilityColor,
                )
            }
        }

        item {
            GameCard {
                CardTitle("⚙️ Economy")
                if (visibleResources.isEmpty()) {
                    EmptyHint("Nothing earned yet. Your fires are just getting going.")
                }
                for (resource in visibleResources) {
                    StatRow(
                        label = resource.displayName,
                        value = formatNumber(state.resources[resource.id], format),
                        trailing = "(${formatRate(derived.productionRates.resourcePerS[resource.id], "/s", format)})",
                    )
                }
            }
        }

        item {
            GameCard {
                CardTitle("🚀 Momentum")
                StatRow(
                    "Production multiplier",
                    "×${formatNumber(derived.effective.global, format)}",
                    valueColor = colors.accent,
                )
                val buildings = state.techOwned.entries.sumOf { (id, count) ->
                    if (TECH_BY_ID[id]?.kind == TechKind.GENERATOR) count else 0
                }
                StatRow("Buildings standing", formatNumber(buildings, format))

                for (active in state.activeEvents) {
                    val definition = RANDOM_EVENT_BY_ID[active.eventDefId] ?: continue
                    StatRow(
                        label = "${definition.icon} ${definition.displayName}",
                        value = "active",
                        valueColor = if (definition.isNegative) colors.danger else colors.good,
                    )
                }

                Text(
                    text = "Every multiplier you own compounds into this number, and nothing " +
                        "ever takes it away. Prices never move either — what a technology " +
                        "costs the first time you see it is what it costs whenever you come " +
                        "back for it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                )
            }
        }

        item {
            GameCard {
                CardTitle("☁️ Major Greenhouse Gases")
                val emitting = GAS_LIST.filter {
                    it.directlyEmitted && derived.productionRates.gasGrossKgPerS[it.id].gt(GameDecimal.ZERO)
                }
                if (emitting.isEmpty()) {
                    EmptyHint("Your fires are banked. Build more on the Production tab.")
                }
                for (gas in emitting) {
                    StatRow(
                        label = gas.formula,
                        value = formatRate(derived.productionRates.gasGrossKgPerS[gas.id], "kg/s", format),
                        valueColor = colors.forGas(gas.id),
                    )
                }
            }
        }

        item { NewsFeedCard(state = state, limit = 4) }

        item {
            Button(
                onClick = onResetEarth,
                enabled = state.collapsed,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.danger,
                    contentColor = colors.text,
                    disabledContainerColor = colors.border,
                    disabledContentColor = colors.textFaint,
                ),
            ) {
                Text(
                    text = if (state.collapsed) "☠️ RESET EARTH" else "Reset available once Earth collapses",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
