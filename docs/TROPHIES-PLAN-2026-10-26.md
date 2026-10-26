# Trophies — plan (2026-10-26)

Status: **built** on branch `trophies` (v1). The decisions below now live in `SPEC.md` (§1,
§2, §3 US-07 and US-63–65, §4.6, §4.7, §4.9, §4.10, §4.11, §6) and the README Features table;
the checklist at the end says what shipped and what changed on the way.

## What exists today

- `ChoreService.MILESTONES` = 5/10/25/50/100/250. When a member's all-time `APPROVED` count
  lands *exactly* on one, the completion outcome carries `milestone` and `Celebrations` shows a
  🏆 dialog with big confetti. That is the whole feature: nothing is stored, nothing can be
  looked at later, and nothing is worth anything.
- **Milestones are lost in approval homes.** `AdminPanel` calls `service.approve(...)` and
  discards the outcome, so when the admin approves a member's 50th chore, nobody celebrates it —
  the member was not on screen, and the admin gets a toast. Same for chores an admin logs for a
  device-less kid from the Admin tab.
- 💎 credits already have the shape we need: `CreditEntry` rows tagged with the
  `completionId` that earned them (undo takes them back), and spree bonuses tagged with
  `spreeTierDays` so a tier is not paid twice.

## The idea in one paragraph

Turn milestones into **trophies you keep**. Every rung of a fixed ladder is a trophy with an
emoji and a short name. A member earns it the moment their approved count reaches it, sees a
proper unlock moment (also when it was earned while they were away), can look at their shelf
in *My stats* with the next trophy and how far it is, and wears their best trophy on their
leaderboard chip. An admin can attach an optional 💎 reward to any rung. A home that sets
nothing gets trophies with no diamonds, and no new setting is required to start.

## Design decisions

### 1. The ladder (fixed, not admin-editable)

| Count | Trophy | Name (en) | Roughly, at 1 chore a day |
|---:|:--:|---|---|
| 5 | 🌱 | Sprout | first week |
| 10 | 🧹 | Helper | 1½ weeks |
| 25 | 🥉 | Bronze | 1 month |
| 50 | 🥈 | Silver | 2 months |
| 100 | 🏆 | Gold | 3 months |
| 250 | 🏅 | Medal of honour | 8 months |
| 500 | 🎓 | Diploma | 1½ years |
| 1000 | 🦄 | Legend | 3 years |

- **Why eight, not the five asked for (10/50/100/500/1000):** the early wins carry the
  motivation (Duolingo front-loads its achievements for the same reason), and the 100→500 gap
  is a year of daily chores with nothing to aim at. 5, 25 and 250 are the milestones the app
  already celebrates; dropping them would take something away from today's users.
- **Fixed, not configurable:** the admin's only lever is the 💎 per rung. An editable ladder
  would need validation, migration of earned trophies when a rung moves, and a harder admin
  screen, for little gain.
- Emoji avoid the glyphs that already mean something: 👑 admin, 🥇 chore master, 💎 credits,
  🔥 spree / third chore, 💪 second chore, 🌟 new chore, ⏳ pending, 🙋 other help.
- The count is the existing all-time `completionCount` (approved chores + accepted other help).
  The badge counter reset (`Home.counterReset`) never touches trophies, as it never touched
  milestones.

### 2. A trophy is *derived*, its 💎 is *stored*

- **Earned = `completionCount ≥ rung`.** No trophy table. Consequences, all wanted:
  existing members own their trophies on the day this ships; undoing the chore that earned a
  trophy takes it back (the mis-tap rule credits already follow); an admin deleting a wrong
  completion corrects the shelf.
- **Earned on** (shown on the shelf) = `doneAt` of the member's *n*-th approved completion —
  one query per earned trophy, asked only when the shelf is drawn, never on the board.
- **The 💎 reward** is a `CreditEntry` (`EARNED`, reason `"trophy"`), tagged with the
  `completionId` that crossed the rung and a new `trophyRung` column (like `spreeTierDays`).
  Paid in `CreditService.award` when the count after this completion equals the rung and no
  `trophyRung` entry for that member and rung exists. Undo deletes it with the completion's
  other credits; re-crossing after an admin deleted an *older* completion does not pay twice.
