# Demo videos — plan (2026-10-26)

Status: **videos 2 and 3 recorded** (`tools/demo-video/run.sh basics` →
`video/flashchores-basics.mp4`, 50.6 s, 15 lines, 71 % voiced; `run.sh rewards` →
`video/flashchores-rewards.mp4`, 61.2 s, 15 lines, 70 % voiced). Video 1 is a script only. The
recorder plays a named scene set (`scenes/<name>.mjs`) and a scene can set the home up off
camera before it calls `start()`; see "What the recorder needs" for what video 1 still requires.

## What the current video tells us

`video/voice.json` holds the 22 narration lines with their start times, so the pacing can be
measured rather than guessed.

| | |
|---|---|
| Length | 2:07 |
| Narrated lines / words | 22 / 171 |
| Voiced time | ≈ 55 s, **43 %** of the video |
| Average gap between line starts | 5.7 s, for lines that take ≈ 2.5 s to say |

Where the time goes:

| Scene | Seconds | Share |
|---|---:|---:|
| Landing, create a home, share code | 19.5 | 15 % |
| Tap, confetti, feedback | 6.7 | 5 % |
| "I'll do it" | 4.9 | 4 % |
| Hold-and-drag a card | 10.7 | 8 % |
| Lists: five items typed, drag, swipe, tick | 25.0 | **20 %** |
| Admin adds a seasonal chore | 16.7 | 13 % |
| Filter chips | 11.5 | 9 % |
| Diamonds: set, earn, redeem | 23.3 | **18 %** |
| Stats and outro | 8.8 | 7 % |

Three things make it feel long, and all three are in the script, not the app:

1. **Voice and hands take turns.** `caption(text, hold, speech)` speaks the line to the end,
   then holds the caption, then the tap happens. `narrate()` — which starts the voice and lets
   the next action run under it — is used only twice. More than half the video is silent
   waiting.
2. **Everything is typed on camera.** Five grocery items, a chore name, an emoji and a note at
   35–40 ms per key is ≈ 12 s of watching letters appear. Typing is only interesting once.
3. **Three gestures are demonstrated** (card drag, line drag, swipe), each 3–4 s of
   deliberately slow finger. One per video is plenty.

Captions also repeat the voice almost word for word, so the eye and the ear carry the same
information and the viewer waits for both.

## Rules for the new scripts

- **Hook in 3 s, payoff by 15 s.** Confetti (or the trophy) is the payoff; the create-home flow
  is not. Where the story does not need it, start on a board that already exists.
- **Voice leads, hands follow.** Every line goes through `narrate()`; the tap starts ≈ 300 ms
  into the line. A caption holds no longer than its line. Target **≥ 65 % voiced**.
- **One idea per line, ≤ 9 words spoken, ≤ 5 words on the caption**, and the caption says
  something the voice does not (the emoji, the number, the name of the control).
- **Type once per video.** The first field is typed; everything else is filled with
  `insertText` under the voice.
- **One gesture demo per video**, where that gesture is the point.
- **Word budget.** Kokoro at 1.15× speaks ≈ 3 words/s. A 60 s video at 65 % voiced holds
  ≈ 115 words, so **14–16 lines of 6–8 words**. The drafts below stay inside that.
- **Same outro card** (⚡ FlashChores, one tagline) on all three, 4 s, so they read as a set.

## The split

| | Video | Audience | Length |
|---|---|---|---:|
| 1 | **Three ways to run it** — own phones, division styles, the shared device, a child without a phone | a parent asking "would this fit *our* family?" | 70 s |
| 2 | **FlashChores in a minute** — the current video, cut to the basics | anyone who has never seen it | 60 s |
| 3 | **Diamonds, streaks and trophies** — earn, keep, spend | a parent deciding whether incentives are for them | 65 s |

