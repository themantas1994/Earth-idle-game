package com.earthgame.idle.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthgame.idle.domain.engine.CollapseSummary
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.OfflineProgressResult
import com.earthgame.idle.domain.engine.PRESTIGE_CURRENCY_NAME
import com.earthgame.idle.domain.engine.RUN_LABEL
import com.earthgame.idle.domain.formatting.NumberFormatMode
import com.earthgame.idle.domain.engine.gameSecondsFor
import com.earthgame.idle.domain.formatting.formatDuration
import com.earthgame.idle.domain.formatting.formatGameAge
import com.earthgame.idle.domain.formatting.formatNumber
import com.earthgame.idle.domain.formatting.formatTemperature
import com.earthgame.idle.domain.model.GAS_LIST
import com.earthgame.idle.domain.model.RESOURCE_LIST
import com.earthgame.idle.domain.model.RunStats
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors

/** "Welcome back" — what the world did while the app was closed. */
@Composable
fun OfflineProgressDialog(
    summary: OfflineProgressResult,
    format: NumberFormatMode,
    onDismiss: () -> Unit,
) {
    val colors = gameColors
    val tempDelta = summary.summary.temperatureAfterC - summary.summary.temperatureBeforeC

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentStrong,
                    contentColor = colors.background,
                ),
            ) { Text("Continue") }
        },
        title = { Text("👋 Welcome Back") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = buildString {
                        append("You were away for ${formatDuration(summary.awaySeconds)}")
                        if (summary.cappedByLimit) {
                            append(" (capped at ${formatDuration(summary.offlineCapSeconds)} of progress)")
                        }
                        append(".")
                        // What the planet experienced, which is the number that
                        // explains a gas total that went *down* while away.
                        val simulated = gameSecondsFor(summary.simulatedSeconds)
                        if (simulated > 0) {
                            append(" Earth aged ${formatGameAge(simulated, format)}.")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                )
                SectionLabel("During your absence")
                for (gas in GAS_LIST) {
                    val amount = summary.summary.gasGeneratedKg[gas.id]
                    if (!gas.directlyEmitted || !amount.gt(GameDecimal.ZERO)) continue
                    StatRow("${gas.displayName} generated", "+${formatNumber(amount, format)} kg")
                }
                for (resource in RESOURCE_LIST) {
                    val amount = summary.summary.resourcesGained[resource.id]
                    if (!amount.gt(GameDecimal.ZERO)) continue
                    StatRow("${resource.displayName} generated", "+${formatNumber(amount, format)}")
                }
                StatRow("Temperature", formatTemperature(tempDelta))
            }
        },
        containerColor = colors.surfaceElevated,
        titleContentColor = colors.text,
        textContentColor = colors.text,
    )
}

/** The Earth has died. Shown until the player resets. */
@Composable
fun UninhabitableDialog(
    runStats: RunStats,
    survivedSeconds: Double,
    format: NumberFormatMode,
    confirmRequired: Boolean,
    onReset: () -> Unit,
) {
    val colors = gameColors
    AlertDialog(
        // Not dismissible: the run is over, and the only way forward is through.
        onDismissRequest = {},
        confirmButton = {
            Button(
                onClick = onReset,
                modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.danger,
                    contentColor = colors.text,
                ),
            ) { Text("RESET EARTH", fontWeight = FontWeight.Bold) }
        },
        title = { Text("☠️ EARTH HAS BECOME UNINHABITABLE") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Human civilization has collapsed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                )
                StatRow("Peak CO₂", "${formatNumber(runStats.peakCo2Ppm, format)} ppm")
                StatRow("Peak Temperature", formatTemperature(runStats.peakTemperatureC))
                StatRow("Civilization survived", formatDuration(survivedSeconds))
                if (confirmRequired) {
                    Text(
                        "Resetting banks your Earth Points and starts the next civilization.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textFaint,
                    )
                }
            }
        },
        containerColor = colors.surfaceElevated,
        titleContentColor = colors.danger,
        textContentColor = colors.text,
    )
}

/** What the run that just ended was worth. */
@Composable
fun CollapseSummaryDialog(
    summary: CollapseSummary,
    format: NumberFormatMode,
    onDismiss: () -> Unit,
) {
    val colors = gameColors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentStrong,
                    contentColor = colors.background,
                ),
            ) { Text("Begin $RUN_LABEL ${summary.runNumber + 1}") }
        },
        title = { Text("🌍 $RUN_LABEL ${summary.runNumber} COMPLETE") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatRow("Civilization Duration", formatDuration(summary.durationSeconds))
                StatRow("Maximum CO₂", "${formatNumber(summary.maxCo2Ppm, format)} ppm")
                StatRow("Maximum Temperature", formatTemperature(summary.maxTemperatureC))
                StatRow("Peak GHG Production", "${formatNumber(summary.maxGasProductionRateKgPerS, format)} kg/s")
                SectionLabel("$PRESTIGE_CURRENCY_NAME Earned")
                Text(
                    text = "+${formatNumber(summary.earthPointsEarned, format)}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        containerColor = colors.surfaceElevated,
        titleContentColor = colors.text,
        textContentColor = colors.text,
    )
}

/** Asked before a reset when "Confirm Before Reset" is on. */
@Composable
fun ConfirmResetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = gameColors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.danger,
                    contentColor = colors.text,
                ),
            ) { Text("Reset Earth") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                Text("Not yet")
            }
        },
        title = { Text("Reset this Earth?") },
        text = {
            Text(
                "Your technology, resources and atmosphere reset. Earth Points, achievements, " +
                    "challenge completions and settings all carry over.",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        containerColor = colors.surfaceElevated,
        titleContentColor = colors.text,
        textContentColor = colors.textDim,
    )
}

/** Shown once when a save could not be read. */
@Composable
fun SaveWarningDialog(recoveredFromBackup: Boolean, onDismiss: () -> Unit) {
    val colors = gameColors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentStrong,
                    contentColor = colors.background,
                ),
            ) { Text("Continue") }
        },
        title = { Text(if (recoveredFromBackup) "Save recovered" else "Save could not be read") },
        text = {
            Text(
                text = if (recoveredFromBackup) {
                    "Your most recent save was damaged, so the backup was loaded instead. " +
                        "You may have lost the last few seconds of progress."
                } else {
                    "Neither the save nor its backup could be read, so a new game has been " +
                        "started. Nothing else on the device was changed."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        },
        containerColor = colors.surfaceElevated,
        titleContentColor = colors.warning,
        textContentColor = colors.textDim,
    )
}
