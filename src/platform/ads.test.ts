import { describe, it, expect } from 'vitest';
import {
  BANNER_AD_UNIT_ID,
  BANNER_GAP_PX,
  TEST_BANNER_AD_UNIT_ID,
  bannerInsetPx,
  resolveBannerAdUnitId,
} from './ads';

describe('resolveBannerAdUnitId', () => {
  it('asks for the live unit in a production build', () => {
    expect(resolveBannerAdUnitId(false)).toBe(BANNER_AD_UNIT_ID);
  });

  it("asks for Google's test unit everywhere else", () => {
    expect(resolveBannerAdUnitId(true)).toBe(TEST_BANNER_AD_UNIT_ID);
  });

  it('never confuses the two', () => {
    expect(BANNER_AD_UNIT_ID).not.toBe(TEST_BANNER_AD_UNIT_ID);
  });
});

describe('bannerInsetPx', () => {
  it('reserves the ad height plus a gap clear of the bottom nav', () => {
    expect(bannerInsetPx(50)).toBe(50 + BANNER_GAP_PX);
  });

  it('reserves nothing when no ad is on screen, gap included', () => {
    // A zero height is how the plugin reports a hidden, removed or unfilled
    // banner; adding the gap anyway would strand an empty strip under the nav.
    expect(bannerInsetPx(0)).toBe(0);
  });

  it('ignores a negative or non-finite height rather than shrinking the shell', () => {
    expect(bannerInsetPx(-32)).toBe(0);
    expect(bannerInsetPx(Number.NaN)).toBe(0);
  });
});
