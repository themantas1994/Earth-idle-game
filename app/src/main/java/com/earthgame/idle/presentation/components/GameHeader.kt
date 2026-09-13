package com.earthgame.idle.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.engine.RUN_LABEL
import com.earthgame.idle.domain.formatting.formatGameAge
import com.earthgame.idle.domain.formatting.formatNumber
import com.earthgame.idle.domain.formatting.formatPercent
import com.earthgame.idle.domain.formatting.formatRate
import com.earthgame.idle.domain.formatting.formatTemperature
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.RESOURCE_LIST
import com.earthgame.idle.presentation.theme.VisualEra
import com.earthgame.idle.presentation.theme.NumericTextStyle
import com.earthgame.idle.presentation.theme.gameColors

/** Test tag on the resource row's overflow affordance, for the UI tests. */
const val RESOURCE_SCROLL_HINT_TAG = "resource-scroll-hint"

/**
 * The persistent header: which Earth this is, how old it is, how hot it is,
 * whether it is still habitable — and, on every screen but Home, the resource
 * balances.
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
            Spacer(Modifier.weight(1f))
            // The planet's own age on the simulated calendar, not the player's
            // time at the controls — which is why it is labelled and sits
            // beside the Earth's name rather than among the live readouts.
            Text(
                text = "AGE",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                color = colors.textFaint,
                modifier = Modifier.semantics { contentDescription = "Earth age" },
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = formatGameAge(state.gameAgeSeconds, format),
                style = NumericTextStyle.copy(fontWeight = FontWeight.Bold),
                color = colors.text,
                // A four-figure age on a 320 dp phone is the tightest this row
                // ever gets; one line with an ellipsis is better than a header
                // that grows a second row. Past ten thousand years the
                // formatter switches to compact notation and it stops being
                // close.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                ResourceScrollRow {
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

/**
 * The horizontally scrollable resource row, with an affordance that appears
 * only when there is actually something past the right edge.
 *
 * Players were not discovering that the row scrolled: six balances do not fit a
 * phone, the last one ended flush with the screen edge, and a row that ends
 * cleanly looks finished. The fix is a soft fade plus a chevron over the right
 * edge, which reads as "there is more this way" without spending a line of
 * chrome on saying so.
 *
 * Both are driven by the scroll state's own `maxValue`, which is zero exactly
 * when the content fits, so a wide screen or a run with two resources shows
 * nothing at all.
 *
 * ## Reading the scroll position without recomposing on it
 *
 * A scroll offset changes every frame of a drag, so reading it straight from a
 * composable body recomposes the header on every one of those frames — which is
 * what `FrequentlyChangingValue` lints against. Two reads, two phases:
 *
 * - **Whether** to show the affordance is a [derivedStateOf] boolean, so a
 *   recomposition happens only on the two frames where it actually flips.
 * - **How strongly** to show it is read inside [graphicsLayer], a draw-phase
 *   lambda: the fade tracks the finger continuously without composition being
 *   involved at all. That also makes it smooth without an animation, so there
 *   is nothing for the reduced-animations setting to turn off.
 *
 * The affordance is drawn *over* the row and takes no input: it is painted in
 * an overlay sized with `matchParentSize`, so every pixel of the row remains
 * scrollable and nothing new intercepts a vertical swipe on its way to the page
 * beneath.
 */
@Composable
private fun ResourceScrollRow(content: @Composable () -> Unit) {
    val colors = gameColors
    val scrollState = rememberScrollState()

    // `maxValue` is Int.MAX_VALUE until the row has been measured. Treating
    // that as "scrollable" would flash the hint for one frame on every screen,
    // including the widths where everything fits — so an unmeasured row is
    // treated as fitting until it says otherwise.
    val canScroll by remember(scrollState) {
        derivedStateOf { scrollState.maxValue.let { it != Int.MAX_VALUE && it > 0 } }
    }
    val hasMoreToSee by remember(scrollState) {
        derivedStateOf {
            val max = scrollState.maxValue
            max != Int.MAX_VALUE && max > 0 && scrollState.value < max
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                // The chips already announce themselves one by one; naming the
                // row tells a screen-reader user it is a strip they can swipe
                // through, which is what the fade tells a sighted one.
                .semantics {
                    contentDescription = if (canScroll) {
                        "Resource balances, scroll sideways for more"
                    } else {
                        "Resource balances"
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }

        if (hasMoreToSee) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .testTag(RESOURCE_SCROLL_HINT_TAG),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(36.dp)
                        .graphicsLayer {
                            // Fades across the last quarter of the travel rather
                            // than only at the very end, so reaching the final
                            // chip does not leave a marker hanging over it and
                            // scrolling back brings it with you.
                            val max = scrollState.maxValue
                            val remaining = if (max > 0) (max - scrollState.value).toFloat() / max else 0f
                            alpha = (remaining * 4f).coerceIn(0f, 1f)
                        }
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    colors.surfaceElevated.copy(alpha = 0f),
                                    colors.surfaceElevated.copy(alpha = 0.95f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textDim,
                    )
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
