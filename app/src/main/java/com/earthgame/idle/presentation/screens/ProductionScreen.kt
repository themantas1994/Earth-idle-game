package com.earthgame.idle.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.OWNERSHIP_BONUS
import com.earthgame.idle.domain.engine.computeTechProductionRates
import com.earthgame.idle.domain.formatting.formatRate
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.gasOf
import com.earthgame.idle.domain.model.resourceOf
import com.earthgame.idle.domain.technologies.GENERATOR_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.isTechAvailable
import com.earthgame.idle.presentation.BuyQuantity
import com.earthgame.idle.presentation.components.EmptyHint
import com.earthgame.idle.presentation.components.SegmentedRow
import com.earthgame.idle.presentation.components.TechCard
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors

/**
 * Every generator in the game, and the only screen that sells them.
 *
 * Generators whose prerequisites are not met yet are still listed — rendered
 * locked, with their requirements — so the branch the player is researching
 * shows what it will actually unlock.
 */
@Composable
fun ProductionScreen(
    state: GameState,
    derived: DerivedState,
    quantity: BuyQuantity,
    onQuantityChange: (BuyQuantity) -> Unit,
    onBuy: (String, BuyQuantity) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val format = state.settings.numberFormat
    val unlockedCount = GENERATOR_TECHNOLOGIES.count {
        (state.techOwned[it.id] ?: 0) > 0 || isTechAvailable(it, state.techOwned)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        item {
            SegmentedRow {
                for (option in BuyQuantity.entries) {
                    FilterChip(
                        selected = option == quantity,
                        onClick = { onQuantityChange(option) },
                        label = { Text(option.label) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Dimens.MinTouchTarget),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentStrong,
                            selectedLabelColor = colors.background,
                            labelColor = colors.textDim,
                        ),
                    )
                }
            }
        }

        item {
            Text(
                text = "Every building's price is fixed the moment you see it. Only the copies " +
                    "you buy of a building make that building's next copy dearer — and every " +
                    "${OWNERSHIP_BONUS.everyUnits}th copy doubles its output.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (unlockedCount == 0) {
            item { EmptyHint("Nothing buildable yet — research a technology that unlocks one.") }
        }

        items(GENERATOR_TECHNOLOGIES, key = { it.id }) { tech ->
            val owned = state.techOwned[tech.id] ?: 0

            if (owned > 0) {
                // This building's own contribution, not the global rate for the
                // gases it happens to emit — those are on Home and Atmosphere.
                val mine = computeTechProductionRates(tech, owned, derived.effective)
                val outputs = buildList {
                    for (gas in tech.effect.gasProductionPerUnit.keys) {
                        val rate = mine.gasGrossKgPerS[gas]
                        if (rate.gt(GameDecimal.ZERO)) add(formatRate(rate, "kg ${gasOf(gas).formula}/s", format))
                    }
                    for (resource in tech.effect.resourceProductionPerUnit.keys) {
                        val rate = mine.resourcePerS[resource]
                        if (rate.gt(GameDecimal.ZERO)) add(formatRate(rate, "${resourceOf(resource).shortName}/s", format))
                    }
                }
                if (outputs.isNotEmpty()) {
                    Text(
                        text = "Producing ${outputs.joinToString(" · ")} from ×$owned",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accent,
                        modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                    )
                }
            }

            TechCard(
                tech = tech,
                state = state,
                derived = derived,
                quantity = quantity,
                onBuy = onBuy,
            )
        }
    }
}
