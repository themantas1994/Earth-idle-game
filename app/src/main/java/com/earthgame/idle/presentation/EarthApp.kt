package com.earthgame.idle.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.earthgame.idle.domain.model.GameState
import com.earthgame.idle.domain.technologies.TechBranch
import com.earthgame.idle.platform.ads.MonetizationController
import com.earthgame.idle.presentation.components.AchievementToast
import com.earthgame.idle.presentation.components.BannerAd
import com.earthgame.idle.presentation.components.BottomNav
import com.earthgame.idle.presentation.components.CollapseSummaryDialog
import com.earthgame.idle.presentation.components.ConfirmResetDialog
import com.earthgame.idle.presentation.components.EventToast
import com.earthgame.idle.presentation.components.GameHeader
import com.earthgame.idle.presentation.components.MilestoneToast
import com.earthgame.idle.presentation.components.OfflineProgressDialog
import com.earthgame.idle.presentation.components.OwnershipToast
import com.earthgame.idle.presentation.components.SaveWarningDialog
import com.earthgame.idle.presentation.components.SideNav
import com.earthgame.idle.presentation.components.StormToast
import com.earthgame.idle.presentation.components.TutorialBanner
import com.earthgame.idle.presentation.components.UninhabitableDialog
import com.earthgame.idle.presentation.navigation.Destination
import com.earthgame.idle.presentation.screens.AboutScreen
import com.earthgame.idle.presentation.screens.AchievementsScreen
import com.earthgame.idle.presentation.screens.AtmosphereScreen
import com.earthgame.idle.presentation.screens.ChallengesScreen
import com.earthgame.idle.presentation.screens.HomeScreen
import com.earthgame.idle.presentation.screens.LicensesScreen
import com.earthgame.idle.presentation.screens.PrestigeScreen
import com.earthgame.idle.presentation.screens.ProductionScreen
import com.earthgame.idle.presentation.screens.SettingsScreen
import com.earthgame.idle.presentation.screens.StatisticsScreen
import com.earthgame.idle.presentation.screens.TechnologyScreen
import com.earthgame.idle.presentation.theme.Dimens
import com.earthgame.idle.presentation.theme.gameColors
import com.earthgame.idle.presentation.visualization.EnvironmentOverlay
import com.earthgame.idle.presentation.visualization.environmentalVisualizationOf

/**
 * The app shell: header, current screen, navigation, and everything that
 * floats over them.
 *
 * ## Navigation and Back
 *
 * Destinations are tracked as an explicit back stack rather than through
 * `NavHost`. The reason is Back: on a nine-tab game, Android's Back has to
 * unwind the screens the player actually visited and only leave the app from
 * Home — the default single-destination behaviour closed the game on any
 * mis-swipe, which was one of the concrete complaints about the previous
 * build. The stack survives configuration changes through [rememberSaveable].
 *
 * ## Adaptive layout
 *
 * On a phone this is the portrait column the game was designed for. On a
 * tablet or an unfolded foldable the navigation moves to a rail on the left and
 * the content column is centred and width-capped, so the extra width becomes
 * margin rather than absurdly long stat rows.
 */
