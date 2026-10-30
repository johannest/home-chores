# Code review — 2026-10-30

Scope: the open PR #19 `sound-and-encryption` (commits a152a5f "Add sound and vibration for
completions, refusals and ticks" and 9c6e399 "Encrypt the H2 database file at rest when
FLASHCHORES_DB_KEY is set", `git diff origin/main..HEAD`), and the three PRs merged since the
last review: #18 `list-fixes` (2b52211..8392000), #17 `routines` (f6f8517..2b52211) and #16
`starter-chores` (c09a888..f6f8517). Generated and vendored files excluded. Run from the
personas in `CLAUDE.md`; #17 and #16 were reviewed by delegated readers and every Should-fix
of theirs was re-read in the code before it was kept. Each finding says whether it was
**confirmed** (probed, grepped or run) or is **plausible** (read only). Line numbers are as of
9c6e399.

Tests run for this review (before the fixes) (`mvn -q test -Dtest=…`, Java 25): `EffectsUiTest` 5,
`DatabaseEncryptionTest` 7, `EncryptDatabaseTest` 4, `ArchitectureTest` 18,
`BoardRenderCostTest` 5; the #17 classes (`RoutineTest`, `RoutineServiceTest`, `RoutineUiTest`,
`PrivateListServiceTest`, `ListEditUiTest`) 54; the #16 classes (`ChoreCatalogTest`,
`ChoreFeaturesTest`, `HomeUiTest`, `SharedDeviceTest`, `SharedDeviceUiTest`) 131. **0 failures.**

## Status — 30 Oct 2026

All eleven Should-fix items were fixed the same day, on the `sound-and-encryption` branch, with
N-3 and N-4 alongside them; each ticked item below says what changed and which test pins it.
The nits stay open.

## Verdict

No blocker in PR #19: tenant isolation is untouched (no id in the diff comes from the client),
the three language files are translated, the actuator exposes only prometheus / vaadin / health
so the new property never shows in `/env`, the H2 scope change pulls nothing new in, and the
SPEC numbers (16 characters, 500 ms, 12 % gain, G4 → E♭4) match the code. Five things to fix
before or soon after merge; the sharpest is that the conversion tool, run against a database
that is still open, restores a stale copy over the live file. The merged PRs #17 and #16 each
left one confirmed service-layer or restore gap worth a follow-up.

## PR #19 — sound and vibration; encryption at rest

### Should fix

