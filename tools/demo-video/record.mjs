// Records one FlashChores demo video: `node record.mjs <name>` plays the scene set in
// scenes/<name>.mjs against a running app (run.sh starts one on a fresh database) while the
// browser's own video recording keeps every frame. Captions, the tap ripple and the finger dot
// are injected into the page. The narration is spoken by Kokoro (tts.py) as the scenes play, and
// a line never waits for the hands: `say()` starts the voice and returns, the scene taps under
// it, and the next `say()` waits for the previous line to end.
//
// A scene set may use more than one screen — a second phone, the kitchen tablet — each its
// own recorded browser context, and says which one is on camera with `onCamera()`. The clips
// and their start times land in video/<name>/voice.json, and the cut list (which screen's
// recording, from when to when) in video/<name>/cuts.json, for mix.sh to splice and voice.
// A scene set's optional `setup` export runs before the camera rolls, so the home a video
// opens on can have the history its story needs.
import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { mkdirSync, writeFileSync } from 'node:fs';

const NAME = process.argv[2] ?? 'basics';
const BASE = `http://127.0.0.1:${process.env.DEMO_PORT ?? 8120}`;
const OUT = new URL(`./video/${NAME}/`, import.meta.url).pathname;
const FAIL = OUT + 'fail.png';

// 9:19.5 phone-ish canvas, recorded 1:1. 540px wide keeps the app's mobile
// breakpoint (max-width: 640px) active while being crisper than 390. Every screen,
// the tablet included, records at this one size so the cuts splice without scaling.
const W = 540, H = 1170;

const sceneModule = await import(`./scenes/${NAME}.mjs`);
const scenes = sceneModule.default;
mkdirSync(OUT + 'voice', { recursive: true });

// The voice loads its model before the recording starts, so the first line costs no dead air.
// DEMO_VOICE and DEMO_SPEED pass through to it.
const tts = spawn(new URL('./.venv/bin/python', import.meta.url).pathname,
  [new URL('./tts.py', import.meta.url).pathname], { stdio: ['pipe', 'pipe', 'ignore'] });
const replies = createInterface({ input: tts.stdout })[Symbol.asyncIterator]();
const reply = async () => {
  const { value, done } = await replies.next();
  if (done) throw new Error('tts.py exited — is tools/demo-video/.venv set up? (see run.sh)');
  return JSON.parse(value);
};
await reply(); // {"ready": true}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/* ---------- screens: one recorded context each, and which one the camera shows ---------- */
const browser = await chromium.launch();
const screens = [];

/** Opens another screen: its own context, storage and recording, at the shared canvas. */
async function screen(name) {
  const ctx = await browser.newContext({
    viewport: { width: W, height: H },
    isMobile: true,
    hasTouch: true,
    deviceScaleFactor: 2,
    recordVideo: { dir: OUT, size: { width: W, height: H } },
    locale: 'en-US',
  });
  const page = await ctx.newPage();
  // Real touch input: list-swipe.js ignores mice, hold-drag wants a still hold.
  const cdp = await ctx.newCDPSession(page);
  const s = { name, ctx, page, cdp, t0: Date.now() };   // its recording starts with its page
  screens.push(s);
  return s;
}

let cam = await screen('phone');   // the first screen; a one-screen video never looks elsewhere

// The cut list: which screen's recording the finished video shows, from when to when in
// that recording. The camera is off while a scene set's `setup` runs.
const cuts = [];
let rolling = false;
let played = 0;   // ms of finished video in the cuts already closed

function openCut(s) {
  cuts.push({ screen: s, from: Date.now() - s.t0, startedAt: Date.now() });
}
function closeCut() {
  const cut = cuts.at(-1);
  cut.to = Date.now() - cut.screen.t0;
  played += cut.to - cut.from;
}
/** Where the finished video is now, in ms — what a narration line's start time is measured in. */
const playhead = () => rolling ? played + (Date.now() - cuts.at(-1).startedAt) : 0;

