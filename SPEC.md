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
| Change home settings (approval, rejoin gate, division style, booking hold, daily target, PIN, name) | — | ✅ |
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
  keeps sessions short (Spring's 30-minute default) so idle phones cost it nothing.
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
- **US-39 Abandoned-home retention (operator).** As the operator of a public instance, I
  can have homes that were created and then abandoned *before anyone used them* removed
  automatically, so stray sign-ups don't accumulate. A home qualifies only when it has no
  chore history at all and at most one member. A home a family actually used is never
  auto-deleted however long it sits idle: the app has no email or push channel, so nobody
  could be warned first, and silently destroying a child's chore history is not a trade
  worth making for disk space. Off by default.

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
- A list of all `PENDING` completions for the home, newest first, each with member,
  chore, time, feedback, and **Approve** / **Reject** actions.
- Approve → `APPROVED`, records reviewer/time, triggers milestone/new-chore/credit
  evaluation.
- Reject → `REJECTED` (retained for audit, excluded from all counts and fairness).
- Live-updates when members submit; the Admin tab shows a pending-count badge.

### 4.11 Backup / restore (admin)
- **Backup**: a JSON document `{ version, home, members[], tasks[], completions[],
  spreeTiers[], credits[] }` for this home only (the "family DB"), offered as a
  file download `home-chores-<code>-backup.json`. Tasks include interval, credit
  value and availability windows.
- **Restore**: upload a backup JSON. After a confirmation dialog warning that current
  data will be replaced, the home's data is deleted and recreated from the file, and
  home settings (name, PIN, approval, division style, booking hold, target) are
  applied. IDs are remapped internally; orphaned records are skipped. Invalid files
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
  sessions keep Spring's 30-minute default; local storage, not session length, is what
  keeps a phone signed in.
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
  freed code reusable).
- **Vaadin UI Unit tests** (`vaadin-testbench-unit-junit5`, `SpringUIUnitTest`,
  browserless): create-home flow navigates to the board with three tabs; join flow
  (two tabs, no Admin); PIN claim reveals the Admin tab; admin adds a chore from the
  Admin panel; members never see the Admin tab; the sign-back-in picker signs in as the
  existing member without duplicating them (gate off) and raises a pending request
  instead of signing in (gate on); deleting the home needs the code typed correctly and
  signs the admin out.

## 7. Out of scope (possible future work)
- Real authentication/accounts; weekly/monthly leaderboards; push notifications;
  undo of a single tap; per-member chore preferences; native mobile apps.
