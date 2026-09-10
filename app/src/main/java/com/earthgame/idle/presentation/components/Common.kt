package com.earthgame.idle.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.LocalReducedAnimations
import com.earthgame.idle.presentation.theme.NumericTextStyle
import com.earthgame.idle.presentation.theme.gameColors

/**
 * The standard panel every screen is built from — the Compose equivalent of the
 * original build's `.card`.
 */
@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = gameColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.CardCorner))
            .background(colors.card)
            .border(1.dp, borderColor ?: colors.border, RoundedCornerShape(Dimens.CardCorner))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Dimens.CardPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = gameColors.text,
        modifier = modifier,
    )
}

/** A small-caps section heading between cards. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = gameColors.textFaint,
        modifier = modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
    )
}

/**
 * A label-and-value row, the workhorse of every readout in the game.
 *
 * The label and value are merged into one semantics node so TalkBack reads
 * "Habitability, 84%" rather than announcing two unrelated fragments.
 */
@Composable
fun StatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    trailing: String? = null,
) {
    val colors = gameColors
    val spoken = buildString {
        append(label)
        append(", ")
        append(value)
        trailing?.let { append(", "); append(it) }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textDim,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = NumericTextStyle,
                color = valueColor ?: colors.text,
            )
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = trailing,
                    style = NumericTextStyle.copy(fontWeight = FontWeight.Normal),
                    color = colors.textFaint,
                )
            }
        }
    }
}

/**
 * A horizontal progress bar.
 *
 * Animates toward its target unless the player has asked for reduced motion, in
 * which case it jumps — the setting is honoured here rather than at a dozen
 * call sites.
 */
@Composable
fun ProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 8.dp,
    trackColor: Color? = null,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated = if (LocalReducedAnimations.current) {
        target
    } else {
        animateFloatAsState(targetValue = target, label = "progress").value
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor ?: gameColors.border)
            // The bar is decoration for a value already announced next to it.
            .clearAndSetSemantics { },
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(color),
        )
    }
}

/** A small status pill — "OWNED", "COMPLETED", "IN PROGRESS". */
@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** The muted line shown where a list has nothing in it yet. */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = gameColors.textFaint,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp, horizontal = 12.dp),
    )
}

/** An emoji used purely as decoration, hidden from screen readers. */
@Composable
fun DecorativeIcon(emoji: String, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    Text(
        text = emoji,
        fontSize = size,
        modifier = modifier.clearAndSetSemantics { },
    )
}

/** A coloured dot, used to key a gas to its colour in a list. */
@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(9.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
            .clearAndSetSemantics { },
    )
}

/** Row spacing used between the segmented buttons on the shopping screens. */
@Composable
fun SegmentedRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