/** Puts a screen on camera: the helpers act on it from here, and the video cuts to it. */
function onCamera(s) {
  if (rolling && s !== cam) {
    closeCut();
    openCut(s);
  }
  cam = s;
}

/* ---------- narration: one clip per line, never two at once ---------- */
const lines = [];
let speechUntil = 0;

async function speak(text) {
  const file = `${OUT}voice/${String(lines.length).padStart(2, '0')}.wav`;
  tts.stdin.write(JSON.stringify({ text, file }) + '\n');
  return { file, ms: (await reply()).ms };
}

/** Waits for the line that is speaking, if any, to finish. */
async function speechDone() {
  const wait = speechUntil - Date.now();
  if (wait > 0) await sleep(wait);
}

/** Starts a line as soon as the previous one has ended; returns how long it speaks. */
async function narrate(text) {
  const clip = await speak(text);
  await speechDone();
  lines.push({ file: clip.file, at: playhead(), text });
  speechUntil = Date.now() + clip.ms + 250;
  return clip.ms + 250;
}

/* ---------- overlay helpers (captions + touch ripple), injected once per screen ---------- */
async function installOverlay() {
  await cam.page.evaluate(() => {
    if (document.getElementById('demo-caption')) return;
    const style = document.createElement('style');
    style.textContent = `
      #demo-caption {
        position: fixed; left: 50%; bottom: 72px; transform: translateX(-50%);
        max-width: 86vw; padding: 14px 22px; border-radius: 18px;
        background: rgba(17, 24, 39, 0.88); color: #fff;
        font: 800 26px/1.25 -apple-system, 'Segoe UI', Roboto, sans-serif;
        text-align: center; letter-spacing: .2px; z-index: 99999;
        box-shadow: 0 8px 30px rgba(0,0,0,.35);
        opacity: 0; transition: opacity .35s ease, transform .35s ease;
        pointer-events: none;
      }
      #demo-caption.show { opacity: 1; transform: translateX(-50%) translateY(-6px); }
      .demo-ripple {
        position: fixed; width: 64px; height: 64px; border-radius: 50%;
        background: rgba(16,185,129,.45); border: 3px solid rgba(255,255,255,.9);
        transform: translate(-50%,-50%) scale(.4); pointer-events: none; z-index: 99998;
        animation: demo-rip .55s ease-out forwards;
      }
      @keyframes demo-rip {
        60% { opacity: .9; transform: translate(-50%,-50%) scale(1); }
        100% { opacity: 0; transform: translate(-50%,-50%) scale(1.5); }
      }`;
    document.head.appendChild(style);
    const cap = document.createElement('div');
    cap.id = 'demo-caption';
    document.body.appendChild(cap);
  });
}

/** Shows a caption and holds it; silent — a spoken line goes through say(). */
async function caption(text, holdMs = 0) {
  await installOverlay(); // survives SPA nav; reinstalls after a reload
  await cam.page.evaluate((t) => {
    const c = document.getElementById('demo-caption');
    c.classList.remove('show');
    setTimeout(() => { c.textContent = t; c.classList.add('show'); }, 120);
  }, text);
  if (holdMs > 0) await sleep(holdMs);
}

/**
 * The unit of a scene: a spoken line with its caption. The voice starts (once the previous
 * line has ended) and the scene carries on tapping under it. `lead` is the beat before the
 * hands move, so the viewer hears what is about to happen.
 */
async function say(speech, text, { lead = 300 } = {}) {
  const ms = await narrate(speech);
  if (text != null) await caption(text);
  await sleep(lead);
  return ms;
}

async function captionOff() {
  await cam.page.evaluate(() => document.getElementById('demo-caption')?.classList.remove('show'));
}

const settle = (ms) => sleep(ms);

