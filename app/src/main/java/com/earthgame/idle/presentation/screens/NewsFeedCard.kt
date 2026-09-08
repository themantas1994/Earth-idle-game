package com.earthgame.idle.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.earthgame.idle.domain.formatting.formatDuration
import com.earthgame.idle.domain.milestones.MILESTONE_BY_ID
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.presentation.components.CardTitle
import com.earthgame.idle.presentation.components.EmptyHint
import com.earthgame.idle.presentation.components.GameCard
import com.earthgame.idle.presentation.theme.gameColors

/**
 * The run's world-news feed: every milestone headline this Earth has triggered,
 * newest first.
 *
 * It is the narrative counterpart to the Atmosphere screen's numbers — the same
 * events, told as consequences — so a multi-day run reads as a story rather
 * than a rising number.
 */
@Composable
fun NewsFeedCard(
    state: GameState,
    modifier: Modifier = Modifier,
    limit: Int? = null,
) {
    val colors = gameColors
    val items = if (limit != null) state.newsFeed.take(limit) else state.newsFeed

    GameCard(modifier = modifier) {
        CardTitle("📰 World News")

        if (items.isEmpty()) {
            EmptyHint("Nothing newsworthy yet. Keep burning things.")
        }

        for (item in items) {
            val definition = MILESTONE_BY_ID[item.milestoneId] ?: continue
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${definition.source} · ${formatDuration(item.runSeconds)} into this Earth",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                )
                Text(
                    text = "${definition.icon} ${definition.headline}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                )
                Text(
                    text = definition.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                )
            }
        }

        if (limit != null && state.newsFeed.size > limit) {
            val extra = state.newsFeed.size - limit
            Text(
                text = "+ $extra earlier ${if (extra == 1) "headline" else "headlines"} this run",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textFaint,
            )
        }
    }
}