@Composable
fun EarthApp(
    uiState: GameUiState,
    windowWidthSizeClass: WindowWidthSizeClass,
    monetization: MonetizationController?,
    onBuyTechnology: (String, BuyQuantity) -> Unit,
    onBuyPrestigeUpgrade: (String) -> Unit,
    onResetEarth: () -> Unit,
    onStartChallenge: (String) -> Unit,
    onAbandonChallenge: () -> Unit,
    onUpdateSettings: ((com.earthgame.idle.domain.model.Settings) -> com.earthgame.idle.domain.model.Settings) -> Unit,
    onAdvanceTutorial: () -> Unit,
    onSkipTutorial: () -> Unit,
    onDismissOfflineSummary: () -> Unit,
    onDismissCollapseSummary: () -> Unit,
    onDismissAchievementToast: () -> Unit,
    onDismissEventToast: () -> Unit,
    onDismissMilestoneToast: () -> Unit,
    onDismissOwnershipToast: () -> Unit,
    onDismissStormToast: () -> Unit,
    onDismissSaveWarnings: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = gameColors
    val state = uiState.state

    // Screens the player navigated away from, most recent last. Back unwinds
    // this before it is allowed to leave the app.
    var current by rememberSaveable { mutableStateOf(Destination.START) }
    val history = rememberSaveable(saver = destinationStackSaver) { mutableListOf() }

    var buyQuantity by rememberSaveable { mutableStateOf(BuyQuantity.MAX) }
    var techBranchFilter by rememberSaveable { mutableStateOf<TechBranch?>(null) }
    var confirmingReset by remember { mutableStateOf(false) }

    // Which environmental view the globe is showing, and which storm the
    // player has opened. Both survive a rotation; neither is game state, so
    // neither goes anywhere near the save.
    var overlayRoute by rememberSaveable { mutableStateOf(EnvironmentOverlay.DEFAULT.id) }
    var selectedStormId by rememberSaveable { mutableStateOf<String?>(null) }
    val overlay = EnvironmentOverlay.fromId(overlayRoute)

    // The globe's snapshot of the world, rebuilt only when the world changes.
    //
    // Keyed on the state and the derived bundle rather than on a clock, and
    // timed by `lastTickAt` — the simulation's own notion of now — so nothing
    // here ever reads the device clock and a paused game produces a still
    // globe rather than one that drifts on by itself.
    val environment = remember(state, uiState.derived) {
        environmentalVisualizationOf(state, uiState.derived, state.lastTickAt)
    }

    // A storm that has dissipated cannot stay selected, or the card would
    // outlive the thing it describes. Derived rather than cleared, so nothing
    // writes state during composition: the stale id simply stops resolving, and
    // the next tap overwrites it.
    val activeStormId = selectedStormId?.takeIf { id -> environment.storms.any { it.id == id } }

    fun navigate(destination: Destination) {
        if (destination == current) return
        history.add(current)
        current = destination
    }

    BackHandler(enabled = true) {
        val previous = history.removeLastOrNull()
        if (previous != null) current = previous else onExit()
    }

    if (!uiState.loaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
            contentAlignment = Alignment.Center,
        ) {
            Text("Loading Earth…", style = MaterialTheme.typography.bodyMedium, color = colors.textDim)
        }
        return
    }

    val wide = windowWidthSizeClass != WindowWidthSizeClass.Compact

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                SideNav(current = current, onSelect = ::navigate)
            }

            Column(Modifier.fillMaxSize()) {
                GameHeader(
                    state = state,
                    derived = uiState.derived,
                    showResources = current != Destination.HOME && current.inNavigation,
                )

                TutorialBanner(
                    state = state,
                    onNavigate = ::navigate,
                    onAdvance = onAdvanceTutorial,
                    onSkip = onSkipTutorial,
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    // On a wide screen the content column is capped rather than
                    // stretched: a stat row a tablet wide is unreadable.
                    //
                    // The tag identifies the scrolling content list to UI tests,
                    // which otherwise cannot tell it apart from the side rail.
                    val contentModifier = if (wide) {
                        Modifier.widthIn(max = 640.dp)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                        .padding(horizontal = Dimens.ScreenPadding)
                        .testTag(ContentListTestTag)

                    val contentPadding = PaddingValues(
                        top = Dimens.CardSpacing,
                        bottom = Dimens.CardSpacing * 2,
                    )

                    when (current) {
                        Destination.HOME -> HomeScreen(
                            state = state,
                            derived = uiState.derived,
                            environment = environment,
                            overlay = overlay,
                            onOverlayChange = { overlayRoute = it.id },
                            selectedStormId = activeStormId,
                            onStormSelected = { selectedStormId = it },
                            onNavigate = ::navigate,
                            onResetEarth = {
                                if (state.settings.confirmReset) confirmingReset = true else onResetEarth()
                            },
                            contentPadding = contentPadding,
                            modifier = contentModifier,
                        )

                        Destination.ATMOSPHERE -> AtmosphereScreen(
                            state = state,
                            derived = uiState.derived,
                            contentPadding = contentPadding,
                            modifier = contentModifier,
                        )

                        Destination.TECHNOLOGY -> TechnologyScreen(
                            state = state,
                            derived = uiState.derived,
                            activeBranch = techBranchFilter,
                            onBranchChange = { techBranchFilter = it },
                            onBuy = onBuyTechnology,
                            contentPadding = contentPadding,
                            modifier = contentModifier,
                        )

                        Destination.PRODUCTION -> ProductionScreen(
                            state = state,
                            derived = uiState.derived,
                            quantity = buyQuantity,
                            onQuantityChange = { buyQuantity = it },
                            onBuy = onBuyTechnology,
                            contentPadding = contentPadding,
                            modifier = contentModifier,
                        )

                        Destination.PRESTIGE -> PrestigeScreen(
                            state = state,
                            onBuyUpgrade = onBuyPrestigeUpgrade,
                            contentPadding = contentPadding,
                            modifier = contentModifier,
                        )

                        Destination.CHALLENGES -> ChallengesScreen(
                            state = state,
                            onStartChallenge = onStartChallenge,
                            onAbandonChallenge = onAbandonChallenge,
                            contentPadding = contentPadding,
                            modifier = contentModifier,
                        )

                        Destination.ACHIEVEMENTS -> AchievementsScreen(
                            state = state,
                            contentPadding = contentPadding,
                            modifier = contentModifier,
                        )

                        Destination.STATISTICS -> StatisticsScreen(
                            state = state,
                            contentPadding = contentPadding,
                            modifier = contentModifier,
                        )

                        Destination.SETTINGS -> SettingsScreen(
                            state = state,
                            onUpdateSettings = onUpdateSettings,
                            adPrivacyAvailable = monetization?.privacyOptionsAvailable == true,
                            onOpenAdPrivacy = { monetization?.showPrivacyOptions() },
                            onOpenAbout = { navigate(Destination.ABOUT) },
                            contentPadding = contentPadding,
                            modifier = contentModifier,
                        )

                        Destination.ABOUT -> AboutScreen(
                            adPrivacyAvailable = monetization?.privacyOptionsAvailable == true,
                            onOpenAdPrivacy = { monetization?.showPrivacyOptions() },
                            onOpenLicenses = { navigate(Destination.LICENSES) },
                            contentPadding = contentPadding,
                            modifier = contentModifier,
                        )

                        Destination.LICENSES -> LicensesScreen(
                            contentPadding = contentPadding,
                            modifier = contentModifier,
                        )
                    }
                }

                // The banner sits above the nav, never over it: AdMob policy
                // forbids placing an ad flush against a control the player is
                // tapping, and a mistap that lands on the ad is an accidental
                // click.
                BannerAd(
                    controller = monetization,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                if (!wide) {
                    BottomNav(current = current, onSelect = ::navigate)
                } else {
                    Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
        }

        // Toasts float over the content rather than displacing it, so a headline
        // arriving mid-purchase never moves the button out from under a thumb.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            AchievementToast(uiState.newAchievements.lastOrNull(), onDismissAchievementToast)
            MilestoneToast(uiState.activeMilestoneToast, onDismissMilestoneToast)
            EventToast(uiState.activeEventToast, onDismissEventToast)
            OwnershipToast(uiState.activeOwnershipToast, onDismissOwnershipToast)
            StormToast(uiState.activeStormToast, onDismissStormToast)
        }
    }

    // --- Dialogs, in the order they take precedence ---

    if (uiState.recoveredFromBackup || uiState.saveWasCorrupted) {
        SaveWarningDialog(
            recoveredFromBackup = uiState.recoveredFromBackup,
            onDismiss = onDismissSaveWarnings,
        )
    }

    uiState.offlineSummary?.let { summary ->
        OfflineProgressDialog(
            summary = summary,
            format = state.settings.numberFormat,
            onDismiss = onDismissOfflineSummary,
        )
    }

    uiState.collapseSummary?.let { summary ->
        CollapseSummaryDialog(
            summary = summary,
            format = state.settings.numberFormat,
            onDismiss = onDismissCollapseSummary,
        )
    }

    if (state.collapsed && uiState.collapseSummary == null) {
        UninhabitableDialog(
            runStats = state.runStats,
            survivedSeconds = (state.lastTickAt - state.runStartedAt) / 1000.0,
            format = state.settings.numberFormat,
            confirmRequired = state.settings.confirmReset,
            onReset = onResetEarth,
        )
    }

    if (confirmingReset) {
        ConfirmResetDialog(
            onConfirm = {
                confirmingReset = false
                onResetEarth()
            },
            onDismiss = { confirmingReset = false },
        )
    }
}

/** Identifies the scrolling content list, so UI tests can scroll it deliberately. */
const val ContentListTestTag = "earth:content"

/** Persists the navigation back stack across configuration changes and process death. */
private val destinationStackSaver = androidx.compose.runtime.saveable.listSaver<MutableList<Destination>, String>(
    save = { it.map(Destination::route) },
    restore = { routes -> routes.map(Destination::fromRoute).toMutableList() },
)
