package com.earthgame.idle.presentation.screens

import androidx.compose.foundation.layout.Arrangement
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
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.events.RANDOM_EVENT_BY_ID
import com.earthgame.idle.domain.formatting.formatFixed
import com.earthgame.idle.domain.formatting.formatGameAge
import com.earthgame.idle.domain.formatting.formatNumber
import com.earthgame.idle.domain.formatting.formatPercent
import com.earthgame.idle.domain.formatting.formatRate
import com.earthgame.idle.domain.model.GAS_LIST
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.RESOURCE_LIST
import com.earthgame.idle.domain.technologies.ALL_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.TECH_BY_ID
import com.earthgame.idle.domain.technologies.TechBranch
import com.earthgame.idle.domain.technologies.TechKind
import com.earthgame.idle.domain.technologies.isTechAvailable
import com.earthgame.idle.presentation.components.CardTitle
import com.earthgame.idle.presentation.components.EmptyHint
import com.earthgame.idle.presentation.components.GameCard
import com.earthgame.idle.presentation.components.GlobeCard
import com.earthgame.idle.presentation.components.ProgressBar
import com.earthgame.idle.presentation.components.StatRow
import com.earthgame.idle.presentation.components.StormDetailCard
import com.earthgame.idle.presentation.components.StormImpactRows
import com.earthgame.idle.presentation.components.StormStatusStrip
import com.earthgame.idle.presentation.navigation.Destination
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors
import com.earthgame.idle.presentation.visualization.EnvironmentOverlay
import com.earthgame.idle.presentation.visualization.EnvironmentalVisualizationState
import kotlin.math.min

/**
 * Home: a live 3D visualization of the planet the simulation is running, with
 * the numbers that explain it directly beneath.
 *
 * ## What the globe is, and is not
 *
 * It is a **view**. Every value it draws — temperature, humidity, wind, gas
 * concentrations, storms, events, the planet's rotation — arrives as an
 * [EnvironmentalVisualizationState] built from `GameState` by
 * `environmentalVisualizationOf`. Nothing is computed for the globe's benefit
 * and nothing flows back from it. If the renderer fails, or the device has no
 * 3D at all, the flat fallback draws the same state and the screen below it is
 * unchanged.
 *
 * ## Layout
 *
 * Top to bottom: the globe, the overlay picker and legend, whatever is
 * currently happening to the planet, then the compact status the game has
 * always shown — objective, planetary status, economy, momentum, emissions,
 * news. The globe is the focus; it is never the whole screen, and nothing that
 * was on Home before has been taken away to make room for it.
 */
@Composable
fun HomeScreen(
    state: GameState,
    derived: DerivedState,
    environment: EnvironmentalVisualizationState,
    overlay: EnvironmentOverlay,
    onOverlayChange: (EnvironmentOverlay) -> Unit,
    selectedStormId: String?,
    onStormSelected: (String?) -> Unit,
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

    // The night side lights up as the civilization grows. A cheap, readable
    // proxy: the tech tree's own progress index, flattened so the last tiers
    // do not wash the planet out.
    val nightLights = remember(derived.civLevel) {
        min(1.0, derived.civLevel / 120.0).toFloat()
    }

    val selectedStorm = environment.storms.firstOrNull { it.id == selectedStormId }
    val stormDrag = derived.storms

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        item(key = "globe") {
            GlobeCard(
                environment = environment,
                overlay = overlay,
                onOverlayChange = onOverlayChange,
                quality = state.settings.graphicsQuality,
                reducedMotion = state.settings.reducedAnimations,
                nightLights = nightLights,
                focusStormId = selectedStormId,
                onStormSelected = onStormSelected,
            )
        }

        item(key = "phenomena") {
            GameCard {
                CardTitle("🌀 Active Phenomena")
                StormStatusStrip(
                    environment = environment,
                    selectedStormId = selectedStormId,
                    onSelectStorm = onStormSelected,
                )
                StormImpactRows(
                    globalPenalty = stormDrag.globalPenalty,
                    branchPenalties = TechBranch.entries
                        .mapNotNull { branch ->
                            val penalty = stormDrag.branchPenalty(branch)
                            if (penalty > 0.0005) branch.displayName to penalty else null
                        }
                        .sortedByDescending { it.second },
                )

                for (event in environment.events) {
                    StatRow(
                        label = "${event.icon} ${event.label}",
                        value = "active",
                        valueColor = if (event.isNegative) colors.danger else colors.good,
                    )
                }
                if (environment.events.isEmpty() && environment.storms.isEmpty()) {
                    Text(
                        text = "Nothing is happening to the planet right now. " +
                            "Storms begin to form once it warms.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textFaint,
                    )
                }
            }
        }

        if (selectedStorm != null) {
            item(key = "storm-detail") {
                StormDetailCard(
                    storm = selectedStorm,
                    onDismiss = { onStormSelected(null) },
                )
            }
        }

        item(key = "environment") {
            GameCard {
                CardTitle("🌍 Environment")
                StatRow(
                    "Temperature",
                    "+${formatFixed(state.temperatureAnomalyC, 2)} °C",
                    valueColor = colors.forHabitability(state.habitability.fraction),
                )
                StatRow("Humidity", formatPercent(environment.humidity.toDouble(), 0))
                StatRow("Wind", formatPercent(environment.windStrength.toDouble(), 0))
                StatRow("Atmosphere", describeAtmosphere(environment.atmosphericOpacity))
                StatRow(
                    "Storms",
                    when (environment.storms.size) {
                        0 -> "None active"
                        1 -> "1 active"
                        else -> "${environment.storms.size} active"
                    },
                    valueColor = if (environment.storms.isEmpty()) colors.textDim else colors.warning,
                )
                StatRow(
                    "Habitability",
                    formatPercent(state.habitability.fraction),
                    valueColor = habitabilityColor,
                )
            }
        }

        item(key = "objective") {
            GameCard(onClick = { onNavigate(objectiveScreen) }) {
                CardTitle("🎯 Objective")
                Text(objective, style = MaterialTheme.typography.bodySmall, color = colors.textDim)
            }
        }

        item(key = "status") {
            GameCard {
                CardTitle("🌡️ Planetary Status")
                // Simulated time, not time played: this is how long this Earth
                // has existed, and the span its greenhouse gases have been
                // decaying over.
                StatRow("Earth Age", formatGameAge(state.gameAgeSeconds, format))
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

        item(key = "economy") {
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

        item(key = "momentum") {
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
                        "back for it. Storms are the one exception, and only while they last.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                )
            }
        }

        item(key = "gases") {
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

        item(key = "news") { NewsFeedCard(state = state, limit = 4) }

        item(key = "reset") {
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

/** The atmosphere in one word, so the readout is not only a colour on the globe. */
private fun describeAtmosphere(opacity: Float): String = when {
    opacity < 0.08f -> "Pristine"
    opacity < 0.25f -> "Thickening"
    opacity < 0.5f -> "Elevated"
    opacity < 0.75f -> "Heavy"
    else -> "Choked"
}
