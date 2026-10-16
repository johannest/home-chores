# Code review — 2026-10-16

Scope: `git diff origin/main..HEAD` on `user-feedback-board-stats` (commits d9a465c "Show
redeemed diamonds and their rewards in Stats" and e5d2f11 "Arrange a shared list by
hold-and-drag, and let reminders sort their lines by due time"), minus generated and vendored
files — plus a pass over the whole UI test suite, since coverage may have drifted before these
two commits. Run from the personas in `CLAUDE.md`. Each finding says whether it was
**confirmed** (probed) or is **plausible** (read only).

Baseline before the review: full suite 468 tests, 1 intermittent failure (see QA-1). After the
tests added below: **476 tests, 0 failures** (`mvn clean test`, Java 25).

## Verdict

No blocker. The two features are complete across code, SPEC, README and all three language
files; the drop path is checked server-side against the session's home on both panels and the
negative paths are pinned. Three things to fix before or soon after merge, the rest are nits.

## Findings

### Should fix

- [x] **SF-1 (UX) No keyboard path to arrange a list.** `src/main/java/com/homechores/ui/ListPanel.java:326`
  enables the hold-and-drag for everyone, but unlike the board (which keeps ↑/↓ in the Admin
  tab as its keyboard path, SPEC §4.2b) a shared list has no other way to be arranged. A keyboard
  or switch user, and a screen-reader user, cannot reorder a list at all. **Confirmed** by grep:
  no move-up/down control exists in `ListPanel`. *Fixed the same day*: ↑/↓ on every plain line,
  disabled at the ends of the plain run, through the same `placeItem` as a drop; clipped out of
  sight below 480px but kept in the tab order and the accessibility tree (SPEC §4.4e).
- [x] **SF-2 (OCD / dependencies) Every `mvn test` dirties three tracked files.** The test-scoped
  `vaadin-charts-flow` (`pom.xml:128`, there only so TestBench's `test(Chart)` overload resolves)
  is on the classpath when Vaadin prepares the frontend during the test phase, so `package.json`,
  `package-lock.json` gain `@vaadin/charts` and `src/main/bundles/dev.bundle` grows from 2.7 MB to
  3.4 MB. **Confirmed**: the tree was already dirty at the start of the session, and `git checkout`
  followed by `mvn clean test` dirtied it again. Risk: the next "commit everything" adds a charts
  dependency the README says the app does not have. *Fixed the same day*: the comment on the
  dependency was stale — in TestBench 25.3 the `test(Chart)` overload lives on
  `CommercialTesterWrappers`, which `UIUnitTest` no longer implements — so the dependency is
  removed; the UI tests run without it and `mvn test` leaves the tree clean.
- [x] **SF-3 (QA) `ListOrderUiTest.withToDoOff_aDeletedCustomListFallsBackToTheFirstList` is
  intermittent.** **Confirmed**: failed in one full-suite run (`expected: <0> but was: <2>` at
  line 168, i.e. the tabs never re-rendered after the "other phone" deleted the list), passed when
  run alone, and later failed in other class-level runs alongside sister tests
  (`theListOnScreenBeingSwitchedOff_landsOnToDo`, the new demotion and removal tests). The test
  predates this branch (edffd1a). *Root cause, confirmed by reading TestBench and Flow 25.3*:
  `SpringUIUnitTest.initVaadinEnvironment()` never calls `initSignalsSupport()`, so no signals
  test environment is registered and `runPendingSignalsTasks()` is a no-op in every UI test here.
  The home's revision is a `SharedNumberSignal` on a `LocalAsynchronousSignalTree`, which
  confirms each bump through the active `SignalEnvironment` — `VaadinService`'s, whose dispatcher
  is `VaadinService.getExecutor()`, a thread pool — and the view's effect then arrives as a
  `ui.access` task that runs only when the pool and the test thread's session lock happen to
  align. *Fixed the same day*: every UI test now extends `HomeChoresUiTest`, which installs the
  signals test environment TestBench documents (every confirm and effect is queued and drained on
  the test thread), and the "another phone" tests wait through `awaitRedraw(condition)`, which
  drains the queue until the outcome holds. The UI package was run three times clean.

### Nits

- [ ] **N-1 (OCD)** `StatsService.diamondStats` (`StatsService.java:339`) loads the `Member` by
  id only to put it on every `Redemption` of the member's own list, where the view never shows
  the name (`named=false`). One wasted query per My stats render; pass the id or let the view
  resolve the name on the admin's list only.
- [ ] **N-2 (OCD)** `HomeStats` (`StatsService.java:631`) is now a 12-field positional record;
  CLAUDE.md asks for a second look before the next one is appended.
- [ ] **N-3 (OCD)** `ListItemService.add` (`ListItemService.java:264`) loads the whole open list to
  find the max `sortOrder` on every add. Bounded by one list, so not an availability issue, but a
  `max()` query would say what it means.
- [ ] **N-4 (OCD)** `ListEditUiTest.java:310` filters with `!l.equals(plumber)` *and*
  `!l.getId().equals(plumber.getId())`: the entity `equals` is redundant with the id compare.
- [ ] **N-5 (UX)** `StatsPanel.java:254` uses `LocalDate.now()` (server zone) for the year-or-not
  decision on redemption dates, while the rest of the UI formats days in
  `SessionContext.timeZone()`. Consistent with the 💎 earned chart (also server zone, SPEC §4.9),
  so cosmetic — but a member in another zone can see "31.12." become "31.12.2025" a few hours
  early.
- [ ] **N-6 (UX, plausible)** Holding a row whose swipe tray is open lifts a ghost of the row with
  the face still translated; `.drag-ghost.list-item .swipe-face` (`styles.css:585`) paints the face
  but does not reset its transform. Rare (the hold is 450 ms still on an open row) and cosmetic.
- [ ] **N-7 (Security, plausible, no action)** A forged `list-place` naming a *pinned* (reminder)
  line as the neighbour is accepted by `placeItem`, since pinned lines are arrangeable server-side.
  Harmless: the result is still a valid order of the session's own list; the UI just never
  produces it.

### Checked and fine

- **Tenant isolation**: both ids of a drop are parsed by `ClientId` and re-checked in
  `ListItemService.placeItem` against the session's `homeCode`; the member is checked by
  `memberOf`. Another home's line as the dragged line or the neighbour, a neighbour on another
  list, a ticked line and a dinner slot all write nothing (`ListItemServiceTest`, and now
  `ListEditUiTest` through the panel). `ChoresPanel.place` still re-checks `isAdmin()` server-side.
- **Availability**: `placeItem` loads one list; a no-op drop writes nothing and bumps nobody, it
  only redraws the sender's own panel. The stats' extra query is the home's REDEEMED rows only.
  The non-passive `touchmove` listener exists only for the lifetime of a lift, as before.
- **Member removal / restore**: `removeMember` deletes the member's credit rows and
  `BackupService.restore` skips credit rows whose member is missing, so `Redemption.member()` on
  the admin's list cannot be null (checked for an NPE on the Home stats render).
- **Data migration**: `ListItem.sortOrder` carries `columnDefinition = "integer not null default 0"`;
  `SchemaEvolutionTest` now proves the default lands under `ddl-auto=update` (see below). Backups
  round-trip the order and an older file restores in writing order (`BackupServiceTest`).
- **Localisation**: seven new keys in all three files, translated, `MessageParityTest` green.
- **Docs**: SPEC §3 (US-58, US-59), §4.2b, §4.4e, §4.7, §4.9, §4.13c and §6, and the README
  Features table and project tree all moved with the code. Commit messages describe the side
  fixes (`ClientId`, `list-swipe.js` standing down, the shared `hold-drag.js`). The gesture was
  driven by hand in the browser pane and the commit says so.
- **Swipe and hold coexist**: the swipe locks its axis after 10 px, the hold is cancelled after
  8 px, so a finger that moves swipes or scrolls and never lifts; once lifted the swipe script
  drops its own drag on the next `pointermove`.

## UI test coverage, whole project

Pre-existing gaps (not introduced by this branch) found by mapping every admin action, dialog and
server-side check in the `ui` package against the UI tests. The ones most worth having were added
in this review; the rest are listed so they can be picked up.

### Added in this review (all green)

`HomeUiTest`:
- `claimAdmin_withAWrongPin_revealsNothing` — the negative path of the PIN claim: field invalid,
  three tabs, no role, member lifetime.
- `aDemotedAdmin_losesTheAdminTab_live` — the rebuild on the revision bump is the only gate between
  a demoted member and the admin actions; it now has a test.
- `aRemovedMember_isShownOut_andSoIsEveryoneWhenTheHomeGoes` — `showOut()` for a removed member and
  for a deleted home, from the affected device's side.
- `theSoleAdmin_cannotDemoteOrRemoveThemselves_fromTheMembersCard` — the last-admin guard through
  the row menu and the confirm dialog, not only in the service.
- `member_getsNoHomeStatsTab` — the negative of the admin's Home stats.
- `stats_redeemedList_saysNoNote_andCountsTheRowsTheCapHid` — "(no note)" and "…and 2 earlier"
  as rendered, the stored marker never shown.

`ListEditUiTest`:
- `aDropNamingAnotherHomesLine_movesNothing_andACustomListArrangesToo` — another home's line
  dragged or as neighbour through the panel; the ticked line's row has no id and sits in a
  non-droppable section; a custom list arranges and refuses a To-do neighbour.

`SchemaEvolutionTest`:
- `listLinesFromBeforeTheyCouldBeArranged_keepWritingOrder_untilTheFirstDrag` — rows inserted
  without `sort_order` on the upgraded legacy database read as 0 in writing order; the first drag
  renumbers densely.

SPEC §6 updated: the create/join tab counts it stated (three / two) were stale against the tests
(four / three) and are corrected; the new tests and an **Upgrade path** bullet for
`SchemaEvolutionTest` (which §6 never named) are listed.

### Still uncovered (ranked)

- [ ] Backup **restore** through the Admin tab (`AdminPanel.java:1380-1444`): the oversize sink
  abort, a rejected file, the confirm step and the sign-out after. Only the service is tested.
- [ ] Chore rules controls (`AdminPanel.java:550-659`): approval, confirm taps, other help, daily
  target, max in a row, division style, rotation enforced, booking hold — every existing test sets
  these through the service, none through the control.
- [ ] Home settings (`AdminPanel.java:666-714, 1542-1555`): counter-reset select and its confirm,
  join/rejoin gate switches, home rename (blank ignored, header updates), change PIN (4-digit
  validation).
- [ ] Join and rejoin requests approved or rejected from the Admin tab, and the landing page's
  "waiting" screen reacting (`AdminPanel.java:196-211`, `LandingView.java:395-431`).
- [ ] Spree tiers add/delete with validation, and the redeem dialog's disabled-at-zero, ≤ 0 and
  over-balance paths (`AdminPanel.java:1265-1359`).
- [ ] Chore Edit and Delete from the row menu (`AdminPanel.java:943-949`); group edit, delete and
  move (`AdminPanel.java:1099-1110`).
- [ ] Booking buttons on chore cards: Book, Cancel, "someone else" toast (`ChoresPanel.java:589-608`).
- [ ] `SnoozeDialog` arming and cancelling through the chips (`SnoozeUiTest` only checks the
  offsets); `ListReminderDialog` quick-option arm, invalid picker, Done on a fired reminder, cancel.
- [ ] Avatar picker: picking, none, and that only your own chip opens it.
- [ ] Member rename dialog; header menu Leave; language switch; daily reminder enable/disable.
- [ ] Add-chore tile rejecting a blank name (`ChoresPanel.java:921-926`).
