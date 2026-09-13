package com.earthgame.idle.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.earthgame.idle.domain.engine.computeDerived
import com.earthgame.idle.domain.engine.gameYearsToSeconds
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.model.createNewGame
import com.earthgame.idle.presentation.components.GameHeader
import com.earthgame.idle.presentation.components.RESOURCE_SCROLL_HINT_TAG
import com.earthgame.idle.presentation.theme.EarthTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The header's resource strip: it scrolls, and it now says so.
 *
 * The affordance is driven by the scroll state's own `maxValue`, so whether it
 * appears depends on measured text width — and text under Robolectric measures
 * narrower than on a device. These tests therefore pin the header to an
 * explicit width rather than relying on a screen qualifier, which is the only
 * way to be certain a case is really testing overflow rather than a font
 * metric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class GameHeaderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = 1_700_000_000_000L

    /** A run with every resource earned, which is what fills the strip. */
    private fun richState(): GameState = createNewGame(now).let {
        var resources = it.resources
        for (resource in ResourceId.entries) resources = resources.with(resource, gd("1.234e9"))
        it.copy(resources = resources, gameAgeSeconds = gameYearsToSeconds(12.0))
    }

    private fun setHeader(state: GameState = richState(), widthDp: Int) {
        val derived = computeDerived(state, now)
        compose.setContent {
            EarthTheme(themePreference = state.settings.darkMode, reducedAnimations = false) {
                Box(Modifier.width(widthDp.dp)) {
                    GameHeader(state = state, derived = derived, showResources = true)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun anOverflowingStripShowsTheScrollAffordance() {
        setHeader(widthDp = 140)
        compose.onNodeWithTag(RESOURCE_SCROLL_HINT_TAG).assertExists()
    }

    @Test
    fun anOverflowingStripSaysSoToAScreenReaderToo() {
        // The fade tells a sighted player there is more; the row's own label
        // has to tell everyone else the same thing.
        setHeader(widthDp = 140)
        compose.onNodeWithContentDescription("Resource balances, scroll sideways for more").assertExists()
    }

    @Test
    fun aStripThatFitsShowsNoAffordanceAtAll() {
        // Nothing is out of view, so an affordance would be pointing at nothing.
        setHeader(widthDp = 4000)
        compose.onNodeWithTag(RESOURCE_SCROLL_HINT_TAG).assertDoesNotExist()
        compose.onNodeWithContentDescription("Resource balances").assertExists()
    }

    @Test
    fun aSingleResourceNeverOverflowsHoweverNarrowTheScreen() {
        val sparse = createNewGame(now).let {
            it.copy(resources = it.resources.with(ResourceId.ENERGY, gd(50.0)))
        }
        setHeader(state = sparse, widthDp = 4000)
        compose.onNodeWithTag(RESOURCE_SCROLL_HINT_TAG).assertDoesNotExist()
    }

    @Test
    fun theStripStillScrollsToTheResourcesPastTheEdge() {
        setHeader(widthDp = 140)
        // Concrete is last in the strip and far off-screen at this width.
        // `performScrollTo` scrolls the ancestor scrollable to reach it, so this
        // fails outright if the affordance has cost the row its scrolling.
        compose.onNodeWithContentDescription("Concrete, 1.23B, 0 /s")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theAffordanceGoesAwayOnceTheEndIsReached() {
        // It marks what is out of view, so once the player has swiped all the
        // way over it must stop hanging above the last chip.
        setHeader(widthDp = 140)
        compose.onNodeWithTag(RESOURCE_SCROLL_HINT_TAG).assertExists()

        val row = compose.onNodeWithContentDescription("Resource balances, scroll sideways for more")
        repeat(4) {
            row.performTouchInput { swipeLeft() }
            compose.waitForIdle()
        }

        compose.onNodeWithTag(RESOURCE_SCROLL_HINT_TAG).assertDoesNotExist()
    }

    @Test
    fun theHeaderNamesTheEarthsAge() {
        setHeader(widthDp = 411)
        compose.onNodeWithContentDescription("Earth age").assertIsDisplayed()
    }
}
