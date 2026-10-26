// Records one FlashChores demo video: `node record.mjs <name>` plays the scene set in
// scenes/<name>.mjs against a running app (run.sh starts one on a fresh database) while the
// browser's own video recording keeps every frame. Captions, the tap ripple and the finger dot
// are injected into the page. The narration is spoken by Kokoro (tts.py) as the scenes play, and
// a line never waits for the hands: `say()` starts the voice and returns, the scene taps under
// it, and the next `say()` waits for the previous line to end. The clips and their start times
// land in video/<name>/voice.json for mix.sh to lay under the video.
import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { mkdirSync, writeFileSync } from 'node:fs';

const NAME = process.argv[2] ?? 'basics';
const BASE = 'http://127.0.0.1:8120';
const OUT = new URL(`./video/${NAME}/`, import.meta.url).pathname;
const FAIL = OUT + 'fail.png';

// 9:19.5 phone-ish canvas, recorded 1:1. 540px wide keeps the app's mobile
// breakpoint (max-width: 640px) active while being crisper than 390.
const W = 540, H = 1170;

const scenes = (await import(`./scenes/${NAME}.mjs`)).default;
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

const browser = await chromium.launch();
const ctx = await browser.newContext({
  viewport: { width: W, height: H },
  isMobile: true,
  hasTouch: true,
  deviceScaleFactor: 2,
  recordVideo: { dir: OUT, size: { width: W, height: H } },
  locale: 'en-US',
});
const page = await ctx.newPage();
const t0 = Date.now();   // the recording starts with the page

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
  if (wait > 0) await page.waitForTimeout(wait);
}

/** Starts a line as soon as the previous one has ended; returns how long it speaks. */
async function narrate(text) {
  const clip = await speak(text);
  await speechDone();
  lines.push({ file: clip.file, at: Date.now() - t0, text });
  speechUntil = Date.now() + clip.ms + 250;
  return clip.ms + 250;
}

