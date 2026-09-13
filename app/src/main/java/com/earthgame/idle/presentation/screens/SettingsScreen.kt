package com.earthgame.idle.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.earthgame.idle.domain.formatting.NumberFormatMode
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.GraphicsQuality
import com.earthgame.idle.domain.model.Settings
import com.earthgame.idle.domain.model.ThemePreference
import com.earthgame.idle.presentation.components.GameCard
import com.earthgame.idle.presentation.components.SectionLabel
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors

private val FORMAT_OPTIONS = listOf(
    NumberFormatMode.COMPACT to "Compact (1.2M)",
    NumberFormatMode.SCIENTIFIC to "Scientific (1.20e+6)",
    NumberFormatMode.ENGINEERING to "Engineering (1.20e+6)",
    NumberFormatMode.FULL to "Full (1,200,000)",
)

private val GRAPHICS_OPTIONS = GraphicsQuality.entries

private val THEME_OPTIONS = listOf(
    ThemePreference.SYSTEM to "Follow system",
    ThemePreference.LIGHT to "Light",
    ThemePreference.DARK to "Dark",
)

@Composable
fun SettingsScreen(
    state: GameState,
    onUpdateSettings: ((Settings) -> Settings) -> Unit,
    adPrivacyAvailable: Boolean,
    onOpenAdPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        item { SectionLabel("Number Format") }
        item {
            GameCard {
                for ((mode, label) in FORMAT_OPTIONS) {
                    ChoiceRow(
                        label = label,
                        selected = settings.numberFormat == mode,
                        onSelect = { onUpdateSettings { it.copy(numberFormat = mode) } },
                    )
                }
            }
        }

        item { SectionLabel("Appearance") }
        item {
            GameCard {
                for ((preference, label) in THEME_OPTIONS) {
                    ChoiceRow(
                        label = label,
                        selected = settings.darkMode == preference,
                        onSelect = { onUpdateSettings { it.copy(darkMode = preference) } },
                    )
                }
                ToggleRow(
                    label = "Reduced Animations",
                    description = "Progress bars jump to their value instead of easing.",
                    checked = settings.reducedAnimations,
                    onChange = { value -> onUpdateSettings { it.copy(reducedAnimations = value) } },
                )
            }
        }

        item { SectionLabel("Globe Quality") }
        item {
            GameCard {
                for (quality in GRAPHICS_OPTIONS) {
                    ChoiceRow(
                        label = "${quality.displayName} — ${quality.description}",
                        selected = settings.graphicsQuality == quality,
                        onSelect = { onUpdateSettings { it.copy(graphicsQuality = quality) } },
                    )
                }
                Text(
                    text = "How much work the Home screen's globe does. Nothing here changes the " +
                        "simulation: the same storms form and cost the same production at every " +
                        "setting, and every value the globe draws is also printed beside it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = gameColors.textFaint,
                )
            }
        }

        item { SectionLabel("Gameplay") }
        item {
            GameCard {
                ToggleRow(
                    label = "Confirm Before Reset",
                    description = "Ask before ending an Earth and banking its points.",
                    checked = settings.confirmReset,
                    onChange = { value -> onUpdateSettings { it.copy(confirmReset = value) } },
                )
                ToggleRow(
                    label = "Offline Progress",
                    description = "Keep the world running while the app is closed, up to the cap.",
                    checked = settings.offlineProgressEnabled,
                    onChange = { value -> onUpdateSettings { it.copy(offlineProgressEnabled = value) } },
                )
            }
        }

        item { SectionLabel("Audio & Feedback") }
        item {
            GameCard {
                ToggleRow(
                    label = "Sound Effects",
                    checked = settings.soundEnabled,
                    onChange = { value -> onUpdateSettings { it.copy(soundEnabled = value) } },
                )
                ToggleRow(
                    label = "Music",
                    checked = settings.musicEnabled,
                    onChange = { value -> onUpdateSettings { it.copy(musicEnabled = value) } },
                )
                ToggleRow(
                    label = "Vibration",
                    description = "A short pulse on a purchase, and a heavier one when an Earth ends.",
                    checked = settings.vibrationEnabled,
                    onChange = { value -> onUpdateSettings { it.copy(vibrationEnabled = value) } },
                )
            }
        }

        // AdMob expects players who were shown a consent message to be able to
        // come back and change it, for as long as the app shows ads — not only
        // the first time. Only players who got a message have a form to reopen,
        // so the row is hidden everywhere else rather than offering a button
        // that fails; About repeats it for the same reason.
        if (adPrivacyAvailable) {
            item { SectionLabel("Privacy") }
            item {
                GameCard {
                    OutlinedButton(
                        onClick = onOpenAdPrivacy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.MinTouchTarget),
                    ) {
                        Text("Manage advertising privacy")
                    }
                }
            }
        }

        item { SectionLabel("About") }
        item {
            GameCard {
                OutlinedButton(
                    onClick = onOpenAbout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.MinTouchTarget),
                ) {
                    Text("About EARTH")
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val colors = gameColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MinTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.text else colors.textDim,
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    description: String? = null,
) {
    val colors = gameColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MinTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.text)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.labelSmall, color = colors.textFaint)
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.accentStrong,
                checkedThumbColor = colors.background,
            ),
        )
    }
}
