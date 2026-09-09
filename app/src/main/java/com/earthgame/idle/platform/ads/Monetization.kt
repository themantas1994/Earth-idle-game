package com.earthgame.idle.platform.ads

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.earthgame.idle.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where the ad identifiers come from.
 *
 * The app ID is read from the manifest by the Mobile Ads SDK itself; both
 * values live in `res/values/ads.xml`, which is the single place to change them
 * for a different AdMob account. Neither is a secret — they ship in every APK —
 * and no secret belongs here.
 *
 * A debuggable build always requests Google's public test units instead.
 * AdMob counts impressions and clicks a developer generates on their own live
 * unit as invalid traffic, and repeat offences suspend the whole account, so a
 * live unit is only ever requested from a release build.
 */
object MonetizationConfig {

    /** Google's public sample banner unit, which always fills. */
    const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    fun bannerUnitId(context: Context): String =
        if (isDebuggable(context)) TEST_BANNER_UNIT_ID else context.getString(R.string.admob_banner_unit_id)

    fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

/**
 * The whole monetization surface: one anchored banner at the bottom of the
 * screen. There are no interstitials and no rewarded ads.
 *
 * Nothing in the simulation knows this class exists. Every failure path — no
 * Play Services, no network at launch, a misconfigured unit, a consent form
 * that will not load — ends the same way: the banner is not shown and the game
 * plays exactly as it would have. An ad is never allowed to cost the player
 * more than the ad.
 */
class MonetizationController(private val activity: Activity) {

    private val initialized = AtomicBoolean(false)
    private var consentInformation: ConsentInformation? = null

    /** Whether a consent form exists to reopen from Settings. */
    var privacyOptionsAvailable: Boolean = false
        private set

    /**
     * Runs Google's User Messaging Platform flow, then initializes the Mobile
     * Ads SDK.
     *
     * Order matters: serving ads in the EEA or UK without a consent message is
     * a policy violation, so consent is gathered *before* the SDK starts, as
     * Google's guide requires. When no choice can be recorded at all — the form
     * failed to load, or UMP itself errored — the SDK is still started but
     * personalization is switched off, which is the conservative reading.
     */
    fun start(onReady: (canRequestAds: Boolean) -> Unit) {
        val parameters = ConsentRequestParameters.Builder()
            .apply {
                if (MonetizationConfig.isDebuggable(activity)) {
                    // Without this a debug build on a device outside the EEA
                    // never sees the form, so the flow cannot be exercised.
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                            .build(),
                    )
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
                        Log.w(TAG, "Consent form unavailable: ${formError.message}")
                    }
                    privacyOptionsAvailable = information.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    initializeAds()
                    onReady(information.canRequestAds())
                }
            },
            { requestError ->
                // Consent could not even be asked about. Fall back to
                // non-personalized ads rather than serving nothing or, worse,
                // serving personalized ads without consent.
                Log.w(TAG, "Consent info update failed: ${requestError.message}")
                initializeAds()
                onReady(false)
            },
        )
    }

    private fun initializeAds() {
        if (!initialized.compareAndSet(false, true)) return
        // Whether ads may be personalized is carried on the request itself (see
        // createBannerView), not in the global configuration.
        runCatching { MobileAds.initialize(activity) {} }
            .onFailure { Log.w(TAG, "Mobile Ads init failed", it) }
    }

    /** Re-opens the consent form so a player can change their choice later. */
    fun showPrivacyOptions() {
        runCatching {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                if (error != null) Log.w(TAG, "Privacy options form failed: ${error.message}")
            }
        }
    }

    /**
     * Builds the anchored adaptive banner.
     *
     * Adaptive banners size themselves to the device width, which is what
     * Google recommends over the fixed 320x50. The "large" variant is the
     * current API — the older fixed-height anchored sizes are deprecated.
     * Returns null if the view cannot be created at all, which the composable
     * renders as no banner.
     */
    fun createBannerView(widthDp: Int, personalized: Boolean): AdView? = runCatching {
        AdView(activity).apply {
            adUnitId = MonetizationConfig.bannerUnitId(activity)
            setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp))
            val request = AdRequest.Builder()
                .apply {
                    if (!personalized) {
                        // The SDK's documented opt-out for non-personalized ads.
                        addNetworkExtrasBundle(
                            com.google.ads.mediation.admob.AdMobAdapter::class.java,
                            android.os.Bundle().apply { putString("npa", "1") },
                        )
                    }
                }
                .build()
            loadAd(request)
        }
    }.onFailure { Log.w(TAG, "Banner creation failed", it) }.getOrNull()

    private companion object {
        const val TAG = "Monetization"
    }
}
