package com.earthgame.idle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.earthgame.idle.platform.ads.MonetizationController
import com.earthgame.idle.presentation.EarthApp
import com.earthgame.idle.presentation.GameViewModel
import com.earthgame.idle.presentation.theme.EarthTheme

class MainActivity : ComponentActivity() {

    private val application: EarthApplication
        get() = getApplication() as EarthApplication

    private val viewModel: GameViewModel by viewModels {
        GameViewModel.Factory(application.saveRepository, application.haptics, application.audio)
    }

    /**
     * Created once, in [onCreate], and never inside a composable: the Mobile
     * Ads SDK must be brought up exactly once per process, and a controller
     * constructed during composition would be rebuilt on every recomposition
     * that outlived its key.
     */
    private lateinit var monetization: MonetizationController

    /**
     * Watches the *process*, not this activity.
     *
     * A configuration change destroys and recreates the activity, and treating
     * that as "the player left" would save and re-settle an absence on every
     * rotation. [ProcessLifecycleOwner] fires only when the app as a whole goes
     * to the background, which is the moment that actually matters: it is the
     * last reliable chance to write the save before Android may kill the
     * process, and the moment offline progress starts accruing.
     */
    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            viewModel.onEnterForeground()
        }

        override fun onStop(owner: LifecycleOwner) {
            viewModel.onEnterBackground()
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        viewModel.start()

        // Consent, then the SDK, then the banner — in that order, because the
        // Mobile Ads SDK may preload an ad the moment it is initialized, and
        // doing that before asking in the EEA/UK is the policy violation. The
        // flow runs off the launch path and nothing waits on it; the game is
        // fully playable, ad or no ad, before it finishes.
        monetization = MonetizationController(this)
        monetization.start()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            EarthTheme(
                themePreference = uiState.state.settings.darkMode,
                reducedAnimations = uiState.state.settings.reducedAnimations,
            ) {
                EarthApp(
                    uiState = uiState,
                    windowWidthSizeClass = calculateWindowSizeClass(this).widthSizeClass,
                    monetization = monetization,
                    onBuyTechnology = viewModel::buyTechnology,
                    onBuyPrestigeUpgrade = viewModel::buyPrestigeUpgrade,
                    onResetEarth = viewModel::resetEarth,
                    onStartChallenge = viewModel::startChallenge,
                    onAbandonChallenge = viewModel::abandonChallenge,
                    onUpdateSettings = viewModel::updateSettings,
                    onAdvanceTutorial = viewModel::advanceTutorial,
                    onSkipTutorial = viewModel::skipTutorial,
                    onDismissOfflineSummary = viewModel::dismissOfflineSummary,
                    onDismissCollapseSummary = viewModel::dismissCollapseSummary,
                    onDismissAchievementToast = viewModel::dismissAchievementToast,
                    onDismissEventToast = viewModel::dismissEventToast,
                    onDismissMilestoneToast = viewModel::dismissMilestoneToast,
                    onDismissOwnershipToast = viewModel::dismissOwnershipToast,
                    onDismissSaveWarnings = viewModel::dismissSaveWarnings,
                    // Back from Home leaves the game, rather than unwinding into
                    // an empty stack.
                    onExit = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        super.onDestroy()
    }
}
