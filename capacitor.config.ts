import type { CapacitorConfig } from '@capacitor/cli';

/**
 * Capacitor packages the built web app (dist/) as a native Android WebView
 * shell. Run `npm run build && npx cap add android && npx cap sync android`
 * (requires the Android SDK, not available in this dev container) to
 * generate the installable APK/AAB project.
 */
const config: CapacitorConfig = {
  appId: 'com.earthgame.idle',
  appName: 'EARTH',
  webDir: 'dist',
  backgroundColor: '#0b1622',
  android: {
    allowMixedContent: false,
  },
};

export default config;
