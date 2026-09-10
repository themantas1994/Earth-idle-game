package com.earthgame.idle.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.technologies.RESEARCH_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.TechBranch
import com.earthgame.idle.presentation.BuyQuantity
import com.earthgame.idle.presentation.components.EmptyHint
import com.earthgame.idle.presentation.components.TechCard
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors

/**
 * The research tree: one-time unlocks, permanent multipliers and the branching
 * strategic choices.
 *
 * Generators are deliberately absent — anything bought repeatedly to raise
 * output lives on Production instead, so the two tabs never show the same card
 * and neither becomes an undifferentiated wall. Locked nodes stay visible, with
 * their cost, requirements and effects, so the full tree is browsable from the
 * start.
 */
@Composable
fun TechnologyScreen(
    state: GameState,
    derived: DerivedState,
    activeBranch: TechBranch?,
    onBranchChange: (TechBranch?) -> Unit,
    onBuy: (String, BuyQuantity) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val visible = RESEARCH_TECHNOLOGIES.filter { activeBranch == null || it.branch == activeBranch }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                item {
                    FilterChip(
                        selected = activeBranch == null,
                        onClick = { onBranchChange(null) },
                        label = { Text("All") },
                        modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentStrong,
                            selectedLabelColor = colors.background,
                            labelColor = colors.textDim,
                        ),
                    )
                }
                items(TechBranch.entries.toList(), key = { it.id }) { branch ->
                    FilterChip(
                        selected = activeBranch == branch,
                        onClick = { onBranchChange(branch) },
                        label = { Text("${branch.icon} ${branch.displayName}") },
                        modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentStrong,
                            selectedLabelColor = colors.background,
                            labelColor = colors.textDim,
                        ),
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyHint("No research nodes in this branch — its buildings are on the Production tab.")
            }
        }

        items(visible, key = { it.id }) { tech ->
            TechCard(
                tech = tech,
                state = state,
                derived = derived,
                // Research nodes are one-time, so the quantity selector does
                // not apply to them.
                quantity = BuyQuantity.ONE,
                onBuy = onBuy,
            )
        }
    }
}
