package com.earthgame.idle.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthgame.idle.presentation.navigation.Destination
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors

/** Identifies the rail, so UI tests can scroll it deliberately. */
const val SideNavTestTag = "earth:sidenav"

/**
 * The tablet and unfolded-foldable navigation: a rail down the left, with room
 * for each destination's full name.
 *
 * On the wider layouts the extra width is better spent on legible labels than
 * on stretching the phone bar, and a left rail keeps the game's content column
 * where a thumb can reach it.
 */
@Composable
fun SideNav(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(168.dp)
            .background(colors.surfaceElevated)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .testTag(SideNavTestTag)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (destination in Destination.navigationEntries) {
            val selected = destination == current
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(if (selected) colors.card else colors.surfaceElevated)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(destination) },
                    )
                    .heightIn(min = Dimens.MinTouchTarget)
                    .padding(horizontal = 10.dp)
                    // Merged and labelled like the bottom bar's tabs, so the
                    // emoji is not announced as a second, meaningless node.
                    .semantics(mergeDescendants = true) { contentDescription = destination.title },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(destination.icon, fontSize = 16.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = destination.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.accent else colors.textDim,
                )
            }
        }
    }
}
