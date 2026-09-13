package com.earthgame.idle.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.computeTechProductionRates
import com.earthgame.idle.domain.formatting.formatNumber
import com.earthgame.idle.domain.formatting.formatRate
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.RESOURCE_LIST
import com.earthgame.idle.domain.model.ResourceClass
import com.earthgame.idle.domain.model.gasOf
import com.earthgame.idle.domain.model.resourceOf
import com.earthgame.idle.domain.technologies.CONSUMER_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.GENERATOR_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.Technology
import com.earthgame.idle.domain.technologies.isTechAvailable
import com.earthgame.idle.presentation.BuyQuantity
import com.earthgame.idle.presentation.components.EmptyHint
import com.earthgame.idle.presentation.components.GameCard
import com.earthgame.idle.presentation.components.SectionLabel
import com.earthgame.idle.presentation.components.TechCard
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors

/** Test tag on the resource flow panel. */
const val RESOURCE_FLOW_TAG = "resource-flow"

/**
 * Every building in the game, and the only screen that sells them.
 *
 * The screen is split the way the economy is: **producers** first — the mines,
 * wells and generators that make something out of nothing but capital — then
 * **processors**, which turn one resource into another and can therefore be
 * short of something. Mixing them into one list was fine when every building
 * was the same kind of thing; it stopped being fine the moment half of them
 * could be sitting at 40% for a reason the player needed to find.
 *
 * Above both sits the flow panel: every resource, what is being made, what is
 * being eaten and what is left over. It is the one place that answers "why is
 * that factory not running?" before the player has to go looking.
 *
 * Buildings whose prerequisites are not met yet are still listed — rendered
 * locked, with their requirements — so the branch the player is researching
 * shows what it will actually unlock.
 */
