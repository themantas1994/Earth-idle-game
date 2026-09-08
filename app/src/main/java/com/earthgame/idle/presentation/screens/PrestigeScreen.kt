package com.earthgame.idle.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthgame.idle.domain.engine.PRESTIGE_CURRENCY_NAME
import com.earthgame.idle.domain.engine.PRESTIGE_CURRENCY_SHORT
import com.earthgame.idle.domain.formatting.formatNumber
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.prestige.PRESTIGE_UPGRADES
import com.earthgame.idle.domain.prestige.PrestigeUpgrade
import com.earthgame.idle.domain.prestige.prestigeUpgradeCost
import com.earthgame.idle.presentation.components.Badge
import com.earthgame.idle.presentation.components.CardTitle
import com.earthgame.idle.presentation.components.DecorativeIcon
import com.earthgame.idle.presentation.components.GameCard
import com.earthgame.idle.presentation.components.SectionLabel
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors

/** The permanent upgrade tree bought with Earth Points. */
@Composable
fun PrestigeScreen(
    state: GameState,
    onBuyUpgrade: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val format = state.settings.numberFormat
    val points = state.prestige.earthPoints

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        item {
            GameCard {
                CardTitle("✨ $PRESTIGE_CURRENCY_NAME")
                Text(
                    text = formatNumber(points, format),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Earned by destroying Earths. Spend permanently to accelerate every future run.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item { SectionLabel("Permanent Upgrades") }

        items(PRESTIGE_UPGRADES, key = { it.id }) { upgrade ->
            val level = state.prestige.upgradesOwned[upgrade.id] ?: 0
            val maxed = level >= upgrade.maxLevel
            val cost = prestigeUpgradeCost(upgrade, level)
            val canAfford = points.gte(cost)

            GameCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DecorativeIcon(upgrade.icon, 17.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = upgrade.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    if (level > 0) {
                        Badge(
                            text = if (upgrade.maxLevel == PrestigeUpgrade.UNLIMITED_LEVELS) {
                                "Lv.$level"
                            } else {
                                "Lv.$level/${upgrade.maxLevel}"
                            },
                            color = colors.accent,
                        )
                    }
                }

                Text(
                    text = upgrade.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                )

                if (maxed) {
                    Badge("MAXED", colors.good)
                } else {
                    Button(
                        onClick = { onBuyUpgrade(upgrade.id) },
                        enabled = canAfford,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.MinTouchTarget),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentStrong,
                            contentColor = colors.background,
                            disabledContainerColor = colors.border,
                            disabledContentColor = colors.textFaint,
                        ),
                    ) {
                        Text(
                            text = "Buy — ${formatNumber(cost, format)} $PRESTIGE_CURRENCY_SHORT",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }
}
