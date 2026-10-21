// Records the vertical FlashChores demo with captions: Playwright drives a running app (run.sh
// starts one on a fresh database) and the browser's own video recording keeps every frame.
// Captions, the tap ripple and the finger dot are injected into the page. The narration is spoken
// by Kokoro (tts.py) while recording, so each caption holds as long as its line takes; the clips and
// their start times land in video/voice/ and video/voice.json for mix.sh to lay under the video.
import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { mkdirSync, writeFileSync } from 'node:fs';

const BASE = 'http://127.0.0.1:8120';
const OUT = new URL('./video/', import.meta.url).pathname;
const FAIL = new URL('./fail.png', import.meta.url).pathname;

// 9:19.5 phone-ish canvas, recorded 1:1. 540px wide keeps the app's mobile
// breakpoint (max-width: 640px) active while being crisper than 390.
const W = 540, H = 1170;

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
mkdirSync(OUT + 'voice', { recursive: true });
const lines = [];
let speechUntil = 0;

async function speak(text) {
  const file = `${OUT}voice/${String(lines.length).padStart(2, '0')}.wav`;
  tts.stdin.write(JSON.stringify({ text, file }) + '\n');
  return { file, ms: (await reply()).ms };
}

async function speechDone() {
  const wait = speechUntil - Date.now();
  if (wait > 0) await page.waitForTimeout(wait);
}

/** Starts a line now (after the previous one ends) and returns how long it speaks. */
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

async function caption(text, holdMs = 2300, speech) {
  await installOverlay(); // survives SPA nav; reinstalls after a reload
  if (speech) holdMs = Math.max(holdMs, await narrate(speech));
  await page.evaluate((t) => {
    const c = document.getElementById('demo-caption');
    c.classList.remove('show');
    setTimeout(() => { c.textContent = t; c.classList.add('show'); }, 120);
  }, text);
  await page.waitForTimeout(holdMs);
}

async function captionOff() {
  await page.evaluate(() => document.getElementById('demo-caption')?.classList.remove('show'));
}

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

async function openSection(title) {
  const details = page.locator('vaadin-details.admin-section', {
    has: page.locator('vaadin-details-summary', { hasText: title }) }).first();
  if (await details.evaluate((d) => d.opened)) return; // a tap would fold it shut
  await tap(page.locator('vaadin-details-summary', { hasText: title }).first());
  await page.waitForTimeout(500);
  await page.evaluate((title) => {
    for (const d of document.querySelectorAll('vaadin-details.admin-section')) {
      if ((d.querySelector('vaadin-details-summary')?.textContent || '').startsWith(title)) d.opened = true;
    }
  }, title);
  await page.waitForTimeout(300);
}

const card = (name) => page.locator('.task-card', { hasText: name }).first();
const line = (text) => page.locator('.shared-list:not(.done) .list-item', { hasText: text }).first();
const chip = (label) => page.locator('.filter-chip', { hasText: label }).first();