@Composable
fun ProductionScreen(
    state: GameState,
    derived: DerivedState,
    quantity: BuyQuantity,
    onQuantityChange: (BuyQuantity) -> Unit,
    onBuy: (String, BuyQuantity) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val format = state.settings.numberFormat

    fun visible(tech: Technology) =
        (state.techOwned[tech.id] ?: 0) > 0 || isTechAvailable(tech, state.techOwned)

    val producerGroups = remember(state.techOwned) {
        GENERATOR_TECHNOLOGIES.groupBy { primaryClassOf(it) }
    }
    val processorGroups = remember(state.techOwned) {
        CONSUMER_TECHNOLOGIES.groupBy { primaryClassOf(it) }
    }
    val unlockedCount = GENERATOR_TECHNOLOGIES.count { visible(it) } + CONSUMER_TECHNOLOGIES.count { visible(it) }
    val anyProcessorVisible = CONSUMER_TECHNOLOGIES.any { visible(it) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        item {
            // Five modes do not fit across a phone, so the row scrolls rather
            // than squeezing each label past legibility.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (option in BuyQuantity.entries) {
                    FilterChip(
                        selected = option == quantity,
                        onClick = { onQuantityChange(option) },
                        label = {
                            Text(
                                text = option.label,
                                fontWeight = if (option.isMilestoneTargeted) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = Dimens.MinTouchTarget)
                            .widthIn(min = 62.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (option.isMilestoneTargeted) colors.accent else colors.accentStrong,
                            selectedLabelColor = colors.background,
                            labelColor = if (option.isMilestoneTargeted) colors.accent else colors.textDim,
                        ),
                    )
                }
            }
        }

        item {
            Text(
                text = if (quantity.isMilestoneTargeted) {
                    "Next buys exactly enough copies to reach a building's next milestone — " +
                        "never a partial one. Each card shows the number and the target."
                } else {
                    "Every building's price is fixed the moment you see it. Only the copies you " +
                        "buy of a building make that building's next copy dearer — and each " +
                        "milestone doubles its output. Milestones start every 10 copies and " +
                        "spread out as you go deeper."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        item { ResourceFlowPanel(state, derived) }

        if (unlockedCount == 0) {
            item { EmptyHint("Nothing buildable yet — research a technology that unlocks one.") }
        }

        item { SectionLabel("Producers · make resources out of the planet") }

        for (group in PRODUCER_GROUP_ORDER) {
            val techs = producerGroups[group].orEmpty()
            if (techs.isEmpty()) continue
            item(key = "producer-group-${group.id}") {
                GroupHeading(group.displayName)
            }
            for (tech in techs) {
                item(key = tech.id) {
                    BuildingRow(tech, state, derived, quantity, onBuy, format)
                }
            }
        }

        item {
            SectionLabel(
                if (anyProcessorVisible) {
                    "Processors · turn one resource into another"
                } else {
                    "Processors · locked until you can supply them"
                },
            )
        }

        if (!anyProcessorVisible) {
            item {
                EmptyHint(
                    "Processors run on a flow of resources, not on your stockpile. Research a " +
                        "mill or a refinery and it will show you exactly what it is waiting for.",
                )
            }
        }

        for (group in PROCESSOR_GROUP_ORDER) {
            val techs = processorGroups[group].orEmpty()
            if (techs.isEmpty()) continue
            item(key = "processor-group-${group.id}") {
                GroupHeading(group.displayName)
            }
            for (tech in techs) {
                item(key = tech.id) {
                    BuildingRow(tech, state, derived, quantity, onBuy, format)
                }
            }
        }
    }
}

/** Groups the Production screen shows, in the order the economy flows. */
private val PRODUCER_GROUP_ORDER = listOf(
    ResourceClass.ENERGY,
    ResourceClass.RAW,
    ResourceClass.PROCESSED,
    ResourceClass.ADVANCED,
    ResourceClass.SPECIAL,
)

private val PROCESSOR_GROUP_ORDER = listOf(
    ResourceClass.ENERGY,
    ResourceClass.PROCESSED,
    ResourceClass.ADVANCED,
    ResourceClass.SPECIAL,
    ResourceClass.RAW,
)

/**
 * Which shelf a building belongs on: the class of the resource it mainly makes.
 * A building that makes nothing but gas — the fluorinated-gas plants — is
 * grouped as special rather than hidden, since it is still bought and still
 * emits.
 */
private fun primaryClassOf(tech: Technology): ResourceClass {
    val primary = tech.effect.resourceProductionPerUnit.maxByOrNull { it.value }?.key
        ?: return ResourceClass.SPECIAL
    return resourceOf(primary).resourceClass
}

@Composable
private fun GroupHeading(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = gameColors.textDim,
        modifier = Modifier.padding(start = 6.dp, top = 2.dp),
    )
}

/** One building: its live contribution, then the card that sells it. */
@Composable
private fun BuildingRow(
    tech: Technology,
    state: GameState,
    derived: DerivedState,
    quantity: BuyQuantity,
    onBuy: (String, BuyQuantity) -> Unit,
    format: com.earthgame.idle.domain.formatting.NumberFormatMode,
) {
    val colors = gameColors
    val owned = state.techOwned[tech.id] ?: 0
    val flow = if (tech.isConsumer) derived.productionRates.consumerFlow(tech.id) else null

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (owned > 0) {
            // This building's own contribution, not the global rate for the
            // gases it happens to emit — those are on Home and Atmosphere.
            val mine = computeTechProductionRates(tech, owned, derived.effective, flow?.utilization ?: 1.0)
            val outputs = buildList {
                for (gas in tech.effect.gasProductionPerUnit.keys) {
                    val rate = mine.gasGrossKgPerS[gas]
                    if (rate.gt(GameDecimal.ZERO)) add(formatRate(rate, "kg ${gasOf(gas).formula}/s", format))
                }
                for (resource in tech.effect.resourceProductionPerUnit.keys) {
                    val rate = mine.resourcePerS[resource]
                    if (rate.gt(GameDecimal.ZERO)) add(formatRate(rate, "${resourceOf(resource).shortName}/s", format))
                }
            }
            if (outputs.isNotEmpty()) {
                Text(
                    text = "Producing ${outputs.joinToString(" · ")} from ×$owned",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        TechCard(
            tech = tech,
            state = state,
            derived = derived,
            quantity = quantity,
            onBuy = onBuy,
            flow = flow,
        )
    }
}

/**
 * Production, consumption and net rate for every resource the player has met.
 *
 * Collapsed to the resources that are actually moving, because a fresh Earth
 * has one and a finished one has fifteen, and a wall of zeroes teaches nobody
 * anything. Tapping the header expands it to every resource in the game, which
 * is how a player finds out what a chain they have not built yet would need.
 */
@Composable
private fun ResourceFlowPanel(state: GameState, derived: DerivedState) {
    val colors = gameColors
    val format = state.settings.numberFormat
    val rates = derived.productionRates
    var showAll by remember { mutableStateOf(false) }

    val rows = RESOURCE_LIST.filter { definition ->
        showAll ||
            state.resources[definition.id].gt(GameDecimal.ZERO) ||
            rates.resourceGrossPerS[definition.id].gt(GameDecimal.ZERO)
    }

    GameCard(modifier = Modifier.testTag(RESOURCE_FLOW_TAG)) {
        Text(
            text = if (showAll) "RESOURCE FLOW · ALL" else "RESOURCE FLOW",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.textDim,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAll = !showAll },
        )

        if (rows.isEmpty()) {
            Text(
                text = "Nothing is flowing yet.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textFaint,
            )
            return@GameCard
        }

        for (definition in rows) {
            val id = definition.id
            val produced = rates.resourceGrossPerS[id]
            val consumed = rates.resourceConsumedPerS[id]
            val net = rates.resourcePerS[id]
            val held = state.resources[id]

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = definition.shortName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    modifier = Modifier.weight(1.1f),
                )
                Text(
                    text = formatNumber(held, format),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textDim,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "+${formatNumber(produced, format)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.good,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (consumed.gt(GameDecimal.ZERO)) "−${formatNumber(consumed, format)}" else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (consumed.gt(GameDecimal.ZERO)) colors.warning else colors.textFaint,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "=${formatNumber(net, format)}/s",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                    modifier = Modifier.weight(1.1f),
                )
            }
        }

        Text(
            text = "held · produced/s · consumed/s · net/s" +
                if (showAll) "" else " — tap for every resource",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textFaint,
        )
    }
}
