# FlashChores — Specification & User Stories

Status: living document. Covers the original (Phase 1) features, the admin/stats
iteration (Phase 2), and the scheduling / rewards / i18n / PWA iteration (Phase 3).
Terms in **bold** map to concepts in the code.

## 1. Glossary

- **Home** — a household. Identified by a short, shareable **home code**
  (7 characters from an alphabet without I/O/0/1).
- **Member** — a person in a home. Identified per device (a phone = a member): the member
  id and a per-device **secret** are kept in the browser's **local storage** and restored
  into each new session; the secret is what proves the device is that member.
- **Shared device** — a tablet or phone the whole family uses (the one in the kitchen). Not
  a member: it has its own secret, shows the board for nobody in particular, and whoever did
  a chore picks their name when tapping it. Paired with the admin PIN; never an admin.
- **Join / rejoin request** — a device asking an admin to let it in: either a first-time
  **join** (default) or an existing member **signing back in** after losing its stored
  identity (browsing data cleared). Held as `PENDING` / `APPROVED` / `REJECTED` with a
  secret **device token** that only the requesting browser holds.
- **Admin** — a member with elevated rights. The creator of a home is the first admin.
  Proven by the **admin PIN**; admins can promote other members.
- **Chore** (a.k.a. task) — a repeatable household task (e.g. "Empty dishwasher").
  May have an **interval** (due every N days), **availability hours** (times of day),
  and a **credit value**.
- **Completion** — a record that a member did a chore at a time. Has a **status**
  (`PENDING` / `APPROVED` / `REJECTED`) and optional **feedback**.
- **Other help** — a completion with no chore behind it (`taskId == null`): the member
  wrote down what they did (`Completion.note`) because the board had no card for it.
  Always starts `PENDING`, whatever the home's approval setting says.
- **Feedback** — a member's reaction to doing a chore: `HATE` / `OK` / `LOVE`.
- **Require-approval** — a per-home toggle. When on, completions start `PENDING`
  and only count once an admin approves them. When off, completions are `APPROVED`
  instantly.
- **Daily target** — how many chores each member is expected to do per day (1–3,
  default 1).
- **Booking** — a member's reservation of a chore ("I'll do it"); blocks others
  until done, cancelled, or expired.
- **Division style** — how chores are divided: `DEFAULT` (free-for-all) or
  `ROTATING` (one assigned chore per member per day).
- **Credit** — reward points (💎). Earned from a chore's credit value, a **spree
  tier** (X consecutive days → Y credits) or a **trophy reward**; spent via an admin
  **redemption**.
- **Trophy** — a rung of the fixed ladder of personal approved-chore counts
  (5/10/25/50/100/250/500/1000, `Trophy`), each with an emoji and a name. A member owns every
  rung their all-time count has reached; nothing is stored. An admin may attach a 💎
  **trophy reward** to a rung (`TrophyReward`).
- **Join link** — `/?join=CODE`; opens the landing page with the Join tab
  preselected and the code prefilled.

## 2. Roles & permissions

| Capability | Member | Admin |
|---|:--:|:--:|
| Join a home, complete chores, give feedback | ✅ | ✅ |
| Book a chore ("I'll do it") / cancel own booking | ✅ | ✅ |
| Snooze a chore ("remind me later" — a one-shot push about that chore) | ✅ | ✅ |
| Set, move, snooze or cancel the home's reminder about a to-do / grocery line | ✅ | ✅ |
| Choose colour scheme and palette (per device) | ✅ | ✅ |
| Log **other help** the board has no card for | ✅ | ✅ |
| Log own other help so it counts at once, naming its reward | — | ✅ |
| Accept / decline other help, and set its reward | — | ✅ |
| Turn accepted other help into a new chore | — | ✅ |
| Copy/share the home code and join link | ✅ | ✅ |
| Switch UI language | ✅ | ✅ |
| Add / tick / untick / delete / clear lines on the shared grocery and to-do lists | ✅ | ✅ |
| See own statistics | ✅ | ✅ |
| See home-wide statistics | — | ✅ |
| Add / edit / delete chores (incl. interval, hours, credits) | — | ✅ |
| Create / rename / delete chore groups; reorder chores and groups | — | ✅ |
| Approve / reject pending completions | — | ✅ |
| Delete / correct any completion | — | ✅ |
| Rename / remove members, promote / demote admins | — | ✅ |
| Add a member who has no phone of their own | — | ✅ |
| Pair a **shared family device** (with the admin PIN); unpair one | — | ✅ |
| Ask to sign back in as an existing member | ✅ | ✅ |
| Approve / reject a join or rejoin request | — | ✅ |
| Change home settings (approval, rejoin gate, division style, booking hold, daily target, other help, PIN, name) | — | ✅ |
| Manage spree tiers, view balances, redeem credits | — | ✅ |
| Set a 💎 reward per trophy | — | ✅ |
| Backup / restore the family's data | — | ✅ |
| Delete the home and all its data | — | ✅ |

Non-admins have **read/complete** rights only; all create/update/delete of shared
config is admin-only — that is the "admin has CRUD over everything" requirement.

**Checked when the action is taken, not when the screen was drawn.** The Admin tab and the
board's ＋ card exist only for admins, but a dialog opened from them hangs off the UI and
outlives the redraw that removes them, so the role is re-read at action time:
`AdminPanel.stillAdmin` (through `ChoreService.isAdminOf`) guards every action in the tab and
refuses a demoted admin's open dialog with a notice, and the service methods that take the
acting admin — `decideRejoin`, `completeFor`, `approve`, `reject`, `logOtherHelpAsAdmin`,
`CreditService.redeem`, `BackupService.restore` — make the same check themselves
(`MemberRepository.findAdmin`: the member exists, is an admin, and is in *that* home), so they
hold whatever screen the call came from.

## 3. User stories

### Phase 1

- **US-01 Create a home.** As a new user, I can create a home with a name and my
  own name, so that I get a shareable home code and become its admin. The name
  fields hint at privacy-friendly values ("Our family", "Mom / Dad", nickname tips).
- **US-02 Join a home.** As a family member, I can ask to join with a home code and my
  name; by default an admin approves the request before I'm added, so a guessed or leaked
  code alone can't put a stranger on the board. (An admin can turn the gate off per home.)
- **US-03 Share the code.** As a member, I can copy/share the home code, so that
  others can join.
- **US-04 Complete a chore.** As a member, I can tap a chore card when I do it, so
  that it's recorded and celebrated.
- **US-05 Add a chore.** (Admin-only — see US-10.)
- **US-06 Fair rotation.** As a household, no one may do the *same* chore more than
  **N times in a row** (admin setting `Home.maxInARow`, default 3, choices No limit / 2 / 3 /
  4 / 5 / 10); the next tap is blocked until someone else does that chore. "No limit" turns
  the rule off for homes where one person genuinely owns a chore.
- **US-07 Celebrations.** As a member, I see confetti on completion, a "new chore
  unlocked" popup the first time I do a chore, and a trophy at 5/10/25/50/100/250/500/1000
  personal chores (US-63).
- **US-08 Live sync.** As a member, when anyone completes a chore my screen updates
  live.

### Phase 2

- **US-09 Admin identity via PIN.** As the creator, I receive a private **admin
  PIN** when I create the home. As an admin on a new phone, I can enter the home
  code + admin PIN to (re)gain admin rights. Wrong PIN is rejected.
- **US-10 Chore CRUD.** As an admin, I can add, rename, re-emoji, and delete chores.
  Deleting a chore also removes its completions.
- **US-11 Member management.** As an admin, I can rename a member, remove a member
  (and their completions), and promote/demote members as admins. I cannot remove
  the last admin.
- **US-12 Optional approval.** As an admin, I can turn "require approval" on/off for
  the home. When on, each completion is **pending**; when off, completions count
  instantly.
- **US-13 Approve / reject.** As an admin, I can see all pending completions (who,
  which chore, when, their feedback) and approve or reject each. Approved
  completions count toward stats, leaderboard and trophies; rejected ones don't.
- **US-14 Completion correction.** As an admin, I can delete any completion to fix
  mistakes.
- **US-15 Daily target.** As an admin, I can set how many chores each member is
  expected to do per day (1–3, default 1). As a member, I see my **"done today /
  target"** progress.
- **US-16 Chore feedback.** As a member, when I complete a chore I can rate it
  `HATE` / `OK` / `LOVE` (optional), so the family learns which chores are disliked.
- **US-17 My statistics.** As a member, I can see my own stats: total chores, chores
  by type, my feedback split, and my daily-target adherence over the last 7 days.
