package com.earthgame.idle.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthgame.idle.domain.economy.nominalizeWallet
import com.earthgame.idle.domain.economy.quotedCost
import com.earthgame.idle.domain.economy.secondsUntilAffordable
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.engine.OWNERSHIP_BONUS
import com.earthgame.idle.domain.engine.nextOwnershipMilestone
import com.earthgame.idle.domain.engine.ownershipMultiplier
import com.earthgame.idle.domain.engine.ownershipProgress
import com.earthgame.idle.domain.formatting.NumberFormatMode
import com.earthgame.idle.domain.formatting.formatDuration
import com.earthgame.idle.domain.formatting.formatNumber
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.gasOf
import com.earthgame.idle.domain.model.resourceOf
import com.earthgame.idle.domain.technologies.TECH_BY_ID
import com.earthgame.idle.domain.technologies.TechKind
import com.earthgame.idle.domain.technologies.Technology
import com.earthgame.idle.domain.technologies.isRivalChoiceTaken
import com.earthgame.idle.domain.technologies.maxAffordableQuantity
import com.earthgame.idle.domain.technologies.requirementsMet
import com.earthgame.idle.presentation.BuyQuantity
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.NumericTextStyle
import com.earthgame.idle.presentation.theme.gameColors
import kotlin.math.max
import kotlin.math.min

/**
 * One purchasable technology.
 *
 * The affordability shown on the button comes from the same engine helpers
 * `purchaseTechnology` uses, via [nominalizeWallet] — so the button can never
 * offer a purchase the engine would then refuse.
 */
@Composable
fun TechCard(
    tech: Technology,
    state: GameState,
    derived: DerivedState,
    quantity: BuyQuantity,
    onBuy: (String, BuyQuantity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val format = state.settings.numberFormat

    val owned = state.techOwned[tech.id] ?: 0
    val discount = derived.prestige.techCostDiscount
    val reqsMet = requirementsMet(tech, state.techOwned)
    val disabledByChallenge = tech.id in derived.disabledTechIds
    val maxedOneTime = tech.isOneTime && owned > 0
    val rivalTaken = isRivalChoiceTaken(tech, state.techOwned)

    if (maxedOneTime || rivalTaken) {
        GameCard(modifier = modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DecorativeIcon(tech.icon, 17.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tech.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textDim,
                    modifier = Modifier.weight(1f),
                )
                Badge(
                    text = if (maxedOneTime) "OWNED" else "NOT CHOSEN",
                    color = if (maxedOneTime) colors.good else colors.textFaint,
                )
            }
        }
        return
    }

    val locked = !reqsMet || disabledByChallenge
    val roomLeft = tech.maxOwned - owned
    val cappedQuantity = min(quantity.amount, roomLeft)

    val affordableQuantity = remember(tech.id, owned, state.resources, discount, cappedQuantity, locked) {
        if (locked) {
            0
        } else {
            maxAffordableQuantity(
                tech,
                owned,
                nominalizeWallet(state.resources, tech.cost, discount),
                cappedQuantity,
            )
        }
    }

    val displayQuantity = when {
        tech.isOneTime -> 1
        quantity == BuyQuantity.MAX -> max(affordableQuantity, 1)
        else -> max(1, cappedQuantity)
    }
    val cost = quotedCost(tech, owned, displayQuantity, discount)
    val canBuy = !locked && affordableQuantity > 0

    val waitSeconds = if (canBuy || locked) {
        null
    } else {
        secondsUntilAffordable(cost, state.resources, derived.productionRates.resourcePerS)
    }

    GameCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DecorativeIcon(tech.icon, 17.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = tech.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = if (locked) colors.textDim else colors.text,
                modifier = Modifier.weight(1f),
            )
            val bonus = ownershipMultiplier(owned)
            if (bonus > 1) {
                Text(
                    text = "×${formatNumber(bonus, format)} output",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                )
                Spacer(Modifier.width(6.dp))
            }
            if (owned > 0 && !tech.isOneTime) {
                Text(
                    text = "×${formatNumber(owned, format)}",
                    style = NumericTextStyle,
                    color = colors.text,
                )
            }
        }

        Text(
            text = tech.description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textDim,
        )

        val effects = remember(tech.id, format) { effectSummary(tech, format) }
        if (effects.isNotEmpty()) {
            Text(
                text = effects.joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textFaint,
            )
        }

        if (tech.kind == TechKind.GENERATOR && !locked) {
            val nextAt = nextOwnershipMilestone(owned)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ProgressBar(
                    progress = ownershipProgress(owned).toFloat(),
                    color = colors.accentStrong,
                    height = 5.dp,
                )
                Text(
                    text = "×${OWNERSHIP_BONUS.multiplier.toInt()} output at $nextAt owned · ${nextAt - owned} to go",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                )
            }
        }

        if (!reqsMet) {
            Text(
                text = "Requires: " + tech.requires.joinToString(", ") { TECH_BY_ID[it]?.displayName ?: it },
                style = MaterialTheme.typography.labelSmall,
                color = colors.warning,
            )
        }
        if (disabledByChallenge) {
            Text(
                text = "Disabled by the active challenge",
                style = MaterialTheme.typography.labelSmall,
                color = colors.warning,
            )
        }

        if (!locked) {
            val priceText = cost.joinToString(", ") {
                "${formatNumber(it.amount, format)} ${resourceOf(it.resource).shortName}"
            }
            val label = when {
                tech.isOneTime -> "Unlock"
                quantity == BuyQuantity.MAX -> "Buy Max" + if (affordableQuantity > 0) " ($affordableQuantity)" else ""
                else -> "Buy $displayQuantity"
            }

            Button(
                onClick = { onBuy(tech.id, quantity) },
                enabled = canBuy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.MinTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tech.kind == TechKind.CHOICE) colors.warning else colors.accentStrong,
                    contentColor = colors.background,
                    disabledContainerColor = colors.border,
                    disabledContentColor = colors.textFaint,
                ),
            ) {
                Text(
                    text = "$label — $priceText",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                )
            }

            // A price you are saving toward is far more motivating with a clock
            // on it — and because prices in this game never move, the clock is
            // a promise rather than an estimate.
            when {
                waitSeconds != null && waitSeconds > 0 -> Text(
                    text = "affordable in ${formatDuration(waitSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                    modifier = Modifier.fillMaxWidth(),
                )

                waitSeconds == null && !canBuy -> Text(
                    text = "no income for this yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun effectSummary(tech: Technology, mode: NumberFormatMode): List<String> {
    val lines = mutableListOf<String>()
    val effect = tech.effect

    for ((gas, amount) in effect.gasProductionPerUnit) {
        lines += "+${formatNumber(amount, mode)} kg ${gasOf(gas).formula}/s"
    }
    for ((gas, amount) in effect.gasRemovalPerUnit) {
        lines += "−${formatNumber(amount, mode)} kg ${gasOf(gas).formula}/s"
    }
    for ((resource, amount) in effect.resourceProductionPerUnit) {
        lines += "+${formatNumber(amount, mode)} ${resourceOf(resource).shortName}/s"
    }
    effect.globalProductionMultiplier?.let { lines += "×$it all production" }
    effect.branchProductionMultiplier?.let { lines += "×${it.multiplier} ${it.branch.displayName}" }
    effect.gasProductionMultiplier?.let { lines += "×${it.multiplier} ${gasOf(it.gas).formula}" }
    effect.resourceProductionMultiplier?.let { lines += "×${it.multiplier} ${resourceOf(it.resource).shortName}" }
    effect.researchMultiplier?.let { lines += "×$it Research" }

    return lines
}
