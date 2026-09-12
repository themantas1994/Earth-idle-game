package com.earthgame.idle.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.viewinterop.AndroidView
import com.earthgame.idle.platform.ads.MonetizationController
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdView

/**
 * The bottom banner.
 *
 * Nothing is requested until the consent flow has settled and reported that an
 * ad request is permitted — [MonetizationController.canRequestAds] is Compose
 * state, so this composable simply does nothing until it flips.
 *
 * It occupies no space at all until an ad actually loads: a device where
 * nothing fills, a player with no network, and a player who declined consent
 * all lose no screen to an empty strip. The ad view is destroyed with the
 * composable, so it cannot leak the activity.
 *
 * The view is *built* while composing and *requested* from a `DisposableEffect`,
 * never the other way round. Requesting during composition would fire before
 * the listener that reads the outcome is attached, and would fire again for a
 * composition that is then abandoned — an ad request nobody is listening to and
 * an `AdView` nobody will destroy.
 */
@Composable
fun BannerAd(
    controller: MonetizationController?,
    modifier: Modifier = Modifier,
) {
    if (controller == null || !controller.canRequestAds) return

    // The adaptive banner is sized against the real window width, which is what
    // `containerSize` reports — `Configuration.screenWidthDp` rounds to the
    // nearest dp and treats insets differently by target SDK.
    val containerWidthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    val widthDp = with(density) { containerWidthPx.toDp() }.value.toInt().coerceAtLeast(1)

    val adView: AdView? = remember(widthDp) { controller.createBannerView(widthDp) }
    // Keyed on the view, not remembered once: a width change builds a new view
    // that has not loaded yet, and carrying the old view's "loaded" across would
    // give the empty replacement full width.
    var loaded by remember(adView) { mutableStateOf(false) }

    DisposableEffect(adView) {
        if (adView == null) return@DisposableEffect onDispose {}
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                loaded = true
            }

            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                // No fill, no network, or a bad unit. Give the space back.
                loaded = false
            }
        }
        // Listener first, then the request: an ad that resolves immediately —
        // a cached fill, or a failure the SDK can answer without the network —
        // would otherwise report to nobody and leave the banner sized as though
        // it had never loaded.
        controller.loadBanner(adView)
        onDispose { adView.destroy() }
    }

    if (adView == null) return

    // `AndroidView` calls its factory once per node and keeps that view for the
    // node's life, so a changed factory lambda alone would leave the *previous*
    // view on screen — the one the effect above has just destroyed. Keying the
    // node on the view is what actually swaps the banner when the window width
    // changes.
    key(adView) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { adView },
                modifier = if (loaded) Modifier.fillMaxWidth() else Modifier,
            )
        }
    }
}
