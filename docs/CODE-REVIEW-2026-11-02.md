# Full code review — 2026-11-02

Scope: current application at `fe978bf9c8e4a7c0702ab4322d4675db6d8ad8bf`.
`HEAD` and `origin/main` are identical, so the requested full review covers the current
application rather than an empty branch diff. Generated and vendored files are excluded.
The pending CLAUDE.md → AGENTS.md conversion and existing untracked `.java-version` files
are not application changes under review. No application fixes were made.

Reviewed from all five AGENTS.md personas, with operator and data-restoration concerns
included. Findings below distinguish executed reproductions from source inspection.
The prior reviews remain useful context; their resolved items are not reported as new bugs.

## Findings

### B-1 — Blocker: restore leaves notifications flowing to deleted members

**Security / data restoration. Confirmed by an executed regression probe.**

Location: `src/main/java/com/homechores/service/BackupService.java:307`;
recipient selection: `src/main/java/com/homechores/service/ListReminderService.java:220`.

Restoring deletes and recreates the home's members, but never deletes its
`PushSubscription` rows. Those rows keep the same `homeCode` and refer to member ids that
no longer exist. The shared-list and home-routine senders select subscriptions by home
code without requiring a live member, so the old devices still receive new family data.

Reproduction: export a home; add another member and their push subscription; restore the
older backup, which has no such member; create a new shared to-do and fire its reminder.
The probe verified that the removed member no longer exists, then observed `sender.send`
called with their old subscription and the new line's text. No real push was sent: the
sender was mocked. Removing that person through the ordinary member-removal action would
have deleted their subscriptions; restoring around them does not.

Delete the home's subscriptions in the restore transaction before deleting members.
Also filter notification audiences against current membership as a second line of defense.
Pin both shared-list and home-routine notifications after restore with negative tests.

*Addendum (second-pass verification, source inspection):* the dangling rows affect every
member, not only removed ones. Every subscription in the home points at a pre-restore member
id afterwards. Restored members' home-wide reminders therefore fall back to English
(`locales.getOrDefault(..., Locale.ENGLISH)`), and their private-list and own-routine
reminders, looked up by the new member id, reach no device at all.

### SF-1 — Should fix: retention mistakes list/routine use for an empty signup

**Project manager / operator. Confirmed by an executed predicate probe and source inspection.**

Location: `src/main/java/com/homechores/service/HomeCleanupService.java:157`;
unsafe export criterion also at `HomeCleanupService.java:194`.

An abandoned home is defined solely by its last activity, absence of chore completions,
and at most one member. A person using private lists, grocery lists, dinner plans or
routines without logging chores satisfies that definition. After a long weekend away
(72 hours in the committed production configuration), the empty-home sweep can delete
all that data without a safety export. The 30-day inactive sweep also skips the export
for homes that have list/routine data but no chore completions.

The probe created a private list containing a line, set the home's last activity four
days back, and found `isAbandoned(..., now - 3 days)` returned true. The deletion path
calls `deleteHome` directly, which deletes the list and routines too.

This implements the existing retention wording, but that definition no longer captures
whether the expanded product holds valuable user data. Update SPEC §4.11.2 and the runbook
alongside the predicate: distinguish an untouched starter home from one holding user-created
organizer data, and require a successful safety export before purging the latter.

### SF-2 — Should fix: approval decisions can be applied repeatedly

**Lead developer / QA. Confirmed by two executed regression probes.**

Location: `src/main/java/com/homechores/service/ChoreService.java:1773` and `:1802`;
UI scenario: `src/main/java/com/homechores/ui/AdminPanel.java:307`.

`approve` rechecks the admin but never requires the completion to still be PENDING.
It awards the chore/help credits every time. Two admins can have the same Accept-help
dialog open; one acceptance redraws the boards but does not invalidate the other's open
dialog. Accepting from that stale dialog pays the same help a second time. Ordinary
approval requests from different sessions can also race.

