package com.earthgame.idle.presentation.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.earthgame.idle.BuildConfig
import com.earthgame.idle.R
import com.earthgame.idle.presentation.components.GameCard
import com.earthgame.idle.presentation.components.SectionLabel
import com.earthgame.idle.presentation.components.StatRow
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors

/**
 * About: what this build is, who made it, and where the legal documents are.
 *
 * Everything on this screen is read from the build or from a string resource —
 * no developer name, company or address is hard-coded, because the repository
 * records none and inventing one would be worse than omitting it. The licence
 * row states the project's licence as it actually stands rather than what would
 * be convenient.
 *
 * Reached from Settings. The links open in whatever handles them; nothing here
 * fails loudly if a device has no browser.
 */
@Composable
fun AboutScreen(
    adPrivacyAvailable: Boolean,
    onOpenAdPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val context = LocalContext.current

    fun open(url: String) {
        // A device with no browser, or a locked-down profile, is not a crash.
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        item {
            GameCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 6.sp),
                        color = colors.accent,
                    )
                    Spacer(Modifier.heightIn(min = 4.dp))
                    Text(
                        text = "An idle game about technology, greenhouse gases and starting over.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        item { SectionLabel("Build") }
        item {
            GameCard {
                StatRow(label = "Version", value = BuildConfig.VERSION_NAME)
                StatRow(label = "Version code", value = BuildConfig.VERSION_CODE.toString())
                StatRow(label = "Package", value = BuildConfig.APPLICATION_ID)
            }
        }

        item { SectionLabel("Licensing") }
        item {
            GameCard {
                StatRow(label = "Project licence", value = stringResource(R.string.project_license))
                Spacer(Modifier.heightIn(min = 6.dp))
                Text(
                    text = "EARTH is built on open-source software. The full notices for " +
                        "everything it ships are included in the app.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                )
                Spacer(Modifier.heightIn(min = 8.dp))
                AboutAction(label = "Open Source Licenses", onClick = onOpenLicenses)
            }
        }

        item { SectionLabel("Privacy & advertising") }
        item {
            GameCard {
                Text(
                    text = "EARTH shows one banner advertisement, supplied by Google AdMob. " +
                        "Your save never leaves the device, and there is no analytics, " +
                        "telemetry or crash reporting of any kind.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                )
                Spacer(Modifier.heightIn(min = 8.dp))
                val policyUrl = stringResource(R.string.privacy_policy_url)
                AboutAction(label = "Privacy policy", onClick = { open(policyUrl) })
                // Only players who were shown a consent message have a form to
                // reopen, so the row is hidden elsewhere rather than offering a
                // button that does nothing. See docs/wiki/Privacy.md.
                if (adPrivacyAvailable) {
                    Spacer(Modifier.heightIn(min = 6.dp))
                    AboutAction(label = "Manage advertising privacy", onClick = onOpenAdPrivacy)
                }
            }
        }

        item { SectionLabel("Source") }
        item {
            GameCard {
                val repositoryUrl = stringResource(R.string.repository_url)
                Text(
                    text = repositoryUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                )
                Spacer(Modifier.heightIn(min = 8.dp))
                AboutAction(label = "Open the repository", onClick = { open(repositoryUrl) })
            }
        }
    }
}

@Composable
private fun AboutAction(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MinTouchTarget),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label)
            Spacer(Modifier.width(4.dp))
        }
    }
}
