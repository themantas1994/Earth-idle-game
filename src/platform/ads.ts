import {
  AdMob,
  AdmobConsentStatus,
  BannerAdPluginEvents,
  BannerAdPosition,
  BannerAdSize,
  type AdMobBannerSize,
} from '@capacitor-community/admob';
import type { PluginListenerHandle } from '@capacitor/core';
import { isNativePlatform } from './native';

/**
 * The live banner unit from the AdMob console. Its app ID
 * (`ca-app-pub-6872627319793193~7208922044`) is declared as
 * `com.google.android.gms.ads.APPLICATION_ID` in
 * `android/app/src/main/AndroidManifest.xml` — the Google Mobile Ads SDK reads
 * the app ID from the manifest, not from JavaScript, and crashes the app at
 * startup if it is missing.
 */
export const BANNER_AD_UNIT_ID = 'ca-app-pub-6872627319793193/5213314092';

/**
 * Google's public sample banner unit, which always fills.
 *
 * AdMob policy treats impressions and clicks a developer generates on their own
 * live unit as invalid traffic, and repeat offences get the whole account
 * suspended — so anything that is not a production build asks for test ads
 * instead of the real ones.
 *
 * @see https://developers.google.com/admob/android/test-ads
 */
export const TEST_BANNER_AD_UNIT_ID = 'ca-app-pub-3940256099942544/6300978111';

/**
 * Clearance kept between the bottom nav and the top edge of the banner.
 *
 * The banner is a native view drawn over the WebView, so nothing in CSS knows
 * it is there; the app reserves the space itself. The gap exists because AdMob
 * policy forbids placing an ad flush against a control the player is tapping —
 * the nav is nine 52px targets along the bottom edge, and a mistap that lands
 * on the ad instead is an accidental click.
 */
export const BANNER_GAP_PX = 8;

/** Live ads in a production build, Google's test unit everywhere else. */
export function resolveBannerAdUnitId(testMode: boolean): string {
  return testMode ? TEST_BANNER_AD_UNIT_ID : BANNER_AD_UNIT_ID;
}

/**
 * Bottom inset the app shell must reserve for a banner of `heightPx` (the
 * height the SDK reports once an ad has actually loaded, in density-independent
 * pixels, which are CSS pixels in the WebView).
 *
 * A height of 0 means "no ad on screen" — the banner was hidden, removed, or
 * never filled — and must reserve nothing at all, gap included, or an empty
 * strip is left under the nav for the rest of the session.
 */
export function bannerInsetPx(heightPx: number): number {
  if (!Number.isFinite(heightPx) || heightPx <= 0) return 0;
  return heightPx + BANNER_GAP_PX;
}

/**
 * True unless this is a production web build. `npm run dev` and any build made
 * with `VITE_ADMOB_TEST=1` (the way to exercise ads in a debug APK) request
 * test ads; `npm run build` — the bundle `cap sync` packages for release —
 * requests live ones.
 */
export function isTestAdMode(): boolean {
  const flag = import.meta.env.VITE_ADMOB_TEST;
  return import.meta.env.DEV || flag === '1' || flag === 'true';
}

/**
 * Whether the UMP SDK has a consent form to show. Captured during startup so
 * Settings can offer "Ad privacy choices" only to the players it can actually
 * work for — outside the EEA/UK there is usually no form configured and
 * showing the form would just fail.
 */
let consentFormAvailable = false;

/** @see consentFormAvailable */
export function isAdConsentFormAvailable(): boolean {
  return consentFormAvailable;
}

/**
 * Runs Google's User Messaging Platform flow and reports whether personalized
 * ads may be requested.
 *
 * Serving ads in the EEA/UK without a consent message is an AdMob policy
 * violation, so this runs before the SDK is initialized, as Google's guide
 * requires. `OBTAINED` means the player answered the form — whichever way they
 * answered — and the SDK reads their actual choice back from the stored consent
 * string, so it does not need the legacy non-personalized flag. The flag is for
 * the cases where no choice could be recorded at all: consent is required but
 * the form failed to load, or UMP itself errored.
 *
 * @see https://developers.google.com/admob/android/privacy
 */
async function gatherConsent(): Promise<boolean> {
  try {
    let info = await AdMob.requestConsentInfo();
    consentFormAvailable = info.isConsentFormAvailable === true;

    if (consentFormAvailable && info.status === AdmobConsentStatus.REQUIRED) {
      info = await AdMob.showConsentForm();
    }

    return info.status === AdmobConsentStatus.NOT_REQUIRED || info.status === AdmobConsentStatus.OBTAINED;
  } catch {
    return false;
  }
}

/**
 * Re-opens the consent form so a player can change their ad-personalization
 * choice after the first launch — the "privacy options" entry point AdMob
 * expects an app to keep available. Resolves to false if the form could not be
 * shown.
 */
export async function showAdPrivacyOptions(): Promise<boolean> {
  if (!isNativePlatform() || !consentFormAvailable) return false;
  try {
    await AdMob.showConsentForm();
    return true;
  } catch {
    return false;
  }
}

/**
 * Gathers consent, initializes the Mobile Ads SDK and shows the anchored
 * adaptive banner at the bottom of the screen. No-op in the browser build,
 * where the plugin has no implementation.
 *
 * `onInsetChange` is called with the space the app must keep clear at the
 * bottom, in CSS pixels: the banner is a native view floating over the WebView,
 * so without it the ad would sit on top of the bottom nav. It is called with 0
 * whenever there is no ad to make room for (failed to fill, removed on
 * teardown), and can be called more than once — an adaptive banner reports its
 * height only after it loads, and reports a new one if the ad is replaced.
 *
 * Returns a disposer that removes the banner and its listeners.
 */
export async function startBannerAd(onInsetChange: (px: number) => void): Promise<() => void> {
  if (!isNativePlatform()) return () => {};

  const testMode = isTestAdMode();
  const listeners: PluginListenerHandle[] = [];

  const dispose = () => {
    for (const listener of listeners) void listener.remove();
    listeners.length = 0;
    void AdMob.removeBanner().catch(() => {});
    onInsetChange(0);
  };

  try {
    listeners.push(
      await AdMob.addListener(BannerAdPluginEvents.SizeChanged, (size: AdMobBannerSize) =>
        onInsetChange(bannerInsetPx(size.height)),
      ),
    );
    listeners.push(await AdMob.addListener(BannerAdPluginEvents.FailedToLoad, () => onInsetChange(0)));

    const personalized = await gatherConsent();

    await AdMob.initialize({ initializeForTesting: testMode });
    await AdMob.showBanner({
      adId: resolveBannerAdUnitId(testMode),
      // Adaptive banners size themselves to the device width and are the size
      // Google recommends over the fixed 320x50 one.
      adSize: BannerAdSize.ADAPTIVE_BANNER,
      position: BannerAdPosition.BOTTOM_CENTER,
      isTesting: testMode,
      npa: !personalized,
      margin: 0,
    });
  } catch {
    // A device with no Play Services, no network at launch, or a misconfigured
    // unit must cost the player nothing more than the ad: drop the reservation
    // and leave the game running.
    dispose();
    return () => {};
  }

  return dispose;
}
