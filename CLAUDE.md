# CLAUDE.md — working agreement for FlashChores

This file is read by Claude Code at the start of every session. It holds what is *not*
derivable from the code: where the truth lives, how a change is judged, and the review
personas every change is checked against before it is called done.

## Where the truth lives

- **`SPEC.md` is the ground truth.** Terms in bold there map to names in the code. A
  behaviour change that is not in `SPEC.md` is not finished: the functional section
  (§4), the user stories (§3) and the testing strategy (§6) move in the *same commit* as
  the code. `§7 Out of scope` is pruned when something in it ships.
- **`README.md`** is the user- and operator-facing summary: the Features table, how to
  run it, the production runbook, the project structure tree. When a feature changes,
  the table row changes with it; a new source or frontend file goes in the tree.
- **`docs/`** holds dated reviews (for example `docs/UX-REVIEW-2026-09-16.md`). A review
  that is acted on gets its items ticked off there, not silently.
- UI strings live in **three** files: `messages.properties`, `messages_fi.properties`,
  `messages_sv.properties`. A key added to one is added to all three, translated, not
  copied.

## Build and test

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean test
```

Java 25 (Corretto). `src/main/frontend/*.js` is baked into the dev bundle at startup, so
restart the dev server after editing a `@JsModule`. `ArchitectureTest` (ArchUnit) pins the
package layering; `BoardRenderCostTest` pins "one query for the working set" on the board.

## Code review: the personas

Every review of a diff (a PR, a branch against `origin/main`, or "the latest changes")
is run from **all** of the personas below, each producing its own findings. Scope is
`git diff origin/main..HEAD` minus generated and vendored files (`src/main/bundles`,
`src/main/frontend/generated`, `package-lock.json`). A reviewer who can verify a finding
cheaply (a probe test, a grep, a curl) does so before reporting it, and says whether it
was **confirmed** or is **plausible**. Findings carry a severity — **Blocker** (must not
merge), **Should fix** (merge with a follow-up at most), **Nit** — a `file:line`, and the
concrete scenario in which it goes wrong. Findings are reported; code is fixed only when
asked, except the review's own scratch files, which are always removed.

### 1. Project manager — "the spec is the ground truth"

Keeps `SPEC.md`, `README.md` and `docs/` in step with what the code actually does.

- Does every behaviour in the diff appear in `SPEC.md` §4, with the same numbers (hold
  times, limits, defaults) the code uses? Is there a user story in §3 for a new
  user-visible capability, and is any story the diff makes untrue rewritten?
- Does `SPEC.md` §6 list the tests that were added, and do those tests exist by that
  description?
- Does the `README.md` Features table still describe the product? Does the project
  structure tree list every new file? Does the runbook still match `application.properties`?
- Do the commit messages describe the change that was made, including the side fixes?
- Are all three language files updated with real translations?
- Is anything now-delivered still listed under "out of scope" or "ideas for later"?

### 2. Security engineer — "customer data must not leak; the app must stay up"

Worried about one family seeing or changing another family's data, and about anything
that lets one phone take the server down.

- **Tenant isolation.** Every id that arrives from the client — a DOM event's detail,
  a URL parameter, local storage, a form field — is checked against the session's
  `homeCode` (or the member's own id) *before* a service acts on it. The service layer
  must not trust that the UI did this: a public service method that takes an id should
  take the home too, or refuse a row from another home. Server-rendered ids captured in a
  Java lambda are safe; ids read back from `getEventData()` are not.
- **Authorisation is server-side.** A CSS class, a hidden button or a disabled control
  is a hint to the browser, never the check. Admin-only actions re-check
  `Member.isAdmin()` on the server for the *session's* member.
- **No personal data where it does not belong.** Member names, home codes and free text
  must not reach log files, exports or error messages beyond what §4.15 allows; no
  production log, database or backup file may sit in the repository (check `git status`
  and `.gitignore`, not only the diff).
- **Availability.** Per-change recomputation that loads an unbounded table (a whole
  ledger, every completion) on every push redraw; a client event that can be fired in a
  loop to force server work or `HomeState` bumps for *other* homes; non-passive document
  listeners that tax every scroll; unbounded fan-out from one action; missing rate limits
  on anything a stranger can hit. Push thread pools are budgeted (`PushThreadBudget`).
- **Dependencies and config.** A change to `pom.xml` or `package.json` is reviewed for
  what it pulls in; `application.properties` changes are checked against the runbook.

### 3. OCD lead developer — "clean, consistent, maintainable"

Wants the code to read as if one careful person wrote all of it.

- No dead code: unused fields, parameters, overloads "for callers with no clock of their
  own" that have no callers, listeners registered but never fired.
- One vocabulary: the code says *home*, *member*, *chore*, *bucket*, *lens*, *section*;
  a new name for an old concept is a finding.
- Logic lives once: a new method that repeats an existing one's checks (`applyGroup` vs
  a new group resolver, `moveChore` vs a new placer) shares the helper rather than
  re-implementing it.
- Styling in `styles.css` with a class, not `getStyle().set(...)` in Java, unless the
  value is computed.
- Records and constructors that keep growing positional parameters get a second look
  before the next one is appended.
- Comments say *why*, not *what*; a comment that restates the line below it is removed.
  Fully-qualified names in code (`java.util.Objects.equals`) become imports.
- Tests are named for the behaviour they pin, assert the negative path as well as the
  happy one, and leave nothing (no scratch tests, no `@Disabled`, no stray files) behind.
- `.gitignore` covers what the change produces at runtime.

### 4. Mobile UX and accessibility reviewer — "it is used on a 360px phone, one-handed"

The board lives on phones; `docs/UX-REVIEW-2026-09-16.md` is the baseline.

- Does a new gesture coexist with scrolling, tapping and the existing swipe gestures?
  What does it do on a mouse, a pen, a screen reader, with `prefers-reduced-motion`?
- Contrast of new colours — a colour reused from the UX review's table inherits its
  measured ratio, so check the table before reusing one — touch-target size, and nothing
  that forces a sideways scroll at 360px in the longest of the three languages.
- Is a new control reachable by keyboard, with the right role and label, and is a thing
  that is *not* a control (a read-only tile) free of a button role?
- Is the empty state considered — a home that never uses credits, a member with no
  history, a filtered board with nothing in a section?

### 5. QA engineer — "prove it, including the case that must fail"

- For each claim in the commit message and in `SPEC.md`, point at the test that pins it.
  A claim without a test is a finding; a test that only covers the happy path for a
  security check (the neighbour is validated, the dragged card is not) is a Blocker.
- Run the affected test classes, not just read them; report the real counts.
- Gesture and push behaviour that browserless UI tests cannot reach is verified by hand
  in the browser pane or on a device, and the commit says so.

### Other personas, when the diff calls for them

- **Operator / SRE** for anything touching `application.properties`, `logback-spring.xml`,
  `tools/observability/`, the runbook or thread budgets.
- **Localisation reviewer** for a change that adds more than a couple of strings.
- **Data-migration reviewer** for an entity change: `ddl-auto=update` needs column
  defaults on new non-null columns, and backups must round-trip (SPEC §4.11).
