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
- **Credit** — reward points (💎). Earned from a chore's credit value or a **spree
  tier** (X consecutive days → Y credits); spent via an admin **redemption**.
- **Join link** — `/?join=CODE`; opens the landing page with the Join tab
  preselected and the code prefilled.

## 2. Roles & permissions

| Capability | Member | Admin |
|---|:--:|:--:|
| Join a home, complete chores, give feedback | ✅ | ✅ |
| Book a chore ("I'll do it") / cancel own booking | ✅ | ✅ |
| Snooze a chore ("remind me later" — a one-shot push about that chore) | ✅ | ✅ |
| Choose colour scheme and palette (per device) | ✅ | ✅ |
| Log **other help** the board has no card for | ✅ | ✅ |
| Log own other help so it counts at once, naming its reward | — | ✅ |
| Accept / decline other help, and set its reward | — | ✅ |
| Turn accepted other help into a new chore | — | ✅ |
| Copy/share the home code and join link | ✅ | ✅ |
| Switch UI language | ✅ | ✅ |
| See own statistics | ✅ | ✅ |
| See home-wide statistics | — | ✅ |
| Add / edit / delete chores (incl. interval, hours, credits) | — | ✅ |
| Create / rename / delete chore groups; reorder chores and groups | — | ✅ |
| Approve / reject pending completions | — | ✅ |
| Delete / correct any completion | — | ✅ |
| Rename / remove members, promote / demote admins | — | ✅ |
| Ask to sign back in as an existing member | ✅ | ✅ |
| Approve / reject a join or rejoin request | — | ✅ |
| Change home settings (approval, rejoin gate, division style, booking hold, daily target, other help, PIN, name) | — | ✅ |
| Manage spree tiers, view balances, redeem credits | — | ✅ |
| Backup / restore the family's data | — | ✅ |
| Delete the home and all its data | — | ✅ |

Non-admins have **read/complete** rights only; all create/update/delete of shared
config is admin-only — that is the "admin has CRUD over everything" requirement.

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
  **3 times in a row**; the 4th tap is blocked until someone else does that chore.
- **US-07 Celebrations.** As a member, I see confetti on completion, a "new chore
  unlocked" popup the first time I do a chore, and milestone trophies at
  5/10/25/50/100/250 personal chores.
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
  completions count toward stats, leaderboard and milestones; rejected ones don't.
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

- **US-50 Arrange the board.** As an admin, I can move a chore up or down so the board reads in
  the order my family actually works through it, instead of the order the chores happened to be
  created in. Arrows rather than dragging, because the board lives on phones. Rearranging is
  purely cosmetic: it never changes who the rotation assigns what to (§4.5), and a home that
  never touches it keeps exactly the order it had.
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
  stale or unverifiable entry (removed member, restored home, wrong secret) is cleared and
  the user sent to the landing page. While the browser is being asked, a full-screen
  overlay covers the landing card, so a lookup that never answers leaves a working page.
