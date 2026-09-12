package com.earthgame.idle.platform.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.earthgame.idle.BuildConfig
import com.earthgame.idle.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where the ad identifiers come from.
 *
 * Both live in `res/values/ads.xml`; the debug variant overlays that file with
 * Google's public sample identifiers, so which set a build uses is decided at
 * resource-merge time rather than by a runtime branch that could be got wrong.
 * The app ID is read straight out of the merged manifest by the Mobile Ads SDK
 * and never appears in code at all.
 *
 * Neither identifier is a secret — they ship in every APK — and no secret
 * belongs here.
 */
object MonetizationConfig {

    fun bannerUnitId(context: Context): String = context.getString(R.string.admob_banner_unit_id)

    /**
     * The hashed device ID UMP needs before it will honour a forced debug
     * geography on physical hardware. Empty in release, and empty in debug
     * until a developer pastes one in — see the debug overlay.
     */
    fun umpTestDeviceHashedId(context: Context): String? =
        context.getString(R.string.ump_test_device_hashed_id).takeIf { it.isNotBlank() }
}

/**
 * The whole monetization surface: one anchored banner at the bottom of the
 * screen. There are no interstitials and no rewarded ads.
 *
 * Nothing in the simulation knows this class exists. Every failure path — no
 * Play Services, no network at launch, a misconfigured unit, a consent form
 * that will not load, a player who declines — ends the same way: the banner is
 * not shown and the game plays exactly as it would have. An ad is never allowed
 * to cost the player more than the ad.
 */
class MonetizationController(private val activity: Activity) {

    private val initialized = AtomicBoolean(false)
    private var consentInformation: ConsentInformation? = null

    /**
     * Whether the recorded consent state permits requesting an ad at all.
     *
     * Compose state, not a plain field: the consent flow finishes some time
     * after the first frame, and the banner and the Settings entry both have to
     * notice when it does.
     */
    var canRequestAds: Boolean by mutableStateOf(false)
        private set

    /** Whether a consent form exists to reopen from Settings. */
    var privacyOptionsAvailable: Boolean by mutableStateOf(false)
        private set

    /**
     * Runs Google's User Messaging Platform flow, then — only if the resulting
     * consent state allows it — initializes the Mobile Ads SDK.
     *
     * The order is the one Google's guide requires, and it is not negotiable:
     * the Mobile Ads SDK may preload an ad the moment it is initialized, so
     * initializing before consent has been gathered is itself the violation.
     *
     * ```
     * requestConsentInfoUpdate ──► loadAndShowConsentFormIfRequired
     *                                        │
     *                                        ▼
     *                               canRequestAds() ? ──no──► nothing happens
     *                                        │yes
     *                                        ▼
     *                          MobileAds.initialize (background thread)
     *                                        ▼
     *                                    banner loads
     * ```
     *
     * Whether the ads that follow are personalized is *not* decided here. The
     * UMP SDK writes the IAB TCF consent signals, the Mobile Ads SDK reads them
     * and picks its serving mode from them. The `npa=1` network extra exists for
     * publishers handling consent without a TCF-certified CMP; setting it
     * alongside UMP would override the player's actual choice in one direction
     * only, so it is deliberately absent.
     *
     * Safe to call more than once: UMP expects `requestConsentInfoUpdate` on
     * every launch, and [initializeAds] is idempotent.
     */
    fun start() {
        val parameters = ConsentRequestParameters.Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    // Without this a debug build outside the EEA never sees the
                    // form, so the flow cannot be exercised. UMP only honours it
                    // on a device it considers a test device — automatic on an
                    // emulator, and on hardware only once a hashed ID is set.
                    val debugSettings = ConsentDebugSettings.Builder(activity)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .apply {
                            MonetizationConfig.umpTestDeviceHashedId(activity)?.let(::addTestDeviceHashedId)
                        }
                        .build()
                    setConsentDebugSettings(debugSettings)
                }
            }
            .build()

        val information = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = information

        information.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        // The message is Google's own diagnostic text; it carries
                        // no player data and no identifier.
                        Log.w(TAG, "Consent form unavailable: ${formError.message}")
                    }
                    onConsentSettled(information)
                }
            },
            { requestError ->
                // Consent could not even be asked about — offline at launch, or
                // UMP itself errored. No recorded consent means no ad request:
                // the game simply runs without a banner.
                Log.w(TAG, "Consent info update failed: ${requestError.message}")
                onConsentSettled(information)
            },
        )
    }

    private fun onConsentSettled(information: ConsentInformation) {
        privacyOptionsAvailable = runCatching {
            information.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        }.getOrDefault(false)

        val permitted = runCatching { information.canRequestAds() }.getOrDefault(false)
        if (permitted) initializeAds()
        // Set last: this is what lets the banner composable start, and it must
        // not do so before the SDK has been told to come up.
        canRequestAds = permitted
    }

    /**
     * Brings the Mobile Ads SDK up exactly once, on a background thread.
     *
     * `MobileAds.initialize` does real work — Play Services handshake, disk and
     * network I/O — and Google's quick start puts it on `Dispatchers.IO` for
     * that reason. Nothing waits on the callback: the banner retries on its own
     * schedule, and the game has already drawn.
     */
    private fun initializeAds() {
        if (!initialized.compareAndSet(false, true)) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { MobileAds.initialize(activity) {} }
                .onFailure { Log.w(TAG, "Mobile Ads init failed", it) }
        }
    }

    /**
     * Re-opens the consent form so a player can change or withdraw their choice.
     *
     * The new state is picked up on the next launch, which is how UMP works —
     * the form dismissal callback does not re-run the gathering flow — so the
     * flags are refreshed here too rather than left stale for the session.
     */
    fun showPrivacyOptions() {
        runCatching {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                if (error != null) Log.w(TAG, "Privacy options form failed: ${error.message}")
                consentInformation?.let(::onConsentSettled)
            }
        }.onFailure { Log.w(TAG, "Privacy options form could not be shown", it) }
    }

    /**
     * Builds the anchored adaptive banner.
     *
     * Adaptive banners size themselves to the device width, which is what
     * Google recommends over the fixed 320x50. The "large" variant is the
     * current API — the older fixed-height anchored sizes are deprecated.
     * Returns null if the view cannot be created at all, or if consent does not
     * permit an ad request, both of which the composable renders as no banner.
     */
    fun createBannerView(widthDp: Int): AdView? {
        if (!canRequestAds) return null
        return runCatching {
            AdView(activity).apply {
                adUnitId = MonetizationConfig.bannerUnitId(activity)
                setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp))
                loadAd(AdRequest.Builder().build())
            }
        }.onFailure { Log.w(TAG, "Banner creation failed", it) }.getOrNull()
    }

    private companion object {
        const val TAG = "Monetization"
    }
}
