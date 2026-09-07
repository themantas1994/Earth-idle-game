import { Capacitor } from '@capacitor/core';
import { Preferences } from '@capacitor/preferences';
import { App, type AppState } from '@capacitor/app';
import { Haptics, ImpactStyle } from '@capacitor/haptics';
import { StatusBar, Style } from '@capacitor/status-bar';
import { SplashScreen } from '@capacitor/splash-screen';
import type { StorageAdapter } from '../engine/save';

/** True inside the packaged Android/iOS shell, false in a plain browser. */
export const isNativePlatform = (): boolean => {
  try {
    return Capacitor.isNativePlatform();
  } catch {
    return false;
  }
};

/**
 * On Android, `localStorage` lives in the WebView's data directory, which the
 * system can clear under storage pressure or when the user clears the app's
 * cache — taking the save with it. Capacitor's `Preferences` writes to
 * `SharedPreferences` instead, which survives that, so the packaged app saves
 * there and the browser build keeps using `localStorage`.
 */
export const preferencesAdapter: StorageAdapter = {
  async getItem(key) {
    try {
      const { value } = await Preferences.get({ key });
      return value ?? null;
    } catch {
      return null;
    }
  },
  async setItem(key, value) {
    try {
      await Preferences.set({ key, value });
    } catch {
      // Storage unavailable — fail silently rather than crash the game loop.
    }
  },
};

/** Registers a handler for Android's hardware/gesture back button. Returns a disposer. */
export async function onHardwareBack(handler: () => void): Promise<() => void> {
  if (!isNativePlatform()) return () => {};
  const listener = await App.addListener('backButton', handler);
  return () => void listener.remove();
}

/**
 * Fires when Android moves the app between foreground and background. This is
 * the reliable save hook on a WebView: `beforeunload` is not guaranteed to run
 * when the OS kills a backgrounded app.
 */
export async function onAppStateChange(handler: (isActive: boolean) => void): Promise<() => void> {
  if (!isNativePlatform()) return () => {};
  const listener = await App.addListener('appStateChange', ({ isActive }: AppState) => handler(isActive));
  return () => void listener.remove();
}

/** Exits the app (Android only) — used when back is pressed on the root screen. */
export function exitApp(): void {
  if (!isNativePlatform()) return;
  void App.exitApp();
}

/** Short haptic pulse for the tap button. No-op off-device. */
export function hapticTap(): void {
  if (!isNativePlatform()) return;
  void Haptics.impact({ style: ImpactStyle.Light }).catch(() => {});
}

/** Heavier pulse for milestone moments (collapse, prestige reset). */
export function hapticNotify(): void {
  if (!isNativePlatform()) return;
  void Haptics.impact({ style: ImpactStyle.Heavy }).catch(() => {});
}

/** Matches the native status bar to the in-app theme so the header doesn't clash. */
export async function applyStatusBarTheme(isDark: boolean): Promise<void> {
  if (!isNativePlatform()) return;
  try {
    await StatusBar.setStyle({ style: isDark ? Style.Dark : Style.Light });
    await StatusBar.setBackgroundColor({ color: isDark ? '#0d1420' : '#f2f5f8' });
  } catch {
    // Status bar control is unavailable on some devices — non-fatal.
  }
}

/** Dismisses the native splash once React has painted the first frame. */
export function hideSplash(): void {
  if (!isNativePlatform()) return;
  void SplashScreen.hide().catch(() => {});
}
