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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthgame.idle.domain.challenges.CHALLENGES
import com.earthgame.idle.domain.challenges.CHALLENGE_BY_ID
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.presentation.components.Badge
import com.earthgame.idle.presentation.components.CardTitle
import com.earthgame.idle.presentation.components.DecorativeIcon
import com.earthgame.idle.presentation.components.GameCard
import com.earthgame.idle.presentation.components.SectionLabel
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors

/**
 * Challenges: a restricted run for a permanent reward.
 *
 * Starting one resets the current Earth, which is why the button says so —
 * a player mid-run should not lose it to a mistap.
 */
@Composable
fun ChallengesScreen(
    state: GameState,
    onStartChallenge: (String) -> Unit,
    onAbandonChallenge: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val activeId = state.challenges.activeId

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        if (activeId != null) {
            item {
                val active = CHALLENGE_BY_ID[activeId]
                GameCard(borderColor = colors.accent) {
                    CardTitle("🎯 Active Challenge")
                    Text(
                        text = "You are running ${active?.displayName ?: activeId}. Its restriction " +
                            "applies until you complete the goal or reset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim,
                    )
                    active?.let {
                        Text(
                            text = "Goal: ${it.goal.description}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textFaint,
                        )
                    }
                    OutlinedButton(
                        onClick = onAbandonChallenge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.MinTouchTarget),
                    ) {
                        Text("Abandon Challenge", color = colors.danger)
                    }
                }
            }
        }

        item { SectionLabel("Available Challenges") }

        items(CHALLENGES, key = { it.id }) { challenge ->
            val done = state.challenges.completed[challenge.id] == true
            val isActive = activeId == challenge.id

            GameCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DecorativeIcon(challenge.icon, 17.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = challenge.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    if (done) Badge("COMPLETED", colors.good)
                }

                Text(
                    text = challenge.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                )
                Text(
                    text = "Goal: ${challenge.goal.description}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                )
                Text(
                    text = "Reward: ${challenge.rewardDescription}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                )

                when {
                    isActive -> Badge("IN PROGRESS", colors.accent)

                    activeId == null -> Button(
                        onClick = { onStartChallenge(challenge.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.MinTouchTarget),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentStrong,
                            contentColor = colors.background,
                        ),
                    ) {
                        Text(
                            text = "${if (done) "Run Again" else "Start Challenge"} (resets current Earth)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }
}