**Keep 3 separate rather than folding it into 2.** The basics video is the one that will be
watched most and must stay under a minute; diamonds alone took 23 s in the current cut, and
trophies add a ladder, an unlock and a shelf. Folded in, video 2 lands back at 1:45. A home
that never sets a reward also gets a complete, honest picture from video 2 alone — the
app's own stance that rewards are optional. If a nod is wanted, video 2 ends on one line
("Tough chores can pay diamonds — that's the next video"), 3 s.

Video 1 is the one the current recording cannot make: it needs **a second phone and a tablet**.
See "What the recorder needs" at the end.

---

## Video 1 — Three ways to run it (≈ 70 s)

Starts on an existing home ("The Nest", two chores done) so the first ten seconds show the
family, not a form. Three contexts: Alex's phone (A), Sam's phone (S), the kitchen tablet (T).

| t | Screen | Voice | Caption |
|---:|---|---|---|
| 0 | A: the board, two chips on the leaderboard | FlashChores fits the way your family already works. | Three ways to run it |
| 4 | A: header, home code chip tapped | Everyone on their own phone? Share one code. | 1 · Everyone's phone |
| 8 | S: Join tab, code prefilled, nickname typed | A nickname is all a phone needs. | No accounts |
| 13 | S: board; S taps "Empty dishwasher", confirms | Sam does the dishes… | 👇 |
| 17 | A: the board updates, Sam's chip climbs | …and it's on Alex's phone before the sink is dry. | Live on every phone 📲 |
| 22 | A: Admin → Division style, the two options | Prefer turns? Switch the board to rotating. | 2 · Who does what |
| 27 | A: board shows "⭐ Your turn" on one card, "Sam today" on another | Each member gets one chore a day, rotating. | ⭐ Your turn |
| 32 | A: the enforced toggle | Enforce it, or keep it as a nudge. | Enforced · or suggested |
| 36 | T: Join → "Set up a shared family device", PIN typed | No phone for everyone? Pair the kitchen tablet. | 3 · The kitchen tablet |
| 42 | T: the family board, no "me" chip | It shows the board for the whole family. | One board for all |
| 46 | T: taps a chore, the face picker opens | Tap a chore, tap your face. | Tap your face |
| 50 | T: picks Lu, confetti | It counts for *that* person, streaks and all. | Counts for them 🎉 |
| 55 | A: Members card, "Add a member without a phone" | A child with no phone still gets a chip… | No phone yet |
| 60 | A: Admin → Log a chore for someone, Lu, "Water plants" | …and a parent logs their chores. | Log a chore for someone |
| 65 | Outro card | One home. However your family runs. | ⚡ FlashChores |

16 lines, ≈ 105 words.

Notes
- The division-style scene is the only one that needs a settings screen. Keep it to the
  select and the toggle; do not open anything else.
- The shared device needs the admin PIN typed; type it as dots and never show the real value
  in a caption (the demo database is throwaway, but the habit matters).
- The face picker on the tablet is the one gesture-free "moment" of this video and should hold
  a beat longer than its line.

## Video 2 — FlashChores in a minute (≈ 60 s)

The current video, cut: lists keep one add and one tick (no drag, no swipe), the card drag
goes (video 1 has the drag on a list line; the board drag can be a short clip of its own),
diamonds move to video 3, and only the home name is typed on camera.

| t | Screen | Voice | Caption |
|---:|---|---|---|
| 0 | Landing | Meet FlashChores. Family chores, zero nagging. | Meet FlashChores ⚡ |
| 3 | Home name typed, nickname filled | No accounts, just nicknames. | Just nicknames 🦸 |
| 7 | Code dialog | One code invites the whole family. | One shared code |
| 10 | Board | You start with everyday chores, ready to tap. | 11 starter chores |
| 14 | Tap, confirm, confetti | Did one? Tap it. Confetti, obviously. | 🎉 |
| 19 | Feedback row, 😍 | Say how it felt. | How was it? 😖 🙂 😍 |
| 22 | "I'll do it" | Call dibs, so nobody does it twice. | I'll do it 🔖 |
| 26 | "Done today (1)" opened: who did what, when | Everyone sees who did what today. | Done today ✅ |
| 30 | Lists tab, "Milk" and "Bread" added, "Milk" ticked | Groceries, to-dos and dinners, shared too. | Shared lists 🛒 |
| 35 | Admin → Add chore, "Shovel snow ❄️", Once a year, Winter | Admins add chores in plain language. Once a year, winter only. | Once a year · Winter |
| 42 | Board, Yearly chip, Off-season chip | Filters show what's due now, and what waits. | Due now · Off-season |
| 47 | Stats tab, four counts | Streaks, fairness and stats, built in. | Today · week · month · all time |
| 47 | Outro card | Tap a chore. Keep it fair. Celebrate together. | ⚡ FlashChores |