The first probe approved one help entry for 5 credits twice: the balance became 10 while
there was still only one completion. The second approved the entry, then called `reject`:
the status became REJECTED but all 5 credits remained. A stale rejection can therefore
make the ledger disagree with the completion history.

`reject` (`ChoreService.java:1802`) has the same missing guard in its own right: it rejects
an already-approved or already-rejected completion without complaint. That is how the
second probe left credits behind for a REJECTED entry.

Make the transition from PENDING atomic, with locking or an equivalent concurrency check,
and award credits only for the winning transition. Refuse or ignore later decisions.
A simple unlocked status check alone does not resolve concurrent requests. Keep admin
corrections to previously approved entries on the existing explicit correction path.

### SF-3 — Should fix: the ordinary completion service accepts another home's member

**Security / lead developer. Confirmed by an executed regression probe.**

Location: `src/main/java/com/homechores/service/ChoreService.java:1493`;
related booking path: `ChoreService.java:1340`.

`complete(taskId, memberId, zone)` loads the task and member independently, then uses the
task's home without checking that the member belongs to it. An unrestricted task in home A
can therefore be completed and credited to a member from home B through this public
service method. `completeFor` explicitly makes this check, and the shared-device wrapper
does too; the ordinary path does not. `bookChore` likewise never validates membership.

The probe called `complete` with a newly created task from one home and a member from a
second home; the call succeeded instead of rejecting the mismatch.

The current ordinary UI captures the task id server-side and takes the member from its
viewer, so this is a confirmed service-boundary violation, **not a demonstrated browser
exploit**. AGENTS.md explicitly requires the service to enforce tenant boundaries rather
than rely on its UI caller. Add the same-home guard before any reads/writes with effects,
and cover both completion and booking with cross-home negative tests.

*Addendum (second-pass verification, source inspection):* two more public service methods
act on a bare id with no home or admin check:

- `deleteCompletion(Long completionId)` (`ChoreService.java:1817`), the admin's "that didn't
  happen" correction. It deletes any completion and its credits, in any home, for any caller.
- `removeMember(Long memberId)` (`ChoreService.java:674`) removes any member of any home
  and checks only the last-admin rule, not that the caller is an admin of that home.

Both are called only from `AdminPanel` with server-rendered ids, so, as above, these are
service-boundary violations, not demonstrated browser exploits. Both should take the acting
admin's id and check it with `adminOf` against the target's own home.

`setMemberAdmin(memberId, admin)` and `renameMember(memberId, name)` (`ChoreService.java:651`,
`:664`) are the same kind of gap: `AdminPanel` re-checks `stillAdmin()` before calling them, but
the service itself does not.

### SF-4 — Should fix: removing a member leaves their chores booked

**Lead developer / QA. Confirmed by an executed regression probe.**

Location: `src/main/java/com/homechores/service/ChoreService.java:674`;
booking validity: `ChoreService.java:1323`.

`removeMember` deletes the member and associated data without clearing
`ChoreTask.bookedByMemberId` / `bookedAt`. Booking validity checks only the timestamp,
so the removed member still blocks everyone else until the original hold expires
(default four hours, configurable up to 24). Their displayed name falls back to “Someone”.

Reproduction: a non-admin books a chore, an admin removes them, then the admin tries to
book the same chore. The probe received false. Normal completion also sees the same live
booking. Clear that member's bookings in the removal transaction and keep the existing
single home revision bump.

## Findings by review persona

