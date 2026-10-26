# Demo videos — plan (2026-10-26)

Status: **videos 1 and 2 recorded**. `tools/demo-video/run.sh basics` →
`video/flashchores-basics.mp4` (50.6 s, 15 lines, 71 % voiced); `run.sh ways` →
`video/flashchores-ways.mp4` (65.8 s, 17 lines, 66 % voiced, three screens). Video 3 is a
script only. The recorder plays a named scene set (`scenes/<name>.mjs`) on one screen or several;
see "What the recorder needs" for what video 3 still requires.

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

## Video 1 — Three ways to run it (≈ 70 s) — recorded

`scenes/ways.mjs`, 65.8 s, 17 lines, 126 words, 66 % voiced. Starts on an existing home
("The Nest") so the first ten seconds show the family, not a form. Three screens: Alex's phone
(A), Sam's phone (S), the kitchen tablet (T). The home is built off camera by the scene's
`setup`: Alex creates it, a backup with last week's chores (Mia did most of them) and Alex's
three "Vacuum" today is restored, and Alex signs back in with the PIN — so the chore master
and the fairness lock are on the board at the first frame, which is why they live here and
not in the basics video. Join approval is switched off in that backup, so Sam walks straight
in. As recorded (the t column is where each line starts):

| t | Screen | Voice | Caption |
|---:|---|---|---|
| 0 | A: the board, Alex and Mia on the leaderboard, 🥇 in the header | FlashChores fits the way your family already works. | Three ways to run it |
| 3 | A: the same board | Last week's top helper wears the medal. | 🥇 Last week's best |
| 7 | A: taps the locked "Vacuum", confirms, the blocked toast | Three in a row? It's someone else's turn. | You ×3 🔒 |
| 11 | A: invite menu opened, the code chip in it | Everyone on their own phone? Share one code. | 1 · Everyone's phone |
| 15 | S: Join tab, code prefilled, "Sam" typed, Join | A nickname is all a phone needs. | No accounts |
| 17 | S: board; taps "Empty dishwasher", confirms, confetti | Sam does the dishes… | 👇 |
| 20 | A: "Done today" opened, Sam's line in it, Sam's chip | …and it's on Alex's phone before the sink is dry. | Live on every phone 📲 |
| 25 | A: Admin → Chore rules, division style → Rotating | Prefer turns? Switch the board to rotating. | 2 · Who does what |
| 29 | A: the enforced switch, off | Enforce it, or keep it as a nudge. | Enforced · or suggested |
| 31 | A: board, "⭐ Your turn" on Alex's card, "Mia today", "Sam today" | Each member gets one chore a day, rotating. | ⭐ Your turn |
| 36 | T: "Set up a shared family device", PIN as dots, Pair | No phone for everyone? Pair the kitchen tablet. | 3 · The kitchen tablet |
| 39 | T: the family board with the 📺 badge, no Admin tab | It shows the board for the whole family. | One board for all |
| 42 | T: taps "Take out trash", the face picker | Tap a chore, tap your face. | Tap your face |
| 45 | T: picks Mia, her celebration | It counts for that person, streaks and all. | Counts for them 🎉 |
| 50 | A: Members → "Add a member without a phone", "Lu" | A child with no phone still gets a chip… | No phone yet |
| 54 | A: Log a chore for someone → Lu, "Water plants", Log it | …and a parent logs their chores. | Log a chore for someone |
| 60 | Outro card | One home. However your family runs. | ⚡ FlashChores |

Changes from the first draft: the chore-master and fairness lines came in (the setup makes
them possible); the enforced switch is toggled in the same Chore rules visit as the division
style, and the board with "⭐ Your turn" is shown after both; the tablet picks Mia, since Lu is
only added in the scene after it; the one typed field is Sam's nickname.

Notes
- The division-style scene is the only one that needs a settings screen. Keep it to the
  select and the toggle; do not open anything else.