- **Not retroactive:** members already past a rung on launch day get the trophy, not the 💎.
  Paying out years of rungs the moment an admin types a number would surprise the admin and
  flood balances. (Decision point — see the end.)

### 3. The unlock moment (the rewarding part)

- **Its own dialog**, replacing the milestone dialog: large trophy emoji with a short
  shine/scale-in (CSS, off under `prefers-reduced-motion`), the name, "50 chores done, Emma!",
  and — only when the rung has a reward — a "+20 💎" line. Big confetti as today. Keeps the
  feedback row and Undo the celebration dialog already has. One button, "See my trophies", into
  *My stats*.
- **Never missed:** `Member.trophiesSeen` (int, the highest rung the member has been shown,
  DDL default 0). When the board opens for a member whose highest earned rung is above it —
  approved while away, logged for them by an admin — the dialog shows then; several rungs at
  once become a single "You earned 3 trophies" dialog. The immediate after-tap dialog also
  bumps the marker. On the shared family device the dialog appears at the tap as today, since
  the member is picked there.
- **Launch moment for free:** with the default 0, every existing member's first board visit
  after the upgrade shows "You already have 4 trophies!" — a one-off introduction to the
  feature, no release notes needed.
- **Anticipation, not clutter:** the ordinary celebration dialog adds one line when the next
  trophy is close (3 or fewer to go): "Just 2 more to 🥈 Silver (+20 💎)". No new element
  on the board itself.

### 4. Where trophies are seen

- **Leaderboard chip:** the member's best trophy as a small badge on the corner of the avatar
  dot. Zero extra width, so the compact chips at 360px are unchanged; included in the chip's
  accessible name ("Emma, 12, Silver trophy"). Nothing for a member with no trophy yet.
