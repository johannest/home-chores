# FlashChores — Specification & User Stories

Status: living document. Covers the original (Phase 1) features, the admin/stats
iteration (Phase 2), and the scheduling / rewards / i18n / PWA iteration (Phase 3).
Terms in **bold** map to concepts in the code.

## 1. Glossary

- **Home** — a household. Identified by a short, shareable **home code**
  (7 characters from an alphabet without I/O/0/1).
- **Member** — a person in a home. Identified per device (a phone = a member): the member
  id is kept in the browser's **local storage** and restored into each new session.
- **Rejoin request** — a device asking to sign back in as an existing member after losing
  its stored identity (browsing data cleared). Held as `PENDING` / `APPROVED` / `REJECTED`
  with a secret **device token** that only the requesting browser holds.
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
| Log **other help** the board has no card for | ✅ | ✅ |
| Accept / decline other help, and set its reward | — | ✅ |
| Turn accepted other help into a new chore | — | ✅ |
| Copy/share the home code and join link | ✅ | ✅ |
| Switch UI language | ✅ | ✅ |
| See own statistics | ✅ | ✅ |
| See home-wide statistics | — | ✅ |
| Add / edit / delete chores (incl. interval, hours, credits) | — | ✅ |
| Approve / reject pending completions | — | ✅ |
| Delete / correct any completion | — | ✅ |
| Rename / remove members, promote / demote admins | — | ✅ |
| Ask to sign back in as an existing member | ✅ | ✅ |
| Approve / reject a rejoin request | — | ✅ |
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
- **US-02 Join a home.** As a family member, I can join with a home code and my
  name, so that I share the same chore board.
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
- **US-22 Booking timeout.** As an admin, I can configure how long a booking holds
  (1/2/3/4/6/8/12/24 hours, default 4). Expired bookings free the chore.
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
  me, I can enter the home code, pick myself from the home's member list, and get my
  own record back — chores, credits, streaks and admin role intact — instead of joining
  again as a duplicate person. If the home requires it, an admin approves my request
  first; the waiting screen flips to the board the moment they do, live.
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
  chore history at all and at most one member. A home a family actually used is never
  auto-deleted however long it sits idle: the app has no email or push channel, so nobody
  could be warned first, and silently destroying a child's chore history is not a trade
  worth making for disk space. Off by default.

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

- **US-47 Log a chore for someone (admin).** As an admin, I can record a chore on another
  member's behalf — the child who has no phone of their own, or the one who did it and forgot
  to tap. I pick who and which chore in the Admin tab and it counts for them straight away:
  my logging it *is* the approval, and the history keeps my name as the reviewer. It goes in
  regardless of the booking, streak, interval, hours and rotation locks, because those steer
  who does what *next* and this is a statement about what already happened. If I get it wrong,
  the unmark list takes it back like any other completion.
- **US-48 Sessions that don't outlive the moment.** As a family, our phones hold no server
  session while nobody is using them: a member's session lasts 3 minutes of inactivity and an
  admin's 15, and expiry is invisible — the page returns itself to the board using the
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
- **Device identity** (`DeviceIdentity`): on sign-in the pair `memberId|homeCode` is
  written to the browser's local storage under `flashchores.identity`, and re-stamped on
  every visit to the board. The landing page reads it on attach, validates that both the
  member and the home still exist, and signs the session in — so server sessions stay
  short-lived and hold no state for phones that aren't actively using the app. A stale
  entry (removed member, restored home) is cleared and the user sent to the landing page.
  While the browser is being asked, a full-screen overlay covers the landing card; it's
  an overlay rather than a hidden form so that a lookup which never answers leaves a
  working page rather than a spinner.
- **Leave**, self-removal and backup restore all clear the stored identity.

