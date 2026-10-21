# Security audit — 2026-10-21

Scope: the **whole application** at `80b758d` on `user-feedback-board-stats` (not a diff), run
from the security-engineer persona in `CLAUDE.md` — tenant isolation, server-side
authorisation, personal data where it does not belong, availability, dependencies and config —
with the OWASP-style extras that persona does not spell out (session handling, headers, SSRF,
injection, repository hygiene). Each finding says whether it was **confirmed** (probed: a
scratch test, `javap` on the resolved jar, a `jshell` run, a grep that proves the path) or is
**plausible** (read only). Scratch files were removed; `git status` is clean. No project file
was changed by the audit.

## Verdict

*Update, the same day:* the blocker and all eight should-fix items were fixed (and N-3 and N-6
with them, as they fell inside the same code); each item below says how. The host check
(`curl -sI https://flashchores.com/`) showed `Secure; HttpOnly` on the cookie and HSTS already
sent by the proxy, so SF-5 reduced to adding `SameSite` and writing the verify step down.
Suite after the fixes: **497 tests, 0 failures** (`mvn clean test`, Java 25), up from 476.

The design is sound and most of the hard things are already right: every id that comes back
from the browser is re-checked against the session's home, local-storage identities are backed
by a hashed 128-bit device secret, the admin PIN is brute-force gated, push endpoints are
SSRF-checked at store *and* send time, restore refuses another family's file, nothing
user-written reaches `innerHTML` or an unparameterised `executeJs`, and no data, log or key has
ever been committed.

**One blocker** stands between the current build and a public announcement: a single hostile
push subscription can stall the one scheduler thread for every home until the next restart,
because the push library sends with no HTTP timeout. It is a contained fix. After it, eight
should-fix items, of which the stale-admin dialog (SF-1) and the two cookie/proxy items
(SF-5, SF-7) are the ones a curious reader of the LinkedIn post could actually poke at.

## Findings

### Blocker

- [x] **B-1 Push sends have no HTTP timeout; one stranger freezes every scheduled sweep for
  every home.** `src/main/java/com/homechores/service/WebPushSender.java:80` calls Vaadin's
  `WebPush.sendNotification`, which delegates to `com.interaso.webpush.WebPushService` 1.3.0.
  **Confirmed** by `javap -c` on the resolved jar: the client is
  `HttpClient.newBuilder().build()` (no `connectTimeout`), the request is built without
  `.timeout(...)`, and the call is the blocking `HttpClient.send`. The JDK client has no default
  request timeout, so a server that accepts the TCP connection and never answers holds the
  thread forever. The class has a single constructor and no way to inject a client.
  *Scenario:* anyone who can sign in (creating a home is 10/h per IP) opens the reminder dialog
  and, from DevTools, answers the subscribe round-trip with an endpoint on a host they
  control — a public name, so `PushEndpoints` passes it. When the reminder is due the sweep
  connects, the host accepts and stays silent. `spring.task.scheduling.pool.size=1`
  (`application.properties`), so booking-expiry visibility, all three push sweeps for all
  homes, rejoin expiry, list purge and the nightly retention purge stop until a restart.
  `application.properties` lines 26-29 argue the single thread is safe because an unresponsive
  push service costs "at most its send timeouts" — there are none.
  *Fix:* use interaso's lower-level `WebPush` class (it has no `HttpClient` inside) with an
  app-owned `HttpClient` that sets `connectTimeout` and a per-request `timeout` of ~10 s; or
  wrap each send in a virtual-thread future with `orTimeout`. Add a test that a hanging sender
  does not block the sweep, and make the properties comment true.
  *Fixed*: `WebPushSender` now does the HTTP exchange itself over its own `HttpClient`
  (connect 5 s, request = deadline), on a virtual thread the sweep waits on for at most
  `homechores.push.send-timeout-ms` (10 s, DNS check included) before cancelling; only the
  payload format is still Vaadin's. `WebPushSenderTest` pins the deadline against a local
  server that never answers.

### Should fix

