package com.earthgame.idle.presentation.components

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthgame.idle.domain.economy.nominalizeWallet
import com.earthgame.idle.domain.economy.quoteNextPurchase
import com.earthgame.idle.domain.economy.quotedCost
import com.earthgame.idle.domain.economy.secondsUntilAffordable
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.engine.nextOwnershipMilestone
import com.earthgame.idle.domain.engine.ownershipMultiplier
import com.earthgame.idle.domain.engine.ownershipProgress
import com.earthgame.idle.domain.formatting.NumberFormatMode
import com.earthgame.idle.domain.formatting.formatDuration
import com.earthgame.idle.domain.formatting.formatNumber
import com.earthgame.idle.domain.formatting.formatRate
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.gasOf
import com.earthgame.idle.domain.model.resourceOf
import com.earthgame.idle.domain.production.ConsumerFlow
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

/** Test tag on the expandable processor detail block. */
const val CONSUMER_DETAIL_TAG = "consumer-detail"

/**
 * One purchasable technology.
 *
 * The affordability shown on the button comes from the same engine helpers
 * `purchaseTechnology` uses, via [nominalizeWallet] — so the button can never
 * offer a purchase the engine would then refuse. That holds for the NEXT mode
 * too: it quotes through `quoteNextPurchase`, which runs the identical
 * affordability check the engine will run, and disables itself rather than
 * making a partial purchase.
 *
 * A processor gets one extra block the producers do not have: what it eats,
 * what it makes, how hard it is running and what is holding it back. The
 * headline of that is always visible, because "73% — short of Oil" is the whole
 * reason the player is looking at the card; the numbers behind it are one tap
 * away, so an ordinary player is not reading a spreadsheet.
 */