#### 4.1.1 Signing back in (rejoin)
Clearing browsing data is the one thing local storage doesn't survive. The Join tab
therefore offers "I'm already a member — sign me back in":
1. The member enters the home code and opens the picker, which lists the home's members.
2. Picking a member calls `requestRejoin(code, memberId, pin)`, which returns:
   - `SIGNED_IN` — the supplied PIN matched the home's admin PIN, or
     `Home.approveRejoin` is off. The device signs in immediately.
   - `PENDING` — a `RejoinRequest` is created and its **device token** returned; the
     browser stores it under `flashchores.rejoin` and shows a waiting screen.
   - `WRONG_PIN` / `UNKNOWN` — rejected with a message; nothing is recorded.
3. Admins see pending requests at the top of the Admin tab (and in its badge) and
   Approve / Reject each. The decision bumps the home revision, so the waiting device
   picks it up over push and navigates straight to the board — no polling or reload.
4. A device that reloaded or closed in the meantime re-checks its stored token on the
   next visit, so an approval granted while it was away still lands. Approved requests
   are **consumed** on sign-in so a token can't be replayed; re-requesting for the same
   member deletes any older pending request, so only the newest device can be let in.
- The PIN is a *bypass*, not a promotion: the member keeps whatever role their existing
  record has, and "Admin?" in the header remains the way to claim admin.
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

- **3 minutes for a member, 15 for an admin** (`SessionContext.MEMBER_TIMEOUT_SECONDS` /
  `ADMIN_TIMEOUT_SECONDS`, applied per session with `WrappedSession.setMaxInactiveInterval`).
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
- The booker can cancel; cards show "🔖 You" / "🔖 <name>". Available only in
  free-for-all division and only for currently-due chores.

### 4.5 Rotating division
- `Home.divisionStyle` ∈ {`DEFAULT`, `ROTATING`}; `Home.rotationEnforced`
  (default true) applies in rotating mode.
- Assignment: with members ordered by join time and chores by creation time,
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
- **My stats** (any member): total approved; bar chart of my chores by type; my
  feedback split (hate/ok/love); 7-day adherence (done vs target per day).
- **Home stats** (admin): bar chart of approved completions per member; chore
  popularity (completions per chore); feedback per chore (hate–ok–love split);
  14-day activity trend; per-member adherence today.

### 4.10 Approvals (admin)
- A list of all `PENDING` chore completions for the home, newest first, each with member,
  chore, time, feedback, and **Approve** / **Reject** actions. Other help is excluded here
  and listed separately (§4.3.2), though the Admin tab's badge counts both.
- Approve → `APPROVED`, records reviewer/time, triggers milestone/new-chore/credit
  evaluation.
- Reject → `REJECTED` (retained for audit, excluded from all counts and fairness).
- Live-updates when members submit; the Admin tab shows a pending-count badge.

### 4.11 Backup / restore (admin)
- **Backup**: a JSON document `{ version, home, members[], tasks[], completions[],
  spreeTiers[], credits[] }` for this home only (the "family DB"), offered as a
  file download `home-chores-<code>-backup.json`. Tasks include interval, credit
  value and availability windows; completions include the other-help `note`.
- **Restore**: upload a backup JSON. After a confirmation dialog warning that current
  data will be replaced, the home's data is deleted and recreated from the file, and
  home settings (name, PIN, approval, division style, booking hold, target) are
  applied. IDs are remapped internally; orphaned records are skipped — an other-help entry
  has no task to remap and is not an orphan. Invalid files
  are rejected with a message. The restoring admin is signed out and rejoins.

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
- **Always `PENDING`**, even when `requireApproval` is off: the text is freeform and there is
  no chore behind it, so it counts for nobody until an admin has read it. `logOtherHelp`
  returns empty when the home has the feature off or the text is blank.
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
- Deletion reuses `ChoreService.deleteHome`, so the cascade across all seven tables stays
  in one place, and each sweep logs the codes it removed.
- **Not implemented, deliberately**: time-based deletion of homes that *were* used. With no
  email or push channel there is no way to warn a family first, and a false positive
  destroys irreplaceable history while a false negative costs kilobytes. If it is ever
  added it should export a backup before deleting, and use a window of months, not days.
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
- **No hand-written SQL.** Deletion calls `ChoreService.deleteHome`, so the seven-table
  cascade lives in one tested place; SQL by hand would risk orphaned rows.