- [x] **SF-1 Admin-only service methods never re-check that the caller is still an admin; a
  demoted admin's already-open dialog still acts.** **Confirmed** with a scratch UI test (since
  deleted): Sam (admin) opens *Rename member* for Alex; Alex demotes Sam on another phone;
  Sam's board redraws to three tabs; Sam clicks *Save* and `renameMember` runs. The same path
  works for delete-home confirm, chore edit/delete, redeem, restore and approve/reject, because
  `AdminPanel`'s dialogs (`confirm()` at `AdminPanel.java:1528`, rename `:800`, redeem `:1330`,
  delete-home `:1490`, restore `:1410`) hang off the UI, not the panel, so they outlive the
  redraw that removes the tab, and none of `ChoreService.setMemberAdmin:573`,
  `renameMember:586`, `removeMember:604`, `decideRejoin:433` (its `adminId` is recorded, never
  checked), `updateTask:714`, `deleteTask:741`, group CRUD `:805-838`, `completeFor:1499`,
  `approve:1670`, `reject:1696`, `deleteCompletion:1710`, `saveHome:174`, `deleteHome:330`,
  `resetCounters:1840`, `CreditService.redeem:138` (`adminId` unused), `addTier/deleteTier
  :171-181` or `BackupService.restore` asks `isAdmin()`. Same-home only — every id is
  server-rendered — so this is privilege *retention*, not escalation. The counter-examples that
  do it right are `ListItemService.moveList/setListEnabled` (`adminOf`, `:681`) and
  `ChoreService.logOtherHelpAsAdmin:1584`. `HomeUiTest.aDemotedAdmin_losesTheAdminTab_live`
  (line 1658) even documents "that redraw is the only thing between a demoted member and the
  admin actions". *Fix:* an `adminOf(homeCode, memberId)` guard in `ChoreService`,
  `CreditService` and `BackupService` applied by the methods above; cheaper first step, re-read
  `isAdmin()` in the dialog listeners. Pin the negative path.
  *Fixed*: `MemberRepository.findAdmin` is the one check; the five service methods that take
  the acting admin (`decideRejoin`, `completeFor`, `approve`, `reject`, `redeem`) and
  `BackupService.restore` (which now takes the admin) refuse a non-admin, and every action in
  `AdminPanel` (plus the board's ＋ dialog) goes through `stillAdmin()`, which toasts and does
  nothing for a demoted member. The 13 settings write-throughs folded into one guarded
  `updateHome`. Negative tests in five service suites and `HomeUiTest`.
- [x] **SF-2 Unbounded push subscriptions per member, and the per-sweep send cap sits outside
  the inner loop.** **Confirmed** by reading. `PushReminderService.storeSubscription:240`
  upserts by endpoint with no per-member or per-home cap; every subscribe round-trip naming a
  new endpoint is a new row, pruned only on 404/410 (a host answering 5xx is kept forever).
  All three sweeps iterate a member's whole subscription list before testing the cap
  (`PushReminderService.sendDueReminders:103-114`, `ChoreReminderService.sendDue:106-113`,
  `ListReminderService.sendDue:160-167`): one member with 5 000 rows is 5 000 DNS lookups and
  5 000 blocking sends in one sweep, even once B-1 has a timeout. *Fix:* cap rows per member
  (five, evict oldest) and check the cap inside the inner loop.
  *Fixed*: `MAX_DEVICES_PER_MEMBER` = 5, oldest evicted on store; all three sweeps check the
  send cap between devices. Pinned in `PushReminderServiceTest` and `ChoreReminderServiceTest`.
- [x] **SF-3 The Stats tab reloads the whole completion ledger on every push redraw.**
  **Confirmed** by reading, not benchmarked. `HomeView.buildChrome` runs on every `HomeState`
  bump and `showSelected()` (`HomeView.java:399`) calls `statsPanel.refresh()`, which runs
  `StatsService.homeStats:428` (`completions.findByHomeCode`), `myStats:242` and
  `diamondStats:347` — all filtered by period in memory. Completions are never purged. Several
  tabs parked on Stats plus a member bumping the home in a loop (any tick, add or drop) is a
  full-ledger load per tab per bump on a 2-CPU box with a Hikari pool of two, felt by every
  other home. `BoardRenderCostTest` pins `taskViews` only. *Fix:* push the period into the
  query (the `...DoneAtGreaterThanEqualAndDoneAtLessThan` methods exist) or memoise per
  revision; pin it next to the board's cost test.
  *Fixed*: any lens but *All time* loads the twelve-month trend window; all-time totals, the
  approval queue and feedback-per-chore are count / group-by queries; the 💎 card loads its
  headline window and sums the rest. `StatsRenderCostTest` pins the rows loaded.
- [x] **SF-4 The home code reaches the log when a retention export fails.**
  `HomeCleanupService.java:226` logs `e.getMessage()` from `Files.writeString(dir.resolve(code
  + "-" + date + ".json"))`. **Confirmed** with `jshell`: every `FileSystemException`
  (`NoSuchFileException`, `AccessDeniedException`, disk full on some providers) uses the path as
  its message, e.g. `NoSuchFileException: data/retention-exports/K7QP4ZT-2026-10-21.json`. An
  unwritable export dir after a deploy (wrong owner, read-only mount) writes one home code per
  inactive home into `logs/flashchores.log`, kept gzipped for 26 weeks. The method's own
  Javadoc says the name "must not leak into logs". *Fix:* log `e.getClass().getSimpleName()`
  and the directory, not the message.
  *Fixed* exactly so.
- [x] **SF-5 The session cookie's `Secure` flag depends on a proxy header the runbook never
  sets.** *Plausible* — verify on the host with `curl -sI https://flashchores.com/ | grep -i
  set-cookie`. `application.properties` sets no `server.servlet.session.cookie.secure` or
  `same-site`; Tomcat marks `JSESSIONID` `Secure` only when `request.isSecure()`, which with
  `server.forward-headers-strategy=native` is true only if the proxy sends
  `X-Forwarded-Proto: https`. Apache `mod_proxy` does not add it by default, and the runbook
  (`README.md` 196-322) has no `RequestHeader set X-Forwarded-Proto "https"` and no
  `Strict-Transport-Security`. *Scenario:* a phone on hotel Wi-Fi follows an `http://` link to
  the site; without HSTS the cookie issued over HTTPS goes out in the clear. *Fix:*
  `server.servlet.session.cookie.secure=true` and `server.servlet.session.cookie.same-site=lax`
  in the production config, plus runbook lines for `X-Forwarded-Proto` and HSTS at the proxy
  (or at Cloudflare).
  *Fixed*: `same-site=lax` in `application.properties`; the host already sends `Secure`,
  `HttpOnly` and HSTS (verified), and the runbook now has the verify step.
- [x] **SF-6 `jackson-databind` is pinned to 2.18.2 while the rest of Jackson 2 resolves at
  2.21.x.** **Confirmed**: `pom.xml:94-101` hard-codes the version for `jackson-databind` and
  `jackson-datatype-jsr310`; `mvn -o dependency:tree` shows `jackson-core 2.21.5` beside them.
  The pin overrides Spring Boot 4.1.1's BOM and freezes databind on a December-2024 patch line
  that no longer moves with Boot's CVE tracking. The pom comment justifies declaring the
  artifact, not the version. *Fix:* drop both `<version>` elements.
  *Fixed*: dropped; databind now resolves at the BOM's version.
- [x] **SF-7 Nothing bounds unauthenticated session creation on the Apache runbook.**
  *Plausible.* Every `GET /` builds an `HttpSession` + `VaadinSession` + `LandingView` that
  lives three minutes; `RateLimiter` guards the three button handlers only. At ~50 req/s that
  is ~9 000 live UIs inside the documented `-Xmx512m`. The optional Cloudflare rate rule
  (README "Behind Cloudflare", step 4) is the only backstop, and the Apache runbook has no
  equivalent. *Fix:* document `mod_evasive` or a Cloudflare rate rule as a required step, or
  lower the unauthenticated session timeout further.
  *Fixed*: the runbook gains a required rate-limit step for the proxy or Cloudflare.
- [x] **SF-8 A push subscription row keeps its first owner forever, and *Leave* never drops
  it.** **Confirmed** by reading. `storeSubscription:244-249` refreshes only the keys on an
  existing endpoint, never `memberId`/`homeCode`; `HomeView.leave():300` forgets the local
  identity only; `ListReminderService.planFor:211` pushes a line's text to every subscription
  of the home. On a shared browser profile, member A of home X enables reminders and leaves; B
  signs into home Y and enables reminders; `pushManager.subscribe` returns the same endpoint,
  so B gets A's named reminders and home X's list-reminder texts. Same device, so low realism,
  but a cross-home leak of free text in principle. *Fix:* set owner and home on the upsert and
  delete the member's rows on Leave.
  *Fixed*: the upsert sets the owner; *Leave* unsubscribes the browser and drops its row.

### Nits

- [ ] **N-1** No session-id rotation on sign-in (`SessionContext.signIn:66`; no
  `reinitializeSession` anywhere). Fixation needs an attacker able to plant a cookie first
  (XSS or a sibling subdomain), neither present; hygiene only. **Confirmed** absent.
- [ ] **N-2** `SecurityHeadersInitListener` sets only `X-Frame-Options` and `nosniff`.
  `Referrer-Policy: strict-origin-when-cross-origin` and a `Permissions-Policy` cost a line
  each and keep `/?join=CODE` out of third-party referrers when a member taps an external
  link from the board. A full CSP is not recommended untested: Vaadin bootstraps with inline
  scripts and `index.html:66` has one too.
- [x] **N-3** `PushReminderService.removeSubscription:253` deletes by a client-supplied
  endpoint with no member scope. Endpoints are unguessable, so only someone already holding
  another device's endpoint could unsubscribe it; `deleteByEndpointAndMemberId` closes it.
  *Fixed* with SF-8.
- [ ] **N-4** `PushEndpoints.isPrivate` relies on `isSiteLocalAddress`, which for IPv6 covers
  only the deprecated `fec0::/10`, not ULA `fc00::/7`; an endpoint whose AAAA record points
  at `fd00::1` passes. Only matters if the host has IPv6-reachable internal services.
- [ ] **N-5** DNS-rebinding window: `resolvesToPublicAddress` resolves, then the HTTP client
  resolves again. The JDK's positive cache narrows it; the payload is encrypted bytes POSTed
  with nothing read back, so impact is low.
- [x] **N-6** `PushEndpoints.resolvesToPublicAddress` does a blocking `getAllByName` on the
  scheduler thread per send; an attacker-chosen host with a dead authoritative server costs
  resolver retries per subscription. Same fix as B-1/SF-2.
  *Fixed* with B-1: the lookup runs inside the bounded exchange.
- [ ] **N-7** H2 unique-constraint messages embed the offending values
  (`PushSubscription.endpoint`, `RejoinRequest.token`); the only path is two simultaneous
  subscribes from one endpoint, and there is no custom `ErrorHandler`, so an uncaught one
  would print an endpoint at WARN. Near-impossible; a custom error handler that logs the
  class only would close it.
- [ ] **N-8** README's real-IP recipe for Cloudflare is for nginx while the runbook is Apache
  (`mod_remoteip`, `RemoteIPHeader CF-Connecting-IP`, `RemoteIPTrustedProxy` ranges). Without
  it every visitor shares a few Cloudflare edge IPs and 10 creates/h locks out the world.
- [ ] **N-9** A stranger holding a home code can redraw every board in that home via
  `requestJoin` (`ChoreService.java:231`) and `cancelRejoin` (`:457`); bounded at 20 + 20 per
  hour per IP, so no real amplification. Likewise five wrong PINs lock a home's PIN check for
  15 minutes, which a stranger with the code and a nickname (rate-limited lookups) can use to
  keep an admin from claiming admin; by design, and cheap to live with.
- [ ] **N-10** `PushReminderService.candidateMemberIds` loads every member with a reminder
  across all homes each minute, then one `findById` per member; O(members with reminders)
  queries a minute, not attacker-cheap. `RateLimiter.allow` rescans all buckets on every call
  once the map exceeds 50 000 keys with nothing expired; reachable only from >50 000 IPs in an
  hour.

## Accepted by design (worth knowing before the post)

- With **approve-rejoin off**, a home code plus a member's nickname signs a device in as that
  member, admin role included (`ChoreService.requestRejoin:379`). The default is on, the
  setting is the home's own, and SPEC §4 says so.
- The in-app backup JSON carries the **admin PIN in clear** (`BackupService.java:223,502`) so
  settings round-trip; it only ever sits on the server's disk and the admin's device.
- The **operator CLI** (`tools/flashchores-admin.py`, `restoreAnyHome`) is guarded by host
  shell access, not by code; it binds `127.0.0.1` on a random port with the service stopped.

## Checked and found clean

- **Client-supplied ids.** Only two DOM channels carry ids: `board-place`
  (`ChoresPanel.java:145`) → `placeChore(homeCode, …)`, which filters task, neighbour and
  group by the session's home (pinned by `ChoreGroupTest.draggingAnotherHomesCard_isRefused`,
  `placingNextToAnotherHomesCard_isRefused`, `HomeUiTest.droppingAnotherHomesCard_isThrownAway`);
  `list-place` (`ListPanel.java:327`) → `placeItem(homeCode, memberId, …)`, which checks
  membership and both lines' home, kind and list. `hold-drag.js` reads only server-stamped
  `data-*` ids; `@ClientCallable onVisible` takes no arguments; `?join=` only prefills a field;
  `retrieveExtendedClientDetails` zone ids go through `ZoneId.of`; theme/palette values are
  allow-listed; the `lang` cookie is a `switch`.
