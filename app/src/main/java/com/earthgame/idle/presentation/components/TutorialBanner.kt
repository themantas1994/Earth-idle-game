package com.earthgame.idle.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.presentation.navigation.Destination
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors
import kotlinx.coroutines.delay

/**
 * One step of the opening tutorial.
 *
 * [autoAdvanceWhen] steps wait for the player to do the thing in-game and then
 * move on by themselves; the rest wait for the button.
 */
private data class TutorialStep(
    val title: String,
    val body: String,
    val cta: String,
    val navigateTo: Destination? = null,
    val autoAdvanceWhen: ((GameState) -> Boolean)? = null,
)

private fun owns(state: GameState, id: String) = (state.techOwned[id] ?: 0) > 0

private val STEPS = listOf(
    TutorialStep(
        title = "WELCOME TO EARTH",
        body = "Your objective is simple. Build civilization. Advance technology. Produce " +
            "greenhouse gases. Heat the planet. When Earth becomes uninhabitable... start " +
            "again. Each destroyed Earth makes the next civilization faster.",
        cta = "Let's begin",
    ),
    TutorialStep(
        title = "Step 1 — Your First Fire",
        body = "A lightning strike left a tree burning, and you have kept it alight. That " +
            "Natural Fire is already producing Energy for you — nothing to tap, nothing to " +
            "hold. Watch the Economy card fill up.",
        cta = "Continue",
        navigateTo = Destination.HOME,
        autoAdvanceWhen = { it.resources[com.earthgame.idle.domain.model.ResourceId.ENERGY].gt(com.earthgame.idle.domain.engine.GameDecimal.ZERO) },
    ),
    TutorialStep(
        title = "Step 2 — Build More Fires",
        body = "The Production tab is where you buy buildings. Spend your Energy on a second " +
            "Natural Fire, then a Controlled Fire — every fire you light makes the next one " +
            "affordable sooner.",
        cta = "Continue",
        navigateTo = Destination.PRODUCTION,
        autoAdvanceWhen = { owns(it, "controlled_fire") },
    ),
    TutorialStep(
        title = "Every Tenth One Is Free Money",
        body = "Look under any building on the Production tab: a thin bar counting toward its " +
            "next bonus. Every tenth copy you own of a building doubles that building's " +
            "output — permanently, for the rest of the run. Buy Max is the default for " +
            "exactly this reason.",
        cta = "Good to know",
        navigateTo = Destination.PRODUCTION,
    ),
    TutorialStep(
        title = "Prices Never Move",
        body = "One promise worth knowing up front: nothing in this game ever gets more " +
            "expensive behind your back. A price you have been quoted is the price you pay " +
            "whenever you come back for it. The only thing that raises a building's price is " +
            "you, buying more of that same building.",
        cta = "Understood",
    ),
    TutorialStep(
        title = "First Emissions",
        body = "Your fires are now producing CO₂ around the clock. Check the Atmosphere tab " +
            "any time to see exactly how much heating each gas is responsible for.",
        cta = "Got it",
    ),
    TutorialStep(
        title = "Step 3 — Research",
        body = "The Technology tab is the research tree: one-time unlocks and permanent " +
            "multipliers, paid for with Research. It never sells buildings — those always " +
            "live on Production. Unlock Cooking to begin.",
        cta = "Continue",
        navigateTo = Destination.TECHNOLOGY,
        autoAdvanceWhen = { owns(it, "cooking") },
    ),
    TutorialStep(
        title = "Step 4 — Two Tabs, One Loop",
        body = "That is the whole game: research an unlock on Technology, then build what it " +
            "unlocked on Production. Work toward Early Agriculture — farms bring methane from " +
            "livestock and nitrous oxide from fertilizer.",
        cta = "Continue",
        navigateTo = Destination.TECHNOLOGY,
        autoAdvanceWhen = { owns(it, "early_agriculture") },
    ),
    TutorialStep(
        title = "Step 5 — Industrial Revolution",
        body = "Research the Steam Engine to enter the Industrial era, opening up Coal Mining, " +
            "Factories, and far dirtier production than fire ever managed.",
        cta = "Continue",
        navigateTo = Destination.TECHNOLOGY,
        autoAdvanceWhen = { owns(it, "steam_engine") },
    ),
    TutorialStep(
        title = "Step 6 — Fossil Fuels",
        body = "Build Oil Drilling on the Production tab. Oil unlocks an entire branch of " +
            "increasingly powerful — and increasingly dirty — technology.",
        cta = "Continue",
        navigateTo = Destination.PRODUCTION,
        autoAdvanceWhen = { owns(it, "oil_drilling") },
    ),
    TutorialStep(
        title = "Step 7 — Watch the World React",
        body = "Your Radiative Forcing and Habitability are already moving. As they do, the " +
            "World News feed on the Home screen will start reporting what your emissions are " +
            "doing to the planet.",
        cta = "Continue",
        navigateTo = Destination.HOME,
    ),
    TutorialStep(
        title = "Step 8 — The Long Game",
        body = "This Earth will take a few days of real time to kill. It keeps running while " +
            "you are away — up to twelve hours banked at a time — so check in, spend what has " +
            "piled up, and let it burn. When habitability reaches zero you can RESET EARTH " +
            "and bank Earth Points. Those make the next civilization faster, and a faster run " +
            "is worth far more points than a slow one.",
        cta = "Start playing",
    ),
)

val TUTORIAL_STEP_COUNT = STEPS.size

/**
 * A non-blocking banner rather than a modal overlay: several steps ask the
 * player to press something in the game underneath, so the tutorial must never
 * capture the taps meant for it.
 */
@Composable
fun TutorialBanner(
    state: GameState,
    onNavigate: (Destination) -> Unit,
    onAdvance: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors
    val tutorial = state.tutorial
    if (tutorial.completed || tutorial.skipped) return
    val step = STEPS.getOrNull(tutorial.step) ?: return

    LaunchedEffect(tutorial.step) {
        step.navigateTo?.let(onNavigate)
    }

    val waitingOnAction = step.autoAdvanceWhen?.invoke(state) == false
    LaunchedEffect(tutorial.step, waitingOnAction) {
        if (step.autoAdvanceWhen != null && !waitingOnAction) {
            // A short beat so the player sees what they did before the banner moves on.
            delay(600)
            onAdvance()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.card)
            .border(1.dp, colors.accent, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = "TUTORIAL · ${tutorial.step + 1}/${STEPS.size}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent,
        )
        Text(step.title, style = MaterialTheme.typography.titleSmall, color = colors.text)
        Text(step.body, style = MaterialTheme.typography.bodySmall, color = colors.textDim)

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (waitingOnAction) {
                Text(
                    text = "Waiting for you to do it in-game…",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Button(
                    onClick = if (tutorial.step >= STEPS.size - 1) onSkip else onAdvance,
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.background,
                    ),
                ) { Text(step.cta) }
                Spacer(Modifier.weight(1f))
            }
            TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                Text("Skip", color = colors.textFaint)
            }
        }
    }
}
