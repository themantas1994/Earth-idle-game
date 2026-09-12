package com.earthgame.idle.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthgame.idle.presentation.navigation.Destination
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors

/**
 * The bottom navigation bar.
 *
 * Nine destinations have to fit across the narrowest Android phone (360 dp), so
 * this is a hand-rolled row rather than Material's `NavigationBar`, which
 * assumes three to five items and reserves far more width per destination than
 * there is. Each column flexes to an equal share and its label is shortened to
 * stay legible at ~40 dp, while the full destination name is what a screen
 * reader announces.
 *
 * The row sits above the navigation-bar inset rather than under it, so the
 * bottom row of pixels is never clipped by a gesture bar.
 */
@Composable
fun BottomNav(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceElevated),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 1.dp)
                .background(colors.border),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(min = Dimens.NavBarHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (destination in Destination.navigationEntries) {
                val selected = destination == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(destination) },
                        )
                        .heightIn(min = 52.dp)
                        .padding(vertical = 6.dp, horizontal = 2.dp)
                        .semantics { contentDescription = destination.title },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = destination.icon,
                        fontSize = 17.sp,
                    )
                    Text(
                        text = destination.shortLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.sp),
                        color = if (selected) colors.accent else colors.textFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}
