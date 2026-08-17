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
| **Celebrations & feedback** | Confetti, a *"New chore unlocked!"* popup, and trophy 🏆 milestones at **5/10/25/50/100/250**. Every completion also asks *"How was it?"* — 😖 / 🙂 / 😍. |
| **Credits & rewards** | Chores can award **💎 credits** (great for challenging tasks), and admins can define **spree bonuses** (X days in a row → Y credits). Admins **redeem** credits for real-world rewards (e.g. movie night). Balances show on the leaderboard. |
| **Daily target** | The admin sets **1–3 chores expected per member per day** (default 1); each person sees a *done/target* progress ring. |
| **Admin role (PIN)** | The creator is admin. Enter the admin PIN via **Admin?** in the header to (re)claim admin on any device. Admins can promote others. |
| **Admin CRUD** | Under the **Admin** tab: add/edit/delete chores (name, emoji, interval, credits, hours), rename/remove members, promote/demote admins, rename the home, change the PIN. |
| **Optional approval** | Admins can require approval. Completions then wait as **pending** until an admin **approves** (counts) or **rejects** (discarded). A badge shows the pending count. |
| **Statistics & charts** | The **Stats** tab shows personal charts (chores by type, feedback split, 7-day trend). Admins also get **Home stats**: per-member totals, chore popularity, feedback per chore, 14-day activity, daily-goal adherence. Charts are dependency-free (no licensed add-on). |
| **Delete the home** | A **Danger zone** at the bottom of the Admin tab wipes the whole family — members, chores, completions, credits, settings. Confirmed by typing the home code, and it prompts for a backup first. Everyone still on the board is signed out live. |
| **Retention (opt-in)** | Tracks when each home was last *used* (a chore, a review, opening the board — not background traffic) and can purge homes that were created and abandoned before anyone used them: **no chore history at all and at most one member**. Homes a family actually used are never auto-deleted, at any age. Off unless `homechores.retention.abandoned-home-days` > 0. |
| **Backup / restore** | Admins can download a JSON backup of the whole family (settings, members, chores, completions, credits, spree tiers) and restore from one (replaces current data after a confirmation). |
| **Live sync** | Vaadin **Signals**: each home has a revision signal (`HomeState`) that every open UI observes via `Signal.effect`, delivered over server push (long-polling). Completions, approvals, leaderboard, badges and pending counts update on everyone's screen instantly. |
| **Languages** | English (default), Finnish, Swedish. The browser language picks the initial locale; the header switcher stores the choice in a `lang` cookie. Default chores are seeded in the creator's language. |
| **Built for phones** | Laid out for a ~360px column first: no horizontal scrolling anywhere, safe-area padding for the notch and home indicator, `100dvh` against iOS Safari's collapsing URL bar, 40px touch targets, and no sticky `:hover` states after a tap. The board header collapses Copy/Share to icons and stacks onto two rows so chores are visible without scrolling. See SPEC §4.15.1. |
| **PWA install** | Installable on Android (install prompt) and iPhone (Share → *Add to Home Screen*): branded icon, standalone display, themed splash screens, offline fallback page. |
| **Privacy page** | A plain-language privacy notice at `/privacy`, linked from the landing page. |

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
./tools/flashchores-admin.py purge --days 30 --dry-run
sudo systemctl start flashchores
```

It starts the app's own code in a one-shot maintenance mode (ephemeral loopback port,
shuts itself down), so `delete` goes through the same cascade as the in-app Danger zone —
no hand-written SQL, and nothing new listening on the internet. `delete` writes a backup
to `data/erasure-exports/` first unless you pass `--no-backup`; keep those as evidence the
request was honoured, and as your undo. Needs the runnable jar (`--jar`, `FLASHCHORES_JAR`,
or newest in `target/`). `--db-url` points it at another database, e.g. a restored copy.

**Retention on a public instance:** set
`homechores.retention.abandoned-home-days=30` (and optionally
`homechores.retention.cron`) to clear out abandoned sign-ups nightly. The rule only ever
matches homes with **zero** chore history and at most one member, so a real family's board
is never at risk — deliberately, because the app has no email or push channel and so no
way to warn anyone first. `/privacy` states the configured window automatically, so the
notice can't drift from the setting.

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
- Possible next steps: weekly/monthly leaderboards, undo a mistaken tap, push
  notifications, real accounts.