- **Requires the service stopped**, because H2 holds an exclusive file lock. The script
  probes the port and refuses with an instruction rather than failing part-way.
- Commands: `list`, `show`, `export`, `delete`, `purge --days N [--dry-run]`. `delete`
  exports a backup to `data/erasure-exports/` first (unless `--no-backup`) and requires the
  home code to be typed, mirroring the in-app Danger zone. `--db-url` targets another
  database, e.g. a restored copy. Exit codes: 0 success, 2 handled failure.

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
  `max(<pad>, env(safe-area-inset-*))` to clear the notch and home indicator, and heights
  use `100dvh` (with a `100vh` fallback) to track iOS Safari's collapsing URL bar.
- **Header (≤640px).** The identity block and the action cluster each take a full row
  rather than pushing each other off-screen; the home name is one ellipsised line. Copy
  and Share collapse to icons — their glyphs are self-explanatory and they sit beside the
  code chip — while "Admin?" and "Leave" keep their text, since a bare key or exit icon is
  ambiguous. The room comes from narrowing the language select instead. Collapsed labels
  stay in `aria-label`.
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
- **Erasure requests.** Self-service is the primary route and is already complete: an admin
  can erase one member (Admin → Members) or the whole home (Admin → Danger zone), and
  export first via Backup & restore. For operator-assisted requests the notice asks for the
  **home code plus admin PIN** — with no stored email or account there is nothing else to
  authenticate a request against, and the code alone would let anyone have a family's board
  deleted. The retention paragraph is generated from the live configuration (§4.11.2).

## 5. Non-functional / technical
- Vaadin 25.2 Flow + Spring Boot 4.1 (Java 21), H2 file DB (`data/`), server push
  (long-polling) + Vaadin Signals, installable PWA.
- Charts, confetti and icons are self-contained (no external CDN, no licensed
  components).
- **Time zones**: availability hours are evaluated in the member's browser time
  zone (per session, server zone as fallback); interval due-dates, daily targets
  and spree streaks use the server's zone.
- The admin PIN is a lightweight household credential, not a security boundary
  against a determined attacker; it gates casual misuse only. Codes are long enough
  not to be guessable; HTTPS is expected in production.
- **Identity storage**: the browser holds only `memberId|homeCode` and, while a rejoin is
  pending, a random 128-bit device token — no personal data and no credential. Server
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
  freed code reusable), and other help (pending regardless of the approval setting, accept
  counts it and awards the named credits, decline leaves it uncounted, separate queues but one
  badge count, blank/switched-off records nothing, over-long text trimmed, member's own undo,
  stats counted apart from chores, backup round-trip keeping the note and the setting), and
  logging a chore for someone (counts for them and not the admin, immediate even with approval
  on, admin recorded as reviewer, goes in despite the streak lock and before an interval chore
  is due, credits to the member who did it, clears a booking, unmarkable, cross-home refused).
- **Vaadin UI Unit tests** (`vaadin-testbench-unit-junit5`, `SpringUIUnitTest`,
  browserless): create-home flow navigates to the board with three tabs; join flow
  (two tabs, no Admin); PIN claim reveals the Admin tab; admin adds a chore from the
  Admin panel; members never see the Admin tab; the sign-back-in picker signs in as the
  existing member without duplicating them (gate off) and raises a pending request
  instead of signing in (gate on); deleting the home needs the code typed correctly and
  signs the admin out; a member logs other help from the board and it waits uncounted, while an
  admin accepts it (counted) and promotes it to a chore, or declines it (no chore added); an
  admin logs a chore for another member from the Admin tab; and the session lifetime is the
  member's until the PIN is entered, and the admin's from then on.

## 7. Out of scope (possible future work)
- Real authentication/accounts; weekly/monthly leaderboards; push notifications;
  undo of a single tap; per-member chore preferences; native mobile apps.