/* ---------- overlay helpers (captions + touch ripple), injected once ---------- */
async function installOverlay() {
  await page.evaluate(() => {
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
  await page.evaluate((t) => {
    const c = document.getElementById('demo-caption');
    c.classList.remove('show');
    setTimeout(() => { c.textContent = t; c.classList.add('show'); }, 120);
  }, text);
  if (holdMs > 0) await page.waitForTimeout(holdMs);
}

/**
 * The unit of a scene: a spoken line with its caption. The voice starts (once the previous
 * line has ended) and the scene carries on tapping under it. `lead` is the beat before the
 * hands move, so the viewer hears what is about to happen.
 */
async function say(speech, text, { lead = 300 } = {}) {
  const ms = await narrate(speech);
  if (text != null) await caption(text);
  await page.waitForTimeout(lead);
  return ms;
}

async function captionOff() {
  await page.evaluate(() => document.getElementById('demo-caption')?.classList.remove('show'));
}

const settle = (ms) => page.waitForTimeout(ms);

/** Tap with a visible ripple so the viewer sees where the finger lands. */
async function tap(locator) {
  const box = await locator.boundingBox();
  if (box) {
    await page.evaluate(([x, y]) => {
      const r = document.createElement('div');
      r.className = 'demo-ripple';
      r.style.left = x + 'px'; r.style.top = y + 'px';
      document.body.appendChild(r);
      setTimeout(() => r.remove(), 600);
    }, [box.x + box.width / 2, box.y + box.height / 2]);
    await page.waitForTimeout(320);
  }
  await locator.click();
}

/** Types letter by letter — once per video, where watching the letters is the point. */
async function typeSlow(locator, text) {
  await locator.pressSequentially(text, { delay: 40 });
  await locator.press('Tab'); // blur, so Vaadin fires its change event
  await page.waitForTimeout(200);
}

/** Fills a field in one go, under the voice. */
async function type(locator, text) {
  await locator.click();
  await page.keyboard.insertText(text);
  await locator.press('Tab');
  await page.waitForTimeout(200);
}

/* ---------- real touch input: list-swipe.js ignores mice, hold-drag wants a still hold ---------- */
const cdp = await ctx.newCDPSession(page);

async function finger(x, y, state) {
  await page.evaluate(([x, y, state]) => {
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

const touch = (type, x, y) => cdp.send('Input.dispatchTouchEvent',
  { type, touchPoints: type === 'touchEnd' ? [] : [{ x, y, id: 1 }] });

async function glide(x0, y0, x1, y1, ms, state) {
  const steps = Math.max(8, Math.round(ms / 25));
  for (let i = 1; i <= steps; i++) {
    const t = i / steps, e = t < .5 ? 2 * t * t : 1 - (-2 * t + 2) ** 2 / 2; // ease in-out
    const x = x0 + (x1 - x0) * e, y = y0 + (y1 - y0) * e;
    await touch('touchMove', x, y);
    await finger(x, y, state);
    await page.waitForTimeout(ms / steps);
  }
}

/** Hold still until the item lifts (450 ms in hold-drag.js), then drag it and let go. */
async function holdAndDrag(from, to, { hold = 900, ms = 1300 } = {}) {
  await finger(from.x, from.y, 'down');
  await touch('touchStart', from.x, from.y);
  await page.waitForTimeout(hold);
  await finger(from.x, from.y, 'held');
  await glide(from.x, from.y, to.x, to.y, ms, 'held');
  await page.waitForTimeout(350);
  await touch('touchEnd');
  await finger(to.x, to.y, 'up');
}

/** A quick horizontal swipe, the way a thumb uncovers a chat app's actions. */
async function swipe(from, dx, ms = 380) {
  await finger(from.x, from.y, 'down');
  await touch('touchStart', from.x, from.y);
  await page.waitForTimeout(60);
  await glide(from.x, from.y, from.x + dx, from.y, ms, 'down');
  await touch('touchEnd');
  await finger(from.x + dx, from.y, 'up');
}

async function point(locator, fx = 0.5, fy = 0.5) {
  await locator.scrollIntoViewIfNeeded();
  const b = await locator.boundingBox();
  return { x: b.x + b.width * fx, y: b.y + b.height * fy };
}

/* ---------- the app's own landmarks ---------- */
const tab = (name) => page.locator('vaadin-tab', { hasText: name }).first();
const card = (name) => page.locator('.task-card', { hasText: name }).first();
const line = (text) => page.locator('.shared-list:not(.done) .list-item', { hasText: text }).first();
const chip = (label) => page.locator('.filter-chip', { hasText: label }).first();
const dialogButton = (name) => page.getByRole('dialog').getByRole('button', { name });

async function openSection(title) {
  const details = page.locator('vaadin-details.admin-section', {
    has: page.locator('vaadin-details-summary', { hasText: title }) }).first();
  if (await details.evaluate((d) => d.opened)) return; // a tap would fold it shut
  await tap(page.locator('vaadin-details-summary', { hasText: title }).first());
  await page.waitForTimeout(500);
  // Belt and braces: if the tap landed during a re-render, open the card directly.
  await page.evaluate((title) => {
    for (const d of document.querySelectorAll('vaadin-details.admin-section')) {
      if ((d.querySelector('vaadin-details-summary')?.textContent || '').startsWith(title)) d.opened = true;
    }
  }, title);
  await page.waitForTimeout(300);
}

/** The closing card every video shares, with its own tagline spoken over it. */
async function outro(tagline, spoken) {
  await page.evaluate((tagline) => {
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
  await page.waitForTimeout(1200);
}

/* --------------------------------- the video --------------------------------- */
try {
  await scenes({
    page, BASE,
    say, caption, captionOff, speechDone, settle,
    tap, type, typeSlow, holdAndDrag, swipe, point,
    tab, card, line, chip, dialogButton, openSection, outro,
  });
} catch (e) {
  console.error('FAILED:', String(e).split('\n')[0]);
  try { await page.screenshot({ path: FAIL }); } catch {}
  await ctx.close(); await browser.close();
  process.exit(1);
}
await ctx.close();   // flushes the video
await browser.close();
tts.stdin.end();
writeFileSync(OUT + 'voice.json', JSON.stringify(lines, null, 1));
console.log(`recorded ${NAME} to`, OUT);
process.exit(0);