- [x] **SF-1 (Security / availability) A failed conversion against an open database loses
  data.** `src/main/java/com/homechores/maintenance/EncryptDatabase.java:94` copies the file
  aside before anything checks that the database is closed, and on *any* failure moves the copy
  back over the file. With the service running, H2 refuses the conversion ("Cannot change the
  setting encryption when the database is already open", or the lock from another process), the
  stale copy replaces the live file, and everything the service wrote after the copy is gone on
  its next restart — the service keeps writing to the unlinked inode until then. **Confirmed**
  with a scratch probe: a row inserted through the open connection after the failed `encrypt`
  was missing when the file was reopened. The wrapper's port probe
  (`tools/flashchores-admin.py:511`) covers the normal path; `--force`, `--port`, a service on
  another port, or running the main class directly do not. Fix: check the lock *before* copying,
  and move the copy back only once `ChangeFileEncryption` has actually started. *Fixed the same day*: `EncryptDatabase.requireClosed` runs H2's own in-use check (`FileLister.tryUnlockDatabase`: the lock file and the lock the store holds on the file) *before* the copy exists, and refuses with "is open. Stop the service first"; a copy that cannot be put back is added as a suppressed exception instead of replacing the cause (N-3). `EncryptDatabaseTest.anOpenDatabase_isRefusedBeforeAnyCopyIsMade_andWhatItWritesMeanwhileSurvives` pins it, including the row written through the open connection afterwards.
- [x] **SF-2 (Security, operator trap) `rekey-db` can silently keep the old key.**
  `tools/flashchores-admin.py:403` takes the *new* key from `FLASHCHORES_DB_KEY` whenever it is
  set. An operator whose shell carries the service's current key is prompted for "Current key"
  and the new key is the same value: a rotation they believe happened did not. **Confirmed** by
  reading. Refuse `from.equals(to)` in `EncryptDatabase.run`, or always prompt for the new key. *Fixed the same day*: `rekey` refuses a new key equal to the old one (`EncryptDatabaseTest.rekeyingToTheCurrentKey_isRefused_andChangesNothing`); SPEC §4.11.4 and the README say so.
- [x] **SF-3 (Security, docs) The threat model omits plaintext remnants on disk.** H2 writes a new
  file and deletes the old, and the `.before-encrypt` copy goes with `Files.delete`; neither is a
  wipe, so a disk image taken after encrypting can still carry the plaintext until the blocks are
  reused. SPEC §4.11.4 "What it does not" and the README runbook should say so. *Plausible*
  (filesystem behaviour), one sentence each. *Fixed the same day*: SPEC §4.11.4 "What it does not" and the README runbook now name the plaintext left in freed disk blocks, and the runbook says what a `.before-<command>` copy left by a crash is and what to do with it (N-4).
- [x] **SF-4 (QA) Three SPEC §4.12c claims have no test.** A completion waiting for approval sends
  `tick` (`src/main/java/com/homechores/ui/Celebrations.java:102`), a spree sends `big` and
  outranks the chore's own effect (`Celebrations.java:165`), and unseen trophies arriving together
  send one `big` (`Celebrations.java:232`). `EffectsUiTest` covers none of them. **Confirmed.** *Fixed the same day*: three tests in `EffectsUiTest`: a completion waiting for approval sends `tick` and no confetti, a spree sends one `big` over the chore's own burst, three trophies earned while away arrive with one `big`. SPEC §6 lists them.
- [x] **SF-5 (PM) The committed `dev.bundle` does not contain `effects.js`** (0 matches in
  `git show HEAD:src/main/bundles/dev.bundle`); the rebuilt one with it sits uncommitted in the
  working tree. Commit it, or leave it out knowingly. **Confirmed.** *Fixed the same day*: the rebuilt `dev.bundle` is committed with this change.

### Nits

- [ ] **N-1 (UX, plausible)** On iPhone the preview chime when switching Sounds *on* is dropped:
  `__setEffect` plays outside the gesture, the context is created suspended, and the click's
  `unlock` ran while the stored choice was still off (`src/main/frontend/effects.js:144`). Later
  taps unlock normally. Not tested on a device.
- [ ] **N-2 (UX)** The unseen-trophies dialog arrives on a push redraw with no gesture, so Chrome
  blocks `navigator.vibrate` and logs an intervention warning. Gate it on
  `navigator.userActivation?.hasBeenActive` (`effects.js:125`).
- [x] **N-3 (OCD)** If moving the copy back fails, the move's exception replaces the real cause and
  `main` still prints "the database is unchanged" (`EncryptDatabase.java:101`, `:44`). Add the
  original as suppressed and drop the claim.
- [x] **N-4 (Operator)** The runbook does not say what a `homechores.mv.db.before-<command>` left
  behind by a crash means, or which of the two files to keep.
- [ ] **N-5 (QA)** The `"sound|vibration|can-vibrate"` parsing that hides the Vibration switch on
  iPhones (`src/main/java/com/homechores/ui/EffectsItems.java:58`) is unreachable from UI tests
  because `.then` never fires there. A static `parse` would let the iPhone case be pinned.
- [ ] **N-6 (OCD)** `DatabaseEncryption.databaseFile` (`src/main/java/com/homechores/DatabaseEncryption.java:127`)
  does not resolve H2's `~/` URL form, so the friendly refusals fall back to H2's own errors there.
  No such URL exists in the repo.

## PR #18 — list drag reaches the ends; routine inset; Finnish typos

Clean: the centre-of-card rule in `hold-drag.js` is as SPEC §4.2b describes it, the clamp applies
to lists only (axis `y`) by design, §6 records the manual verification, and the commit message
covers the flaky-test fix.

- [ ] **N-7 (OCD)** `src/test/java/com/homechores/ui/SharedDeviceUiTest.java:119` repeats the
  time-window clearing from `src/test/java/com/homechores/service/ChoreFeaturesTest.java:55`. A
  helper in `HomeChoresUiTest` would hold it once. **Confirmed.**

## PR #17 — routines on fixed weekdays; private lists and routines

Tenant isolation held on every `RoutineService` entry point and every `ListItemService` read and
write (viewer taken, line's list checked); the browser's zone is validated with `ZoneId.of`
before it is stored; the sweep is bounded, orphan-safe and DST-safe. SPEC, README, docs and the
42 new keys in three languages are in step.

### Should fix

- [x] **SF-6 (Security) Restore leaks private lines onto the shared To-do** when the owner *is*
  present but the list is skipped for a blank name or the ten-list cap.
  `src/main/java/com/homechores/service/BackupService.java:377` records only lists whose owner is
  missing in `droppedPrivate`, so the skipped list's lines resolve to no list at
  `BackupService.java:431` and land on To-do. **Confirmed** by reading; no test. *Fixed the same day*: `BackupService` records *every* private list it does not bring back in `droppedPrivate` — owner missing, blank name or over the cap — so its lines go with it. `PrivateListServiceTest` adds the blank-name case with the owner present; SPEC §4.4e/§6 wording follows.
- [x] **SF-7 (Availability) The ten-sends-per-sweep cap can stop midway through a routine's
  audience, yet `advance` still runs** (`src/main/java/com/homechores/service/RoutineService.java:438`).
  Two homes' routines due the same minute with six devices each: the second home's last devices
  miss that occurrence silently. Same pattern as `ListReminderService.sendDue`. **Confirmed** by
  reading; no test. *Fixed the same day*: `RoutineService.sendDue` checks the cap only between routines: a started occurrence reaches its whole audience (overshoot ≤ one home's devices), and a routine not started stays due. `RoutineServiceTest.theSendCap_startsNoMoreRoutines_butNeverLeavesAStartedOnesAudienceHalfway` (twelve devices, then the second home's routine on the next sweep); SPEC §4.4f and §6 updated. The same pattern in `ListReminderService` and `ChoreReminderService` is left as it was (their comments call the device cap deliberate) — a follow-up if the product wants it changed there too.

### Nits

- [ ] **N-8 (OCD)** `validZone` exists twice (`RoutineService.java:177`, `BackupService.java:591`).
- [ ] **N-9 (OCD)** `Routine.setCreatedByMemberId` (`src/main/java/com/homechores/domain/Routine.java:228`)
  has no callers.
- [ ] **N-10 (OCD)** `RoutineService.today(String)` returns a `LocalDate`, `today(String, Long)` a
  list of today's routines: one name, two concepts.
- [ ] **N-11 (Security, plausible)** `ListItemService.setListPrivate` leaves other members' armed
  reminders on that list's lines; they fire to the owner's devices and show in the owner's fired
  dialog.
- [ ] **N-12 (OCD, plausible)** A restored routine without an emoji keeps `null`
  (`BackupService.java:572`) while `apply` uses the default, so the push title `{0} {1}` renders
  "null Workout" (`RoutineService.java:499`).
- [ ] **N-13 (PM)** SPEC §4.4f says the Today strip costs three queries; `RoutineService.today`
  plus `RoutinesPanel.renderToday` issue five.
- [ ] **N-14 (Data migration, dev only)** `private_to_owner` was added NOT NULL without a column
  default to a table created by the previous commit; a dev database that ran the first commit
  alone trips `ddl-auto=update`. Production never saw the intermediate schema.

## PR #16 — starter chore catalogue; Leave on the shared device; badge clear of the emoji

The picked catalogue keys are built server-side from the checkbox group's items, seeding is
bounded at 60 inserts and 8 groups behind the existing rate limiter, the stored device secret is
dead after `leave` (pinned in `SharedDeviceTest`), all 699 keys exist in all three files with none
an English copy, and `docs/ORGANIZER-PLAN-2026-10-29.md` was ticked when #17 landed.

### Should fix

- [x] **SF-8 (Security) `SharedDeviceService.leave(Long deviceId)`**
  (`src/main/java/com/homechores/service/SharedDeviceService.java:130`) deletes whatever row the
  id names, with no home check. Safe today because its one caller passes the session's own device
  id, but it breaks the CLAUDE.md rule that a public service method taking an id must not trust the
  UI. Take the home too and refuse a row from another home, as `unpair` does. **Confirmed.** *Fixed the same day*: `SharedDeviceService.leave(homeCode, deviceId)` refuses a row from another home; `HomeView` passes the session's home; `SharedDeviceTest` adds the negative path (another home's code leaves the device paired); SPEC §4.1.3 and §6 follow.
- [x] **SF-9 (OCD)** `src/test/java/com/homechores/ui/SharedDeviceUiTest.java:255` copies the menu
  helpers from `HomeUiTest.java:114` verbatim, fully-qualified `java.util.stream.Stream` included.
  They belong in `HomeChoresUiTest`. **Confirmed.** *Fixed the same day*: `usable`, `allMenuItems`, `menuItems`, `menuItem` and `clickMenuItem` live once in `HomeChoresUiTest`; the copies in `HomeUiTest`, `SharedDeviceUiTest`, `EffectsUiTest` and `ListEditUiTest` (a fourth one, found while compiling), and the eight copies of `usable`, are gone.
- [x] **SF-10 (UX, plausible)** All / None in `src/main/java/com/homechores/ui/StarterChoresDialog.java:41`
  are `LUMO_SMALL` tertiary buttons, 32 px against the app's 40 px touch target. Not measured. *Fixed the same day*: All / None drop `LUMO_SMALL` and `.starter-bulk vaadin-button` has `min-height: 40px`; SPEC §4.2.1 says so.
- [x] **SF-11 (QA)** "Tyres spring and autumn, never twice in one season" (`ChoreCatalog.java:132`,
  SPEC §4.2.1) has no test; it is pure `Season.of` + `plusDays` and pins in `ChoreCatalogTest`.
  **Confirmed** untested. *Fixed the same day*: `ChoreCatalogTest.theTyreChange_isDueEachSpringAndAutumn_neverTwiceInOne` walks every spring and autumn day of a year: the next due date is in a different season and before the next of its two seasons ends. SPEC §6 lists it.

### Nits

- [ ] **N-15 (OCD)** `ChoreService.java:722` files seeded chores into groups by setting
  `groupId` / `sortOrder` directly rather than through `applyGroup`; defensible, but say why.
- [ ] **N-16 (L10n)** fi `chore.catalog.litterBox` "Puhdista hiekkalaatikko" reads as a children's
  sandpit first; "Puhdista kissanvessa" is unambiguous. (The two Finnish typos found here were fixed
  in #18.)
- [ ] **N-17 (UX)** The create form's "Choose" button has no accessible description of what is
  being chosen (`LandingView.java:220`); the "Starter chores · 11 chosen" spans are not linked to it.
- [ ] **N-18 (UX)** None → Done → Create is now a path to a home with zero chores; no string or
  test covers a chore-less board.
- [ ] **N-19 (OCD)** `ChoreCatalog.Entry` has seven positional parameters and the seasonal rows
  carry `null, "SUMMER", false`; a `seasonal(…)` factory beside `starter` / `entry` would remove them.
- [ ] **N-20 (QA)** `ChoreCatalogTest.java:74` loads the bundles with `Properties.load(InputStream)`
  (ISO-8859-1), so ä/ö count as two characters; harmless for today's checks, misleading for a
  future value comparison.