- **Identity.** `restoreFrom` (`LandingView.java:464`) verifies member ∈ home and the device
  secret hash with `MessageDigest.isEqual` before signing in; rejoin and join tokens are
  128-bit random and looked up by token only; the approved path signs in with the request's
  own member and home, validated at request time (`RejoinServiceTest.memberFromAnotherHome_isUnknown`).
  Home codes are 7 characters from a 32-letter alphabet (≈3×10¹⁰); lookups and joins are
  10-20/h per IP; the PIN gate is 5 failures / 15 min per home.
- **Member-on-behalf-of-member.** `complete`, `bookChore`, `cancelBooking`, `undoCompletion`,
  snooze schedule/cancel, `saveReminder`, `updateZone`, `setAvatar` all take the session member
  captured in Java; `undoCompletion:1729` and `cancelBooking:1259` also check ownership
  server-side; `ChoreReminderService.schedule:184` refuses a task/member from different homes;
  every list mutation runs `memberOf(item.homeCode, memberId)`. Stats and credit reads take the
  session member or ids from `membersOf(homeCode)`.
- **Backup/restore.** `restore:167` refuses a file whose code ≠ session home; every id is
  remapped and every string re-clipped; the upload sink is byte-capped server-side
  (`AdminPanel.java:1377-1399`, 512 KiB) independently of the client-side check.
