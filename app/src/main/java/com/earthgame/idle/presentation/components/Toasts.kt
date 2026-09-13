package com.earthgame.idle.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthgame.idle.domain.achievements.ACHIEVEMENT_BY_ID
import com.earthgame.idle.domain.events.RANDOM_EVENT_BY_ID
import com.earthgame.idle.domain.milestones.MILESTONE_BY_ID
import com.earthgame.idle.domain.model.NewsBulletin
import com.earthgame.idle.domain.technologies.TECH_BY_ID
import com.earthgame.idle.presentation.theme.LocalReducedAnimations
import com.earthgame.idle.presentation.theme.gameColors
import kotlinx.coroutines.delay

private const val TOAST_DURATION_MS = 4_500L

/**
 * A transient banner along the top of the screen.
 *
 * Marked as a polite live region so TalkBack announces it when it appears —
 * these carry the game's feedback (an achievement, a headline, an ownership
 * doubling) and a screen-reader user would otherwise never learn any of it
 * happened.
 */
@Composable
private fun Toast(
    visible: Boolean,
    accent: Color,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = gameColors
    val reduced = LocalReducedAnimations.current

    LaunchedEffect(visible) {
        if (visible) {
            delay(TOAST_DURATION_MS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reduced) fadeIn() else fadeIn() + slideInVertically { -it },
        exit = if (reduced) fadeOut() else fadeOut() + slideOutVertically { -it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, accent, RoundedCornerShape(10.dp))
                .clickable(onClick = onDismiss)
                .padding(10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
fun AchievementToast(achievementId: String?, onDismiss: () -> Unit) {
    val colors = gameColors
    val achievement = achievementId?.let { ACHIEVEMENT_BY_ID[it] }
    Toast(visible = achievement != null, accent = colors.good, onDismiss = onDismiss) {
        achievement ?: return@Toast
        Row(verticalAlignment = Alignment.CenterVertically) {
            DecorativeIcon(achievement.icon, 17.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "Achievement unlocked",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.good,
                )
                Text(
                    achievement.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                )
            }
        }
        Text(achievement.description, style = MaterialTheme.typography.bodySmall, color = colors.textDim)
    }
}

@Composable
fun MilestoneToast(milestoneId: String?, onDismiss: () -> Unit) {
    val colors = gameColors
    val milestone = milestoneId?.let { MILESTONE_BY_ID[it] }
    Toast(visible = milestone != null, accent = colors.warning, onDismiss = onDismiss) {
        milestone ?: return@Toast
        Text(
            "BREAKING · ${milestone.source}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.warning,
        )
        Text(
            "${milestone.icon} ${milestone.headline}",
            style = MaterialTheme.typography.titleSmall,
            color = colors.text,
        )
        Text(milestone.body, style = MaterialTheme.typography.bodySmall, color = colors.textDim)
    }
}

@Composable
fun EventToast(eventId: String?, onDismiss: () -> Unit) {
    val colors = gameColors
    val event = eventId?.let { RANDOM_EVENT_BY_ID[it] }
    val accent = if (event?.isNegative == true) colors.danger else colors.accent
    Toast(visible = event != null, accent = accent, onDismiss = onDismiss) {
        event ?: return@Toast
        Row(verticalAlignment = Alignment.CenterVertically) {
            DecorativeIcon(event.icon, 17.sp)
            Spacer(Modifier.width(8.dp))
            Text(event.displayName, style = MaterialTheme.typography.titleSmall, color = colors.text)
        }
        Text(event.description, style = MaterialTheme.typography.bodySmall, color = colors.textDim)
    }
}

@Composable
fun OwnershipToast(
    milestone: com.earthgame.idle.domain.engine.OwnershipMilestone?,
    onDismiss: () -> Unit,
) {
    val colors = gameColors
    val tech = milestone?.let { TECH_BY_ID[it.techId] }
    Toast(visible = milestone != null && tech != null, accent = colors.accent, onDismiss = onDismiss) {
        milestone ?: return@Toast
        tech ?: return@Toast
        Row(verticalAlignment = Alignment.CenterVertically) {
            DecorativeIcon(tech.icon, 17.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "${tech.displayName} ×${milestone.atUnits}",
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
            )
        }
        Text(
            "Output doubled — now ×${milestone.multiplier.toLong()} from ownership.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.accent,
        )
    }
}

/**
 * A storm headline.
 *
 * Storms write into the same world-news feed the milestones use, so this is
 * the milestone toast's twin — the difference being that a storm's text is
 * written when it happens rather than looked up, because it names a storm that
 * did not exist until a moment ago.
 *
 * Only *major* developments reach here. A tropical storm quietly forming does
 * not interrupt the player; a superstorm reaching severe does.
 */
@Composable
fun StormToast(bulletin: NewsBulletin?, onDismiss: () -> Unit) {
    val colors = gameColors
    Toast(visible = bulletin != null, accent = colors.warning, onDismiss = onDismiss) {
        bulletin ?: return@Toast
        Row(verticalAlignment = Alignment.CenterVertically) {
            DecorativeIcon(bulletin.icon, 17.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    bulletin.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.warning,
                )
                Text(
                    bulletin.headline,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                )
            }
        }
        Text(bulletin.body, style = MaterialTheme.typography.bodySmall, color = colors.textDim)
    }
}
