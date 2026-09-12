package com.earthgame.idle.presentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.engine.computeDerived
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.presentation.navigation.Destination
import com.earthgame.idle.presentation.theme.EarthTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app shell on a device: that every destination renders live data, that
 * navigation and Back behave, and that a purchase reaches the game.
 *
 * Uses [createAndroidComposeRule] rather than the plain compose rule because
 * the Back behaviour is the point of one of these tests, and that needs a real
 * activity with an `onBackPressedDispatcher`.
 */
@RunWith(AndroidJUnit4::class)
class EarthAppUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = 1_700_000_000_000L

    private fun playableState(): GameState = createNewGame(now).let {
        it.copy(
            techOwned = mapOf("natural_fire" to 3),
            // Enough to make the Buy buttons live.
            resources = it.resources
                .with(ResourceId.ENERGY, gd("1e6"))
                .with(ResourceId.RESEARCH, gd("1e6")),
            // Skipped, so the tutorial banner does not cover the screen under test.
            tutorial = it.tutorial.copy(skipped = true),
        )
    }

    private fun setContent(
        state: GameState = playableState(),
        onBuy: (String, BuyQuantity) -> Unit = { _, _ -> },
        onExit: () -> Unit = {},
    ) {
        val derived: DerivedState = computeDerived(state, now)
        compose.setContent {
            EarthTheme(themePreference = state.settings.darkMode) {
                EarthApp(
                    uiState = GameUiState(state = state, derived = derived, loaded = true),
                    windowWidthSizeClass = WindowWidthSizeClass.Compact,
                    monetization = null,
                    onBuyTechnology = onBuy,
                    onBuyPrestigeUpgrade = {},
                    onResetEarth = {},
                    onStartChallenge = {},
                    onAbandonChallenge = {},
                    onUpdateSettings = {},
                    onAdvanceTutorial = {},
                    onSkipTutorial = {},
                    onDismissOfflineSummary = {},
                    onDismissCollapseSummary = {},
                    onDismissAchievementToast = {},
                    onDismissEventToast = {},
                    onDismissMilestoneToast = {},
                    onDismissOwnershipToast = {},
                    onDismissSaveWarnings = {},
                    onExit = onExit,
                )
            }
        }
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun homeRendersLivePlanetaryData() {
        setContent()
        compose.onNodeWithText("🎯 Objective").assertIsDisplayed()
        compose.onNodeWithText("🌡️ Planetary Status").assertIsDisplayed()
        compose.onNodeWithText("⚙️ Economy").assertIsDisplayed()
    }

    @Test
    fun everyDestinationIsReachableAndRenders() {
        setContent()
        // Nine tabs have to fit on a phone, so each is labelled with its full
        // name for accessibility — which is also how they are found here.
        for (destination in Destination.navigationEntries) {
            compose.onNodeWithContentDescription(destination.title).performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithContentDescription(Destination.HOME.title).performClick()
        compose.onNodeWithText("🎯 Objective").assertIsDisplayed()
    }

    @Test
    fun productionSellsGeneratorsAndBuyingReachesTheGame() {
        val purchases = mutableListOf<Pair<String, BuyQuantity>>()
        setContent(onBuy = { id, quantity -> purchases += id to quantity })

        compose.onNodeWithContentDescription(Destination.PRODUCTION.title).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Natural Fire").assertIsDisplayed()
        compose.onAllNodesWithText("Buy", substring = true).onFirst().performClick()
        compose.waitForIdle()

        assertTrue("a tap should reach the game", purchases.isNotEmpty())
    }

    @Test
    fun backUnwindsScreenHistoryAndOnlyLeavesFromHome() {
        var exited = false
        setContent(onExit = { exited = true })

        compose.onNodeWithContentDescription(Destination.ATMOSPHERE.title).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(Destination.SETTINGS.title).performClick()
        compose.waitForIdle()

        // Settings -> Atmosphere -> Home, without leaving.
        pressBack()
        assertFalse("back from Settings must not leave the app", exited)
        pressBack()
        assertFalse("back from Atmosphere must not leave the app", exited)
        compose.onNodeWithText("🎯 Objective").assertIsDisplayed()

        // ...and only then does it exit.
        pressBack()
        assertTrue("back from Home should leave the app", exited)
    }

    @Test
    fun technologyAndProductionSellDisjointLists() {
        setContent()

        compose.onNodeWithContentDescription(Destination.TECHNOLOGY.title).performClick()
        compose.waitForIdle()
        // Natural Fire is a generator, so the research tree must never list it.
        compose.onAllNodesWithText("Natural Fire").assertCountEquals(0)
    }

    @Test
    fun settingsExposesEveryToggle() {
        setContent()
        compose.onNodeWithContentDescription(Destination.SETTINGS.title).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Compact (1.2M)").assertIsDisplayed()
        compose.onNodeWithText("Reduced Animations").assertIsDisplayed()
        compose.onNodeWithText("Offline Progress").assertIsDisplayed()
        compose.onNodeWithText("Vibration").assertIsDisplayed()
    }
}