- **US-18 Home statistics.** As an admin, I can see home-wide stats: chores per
  member, chore popularity, feedback per chore (what's hated), a 14-day activity
  trend, and per-member daily-target adherence.
- **US-19 Backup.** As an admin, I can download a JSON backup of the whole family
  (settings, members, chores, completions, feedback, credits, spree tiers).
- **US-20 Restore.** As an admin, I can upload a backup to restore the family,
  replacing current data after an explicit confirmation.

### Phase 3

- **US-21 Booking.** As a member, I can book a chore ("🔖 I'll do it") so nobody
  does it before me; others see who booked it and are blocked. I can cancel my
  booking; completing the chore clears the booking automatically.
- **US-21b Chore frequency.** As an admin, I can say how often a chore repeats by picking a
  cadence rather than typing a day count, and I can limit a chore to certain seasons.
- **US-21c Board filters.** As a member, I can narrow the board to what's due now or to one
  frequency, and off-season chores stay out of my way without disappearing for good.
- **US-22 Booking timeout.** As an admin, I can configure how long a booking holds
  (1/2/3/4/6/8/12/24 hours, default 4). Expired bookings free the chore, and the
  board I already have open updates by itself when the hold lapses.
- **US-23 Rotating division.** As an admin, I can switch the home from
  *free-for-all* to *rotating*: every member gets one assigned chore per day
  ("⭐ Your turn"), rotating daily and deterministically. I can choose whether the
  assignment is **enforced** (members may only do their assigned chore) or a
  suggestion.
- **US-24 Interval chores.** As an admin, I can set a chore to repeat every N days
  (e.g. water plants every 7 days). Members see "🕒 due / in Nd" and can't complete
  it early.
- **US-25 Availability hours.** As an admin, I can limit a chore to times of day
  (e.g. dog walk 08–10 and 18–22), evaluated in each member's local time. Outside
  the window the card shows the hours and taps are blocked with a friendly message.
- **US-26 Chore credits.** As an admin, I can give a chore a credit value so that
  challenging chores earn 💎 credits when completed (and approved).
- **US-27 Spree bonuses.** As an admin, I can define spree tiers ("X days in a row →
  Y credits"); a member who completes chores on that many consecutive days earns the
  bonus once per streak.
- **US-28 Balances & redemption.** As a member, I see my credit balance on the
  leaderboard. As an admin, I can redeem credits for a member (with an optional
  note, e.g. "movie night"); redeeming reduces the balance and cannot exceed it.
- **US-29 Localization.** As a user, I get the UI in English, Finnish or Swedish —
  initially from my browser language, switchable at any time (persisted in a
  cookie). A new home's default chores are created in the creator's language.
- **US-30 Join links & sharing.** As a member, I can copy a ready-to-open join link
  or share it via the native share sheet; the shared text includes the home name,
  code and link. Opening the link preselects the Join tab with the code filled in.
- **US-31 PWA install.** As a family member, I can install FlashChores on my phone
  (Android install prompt; iPhone *Share → Add to Home Screen*) and get a branded
  icon, standalone display, themed splash screens, and an offline fallback page.
- **US-32 Privacy notice.** As a visitor, I can read a plain-language privacy page
  at `/privacy` (linked from the landing page) describing what little data the app
  stores and my rights.
- **US-33 Default chores.** As a new home, I start with 11 sensible starter chores
  (localized), including examples of an interval chore (water plants, every 7 days)
  and a time-windowed chore (take the dog out, 08–10 & 18–22).

### Phase 4

- **US-34 Stay signed in without a long session.** As a family member, my phone keeps
  working as me across session timeouts, server restarts and app relaunches, because the
  identity lives in the phone's local storage rather than in server memory. The server
  keeps sessions short (§4.1.2) so idle phones cost it nothing.
- **US-35 Sign back in after clearing browsing data.** As a member whose phone forgot
  me, I can enter the home code and type my nickname, and get my own record back — chores,
  credits, streaks and admin role intact — instead of joining again as a duplicate person.
  (The app never lists the members, so a home code alone can't reveal the family's names.)
  If the home requires it, an admin approves my request first; the waiting screen flips to
  the board the moment they do, live.
- **US-36 Rejoin gate (admin).** As an admin, I can choose whether signing back in needs
  my approval (default **on**, since the home code travels in join links). Pending
  requests appear at the top of the Admin tab and in its badge count, with Approve /
  Reject per request.
- **US-37 Admin recovery.** As an admin whose device forgot me, entering the **admin
  PIN** in the sign-back-in dialog gets me straight in without waiting for anyone —
  including when I'm the only admin. The PIN opens the gate but is not itself a
  promotion; my existing member record already carries my role.
- **US-38 Delete the home.** As an admin, I can permanently delete the whole home —
  members, chores, completions, credits, spree tiers, rejoin requests and settings — from
  a **Danger zone** at the bottom of the Admin tab. Because nothing can undo it, the
  dialog states exactly what will be lost, points me at the backup download first, and
  only proceeds once I've typed the home code. Every other family member still on the
  board is signed out live, with their stored device identity cleared. Other homes on the
  same server are untouched, and the freed code can be issued to a future home.
- **US-40 Confirm before completing.** As a family member, tapping a chore asks me to
  confirm before it is recorded, so a stray tap while scrolling doesn't count as done.
  As an admin I can turn this off for my home (`Home.confirmCompletion`, default **on**).
- **US-41 Undo my own chore.** As a member who confirmed by mistake, I can take the chore
  straight back — from the celebration dialog, or from a strip on the board for
  `ChoreService.UNDO_WINDOW` (10 minutes) afterwards, which survives dismissing the dialog.
  Only my own completion, and only inside that window; anything older is an admin
  correction so nobody can quietly rewrite last week's leaderboard.
- **US-42 Admin can unmark a chore.** As an admin, the **Recent chores** list on the Admin
  tab lets me remove any completion — any member, any age, approved or pending. This is the
  UI for the long-specified US-14, which previously had a service method and no way to
  reach it once a home had approval switched off.
- **US-39 Abandoned-home retention (operator).** As the operator of a public instance, I
  can have homes that were created and then abandoned *before anyone used them* removed
  automatically, so stray sign-ups don't accumulate. A home qualifies only when it has no
  chore history at all and at most one member. Two windows feed the same rule —
  `empty-home-hours` (fast anti-spam tier, 72h in prod config) and `abandoned-home-days`
  (the long upper bound). Off by default in code.
- **US-40 Inactive-home retention (operator).** As the operator, I can additionally have
  *any* home that nobody has opened or used for `inactive-home-days` days (30 in prod
  config) deleted entirely — members, chores and history included — so the database only
  holds families that actually use the app, as the user agreement states. Because a false
  positive here destroys irreplaceable history (a long trip, a lost phone), a home with
  any chore history is exported as a full JSON backup to `retention.export-dir` right
  before deletion — the operator's undo, restorable via the maintenance tool — and a home
  whose export cannot be written is kept, not deleted. Activity means opening the board,
  joining, or logging/reviewing a chore; background push traffic and opt-in reminders
  never count. Off by default in code.

### Phase 6

- **US-50 Arrange the board.** As an admin, I can hold a chore card on the board and drag it to
  where it belongs — into another group too — so the board reads in the order my family actually
  works through it, instead of the order the chores happened to be created in; the Admin tab's
  ↑/↓ stay as the keyboard path. Rearranging is purely cosmetic: it never changes who the
  rotation assigns what to (§4.5), and a home that never touches it keeps exactly the order it
  had.
- **US-55 See what a chore pays.** As a family member choosing what to do next, I can see a
  chore's 💎 value on its card, so a five-diamond chore stands out from a plain one before I tap.
- **US-56 My diamonds.** As a family member, my statistics show the 💎 I *earned* today, this
  week, this month and all time, with the last seven days as a trend — spending them is the
  admin's business and has its own card (US-58).
- **US-57 What each member did.** As an admin, Home stats show every member's own chores-by-type
  chart, folded one per member, so I can see for the whole family what each member sees for
  themselves without asking around.
- **US-58 What I redeemed.** As a family member who earns a lot and cashes it in often, my
  statistics show what I *redeemed* — earned, redeemed and what is left side by side, then each
  redemption with the reward the admin noted ("movie night") — so a balance near zero is not read
  as "never has any diamonds". As an admin, Home stats list the whole family's redemptions by
  name, newest first.
- **US-59 Arrange a list, and let reminders sort themselves.** As a family member, I can hold a
  line on a shared list and drag it to where it belongs, the way the admin arranges the board, so
  the shopping list reads in the order of the aisles. A line I set a reminder on sorts itself by
  when it is due instead: due today or already nagging, it leads the list; due next week or next
  month, it waits at the bottom — "call the plumber, next month" stops sitting between the milk
  and the bread.
- **US-52 See what I did today, this week, this month.** As a family member, my statistics open on
  a row of four counts — today, this week, this month, all time — and I can narrow the charts to
  any of them, so "am I keeping up?" is answerable at a glance instead of inferable from a 7-day
  bar chart. As someone who has used the app for a while, I also get **12-week and 12-month
  trends**, because a fortnight is too short to show whether a household is drifting.

- **US-54 Remind me about this one later.** As a family member who can't do a chore right now, I
  can tap ⏰ on its card and pick "in 1h / 2h / 4h / 8h / a day / a week", and get one push
  notification naming that chore when the time comes. One nudge per chore per person, replaced
  rather than stacked if I change my mind, and it goes away by itself the moment I do the chore —
  a notification ninety minutes later about something I already did is the one way this could be
  worse than nothing.

- **US-53 Pick a colour.** As a family member, I can change how FlashChores looks on *my* phone —
  green, pink, blue or grey — alongside the light/dark choice I already had, because a board that
  sits on the kitchen counter all day should be one somebody wants to look at. Per device:
  nothing about it reaches the home or the other members.

- **US-51 Group the chores.** As an admin, I can create chore groups — 🍳 Kitchen, 🧺 Laundry —
  and put chores in them, so a long board reads as sections instead of one wall of cards.
  Chores I do not group sit under "Other chores" at the bottom. Deleting a group keeps its
  chores and their history; it is a label, not a container.
- **US-60 A slow answer looks like loading, not like a broken app.** As a family member whose
  tap is taking long — typically the first tap after my phone spent a while in my pocket and the
  server let my session go — I see the same full-screen "Getting your chores…" card the app shows
  while it starts, instead of a 4px bar hidden under the camera island, so I know to wait rather
  than tap again or give up.
- **US-61 A child without a phone is still on the board.** As an admin, I can add a family
  member who has no phone of their own from the Members card — name them and accept the user
  agreement on their behalf — so they have a leaderboard chip, a place in the rotation, credits
  and statistics like everyone else. I log their chores for them (US-47), and the card shows
  "no phone yet" under their name until the day they get one and sign in with "I'm already a
  member" and the nickname I gave them.
- **US-62 The kitchen tablet.** As a family, we keep one device on the counter that shows the
  board for all of us: whose turn it is, who has booked what, who has a streak going. When one
  of us does a chore — the child without a phone included — we tap the card on it and tap our
  own face, and that is the confirmation: it counts for that person, with their locks checked
  and their celebration shown. An admin pairs the device once with the home code and the admin
  PIN (signing in as themselves would sign their own phone out), and can unpair it from the
  Admin tab. The device is never an admin and has no reminders of its own.

### Phase 5

- **US-43 Log help nobody made a card for.** As a family member who helped in a way the
  board doesn't cover ("carried the shopping in"), I can tap **🙋 Other help**, write what I
  did in a line, and send it for approval — instead of either tapping a chore that isn't
  what I did or getting nothing for it. It shows as waiting on my card until it's decided,
  and I can take it straight back inside my normal undo window if I mistyped it.
- **US-44 Accept or decline other help (admin).** As an admin, other help appears in its own
  section at the top of the Admin tab, in the member's own words and with who and when. I can
  **accept** it — which counts it for that member exactly like a chore, with a reward in 💎 I
  name myself, since there's no chore carrying a credit value — or **decline** it, which
  leaves it uncounted. It waits for me even in a home where chores need no approval: it's
  free text, so somebody has to read it.
- **US-45 Promote accepted help to a chore (admin).** As an admin, right after accepting I'm
  asked whether this should join the chore list. The name comes prefilled from what the member
  wrote (editable — a description of one evening makes a poor chore name), along with emoji,
  repeat interval and credits. Saying yes gives the family a card anyone can tap from then on;
  saying no leaves the board alone. Either way the accepted help keeps counting.
- **US-46 Switch other help off (admin).** As an admin who doesn't want free-text entries, I
  can turn the feature off for my home (`Home.allowOtherHelp`, default **on**); the card
  disappears from the board.
- **US-55 My own other help counts at once (admin).** As an admin who did something the board
  has no card for, I write it down like anyone else — but it counts immediately, with me as the
  reviewer, and I name its 💎 reward in the same dialog. Other help waits so that somebody reads
  the free text before it counts; when I wrote it, I have. This is US-47's reasoning applied to
  US-43: my logging it *is* the approval. A member's entry still waits exactly as before.

- **US-47 Log a chore for someone (admin).** As an admin, I can record a chore on another
  member's behalf — the child who has no phone of their own, or the one who did it and forgot
  to tap. I pick who and which chore in the Admin tab and it counts for them straight away:
  my logging it *is* the approval, and the history keeps my name as the reviewer. It goes in
  regardless of the booking, streak, interval, hours and rotation locks, because those steer
  who does what *next* and this is a statement about what already happened. If I get it wrong,
  the unmark list takes it back like any other completion.
- **US-48 Sessions that don't outlive the moment.** As a family, our phones hold no server
  session while nobody is using them: a member's session lasts 5 minutes of inactivity and an
  admin's 20, and expiry is invisible — the page returns itself to the board using the
  identity in local storage. As an admin I get the longer one because settings forms are read
  and filled in slowly.
- **US-49 No machine translation on top of ours.** As a user whose browser offers to
  translate pages, FlashChores declines: the app already speaks my language, and an automatic
  translation would rewrite home codes, member names and what people wrote about their own
  help.

### Phase 7

- **US-63 Trophies I keep.** As a member, reaching 5, 10, 25, 50, 100, 250, 500 and 1000
  approved chores each earns me a trophy with its own emoji and name — 🌱 Sprout, 🧹 Helper,
  🥉 Bronze, 🥈 Silver, 🏆 Gold, 🏅 Medal of honour, 🎓 Diploma, 🦄 Legend — unveiled with big
  confetti. A trophy earned while I wasn't looking (an admin approved or logged the chore) is
  unveiled the next time I open the board. My Stats shows my shelf: every trophy, the earned
  ones with the day I reached them, the locked ones with what they pay, and a progress bar to
  the next. My best trophy sits on my leaderboard chip, so the family sees it too.
- **US-64 Trophy rewards (admin).** As an admin, I can attach a 💎 reward to any trophy in the
  Rewards section. Each member who reaches that trophy from then on is paid it once; members
  already past it when I set it are not paid, so a number typed in does not empty the bank.
- **US-65 Almost there.** As a member, when my next trophy is three chores or fewer away, the
  celebration after a chore says so — "Just 2 more to 🥈 Silver (+20 💎)!" — instead of the
  board growing another progress element.

## 4. Functional specification

### 4.1 Login & identity
- Passwordless. Session stores `memberId` + `homeCode` (`SessionContext`), plus the
  member's browser **time zone** (fetched once per session for availability hours).
- **Device identity** (`DeviceIdentity`): each sign-in issues a fresh 128-bit **device
  secret** and writes `memberId|homeCode|secret` to the browser's local storage under
  `flashchores.identity`, re-stamped on every visit to the board. Only the SHA-256 of the
  secret is held server-side (`Member.deviceSecretHash`). The landing page reads the value
  on attach and signs the session in only when the member and home still exist **and** the
  secret matches the stored hash (constant-time compare). A member id is a small, guessable
  number, so it is the secret — not the id — that proves the device is that member: a
  hand-edited `localStorage` value cannot impersonate anyone. Issuing a new secret on each
  sign-in also cuts any previous device off, so an approved rejoin revokes a lost phone. A
  stale or unverifiable entry is cleared and the user left on the landing page **with the
  reason** (`DeviceIdentity.RestoreFailure`, shown in the card's `.auth-notice` with the Join
  tab in front, where "sign me back in" is): `SECRET_REPLACED` — another browser signed in as
  this member since, the usual cause when one phone holds the app both installed and in
  Safari or a chat's in-app browser, each with its own local storage, so they revoke each
  other in turns; `GONE` — the member is no longer in that home; `HOME_GONE`; `UNREADABLE` —
  the pre-secret two-part format or a tampered value, which until now read as "never joined".
  Nothing stored is the ordinary first visit and is not a failure. Each rejection leaves one
  `INFO` line (`LandingView`): the kind and id, the reason, how the browser is running
  (`DeviceIdentity.probeContext`: installed `standalone` or plain `browser`, and whether local
  storage is writable at all) and a short label from the User-Agent (device, OS version,
  browser and major version, `webview` for an iOS page outside Safari proper) — never a
  name or a home code; §4.15's notice names this line. The browser's answer to the probe is
  logged only if it is one of the four the probe can give (`DeviceIdentity.knownContext`,
  otherwise `unknown`), since the browser writes it, and a visitor leaves at most 5 such
  lines an hour (`MAX_REJECTION_LINES_PER_HOUR`): a real phone is refused once and then has
  nothing stored, while a page reloaded in a loop must not fill the log. While the browser is being asked, a full-screen
  overlay covers the landing card, so a lookup that never answers leaves a working page.
- **Leave**, self-removal and backup restore all clear the stored identity.
- **A member without a device** (`ChoreService.addMemberWithoutDevice(homeCode, name,
  adminId)`): an admin adds a member who has no phone from the Admin tab's Members card. The
  member is an ordinary row with `deviceSecretHash == null` (`Member.hasDevice()` is false, and
  the card reads "no phone yet"), so nothing can sign in as them until a phone goes through
  "I'm already a member" by nickname (§4.1.1), which is why the service refuses a nickname
  already in use in the home (case-insensitive, as that match is) and a blank one. The service
  re-checks that the caller is an admin of that home. The dialog carries the same user-agreement
  checkbox as the create and join forms — the admin accepts it on the child's behalf — so
  `termsAcceptedAt` is stamped at creation like every other member's. Their chores are logged
  through §4.3.3.

#### 4.1.1 Joining and signing back in (admin-gated)
Both doors into a home are gated by an admin by default, so knowing the home code is never
enough on its own to get onto the board or into a member's identity. Both raise a
`RejoinRequest` (`PENDING` / `APPROVED` / `REJECTED`) carrying a 128-bit **device token**
the browser stores under `flashchores.rejoin`; both surface at the top of the Admin tab
(and in its badge) with Approve / Reject.

**First-time join** — `requestJoin(code, name)`, gated by `Home.approveJoin` (default **on**):
- Gate on → `PENDING`: **no member is created yet**. The joiner waits, and the member is
  brought into being only when an admin approves — so a guessed or leaked code cannot plant
  anyone on the board by itself.
- Gate off → the member is created and signed in immediately (for homes that opt out).

**Signing back in** — clearing browsing data is the one thing local storage doesn't
survive, so the Join tab offers "I'm already a member — sign me back in":
1. The member enters the home code and **types the nickname** they use in this home. The
   app does *not* list the members — a home code alone must never reveal the family's
   names; someone who forgot their nickname asks their home admin, who can see it.
2. A matching nickname calls `requestRejoin(code, memberId, pin)`, which returns:
   - `SIGNED_IN` — the supplied PIN matched the home's admin PIN, or `Home.approveRejoin`
     is off. The device signs in immediately.
   - `PENDING` — a `RejoinRequest` + **device token**; the browser shows a waiting screen.
   - `WRONG_PIN` / `UNKNOWN` — rejected with a message; nothing is recorded.

Common to both:
3. An admin's decision bumps the home revision, so the waiting device picks it up over push
   and navigates straight to the board — no polling or reload.
4. A device that reloaded or closed in the meantime re-checks its stored token on the next
   visit, so an approval granted while it was away still lands. Approved requests are
   **consumed** on sign-in so a token can't be replayed; re-requesting for the same member
   drops any older pending request, so only the newest device can be let in. Abandoned
   requests are swept after 48h.
- The PIN is a *bypass*, not a promotion: the member keeps whatever role their record has,
  and "Admin?" in the header remains the way to claim admin. Both PIN checks (claim-admin
  and the rejoin bypass) lock a home's PIN for 15 minutes after 5 wrong tries.
- Requests are deleted when the member is removed or the home is restored from a backup.
- **Home code**: 7 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no I/O/0/1),
  ≈ 34 billion combinations — codes can't realistically be enumerated.
- **Admin PIN**: 4-digit numeric, generated on home creation and shown once
  prominently (also visible to admins in Settings). Stored on `Home.adminPin`.
- **Claim admin**: an "Admin?" action asks for the PIN; a correct PIN sets the
  current session's member `admin = true`. The PIN is the admin credential, so
  anyone with it can be admin (by design, like sharing a household master code).
- **Join links**: `/?join=CODE` preselects the Join tab and prefills the code.

#### 4.1.2 Session lifetime
Sessions are short because nothing is lost when one ends: the identity is in local storage,
and the next interaction signs the phone straight back in.

- **5 minutes for a member, 20 for an admin** (`SessionContext.MEMBER_TIMEOUT_SECONDS` =
  300s / `ADMIN_TIMEOUT_SECONDS` = 1200s, applied per session with
  `WrappedSession.setMaxInactiveInterval`). These two constants are the single source of
  truth; this paragraph and the note in `application.properties` quote them, so a change
  there is a change here.
  Members tap and pocket the phone; admins read and fill in forms — settings, PINs, reward
  tiers — which produce no requests while being read, and where being bounced mid-edit costs
  real work. The lifetime is re-applied on every board render, so a promotion or demotion
  moves that device onto the other one without signing out.
- `server.servlet.session.timeout=3m` covers visitors who haven't signed in yet.
- **The session cookie** is `Secure` and `HttpOnly` (Tomcat, behind the TLS proxy's
  `X-Forwarded-Proto`, honoured through `server.forward-headers-strategy=native`) and
  `SameSite=Lax` (`server.servlet.session.cookie.same-site`, set in the app because nothing
  upstream sets it). Lax is what the app needs: same-site UIDL, push and heartbeat traffic, a
  join link opened from a chat still carrying the cookie, and no cookie on a cross-site POST.
  The runbook's verify step checks all three flags on the host.
- **`vaadin.closeIdleSessions=true`** is what makes the numbers mean anything. Without it,
  Vaadin's heartbeats keep an open tab alive indefinitely and the timeout only applies to
  closed tabs. With it, the clock runs from `VaadinSession.lastRequestTimestamp`, which only
  `ServerRpcHandler.handleRpc` updates — i.e. from the last real interaction. Heartbeats and
  push traffic don't count as the user being present, so a board left open on a counter does
  expire.
- **`vaadin.heartbeatInterval=60`**, below the shortest timeout: expiry is only noticed when
  some request runs `cleanupSession`, so the default 300 s heartbeat would let a 3-minute
  session linger for five.
- **Expiry is silent** (`SessionExpiryInitListener`): the "Session Expired — take note of any
  unsaved data" dialog is the right message for a bank and the wrong one for a chore board,
  so it is disabled and `sessionExpiredURL` points at `/`. The browser reloads the landing
  page, which restores the stored identity and returns to the board. A device waiting on a
  rejoin approval re-reads its token the same way and goes back to waiting.
- **A long wait is shown as loading** (`LoadingOverlayInitListener`, `loading-overlay.js`).
  Vaadin's loading indicator is a 4px bar along the top of the viewport — under a phone's camera
  island — so a tap that takes seconds looks like a tap that did nothing. From the indicator's
  second stage on, **1.5 s** into a request (`LoadingOverlayInitListener.OVERLAY_DELAY_MS`, after
  the bar's own first stage), the page is covered by the landing page's restore card: built
  client-side on every page from the same CSS classes, raised as a manual **popover** — the
  browser's top layer, so it covers a dialog left open too — while the bar is in its second or
  third stage, and given its text (`app.loading`, the key the landing card reads) by the server
  in the UI's language. The first tap on an expired session therefore reads as the
  app reloading, which is what it is, and a wait that ends in a reload runs straight into the
  landing page's own copy of the card. An ordinary redraw — tens of milliseconds plus a round
  trip — never reaches it. While the connection itself is down the card yields to Vaadin's
  "Connection lost" message, and the first-stage bar is moved below `env(safe-area-inset-top)`.

#### 4.1.3 The shared family device
A member is one phone: signing a member in on a second device issues a new secret and cuts
the first one off (§4.1). So a device the whole family uses — the tablet in the kitchen — is
its own kind of identity, a **`SharedDevice`**, with a home, a name and a secret hash like a
member's but no member of its own.

- **Pairing** (`SharedDeviceService.pair(code, pin, name, defaultName)`), from "Set up a shared
  family device" under the landing page's Join form: the home code and the **admin PIN** (with
  no code typed yet, the link asks for one — "Enter your home code first, then pair the
  device", `landing.device.needCode` — rather than borrowing sign-back-in's words). The PIN is
  the admin credential, so pairing is the admin's consent and there is no separate gate or
  setting; a wrong PIN counts against the same five-tries, fifteen-minute lock as claiming
  admin and the rejoin bypass, and the landing page allows 10 pairing attempts per hour per
  connection (`LandingView.MAX_PAIRINGS_PER_HOUR`). A home may hold at most
  `SharedDevice.MAX_PER_HOME` = 5 devices. The browser stores `deviceId|homeCode|secret` under
  `flashchores.device` (never alongside a member identity — writing one clears the other), the
  landing page restores it the way it restores a member, and the server keeps only the SHA-256.
  The device's idle lifetime is a member's (§4.1.2); expiry reloads back onto the same board.
- **Unpairing**, from the Admin tab's **Shared devices** card (name, paired when, last seen):
  `unpair(deviceId, adminId)` re-checks the caller is an admin of the device's home, deletes the
  row — so the stored secret stops working at once — and bumps the home, which shows an open
  board on that device out to the landing page, identity dropped. A device is not part of a
  backup (it is a fact about a browser, not about the family) and survives a restore; deleting
  the home deletes its devices.
- **The board belongs to nobody.** `taskViews(homeCode, null)` evaluates only the locks about
  *time* — season, interval, availability hours (in the device's own time zone) — and leaves
  the locks about *who* (rotation, booking, streak) to the tap. The cards still carry the
  badges those would have produced for someone: "Sam today", "🔖 Sam", "Sam ×3 🔒", so the
  counter-top screen shows the family's state rather than one member's. No daily-target
  ring (it is one person's), no ⏰ chips, no reminders, no "Admin?", no Leave; the header
  says "📺 Kitchen tablet · shared device" as dark text on a near-white pill (`.device-badge`),
  since white on the header gradient measures 2.6–2.9:1 (UX review row B) and this is the one
  line saying whose board it is.
- **The pick is the confirmation.** Tapping a card opens "Who did it?" with the family as
  avatar chips; tapping a chip is the "yes". The dialog is named by its question and chore
  (`aria-label`, shared with the member's "Did you do it?" through one `askDialog`), each chip's
  accessible name is the member's name once (the face is `aria-hidden`), and a long name ends
  in an ellipsis inside the chip rather than pushing the dialog sideways at 360px. `SharedDeviceService.complete(deviceId, taskId,
  memberId, zone)` refuses unless the device, the member and the chore are in one home, then
  runs `ChoreService.complete` for that member, so every lock in §4.3 applies to *them*: a
  refusal names them ("Sam has done … 3× in a row", `blocked.named.*`), and a success is
  their celebration, feedback and credits. The home's confirm-completion setting does not
  apply here — there is nobody to skip the question for. The chip ids are captured in
  server-side lambdas, never read back from the client.
- **Booking** asks who ("Who will do it?") through `book(deviceId, taskId, memberId)`; a
  booking on the device's board is anyone's to clear (`cancelBooking(deviceId, taskId)`), since
  the device is the household's and a booking left behind by someone who went out would
  otherwise block the kitchen. **Other help** asks who too (`logOtherHelp`) and waits for an
  admin exactly as that member's own entry would.
- **Undo**: the device has no member to ask `undoableCompletion` for, so the board remembers
  the last completion it logged (`ChoresPanel.FromDevice`) and shows "Sam just did … · Undo"
  for the usual `UNDO_WINDOW`, taking it back through `undoCompletion(id, memberId)` for that
  member. The row is looked up through the same query, so one an admin has since unmarked
  is gone here too.
- **Lists**: lines added or ticked from the device carry no author (`createdByMemberId` /
  `doneByMemberId` null; a ticked line reads "shared device"). Every list write takes an
  **author** (`Author`: a member id or a shared-device id, exactly one), and `ListItemService`
  reads either back from the database and refuses it unless it belongs to the home of the row
  it touches: a device of another home, or one unpaired a moment ago while the home keeps
  another, writes nothing. The session proves the device's secret; which home it is in is the
  database's to say. The admin-only list settings still need an admin. No list reminders can
  be set or answered from it.
- **Stats**: "Whose statistics?" picks one member and shows their personal statistics — the
  one place a member without a phone of their own sees their numbers.

### 4.2 Chores (CRUD — admin)
- Create: name (required) + emoji (optional, default ✅) + **repeat every N days**
  (0 = always available) + **reward credits** (0 = none) + **availability hours**
  (optional; e.g. `08:00-10:00, 18:00-22:00`, flexible input like `8-10` accepted
  and normalized; invalid input is rejected with an error).
- Update: all of the above.
- Delete: removes the chore and cascades to its completions. Confirmation required.
- The "＋ Add chore" affordance and edit/delete controls are visible only to admins.
- New homes are seeded with 11 default chores, localized to the creator's language
  (see US-33).

### 4.2b Chore groups and board order
- **Groups** (`ChoreGroup`: name, emoji, position) are the board's section headings — 🍳 Kitchen,
  🧺 Laundry. A chore carries a nullable `groupId`; chores without one form a trailing
  "Other chores" section. Admin-only, created and arranged in their own Admin card above Chores.
- **A group is a label, not a container.** Deleting one keeps every chore in it, and their whole
  history — they simply drop to the ungrouped section. The confirmation says so.
- **Order** is `ChoreTask.sortOrder`, dense 0..n-1 within each group, arranged with ↑/↓ in the
  Admin tab or by **holding and dragging a card on the board**. Arrows are disabled at the ends
  of a bucket, not hidden, so rows do not reflow as things move; they stay as the keyboard path.
- **Hold-and-drag** (`board-drag.js`, admins only — a configuration of the shared
  `hold-drag.js`, which `list-drag.js` reuses for shared lists, §4.4e): hold a chore card still
  for 450 ms and it lifts (a swell at 60 % of the hold says the hold is being noticed; a short vibration where the
  device has one); drag it and the other cards reflow around the slot, like apps on a phone's
  home screen; let go and it stays. Dropping it in another section moves it into that group.
  Not HTML5 drag-and-drop, which does not fire on mobile touch: pointer events, with a
  non-passive `touchmove` cancelled only while a card is lifted, so the board still scrolls under
  a finger that starts on a card (`touch-action: pan-y`) and a move within the hold is a scroll,
  not a lift. The DOM is moved only for the live preview; the drop dispatches `board-place` on
  `.board-sections` with the dragged id, the target grid's group and the **next chore card's id**
  (blank = the section's end). A neighbour rather than an index, because a filtered board shows a
  subset of each section and "slot 3" would mean a different place on every lens. The server
  re-checks that the sender is an admin — the `draggable` class is a hint to the browser, not an
  authorisation — and `ChoreService.placeChore` removes the chore from its old bucket, inserts it
  before the neighbour (the neighbour's section wins over the grid's, should they differ), writes
  the bucket densely and bumps the home; a drop that changes nothing, one before itself, or one
  of or beside another home's card writes nothing and the member's board is redrawn so the
  preview snaps back. The dragged card is checked against the *session's* home, not the card's:
  every id in the drop comes from the browser, and an admin of one home must not be able to
  arrange another's board by forging them. The 🙋 and ＋ tiles carry no id, so they can be neither lifted nor landed on.
  Rotation is unaffected (`tasksInRotationOrder`), as for every other arrangement.
- **The upgrade is invisible.** `sortOrder` lands on existing rows at 0, so the board query
  tie-breaks on `createdAt` and a home that never reorders anything keeps exactly the order it
  had. Renumbering is lazy and per-bucket — the first move in a group is what gives it real
  positions. Deleting or adding a chore deliberately leaves a gap; a gap changes no visible order.
- **Two orderings, named apart.** `ChoreService.tasksOf` is board order (groups, then position,
  then creation time) and feeds every renderer plus the statistics bars, so the charts keep
  matching the board. `tasksInRotationOrder` is creation order and feeds the rotation alone
  (§4.5). A dangling `groupId` — a deleted group, a hand-edited backup — reads as ungrouped
  rather than making the chore vanish.
- **On the board**, a heading appears only once at least one group section is being rendered, so
  a home with no groups renders exactly the single unheaded grid it always did. Empty groups show
  no heading; a filter narrows *within* sections. Groups are structure, filter chips are lenses —
  groups deliberately get no chips of their own (§4.4c).

### 4.3 Completing a chore — locks & fairness
Checks happen in this order; the first failure blocks the tap with a localized
message and shows a matching badge on the card (`LockReason`):

1. **Interval** (`NOT_DUE`): an interval chore is due when
   `today ≥ lastDone + N days` (server date). Card badge: "🕒 due / in Nd".
2. **Availability hours** (`OUTSIDE_HOURS`): the member's local wall-clock time
   must fall in one of the chore's windows (end-exclusive). Card badge:
   "🕒 8–10, 18–22". Unparseable stored windows fail *open* (never lock a chore
   permanently).
3. **Rotation** (`NOT_ASSIGNED`, rotating + enforced only): the chore must be the
   member's assigned chore today. Rotation ignores booking and the streak rule.
4. **Booking** (`BOOKED`): blocked if someone else holds a live booking.
5. **Fairness** (`STREAK`): for the chore's non-`REJECTED` completions newest
   first, count the leading run by one member. If that member's run ≥
   `Home.maxInARow` (default 3; 0 = rule off, so this step never blocks), they are
   locked out until a different member does it. Pending completions count toward
   the run (so approval can't be gamed). The blocked message names the actual run,
   which matters when an admin lowers the limit under a longer existing run.

- **Approval-aware creation**:
  - `requireApproval == false` → completion saved `APPROVED`; celebrations and
    credit awards fire immediately.
  - `requireApproval == true` → completion saved `PENDING`; the member sees a
    "sent for approval ⏳" message (no confetti yet). Counts, celebrations and
    credits are deferred until an admin approves.
- Completing a chore clears any booking on it.
- **Feedback**: the completion celebration offers three buttons
  (😖 Hate / 🙂 OK / 😍 Love) that set `Completion.feedback`. Optional.

### 4.4 Booking ("I'll do it")
- One live booking per chore. Booking fails if someone else holds a live one.
- A booking expires `Home.bookingTimeoutHours` after it was made
  (1/2/3/4/6/8/12/24 h, default 4); expiry frees the chore silently.
- Expiry works two ways. `ChoreService.effectiveBookerId` derives it on every read, so
  a lapsed hold stops blocking anyone the instant it lapses. A scheduled sweep,
  `ChoreService.releaseExpiredBookings` (every `homechores.booking.sweep-ms`, default
  60 s), then writes the release back: it nulls `bookedByMemberId`/`bookedAt` and bumps
  `HomeState` for the home. Without the sweep, an already-open board would keep showing
  "🔖 <name>" and a locked card until some unrelated change redrew it, because boards
  only re-render on a bump and a hold quietly lapsing is not a mutation.
- The booker can cancel; cards show "🔖 You" / "🔖 <name>". Available only in
  free-for-all division and only for currently-due chores.

### 4.4b Chore frequency and seasons
- **Frequency** is edited as a preset (Anytime / Every day / Every week / Every 2 weeks /
  Every month / Every 3 months / Once a year / Custom…) which writes canonical values into
  `ChoreTask.intervalDays` — 0/1/7/14/30/90/365. That int stays the single source of truth
  for due dates; a hand-typed or legacy interval round-trips through "Custom…".
  See `FrequencyField`.
- **`Cadence.of(intervalDays)`** buckets any interval into ANYTIME / DAILY / WEEKLY / MONTHLY /
  MULTI_MONTH / YEARLY, cutting at the geometric midpoints between the canonical values
  (2 | 3, 14 | 15, 51 | 52, 180 | 181). Purely a lens for the board filter; nothing is stored.
- **Seasons**: `ChoreTask.seasons` holds a canonical `"SPRING[,SUMMER…]"` (see `Seasons`),
  null/blank = all year round; all four collapses to null. Where `availableWindows` says time of
  day, this says time of year. Northern-hemisphere meteorological months, fixed — the app has
  no hemisphere setting.
- Out of season blocks completion with `LockReason.OUT_OF_SEASON`, checked **before** the
  interval in both `computeLock` and `complete` so the badge reads "❄️ only" rather than
  "🕒 in 200d". Evaluated on the server date, not the member's zone: hours are
  member-local, but a season is a month-level notion and travelling must not change it.
  Unparseable data fails open, as windows do.

### 4.4c Board filters
- A single-select chip row above the card grid: All · Today · the non-empty cadence
  buckets · Off-season. Chips are lenses, not a partition — an off-season chore appears
  under both `Off-season` and its own cadence chip — so they carry no counts.
- **`Today` is the default lens**: the board opens on "what is on today's list" — anytime
  chores plus interval chores whose interval has elapsed (a weekly chore done three days
  ago is absent), in season, ignoring time of day (an out-of-hours dog walk is still
  today's chore, shown locked), booking and streak state. The predicate is
  `lockReason ∉ {NOT_DUE, OUT_OF_SEASON}`, which is deliberately clock-independent.
- Empty buckets get no chip; a lone cadence bucket is dropped (it would just be `All` renamed);
  `Today` is dropped when it would select everything — the default then falls back to `All`,
  which renders the identical board; and the row hides entirely unless it offers at least
  one real alternative to `All`. A fresh home therefore still looks exactly as before.
- The selection lives in a field on `ChoresPanel`, so it survives every `HomeState` rebuild but
  resets on navigation (back to `Today`). Clicking a chip re-renders that board only — it never
  bumps `HomeState`, because one member's view preference is not the family's business.
- **The board never renders empty because of a filter.** If the chosen bucket disappears or its
  result is empty, the lens resets to `All`. Under rotating division the member's own assigned
  card is always included, whatever the lens, so the board cannot become a dead end.
- Filtering happens in `ChoresPanel` only, and so does sectioning by group (§4.2b). The *order*
  now comes from the service; the panel decides visibility and grouping. `rotationAssignedChoreId`
  indexes the chore list by position, so a filtered or reordered list would silently reassign
  everyone's daily chore — which is why rotation reads `tasksInRotationOrder`, never `tasksOf`.
- "Other help" and "Add chore" are not chores, so they show only under `All` and the
  default `Today` — the opening board must not hide them.

### 4.4e Shared lists (groceries, to-dos and dinners)
- A **Lists** tab between Chores and Stats, for everyone. Three sub-tabs, **Groceries**,
  **To-do** and **Dinner**, backed by one table (`ListItem`: `homeCode`, `kind`
  GROCERY/TODO/DINNER, `text` ≤ 160, `createdAt`, `createdByMemberId`, `doneAt`,
  `doneByMemberId`, nullable `day` for dinners, `sortOrder`).
- **Not chores.** Ticking a line creates no `Completion`, earns no credits, starts no streak
  and needs no approval — whoever is at the store ticks the milk off. That is why the feature
  lives in its own `ListItemService` rather than in `ChoreService`.
- Anyone in the home may add, tick, untick, delete or **Clear done**; a shared list is shared.
  A member of another home is refused (`IllegalArgumentException`), the same line the snooze
  service holds.
- Quick entry: a text box with Enter-to-add at the top of the list; the half-typed draft is a
  panel field, so another phone's edit re-rendering the panel does not lose it. Blank lines
  add nothing; long lines are clipped (`InputLimits.LIST_ITEM`).
- **Order.** Open lines are shown in the family's own order, `ListItem.sortOrder` ascending
  with `createdAt` as the tie-break — so a list nobody has arranged (every line at 0) reads in
  writing order, exactly as before. **Hold-and-drag** (`list-drag.js`, the same gesture as the
  board's, §4.2b, from the shared `hold-drag.js`) rearranges it, for *everyone* in the home: a
  shared list is shared, and whoever is at the store arranges it. The drop dispatches
  `list-place` on `.shared-list` with the dragged line's id and the next arrangeable line's id
  (blank = the end); `ListItemService.placeItem` checks both ids against the *session's* home —
  the ids come from the browser — refuses a line that is gone, ticked, a dinner slot, another
  home's, or a neighbour on another list, renumbers the list's open lines densely 0..n-1 and bumps
  the home; a drop that changes nothing writes nothing and the panel redraws so the preview snaps
  back. A new line gets the position after the last one, so it still lands at the bottom of an
  arranged list; ticking and unticking keep a line's number. The upgrade is invisible, as the
  board's was: the column lands on existing rows at 0 with a default (`ddl-auto=update` on a table
  that already has every family's lines in it), and the first drag on a list is what gives it
  real positions.
- **Keyboard path.** A hold is a thumb's gesture, so every plain line also carries ↑/↓
  (`list-move-btn`, labelled "Move up" / "Move down"), disabled at the ends of the plain run as
  the board's arrows are at the ends of a bucket, and going through the same `placeItem` with the
  neighbour a drop there would name. Below 480px, where the row has no room for two more 40px
  targets beside the bell and the trash, they are clipped out of sight rather than removed —
  still in the tab order and the accessibility tree — and reappear while focused.
- **Reminders sort themselves** (`ListItemService.displayOrder`): a line with a reminder is not
  the family's to arrange — it is pinned by its due time. Lines whose reminder is **due** (fired
  and unanswered, or due by the end of today in the viewer's zone, the zone the badge is
  formatted in) lead the list, soonest first; then the plain lines in the family's order; then
  lines whose reminder is further off, soonest first. "Tomorrow 9:00" waits below the plain lines
  until midnight and then leads; "next month" sits at the bottom until its day comes. A pinned
  line carries no `data-item-id`, so it can be neither lifted nor landed on, and the drag's run of
  landable lines is bounded by the pinned ones above and below. Underneath, the line keeps its own
  `sortOrder`, so once the reminder is answered or cancelled it drops back into the family's order
  where it was. While a line is lifted, its slot is a whole dashed frame drawn inside the row
  (2px, `--lumo-contrast-50pct`, about 3:1 against the page in both schemes) with the words faded
  under it: no line changes height, and no neighbour covers an edge of the frame.
  The swipe (`list-swipe.js`) and the hold coexist on the same row: a finger that
  moves within the 450 ms hold swipes or scrolls and never lifts, and once a line is lifted the
  swipe script stands down for that gesture.
- Ticked lines sink to a **Done (n)** section, struck through, with who ticked them and when.
  Ticking an already ticked line keeps the original ticker (two phones racing must not flip
  it). Unticking clears both stamps.
- **24-hour retention**, applied twice on purpose: the read path only returns done lines with
  `doneAt` inside the window, so they age out of every board on its next render with no
  write; an hourly sweep (`homechores.list.purge-ms`) reclaims the rows and never bumps
  `HomeState`. Purging on read was rejected because the read runs inside every open device's
  render effect.
- Every edit bumps `HomeState` (after commit, like everything else) and `touchHome`s, so a
  family that only uses the list is still "active" for retention.
- The sub-tab is view-local (a panel field, like the stats lens) and never bumps `HomeState`.
- **Dinner** is a different shape: a sliding window of eight days — today plus a full week, so
  planning next week on a Sunday still reaches next Sunday — from today (member's zone),
  one free-text slot per day, rows labelled with the localized full weekday and short date,
  today highlighted. A slot is set, changed or cleared inline (Enter or the ✓ button), never
  ticked; `createdAt`/`createdByMemberId` on a dinner row mean "last set at / by", shown under
  the slot. `ListItemService.setDinner` is an upsert: blank clears, an unchanged save does not
  bump the home, two phones on the same day — last write wins. A day that has passed drops off
  the view on the next render; its row is reclaimed by the hourly sweep
  `DINNER_RETENTION_DAYS` (7) after its day, using the server date, so a backup still holds last
  week's meals and no visible slot can be purged whatever the member's zone.
- Backed up and restored with the home (`listItems` in the JSON, member ids remapped; a file
  from before the lists restores with empty lists) and wiped by `deleteHome`.

### 4.4d Done today
- A collapsible home-wide list ("Done today (n)") directly under the daily strip: who did
  what, how long ago, feedback emoji. Same server-date "today" as the daily ring.
- `APPROVED` and `PENDING` rows both show (pending marked ⏳ — in a require-approval home
  the list would otherwise stay empty all day); `REJECTED` excluded. Newest first.
- Collapsed by default; the open state is a `ChoresPanel` field (survives rebuilds via the
  `isFromClient` listener trick, resets per navigation). Absent entirely on a blank day.

### 4.5 Rotating division
- `Home.divisionStyle` ∈ {`DEFAULT`, `ROTATING`}; `Home.rotationEnforced`
  (default true) applies in rotating mode.
- Assignment: with members ordered by join time and chores by **creation** time
  (`ChoreService.tasksInRotationOrder` — deliberately not the board order, so that arranging
  the board in the Admin tab is a layout preference and never reassigns anyone's day),
  member *m* is assigned chore `(m + epochDay) mod choreCount` — one chore per
  member per day, rotating daily, no server state.
- Cards show "⭐ Your turn" (own) or "<name> today" (others). Enforced mode blocks
  other chores (`NOT_ASSIGNED`); suggested mode merely highlights.

### 4.6 Counting, trophies, leaderboard
- A member's **count** = number of their `APPROVED` completions.
- **Trophies** (`Trophy`): a fixed ladder of eight rungs — 🌱 Sprout 5, 🧹 Helper 10, 🥉 Bronze
  25, 🥈 Silver 50, 🏆 Gold 100, 🏅 Medal of honour 250, 🎓 Diploma 500, 🦄 Legend 1000. The emoji
  avoid every glyph that already means something (👑 admin, 🥇 chore master, 💎, 🔥, 💪, 🌟, ⏳,
  🙋). Not admin-editable: the admin's lever is the reward (§4.7).
  - **Derived, never stored.** A member owns every rung their all-time count (`completionCount`,
    chores plus accepted other help) has reached. Undoing or deleting the completion that
    reached a rung takes the trophy back, as it takes back that completion's credits; members
    who were past a rung on the day trophies shipped owned it that day. The badge counter
    reset never touches trophies.
  - **Reached** = the count after a completion becomes `APPROVED` lands exactly on a rung
    (`CompleteOutcome.trophy`). Pending completions reach nothing.
  - **Unlock dialog**: the trophy's emoji (a pop and a short golden glow, both off under
    `prefers-reduced-motion`), "New trophy: Silver!", "50 chores done, Emma!", the reward line
    "+20 💎 reward" when the rung pays, big confetti, the feedback row, **See my trophies**
    (straight to the shelf) and Undo.
  - **Never missed** (`Member.trophiesSeen`, the highest rung the member has been shown, DDL
    default 0): the member's own tap marks its trophy seen; an admin's approval or logging does
    not. Whenever a member's board is drawn (`HomeView`) and they own a rung above the marker,
    one dialog shows it — "3 new trophies!" with their names when there are several — and the
    marker moves up before it opens, so a redraw cannot show it twice. Not on the shared device.
    With the default 0, every member's first visit after the upgrade presents the trophies they
    already own, once.
  - **Almost there**: when the next rung is `Celebrations.TROPHY_NEAR` = **3** or fewer chores
    away, the ordinary celebration dialog adds "Just 2 more to 🥈 Silver (+20 💎)!" (the
    reward only when set).
  - **On the leaderboard chip**: the member's best trophy as a small badge on the avatar's
    corner — no extra width, so compact chips still fit at 360px — with `role="img"` and the
    label "Trophy: Helper", which the compact chip's accessible name carries too. Every
    member's all-time count comes from **one** grouped query
    (`CompletionRepository.approvedCountsByMember`), not a count per member per redraw.
  - Confetti is skipped entirely under `prefers-reduced-motion` (`confetti.js`); the dialog
    still says the same thing.
- **new-chore** achievement = first `APPROVED` completion of that chore by that member.
- Leaderboard shows each member with their **badge count** and 💎 credit balance
  (when > 0); admins are badged 👑. Ordered by badge count, highest first, ties in join order
  (stable sort). The chips wrap to as many lines as the family needs: a single horizontally
  scrollable row was tried and reverted — the power users wanted every member visible at once.
- **Badge counter reset** (`Home.counterReset`: NEVER / WEEKLY / MONTHLY / YEARLY, default
  MONTHLY; `Home.counterResetAt`: the admin's last "Reset counters now"). The badge counts
  `APPROVED` completions done since the later of the current period's start (server zone, weeks
  from Monday like the stats) and the manual reset (`ChoreService.badgeCount`). Only that
  number: `completionCount`, trophies, credits, the chore master and every statistic stay
  all-time. "Reset counters now" asks first (the whole family's badges go to zero at once),
  stamps `counterResetAt` and bumps the home. A manual reset outlives a period boundary only
  until the next period starts. The column carries a DDL default, so existing homes adopt
  monthly on upgrade. Both fields travel through backup; older files restore as MONTHLY with
  no reset stamp.
- **Avatars**: each member may pick an animal avatar from a fixed CC0 set (Kenney
  "Animal Pack Redux", 30 round faces under `META-INF/resources/avatars/`, id whitelisted
  in `Avatars`). Rendered by the shared `MemberAvatar` dot (color ring + image, or
  initials when unset) on the leaderboard and admin member list. Picker opens from your
  own leaderboard chip, or from the admin rename dialog (for device-less kids). The id
  round-trips backups, whitelisted on restore like colors.
- **Chore master of last week**: the member with the most `APPROVED` completions in the
  previous ISO week (Mon–Sun, server zone) is badged 🥇 in the board header (👑 = admin,
  so a different glyph). Ties go to the earliest-joined member; hidden for solo homes and
  blank weeks.
- **Escalating daily celebrations**: the member's 2nd approved chore of the day gets a 💪
  dialog, the 3rd+ a 🔥 one with double-burst side-cannon confetti; precedence stays
  pending > trophy > new-chore > daily tier. Pending completions keep the neutral ⏳
  dialog — praise waits until the chore actually counts.

### 4.7 Credits & rewards
- **Earning**: when a completion becomes `APPROVED`:
  - the chore's `creditValue` (if > 0) is credited;
  - spree check: the member's streak of consecutive days with ≥ 1 approved
    completion (server dates, ending today) is computed; if it exactly equals a
    tier's `days` and that tier hasn't been awarded for this streak already, the
    tier's credits are awarded. Celebration toasts announce both;
  - **trophy reward**: if the member's count now lands exactly on a trophy rung, the home's
    reward for that rung is > 0, and no ledger row with that `trophyRung` exists for the
    member, a `CreditEntry` (reason `trophy`, tagged with the rung and the completion) pays it.
    Exact rung only, so setting a reward never pays members already past it; once per member
    and rung, so climbing back over a rung after an admin deleted an older completion does not
    pay twice; tagged with the completion, so undoing that chore takes the reward back. The
    unlock dialog announces it.
- **Spree tiers** (admin): list of `days → credits` rows, addable/deletable.
- **Trophy rewards** (admin): one row per rung under the spree tiers — emoji, name, "50
  chores" and a 💎 field (empty = none), saved as soon as it changes and clamped to
  `InputLimits.MAX_CREDITS`. The same for every member of the home. Stored as
  `TrophyReward` rows only for rungs that pay; `CreditService.setTrophyReward` re-checks that
  the acting member is an admin of that home and takes a `Trophy`, never a free number.
- **Balance** = earned − redeemed, per member.
- **On the card**: a chore with `creditValue > 0` carries a 💎 *n* pill under its name on the
  board (`.card-credit`, the leaderboard's yellow pill), so a member can see what a tap pays
  before choosing. It sits inside the tap area — information, not a control — and a chore worth
  nothing shows nothing, so a home that never uses credits sees the board it always had.
- **Redemption** (admin): amount 1..balance with an optional note; recorded as a
  credit entry so history is auditable. Over-balance redemptions are rejected. A blank note is
  stored as the marker `CreditService.NO_NOTE`, which the stats show as "(no note)" in the
  member's language rather than as a reward called "Redeemed".

### 4.8 Daily target (per member)
- `Home.dailyTargetPerMember` ∈ [1,3], default 1.
- "Done today" = a member's `APPROVED` completions with `doneAt` on the server's
  current date. Shown as `done/target` with a progress ring; reaching the target is
  highlighted.

### 4.9 Statistics & charts
- Rendered with a small dependency-free SVG/CSS **BarChart** (no commercial add-on).
- **Periods.** A row of four tiles — **Today / This week / This month / All time** — shows all
  four counts at once *and* is the lens picker: tapping a tile selects that period for the charts
  below (the tiles are plain divs with `role="button"`, focusable and operable with Enter/Space).
  The counts are deliberately *not* filtered: the question is a comparison ("what did I do today,
  this week, this month"), and a row showing only the selected period would answer a quarter of
  it per tap. There used to be a separate chip row with the same four labels beneath the tiles;
  users read it as redundant and tapped the tiles instead, so the chip row was removed. Weeks run Monday–Sunday, the
  same week `lastWeekChoreMaster` uses; months are calendar months; both use the server zone.
- **What the lens narrows**: the per-chore bars, the feedback split and (home) the per-member and
  popularity bars. What it never narrows: the headline tiles, the trends, today's adherence, and
  feedback-per-chore — "which chore does this family hate" needs more than a week of reactions.
- **Bucketing is by `doneAt`**, so a completion approved days late still belongs to the day it
  was *done*. Approval never moves a row between periods.
- **Cost.** A Stats tab is rebuilt on every change in the home, for every device parked on it,
  and completions are never purged — so what one render loads must not grow with the years.
  Under any lens but *All time*, the pass walks the **trend window only** (`StatsService
  .historyStart`: the twelve months the month trend draws, which covers every other picture the
  tiles and charts take from the rows themselves), and the facts that stay all-time under every
  lens are asked of the database instead: the approved total and the approval queue are count
  queries, feedback-per-chore is a group-by (`CompletionRepository.feedbackByTask`), the 💎
  totals are sums and the redemption list is read on its own (small by nature — a redemption is
  an admin typing into a dialog). Only the *All time* lens, the member's own choice, loads the
  ledger whole. The period is then applied inside that single pass and the trends derived from
  the same per-day map. `StatsRenderCostTest` pins the number of rows a render loads.
- **🏆 My trophies** (any member; on the shared device, the picked member's): the first card,
  above the period tiles, since the tiles are the lens for what is under them and trophies are
  all-time. The eight rungs as tiles four to a row (`role="list"`, no button role or focus
  stop): earned ones tinted with the day reached (`doneAt` of the member's *n*-th approved
  completion), locked ones with a greyed emoji, their rung, and a 💎 pill when they pay; the
  next rung outlined. Under them "Next: 🥈 Silver — 38 / 50, 12 to go" over a progress bar
  (`role="progressbar"`), or "Every trophy earned" at the top. Shown from the first visit — an
  empty shelf with the first bar is the invitation. Cost: a count, the reward rows and one
  single-row query per earned trophy, at most eight (`StatsService.trophyShelf`), only here.
  Empty for a member of another home.
- **My stats** (any member): total approved; the period tiles; bar chart of my chores by type
  under the lens; **💎 earned** and **💎 redeemed** (below); my feedback split; 7-day adherence;
  **12-week and 12-month trends**.
- **💎 Diamonds I earned** (`StatsService.diamondStats`, one query for the member's own ledger):
  the four headline totals — today / this week / this month / all time — as read-only tiles (the
  picker's shape, no button role, no selected state) over a 7-day column trend. *Earned* only:
  EARNED ledger rows including spree bonuses, bucketed by `createdAt` in the server zone, so a
  credit lands on the day its completion was approved — the day the celebration toast announced
  it. Redemptions never shrink it; spending has its own card, below. An
  undone completion has its rows deleted, so the chart self-corrects. The card is hidden until
  the member has ever earned a diamond, so a home that never uses credits sees no row of zeros.
  Never narrowed by the lens, like the chore tiles beside it.
- **💎 Diamonds I redeemed** (the REDEEMED rows of the same ledger pass, `DiamondStats.redeemed`
  and `.redemptions`): three read-only tiles — **earned · redeemed · balance**, where balance is
  the leaderboard's number — over a list of the member's redemptions, newest first, each with
  the day (`12.10.`, with the year only when it is not this year), the admin's note as the reward
  or "(no note)", and the 💎 amount. The list is a real `<ul>` so a screen reader announces its
  length. It is capped at the newest `StatsService.RECENT_REDEMPTIONS` = **10** rows, a phone
  screen's worth; older rows are counted in a trailing "…and N earlier" line and in the redeemed
  tile, never dropped from the total. Hidden until the member has redeemed at least once, for the
  same reason the earned card is hidden; never narrowed by the lens. The point of the card: a
  member who earns a lot and cashes it in promptly otherwise shows a balance near zero everywhere
  and reads, to the rest of the family, as someone who never earns anything.
- **Home stats** (admin): per-member bars under the lens; **what each member did** (below);
  **💎 Redeemed rewards** — the same redemption list for the whole home, newest first, each row
  prefixed with the member's name (`HomeStats.redemptions`, one query for the home's REDEEMED rows
  only: a redemption is an admin typing into a dialog, so that table grows by the week where
  completions grow by the hour), same cap, hidden while the home has never redeemed, never
  narrowed by the lens; chore-popularity bars under the lens; feedback per chore; 14-day activity;
  **12-week and 12-month trends**; per-member adherence today.
- **What each member did** (`HomeStats.perMemberChores`): every member's own "My chores" chart —
  their chores by type in board order plus accepted other help — gathered in the same single pass
  as the other home aggregates, so the admin sees for the whole family what each member sees for
  themselves. One `Details` fold per member, summary "Name · total", **folded by default** (five
  members would otherwise stack five bar charts on a phone before the trends); members with
  nothing in the period get no fold, since the card ranks what was done and the adherence card is
  the roll call. Which folds are open lives in a field on `StatsPanel`, so a `HomeState` redraw
  keeps them open; it resets on navigation like the lens. Follows the lens.
- **Twelve columns is the cap** (`StatsService.TREND_WEEKS`): that is what a ~360px phone column
  fits at a readable width, and the page must never scroll sideways. The axis carries digits —
  `15.6` for a week, the month *number* for a month — with the readable form in the tooltip,
  because a localized short month ("marrask.") is half again wider than its column and
  `overflow-x: hidden` would clip it rather than reveal it (§4.15.1).
- The lens lives in a field on `StatsPanel`, so it survives every `HomeState` rebuild and resets
  on navigation. Tapping a tile re-renders that panel only and **never bumps `HomeState`** —
  which period one member is looking at is not the family's business, the same rule the board's
  filter chips follow.

### 4.10 Approvals (admin)
- A list of all `PENDING` chore completions for the home, newest first, each with member,
  chore, time, feedback, and **Approve** / **Reject** actions. Other help is excluded here
  and listed separately (§4.3.2), though the Admin tab's badge counts both.
- Approve → `APPROVED`, records reviewer/time, triggers trophy/new-chore/credit
  evaluation. The member sees a trophy it reached on their next board visit (§4.6).
- Reject → `REJECTED` (retained for audit, excluded from all counts and fairness).
- Live-updates when members submit; the Admin tab shows a pending-count badge.

### 4.11 Backup / restore (admin)
- **Backup**: a JSON document `{ version, home, members[], groups[], tasks[], completions[],
  spreeTiers[], trophyRewards[], credits[] }` for this home only (the "family DB"), offered as a
  file download `home-chores-<code>-backup.json`. Tasks include interval, credit
  value and availability windows; completions include the other-help `note`; members their
  `trophiesSeen`; credit rows their `trophyRung`. On restore, trophy rewards and paid rungs
  are kept only for rungs on the ladder, and a member from a file written before trophies is
  marked as having seen the trophies their restored completions reach, so a restore does not
  replay a year of unlock dialogs.
- **Restore**: upload a backup JSON. After a confirmation dialog warning that current
  data will be replaced, the home's data is deleted and recreated from the file, and
  home settings (name, PIN, approval, division style, booking hold, target) are
  applied. **The file's home code must match the admin's own home** — an admin can only
  restore over their own home, never another family's — **and the acting member must be an
  admin of it at that moment**, since the confirmation outlives a demotion. IDs are remapped internally;
  orphaned records are skipped — an other-help entry has no task to remap and is not an
  orphan. Invalid files (including a mismatched home code) are rejected with a message.
  A file carrying keys this build does not know (a backup written by a newer version) is
  restored for everything it does understand rather than refused outright. Restoring
  stamps the home as active. The restoring admin is signed out and rejoins.
  Restoring a home that no longer exists is deliberately *not* reachable here — there is
  no admin to be signed in as — and is the operator's `restore` in §4.11.3.

### 4.11.1 Deleting the home (admin)
- A **Danger zone** section at the bottom of the Admin tab, visually separated because it
  holds the only action nothing can undo.
- The dialog names the home, spells out what is lost (member and chore counts, plus every
  completion, credit and setting), suggests downloading a backup first, and requires the
  **home code to be typed** before the delete button does anything.
- `ChoreService.deleteHome` removes, in order: rejoin requests, completions, credits and
  spree tiers, chores, members, and finally the home row. Other homes are untouched.
- The acting admin's device signs itself out and returns to the landing page *before* the
  delete runs, so it gets its own confirmation; every other device is shown out by the
  revision bump with "This home was deleted by an admin", and drops its stored identity.
- Revision bumps are deferred to **after the transaction commits** (`HomeState.bump`).
  Bumping inside the transaction would wake the other devices while they can still read
  the pre-delete state — they would re-render the doomed board and never hear again.
- The freed home code returns to the pool and may be issued to a future home.

### 4.12 Localization (i18n)
- Languages: English (default/fallback), Finnish, Swedish, via an `I18NProvider`
  over `messages[_fi|_sv].properties`. All UI texts, blocked messages, badges and
  placeholders are localized.
- Initial locale = best match for the browser language; the **language switcher**
  (landing page and home header) persists the choice in a `lang` cookie applied on
  session init.
- Default chores are seeded using the home creator's locale (chore names are data;
  they don't change retroactively when the UI language changes).

### 4.12b Appearance: colour scheme and palette
- Two independent **per-device** axes, both in `localStorage`, neither a home setting: the colour
  **scheme** (auto / light / dark, narrowing the `light dark` the server ships) and the brand
  **palette** (green / pink / blue / grey). Both are applied by the inline `<head>` script in
  `index.html`, before Vaadin loads, so there is no flash of the wrong one.
- **A palette is three numbers**, not a second copy of every token: `--brand-h`, `--brand-h2` and
  a saturation multiplier `--brand-sat`, which every brand colour in `styles.css` is written in
  terms of. Four copies of each `light-dark()` pair would be four chances for one scheme to drift
  unnoticed. "Grey" is the palette a hue alone cannot express, so it drops `--brand-sat` and buys
  back the text token's contrast with lightness — the file's one per-palette token override.
- The palette rides on its own **`data-palette`** attribute. Vaadin owns `theme` on `<html>` and
  rewrites it wholesale on every scheme change, which would carry a palette parked there away.
- **`html, html[theme]`, not a bare `html`.** Lumo ships
  `[theme~="dark"] { --lumo-primary-color: … }`, whose specificity beats a plain `html` — so in
  *forced* dark mode every brand token silently lost to Lumo's blue and the app stopped being
  green. Auto mode was unaffected, which is why it went unnoticed.
- **Semantic colours deliberately do not follow the palette**: the hate/ok/love segments, the
  error red, the streak flame, the credit gold, member colours. Those encode meaning, and the same
  bar must not read differently on two phones in one kitchen.
- The picker is a single palette-glyph **`MenuBar`** in the header holding both axes. It replaced
  the 7.5em theme select rather than joining it: the ≤640px action row was already at its limit
  (§4.15.1), so this adds a setting while making the header *narrower*.
- `@PWA(themeColor)` and the icon set are build-time constants, so the **installed** app's splash
  and task-switcher card stay green whatever palette is chosen. The in-page
  `<meta name="theme-color">` is patched to match, since a green address bar above a pink header
  is visible for the whole session.

### 4.13 PWA & sharing
- `@PWA` app shell: manifest (standalone display, theme color `#10b981`, white
  background), service worker, offline fallback stub.
- A full custom icon set is provided as static resources under
  `META-INF/resources/icons/` (favicons, 144/192/512 manifest icons, 180×180
  apple-touch icon, and all iOS splash-screen sizes) — static resources override
  the auto-generated default-logo icons in every deployment mode.
- **Share**: the header's *Copy link* copies `origin/?join=CODE`; *Share* opens the
  native share sheet with a localized text that includes the home name, code **and
  the join link** (some share targets drop the separate URL field), falling back to
  the clipboard.
- **Chore reminders (Web Push, opt-in)**: when VAPID keys are configured
  (`homechores.push.*`), a bell in the board header lets each member pick a daily
  reminder time (their own browser timezone, persisted on `Member.zoneId`). A
  once-a-minute sweep (`PushReminderService`) sends a push to the member's subscribed
  devices when that time passes with no completion (any status) logged that member-local
  day; `Member.lastRemindedOn` makes it once per day and restart-safe. The payload is
  Vaadin's `flow-webpush` format, so Vaadin's generated service worker handles the `push`
  and `notificationclick` events unchanged; the HTTP send is the app's own
  (`WebPushSender`), over a client with timeouts. **A send is bounded**: DNS check, connect,
  request and response run on a virtual thread the sweep waits on for at most
  `homechores.push.send-timeout-ms` (10 s; connect 5 s), after which the exchange is cancelled
  and the row kept. The endpoint is the one URL a browser chooses and the server then connects
  to, and every sweep runs on the single scheduler thread, so without that bound one endpoint
  that accepts and never answers would stall every sweep for every home until a restart.
  Subscriptions (`PushSubscription`) are per device, **at most `MAX_DEVICES_PER_MEMBER` = 5 per
  member** (the oldest is evicted when a sixth arrives), pruned when the push service reports
  404/410, deleted with their member/home, and excluded from backups. The per-sweep send caps
  count devices, not reminders. An endpoint already on file **moves to whoever subscribes from
  that browser** — a profile hands the same endpoint to everyone who signs in on it — and
  *Leave* unsubscribes the device and drops its row, so a shared tablet never carries one
  member's reminders (or a home's list-line texts) to the next. Unsubscribing is scoped to the
  member's own row. On iOS this requires the PWA installed to the Home Screen (16.4+); the
  dialog says so.

### 4.13b Per-chore reminders (one-shot snooze)
- A ⏰ on each chore card opens a six-chip dialog (1h / 2h / 4h / 8h / 1d / 1w). Offsets are
  relative to **now**, not to when the chore next comes due: the member is looking at the board
  when they ask, and ten of the eleven seeded chores have no interval for a due date to anchor to.
- **The row is the reminder.** `ChoreReminder` exists from the moment it is asked for until it
  fires, and is then deleted — a one-shot has no recurrence, so there is no `lastRemindedOn`
  equivalent and nothing to make idempotent beyond the row's own existence. **At most one per
  member per chore**, upserted: "in 2h" then "actually, tomorrow" moves it rather than arming a
  second. Enforced in the service, not with a unique index, since `ddl-auto=update` adding one to
  a populated table is exactly the kind of thing that bites.
- `ChoreReminderService` is a **sibling** of `PushReminderService`, not an extension: their working
  sets are opposite. The daily sweep evaluates every member with a reminder configured, every
  minute; this one asks for what is already due — normally nothing, one query returning no rows,
  which is what makes a fifth job on the single-thread scheduler affordable. What they share is
  the transport and the discipline: **no transaction spans a blocking push send**, and sends are
  capped (10 here, lower than the daily 20 because the two share a thread).
- **Retired unconditionally** — sent, undeliverable, member gone, or no longer due. A row that can
  never produce a useful notification must not be reconsidered every sixty seconds forever, and
  unconditional deletion bounds the crash window to one duplicate nudge rather than an endless
  retry.
- **"Someone else did it" needs no write.** The sweep checks `isDue` when a reminder actually
  fires and retires it silently if the chore is no longer due, instead of fanning a write out
  across every member's reminders on the hottest path in the app.
- **Cancelled by**: completing the chore (own reminder only — `complete` and `completeFor`),
  deleting the chore, removing the member, deleting the home, restoring a backup (ids are
  remapped, so a survivor would nudge the wrong person about the wrong chore), and turning the
  daily reminder off (which destroys the browser subscription these would be delivered to).
- **Excluded from backups**, like `PushSubscription`: a pending, device-targeted notification is
  not family history.
- The ⏰ shows only when VAPID keys are configured, like the header bell — a control that exists to
  explain a server setting the family cannot change is noise. A member who has never enabled
  notifications runs the permission flow first, and the reminder is stored only on `granted`;
  arming one that provably cannot be delivered would be a lie the board would then badge.
- An armed reminder badges its own card with the **absolute** time it will fire, not "in 2h": the
  board only re-renders when something in the home changes, so a relative label would be wrong
  within a minute. Cancelling lives inside the dialog, never on the card's chip — a one-tap
  destructive action on a card someone may be trying to complete is the stray-tap problem
  `confirmCompletion` exists to solve.
- Arming **does not bump `HomeState`**: one member's private nudge is not the family's business.
- The board lookup is **one query per render** in `ChoresPanel`, never one per card and never
  inside `taskViews` — that method's cost is a tested invariant (`BoardRenderCostTest`), and a
  per-member badge has no business in a per-home projection.
- **Tapping any FlashChores notification opens the board, not the chore.** Vaadin's generated
  service worker hard-codes `/` in its `notificationclick` handler, and overriding it would mean
  owning the precache manifest, the offline fallback and the connection-lost channel — plus
  widening `WebPushSender.send` to carry a URL, a method the daily reminder shares. Two invariants
  disturbed to save one tap; the notification already names the chore.

### 4.13c List-line reminders (home-wide, answered in-app)
- A 🔔 on every open to-do and grocery line (never a ticked one, never a dinner slot) opens a
  dialog with four quick chips — **in 1 hour, tomorrow 9:00, next week 9:00, next month 9:00** —
  and a fifth that unfolds a date-and-time picker (15-minute step, defaulting to tomorrow 9:00).
  The wall-clock options land on `ListReminderService.ANCHOR` (09:00) in the *setter's* zone,
  resolved on the phone via `SessionContext.timeZone()` before the instant is stored: a reminder
  set at 23:40 that fired at 23:40 the next day would be one nobody wanted. `plusMonths` clamps
  the 31st to the last day of a shorter month. Bounds in the service: `[now + 1 min, now + 400 d]`.
- **Home-wide, not per member** — the deliberate difference from §4.13b. A shared list is
  shared: one `ListReminder` per line, pushed to every subscribed device in the home (each in its
  own member's language via `Member.reminderLocale`), badged under the line for everyone
  ("⏰ 9:00", "⏰ 19.9. 9:00", "⏰ in 12d" — absolute, never relative, for the reason the chore
  badge is), and movable or cancellable by anyone in the home. The reminder also decides where
  the line sits on its list — due ones lead, far-off ones trail (§4.4e). Because it is the family's
  business, arming, answering and firing all **bump `HomeState`** — again unlike the chore snooze.
  Upserted per line in the service, not with a unique index (`ddl-auto=update`, §4.13b).
- **Fired is not finished.** Vaadin's generated service worker answers a notification tap by
  opening or focusing the board and nothing else (§4.13b explains why the worker is not ours to
  rewrite, and iOS shows no notification action buttons at all), so the snooze has to happen on
  the board. The sweep therefore stamps `firedAt` instead of deleting the row. `HomeView` shows
  the oldest unanswered reminder as a dialog — "⏰ Reminder", the line, the same chips now meaning
  *snooze until*, plus **Done** (ticks the line off) and **Dismiss** — from three triggers:
  every `buildChrome` (the sweep's bump brings it live to an open board), arrival on the view, and
  a `visibilitychange` listener calling `@ClientCallable onVisible()`, which is what turns a tap on
  the notification into the dialog when the PWA was already open in the background. One dialog
  per nudge per screen: a dialog still open for a nudge somebody else has since answered closes
  itself; one closed with the footer button stays away for that screen and returns on the next visit.
- **Sweep** (`ListReminderService`, `homechores.list-reminder.sweep-ms`, default 60 s): the third
  push sweep, a sibling of the other two with the same three-phase transaction discipline and the
  same 10-send cap as the chore snooze. Working set = due and not yet fired, normally empty. A
  reminder whose line is gone or already ticked is deleted without sending; one attempted to at
  least one device (even a device that turned out to be gone, which is pruned) becomes unanswered;
  one with nobody to send to is dropped quietly — a dialog nobody was notified about is noise.
- **Retired by**: snoozing (back to pending with the new time), ticking the line off — by
  anyone, pending or fired; unticking does not revive it — deleting the line, dismissing from the
  dialog, deleting the home, restoring a backup (line ids are reissued), and, unanswered, the
  hourly purge after `FIRED_RETENTION` (7 days). **Not** by removing a member (it belongs to the
  line; `setByMemberId` dangles like `createdByMemberId`) and **not** by a member turning their
  daily reminder off — the other phones still receive it.
- Excluded from backups, like every other pending notification. The 🔔 shows only when VAPID keys
  are configured, and arming runs this device's permission flow first, storing only on `granted`
  — the member asking to be reminded should be among those who are.

### 4.14 Live sync
- `HomeState` keeps a per-home revision **Vaadin Signal**; every mutation bumps it.
- `HomeView` registers a `Signal.effect` that re-renders when the revision changes,
  delivered to all of the home's open UIs via `@Push` (long-polling). This replaces
  the earlier broadcaster/`UI.access` plumbing; effects are disposed on detach.

### 4.3.1 Confirming, undoing and unmarking
- **Confirm** (`Home.confirmCompletion`, default on): tapping a card opens a small dialog
  naming the chore, with "Yes, I did it" / "Not yet". Declining records nothing. Turning
  the setting off restores the original one-tap behaviour.
- **Member undo**: `ChoreService.undoCompletion(completionId, memberId)` succeeds only for
  the member's **own** completion and only within `UNDO_WINDOW` (10 minutes). Offered in
  the celebration dialog and, so it survives dismissing that, from an undo strip on the
  board fed by `undoableCompletion(memberId)`.
- **Admin unmark**: `deleteCompletion(id)` from the Recent chores list — any member, any
  age, any status.
- **Credits follow the completion.** `CreditEntry.completionId` records which completion
  earned an award (chore value *and* any spree bonus triggered by it), and both undo paths
  revoke them. Without that link an undone chore would leave phantom 💎 behind, and a
  member could farm credits by completing and undoing the same chore repeatedly. The id is
  remapped on backup restore alongside members and tasks.
- Undoing also frees the chore for the fairness streak rule again, since the run is
  recomputed from the remaining completions.

### 4.3.2 Other help (help no chore covers)
A chore list is never complete, and a child who carried the shopping in shouldn't have to
choose between tapping something they didn't do and getting nothing for it.

- **Modelled as a completion without a chore.** `Completion.taskId == null` and
  `Completion.note` holds what the member wrote (≤ `ChoreService.MAX_HELP_LENGTH` = 200 chars,
  trimmed to fit). Reusing the completion table is what makes an accepted entry count
  everywhere a chore does — totals, daily target, spree streaks, leaderboard, the admin's
  recent list, undo, backup — instead of needing a parallel notion of "credit for something".
  Nullable `taskId` is why the aggregations compare `task.getId().equals(c.getTaskId())` and
  never the other way round.
- **Always `PENDING` when a member logs it**, even when `requireApproval` is off: the text is
  freeform and there is no chore behind it, so it counts for nobody until an admin has read
  it. `logOtherHelp` returns empty when the home has the feature off or the text is blank.
- **An admin's own help counts at once** (`logOtherHelpAsAdmin`, US-55). Recorded `APPROVED`
  with the admin as `reviewedByMemberId` — the same shape as §4.3.3 — and the reward named in
  the dialog, since acceptance is where a member's entry would get one. Written once and
  bumped once rather than saved `PENDING` and then approved, because `HomeState.bump` does
  not coalesce and two bumps redraw every open board twice. The service refuses a caller who
  is not an admin of that home; the board only shows the admin variant of the dialog to
  admins, but the rule lives in the service. The celebration dialog handles the task-less
  outcome ("🙋 Other help") — until this, nothing ever celebrated one.
- **Member's view**: a 🙋 card at the end of the board (dashed, so it doesn't read as a chore),
  a one-field dialog, and a "⏳ n waiting" badge for their own undecided entries. Their own
  entry is undoable from the board strip for `UNDO_WINDOW` like any completion.
- **Admin's view** (§4.10 lists chores only; help has its own section above it, since the
  decision differs): who, what, when, **Accept** / **Decline**. Accepting asks for a reward in
  credits (0 = none) — a chore carries its own `creditValue`, hand-written help has nothing to
  read one off — and then offers to add it to the chore list (US-45). Declining sets
  `REJECTED`, which every count already excludes.
- **Statistics** count accepted help as one more bar in "my chores by type" and in chore
  popularity. `StatsService` returns the count and the view supplies the label: "Other help"
  is UI wording, unlike a chore name, which is the family's own data.
- **Per-home switch**: `Home.allowOtherHelp`, default on, with the usual column default so
  `ddl-auto=update` can add it to existing databases (§5).

### 4.3.3 Logging a chore for someone else (admin)
`ChoreService.completeFor(taskId, memberId, adminId)`, from the **Log a chore for someone**
section of the Admin tab (member picker + chore picker + "Log it"). The member picker lists
everyone in the home except the admin doing the logging.

- **Skips every lock** in §4.3 — interval, hours, rotation, booking, fairness streak. They
  decide who *should* do a chore next; this records who *did* one. It clears any booking on
  the chore, as completing it normally would.
- **Recorded `APPROVED`** whatever `requireApproval` says, with `reviewedByMemberId` set to
  the admin, so the history shows whose word it was. Credits, trophies, the daily target and
  spree streaks all follow for the member it was logged for, not the admin.
- **Reversible** through the same Recent chores unmark (§4.3.1), credits included.

### 4.11.2 Retention of abandoned homes (operator)
- `Home.lastActiveAt` records when a **person** last used the home: completing a chore,
  approving/rejecting one, joining, signing back in, or opening the board. It is *not*
  touched by push traffic, so a phone left on a charger doesn't keep a home looking alive.
  Writes are throttled to once an hour per home and never bump the revision signal.
  Nullable — homes predating the column fall back to `createdAt`.
- `HomeCleanupService` purges a home only when **all** hold:
  1. last activity (or creation) is older than the configured window;
  2. it has **no completions at all**, of any status (a rejected one still counts as history);
  3. it has **at most one member** — inviting someone means it was more than a stray tap.
- Governed by `homechores.retention.abandoned-home-days` (**0 = disabled, the default**)
  and `homechores.retention.cron` (nightly at 03:30 by default). `findAbandoned(cutoff)`
  is a dry run for inspecting candidates without deleting.
- Deletion reuses `ChoreService.deleteHome`, so the cascade across all eleven tables
  — rejoin requests, push subscriptions, chore reminders, completions, credits, spree tiers,
  chores, chore groups, shared-list lines, members, the home row — stays in one place; sweeps log counts only, since a home
  code is the home's access credential.
- Time-based deletion of homes that *were* used exists as its own opt-in tier
  (`inactive-home-days`, US-40) and follows the safeguard this section always demanded:
  a full backup is exported before deleting, the home is kept if that export fails, and
  the export is restorable with the maintenance CLI's `restore` (§4.11.3) — a restore
  stamps `lastActiveAt`, so a revived home is not swept away again the same night.
  There is still no way to warn a whole family first, so the window is a deliberate
  operator decision and both `/terms` and `/privacy` state it (generated from config,
  with a "contact us to restore" note).
- `PrivacyView` renders the retention sentence from the configured value, so the published
  notice cannot drift out of step with what the server actually does.

### 4.11.3 Operator maintenance CLI
`tools/flashchores-admin.py` + `MaintenanceRunner` service the erasure requests the
operator has to handle personally (lost admin PIN, legal escalation) and let them inspect
retention candidates before enabling the sweep.

- **No public admin surface, by design.** The app has no authentication beyond household
  codes, so an authenticated "delete any home" endpoint on the internet would be its
  highest-value target. Instead the CLI starts the app's own code in a one-shot mode
  (`--maintenance.command=…`), which prints a JSON result between fence markers and shuts
  itself down. Vaadin's Spring integration requires a web context, so the launcher gives it
  an ephemeral **loopback** port for the couple of seconds the command runs.
- **No hand-written SQL.** Deletion calls `ChoreService.deleteHome`, so the ten-table
  cascade (§4.11.2) lives in one tested place; SQL by hand would risk orphaned rows.
  Restore likewise goes through `BackupService`, which remaps ids rather than trusting
  the ones in the file.
- **Requires the service stopped**, because H2 holds an exclusive file lock. The script
  probes the port and refuses with an instruction rather than failing part-way.
- Commands: `list`, `show`, `export`, `delete`, `restore FILE [--overwrite]`,
  `purge --days N [--dry-run]`. `delete` exports a backup to `data/erasure-exports/` first
  (unless `--no-backup`) and requires the home code to be typed, mirroring the in-app
  Danger zone. `--db-url` targets another database, e.g. a restored copy. Exit codes:
  0 success, 2 handled failure.
- **`restore` is the other half of retention.** The in-app restore (§4.11) requires an
  admin signed into the home, which a home the sweep has already deleted no longer has —
  so without an operator-side restore the safety export written in §4.11.2 would be a file
  nothing could read back, while `/terms` and `/privacy` tell families they can ask for it.
  It reads the home code out of the file, prints what will be created, and requires the
  code to be typed like `delete` does. A home with that code that still exists is refused
  unless `--overwrite` is given, because restore is a wipe-and-replace and the normal case
  here is putting back something that is gone. `BackupService.restoreAnyHome` is the entry
  point; its safety comes from being reachable only from a process the operator starts on
  the host with the service stopped, not from a code comparison.

### 4.15.1 Mobile layout
The app is used mostly on phones, so the layout is designed for a ~360–400px column and
scales up, not the other way round.

- **A tab switch starts at the top.** The page is the scroller, so a phone deep in the long
  Chores or Admin panel that switched to Lists or Stats used to land with the tab bar and the
  whole short panel scrolled out of view. `HomeView` scrolls `<body>` (the scroller) to the top on the
  member's own tab tap — never on a `HomeState` rebuild, which must not move anyone's scroll.
- **No horizontal scrolling, ever.** The app is a single column; any sideways travel is a
  layout bug, and being able to drag the page half out of the viewport feels broken.
  `html, body` set `overflow-x: hidden` and `overscroll-behavior-x: none` as a backstop,
  but overflow is fixed at its source — the backstop hides a control rather than
  revealing it, so a clipped element is *less* visible, not more. When checking layout,
  measure each element against its container, not just against the viewport.
- **`box-sizing: border-box` globally.** Every box here is padded; a content-box element
  with `width: 100%` plus padding silently overflows. This was the sign-in card bug: 40px
  of padding made a 327px card 407px wide on a 375px phone.
- **Safe areas.** `index.html` ships `viewport-fit=cover`, so page padding uses
  `max(<pad>, env(safe-area-inset-*))` on all four sides — top included, or the header
  slides under the iPhone camera island / Dynamic Island — to clear the notch and home
  indicator, and heights use `100dvh` (with a `100vh` fallback) to track iOS Safari's
  collapsing URL bar. The fixed `.restore-overlay` (and the loading card built from it, §4.1.2)
  pads its own top the same way.
- **Toasts at the bottom.** Every toast — credits, Undo, copied, saved, errors — opens
  at the bottom centre (`Toasts`), never at the top, where with `viewport-fit=cover` it sat
  behind the camera island. The bottom is also where the thumb is for a toast's Undo, and the
  notification container pads its bottom with `max(var(--lumo-space-s), env(safe-area-inset-bottom))`
  to clear the home indicator — or by `--keyboard-inset` when that is larger: iOS Safari and
  Android Chrome open the on-screen keyboard over the page without resizing it, so a join error
  answering Enter would open under the keys, and `keyboard-inset.js` measures how much of the
  page the visual viewport no longer shows. The two answers to a tap that did nothing — why a chore cannot be done right now,
  and "someone else just booked this" — stay in the middle of the screen, where the eye already is.
- **Header (≤640px).** The identity block and the action cluster each take a full row
  rather than pushing each other off-screen; the home name is one ellipsised line. Copy
  and Share collapse to icons — their glyphs are self-explanatory and they sit beside the
  code chip — while "Admin?" and "Leave" keep their text, since a bare key or exit icon is
  ambiguous. The room comes from narrowing the language select instead. Collapsed labels
  stay in `aria-label`.
- **Invite menu (multi-member homes).** Once a second member has joined, the inline code
  chip + Copy + Share retire into a single share-glyph `MenuBar` at the right end of the
  action cluster: the code (tap to copy it bare), Copy link, Share. A solo home keeps the
  inline row — inviting is the one thing that board still has to make happen. The menu's
  code chip uses lumo tokens (`.code-menu-chip`), not the white-on-gradient `.code-chip`.
- **Touch.** Hover effects are behind `@media (hover: hover)` — on a touch screen `:hover`
  sticks after a tap and leaves a chore card looking permanently pressed. Small icon
  buttons get a 40px minimum hit area under `@media (pointer: coarse)`, and tapped cards
  suppress the platform's grey tap highlight in favour of their own press animation.
- **Wrapping over truncation.** Admin field-plus-button rows and field labels wrap; long
  select options were shortened with the explanation moved to helper text rather than
  being cut off mid-word.

### 4.15 Privacy
- `/privacy` is a public, plain-language notice: what is stored (names, completions,
  code/PIN), what is not (no emails, no trackers, single strictly-necessary session
  cookie), data location/retention, user rights (correct/erase/export via admin),
  children's-data guidance (nicknames), and operator contact details.
- **Server logs and hosting statistics.** The app records no client data of its own —
  Tomcat access logging is off (`server.tomcat.accesslog.enabled=false`) and nothing logs
  at request level, so no IP addresses are stored by FlashChores. The hosting platform
  underneath still keeps the ordinary web-server records every site needs (access logs and
  AWStats-style aggregate statistics) for availability and abuse/attack detection. The
  notice says so plainly, and says that those records are infrastructure-level, short-lived
  and never correlated with a member, a home or a home code — otherwise the "what we do NOT
  store" claim reads as more absolute than the deployment can honour. The one line the app
  itself writes about a browser — a refused stored sign-in (§4.1): the member's or shared
  device's id, the reason, how the browser runs and a short User-Agent label, never a name, a
  home code or an IP address — is named in the notice as a narrow exception, with the log's
  26-week retention (`logback-spring.xml`, `maxHistory`).
- **Both reminder kinds are described.** The daily "nothing logged yet" reminder stores a
  time, a timezone and a push subscription; a per-chore snooze (§4.13b) stores which chore and
  when, until it fires. Each has its own bullet under "What data we store", each says when the
  data goes away, and both say they never enter a backup. The notice has to grow when the
  stored data does, or "what we store" quietly becomes a lower bound rather than a list.
- **Erasure requests.** Self-service is the primary route and is already complete: an admin
  can erase one member (Admin → Members) or the whole home (Admin → Danger zone), and
  export first via Backup & restore. For operator-assisted requests the notice asks for the
  **home code plus admin PIN** — with no stored email or account there is nothing else to
  authenticate a request against, and the code alone would let anyone have a family's board
  deleted. The retention paragraph is generated from the live configuration (§4.11.2).

### 4.16 SEO & discoverability
- Vaadin Flow has no crawler prerendering, so crawlability is built into the shell:
  `index.html` carries static meta description + Open Graph/Twitter tags and a short
  honest `#seo-content` block inside the outlet (hidden by CSS the instant the app
  mounts — same HTML for every client, no cloaking), with plain links to `/privacy` and
  `/terms`.
- `SeoIndexHtmlListener` injects the per-route bits: canonical + description + og:title/
  og:url for the three public routes, JSON-LD (`WebApplication`) on `/` only, and
  `noindex` for `/home` and everything else. All emitted only when `homechores.base-url`
  is set, so a staging deployment never declares itself canonical.
- Static `robots.txt` (disallow `/home`, sitemap pointer) and `sitemap.xml` (the three
  public URLs). No hreflang: all three languages share one URL (cookie/header locale),
  which hreflang cannot express — SEO content stays English, while Vaadin still stamps
  `<html lang>` from the resolved locale.

## 5. Non-functional / technical
- Vaadin 25.3 Flow + Spring Boot 4.1 (Java 25), H2 file DB (`data/`), server push
  (long-polling) + Vaadin Signals, installable PWA.
- Charts, confetti and icons are self-contained (no external CDN, no licensed
  components).
- **Time zones**: availability hours are evaluated in the member's browser time
  zone (per session, server zone as fallback); interval due-dates, daily targets
  and spree streaks use the server's zone.
- The admin PIN is a lightweight household credential, not a strong secret; it gates
  casual misuse and is **rate-limited** (5 wrong tries → 15-minute per-home lockout on both
  the claim-admin and rejoin-bypass checks) rather than being brute-force-proof. Codes are
  long enough not to be guessable. **HTTPS is required in production**: the app binds
  loopback and expects a TLS-terminating reverse proxy — enforce HTTPS + HSTS there and send
  `X-Forwarded-Proto`, which is what makes Tomcat mark the session cookie `Secure`; the app
  itself adds `SameSite=Lax` (§4.1.2). Behind Cloudflare the edges are trusted in the
  forwarded-for chain (`server.tomcat.remoteip.trusted-proxies`), so the client the rate
  limiter keys on is the visitor, not the edge, whatever the proxy forwards.
- **Response headers** (`SecurityHeadersInitListener`, on every request the app serves, so
  they hold without the proxy): `X-Frame-Options: DENY` against click-jacking of the
  authenticated board, `X-Content-Type-Options: nosniff`, `Referrer-Policy:
  strict-origin-when-cross-origin` so the invite URL (which carries the home code) never
  reaches a third-party site as a referrer, and a `Permissions-Policy` that switches off
  camera, microphone, geolocation, payment and USB — the clipboard, the share sheet and
  notifications, which the app uses, are left alone. No CSP: Vaadin bootstraps with inline
  scripts and `index.html` carries one, so a nonce-based policy is a project of its own.
- **Identity storage**: the browser holds `memberId|homeCode|secret` — a per-device 128-bit
  secret whose SHA-256 the server checks on every restore — and, while a join/rejoin is
  pending, a separate 128-bit device token. No personal data. The secret *is* the
  credential: a hand-edited `localStorage` value cannot impersonate a member. Server
  sessions are short-lived (§4.1.2); local storage, not session length, is what keeps a
  phone signed in.
- **Browser auto-translation is off** (`translate="no"` + `notranslate` + the Google meta
  tag, in `index.html`). The app already renders in the user's language (§4.12), so a
  machine translation on top would fight it — and would rewrite the strings that must stay
  verbatim: home codes, member names and the free text of other-help entries.
- **Schema evolution**: with `ddl-auto=update`, new non-null columns on existing tables
  must declare a column default (see `Home.approveRejoin`) — H2 rejects the plain
  `ALTER TABLE … ADD COLUMN … NOT NULL` on a table that already has rows.

## 6. Testing strategy
- **Service unit tests (JUnit 5)** against an in-memory H2 profile: home create/join,
  admin PIN claim, fairness (incl. reset by another member, pending counting),
  approval on/off effect on counts & trophies, feedback, daily target, booking
  (block/expiry/cancel/clear-on-complete), rotation (distinct daily assignments,
  enforcement, day advance), interval due-dates, availability windows (parsing,
  normalization, in/out-of-window completion via fixed-offset zones), credits &
  spree bonuses, stats aggregation, backup→restore round-trip (incl. windows and the
  rejoin gate), localized default seeding, and rejoin requests (gate on/off, PIN bypass
  without promotion, wrong PIN, member from another home, approve/reject, token
  consumption, newest-device-wins, cancel, cleanup when a member is removed), and home
  deletion (every table wiped, other homes untouched, idempotent, code normalization,
  freed code reusable), and other help (pending regardless of the approval setting when a member logs it; counted at
  once with the admin as reviewer and the named credits awarded when an admin logs their own,
  and that path refused for a non-admin, for another home's code, and when help is off; accept
  counts it and awards the named credits, decline leaves it uncounted, separate queues but one
  badge count, blank/switched-off records nothing, over-long text trimmed, member's own undo,
  stats counted apart from chores, backup round-trip keeping the note and the setting), and
  logging a chore for someone (counts for them and not the admin, immediate even with approval
  on, admin recorded as reviewer, goes in despite the streak lock and before an interval chore
  is due, credits to the member who did it, clears a booking, unmarkable, cross-home refused),
  and chore groups and board order — the headline case being that **reordering chores does not
  change today's rotation assignment** (and that the enforcement gate still names the same chore
  the badge does), plus group CRUD, deleting a group keeping its chores and history, move-past-
  the-end being a no-op, buckets staying dense, a dangling groupId rendering as ungrouped, a
  cross-home group being refused, the legacy database keeping its exact order until something
  moves, and groups round-tripping a backup (including a pre-groups file restoring ungrouped);
  and `placeChore` — before a neighbour, to a section's end, into another group via a neighbour,
  into an empty group, a no-op drop (same slot, before itself, the last card to the end), another
  home's neighbour refused, another home's *card* refused (the session's home scopes the drop),
  and the rotation invariant again under drags; and trophies (`TrophyTest`): the ladder's
  best / next / exact rung; the reward paid at the rung, once, and not below it; a rung with no
  reward still reached; undoing the completion that reached it takes trophy and 💎 back and
  re-doing pays again, net once; climbing back after an older completion is deleted is
  celebrated but not paid twice; setting a reward is not retroactive; in an approval home a
  pending chore reaches nothing, the approval reaches it and leaves it unseen for the member,
  and marking it seen clears it; an admin's logging leaves it unseen; the member's own tap
  marks it seen; trophies owned before the feature are all unseen once; only a current admin
  of that home sets a reward (member, another home's admin and a demoted admin refused), it is
  clamped and 0 removes it; `approvedCounts` groups the home by member, ignoring pending rows
  and other homes; the shelf dates each earned rung by the *n*-th completion and names the
  next with its reward, and is empty for another home's member; rewards, paid rungs and the
  seen marker survive a backup round-trip, and a pre-trophy file restores with its trophies
  seen. `BoardRenderCostTest` pins the leaderboard's counts at one statement for a home of six,
  and `DeleteHomeTest` that trophy rewards go with the home.
- **Upgrade path** (`SchemaEvolutionTest`): the whole application booted with the production
  `ddl-auto=update` against a file database built from `legacy-schema.sql`, a pre-Phase-4
  FlashChores: the context starts at all, settings added since adopt their documented
  default on an old home, historic rows still complete, aggregate and export, tables added
  since are created empty and writable, chores from before groups existed keep their order
  with the column default 0 until the first move, and list lines written without
  `sort_order` read in writing order with the default 0 until the first drag renumbers them.
- **Vaadin UI Unit tests** (`vaadin-testbench-unit-junit5`, `SpringUIUnitTest` through
  `HomeChoresUiTest`, which installs TestBench's signals test environment so that another
  phone's bump of the home reaches the view deterministically through `runPendingSignalsTasks`
  rather than through `VaadinService`'s thread pool; browserless): create-home flow navigates to the board with four tabs; join flow
  (three tabs, no Admin); PIN claim reveals the Admin tab, and a wrong PIN marks the field
  and reveals nothing — no tab, no role, the member lifetime; a demoted admin's own device
  loses the Admin tab and the panel on the next redraw and drops back to the member
  lifetime; a removed member, and everyone in a deleted home, is shown out to the landing
  page signed out; the members card refuses to demote or remove the last admin; a member's
  Stats has no Home stats switch; admin adds a chore from the
  Admin panel; members never see the Admin tab; the sign-back-in dialog matches a member by
  nickname and signs in as the existing member without duplicating them (gate off) and
  raises a pending request instead of signing in (gate on); a first-time join with the
  gate on raises a request instead of creating a member; deleting the home needs the code typed correctly and
  signs the admin out; a member logs other help from the board and it waits uncounted, while an
  admin accepts it (counted) and promotes it to a chore, or declines it (no chore added); an
  admin logs a chore for another member from the Admin tab; an admin creates a chore group and the
  board shows it as a heading over its own grid while a home with no groups still renders one
  unheaded grid; moving a chore up reorders the board; the board is `draggable` for the admin
  only, with ids on chore cards and not on the 🙋/＋ tiles, and a drop handled by `ChoresPanel`
  reorders the board for an admin and is thrown away for a member or when its ids belong to
  another home; a chore's 💎 value shows on
  its card and only there; the 💎 card on My stats appears once a diamond was earned and is not a
  picker; the 💎 redeemed card appears once something was redeemed, with earned · redeemed ·
  balance as plain tiles and the reward note in the list, and the admin's Home stats list the same
  row with the member's name, a blank note reads "(no note)" and past ten rows the card counts
  the ones it left out; the admin's Home stats fold each member's own chores and keep an open fold open across
  a redraw; the trophy shelf on My stats lists all eight rungs as non-focusable list items with
  the earned one tinted, the next outlined and a progress bar saying 7 of 10; a trophy earned
  while away opens on the board once, leads to the shelf, and is not shown on the next visit,
  and three at once share one "3 new trophies!" dialog; the leaderboard chip wears the best
  trophy with the label "Trophy: Helper"; the fourth tap says "Just 1 more to 🌱 Sprout (+7 💎)!"
  and the fifth unveils the trophy with its reward; an admin's 💎 field for a rung saves the
  reward; and the session lifetime is the member's until the PIN is entered, and the admin's
  from then on. `LoadingOverlayUiTest`: the loading card gets its text in the UI's language
  (Finnish, English, Swedish) from the same key as the landing page's restore card, appears at
  the indicator's second stage set to 1.5 s with the thin bar still first, and every new UI is
  configured by the init listener. The card itself — covering the page at 1.5 s, yielding to the
  connection-lost message — is browser behaviour, verified by hand in the browser pane.
- **Statistics** (`StatsServiceTest`): my/home aggregates; 💎 earned follows an undo and ignores
  redemptions, bucketed by the day the credit landed; 💎 redeemed lists the member's redemptions
  newest first with the note as the reward and a blank note as none, and earned − redeemed is the
  balance; the list is capped at ten with the hidden rows counted; the home's list names who
  redeemed what, never shows another home's redemptions, and is empty for a home that never
  redeems; each member's own bars in join order with idle members skipped.
- **Statistics periods** (`StatsPeriodTest`): yesterday counts in the week but not in today; the
  week starts Monday, matching the chore-master badge; REJECTED counts in no period; a
  **late-approved completion buckets by when it was done, not when it was approved**; the headline
  tiles stay all-time under every lens; trends are zero-filled and end with the current bucket; a
  chore done last month lands in its own column; home bars follow the lens while the headline does
  not; and **axis labels stay narrow in every language** — the case Finnish broke.
- **Push delivery** (`WebPushSenderTest`, against a local HTTP server standing in for the push
  service): a service that accepts the connection and never answers holds the sweep no longer
  than the deadline; an exchange that overruns the deadline is given up on; 2xx is delivered,
  404/410 is gone, 5xx keeps the row, and the request carries the VAPID signature, `aes128gcm`
  and a TTL; `send` drops only a malformed endpoint and keeps one it will not connect to.
- **Push subscriptions** (`PushReminderServiceTest`): a sixth device evicts the oldest;
  re-subscribing from another member's browser moves the row to them; unsubscribing touches
  only the member's own row. `ChoreReminderServiceTest`: one member with more devices than the
  sweep cap is sent to no more than the cap.
- **Admin actions re-checked at action time** (`RejoinServiceTest`, `ChoreServiceTest`,
  `LogForMemberTest`, `CreditServiceTest`, `BackupServiceTest`, `AddMemberTest`): a member of
  the home and an admin of another home can neither decide a request, approve or reject, log a
  chore for someone, redeem, restore, nor add a member without a device; redeeming for a member
  who is not in the home is refused too.
  `HomeUiTest`: a demoted admin's already-open rename dialog saves nothing.
- **A member without a device** (`AddMemberTest`): added trimmed, non-admin, with no device and
  the agreement stamped, and an admin can log a chore for them; a blank or already-taken
  nickname (case-insensitive) is refused; and the sign-back-in by nickname with the PIN later
  gives them a device. `HomeUiTest`: the Members card's dialog adds them only once the agreement
  is ticked, and then shows "no phone yet" under their name; a name of spaces keeps Add
  disabled, and a taken one is refused naming it.
- **The shared device** (`SharedDeviceTest`): pairs with the code and PIN and the secret proves
  it (a wrong or blank one does not); a wrong PIN, an unknown home and a sixth device are
  refused, and a blank name takes the default; wrong pairing guesses lock the PIN for claiming
  too; only an admin of that home unpairs, after which the secret is dead; a completion from the
  device counts for the member picked with their streak lock enforced; a member, chore or device
  from another home is refused for completing, booking, clearing a booking and other help; it
  books for the member picked and clears anyone's booking; other help from it waits for an
  admin; the device's board locks on time only and still names whose turn and whose booking;
  list lines from it have no author, nor may the device change the admin's list settings; another
  home's tablet can neither tick, rename nor delete this home's lines, nor this home's tablet add
  to theirs, and an unpaired device writes nothing even while its home keeps another; and devices are absent from a backup, survive a
  restore and go with the home. `SharedDeviceUiTest`: three tabs, the header badge, no Admin?,
  no Leave, no daily ring; tapping a card asks who, cancelling records nothing, a chip counts for
  that member and the undo strip names them; under enforced rotation the card is not locked and
  a wrong pick is refused in their name (the toast names Milla, not "you") and the right one
  puts Alex on the undo strip; "I'll do it" asks for whom, and a booking — the picked member's or
  one made on a phone — is cleared from the device; other help asks who, refuses a blank entry,
  and waits for an admin in that member's name; the Stats tab picks whose; a list line from the
  device has no author and no bell; an unpaired device is shown out. `HomeUiTest`: pairing with
  the PIN signs the browser in as the device and a wrong PIN marks the field; with no home code
  it asks for one in pairing's own words; a sixth device is refused with where to unpair one;
  the Admin tab lists the devices and unpairing asks first (cancel keeps it).
  `PairingRateLimitUiTest` (limiter on): the eleventh pairing attempt in an hour is refused
  before the PIN is tried, even with the right PIN. `SchemaEvolutionTest`: the table
  is created empty on an upgrade.
- **A rejected stored identity is explained** (`DeviceIdentityTest`): a current value reads as
  an identity, nothing stored is not a failure, and the pre-secret pair, a missing code or
  secret and a non-numeric id read as unreadable; the browser label names the device, OS
  version, browser and major version (and `webview` for an iOS page without the Safari
  token), never the whole string. `HomeUiTest`: the current secret signs straight in with the
  admin's lifetime; a replaced secret, a removed member and an older-version value stay on
  the landing page signed out, with the matching notice and the Join tab in front; nothing
  stored shows the plain card with Create in front; an unknown shared device is told it is
  unpaired; a paired device's own secret signs it in as nobody, while its real id with a
  made-up secret, or its real secret with another home's code, signs nothing in.
  `DeviceIdentityTest`: only the probe's own four answers reach the log — a forged line or a
  megabyte of text reads `unknown`.
- **Stats render cost** (`StatsRenderCostTest`, Hibernate statistics): under the week lens a
  two-year-old completion is counted (all-time total, feedback per chore) but not loaded; the
  all-time lens loads it; the 💎 card loads the headline window and sums the rest.
- **Per-chore reminders** (`ChoreReminderServiceTest`, sender mocked): fires once and the row is
  gone; nothing before its time; arming twice moves it rather than stacking; completing cancels the
  member's own but not another's; an admin logging it for someone cancels theirs; a chore someone
  else did is retired without sending; deleting the chore / member / home clears them; a restore
  drops them; a dead subscription is pruned and the reminder still retired; a member with no
  devices still has it retired; the sweep is capped and the rest go next minute; absurd offsets are
  clamped; and the notification is in the member's own language even if they never used the daily
  reminder. Plus `SnoozeUiTest` (push mocked as configured): a chip on every chore card and none on
  the 🙋/＋ tiles, no chips at all without VAPID keys, the dialog offers every offset, and an armed
  reminder badges its own card and only that one.
- **Shared lists** (`ListItemServiceTest`): writing order per list and clipping; blank lines and
  strangers refused; ticking stamps who and when, unticking clears, nothing becomes a chore; done
  lines hide after a day and the purge reclaims them; clear-done per list; delete for any member
  and no outsider; dinner upsert, inclusive window, weekly purge; every edit bumps the home.
  **Arranging**: a drop lands before its neighbour or at the end, a drop that changes nothing or
  "before itself" says so, a new line still goes last on an arranged list, and ticking plus
  unticking keeps a line's place; a stranger is refused, and another home's line (dragged or as the
  neighbour), a neighbour on another list, a ticked line and a dinner slot all move nothing.
  **Display order**: due reminders lead soonest first, far-off ones trail soonest first, the plain
  lines keep the family's order between them, "tomorrow 9:00" is below the plain lines today and
  above them tomorrow, and with no reminders the display order is the family's order. Plus
  `ListEditUiTest`: tapping the text edits, the swipe tray edits and deletes (and the Undo toast
  opens at the bottom centre), the reminder badge
  opens its time; rows with a reminder sort by due date and carry no id while plain rows do, the
  list is `draggable` for a member, a drop through the panel reorders the rows and the family's
  order, a drop beside an id that is not ours moves nothing, a drop naming another home's line
  (dragged or as the neighbour) moves nothing in either home, a ticked line carries no id and
  sits in a section that is no drop target, and a custom list is arranged the same way but
  never beside a To-do neighbour; ↑/↓ sit on the plain lines only, are disabled at the ends of
  the plain run, and move a line through the same path as a drop. `BackupServiceTest`: an arranged
  list comes back arranged, and a file from before lines could be arranged comes back in writing
  order.
- **List-line reminders** (`ListReminderServiceTest`, sender mocked): fires once to every device
  in the home and is then marked fired rather than deleted, never sent twice; nothing before its
  time; scheduling twice moves the one reminder and anyone in the home may move it; snoozing a
  fired one puts it back to pending with the new time; ticking the line off by anyone or deleting
  it removes it and unticking does not revive it; a line done or gone at fire time is retired
  without sending; a dead subscription is pruned and the reminder still fires; a home with no
  devices has it dropped quietly; deleting the home or restoring a backup drops them and the
  export never holds them; removing a member leaves it; a stranger is refused and a housemate may
  cancel; absurd times are clamped; unanswered for a week is purged; each device is addressed in
  its own member's language; and the quick options land on 9:00 (across midnight, from the 31st,
  across a DST change). Plus `ListReminderUiTest` (push mocked as configured): a bell on every
  open to-do and grocery line and none on ticked lines or dinner slots, none without VAPID keys,
  the dialog offers every quick option plus the picker, an armed reminder badges its line only,
  and a fired reminder opens the dialog on arrival with **Dismiss** clearing it for the home.
- **Message-bundle parity** (`MessageParityTest`): the three properties files carry identical key
  sets, and no parameterized value hides a lone apostrophe. A missing key fails nothing at
  runtime — it simply renders as the key — so it needs a test rather than a reviewer.
- **Response headers** (`SecurityHeadersInitListenerTest`): the one handler sets all four
  headers and returns the request to the normal chain. **Push endpoints**
  (`PushEndpointsTest`): IPv6 unique-local, link-local, loopback and multicast literals are
  private; public IPv6 and IPv4 addresses are not.
- **Trusted proxies** (`TrustedProxiesTest`): the Cloudflare edge regex in
  `application.properties` is read from the production file the way Tomcat will read it (a `.properties` file unescapes
  backslashes) and matches edge addresses but not a visitor or the loopback proxy.
- **Architecture rules** (`ArchitectureTest`, ArchUnit over the compiled main classes): the
  layering `ui → service → domain` with `i18n` a leaf and `maintenance` depended on by nothing;
  no package cycles; `domain` free of in-house and Vaadin dependencies; services free of Vaadin
  except `HomeState` and `WebPushSender`; repositories are `domain` interfaces used only by
  services and the CLI, with derived queries only (`@Query` is allowed for an aggregate — a sum or a group-by — never a row load or `@Modifying`); entities in `domain` with a protected no-arg
  constructor; constructor injection only; Spring's `@Transactional`, never Jakarta's; `@Route`
  in `ui`, `@Service` in `service`, other `@Component`s in `ui` only as `VaadinServiceInitListener`s;
  `UI.getCurrent()`/`VaadinSession` touched only from `ui` (plus the `LocaleInitListener`
  session hook); `ui` helpers package-private
  (`SessionContext` excepted); no toast positioned at the top of the screen (no `TOP_*`
  `Notification.Position`, §4.15.1); no standard streams outside `MaintenanceRunner`; no
  `java.util.logging`. Every rule passed on the day it was written — the suite exists so the
  next change cannot quietly bend the shape.

## 7. Out of scope (possible future work)
- Real authentication/accounts; weekly/monthly leaderboards;
  per-member chore preferences; native mobile apps.