/* --------------------------------- the demo --------------------------------- */
try {

// Scene 1 — landing + create a home
await page.goto(BASE);
await page.waitForSelector('vaadin-text-field');
await caption('Meet FlashChores ⚡ family chores, zero nagging', 2300, 'Meet FlashChores. Family chores, with zero nagging.');
// Type + blur so Vaadin fires its change event and enables the button.
const homeName = page.locator('vaadin-text-field').nth(0).locator('input');
await homeName.pressSequentially('The Nest', { delay: 40 });
await homeName.press('Tab');
await page.waitForTimeout(300);
const yourName = page.locator('vaadin-text-field').nth(1).locator('input');
await yourName.pressSequentially('Captain Mop', { delay: 40 });
await yourName.press('Tab');
await page.waitForTimeout(300);
await caption('No accounts, no emails — just nicknames 🦸', 2000,
  'No accounts, no emails. Just nicknames. Your real name never goes in.');
await tap(page.getByRole('checkbox', { name: 'I agree' }));
await page.waitForTimeout(300);
await tap(page.locator('vaadin-button', { hasText: 'Create home' }));

// Scene 2 — the share code dialog
await page.waitForSelector('vaadin-dialog-overlay');
await caption('One shared code invites the whole family', 2400, 'One shared code invites the whole family.');
await tap(page.locator('vaadin-button', { hasText: "Let's go" }));
await page.waitForTimeout(600);

// Scene 3 — the board
await page.waitForSelector('.task-card');
await caption('Starter chores, ready to tap', 2000, 'You start with a board of everyday chores.');

// Scene 4 — complete a chore, confetti
await caption('Did one? Tap it 👇', 1600, 'Did one? Just tap it.');
await tap(page.locator('.complete-area').first());
await page.waitForSelector('vaadin-dialog-overlay');
await tap(page.locator('vaadin-button', { hasText: 'Yes, I did it' }));
await caption('Confetti. Obviously. 🎉', 2200);
// The celebration asks "How was it?" — answer 😍 and close it.
await tap(page.locator('.feedback-btn').nth(2));
await page.waitForTimeout(500);
await tap(page.getByRole('dialog').getByRole('button', { name: /^Done/ }));
await page.waitForTimeout(500);

// Scene 5 — book a chore
await caption("Call dibs with “I'll do it” 🔖", 1800, "Call dibs with I'll do it, so nobody does it twice.");
await tap(page.locator('.book-btn', { hasText: "I'll do it" }).first());
await page.waitForTimeout(1200);

// Scene 6 — arrange the board by hold-and-drag
// The voice runs under the gesture rather than holding it up.
await narrate('Admins arrange the board. Hold a card, and drag it where it belongs.');
await caption('Admins arrange the board: hold a card…', 1600);
const from = await point(card('Water plants'), 0.5, 0.35);
const to = await point(card('Empty dishwasher'), 0.15, 0.35);
await caption('…and drag it where it belongs ✋', 200);
await holdAndDrag(from, to);
await page.waitForTimeout(1200);
await caption('Everyone’s phone updates live 📲', 1800, 'Every phone in the home updates live.');

// Scene 7 — shared lists: add, reorder, swipe, tick
await tap(page.getByRole('tab', { name: 'Lists' }));
await page.waitForTimeout(700);
await caption('Shared lists: groceries, to-dos, dinners 🛒', 1800, 'Shared lists, for groceries, to-dos and dinners.');
const addBox = page.getByRole('textbox', { name: 'Add an item…' });
for (const item of ['Milk', 'Eggs', 'Coffee', 'Bread', 'Bananas']) {
  await addBox.pressSequentially(item, { delay: 35 });
  await addBox.press('Enter');
  await page.waitForTimeout(450);
}
await page.waitForTimeout(400);
await caption('Hold a line to move it ✋', 1200, 'Hold a line to move it.');
await holdAndDrag(await point(line('Bananas'), 0.3, 0.5), await point(line('Milk'), 0.3, 0.15), { ms: 1100 });
await page.waitForTimeout(1200);
await caption('Swipe left to delete or edit 👈', 900, 'Swipe left to delete or edit.');
await swipe(await point(line('Coffee'), 0.75, 0.5), -230);
await page.waitForTimeout(1600);
await tap(line('Coffee').locator('.swipe-delete'));
await page.waitForTimeout(1000);
await caption('Got it? Tick it off ✅', 900, 'Got it? Tick it off.');
await tap(line('Milk').locator('vaadin-checkbox'));
await page.waitForTimeout(1500);
await tap(page.getByRole('tab', { name: 'Chores' }));
await page.waitForTimeout(600);

// Scene 8 — admin: add a seasonal chore (shows frequency presets + seasons)
await caption('Admins add chores in plain language…', 1800, 'Admins add chores in plain language.');
await tap(page.locator('vaadin-tab', { hasText: 'Admin' }));
await page.waitForTimeout(600);
await tap(page.locator('vaadin-details-summary', { hasText: 'Chores' }).first());
await page.waitForTimeout(700);
// Belt and braces: if the tap landed during a re-render, open the card directly.
await page.evaluate(() => {
  for (const d of document.querySelectorAll('vaadin-details.admin-section')) {
    const t = d.querySelector('vaadin-details-summary')?.textContent || '';
    if (t.startsWith('Chores')) { d.opened = true; }
  }
});
await page.waitForTimeout(400);
await tap(page.locator('vaadin-button', { hasText: 'Add chore' }));
// Dialog content is not a DOM descendant of vaadin-dialog-overlay in Vaadin 25 —
// address the fields by their accessible labels instead.
const choreName = page.getByLabel('Chore name');
await choreName.waitFor();
await choreName.pressSequentially('Shovel snow', { delay: 35 });
await choreName.press('Tab');
const choreEmoji = page.getByRole('dialog').getByRole('textbox', { name: 'Emoji' });
await choreEmoji.click();
await page.keyboard.insertText('❄️');
await choreEmoji.press('Tab');
await caption('“Once a year”, not “interval=365” 📅', 2000, 'Once a year. Not interval three sixty-five.');
await tap(page.getByLabel('How often?'));
await page.waitForTimeout(500);
await tap(page.getByRole('option', { name: 'Once a year' }));
await page.waitForTimeout(400);
await caption('Winter only — gone from the board in July', 2200, 'Winter only, so it is gone from the board in July.');
await tap(page.getByRole('checkbox', { name: 'Winter' }));
await page.waitForTimeout(400);
await tap(page.getByRole('button', { name: 'Save' }));
await page.waitForTimeout(800);

// Scene 9 — back to board: filter chips
await tap(page.locator('vaadin-tab', { hasText: 'Chores' }).first());
await page.waitForSelector('.filter-chip');
await caption('Filters: due now, weekly, yearly…', 2000, 'Filters show what is due now, weekly, or yearly.');
await tap(chip('Yearly'));
await page.waitForTimeout(1400);
await caption('Off-season chores wait their turn ❄️', 2200, 'Off-season chores wait their turn.');
await tap(chip('Off-season'));
await page.waitForTimeout(1600);
await tap(chip('All'));
await page.waitForTimeout(600);

// Scene 10 — diamonds: reward a tough chore, earn it, cash it in
await tap(page.locator('vaadin-tab', { hasText: 'Admin' }));
await page.waitForTimeout(600);
await caption('Tough chores can pay 💎 diamonds', 1800, 'Tough chores can pay diamonds.');
await openSection('Chores');
const vacuumRow = page.locator('.admin-section .list-row', { has: page.getByText('Vacuum', { exact: true }) }).first();
await vacuumRow.scrollIntoViewIfNeeded();
await tap(vacuumRow.locator('vaadin-menu-bar-button').first());
await page.waitForTimeout(300);
await tap(page.getByRole('menuitem', { name: 'Edit chore' }));
// The dialog's first integer field is the hidden custom interval: find the reward by its label.
const reward = page.getByRole('dialog').locator('vaadin-integer-field')
  .filter({ has: page.getByRole('spinbutton', { name: 'Reward credits' }) });
await reward.scrollIntoViewIfNeeded();
for (let i = 0; i < 5; i++) await tap(reward.locator('[part~=increase-button]'));
await page.waitForTimeout(400);
await tap(page.getByRole('dialog').getByRole('button', { name: 'Save' }));
await page.waitForTimeout(700);
await tap(page.locator('vaadin-tab', { hasText: 'Chores' }).first());
await page.waitForTimeout(600);
await card('Vacuum').scrollIntoViewIfNeeded();
await caption('Do it, and they’re yours', 1400, 'Do it, and the diamonds are yours.');
await tap(card('Vacuum').locator('.complete-area'));
await tap(page.locator('vaadin-button', { hasText: 'Yes, I did it' }));
await page.waitForTimeout(2200);
await tap(page.getByRole('dialog').getByRole('button', { name: /^Done/ }));
await page.waitForTimeout(800);
await captionOff();
await tap(page.locator('vaadin-tab', { hasText: 'Admin' }));
await page.waitForTimeout(600);
await openSection('Rewards');
await page.getByRole('button', { name: 'Redeem' }).scrollIntoViewIfNeeded();
await caption('…then cash them in for a treat 🍿', 1600, 'Then cash them in for a real treat, like movie night.');
await tap(page.getByRole('button', { name: 'Redeem' }));
const note = page.getByRole('dialog').getByRole('textbox', { name: /Note/ });
await note.waitFor();
await note.pressSequentially('Movie night', { delay: 40 });
await page.waitForTimeout(400);
await tap(page.getByRole('dialog').getByRole('button', { name: 'Redeem' }));
await page.waitForTimeout(900);

// Scene 11 — stats
await tap(page.locator('vaadin-tab', { hasText: 'Stats' }));
await page.waitForTimeout(500);
await narrate('Streaks, fairness and rewards, built in.');
await caption('Streaks, fairness & rewards built in 🏆', 1200);
// The 💎 cards appear once diamonds were earned and redeemed — scroll to them like a thumb would.
await page.getByText('💎 Diamonds I earned').evaluate((el) => el.scrollIntoView({ behavior: 'smooth', block: 'center' }));
await page.waitForTimeout(2200);
await speechDone();

// Scene 12 — outro
await page.evaluate(() => {
  const o = document.createElement('div');
  o.id = 'demo-outro';
  o.style.cssText = `position:fixed;inset:0;z-index:99997;display:flex;flex-direction:column;
    align-items:center;justify-content:center;gap:18px;color:#fff;text-align:center;
    background:linear-gradient(135deg,#10b981,#0ea5e9);opacity:0;transition:opacity .6s ease;
    font-family:-apple-system,'Segoe UI',Roboto,sans-serif;`;
  o.innerHTML = `<div style="font-size:64px">⚡</div>
    <div style="font-size:40px;font-weight:900">FlashChores</div>
    <div style="font-size:22px;font-weight:600;opacity:.92">Tap a chore. Keep it fair.<br>Celebrate together.</div>
    <div style="font-size:17px;font-weight:600;opacity:.8;margin-top:12px">Vaadin Flow · Spring Boot · one tiny server</div>`;
  document.body.appendChild(o);
  requestAnimationFrame(() => { o.style.opacity = '1'; });
});
await captionOff();
await narrate('FlashChores. Tap a chore, keep it fair, and celebrate together.');
await speechDone();
await page.waitForTimeout(1200);

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
console.log('recorded to', OUT);
process.exit(0);
