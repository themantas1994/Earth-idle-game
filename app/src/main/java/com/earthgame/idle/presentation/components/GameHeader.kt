package com.earthgame.idle.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.engine.RUN_LABEL
import com.earthgame.idle.domain.formatting.formatNumber
import com.earthgame.idle.domain.formatting.formatPercent
import com.earthgame.idle.domain.formatting.formatRate
import com.earthgame.idle.domain.formatting.formatTemperature
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.RESOURCE_LIST
import com.earthgame.idle.presentation.theme.VisualEra
import com.earthgame.idle.presentation.theme.NumericTextStyle
import com.earthgame.idle.presentation.theme.gameColors

/**
 * The persistent header: which Earth this is, how hot it is, whether it is
 * still habitable — and, on every screen but Home, the resource balances.
 *
 * The balances are here because every other tab asks the player to spend:
 * having them only on Home meant bouncing back and forth to answer "can I
 * afford this yet?".
 *
 * Its background tint tracks the planet's era, so a run reads as a slow slide
 * from green to burning red without the player having to watch a number.
 */
@Composable
fun GameHeader(
    state: GameState,
    derived: DerivedState,
    showResources: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val format = state.settings.numberFormat
    val era = VisualEra.of(state.temperatureAnomalyC, state.collapsed)
    val habitabilityColor = colors.forHabitability(state.habitability.fraction)

    val status = when {
        state.habitability.fraction > 0.5 -> "Stable"
        state.habitability.fraction > 0.15 -> "Strained"
        state.habitability.fraction > 0 -> "Critical"
        else -> "Collapsed"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(era.tint, colors.surfaceElevated)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "🌍 $RUN_LABEL${if (state.runNumber > 0) " ${state.runNumber}" else ""}",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Run #${state.runNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textFaint,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatChip("Temp", formatTemperature(state.temperatureAnomalyC), colors.text, Modifier.weight(1f))
            StatChip("Habitability", formatPercent(state.habitability.fraction), habitabilityColor, Modifier.weight(1f))
            StatChip("Status", status, habitabilityColor, Modifier.weight(1f))
        }

        if (showResources) {
            val visible = RESOURCE_LIST.filter {
                !state.resources[it.id].isZero() || !derived.productionRates.resourcePerS[it.id].isZero()
            }
            if (visible.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (resource in visible) {
                        StatChip(
                            label = resource.shortName,
                            value = formatNumber(state.resources[resource.id], format),
                            valueColor = colors.text,
                            trailing = formatRate(derived.productionRates.resourcePerS[resource.id], "/s", format),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val colors = gameColors
    val spoken = buildString {
        append(label); append(", "); append(value)
        trailing?.let { append(", "); append(it) }
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.card)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            color = colors.textFaint,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = NumericTextStyle.copy(fontWeight = FontWeight.Bold),
                color = valueColor,
            )
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = colors.textFaint,
                )
            }
        }
    }
}
