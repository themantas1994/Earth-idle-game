package com.earthgame.idle.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthgame.idle.domain.formatting.formatDuration
import com.earthgame.idle.domain.formatting.formatPercent
import com.earthgame.idle.presentation.theme.gameColors
import com.earthgame.idle.presentation.visualization.EnvironmentalVisualizationState
import com.earthgame.idle.presentation.visualization.StormVisual

/**
 * The storm status strip: how many storms are running, and a way into them.
 *
 * Shows nothing alarming when there is nothing to be alarmed about — an idle
 * game that keeps a red badge on screen at all times has taught the player to
 * ignore it. With clear skies this is one quiet line, or, once the planet is
 * warm enough to make storms, the current risk.
 */
@Composable
fun StormStatusStrip(
    environment: EnvironmentalVisualizationState,
    selectedStormId: String?,
    onSelectStorm: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val storms = environment.storms

    if (storms.isEmpty()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = if (environment.stormRisk <= 0f) {
                        "No active storms. The planet is too cool to form them."
                    } else {
                        "No active storms. Storm risk " +
                            formatPercent(environment.stormRisk.toDouble(), 0) + "."
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🌤️", modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(7.dp))
            Text(
                text = if (environment.stormRisk <= 0f) {
                    "No storms — the planet is too cool to form them"
                } else {
                    "No storms — risk ${formatPercent(environment.stormRisk.toDouble(), 0)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim,
            )
        }
        return
    }

    val severe = storms.count { it.intensity >= 0.5f }
    val headline = when (storms.size) {
        1 -> "1 ACTIVE STORM"
        else -> "${storms.size} ACTIVE STORMS"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = headline,
                style = MaterialTheme.typography.labelSmall,
                color = if (severe > 0) colors.warning else colors.textDim,
                fontWeight = FontWeight.Bold,
            )
            if (severe > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "· $severe severe or worse",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                )
            }
        }

        // One chip per storm. Tapping focuses the globe on it and opens its
        // card; tapping it again hands the camera back.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (storm in storms.take(4)) {
                val active = storm.id == selectedStormId
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (active) colors.accent.copy(alpha = 0.18f) else colors.surfaceElevated,
                        )
                        .clickable { onSelectStorm(if (active) null else storm.id) }
                        .heightIn(min = 36.dp)
                        .padding(horizontal = 7.dp, vertical = 6.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Focus on ${storm.accessibleSummary}"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(storm.icon, fontSize = 13.sp, modifier = Modifier.clearAndSetSemantics { })
                    Spacer(Modifier.width(5.dp))
                    Column(Modifier.clearAndSetSemantics { }) {
                        Text(
                            text = storm.displayName.substringAfterLast(' '),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.text,
                            maxLines = 1,
                        )
                        Text(
                            text = storm.severityLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = severityColour(storm.intensity, colors.good, colors.warning, colors.danger),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (storms.size > 4) {
                Text(
                    text = "+${storms.size - 4}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

/**
 * The card for one storm: what it is, how strong, where, how long left, and —
 * the part that matters — exactly what it is costing right now.
 *
 * Effects are printed as numbers, never implied by the size of the spiral on
 * the globe. Deliberately compact: it sits under the globe rather than over it,
 * so selecting a storm never hides the thing being described.
 */
@Composable
fun StormDetailCard(
    storm: StormVisual,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val severityColor = severityColour(storm.intensity, colors.good, colors.warning, colors.danger)

    GameCard(modifier = modifier, borderColor = severityColor.copy(alpha = 0.55f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(storm.icon, fontSize = 17.sp, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = storm.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                )
                Text(
                    text = "${storm.severityLabel} · ${storm.locationLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = severityColor,
                )
            }
            Text(
                text = "✕",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textFaint,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Close the storm card"
                    },
            )
        }

        StatRow("Intensity", "${(storm.intensity * 100).toInt()}% — ${storm.severityLabel}")
        StatRow("Duration remaining", formatDuration(storm.remainingSeconds))

        Text(
            text = "CURRENT EFFECTS",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textFaint,
        )

        if (storm.globalPenalty <= 0.0005 && storm.branchPenalties.isEmpty()) {
            Text(
                text = "Still organising — no measurable effect on production yet.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim,
            )
        } else {
            if (storm.globalPenalty > 0.0005) {
                StatRow(
                    label = "All production",
                    value = formatPenalty(storm.globalPenalty),
                    valueColor = colors.danger,
                )
            }
            for ((branch, penalty) in storm.branchPenalties) {
                StatRow(
                    label = branch,
                    value = formatPenalty(penalty),
                    valueColor = colors.danger,
                )
            }
        }

        Text(
            text = "Storm penalties are temporary and stack with diminishing returns. " +
                "Nothing is lost permanently — production returns in full when it passes.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textFaint,
        )
    }
}

/**
 * What every live storm together is costing, after stacking and the caps.
 *
 * Separate from the per-storm card because the two answer different questions:
 * one is "what is Iris doing", the other is "why is my output down".
 */
@Composable
fun StormImpactRows(
    globalPenalty: Double,
    branchPenalties: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    if (globalPenalty <= 0.0005 && branchPenalties.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (globalPenalty > 0.0005) {
            StatRow("Weather drag, all production", formatPenalty(globalPenalty), valueColor = colors.danger)
        }
        for ((branch, penalty) in branchPenalties) {
            StatRow("Weather drag, $branch", formatPenalty(penalty), valueColor = colors.danger)
        }
    }
}

/** "−12%", with a real minus sign rather than a hyphen. */
fun formatPenalty(penalty: Double): String = "−${(penalty * 100 + 0.5).toInt()}%"

private fun severityColour(intensity: Float, good: Color, warning: Color, danger: Color): Color = when {
    intensity < 0.25f -> good
    intensity < 0.5f -> warning
    else -> danger
}