- **SSRF.** `isWellFormed` at store, `resolvesToPublicAddress` before every send (loopback,
  link-local, site-local, CGNAT, 192.0.0/24, multicast and unresolvable refused);
  `PushEndpointsTest` covers loopback-by-name and unresolvable hosts.
- **Logging / PII.** All 12 log statements carry counts, numeric ids, instants or fixed
  strings (SF-4 is the one exception path). `WebPushException` messages are fixed strings
  (checked with `javap` on `flow-webpush-25.3.0`); no entity overrides `toString`; access log
  off; no request-level logging; `show-sql` unset. The observability insight buffer gets only
  exception messages that never embed a code (the two that do are caught and shown to the
  uploading admin), and lives on loopback 8090.
- **Repository hygiene.** `git ls-files` holds no data, db, log, env, key or fixture files;
  `git log --all` never tracked anything under `data/`, `*.mv.db`, `*.log`, `.env`, `*.pem`;
  `git log -S` for private-key markers and `VAPID_PRIVATE=` is empty. `.gitignore` covers
  `/data/`, `/logs/`, `*.log`, `/target/`, `/node_modules/`, generated frontend. VAPID keys
  come from env vars with blank defaults. `tools/release.sh` is `set -euo pipefail`, quoted,
  secret-free; the admin tool uses `subprocess.run(list)` with no shell.
