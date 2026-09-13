package com.earthgame.idle.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earthgame.idle.domain.formatting.formatFixed
import com.earthgame.idle.domain.formatting.formatPercent
import com.earthgame.idle.domain.model.GraphicsQuality
import com.earthgame.idle.presentation.globe.GlobeSurface
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors
import com.earthgame.idle.presentation.visualization.EnvironmentOverlay
import com.earthgame.idle.presentation.visualization.EnvironmentalVisualizationState

/** Height of the globe itself. Tall enough to be the focus, short enough that Home is still Home. */
private val GlobeHeight = 300.dp

/**
 * The Home screen's centrepiece: the planet, the overlay picker, and the
 * legend that says what the colours mean.
 *
 * The globe is the visual focus but never the whole screen — the environmental
 * summary, the objective and the economy all stay visible beneath it, which is
 * the portrait, one-handed shape the rest of the game is built in.
 */
@Composable
fun GlobeCard(
    environment: EnvironmentalVisualizationState,
    overlay: EnvironmentOverlay,
    onOverlayChange: (EnvironmentOverlay) -> Unit,
    quality: GraphicsQuality,
    reducedMotion: Boolean,
    nightLights: Float,
    focusStormId: String?,
    onStormSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    GameCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(GlobeHeight)
                .clip(RoundedCornerShape(Dimens.CardCorner))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF060A14), Color(0xFF0C1526), Color(0xFF060A14)),
                    ),
                ),
        ) {
            GlobeSurface(
                environment = environment,
                overlay = overlay,
                quality = quality,
                reducedMotion = reducedMotion,
                nightLights = nightLights,
                focusStormId = focusStormId,
                onStormSelected = onStormSelected,
                contentDescription = globeDescription(environment, overlay),
                modifier = Modifier.fillMaxWidth().height(GlobeHeight),
            )
        }

        OverlaySelector(selected = overlay, onSelect = onOverlayChange)
        OverlayLegend(overlay = overlay, environment = environment)
    }
}

/**
 * The overlay picker.
 *
 * A scrolling pill row rather than a grid: six options do not fit across a
 * 320 dp phone at a legible size, and stacking them would cost the globe the
 * height it needs.
 */
@Composable
fun OverlaySelector(
    selected: EnvironmentOverlay,
    onSelect: (EnvironmentOverlay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (option in EnvironmentOverlay.entries) {
            val active = option == selected
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) colors.accent.copy(alpha = 0.20f) else colors.surfaceElevated)
                    .clickable { onSelect(option) }
                    .heightIn(min = 34.dp)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
                    .semantics {
                        role = Role.Tab
                        this.selected = active
                        contentDescription = "${option.label} overlay. ${option.description}"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(option.icon, modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(5.dp))
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) colors.accent else colors.textDim,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/**
 * The legend.
 *
 * Every overlay states its own scale in words as well as in colour, and prints
 * the value it is currently showing. A player who cannot distinguish the ramp —
 * or who is looking at the flat fallback globe — still learns the same thing
 * from the line of text.
 */
@Composable
fun OverlayLegend(
    overlay: EnvironmentOverlay,
    environment: EnvironmentalVisualizationState,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val reading = overlayReading(overlay, environment)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "${overlay.label} scale, ${overlay.legendLow} to ${overlay.legendHigh}. $reading"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = overlay.legendLow,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textFaint,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(legendGradient(overlay))),
            )
            Text(
                text = overlay.legendHigh,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textFaint,
            )
        }
        Text(
            text = reading,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textDim,
        )
    }
}

private fun legendGradient(overlay: EnvironmentOverlay): List<Color> = when (overlay) {
    EnvironmentOverlay.TEMPERATURE -> listOf(
        Color(0xFF3378DB), Color(0xFF4CBD93), Color(0xFFF7B528), Color(0xFFED331D),
    )
    EnvironmentOverlay.HUMIDITY -> listOf(Color(0xFF3D6E86), Color(0xFFD9ECFA))
    EnvironmentOverlay.WIND -> listOf(Color(0xFF23506E), Color(0xFF7FC6E8))
    EnvironmentOverlay.ATMOSPHERE -> listOf(Color(0xFF59A8FF), Color(0xFFFF8C38))
    EnvironmentOverlay.EVENTS -> listOf(Color(0xFF48C774), Color(0xFFF5654C))
    EnvironmentOverlay.STORMS -> listOf(Color(0xFF4A5A6B), Color(0xFFEEF3F8))
}

/** What the selected overlay currently reads, in words and numbers. */
private fun overlayReading(
    overlay: EnvironmentOverlay,
    environment: EnvironmentalVisualizationState,
): String = when (overlay) {
    EnvironmentOverlay.TEMPERATURE ->
        "Now +${formatFixed(environment.temperatureAnomalyC, 2)} °C above pre-industrial."

    EnvironmentOverlay.HUMIDITY ->
        "Now ${formatPercent(environment.humidity.toDouble(), 0)} relative humidity."

    EnvironmentOverlay.WIND ->
        "Now ${describeWind(environment.windStrength)} — " +
            "${formatPercent(environment.windStrength.toDouble(), 0)} of the modelled maximum."

    EnvironmentOverlay.ATMOSPHERE -> {
        val leader = environment.gasContributions.firstOrNull()
        if (leader == null) {
            "No greenhouse forcing yet. The atmosphere is as it was found."
        } else {
            "${leader.formula} leads, ${formatPercent(leader.share.toDouble(), 0)} of the warming."
        }
    }

    EnvironmentOverlay.EVENTS -> when (environment.events.size) {
        0 -> "Nothing unusual happening anywhere."
        1 -> "One world event running."
        else -> "${environment.events.size} world events running."
    }

    EnvironmentOverlay.STORMS -> when {
        environment.storms.isNotEmpty() ->
            "${environment.storms.size} active. Tap one for its effects."
        environment.stormRisk <= 0f ->
            "Clear skies. The planet is too cool to form storms."
        else ->
            "No storms right now. Storm risk ${formatPercent(environment.stormRisk.toDouble(), 0)}."
    }
}

private fun describeWind(strength: Float): String = when {
    strength < 0.25f -> "light"
    strength < 0.45f -> "moderate"
    strength < 0.7f -> "strong"
    else -> "gale-force"
}

/**
 * The globe's spoken description.
 *
 * TalkBack cannot read a shader, so this is the whole planet in one sentence:
 * what overlay is showing, how warm and humid it is, how windy, what is
 * happening on it. See `docs/wiki/Accessibility.md`.
 */
internal fun globeDescription(
    environment: EnvironmentalVisualizationState,
    overlay: EnvironmentOverlay,
): String = buildString {
    append("Interactive globe showing the ${overlay.label.lowercase()} overlay. ")
    append("Temperature +${formatFixed(environment.temperatureAnomalyC, 1)} degrees Celsius. ")
    append("Humidity ${formatPercent(environment.humidity.toDouble(), 0)}. ")
    append("Wind ${describeWind(environment.windStrength)}. ")
    append("Habitability ${formatPercent(environment.habitability.toDouble(), 0)}. ")
    append(
        when (environment.storms.size) {
            0 -> "No active storms. "
            1 -> "One active storm. "
            else -> "${environment.storms.size} active storms. "
        },
    )
    if (environment.events.isNotEmpty()) {
        append("${environment.events.size} world events running. ")
    }
    append("Drag to rotate, pinch to zoom.")
}
