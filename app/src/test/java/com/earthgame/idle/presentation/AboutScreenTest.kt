package com.earthgame.idle.presentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.earthgame.idle.BuildConfig
import com.earthgame.idle.domain.engine.DerivedState
import com.earthgame.idle.domain.engine.computeDerived
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.presentation.navigation.Destination
import com.earthgame.idle.presentation.theme.EarthTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * About and the licence notices: reachable, populated, and legally honest.
 *
 * These two screens are the only place a player can read what the app is built
 * on and what it does with their data, so "it renders" is not the interesting
 * assertion — "it says the true thing" is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AboutScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = 1_700_000_000_000L

    private fun setContent(state: GameState = createNewGame(now).let { it.copy(tutorial = it.tutorial.copy(skipped = true)) }) {
        val derived: DerivedState = computeDerived(state, now)
        compose.setContent {
            EarthTheme(themePreference = state.settings.darkMode) {
                EarthApp(
                    uiState = GameUiState(state = state, derived = derived, loaded = true),
                    windowWidthSizeClass = WindowWidthSizeClass.Compact,
                    monetization = null,
                    onBuyTechnology = { _, _ -> },
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
                    onDismissStormToast = {},
                    onDismissSaveWarnings = {},
                    onExit = {},
                )
            }
        }
    }

    private fun openAbout() {
        compose.onNodeWithContentDescription(Destination.SETTINGS.title).performClick()
        scrollTo("About EARTH")
        compose.onNodeWithText("About EARTH").performClick()
        compose.waitForIdle()
    }

    @Test
    fun aboutIsReachableFromSettingsAndShowsTheRealBuild() {
        setContent()
        openAbout()

        compose.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
        compose.onNodeWithText(BuildConfig.APPLICATION_ID).assertIsDisplayed()
    }

    @Test
    fun aboutStatesTheProjectLicenceRatherThanAssumingOne() {
        // The repository carries no LICENSE file, so the app must not imply
        // permissions nobody granted. If a licence is ever added, this test is
        // the reminder that the string has to change with it.
        setContent()
        openAbout()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val declared = context.getString(com.earthgame.idle.R.string.project_license)
        scrollTo(declared)
        compose.onNodeWithText(declared).assertIsDisplayed()
    }

    @Test
    fun aboutIsNotATabInTheNavigationBar() {
        // Nine destinations already have to fit across a 360 dp phone; About and
        // the notices are reached from Settings, not from the bar.
        assertTrue(Destination.navigationEntries.none { it == Destination.ABOUT })
        assertTrue(Destination.navigationEntries.none { it == Destination.LICENSES })
        assertTrue(Destination.entries.contains(Destination.ABOUT))
        assertTrue(Destination.entries.contains(Destination.LICENSES))
    }

    @Test
    fun backUnwindsLicencesToAboutToSettings() {
        setContent()
        openAbout()
        scrollTo("Open Source Licenses")
        compose.onNodeWithText("Open Source Licenses").performClick()
        compose.waitForIdle()
        // The notices themselves, not a placeholder. The file is read off the
        // main thread, so wait for it rather than assuming the first frame has
        // it.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("THIRD-PARTY NOTICES", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Each screen is a real back-stack entry: Back returns to the previous
        // one rather than closing the game, which is the whole reason the shell
        // keeps its own stack.
        pressBack()
        scrollTo("Open Source Licenses")
        compose.onNodeWithText("Open Source Licenses").assertIsDisplayed()

        pressBack()
        scrollTo("About EARTH")
        compose.onNodeWithText("About EARTH").assertIsDisplayed()
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithTag(ContentListTestTag).performScrollToNode(hasText(text))
        compose.waitForIdle()
    }

    @Test
    fun theBundledNoticesAreRealAndCoverTheShippedLicences() {
        // The asset is generated from THIRD_PARTY_NOTICES.txt at build time. If
        // that wiring breaks, the in-app licence screen silently shows nothing —
        // which is exactly the failure attribution requirements are about.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notices = context.assets.open("third_party_notices.txt").bufferedReader().use { it.readText() }

        assertTrue("Notices asset is suspiciously small", notices.length > 10_000)
        assertTrue(notices.contains("Apache License"))
        assertTrue(notices.contains("MIT License"))
        assertTrue(notices.contains("BSD 3-CLAUSE LICENSE"))
        assertTrue(notices.contains("com.google.android.ump:user-messaging-platform"))
        assertTrue(notices.contains("com.google.android.gms:play-services-ads"))
        assertTrue(notices.contains("org.jetbrains.kotlinx:kotlinx-serialization-json"))
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