- **Dependencies** (via `mvn -o dependency:tree`): Spring Boot 4.1.1 (Framework 7.0.9, Tomcat
  11.0.24, Logback 1.5.38), Vaadin 25.3.0 (Jackson 3.1.5, jsoup 1.23.2, flow-webpush 25.3.0 →
  interaso webpush 1.3.0), Observability Kit 5.0.0, Hibernate ORM 7.4.5, H2 2.4.240. No CVE
  known to the reviewer (knowledge to June 2026) applies to these lines; run
  `mvn org.owasp:dependency-check-maven:check` and `npm audit` for anything newer. `vaadin-dev`
  is optional and stays out of the production jar; `-Pproduction` sets production mode. H2 is
  file-only with no TCP server and no console. `vaadin.npm.minimumFrontendPackageAgeDays=0`
  disables the supply-chain cooldown at dev time only; re-enable once the Vaadin packages age.
- **Injection / XSS.** No `new Html(`, `setInnerHtml`, `innerHTML` or `setProperty(` in
  `src/main/java`; all 16 `executeJs` calls pass user data as `$0/$1` parameters; `Translations`
  uses bundle text as the `MessageFormat` pattern and user strings only as arguments; charts
  and celebrations use `setText`; `SeoIndexHtmlListener` injects constants and the configured
  base URL; the backup file name is built from the 32-letter code alphabet, so no
  `Content-Disposition` injection; no `@Query`, native SQL or JPQL concatenation.
