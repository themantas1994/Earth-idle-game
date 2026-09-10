package com.earthgame.idle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    private var monetization: MonetizationController? = null

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

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var adsPersonalized by remember { mutableStateOf(false) }
            var adsReady by remember { mutableStateOf(false) }

            // Consent, then the SDK, then the banner — in that order, because
            // serving ads in the EEA/UK before asking is a policy violation.
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val controller = MonetizationController(this@MainActivity)
                monetization = controller
                controller.start { canRequestAds ->
                    adsPersonalized = canRequestAds
                    adsReady = true
                }
            }

            EarthTheme(
                themePreference = uiState.state.settings.darkMode,
                reducedAnimations = uiState.state.settings.reducedAnimations,
            ) {
                EarthApp(
                    uiState = uiState,
                    windowWidthSizeClass = calculateWindowSizeClass(this).widthSizeClass,
                    monetization = if (adsReady) monetization else null,
                    adsPersonalized = adsPersonalized,
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