@Composable
fun TechCard(
    tech: Technology,
    state: GameState,
    derived: DerivedState,
    quantity: BuyQuantity,
    onBuy: (String, BuyQuantity) -> Unit,
    modifier: Modifier = Modifier,
    flow: ConsumerFlow? = null,
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
    val requestedQuantity = if (tech.isOneTime) 1 else quantity.resolveQuantity(owned)
    val cappedQuantity = min(requestedQuantity, roomLeft)

    val nextQuote = remember(tech.id, owned, state.resources, discount, locked, quantity) {
        if (locked || tech.isOneTime || quantity != BuyQuantity.NEXT) {
            null
        } else {
            quoteNextPurchase(tech, owned, state.resources, discount)
        }
    }

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
    val cost = nextQuote?.cost ?: quotedCost(tech, owned, displayQuantity, discount)
    val canBuy = when {
        locked -> false
        nextQuote != null -> nextQuote.affordable
        else -> affordableQuantity > 0
    }

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

        if (tech.isConsumer) {
            ProcessorBlock(tech = tech, owned = owned, flow = flow, format = format)
        }

        val effects = remember(tech.id, format) { effectSummary(tech, format) }
        if (effects.isNotEmpty()) {
            Text(
                text = effects.joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textFaint,
            )
        }

        if (tech.isBuilding && !locked) {
            val nextAt = nextOwnershipMilestone(owned)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ProgressBar(
                    progress = ownershipProgress(owned).toFloat(),
                    color = colors.accentStrong,
                    height = 5.dp,
                )
                Text(
                    text = "×2 output at $nextAt owned · ${nextAt - owned} to go",
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
                nextQuote != null -> "Next ×${nextQuote.quantity} → ${nextQuote.targetOwned}"
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
                    containerColor = when {
                        tech.kind == TechKind.CHOICE -> colors.warning
                        nextQuote != null -> colors.accent
                        else -> colors.accentStrong
                    },
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

            // NEXT never buys a partial milestone, so when it cannot complete
            // one it says how far short the player is rather than silently
            // doing something smaller than what the button promises.
            if (nextQuote != null && !nextQuote.affordable) {
                Text(
                    text = "need ${nextQuote.shortfallUnits} more unit" +
                        (if (nextQuote.shortfallUnits == 1) "" else "s") +
                        " · Max would buy ${nextQuote.affordableNow}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.warning,
                    modifier = Modifier.fillMaxWidth(),
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

/**
 * The processor block: what it eats, what it makes, and — once any are owned —
 * how hard it is actually running and why.
 *
 * The one-line status is always shown. The per-input arithmetic behind it is
 * behind a tap, because most of the time "88%, short of Iron Ore" is the entire
 * answer and the player wants to go and buy a mine, not read a table.
 */
@Composable
private fun ProcessorBlock(
    tech: Technology,
    owned: Int,
    flow: ConsumerFlow?,
    format: NumberFormatMode,
) {
    val colors = gameColors
    var expanded by remember(tech.id) { mutableStateOf(false) }

    val consumes = tech.effect.inputsPerUnit.entries
        .sortedBy { it.key.ordinal }
        .joinToString(" + ") { "${formatNumber(it.value, format)} ${resourceOf(it.key).shortName}/s" }
    val produces = tech.effect.resourceProductionPerUnit.entries
        .sortedBy { it.key.ordinal }
        .joinToString(" + ") { "${formatNumber(it.value, format)} ${resourceOf(it.key).shortName}/s" }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = if (produces.isEmpty()) "Consumes $consumes per unit" else "Consumes $consumes → makes $produces per unit",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textDim,
        )

        if (owned > 0 && flow != null) {
            val percent = flow.utilizationPercent
            val limiting = flow.limitingResource
            val statusColor = when {
                percent >= 99 -> colors.good
                percent >= 50 -> colors.accent
                else -> colors.warning
            }
            val status = if (limiting == null) {
                "Running at $percent% — fully supplied"
            } else {
                "Running at $percent% — short of ${resourceOf(limiting).displayName}"
            }

            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = statusColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .semantics { contentDescription = "$status. Tap for the flow breakdown." },
            )
            ProgressBar(
                progress = flow.utilization.toFloat(),
                color = statusColor,
                height = 4.dp,
            )

            if (expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .semantics { contentDescription = CONSUMER_DETAIL_TAG },
                ) {
                    for (input in flow.inputs) {
                        val name = resourceOf(input.resource).shortName
                        Text(
                            text = "· $name — wants ${formatRate(input.demandPerS, "/s", format)}, " +
                                "economy supplies ${formatRate(input.availablePerS, "/s", format)}, " +
                                "taking ${formatRate(input.consumedPerS, "/s", format)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textFaint,
                        )
                    }
                    for (resource in tech.effect.resourceProductionPerUnit.keys.sortedBy { it.ordinal }) {
                        Text(
                            text = "· ${resourceOf(resource).shortName} — making " +
                                "${formatRate(flow.actualOutputPerS[resource], "/s", format)} of a possible " +
                                formatRate(flow.theoreticalOutputPerS[resource], "/s", format),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textFaint,
                        )
                    }
                }
            }
        } else if (owned > 0) {
            Text(
                text = "Idle — waiting for the simulation to price its inputs",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textFaint,
            )
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
    // A processor's own intake and output are spelled out in its status block,
    // so repeating the per-unit output here would only be noise.
    if (!tech.isConsumer) {
        for ((resource, amount) in effect.resourceProductionPerUnit) {
            lines += "+${formatNumber(amount, mode)} ${resourceOf(resource).shortName}/s"
        }
    }
    effect.globalProductionMultiplier?.let { lines += "×$it all production" }
    effect.branchProductionMultiplier?.let { lines += "×${it.multiplier} ${it.branch.displayName}" }
    effect.gasProductionMultiplier?.let { lines += "×${it.multiplier} ${gasOf(it.gas).formula}" }
    effect.resourceProductionMultiplier?.let { lines += "×${it.multiplier} ${resourceOf(it.resource).shortName}" }
    effect.researchMultiplier?.let { lines += "×$it Research" }

    return lines
}