- **Leave**, self-removal and backup restore all clear the stored identity.

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
  Admin tab. Move up/down rather than drag-and-drop: the app is phone-first and HTML5 drag events
  do not fire on mobile touch. Arrows are disabled at the ends of a bucket, not hidden, so rows
  do not reflow as things move.
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
   first, count the leading run by one member. If that member's run ≥ 3, they are
   locked out until a different member does it. Pending completions count toward
   the run (so approval can't be gamed).

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

### 4.6 Counting, milestones, leaderboard
- A member's **count** = number of their `APPROVED` completions.
- **Milestones** (5/10/25/50/100/250) evaluated on the member's approved count at the
  moment a completion becomes approved.
- **new-chore** achievement = first `APPROVED` completion of that chore by that member.
- Leaderboard shows each member with their approved count and 💎 credit balance
  (when > 0); admins are badged 👑.
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
  pending > milestone > new-chore > daily tier. Pending completions keep the neutral ⏳
  dialog — praise waits until the chore actually counts.

### 4.7 Credits & rewards
- **Earning**: when a completion becomes `APPROVED`:
  - the chore's `creditValue` (if > 0) is credited;
  - spree check: the member's streak of consecutive days with ≥ 1 approved
    completion (server dates, ending today) is computed; if it exactly equals a
    tier's `days` and that tier hasn't been awarded for this streak already, the
    tier's credits are awarded. Celebration toasts announce both.
- **Spree tiers** (admin): list of `days → credits` rows, addable/deletable.
- **Balance** = earned − redeemed, per member.
- **Redemption** (admin): amount 1..balance with an optional note; recorded as a
  credit entry so history is auditable. Over-balance redemptions are rejected.

### 4.8 Daily target (per member)
- `Home.dailyTargetPerMember` ∈ [1,3], default 1.
- "Done today" = a member's `APPROVED` completions with `doneAt` on the server's
  current date. Shown as `done/target` with a progress ring; reaching the target is
  highlighted.

### 4.9 Statistics & charts
- Rendered with a small dependency-free SVG/CSS **BarChart** (no commercial add-on).
- **Periods.** A chip row picks a lens — **Today / This week / This month / All time** — above a
  row of four tiles showing all four counts at once. The tiles are deliberately *not* filtered:
  the question is a comparison ("what did I do today, this week, this month"), and a row showing
  only the selected period would answer a quarter of it per tap. Weeks run Monday–Sunday, the
  same week `lastWeekChoreMaster` uses; months are calendar months; both use the server zone.
- **What the lens narrows**: the per-chore bars, the feedback split and (home) the per-member and
  popularity bars. What it never narrows: the headline tiles, the trends, today's adherence, and
  feedback-per-chore — "which chore does this family hate" needs more than a week of reactions.
- **Bucketing is by `doneAt`**, so a completion approved days late still belongs to the day it
  was *done*. Approval never moves a row between periods.
- **Cost.** The period is applied inside the single pass that already loads the working set, and
  the longer trends are derived from the same per-day map. Bounding the query per lens instead
  would be one round trip per chip against a `doneAt` with no index behind it — more work, to
  avoid a comparison per row already in memory, and it would cost the "one query for the working
  set" property `BoardRenderCostTest` pins.
- **My stats** (any member): total approved; the period tiles; bar chart of my chores by type
  under the lens; my feedback split; 7-day adherence; **12-week and 12-month trends**.
- **Home stats** (admin): per-member and chore-popularity bars under the lens; feedback per chore;
  14-day activity; **12-week and 12-month trends**; per-member adherence today.
- **Twelve columns is the cap** (`StatsService.TREND_WEEKS`): that is what a ~360px phone column
  fits at a readable width, and the page must never scroll sideways. The axis carries digits —
  `15.6` for a week, the month *number* for a month — with the readable form in the tooltip,
  because a localized short month ("marrask.") is half again wider than its column and
  `overflow-x: hidden` would clip it rather than reveal it (§4.15.1).
- The lens lives in a field on `StatsPanel`, so it survives every `HomeState` rebuild and resets
  on navigation. Tapping a chip re-renders that panel only and **never bumps `HomeState`** —
  which period one member is looking at is not the family's business, the same rule the board's
  filter chips follow.

### 4.10 Approvals (admin)
- A list of all `PENDING` chore completions for the home, newest first, each with member,
  chore, time, feedback, and **Approve** / **Reject** actions. Other help is excluded here
  and listed separately (§4.3.2), though the Admin tab's badge counts both.
- Approve → `APPROVED`, records reviewer/time, triggers milestone/new-chore/credit
  evaluation.
- Reject → `REJECTED` (retained for audit, excluded from all counts and fairness).
- Live-updates when members submit; the Admin tab shows a pending-count badge.

### 4.11 Backup / restore (admin)
- **Backup**: a JSON document `{ version, home, members[], groups[], tasks[], completions[],
  spreeTiers[], credits[] }` for this home only (the "family DB"), offered as a
  file download `home-chores-<code>-backup.json`. Tasks include interval, credit
  value and availability windows; completions include the other-help `note`.
- **Restore**: upload a backup JSON. After a confirmation dialog warning that current
  data will be replaced, the home's data is deleted and recreated from the file, and
  home settings (name, PIN, approval, division style, booking hold, target) are
  applied. **The file's home code must match the admin's own home** — an admin can only
  restore over their own home, never another family's. IDs are remapped internally;
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
  day; `Member.lastRemindedOn` makes it once per day and restart-safe. Sending uses
  Vaadin's `flow-webpush`; Vaadin's generated service worker already handles the `push`
  and `notificationclick` events. Subscriptions (`PushSubscription`) are per device,
  pruned when the push service reports 404/410, deleted with their member/home, and
  excluded from backups. On iOS this requires the PWA installed to the Home Screen
  (16.4+); the dialog says so.

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
  the admin, so the history shows whose word it was. Credits, milestones, the daily target and
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
- Deletion reuses `ChoreService.deleteHome`, so the cascade across all ten tables
  — rejoin requests, push subscriptions, chore reminders, completions, credits, spree tiers,
  chores, chore groups, members, the home row — stays in one place; sweeps log counts only, since a home
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
  collapsing URL bar. The fixed `.restore-overlay` pads its own top the same way.
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
  store" claim reads as more absolute than the deployment can honour.
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
- Vaadin 25.2 Flow + Spring Boot 4.1 (Java 21), H2 file DB (`data/`), server push
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
  loopback and expects a TLS-terminating reverse proxy — enforce HTTPS + HSTS and a Secure
  session cookie there, since nothing in the app itself does.
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
  approval on/off effect on counts & milestones, feedback, daily target, booking
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
  moves, and groups round-tripping a backup (including a pre-groups file restoring ungrouped).
- **Vaadin UI Unit tests** (`vaadin-testbench-unit-junit5`, `SpringUIUnitTest`,
  browserless): create-home flow navigates to the board with three tabs; join flow
  (two tabs, no Admin); PIN claim reveals the Admin tab; admin adds a chore from the
  Admin panel; members never see the Admin tab; the sign-back-in dialog matches a member by
  nickname and signs in as the existing member without duplicating them (gate off) and
  raises a pending request instead of signing in (gate on); a first-time join with the
  gate on raises a request instead of creating a member; deleting the home needs the code typed correctly and
  signs the admin out; a member logs other help from the board and it waits uncounted, while an
  admin accepts it (counted) and promotes it to a chore, or declines it (no chore added); an
  admin logs a chore for another member from the Admin tab; an admin creates a chore group and the
  board shows it as a heading over its own grid while a home with no groups still renders one
  unheaded grid; moving a chore up reorders the board; and the session lifetime is the member's
  until the PIN is entered, and the admin's from then on.
- **Statistics periods** (`StatsPeriodTest`): yesterday counts in the week but not in today; the
  week starts Monday, matching the chore-master badge; REJECTED counts in no period; a
  **late-approved completion buckets by when it was done, not when it was approved**; the headline
  tiles stay all-time under every lens; trends are zero-filled and end with the current bucket; a
  chore done last month lands in its own column; home bars follow the lens while the headline does
  not; and **axis labels stay narrow in every language** — the case Finnish broke.
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
- **Message-bundle parity** (`MessageParityTest`): the three properties files carry identical key
  sets, and no parameterized value hides a lone apostrophe. A missing key fails nothing at
  runtime — it simply renders as the key — so it needs a test rather than a reviewer.

## 7. Out of scope (possible future work)
- Real authentication/accounts; weekly/monthly leaderboards;
  per-member chore preferences; native mobile apps.