15 lines, 86 words, recorded at 50.6 s. Two lines from the first draft went: the **fairness
lock** needs the member to have done one chore three times before the camera rolls, and the
**chore master** needs last week's history — neither exists in a home created on camera, and
the basics video creates its home on camera on purpose. Both belong in video 1, which starts
on an existing home and has an unrecorded setup step. The Stats scene shows the trophy shelf
at the top of My stats for free (one approved chore: "Next: 🌱 Sprout — 1 / 5").

## Video 3 — Diamonds, streaks and trophies (≈ 65 s)

The story is earn → keep → spend. Starts on a board where Alex has **three** approved chores,
so the trophy arrives on the fifth tap, inside the video. Confirmation is switched off in setup
so the taps are quick.

| t | Screen | Voice | Caption |
|---:|---|---|---|
| 2 | Board, the finger on "Cook dinner"'s 💎 5 pill | Some chores are worth more. Admins say how much. | 💎 5 on the card |
| 5 | Tap it, confetti, "+5 💎 credits earned!" toast | Do it, and the diamonds are yours. | +5 💎 |
| 8 | The celebration's "Just 1 more to 🌱 Sprout (+7 💎)!" line | And the app tells you when something bigger is close. | Just 1 more… |
| 12 | Admin → Rewards: 7 days and 10 credits filled, "Add tier" | Every day for a week? A spree bonus. | 7 days → 💎 |
| 16 | Trophy rewards rows, the finger on 🌱 Sprout's 💎 7 | Trophies at 5, 10, 25 chores and up — each can pay. | 🌱 🧹 🥉 🥈 🏆 🏅 🎓 🦄 |
| 20 | Board, fifth tap: the unlock dialog, shine, "+7 💎 reward" | Five chores in: your first trophy. | New trophy: Sprout! |
| 26 | "See my trophies" → the shelf, "Next: 🧹 Helper — 5 / 10" | Trophies are kept, not just celebrated. | Your shelf 🏆 |
| 29 | The finger on the 🦄 Legend tile | Eight rungs, from five chores to a thousand. | 5 → 1000 chores |
| 33 | Leaderboard chip "Alex 5 · 💎 12" with 🌱 on the avatar | Your best one sits on your chip, for all to see. | On the leaderboard |
| 37 | Sixth tap (💎 17), "Oops — undo this", "Taken back" (💎 12) | Mis-tap? Undo, and the diamonds go back too. | Undo takes it back |
| 42 | Admin → Rewards → Balances → Redeem | Then spend them on something real. | 🍿 Movie night |
| 44 | Amount 10, "Movie night" typed, Redeem | Pick an amount, and say what it was for. | Amount · note |
| 47 | Stats: 💎 earned 12 · redeemed 10 · balance 2, "Movie night 💎 10" | Earned, redeemed, and what's left — all on Stats. | Earned · redeemed · balance |
| 52 | Admin → Rewards: the finger on 🧹 Helper's empty field | Don't want incentives? Leave it empty. Trophies still count. | Optional, always |
| 57 | Outro card | Earn them. Keep them. Spend them together. | ⚡ FlashChores |

15 lines, 125 words, recorded at 61.2 s and 70 % voiced.

