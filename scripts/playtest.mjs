/**
 * Drives the exact bundle that ships inside the APK
 * (android/app/src/main/assets/public) at a phone viewport, through the whole
 * golden path: tap -> earn -> buy -> automate -> gases rise -> every screen
 * renders live data. Chromium is the same engine family as the Android
 * WebView the shell runs, so this exercises all the game code that ships.
 */
import { chromium } from 'playwright';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = 'android/app/src/main/assets/public';
const PORT = 5199;
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.webmanifest': 'application/manifest+json', '.map': 'application/json' };

const server = http.createServer((req, res) => {
  const url = decodeURIComponent(req.url.split('?')[0]);
  let file = path.join(ROOT, url === '/' ? 'index.html' : url);
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(ROOT, 'index.html');
  res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] ?? 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => server.listen(PORT, r));

// PLAYWRIGHT_BROWSERS_PATH environments (like CI images that ship a browser)
// expose it through Playwright's own resolution; only fall back to an explicit
// path when one is given.
const browser = await chromium.launch(
  process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {},
);
const context = await browser.newContext({
  viewport: { width: Number(process.env.VP_W ?? 390), height: Number(process.env.VP_H ?? 844) },
  deviceScaleFactor: 3,
  isMobile: true,
  hasTouch: true,
  userAgent: 'Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
});
const page = await context.newPage();

const errors = [];
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
page.on('console', (m) => { if (m.type() === 'error') errors.push(`console.error: ${m.text()}`); });

await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
await page.waitForSelector('.bottom-nav', { timeout: 15000 });