- **HTTP / config.** Both listeners bind `127.0.0.1`; `/actuator` (health included) is on 8090
  only; Boot 4's error defaults hide message and stack; `forward-headers-strategy=native` is
  safe behind a loopback proxy that appends to `X-Forwarded-For` (a client-supplied value
  cannot become `getRemoteAddr()`); no custom servlet, filter, data-serving `RequestHandler` or
  REST endpoint exists outside Vaadin's CSRF-protected request handling; the backup download
  is a per-resource-keyed, session-bound `StreamResource`.
- **Availability of the authenticated surface.** All `homeState.bump` sites use the session's
  home or the entity's own; drops bump nobody on a no-op; board render cost is pinned by
  `BoardRenderCostTest`; every free-text field is clipped in the service layer (`InputLimits`);
  custom lists ≤ 10; one-shot sweeps use `findTop50`; booking expiry iterates booked tasks
  only; purges are single `DELETE`s; retention is O(homes) nightly; push sends hold no DB
  connection. Frontend document listeners are installed once and passive, the one non-passive
  `touchmove` is added at lift and removed at finish, the `visibilitychange` listener is
  replaced rather than stacked. Sessions close on idle (`closeIdleSessions`, 60 s heartbeat,
  5/20 min timeouts), long-poll resumes every 45 s, Atmosphere pools are budgeted.

## Suggested order

1. B-1 (push timeout), SF-2 (subscription cap inside the loop) — one change, one test.
2. SF-1 (`adminOf` guard) with a negative-path test per gated method.
3. SF-4 (log line), SF-6 (pom), N-2 (two headers) — ten minutes together.
4. SF-5 and SF-7 on the host: cookie flags in the production config, `X-Forwarded-Proto`,
   HSTS and a rate rule in the runbook; then the `curl -sI` check.
5. SF-3 (Stats query by period) and SF-8 (subscription owner) can follow the post.