/** Tap with a visible ripple so the viewer sees where the finger lands. */
async function tap(locator) {
  const box = await locator.boundingBox();
  if (box) {
    await cam.page.evaluate(([x, y]) => {
      const r = document.createElement('div');
      r.className = 'demo-ripple';
      r.style.left = x + 'px'; r.style.top = y + 'px';
      document.body.appendChild(r);
      setTimeout(() => r.remove(), 600);
    }, [box.x + box.width / 2, box.y + box.height / 2]);
    await sleep(320);
  }
  await locator.click();
}

/** Types letter by letter — once per video, where watching the letters is the point. */
async function typeSlow(locator, text) {
  await locator.pressSequentially(text, { delay: 40 });
  await locator.press('Tab'); // blur, so Vaadin fires its change event
  await sleep(200);
}

/** Fills a field in one go, under the voice. */
async function type(locator, text) {
  await locator.click();
  await cam.page.keyboard.insertText(text);
  await locator.press('Tab');
  await sleep(200);
}

/* ---------- real touch input, on the screen that is on camera ---------- */
async function finger(x, y, state) {
  await cam.page.evaluate(([x, y, state]) => {
    let f = document.getElementById('demo-finger');
    if (!f) {
      f = document.createElement('div');
      f.id = 'demo-finger';
      f.style.cssText = `position:fixed;width:46px;height:46px;border-radius:50%;z-index:99998;
        pointer-events:none;transform:translate(-50%,-50%);transition:background .2s, box-shadow .2s;
        border:3px solid rgba(255,255,255,.95);box-shadow:0 2px 10px rgba(0,0,0,.3);`;
      document.body.appendChild(f);
    }
    f.style.left = x + 'px'; f.style.top = y + 'px';
    f.style.display = state === 'up' ? 'none' : 'block';
    f.style.background = state === 'held' ? 'rgba(14,165,233,.6)' : 'rgba(16,185,129,.5)';
    f.style.boxShadow = state === 'held' ? '0 0 0 10px rgba(14,165,233,.25)' : '0 2px 10px rgba(0,0,0,.3)';
  }, [x, y, state]);
}

const touch = (type, x, y) => cam.cdp.send('Input.dispatchTouchEvent',
  { type, touchPoints: type === 'touchEnd' ? [] : [{ x, y, id: 1 }] });

async function glide(x0, y0, x1, y1, ms, state) {
  const steps = Math.max(8, Math.round(ms / 25));
  for (let i = 1; i <= steps; i++) {
    const t = i / steps, e = t < .5 ? 2 * t * t : 1 - (-2 * t + 2) ** 2 / 2; // ease in-out
    const x = x0 + (x1 - x0) * e, y = y0 + (y1 - y0) * e;
    await touch('touchMove', x, y);
    await finger(x, y, state);
    await sleep(ms / steps);
  }
}

/** Hold still until the item lifts (450 ms in hold-drag.js), then drag it and let go. */
async function holdAndDrag(from, to, { hold = 900, ms = 1300 } = {}) {
  await finger(from.x, from.y, 'down');
  await touch('touchStart', from.x, from.y);
  await sleep(hold);
  await finger(from.x, from.y, 'held');
  await glide(from.x, from.y, to.x, to.y, ms, 'held');
  await sleep(350);
  await touch('touchEnd');
  await finger(to.x, to.y, 'up');
}

/** A quick horizontal swipe, the way a thumb uncovers a chat app's actions. */
async function swipe(from, dx, ms = 380) {
  await finger(from.x, from.y, 'down');
  await touch('touchStart', from.x, from.y);
  await sleep(60);
  await glide(from.x, from.y, from.x + dx, from.y, ms, 'down');
  await touch('touchEnd');
  await finger(from.x + dx, from.y, 'up');
}

async function point(locator, fx = 0.5, fy = 0.5) {
  await locator.scrollIntoViewIfNeeded();
  const b = await locator.boundingBox();
  return { x: b.x + b.width * fx, y: b.y + b.height * fy };
}

/** Rests the finger on a thing without touching it, so the viewer looks where the line points. */
async function pointAt(locator, ms = 1200) {
  const { x, y } = await point(locator);
  await finger(x, y, 'down');
  await sleep(ms);
  await finger(x, y, 'up');
}