const results = [];
const check = (name, ok, detail = '') => { results.push({ name, ok, detail }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ' — ' + detail : ''}`); };

// --- layout: the body-margin bug would show as a horizontally/vertically
// overflowing document and a bottom nav pushed off the viewport.
const layout = await page.evaluate(() => {
  const nav = document.querySelector('.bottom-nav').getBoundingClientRect();
  return {
    bodyMarginTop: getComputedStyle(document.body).marginTop,
    docScrollH: document.documentElement.scrollHeight,
    innerH: window.innerHeight,
    innerW: window.innerWidth,
    docScrollW: document.documentElement.scrollWidth,
    navBottom: nav.bottom,
    navHeight: nav.height,
  };
});
check('body margin reset to 0', layout.bodyMarginTop === '0px', layout.bodyMarginTop);
check('document does not scroll vertically', layout.docScrollH <= layout.innerH + 1, `scrollH=${layout.docScrollH} innerH=${layout.innerH}`);
check('document does not scroll horizontally', layout.docScrollW <= layout.innerW + 1, `scrollW=${layout.docScrollW} innerW=${layout.innerW}`);
check('bottom nav fully inside viewport', layout.navBottom <= layout.innerH + 1 && layout.navHeight > 0, `navBottom=${layout.navBottom.toFixed(1)}`);

// Every destination must be reachable without a hidden horizontal scroll.
const nav = await page.evaluate(() => {
  const el = document.querySelector('.bottom-nav');
  const items = [...el.querySelectorAll('.bottom-nav__item')];
  return {
    count: items.length,
    scrollW: el.scrollWidth,
    clientW: el.clientWidth,
    offscreen: items.filter((i) => i.getBoundingClientRect().right > window.innerWidth + 0.5).map((i) => i.dataset.screen),
    minHeight: Math.min(...items.map((i) => i.getBoundingClientRect().height)),
  };
});
check('all nav destinations fit on screen', nav.offscreen.length === 0 && nav.scrollW <= nav.clientW + 1, `${nav.count} items, offscreen=[${nav.offscreen}]`);
check('nav touch targets meet 48dp minimum', nav.minHeight >= 48, `min=${nav.minHeight.toFixed(1)}px`);

// --- the core loop: Energy accrues on its own, with nothing to tap
const energyOf = () => page.evaluate(() => {
  const row = [...document.querySelectorAll('.row')].find((r) => r.textContent.trim().startsWith('Energy'));
  return row ? row.querySelector('.row__value')?.textContent?.trim() : null;
});
check('no tap button anywhere on the page', (await page.locator('.tap-button').count()) === 0);
await page.waitForTimeout(3000);
const passiveEnergy = await energyOf();
check('Natural Fire produces Energy with no player input', passiveEnergy !== null && !/^0(\.0+)?\s/.test(passiveEnergy), `energy=${passiveEnergy}`);

// --- the two shopping screens are genuinely different lists
await page.click('[data-screen="technology"]');
await page.waitForTimeout(400);
const techNames = await page.locator('.tech-card__name').allInnerTexts();
check('technology screen lists research nodes', techNames.length > 0, `${techNames.length} cards`);
check('technology screen sells no generators', !techNames.includes('Natural Fire') && !techNames.includes('Controlled Fire'), techNames.slice(0, 3).join(', '));

await page.click('[data-screen="production"]');
await page.waitForTimeout(400);
const prodNames = await page.locator('.tech-card__name').allInnerTexts();
check('production screen sells the generators', prodNames.includes('Natural Fire'), `${prodNames.length} cards`);

// Fast-forward the wallet rather than idling through the opening minutes: this
// is checking that a purchase works, not how long one takes to afford.
//
// It has to run as an init script rather than a plain evaluate-then-reload,
// because the app saves on `beforeunload` — a wallet written before the reload
// is overwritten by the real state on the way out. An init script runs after
// that final save and before the app boots, so its patch is the one loaded.
// One-shot, so the later persistence reload still reflects real play.
await context.addInitScript(() => {
  try {
    if (sessionStorage.getItem('playtest-wallet-seeded')) return;
    const raw = localStorage.getItem('earth-idle-save');
    if (!raw) return;
    const save = JSON.parse(raw);
    if (!save.resources?.energy) return;
    save.resources.energy = { __decimal: [1, 1, 6] };
    save.resources.research = { __decimal: [1, 1, 6] };
    localStorage.setItem('earth-idle-save', JSON.stringify(save));
    sessionStorage.setItem('playtest-wallet-seeded', '1');
  } catch {
    // A browser with storage disabled just plays the slow way; the checks below still hold.
  }
});
await page.reload({ waitUntil: 'networkidle' });
await page.waitForSelector('.bottom-nav', { timeout: 15000 });
await page.click('[data-screen="production"]');
await page.waitForTimeout(500);

let bought = false;
for (let attempt = 0; attempt < 20 && !bought; attempt++) {
  const buy = page.locator('.tech-card', { hasText: 'Controlled Fire' }).locator('button:not([disabled])').first();
  if (await buy.count()) { await buy.click(); bought = true; break; }
  await page.waitForTimeout(500);
}
check('bought a generator on the Production screen (Controlled Fire)', bought);

// --- gases must start accumulating from an owned generator
await page.locator('.bottom-nav__item').first().click();
await page.waitForTimeout(2500);
const co2Rate = await page.evaluate(() => {
  const row = [...document.querySelectorAll('.row')].find((r) => r.textContent.includes('CO'));
  return row ? row.textContent : null;
});
check('CO2 production rate appears after buying a generator', Boolean(co2Rate), co2Rate ?? 'none');

const tempText = await page.locator('.stat-chip').first().innerText();
check('header shows a live temperature', /[\d.]/.test(tempText), tempText.replace(/\n/g, ' '));

// --- every screen renders without throwing
const screens = ['Home', 'Atmo', 'Tech', 'Prod', 'Prestige', 'Wins', 'Trials', 'Stats', 'Settings'];
const navLabels = await page.locator('.bottom-nav__item').allInnerTexts();
for (let i = 0; i < navLabels.length; i++) {
  const before = errors.length;
  await page.locator('.bottom-nav__item').nth(i).click();
  await page.waitForTimeout(350);
  const main = await page.locator('.app-main').innerText();
  check(`screen "${navLabels[i].replace(/\n/g, ' ').trim()}" renders content`, main.trim().length > 20 && errors.length === before, `${main.trim().length} chars`);
}

// --- the world-news feed renders (its first headline fires on Controlled Fire)
await page.locator('.bottom-nav__item').first().click();
await page.waitForTimeout(800);
const newsText = await page.locator('.card', { hasText: 'World News' }).first().innerText();
check('world news feed reports the first milestone', /Smoke on the horizon/.test(newsText), newsText.split('\n').slice(0, 3).join(' / '));

// --- persistence: the save must survive a reload
await page.waitForTimeout(1200);
await page.evaluate(() => window.dispatchEvent(new Event('beforeunload')));
await page.waitForTimeout(300);
const savedRaw = await page.evaluate(() => localStorage.getItem('earth-idle-save'));
check('game state is written to storage', Boolean(savedRaw) && savedRaw.length > 500, `${savedRaw?.length ?? 0} bytes`);

await page.reload({ waitUntil: 'networkidle' });
await page.waitForSelector('.bottom-nav', { timeout: 15000 });
await page.waitForTimeout(600);
const ownedAfterReload = await page.evaluate(() => {
  const raw = localStorage.getItem('earth-idle-save');
  return raw ? (JSON.parse(raw).techOwned?.controlled_fire ?? 0) : 0;
});
check('owned technology survives a reload', ownedAfterReload > 0, `controlled_fire=${ownedAfterReload}`);

const SHOT_DIR = process.env.SHOT_DIR ?? '';
if (SHOT_DIR) await page.screenshot({ path: `${SHOT_DIR}/shot-home.png` });
await page.locator('.bottom-nav__item', { hasText: 'Atmo' }).first().click().catch(() => {});
await page.waitForTimeout(400);
if (SHOT_DIR) await page.screenshot({ path: `${SHOT_DIR}/shot-atmosphere.png` });
await page.locator('.bottom-nav__item', { hasText: 'Tech' }).first().click().catch(() => {});
await page.waitForTimeout(400);
if (SHOT_DIR) await page.screenshot({ path: `${SHOT_DIR}/shot-tech.png` });
await page.locator('.bottom-nav__item', { hasText: 'Output' }).first().click().catch(() => {});
await page.waitForTimeout(400);
if (SHOT_DIR) await page.screenshot({ path: `${SHOT_DIR}/shot-production.png` });

check('no uncaught page errors during the whole run', errors.length === 0, errors.slice(0, 5).join(' | '));

await browser.close();
server.close();

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
process.exit(failed.length ? 1 : 0);
