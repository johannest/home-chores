# ⚡ FlashChores

Effortless tracking of small household chores. Create a home, share the code (or a
join link) with your family, and tap a button whenever you do a chore. The app keeps
things **fair** (no one can hog the easy chore forever), rewards effort with
**credits**, and celebrates every win.

Built with **Vaadin 25 Flow + Spring Boot 4** (Java 21), an **H2** file database,
**Vaadin Signals + server push** for live sync, and installable as a **PWA** on
iPhone and Android. Available in **English, Finnish and Swedish**.

See [SPEC.md](SPEC.md) for the full user stories and specification.

## Features

| Feature | How it works |
|---|---|
| **Kahoot-style login** | No passwords. *Create a home* → get a 7-character code **and a private admin PIN**. Others *Join a home* with the code and their name, or open a **join link** (`/?join=CODE`) that prefills everything. **Copy link** / **Share** in the header use the clipboard or the native share sheet. |
| **Stays signed in** | Each phone remembers who it is in **browser local storage**, not in a long-lived server session. Sessions can expire (and the server can restart) without anyone being asked to sign in again. |
| **Sign back in** | If a phone clears its browsing data, its identity is gone — so the Join tab offers **"I'm already a member"**: pick yourself from the home's member list and keep your chores, credits and streaks instead of becoming a second "Sam". Admins can require approval for this (default on); knowing the **admin PIN** always skips the wait. |
| **Confirm before counting** | Tapping a chore asks *"did you just do this?"* first — the cards are big and close together on a phone, and stray taps were counting as done. Admins can switch it off per home for speed. |
| **Undo a mis-tap** | The celebration dialog offers *"Oops — undo this"*, and a quiet strip on the board lets a member take back their **own** chore for 10 minutes after doing it. Beyond that it's an admin job. |
| **Admin can unmark** | A **Recent chores** list on the Admin tab unmarks any completion, however old and whoever did it — including one already approved. Any 💎 credits it earned are taken back with it. |
| **Tap-to-complete chores** | Big chore cards under the **Chores** tab. Tap the one you just did. New homes start with **11 localized default chores**. |
| **Fairness rule** | A member may do the *same* chore at most **3 times in a row** (`ChoreService.MAX_IN_A_ROW`); the 4th tap is blocked until *someone else* does it. |
| **Booking ("I'll do it")** | A member can reserve a chore; others are blocked until the booking is completed, cancelled, or expires (admin-configurable hold, 1–24 h, default 4). |
| **Division styles** | *Free-for-all* (default, fair rotation via the streak rule) or *Rotating*: every member gets one assigned chore per day, rotating daily. Rotation can be **enforced** (only your chore) or a highlighted suggestion. |
| **Interval chores** | A chore can repeat every N days (e.g. water plants every 7 days). Until due again, the card shows "🕒 in Nd" and is locked. |
| **Availability hours** | A chore can be limited to times of day in the member's local time (e.g. take the dog out 8–10 and 18–22). Outside the window the card shows "🕒 8–10, 18–22" and taps are blocked. |
| **Celebrations & feedback** | Confetti, a *"New chore unlocked!"* popup, trophy 🏆 milestones at **5/10/25/50/100/250**, and escalating daily praise: the 2nd chore of the day gets 💪, the 3rd+ gets 🔥 with double-burst side-cannon confetti. Every completion also asks *"How was it?"* — 😖 / 🙂 / 😍. |
| **Today filter & done-today** | The board opens on a **Today** lens (anytime chores + interval chores that are due again; a weekly chore done recently stays parked), and a collapsible **"Done today (n)"** list under the progress ring shows who did what, home-wide. |
| **Avatars & chore master** | Members pick an animal avatar from a CC0 set (Kenney's Animal Pack Redux, self-hosted — tap your own leaderboard chip), and last ISO week's most active member wears the **🥇 chore master** badge in the header. |
| **Credits & rewards** | Chores can award **💎 credits** (great for challenging tasks), and admins can define **spree bonuses** (X days in a row → Y credits). Admins **redeem** credits for real-world rewards (e.g. movie night). Balances show on the leaderboard. |
| **Daily target** | The admin sets **1–3 chores expected per member per day** (default 1); each person sees a *done/target* progress ring. |
| **Admin role (PIN)** | The creator is admin. Enter the admin PIN via **Admin?** in the header to (re)claim admin on any device. Admins can promote others. |
| **Admin CRUD** | Under the **Admin** tab: add/edit/delete chores (name, emoji, interval, credits, hours), rename/remove members, promote/demote admins, rename the home, change the PIN. |
| **Optional approval** | Admins can require approval. Completions then wait as **pending** until an admin **approves** (counts) or **rejects** (discarded). A badge shows the pending count. |
| **Statistics & charts** | The **Stats** tab shows personal charts (chores by type, feedback split, 7-day trend). Admins also get **Home stats**: per-member totals, chore popularity, feedback per chore, 14-day activity, daily-goal adherence. Charts are dependency-free (no licensed add-on). |
| **Delete the home** | A **Danger zone** at the bottom of the Admin tab wipes the whole family — members, chores, completions, credits, settings. Confirmed by typing the home code, and it prompts for a backup first. Everyone still on the board is signed out live. |
| **Retention (opt-in)** | Tracks when each home was last *used* (a chore, a review, opening the board — not background traffic). Three windows: `empty-home-hours` (72h in prod config) and `abandoned-home-days` purge homes that were never used (**no chore history and at most one member**); `inactive-home-days` (30 in prod config) additionally deletes **any** home nobody has used that long — after writing a full JSON safety export to `retention.export-dir` (the operator's undo; export failure keeps the home). `/terms` and `/privacy` state the windows automatically. Off when all are 0. |
| **Backup / restore** | Admins can download a JSON backup of the whole family (settings, members, chores, completions, credits, spree tiers) and restore from one (replaces current data after a confirmation). |
| **Live sync** | Vaadin **Signals**: each home has a revision signal (`HomeState`) that every open UI observes via `Signal.effect`, delivered over server push (long-polling). Completions, approvals, leaderboard, badges and pending counts update on everyone's screen instantly. |
| **Languages** | English (default), Finnish, Swedish. The browser language picks the initial locale; the header switcher stores the choice in a `lang` cookie. Default chores are seeded in the creator's language. |
| **Built for phones** | Laid out for a ~360px column first: no horizontal scrolling anywhere, safe-area padding for the notch and home indicator, `100dvh` against iOS Safari's collapsing URL bar, 40px touch targets, and no sticky `:hover` states after a tap. The board header collapses Copy/Share to icons and stacks onto two rows so chores are visible without scrolling. See SPEC §4.15.1. |
| **PWA install** | Installable on Android (install prompt) and iPhone (Share → *Add to Home Screen*): branded icon, standalone display, themed splash screens, offline fallback page. |
| **Privacy page** | A plain-language privacy notice at `/privacy` and a brief user agreement at `/terms` (consented via a checkbox on the create/join forms), both linked from the landing page. |
| **Dark mode** | Follows the OS theme automatically (`@ColorScheme(SYSTEM)` + CSS `light-dark()`), with a per-device auto/light/dark selector in the header (stored in localStorage). |
| **Chore reminders (opt-in)** | Real Web Push: each member can pick a wall-clock time (their own timezone) and get a notification on subscribed devices if they haven't logged any chores that day. Requires VAPID keys (see below); on iPhone/iPad it works once the PWA is added to the Home Screen (iOS 16.4+). |

## Running it

Requires **Java 21** and Maven.

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home mvn spring-boot:run
```

Then open http://localhost:8080. On your phone, use your computer's LAN IP
(e.g. `http://192.168.1.42:8080`) so the whole family can join.

**Reset all data:** stop the app and delete the `data/` folder. (To wipe a single family
without touching the others, use *Admin → Danger zone → Delete this home* in the app.)

**Operator maintenance (`tools/flashchores-admin.py`):** for erasure requests you have to
service yourself — a lost admin PIN, a legal escalation — rather than the family doing it
in-app. Run it on the host with the service **stopped** (H2 locks the database file); it
refuses rather than racing a running instance.

```bash
sudo systemctl stop flashchores
./tools/flashchores-admin.py list                 # every home, most idle first
./tools/flashchores-admin.py show K7QP4ZT         # members, chores, history, last use
./tools/flashchores-admin.py export K7QP4ZT       # JSON backup
./tools/flashchores-admin.py delete K7QP4ZT       # backs up first, asks you to type the code
./tools/flashchores-admin.py restore data/retention-exports/K7QP4ZT-2026-08-01.json
./tools/flashchores-admin.py purge --days 30 --dry-run
sudo systemctl start flashchores
```

It starts the app's own code in a one-shot maintenance mode (ephemeral loopback port,
shuts itself down), so `delete` goes through the same cascade as the in-app Danger zone —
no hand-written SQL, and nothing new listening on the internet. `delete` writes a backup
to `data/erasure-exports/` first unless you pass `--no-backup`; `restore` is how you put
one back (it asks you to type the code, and refuses to overwrite a home that still exists
without `--overwrite`). Keep the exports as evidence the
request was honoured, and as your undo. Needs the runnable jar (`--jar`, `FLASHCHORES_JAR`,
or newest in `target/`). `--db-url` points it at another database, e.g. a restored copy.

**Retention on a public instance:** `homechores.retention.empty-home-hours=72` (fast
anti-spam tier) and `abandoned-home-days=30` clear out never-used sign-ups (zero chore
history, at most one member) nightly; `homechores.retention.cron` sets when. On top of
that, `inactive-home-days=30` deletes **any** home nobody has opened or used for 30 days,
history and all. Because that can hit a real family away for a long stretch, every home
with chore history is exported to `data/retention-exports/` right before deletion — keep
those as the undo — `./tools/flashchores-admin.py restore <file>` puts the family's board
back, PIN and history included, and stamps it active so the next sweep leaves it alone.
(The in-app restore cannot do this one: it needs an admin signed into the home, and a
purged home has none.) A home whose export fails is kept. `/privacy` and `/terms` state the configured windows
automatically, so the notices can't drift from the settings. A private family server that
wants no auto-deletion sets all three windows to 0.

**Web Push reminders:** generate a VAPID key pair once with
`npx web-push generate-vapid-keys` and supply it via environment variables —
`FLASHCHORES_VAPID_PUBLIC`, `FLASHCHORES_VAPID_PRIVATE`, and optionally
`FLASHCHORES_VAPID_SUBJECT` (a `mailto:` or `https:` URL). Without the keys the whole
subsystem is inert and the reminder bell never shows. Subscriptions are per device,
stored server-side, pruned automatically when the push service reports them gone, and
never included in backups.

**npm cooldown note:** Vaadin 25.2 skips npm packages published less than a day ago
(supply-chain cooldown), which can break the frontend install when Vaadin's own
packages are fresh. The workaround `vaadin.npm.minimumFrontendPackageAgeDays=0` is
already configured (in `application.properties` and the `spring-boot` plugin); pass it
as `-Dvaadin.npm.minimumFrontendPackageAgeDays=0` if you invoke other Maven goals that
build the frontend.

**Production build (optimized frontend, executable jar):**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home mvn clean package -Pproduction
java -jar target/flashchores-1.0.0.jar
```

The `-Pproduction` profile is required for a runnable jar — without it the jar boots
in dev mode and fails on the missing dev server.

### Running in production on a low-process host

The target host allows 100 processes, and on Linux that limit counts **threads** — every
Java thread is a task against `RLIMIT_NPROC` / cgroup `pids.max`. Run the jar with:

```bash
java -XX:ActiveProcessorCount=2 -XX:+UseSerialGC -Xmx512m -jar target/flashchores-1.0.0.jar
```

Measured on this jar: **29 threads whether idle or serving 600 concurrent requests**, of
which 12 are the bare JVM floor. What each part buys:

| Setting | Effect |
|---|---|
| `-XX:ActiveProcessorCount=2` | Caps GC, JIT and virtual-thread carrier parallelism. Also the reason there are 2 carriers. |
| `-XX:+UseSerialGC` | Drops G1's six threads (`GC Thread`×2, `G1 Service`, `G1 Refine`, `G1 Main Marker`, `G1 Conc`). The live set is tens of MB, so serial pauses stay trivial. |
| `-Xmx512m` | Comfortable under the host's 2 GB limit: ~76 MB RSS idle, ~290 MB after a 600-request burst (SerialGC is not eager about returning it). Lower it if you want a tighter ceiling. |
| `spring.threads.virtual.enabled=true` (in `application.properties`) | Removes Tomcat's growable exec pool, so thread count no longer tracks traffic. |

Verify on the host with `ls /proc/<pid>/task | wc -l` (JVM threads) and
`cat /sys/fs/cgroup/pids.current` (what the limit actually counts).

Two things deliberately **not** done: `-XX:TieredStopAtLevel=1 -XX:CICompilerCount=1` saves
one thread but disables C2 and made startup slower (2.5 s → 3.6 s), and a GraalVM native
image trades away JIT peak throughput for startup and memory wins this host does not need.

### Behind Cloudflare (free tier)

The app is Cloudflare-ready (the repo side is `vaadin.pushLongPollingSuspendTimeout=80000`
in `application.properties` — Cloudflare kills idle requests at ~100 s, and the suspended
long-poll would otherwise sit open forever). Everything else is dashboard/proxy work:

1. **DNS & TLS**: proxy (orange-cloud) the apex + `www`; topology stays
   CF → your TLS reverse proxy → `127.0.0.1:8080`. Set SSL mode to **Full (strict)** and
   install a free Cloudflare **Origin CA certificate** on the proxy (or keep Let's
   Encrypt via DNS-01). Recommended: firewall port 443 to [Cloudflare's IP
   ranges](https://www.cloudflare.com/ips/) so the origin can't be reached around CF.
2. **Real client IP — required, or the in-app rate limiter breaks.** Restore it at the
   reverse proxy from `CF-Connecting-IP`; for nginx:
   ```
   # one line per range from https://www.cloudflare.com/ips/ (refresh occasionally)
   set_real_ip_from 173.245.48.0/20;
   # ... all other Cloudflare ranges ...
   real_ip_header CF-Connecting-IP;
   ```
   `set_real_ip_from` means the header is only honored when the TCP peer really is
   Cloudflare, so it can't be spoofed by direct hits. The app then needs no changes
   (`server.forward-headers-strategy=native` keeps working). Without this, every visitor
   shares a few CF edge IPs and one spammer's rate limit throttles everyone.
3. **Cache Rules** (3 of the 10 free): `/VAADIN/build/*` → cache, edge TTL 1 year
   (content-hashed bundles; never cache `/VAADIN/` more broadly — the push endpoint lives
   under it); `/icons/*` and `/avatars/*` → cache, 1 month; **bypass** `/sw.js`,
   `/styles.css`, `/manifest.webmanifest` (stable un-hashed URLs — a stale edge-cached
   `sw.js` is the classic broken-PWA-update failure). Do not enable "Cache Everything".
4. **Protection**: the Free Managed WAF ruleset is on by default. Bot Fight Mode is worth
   a trial but is zone-wide and unscopable — if installed-PWA requests start getting
   challenged, turn it off. Configure the one free **rate-limiting rule** as a coarse
   backstop only, e.g. `POST` to `/` above ~60 requests/10 s per IP → block; anything
   more aggressive breaks the app's own UIDL/heartbeat traffic (the real business limits
   live in the in-app `RateLimiter`). "Under Attack" mode is the emergency lever; it
   temporarily breaks already-open boards until reload. Cloudflare Turnstile on the
   create-home form is a possible later addition.
5. **Verify after cutover**: two devices on different networks get *separate* rate-limit
   buckets (create 10 homes from one network — the other must not be blocked), push still
   updates a second device live, and the PWA updates after a redeploy.

## Project structure

```
src/main/java/com/homechores/
├── Application.java              # Spring Boot entry: @Push, @PWA, Lumo + styles.css
├── domain/                       # JPA entities + repositories
│   ├── Home / Member / ChoreTask / Completion
│   ├── CreditEntry / SpreeTier   # credit rewards
│   ├── RejoinRequest.java        # a device asking to sign back in as an existing member
│   ├── TimeWindows.java          # availability-hours parsing & evaluation
│   └── (enums: CompletionStatus, Feedback, DivisionStyle, CreditType)
├── service/
│   ├── ChoreService.java         # create/join, admin/PIN, complete, fairness, booking,
│   │                             #   rotation, intervals, availability, approvals, milestones
│   ├── CreditService.java        # chore credits, spree bonuses, balances, redemption
│   ├── StatsService.java         # chart aggregations (my stats + home stats)
│   ├── BackupService.java        # per-home JSON export / restore
│   └── HomeState.java            # per-home revision Signal (live sync)
├── i18n/
│   ├── Translations.java         # I18NProvider over messages[_fi|_sv].properties
│   └── LocaleInitListener.java   # applies the "lang" cookie to new sessions
└── ui/
    ├── LandingView.java          # create / join screen (+ admin-PIN reveal, ?join= links,
    │                             #   identity restore, "sign me back in" picker + waiting)
    ├── DeviceIdentity.java       # member id kept in the browser's local storage
    ├── HomeView.java             # header + Chores / Stats / Admin tab host, share links
    ├── ChoresPanel.java          # daily ring, leaderboard, chore cards + badges
    ├── StatsPanel.java           # personal + home charts
    ├── AdminPanel.java           # approvals, settings, members, chores, rewards, backup,
    │                             #   danger zone (delete the whole home)
    ├── Charts.java               # dependency-free bar / segment / trend charts
    ├── Celebrations.java         # confetti + congratulation + feedback dialogs
    ├── PrivacyView.java          # /privacy notice
    ├── LanguageSwitcher.java     # en/fi/sv select, persisted in a cookie
    ├── SessionContext.java       # who am I / which home / my time zone (VaadinSession)
    │                             #   — short-lived; DeviceIdentity is what outlives it
    └── T.java                    # small translation helper
src/main/resources/
├── messages[_fi|_sv].properties  # UI texts (all three languages)
├── META-INF/resources/styles.css # app styling (Lumo tweaks + components)
└── META-INF/resources/icons/     # PWA icons + iOS splash screens (generated set)
src/main/frontend/confetti.js     # self-contained confetti (no CDN)
src/test/java/com/homechores/     # JUnit service tests + Vaadin UI unit tests
```

## Tests

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home mvn clean test
```

(Use `clean` — incremental builds can leave stale compiled classes behind.)

Service tests (fairness, booking, rotation, intervals, availability windows, approval,
credits/sprees, stats, backup round-trip, admin/PIN, localized seeding) run against an
in-memory H2 database; UI tests use Vaadin's browserless **UI Unit Testing**
(`SpringUIUnitTest`). *(The `vaadin-charts-flow` test dependency is only there so the
test base class's `test(Chart)` overload resolves during JUnit scanning — the app never
uses Charts.)*

## Credits

- Member avatars are Kenney's **Animal Pack Redux** (round faces), released under
  **CC0 1.0** — thank you, [Kenney](https://kenney.nl)! (Credit is appreciated but not
  required; the license ships alongside the images in
  `src/main/resources/META-INF/resources/avatars/LICENSE.txt`.)

## Notes & ideas for later

- Members are identified per device (a phone = a member): the member id lives in the
  browser's local storage and is restored into a fresh session on every visit. Two people
  on the *same* browser share one identity — fine for real use where everyone has their
  own phone. Server sessions are deliberately left at Spring's 30-minute default, so an
  idle phone costs the server nothing.
- Clearing browsing data is the one thing local storage doesn't survive; the
  "I'm already a member" flow on the Join tab is the recovery path. It's gated by
  `Home.approveRejoin` (default on) because the home code travels in join links — without
  the gate, anyone holding one could step into a member's identity. The admin PIN bypasses
  the gate but doesn't grant admin by itself; the header's "Admin?" action still does that.
- The fairness rule is intentionally per-chore: doing a *different* chore doesn't reset
  your streak on the locked one — someone else has to take a turn. Tweak `MAX_IN_A_ROW`
  in `ChoreService` to change the limit.
- Availability hours are evaluated in each member's **browser time zone**; intervals
  and spree streaks use the server's time zone (a self-hosted family server is
  normally in the household's zone anyway).
- Possible next steps: weekly/monthly leaderboards, real accounts.