Notes
- The "Just 1 more" line lands on the fourth tap, the trophy on the fifth: two taps, two
  payoffs, which is why the video opens at three approved chores and not zero.
- The spree bonus cannot be *earned* inside a one-minute recording (it needs days); it is
  shown being configured, not paid. Say so with the words ("Every day for a week?"), not a
  fake payout.
- The undo line is here rather than in video 2 because the diamonds going back is the point;
  the plain undo is implied by the dialog in video 2. The mis-tap is a second "Cook dinner",
  so the chip's 💎 visibly drops from 17 to 12.
- **What was recorded** (`scenes/rewards.mjs`). Off camera, before `start()`: "The Nest" is
  created with Alex as admin, "Ask did you do this?" is switched off (Chore rules), "Cook
  dinner" gets 💎 5 (Chores → ⋯ → Edit chore → Reward credits), 🌱 Sprout gets 💎 7 (Rewards →
  Trophy rewards), and Alex does Empty dishwasher, Take out trash and Vacuum; the admin
  sections are folded again. That takes ≈ 12–14 s and `mix.sh` cuts it off.
- Kokoro speaks the first draft's 13 lines (108 words) in 37.5 s, so the draft recorded at 52 s
  and could not reach 60 s at 65 % voiced. Two lines were added where the picture earns a beat
  — the shelf's ladder and the redeem dialog — and each payoff (the pill, "Just 1 more", the
  unlock, the undo, the ledger) is held for a moment after its line ends (`beat()` in the
  scene). The plan's own 13 lines are spoken word for word.
- The redeem note is the one field typed on camera; the spree tier and the amount are filled
  under the voice.

---

## What the recorder needs

`record.mjs` records one page in one context, and `mix.sh` mixes the newest `.webm`. For
three videos, and for video 1's three screens:

1. ~~**Scenes by name.**~~ Done: `run.sh <name>` plays `scenes/<name>.mjs`, records to
   `video/<name>/` and `mix.sh <name>` writes `video/flashchores-<name>.mp4`. The voice cache
   is keyed by text, so re-recording one video does not re-synthesise the others.
2. **A second phone and a tablet.** Two more `browser.newContext()` calls, each with its own
   `recordVideo` — Playwright records per context. The tablet is recorded in **portrait at
   the same 540 × 1170 canvas** (an iPad on a counter stands either way), so all three streams
   share one frame size and `ffmpeg concat` splices them in script order without scaling. The
   script writes a cut list (which context, from when to when) beside `voice.json`.
3. ~~**Unrecorded setup.**~~ Done: the recording starts with the page, and a scene calls
   `start()` once the camera matters; `voice.json` carries that moment as `startMs` (the lines'
   times are relative to it) and `mix.sh` cuts the recording there with `-ss`. Video 3 creates
   the home, switches confirmation off, sets the 💎 5 chore and the 🌱 Sprout reward and does
   Alex's three chores before `start()`. Video 1 does the same with Sam joined and three of one
   chore in a row for the fairness lock. The `run.sh` fresh-database start stays.
4. ~~**`caption()` without `speech`.**~~ Done: a scene's unit is `say(speech, caption)`, which
   starts the voice and returns so the taps run under it; the next `say()` waits for the line
   to end. The serial `caption(text, hold, speech)` form is gone, and with it the original
   2:07 scene list (in git history at `07e5652`, should it ever be wanted again).
5. **PIN on camera.** The shared-device pairing types the admin PIN; the field is a password
   field, but the caption and the voice never say the digits.

Cost estimate: the recorder changes are a day; each script is an afternoon of tuning holds
against the real voice, since Kokoro's timing only shows up when it speaks.

## Decisions to take

1. Three videos, with rewards separate from the basics — **recommended above**.
2. Video 1's tablet in portrait at the phone canvas, or a landscape tablet letterboxed into the
   vertical frame (truer to a counter, but a quarter of the height).
3. Whether the basics video gets the 3 s pointer to the rewards video.