| Persona | Findings |
| --- | --- |
| Project manager | SF-1: retention's definition of an unused home needs to include organizer use. **Nit, confirmed (second pass):** `SPEC.md:1530` still calls `abandoned-home-days` "0 = disabled, the default"; that is the code default, but the committed `application.properties` sets it to 30 (and `empty-home-hours` to 72), which §4.11.2 does not mention. **Nit, confirmed:** `README.md:20` still says rejoining selects from a family member list; `LandingView.java:340` deliberately asks for a nickname to avoid exposing names. |
| Security engineer | B-1: post-restore notification disclosure. SF-3: missing service-layer membership checks, including `deleteCompletion` and `removeMember` (second pass). Existing cross-home drag, private-list/routine and admin-gate tests passed; that does not cover these two gaps. |
| Lead developer | SF-2: non-atomic/non-idempotent decisions. SF-4: incomplete member deletion. SF-3: inconsistent guards between ordinary and delegated completion paths. |
| Mobile UX and accessibility | **Confirmed existing deferred issue:** `ChoresPanel.java:596` creates the primary completion action as a clickable `Div` with no keyboard activation or focus semantics; the snooze `Span` at `:629` has the same problem. This is already deferred in `docs/UX-REVIEW-2026-09-16.md:147`, not a newly discovered regression. **Nit, confirmed:** `HomeView.java:524` hard-codes “Cancel” in the claim-admin dialog, so Finnish and Swedish users see English despite an existing `common.cancel` translation. |
| QA engineer | Six missing negative/state-transition cases reproduced B-1 and SF-1–SF-4 despite the existing suite passing. Retain these cases as regression tests when fixing the issues; add concurrent-decision coverage for SF-2. Browser interaction and licensed TestBench validation remain outstanding. |

## Validation and limits

- **512 existing tests passed**, zero failures/errors/skips, across 52 classes. Java 25
  OpenJDK was available; Maven 3.9.9 was installed temporarily because `mvn` was absent.
  This is Linux, so the macOS `/usr/libexec/java_home` command in AGENTS.md does not apply.
- The broad run covered services, domain, maintenance, architecture, schema evolution,
  encryption, trusted proxies, push thread budgets, translations and chart helpers:
  **506 tests across 50 classes**. A separate run covered `DeviceIdentityTest` (5) and
  `SecurityHeadersInitListenerTest` (1).
- Relevant class counts include `ChoreServiceTest` 16, `CreditServiceTest` 4,
  `BackupServiceTest` 11, `HomeCleanupServiceTest` 24, `SharedDeviceTest` 13,
  `ArchitectureTest` 18, `SchemaEvolutionTest` 12, `BoardRenderCostTest` 5,
  `StatsRenderCostTest` 2, `MessageParityTest` 2, `DatabaseEncryptionTest` 7 and
  `EncryptDatabaseTest` 6. All passed.
- **Six temporary regression probes failed their expected-correct-behavior assertions,
  with zero errors**, confirming the five findings above. The probes used an in-memory
  test database and a mocked push sender. Their source and compiled scratch files were
  removed after recording the results.
- The full `mvn test` run reached the Vaadin TestBench license-validation prompt and could
  not finish unattended. It was interrupted; the licensed UI tests are **not counted as
  passing**. The added Vaadin documentation MCP connection is a documentation tool, not
  a TestBench license.
- No browser/device gesture, screen-reader, 360px layout or live push delivery testing was
  performed. Existing deferred accessibility findings were checked in source only.
- Repository checks found no tracked `.mv.db`, `.trace.db`, `.log`, `.env*` or `.pem` files.
  `.gitignore` excludes runtime data/logs and generated output. No fresh external dependency
  vulnerability audit or production deployment test was performed.

Commands used for the passing runs (with the temporary Maven executable in place of `mvn`):

```bash
mvn -B -ntp test '-Dtest=**/service/*Test,**/domain/*Test,**/maintenance/*Test,ArchitectureTest,SchemaEvolutionTest,DatabaseEncryptionTest,TrustedProxiesTest,PushThreadBudgetTest,PushThreadBudgetRuntimeTest,MessageParityTest,ChartsTest'
mvn -B -ntp test -Dtest=DeviceIdentityTest,SecurityHeadersInitListenerTest
```

## Resolution

Fixed on branch `review-2026-11-02-fixes`. Each item has a regression test that fails
without the fix.