- **My stats → "🏆 My trophies" card**, first card on the page: the eight rungs as read-only
  tiles (4 × 2 at 360px; no button role, like the 💎 tiles). Earned: full colour, name, date.
  Locked: greyed emoji with the number still at full text contrast, plus its 💎 reward when
  set ("+50 💎" — the same "show what it pays" rule as the card's credit pill). The **next**
  rung is highlighted with a progress bar: "38 / 50 · 12 to go". Shown from the first visit —
  an empty shelf with a progress bar is itself the invitation.
- ~~**Home stats (admin):** each member's fold in *What each member did* gets the member's row
  of earned trophy emoji in its summary.~~ Dropped while building: those folds follow the period
  lens and only exist for members active in it, while trophies are all-time — mixing the two in
  one summary would read as "earned this week". The leaderboard badge already shows the admin
  every member's best trophy.

### 5. Admin: 💎 per trophy

- In the Admin tab's credits section, under spree bonuses: **"Trophy rewards"** — eight rows,
  emoji + "50 chores" + an `IntegerField` for 💎 (empty = 0). Saved on change, clamped by
  `ChoreService.clampCredits`. Same for every member of the home, by design: fair, and one
  place to set it.
- Stored as `TrophyReward(homeCode, rung, diamonds)` rows (only non-zero ones), like
  `SpreeTier`. Round-trips through backup.

### 6. Later, if v1 lands well (not in v1)

- **Printable diploma:** tap an earned trophy on the shelf → a certificate dialog (name, home
  name, trophy, count, date) with *Print*, print CSS only. The fridge version of the 🎓 rung.
- **Family shout-out:** a toast on the other family members' boards, "Emma earned 🥈 Silver!".
  Needs a per-member "seen" for someone else's event and fan-out on push redraw; worth it only
  if families ask.

## Implementation outline

| Area | Change |
|---|---|
| `domain` | `Trophies` (the ladder: rung, emoji, message key; `best(count)`, `next(count)`), replaces `ChoreService.MILESTONES`. `TrophyReward` + repository. `CreditEntry.trophyRung` (default 0). `Member.trophiesSeen` (default 0). |
| `CreditService` | `award(...)` takes the member's count after this completion and pays a trophy reward; `trophyRewards(home)`, `setTrophyReward(home, rung, diamonds, adminId)` — re-checks `members.findAdmin`, refuses a rung not on the ladder. `deleteForHome` clears rewards. |
| `ChoreService` | count before award (reorder in `complete`, `completeFor`, `approve`, admin's own help); `CompleteOutcome.milestone` → `trophy`; `markTrophiesSeen(memberId)`; one grouped count query for the leaderboard (`countApprovedByMember(homeCode)`). |
| `StatsService` | `trophyShelf(memberId)` → earned rungs with dates, next rung, progress. |
| `BackupService` | `TrophyRewardDto` list, `CreditEntryDto.trophyRung`, `MemberDto.trophiesSeen`; an older file restores with `trophiesSeen` = the member's best rung, so a restore does not replay unlock dialogs. |
| UI | `Celebrations` trophy dialog + near-next line; `ChoresPanel` unseen check on render + chip badge; `StatsPanel` trophy card; `AdminPanel` reward rows; `styles.css`; 3 × messages (8 names + ~12 strings). |

### Costs and security (from the review personas, up front)

- **Board render:** the chip badge needs every member's all-time count. Today the leaderboard
  already runs per-member count and balance queries; the badge must come from *one* grouped
  query, and `BoardRenderCostTest` should pin that it adds a constant, not one per member.
- **Shelf:** at most eight indexed single-row queries, only on *My stats*.
- **Tenant isolation:** the shelf reads the session's member only; `setTrophyReward` takes
  the home from the session and the admin id, re-checks admin server-side and whitelists the
  rung. No id from the client.
- **No farming:** complete → undo → complete pays once net (the undo deletes the entry);
  the `trophyRung` check stops a second payout after a re-cross.
- **Reduced motion:** `confetti.js` does not check `prefers-reduced-motion` today; the trophy
  shine must, and it is worth fixing confetti in the same change.

### Tests to add (SPEC §6)

- Reward paid exactly once at the rung; not below it; not when the rung has no reward.
- Undo of the crossing completion removes trophy and 💎; re-doing pays again; re-crossing
  after an older completion is deleted does not pay twice.
- Approval home: no trophy while pending, earned at approval, unlock dialog shown on the
  member's next board visit, and only once.
- Not retroactive: setting a reward does not pay members already past the rung.
- `setTrophyReward` refuses a non-admin, a demoted admin, another home, and an off-ladder rung.
- Backup round-trip of rewards, `trophyRung`, `trophiesSeen`; old file restores without replay.
- Board render cost unchanged per member; UI tests for the shelf (earned / locked / next),
  chip badge accessible name, admin reward rows.

## Decisions (product owner, 2026-10-26)

1. **Ladder:** eight rungs as above. ✅
2. **Retroactive 💎:** no — the admin configures the 💎 per trophy, paid from then on. ✅
3. **Diploma print:** later.

## Checklist

- [x] `Trophy` ladder replaces `ChoreService.MILESTONES`; `CompleteOutcome.trophy` and
      `CompleteOutcome.award` replace `milestone` and the three loose credit fields.
- [x] `TrophyReward` rows, `CreditEntry.trophyRung`, `Member.trophiesSeen` (both new columns
      with DDL defaults).
- [x] Reward paid in `CreditService.award` — exact rung, once per member and rung, revoked with
      the completion.
- [x] Unlock dialog with reward line and *See my trophies*; unseen trophies shown from
      `HomeView` (one dialog for several); "Just N more" line within 3.
- [x] Leaderboard badge from one grouped query (`approvedCountsByMember`), pinned in
      `BoardRenderCostTest`.
- [x] *My trophies* shelf on My stats (also for the member picked on the shared device).
- [x] Admin *Trophy rewards* rows in the Rewards section, saved on change, admin re-checked in
      the service.
- [x] Backup round-trip; pre-trophy files restore with trophies already seen.
- [x] `confetti.js` and the dialog animations respect `prefers-reduced-motion`.
- [x] Three languages; SPEC, README.
- [ ] Printable diploma — later.
- [ ] Family shout-out toast — later, if asked for.
