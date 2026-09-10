package com.earthgame.idle.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
 * It occupies no space at all until an ad actually loads: a device where
 * nothing fills, or a player with no network, loses no screen to an empty
 * strip. The ad view is destroyed with the composable, so it cannot leak the
 * activity.
 */
@Composable
fun BannerAd(
    controller: MonetizationController?,
    personalized: Boolean,
    modifier: Modifier = Modifier,
) {
    if (controller == null) return

    // The adaptive banner is sized against the real window width, which is what
    // `containerSize` reports — `Configuration.screenWidthDp` rounds to the
    // nearest dp and treats insets differently by target SDK.
    val containerWidthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    val widthDp = with(density) { containerWidthPx.toDp() }.value.toInt().coerceAtLeast(1)
    var loaded by remember { mutableStateOf(false) }

    val adView: AdView? = remember(widthDp, personalized) {
        controller.createBannerView(widthDp, personalized)
    }

    DisposableEffect(adView) {
        adView?.adListener = object : AdListener() {
            override fun onAdLoaded() {
                loaded = true
            }

            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                // No fill, no network, or a bad unit. Give the space back.
                loaded = false
            }
        }
        onDispose { adView?.destroy() }
    }

    if (adView == null) return

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