/* ---------- the app's own landmarks, on the screen that is on camera ---------- */
const tab = (name) => cam.page.locator('vaadin-tab', { hasText: name }).first();
const card = (name) => cam.page.locator('.task-card', { hasText: name }).first();
const line = (text) => cam.page.locator('.shared-list:not(.done) .list-item', { hasText: text }).first();
const chip = (label) => cam.page.locator('.filter-chip', { hasText: label }).first();
const dialogButton = (name) => cam.page.getByRole('dialog').getByRole('button', { name });

async function openSection(title) {
  const page = cam.page;
  const details = page.locator('vaadin-details.admin-section', {
    has: page.locator('vaadin-details-summary', { hasText: title }) }).first();
  if (await details.evaluate((d) => d.opened)) return; // a tap would fold it shut
  await tap(page.locator('vaadin-details-summary', { hasText: title }).first());
  await sleep(500);
  // Belt and braces: if the tap landed during a re-render, open the card directly.
  await page.evaluate((title) => {
    for (const d of document.querySelectorAll('vaadin-details.admin-section')) {
      if ((d.querySelector('vaadin-details-summary')?.textContent || '').startsWith(title)) d.opened = true;
    }
  }, title);
  await sleep(300);
}

/** The closing card every video shares, with its own tagline spoken over it. */
async function outro(tagline, spoken) {
  await cam.page.evaluate((tagline) => {
    const o = document.createElement('div');
    o.id = 'demo-outro';
    o.style.cssText = `position:fixed;inset:0;z-index:99997;display:flex;flex-direction:column;
      align-items:center;justify-content:center;gap:18px;color:#fff;text-align:center;
      background:linear-gradient(135deg,#10b981,#0ea5e9);opacity:0;transition:opacity .6s ease;
      font-family:-apple-system,'Segoe UI',Roboto,sans-serif;`;
    o.innerHTML = `<div style="font-size:64px">⚡</div>
      <div style="font-size:40px;font-weight:900">FlashChores</div>
      <div style="font-size:22px;font-weight:600;opacity:.92">${tagline}</div>
      <div style="font-size:17px;font-weight:600;opacity:.8;margin-top:12px">Vaadin Flow · Spring Boot · one tiny server</div>`;
    document.body.appendChild(o);
    requestAnimationFrame(() => { o.style.opacity = '1'; });
  }, tagline);
  await captionOff();
  await narrate(spoken);
  await speechDone();
  await sleep(1200);
}

/* --------------------------------- the video --------------------------------- */
const toolkit = {
  get page() { return cam.page; },   // the screen on camera
  BASE, screens, screen, onCamera,
  say, caption, captionOff, speechDone, settle,
  tap, type, typeSlow, holdAndDrag, swipe, point, pointAt,
  tab, card, line, chip, dialogButton, openSection, outro,
};
try {
  if (sceneModule.setup) await sceneModule.setup(toolkit);
  rolling = true;
  openCut(cam);
  await scenes(toolkit);
  closeCut();
} catch (e) {
  console.error('FAILED:', String(e).split('\n').slice(0, 8).join('\n'));
  try { await cam.page.screenshot({ path: FAIL }); } catch {}
  for (const s of screens) await s.ctx.close();
  await browser.close();
  process.exit(1);
}
const videos = new Map();
for (const s of screens) {
  videos.set(s, await s.page.video().path());
  await s.ctx.close();   // flushes the recording
}
await browser.close();
tts.stdin.end();
writeFileSync(OUT + 'voice.json', JSON.stringify(lines, null, 1));
writeFileSync(OUT + 'cuts.json', JSON.stringify(cuts.map((c) =>
  ({ screen: c.screen.name, file: videos.get(c.screen), from: c.from, to: c.to })), null, 1));
console.log(`recorded ${NAME} to`, OUT);
process.exit(0);