- The shared device needs the admin PIN typed; type it as dots and never show the real value
  in a caption (the demo database is throwaway, but the habit matters). The pairing dialog's
  PIN field is a plain text field in the app, so the scene switches the input to `password`
  before typing — a recording trick, not a product change.
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
| 0 | Board, a card with a 💎 5 pill | Some chores are worth more. Admins say how much. | 💎 5 on the card |
| 5 | Tap it, confetti, "+5 💎" toast | Do it, and the diamonds are yours. | +5 💎 |
| 10 | The celebration's "Just 1 more to 🌱 Sprout (+7 💎)!" line | And the app tells you when something bigger is close. | Just 1 more… |
| 15 | Admin → Rewards → Spree bonuses row | Every day for a week? A spree bonus. | 7 days → 💎 |
| 20 | Trophy rewards rows, 🌱 Sprout set to 7 | Trophies at 5, 10, 25 chores and up — each can pay. | 🌱 🧹 🥉 🥈 🏆 🏅 🎓 🦄 |
| 26 | Board, fifth tap: the unlock dialog, shine, "+7 💎 reward" | Five chores in: your first trophy. | New trophy: Sprout! |
| 31 | "See my trophies" → the shelf, progress bar 5 / 10 | Trophies are kept, not just celebrated. | Your shelf 🏆 |
| 36 | Leaderboard chip with 🌱 on the avatar | Your best one sits on your chip, for all to see. | On the leaderboard |
| 41 | Celebration dialog, "Oops — undo this" | Mis-tap? Undo, and the diamonds go back too. | Undo takes it back |
| 46 | Admin → Rewards → Redeem, "Movie night" | Then spend them on something real. | 🍿 Movie night |
| 51 | Stats: 💎 earned, redeemed, balance | Earned, redeemed, and what's left — all on Stats. | Earned · redeemed · balance |
| 56 | Admin: the trophy field left empty | Don't want incentives? Leave it empty. Trophies still count. | Optional, always |
| 61 | Outro card | Earn them. Keep them. Spend them together. | ⚡ FlashChores |

13 lines, ≈ 110 words.

Notes
- The "Just 1 more" line lands on the fourth tap, the trophy on the fifth: two taps, two
  payoffs, which is why the video opens at three approved chores and not zero.
- The spree bonus cannot be *earned* inside a one-minute recording (it needs days); it is
  shown being configured, not paid. Say so with the words ("Every day for a week?"), not a
  fake payout.
- The undo line is here rather than in video 2 because the diamonds going back is the point;
  the plain undo is implied by the dialog in video 2.

---

## What the recorder needs

`record.mjs` used to record one page in one context, and `mix.sh` mixed the newest `.webm`.
For three videos, and for video 1's three screens:

1. ~~**Scenes by name.**~~ Done: `run.sh <name>` plays `scenes/<name>.mjs`, records to
   `video/<name>/` and `mix.sh <name>` writes `video/flashchores-<name>.mp4`. The voice cache
   is keyed by text, so re-recording one video does not re-synthesise the others.
2. ~~**A second phone and a tablet.**~~ Done: a scene asks for more screens with
   `screen(name)` — each a `browser.newContext()` with its own `recordVideo`, Playwright
   records per context — and says which is on camera with `onCamera(screen)`; the helpers
   (`say`, `tap`, `card`…) act on that screen. The tablet is recorded in **portrait at the
   same 540 × 1170 canvas** (an iPad on a counter stands either way), so every stream shares
   one frame size. The recorder writes the cut list (which recording, from when to when, in
   script order) to `video/<name>/cuts.json` beside `voice.json`, with each line's start time
   measured in the finished video; `mix.sh` trims and concatenates the cuts with ffmpeg
   before laying the voice under them. A one-screen video is a single cut, so `basics` mixes
   as before. `DEMO_PORT` moves the app and the recorder off 8120, so two videos can record
   side by side.
3. ~~**Unrecorded setup.**~~ Done: a scene set's optional `setup` export runs before the camera
   rolls, on the same screens. `ways` creates the home, restores a backup that carries last
   week's chores and today's three in a row, and signs Alex back in; video 3's setup can pre-do
   three chores and switch confirmation off the same way. The existing `run.sh` fresh-database
   start stays.
4. ~~**`caption()` without `speech`.**~~ Done: a scene's unit is `say(speech, caption)`, which
   starts the voice and returns so the taps run under it; the next `say()` waits for the line
   to end. The serial `caption(text, hold, speech)` form is gone, and with it the original
   2:07 scene list (in git history at `07e5652`, should it ever be wanted again).
5. ~~**PIN on camera.**~~ Done: the shared-device pairing types the admin PIN into the field
   switched to `password` for the recording, and neither the caption nor the voice says the
   digits.

Cost estimate: the recorder changes are a day; each script is an afternoon of tuning holds
against the real voice, since Kokoro's timing only shows up when it speaks.

## Decisions to take

1. Three videos, with rewards separate from the basics — **recommended above**.
2. ~~Video 1's tablet in portrait at the phone canvas, or a landscape tablet letterboxed into the
   vertical frame (truer to a counter, but a quarter of the height).~~ Recorded in portrait at
   the phone canvas.
3. Whether the basics video gets the 3 s pointer to the rewards video.
