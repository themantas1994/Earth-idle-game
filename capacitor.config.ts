import type { CapacitorConfig } from '@capacitor/cli';

/**
 * Capacitor packages the built web app (dist/) as a native Android WebView
 * shell. `npm run android:build` runs the whole chain (web build -> cap sync
 * -> Gradle assemble); see README "Building the Android app" for the SDK
 * prerequisites.
 */
const config: CapacitorConfig = {
  appId: 'com.earthgame.idle',
  appName: 'EARTH',
  webDir: 'dist',
  backgroundColor: '#0b1622',
  android: {
    allowMixedContent: false,
    // The game never loads remote content, so a WebView debugger is only ever
    // attached deliberately during development.
    webContentsDebuggingEnabled: false,
    // Keep the WebView's own zoom/pan off: the UI is a fixed one-handed column
    // and browser zoom would fight the layout.
    initialFocus: false,
  },
  plugins: {
    SplashScreen: {
      // Held only until React paints, then dismissed from App.tsx via
      // `hideSplash()` — a fixed duration would either flash or stall.
      launchAutoHide: false,
      launchShowDuration: 0,
      backgroundColor: '#0b1622',
      androidScaleType: 'CENTER_CROP',
      showSpinner: false,
      splashFullScreen: false,
      splashImmersive: false,
    },
    StatusBar: {
      // The app draws its own header right below the status bar rather than
      // behind it, so the bar keeps its own background.
      overlaysWebView: false,
      backgroundColor: '#0b1622',
      style: 'DARK',
    },
  },
};

export default config;