- [x] **B-1:** restore deletes the home's `PushSubscription` rows, and the shared-list and
  routine senders skip any device whose member no longer exists.
  Tests: `ListReminderServiceTest.restoringAnOlderBackup_dropsTheHomesDevices_soARemovedPersonHearsNothing`,
  `…aDeviceWhoseMemberIsGone_isNotRemindedOfTheHomesLines`,
  `RoutineServiceTest.afterRestoringAnOlderBackup_aRemovedPersonsDevice_getsNoHomeRoutine`,
  `…aDeviceWhoseMemberIsGone_getsNoHomeRoutine`.
- [x] **SF-1:** retention asks `BackupService.holdsFamilyData`: completions, list lines
  (dinners included), custom lists or routines. Such a home is never treated as empty, and
  the inactive sweep exports it before deleting. SPEC US-39/US-40 and §4.11.2, the README
  row and runbook, and the `/terms` sentence in all three languages are updated.
  `HomeCleanupService` no longer takes a `CompletionRepository`.
  Tests: `HomeCleanupServiceTest.fastTier_neverTouchesAHomeUsedOnlyForItsOrganizer`,
  `…inactiveTier_exportsAHomeUsedOnlyForItsOrganizer_beforeDeletingIt`.
- [x] **SF-2:** `approve`/`reject` load the entry with a row lock
  (`CompletionRepository.findForDecisionById`, `PESSIMISTIC_WRITE`) and decide it only while
  it is still PENDING. `approve` returns `Optional.empty()` and `reject` returns `false` when
  the entry was already decided. The admin panel then shows `admin.review.alreadyDecided`.
  A bulk `@Modifying` update was tried first, but `ArchitectureTest` forbids it ("writes go
  through the entity"). `StatsAggregationTest`'s fixture used to reject an approved
  completion; it now turns approval on first.
  Tests: `OtherHelpTest.acceptingTwice_fromAStaleDialog_paysOnce`,
  `…decliningAfterAccepting_isRefused_soTheCreditsAndTheHistoryAgree`,
  `…acceptingAfterDeclining_isRefused`, `ChoreServiceTest.approvingAChoreTwice_countsItOnce`,
  and `ConcurrentReviewTest` (20 committed two-thread rounds). That test fails at round 0
  against an unlocked "check the status, then save" version.
- [x] **SF-3:** `complete` and `bookChore` share `requireSameHome` with `completeFor`.
  `deleteCompletion(id, adminId)` and `removeMember(id, adminId)` check `adminOf` against
  the target's own home. Member undo uses a private `removeCompletion` and keeps its own
  narrower check. Tests: `ChoreServiceTest.aMemberOfAnotherHome_canNeitherCompleteNorBookItsChores`,
  `…unmarkingAChore_andRemovingAMember_needAnAdminOfThatHome`.
- [x] **SF-4:** `removeMember` releases the member's bookings in the same transaction.
  Test: `ChoreServiceTest.removingAMember_releasesTheChoresTheyHadBooked`.
- [x] **Nits:** README rejoin row (nickname, not a member list); the claim-admin dialog's
  Cancel uses `common.cancel`; SPEC §4.11.2 now states both the code defaults and the
  production values.
- [ ] **Open:** `setMemberAdmin` and `renameMember` still rely on the UI's `stillAdmin()`
  re-check (see the SF-3 addendum). The deferred keyboard/screen-reader items stay in
  `docs/UX-REVIEW-2026-09-16.md`.

Validation: 526 tests across 53 classes passed, with 0 failures, errors or skips. That is
the same non-UI selection as above plus `DeviceIdentityTest` and
`SecurityHeadersInitListenerTest`. The licensed UI tests (`HomeUiTest` and others, whose
calls were updated for the new signatures) compile, but they stop at the TestBench license
check and were **not run**. The new "already decided" notice was not checked in a browser.

