package com.earthgame.idle.presentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.earthgame.idle.presentation.components.SideNavTestTag
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.engine.computeDerived
import com.earthgame.idle.domain.engine.gameYearsToSeconds
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.ThemePreference
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.presentation.navigation.Destination
import com.earthgame.idle.presentation.theme.EarthTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app shell, actually composed and interacted with.
 *
 * These run under Robolectric rather than on a device: there is no
 * hardware-accelerated emulator in this build environment, and a UI that is
 * never executed is a UI that has never been shown to work. The same tests also
 * exist as an instrumented suite (`app/src/androidTest`) for running against
 * real hardware.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class EarthAppScreenTest {

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
        widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        onBuy: (String, BuyQuantity) -> Unit = { _, _ -> },
        onReset: () -> Unit = {},
        onExit: () -> Unit = {},
    ) {
        val derived: DerivedState = computeDerived(state, now)
        compose.setContent {
            EarthTheme(
                themePreference = state.settings.darkMode,
                reducedAnimations = state.settings.reducedAnimations,
            ) {
                EarthApp(
                    uiState = GameUiState(state = state, derived = derived, loaded = true),
                    windowWidthSizeClass = widthSizeClass,
                    monetization = null,
                    onBuyTechnology = onBuy,
                    onBuyPrestigeUpgrade = {},
                    onResetEarth = onReset,
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

    private fun navigateTo(destination: Destination) {
        compose.onNodeWithContentDescription(destination.title).performClick()
        compose.waitForIdle()
    }

    /**
     * Scrolls the content list to a node before asserting on it.
     *
     * These screens are `LazyColumn`s, which only compose what is on screen —
     * anything below the fold genuinely does not exist until scrolled to, so a
     * test that skips this is asserting about a phone-sized viewport rather
     * than about the screen.
     */
    private fun scrollContentTo(text: String, substring: Boolean = false) {
        compose.onNodeWithTag(ContentListTestTag)
            .performScrollToNode(hasText(text, substring = substring))
        compose.waitForIdle()
    }

    @Test
    fun homeRendersLivePlanetaryData() {
        setContent()
        compose.onNodeWithText("🎯 Objective").assertIsDisplayed()
        compose.onNodeWithText("🌡️ Planetary Status").assertIsDisplayed()
        compose.onNodeWithText("⚙️ Economy").assertIsDisplayed()
        scrollContentTo("📰 World News")
        compose.onNodeWithText("📰 World News").assertIsDisplayed()
    }

    @Test
    fun everyDestinationIsReachableAndRenders() {
        setContent()
        // Nine tabs have to fit on a phone, so each carries its full name as an
        // accessibility label — which is also how they are found here.
        for (destination in Destination.navigationEntries) {
            navigateTo(destination)
        }
        navigateTo(Destination.HOME)
        compose.onNodeWithText("🎯 Objective").assertIsDisplayed()
    }

    @Test
    fun atmosphereShowsEveryGas() {
        setContent()
        navigateTo(Destination.ATMOSPHERE)
        compose.onNodeWithText("Carbon Dioxide (CO₂)").assertIsDisplayed()
        scrollContentTo("Methane (CH₄)")
        compose.onNodeWithText("Methane (CH₄)").assertIsDisplayed()
        scrollContentTo("Total Radiative Forcing", substring = true)
        compose.onNodeWithText("Total Radiative Forcing", substring = true).assertIsDisplayed()
    }

    @Test
    fun productionSellsGeneratorsAndBuyingReachesTheGame() {
        val purchases = mutableListOf<Pair<String, BuyQuantity>>()
        setContent(onBuy = { id, quantity -> purchases += id to quantity })

        navigateTo(Destination.PRODUCTION)
        compose.onNodeWithText("Natural Fire").assertIsDisplayed()
        compose.onAllNodesWithText("Buy", substring = true).onFirst().performClick()
        compose.waitForIdle()

        assertTrue("a tap should reach the game", purchases.isNotEmpty())
    }

    @Test
    fun technologyAndProductionSellDisjointLists() {
        setContent()
        navigateTo(Destination.TECHNOLOGY)
        // Natural Fire is a generator, so the research tree must never list it.
        compose.onAllNodesWithText("Natural Fire").assertCountEquals(0)
    }

    @Test
    fun backUnwindsScreenHistoryAndOnlyLeavesFromHome() {
        var exited = false
        setContent(onExit = { exited = true })

        navigateTo(Destination.ATMOSPHERE)
        navigateTo(Destination.SETTINGS)

        pressBack()
        assertFalse("back from Settings must not leave the app", exited)
        pressBack()
        assertFalse("back from Atmosphere must not leave the app", exited)
        compose.onNodeWithText("🎯 Objective").assertIsDisplayed()

        pressBack()
        assertTrue("back from Home should leave the app", exited)
    }

    @Test
    fun settingsExposesEveryToggle() {
        setContent()
        navigateTo(Destination.SETTINGS)

        compose.onNodeWithText("Compact (1.2M)").assertIsDisplayed()
        for (label in listOf("Follow system", "Reduced Animations", "Offline Progress", "Vibration")) {
            scrollContentTo(label)
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun theResetButtonIsInertUntilTheEarthIsDead() {
        var resets = 0
        setContent(onReset = { resets++ })

        scrollContentTo("Reset available once Earth collapses")
        compose.onNodeWithText("Reset available once Earth collapses").performClick()
        compose.waitForIdle()
        assertTrue("a living Earth must not be resettable", resets == 0)
    }

    @Test
    fun aDeadEarthIsAnnouncedAndOffersAReset() {
        val collapsed = playableState().copy(
            collapsed = true,
            atmosphere = playableState().atmosphere.with(GasId.CO2, gd(6000.0)),
        )
        setContent(state = collapsed)

        compose.onNodeWithText("☠️ EARTH HAS BECOME UNINHABITABLE").assertIsDisplayed()
        compose.onNodeWithText("RESET EARTH").assertIsDisplayed()
    }

    @Test
    fun theTutorialGreetsANewPlayerWithoutBlockingTheGame() {
        val fresh = createNewGame(now)
        setContent(state = fresh)

        compose.onNodeWithText("WELCOME TO EARTH").assertIsDisplayed()
        // It is a banner, not a modal: the game underneath is still reachable.
        compose.onNodeWithText("🎯 Objective").assertIsDisplayed()
    }

    @Test
    fun lightThemeRendersEveryScreen() {
        // A palette mistake shows up as a crash or an invisible screen, so the
        // light theme gets the same walk-through as the dark one.
        setContent(state = playableState().let { it.copy(settings = it.settings.copy(darkMode = ThemePreference.LIGHT)) })
        for (destination in Destination.navigationEntries) {
            navigateTo(destination)
        }
        navigateTo(Destination.HOME)
        compose.onNodeWithText("🎯 Objective").assertIsDisplayed()
    }

    // ------------------------------------------------------- the Earth's age --

    @Test
    fun theHeaderAndHomeBothShowTheEarthsSimulatedAge() {
        // 12 years and 4 months of simulated time — the planet's own age, which
        // is not the same number as how long the player has been playing.
        val aged = playableState().copy(
            gameAgeSeconds = gameYearsToSeconds(12.0) + gameYearsToSeconds(4.0 / 12),
        )
        setContent(state = aged)

        compose.onNodeWithContentDescription("Earth age").assertIsDisplayed()
        compose.onAllNodesWithText("12y 4m 0d").onFirst().assertIsDisplayed()

        compose.onNodeWithText("Earth Age").assertIsDisplayed()
    }

    @Test
    fun aBrandNewEarthReadsAsAgeZero() {
        setContent(state = playableState().copy(gameAgeSeconds = 0.0))
        compose.onAllNodesWithText("0d").onFirst().assertIsDisplayed()
    }

    // ------------------------------------------------------------- resources --

    @Test
    fun theSteelResourceIsShownToThePlayerAsMetals() {
        val withMetals = playableState().let {
            it.copy(resources = it.resources.with(ResourceId.STEEL, gd(1234.0)))
        }
        setContent(state = withMetals)
        navigateTo(Destination.PRODUCTION)

        // The header chip names it, and nothing anywhere calls it Steel.
        compose.onNodeWithContentDescription("Metals, 1.23K, 0 /s").assertIsDisplayed()
        compose.onAllNodesWithText("STEEL").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun aWideWindowUsesTheSideRailInsteadOfTheBottomBar() {
        // A real tablet window: forcing the Expanded layout into a phone-sized
        // one would be testing a configuration that cannot occur.
        setContent(widthSizeClass = WindowWidthSizeClass.Expanded)
        // The rail shows full names rather than the phone bar's abbreviations.
        compose.onNodeWithText("Atmosphere").assertIsDisplayed()

        // The rail scrolls too, so the last destinations need reaching.
        compose.onNodeWithTag(SideNavTestTag)
            .performScrollToNode(hasContentDescription(Destination.STATISTICS.title))
        navigateTo(Destination.STATISTICS)
        // A stat row rather than the section heading: `SectionLabel` uppercases
        // its text the way the web build's CSS did, so the heading on screen is
        // never the string the screen source passes in.
        compose.onNodeWithText("Total Resets", substring = true).assertIsDisplayed()
    }
}
