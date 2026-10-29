# From chore board to everyday organizer — ideas and a plan (2026-10-29)

The Lists tab moved FlashChores from "who did the dishes" towards "what is our family doing
this week". This note collects what would serve that use case, ranks it, and sketches the
first piece the family asked for: **a task that repeats on fixed weekdays** ("workout every
Tuesday evening").

**Status (2026-10-29):** idea 1 is built (routines, SPEC §4.4f), with the plan's open questions
decided as below, plus private routines and private lists (SPEC §4.4e), which came up while
building it. The other ideas are not built. Decisions marked **Q** were open when this was written.

- [x] **Q1** Weekly and every other week (anchored to a Monday, "starting this week / next week").
- [x] **Q2** Monthly dates left for later (SPEC §7).
- [x] **Q3** A Routines tab on Lists, plus today's routines at the top of the Chores tab.
- [x] **Q4** One time zone for the whole family (`Home.timeZone`), set from the first board.
- [x] Not in the plan: a routine of one's own can be private, and so can a list one made.
- [ ] The in-app Done / Snooze answer to a routine reminder (the notification just opens the board).

## What the app already has to build on

| Piece | What it gives us |
|---|---|
| Chores with `intervalDays`, seasons, availability hours | "Due again N days after it was last done" — a *floating* schedule. |
| Per-chore ⏰ snooze, list-line reminders, the daily nudge | One-shot Web Push, a sweep on the scheduler thread, answering a reminder in-app (works on iPhone). |
| Shared lists (Groceries, To-do, Dinner week) | Home-wide free text, ticking, a dated 8-day window. |
| Members, shared device, live sync | Who it is for, a counter-top screen, every screen updating at once. |

What is missing is the *fixed* calendar: "every Tuesday", "the 1st of the month", "on 14
November". `intervalDays = 7` is not the same thing — do the workout on Wednesday and the next
one floats to the following Wednesday.

## Ideas, ranked by value for the effort

1. ✅ **Routines: things that repeat on fixed weekdays** (the ask — built). Workout Tue 18:00, piano
   lesson Thu 16:30, recycling bin out Sunday evening, pills every morning. Personal or for the
   whole home. Plan below.
2. **A "Today" agenda on the board** — routines due today, chores due today, today's dinner and
   reminders due today in one short list at the top of the Chores tab (or its own tab). Turns
   three tabs of scattered dates into the one glance a parent makes at 7 am. Cheap once 1 exists.
3. **Chores on fixed weekdays** — the same weekday rule as routines, offered on chores ("bins
   out every Sunday", "sauna on Saturdays"), so they lock/unlock by calendar rather than by
   interval. Reuses 1's rule type; lands in `taskViews` next to seasons and hours.
4. **Family calendar events (one-off dates)** — "dentist 14.11 at 9:15, Milla", "school photo
   day". A To-do line with a date already half-does this; a proper date + member + reminder the
   day before is the step up. Shows in the Today agenda and as a week strip.
5. **Meal plan → groceries** — a Dinner slot can push its ingredients to Groceries with one tap
   ("Tacos" → tortillas, minced meat, salsa), and recurring favourites suggest themselves.
6. **Recurring list lines** — milk and bread that re-add themselves to Groceries every week, or
   "pay the daycare bill" on the 5th of each month on To-do.
7. **Birthdays and name days** — a yearly date per member (Finnish name days are a nice local
   touch), on the agenda a few days ahead.
8. **Pocket money from credits** — a fixed 💎 allowance every Saturday, which the credits ledger
   can already hold (a `CreditType` with a weekly sweep).
9. **Calendar export (iCal feed)** — a secret per-home URL that phones subscribe to, so routines
   and events show in the phone's own calendar. Read-only, no OAuth. Worth it after 1 and 4.

Not recommended for now: two-way Google/Apple calendar sync (OAuth, privacy surface, support
load), chat/messaging (WhatsApp already won), location sharing.

## Plan: routines (fixed-weekday repeating tasks)

### The user stories

- *As a member, I set "Workout" for Tuesdays at 18:00 for myself, get a push at 18:00 each
  Tuesday, and tick it off when done.* If I don't, it shows as missed tomorrow morning, not
  forever.
- *As an admin, I set "Bins out" every Sunday at 19:00 for the whole home*: everyone sees it,
  anyone ticks it.
- *As a parent, I set "Piano" Thursdays 16:30 for Milla* (a member without a phone): it shows on
  the shared device and on my phone.

### Model

A new entity, not a chore: a routine earns no credits, starts no streak and is not part of the
fairness rules — like a list line, it is organisation, not housework.

```
Routine        id, homeCode, name, emoji,
               weekdays (bitmask Mon..Sun), time (LocalTime, optional),
               forMemberId (null = whole home), createdByMemberId,
               remind (boolean), createdAt
RoutineDone    id, routineId, date (LocalDate in the home's zone), doneByMemberId / deviceId
```

- "Due today" = today's weekday is in the mask and there is no `RoutineDone` for today.
  Nothing is pre-generated: occurrences are computed, so a routine that is never ticked leaves
  no rows behind.
- **Q1 Every N weeks?** "Every other Tuesday" needs an anchor date and `(weeks since anchor) % N`.
  Cheap to add to the model now (default N=1), UI later.
- **Q2 Monthly by date** ("the 5th") — same entity with a second rule kind, or leave for
  recurring list lines (idea 6)? Suggest: weekdays only in v1.

### Where it shows

- **Q3** A fourth tab ("Routines"/"Week") or a section on the Lists tab? The bottom tab bar is
  already at four for admins (`.main-tabs` sizing, §4.15.1). Suggest: a **Routines** list on
  the Lists tab beside Groceries / To-do / Dinner, plus the routines due today at the top of
  the Chores tab (the first slice of the Today agenda, idea 2).
- A row reads "🏋️ Workout · Tue 18:00 · Johannes" with a tick; ticked today it reads
  "done ✓ by …" until midnight, like a list line.
- The shared device shows home-wide routines and those of members without a phone, and can
  tick them (author = device, as for list lines, `Author`).

### Reminders

- Reuse the list-reminder machinery: a sweep finds routines whose weekday and time have just
  passed and are not done, and pushes once to their member (or to everyone for a home routine),
  answered in-app with Done / Snooze 1h / Skip today. The push rows are per occurrence, retired
  unconditionally as §4.13c does now.
- Time zone: a routine's time is wall-clock in the **home's** zone (needs a home zone setting —
  today zones are per session). **Q4** per-home zone vs. the creator's zone at save time.

### Permissions and limits

- Any member creates routines for themselves; routines for someone else or for the home: admin
  only (re-checked server-side against the session's member, as for chores).
- Per home cap (say 100) and per member cap (say 30); name `InputLimits.TASK_NAME`.
- Tenant isolation: every `RoutineService` method takes the home code and refuses a routine of
  another home — the pattern `ListItemService` uses with `Author`.

### Data and ops

- New tables only, created by `ddl-auto=update`; no column added to existing rows.
- In the backup (it is family data), with ids remapped on restore; reminder rows excluded.
- Retention: deleted with the home and member like list lines.

### Size and order of work

1. Entity + service + tests (due-today rule across weekdays and zones, isolation, caps).
2. Lists-tab list: add / edit / delete / tick, the shared device.
3. "Due today" strip on the Chores tab.
4. Push reminder + in-app answer.
5. SPEC §3/§4/§6, README, three languages.

Roughly the size of the list reminders (§4.13c) work. Ideas 2 and 3 fall out of it cheaply
afterwards.
