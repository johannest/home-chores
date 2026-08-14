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
| **Backup / restore** | Admins can download a JSON backup of the whole family (settings, members, chores, completions, credits, spree tiers) and restore from one (replaces current data after a confirmation). |
| **Live sync** | Vaadin **Signals**: each home has a revision signal (`HomeState`) that every open UI observes via `Signal.effect`, delivered over server push (long-polling). Completions, approvals, leaderboard, badges and pending counts update on everyone's screen instantly. |
| **Languages** | English (default), Finnish, Swedish. The browser language picks the initial locale; the header switcher stores the choice in a `lang` cookie. Default chores are seeded in the creator's language. |
| **PWA install** | Installable on Android (install prompt) and iPhone (Share → *Add to Home Screen*): branded icon, standalone display, themed splash screens, offline fallback page. |
| **Privacy page** | A plain-language privacy notice at `/privacy`, linked from the landing page. |

## Running it

Requires **Java 21** and Maven.

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home mvn spring-boot:run
```

Then open http://localhost:8080. On your phone, use your computer's LAN IP
(e.g. `http://192.168.1.42:8080`) so the whole family can join.

**Reset all data:** stop the app and delete the `data/` folder.

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
    ├── LandingView.java          # create / join screen (+ admin-PIN reveal, ?join= links)
    ├── HomeView.java             # header + Chores / Stats / Admin tab host, share links
    ├── ChoresPanel.java          # daily ring, leaderboard, chore cards + badges
    ├── StatsPanel.java           # personal + home charts
    ├── AdminPanel.java           # approvals, settings, members, chores, rewards, backup
    ├── Charts.java               # dependency-free bar / segment / trend charts
    ├── Celebrations.java         # confetti + congratulation + feedback dialogs
    ├── PrivacyView.java          # /privacy notice
    ├── LanguageSwitcher.java     # en/fi/sv select, persisted in a cookie
    ├── SessionContext.java       # who am I / which home / my time zone (VaadinSession)
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

- Members are identified per browser session (a phone = a member). Two people on the
  *same* browser share a session — fine for real use where everyone has their own phone.
- The fairness rule is intentionally per-chore: doing a *different* chore doesn't reset
  your streak on the locked one — someone else has to take a turn. Tweak `MAX_IN_A_ROW`
  in `ChoreService` to change the limit.
- Availability hours are evaluated in each member's **browser time zone**; intervals
  and spree streaks use the server's time zone (a self-hosted family server is
  normally in the household's zone anyway).
- Possible next steps: weekly/monthly leaderboards, undo a mistaken tap, push
  notifications, real accounts.
