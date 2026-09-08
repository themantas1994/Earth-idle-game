package com.earthgame.idle.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthgame.idle.domain.achievements.ACHIEVEMENTS
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.presentation.components.Badge
import com.earthgame.idle.presentation.components.CardTitle
import com.earthgame.idle.presentation.components.DecorativeIcon
import com.earthgame.idle.presentation.components.GameCard
import com.earthgame.idle.presentation.components.ProgressBar
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors

@Composable
fun AchievementsScreen(
    state: GameState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val unlockedCount = ACHIEVEMENTS.count { state.achievementsUnlocked[it.id] == true }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        item {
            GameCard {
                CardTitle("🏆 Achievements")
                Text(
                    text = "$unlockedCount / ${ACHIEVEMENTS.size}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                ProgressBar(
                    progress = unlockedCount.toFloat() / ACHIEVEMENTS.size,
                    color = colors.accent,
                )
            }
        }

        items(ACHIEVEMENTS, key = { it.id }) { achievement ->
            val done = state.achievementsUnlocked[achievement.id] == true
            GameCard(modifier = Modifier.alpha(if (done) 1f else 0.55f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DecorativeIcon(if (done) achievement.icon else "🔒", 17.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = achievement.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    if (done) Badge("UNLOCKED", colors.good)
                }
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                )
            }
        }
    }
}
