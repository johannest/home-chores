package com.homechores.ui;

import com.homechores.domain.Cadence;
import com.homechores.domain.ChoreGroup;
import com.homechores.domain.ChoreTask;
import com.homechores.domain.Completion;
import com.homechores.domain.DivisionStyle;
import com.homechores.domain.Home;
import com.homechores.domain.InputLimits;
import com.homechores.domain.Member;
import com.homechores.domain.Seasons;
import com.homechores.domain.TimeWindows;
import com.homechores.domain.Trophy;
import com.homechores.service.ChoreReminderService;
import com.homechores.service.ChoreService;
import com.homechores.service.CreditService;
import com.homechores.service.PushReminderService;
import com.homechores.service.SharedDeviceService;
import com.homechores.service.WebPushSender;
import com.homechores.service.ChoreService.CompleteOutcome;
import com.homechores.service.ChoreService.LockReason;
import com.homechores.service.ChoreService.TaskView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** The chore board: daily progress, leaderboard, and tap-to-complete chore cards. */
class ChoresPanel extends VerticalLayout {

    private final ChoreService service;
    private final CreditService creditService;
    private final ChoreReminderService snoozes;
    private final PushReminderService reminders;
    private final WebPushSender pushSender;
    private final SharedDeviceService sharedDevices;
    private final Viewer viewer;

    /** taskId → when its nudge fires, refreshed once per board render. */
    private final Map<Long, Instant> armedReminders = new java.util.HashMap<>();
    private final String homeCode;
    /** The member this board is drawn for; null on the shared device, where every "mine"
     *  check reads as "not mine" and who did a chore is picked at the tap. */
    private final Long memberId;

    /** The last chore logged from the shared device while it can still be taken back: the
     *  device has no member to ask {@code undoableCompletion} for, so it remembers its own. */
    private record FromDevice(Long completionId, Long memberId) {
    }

    private FromDevice lastFromDevice;

    private final Div dailyStrip = new Div();
    private final Div doneTodayHolder = new Div();
    private final Div undoStrip = new Div();
    private final Div leaderboard = new Div();
    private final Div filterBar = new Div();
    /** The board's group sections: a heading and a .task-grid per section. A home with no groups
     *  gets exactly one unheaded grid, so nothing about it changes. */
    private final Div board = new Div();

    /**
     * One narrowing lens over the board. Declaration order is the order the chips render in.
     *
     * <p>Lenses, not a partition: an off-season chore shows under both {@link #OFF_SEASON} and its
     * own cadence chip, so the counts would not add up to the number of chores. That is why the
     * chips carry no count pills.
     */
    private enum Filter {
        ALL(null, "board.filter.all"),
        TODAY(null, "board.filter.today"),
        ANYTIME(Cadence.ANYTIME, "board.filter.anytime"),
        DAILY(Cadence.DAILY, "board.filter.daily"),
        WEEKLY(Cadence.WEEKLY, "board.filter.weekly"),
        MONTHLY(Cadence.MONTHLY, "board.filter.monthly"),
        MULTI_MONTH(Cadence.MULTI_MONTH, "board.filter.multiMonth"),
        YEARLY(Cadence.YEARLY, "board.filter.yearly"),
        OFF_SEASON(null, "board.filter.offSeason");

        private final Cadence cadence;
        private final String key;

        Filter(Cadence cadence, String key) {
            this.cadence = cadence;
            this.key = key;
        }
    }

    /**
     * The member's chosen lens. A plain field is enough: HomeView builds this panel once per
     * navigation and holds it, so it survives every HomeState rebuild — the same trick
     * StatsPanel uses for its sub-tab.
     *
     * <p>TODAY is the default: the board opens on "what is on today's list". When TODAY
     * would select everything its chip is suppressed and the vanished-chip reset below
     * lands on ALL — which renders the identical board, so nothing is lost.
     */
    private Filter filter = Filter.TODAY;

    /** Whether the "Done today" list is expanded — same plain-field trick as the lens. */
    private boolean doneTodayOpen = false;

    /** Opens this member's trophy shelf; set by {@link HomeView}, which owns the tabs. */
    private Runnable showTrophies = () -> { };

    /** From this many members the leaderboard drops names into tooltips (see renderLeaderboard). */
    static final int COMPACT_LEADERBOARD_FROM = 4;

    ChoresPanel(ChoreService service, CreditService creditService, ChoreReminderService snoozes,
                PushReminderService reminders, WebPushSender pushSender,
                SharedDeviceService sharedDevices, Viewer viewer) {
        this.service = service;
        this.creditService = creditService;
        this.snoozes = snoozes;
        this.reminders = reminders;
        this.pushSender = pushSender;
        this.sharedDevices = sharedDevices;
        this.viewer = viewer;
        this.homeCode = viewer.homeCode();
        this.memberId = viewer.memberId();
        setPadding(false);
        setSpacing(false);
        setWidthFull();

        leaderboard.addClassName("leaderboard");
        // "scroll": one row that pans sideways. Up to nine lenses used to wrap three rows deep
        // (four in Finnish) on a phone, all of it above the first chore. SnoozeDialog reuses the
        // plain .filter-bar recipe for its six offsets, where wrapping is what you want.
        filterBar.addClassNames("filter-bar", "scroll");
        board.addClassName("board-sections");

        undoStrip.setVisible(false);
        // Hidden until there is something worth choosing between, so a simple home looks
        // exactly as it did before.
        filterBar.setVisible(false);
        add(dailyStrip, doneTodayHolder, undoStrip, sectionLabel(T.tr("board.leaderboard")),
                leaderboard, sectionLabel(T.tr("board.tapPrompt")), filterBar, board);

        // Where a held-and-dragged card was let go (board-drag.js). The ids arrive as strings —
        // an empty one means "no neighbour" / "ungrouped" — and both the admin check and the home
        // are re-established here rather than trusted from the client: the class that enables the
        // gesture is a hint to the browser, not an authorisation, and the ids could be anyone's.
        board.getElement().addEventListener("board-place", e -> place(
                        ClientId.parse(e.getEventData().path("event.detail.taskId").asString()),
                        ClientId.parse(e.getEventData().path("event.detail.groupId").asString()),
                        ClientId.parse(e.getEventData().path("event.detail.beforeTaskId").asString())))
                .addEventData("event.detail.taskId")
                .addEventData("event.detail.groupId")
                .addEventData("event.detail.beforeTaskId");
    }

    void onShowTrophies(Runnable showTrophies) {
        this.showTrophies = showTrophies;
    }

    /**
     * Puts a dragged chore where it was dropped: into the section {@code groupId} names, before
     * {@code beforeTaskId} (null = that section's end). Admins only — a member's drop is ignored
     * outright: their board never lifts a card, so there is no preview to snap back, and an
     * event forged from a member's browser earns no server work beyond this check.
     */
    void place(Long taskId, Long groupId, Long beforeTaskId) {
        boolean admin = service.findMember(memberId).map(Member::isAdmin).orElse(false);
        if (!admin || taskId == null) {
            return;
        }
        if (!service.placeChore(homeCode, taskId, groupId, beforeTaskId)) {
            refresh(); // nothing changed server-side, so no push: undo the preview by hand
        }
        // Otherwise the HomeState bump redraws this board along with everyone else's.
    }

    private Div sectionLabel(String text) {
        Div d = new Div();
        d.setText(text);
        d.addClassName("section-label");
        return d;
    }

    void refresh() {
        boolean admin = service.findMember(memberId).map(Member::isAdmin).orElse(false);
        // The daily target is one person's; the device's board shows the family's day instead
        // (the Done today list below it).
        dailyStrip.setVisible(!viewer.isDevice());
        if (!viewer.isDevice()) {
            renderDaily();
        }
        renderDoneToday();
        renderUndo();
        renderLeaderboard();
        renderTasks(admin);
    }

    /** The collapsible home-wide "Done today" list, directly under the daily strip. */
    private void renderDoneToday() {
        doneTodayHolder.removeAll();
        List<ChoreService.DoneToday> rows = service.doneTodayList(homeCode);
        doneTodayHolder.setVisible(!rows.isEmpty());
        if (rows.isEmpty()) {
            return; // no empty shell — the section earns its place by having content
        }
        Details section = new Details(T.tr("board.doneToday", rows.size()));
        section.addClassName("done-today");
        for (ChoreService.DoneToday row : rows) {
            Completion c = row.completion();
            Div line = new Div();
            line.setText(row.memberName() + " — " + row.text());
            line.getStyle().set("font-weight", "600");
            String pending = c.getStatus() == com.homechores.domain.CompletionStatus.PENDING
                    ? "  ·  ⏳ " + T.tr("board.doneToday.pending") : "";
            Span sub = new Span(T.ago(c.getDoneAt()) + pending
                    + (c.getFeedback() != null ? "  ·  " + c.getFeedback().getEmoji() : ""));
            sub.addClassName("sub");
            Div info = new Div(line, sub);
            info.addClassName("grow");
            Div rowEl = new Div(info);
            rowEl.addClassName("list-row");
            section.add(rowEl);
        }
        // Order matters: setOpened before the listener, or the programmatic restore would
        // be recorded as the member's own choice (see AdminPanel's sections for the trick).
        section.setOpened(doneTodayOpen);
        section.addOpenedChangeListener(e -> {
            if (e.isFromClient()) {
                doneTodayOpen = e.isOpened();
            }
        });
        doneTodayHolder.add(section);
    }

    private void renderDaily() {
        dailyStrip.removeAll();
        Home home = service.findHome(homeCode).orElseThrow();
        int target = home.getDailyTargetPerMember();
        long done = service.doneToday(memberId);

        Div ring = new Div();
        ring.addClassName("daily-ring");
        ring.setText(done + "/" + target);
        double pct = target == 0 ? 0 : Math.min(1.0, (double) done / target) * 100;
        // The brand colour comes from the token, not a literal: this ring is the first element on
        // the board, and a hardcoded green here is what a per-device palette cannot reach.
        // Only the partial state needs an inline gradient — the filled state is a flat brand fill,
        // which lives in CSS with the .done class rather than as an inline style overriding it.
        if (done >= target) {
            ring.addClassName("done");
        } else {
            ring.getStyle().set("background", "conic-gradient(var(--lumo-primary-color) " + pct
                    + "%, var(--lumo-contrast-10pct) " + pct + "%)");
        }

        Div text = new Div();
        String msg = done >= target
                ? T.tr("board.goalReached")
                : T.tr("board.goal", target, target - done);
        text.setText(msg);
        text.getStyle().set("font-weight", "600");

        Div strip = new Div(ring, text);
        strip.addClassName("daily-strip");
        dailyStrip.add(strip);
    }

    private void renderLeaderboard() {
        leaderboard.removeAll();
        Home home = service.findHome(homeCode).orElse(null);
        // The badge counts since the home's reset period / last manual reset; stats do not.
        Map<Long, Long> counts = new LinkedHashMap<>();
        List<Member> ranked = new ArrayList<>(service.membersOf(homeCode));
        for (Member m : ranked) {
            counts.put(m.getId(), home == null
                    ? service.completionCount(m.getId()) : service.badgeCount(m.getId(), home));
        }
        // Highest count first; ties keep the join order (List.sort is stable), so a fresh home
        // with everyone at zero still lists the family in the order it was built.
        ranked.sort((a, b) -> Long.compare(counts.get(b.getId()), counts.get(a.getId())));
        // Trophies follow the all-time count whatever the badge counts, so they come from one
        // grouped query rather than another per-member count on every redraw.
        Map<Long, Long> allTime = service.approvedCounts(homeCode);
        // Four or more members is where the chips stopped fitting: on a phone each named chip took
        // a row of its own, and a family of five spent 200px on the board's ranking before a single
        // chore. Compact chips keep the face (avatar or initials), the count and the credits, and
        // move the name into the tooltip and accessible name — the name Span stays in the DOM,
        // hidden by CSS, so nothing reading the chip changes.
        boolean compact = ranked.size() >= COMPACT_LEADERBOARD_FROM;
        if (compact) {
            leaderboard.addClassName("compact");
        } else {
            leaderboard.removeClassName("compact");
        }
        for (Member m : ranked) {
            long count = counts.get(m.getId());
            Trophy best = Trophy.best(allTime.getOrDefault(m.getId(), 0L)).orElse(null);

            Div dot = MemberAvatar.dot(m);
            Div face = dot;
            if (best != null) {
                // On the avatar's corner rather than beside it: no extra width, so compact chips
                // still fit three to a row at 360px. The dot clips its contents, hence the wrapper.
                Span badge = new Span(best.emoji());
                badge.addClassName("trophy-badge");
                String label = T.tr("trophy.aria", T.tr(best.nameKey()));
                badge.getElement().setAttribute("role", "img");
                badge.getElement().setAttribute("aria-label", label);
                badge.setTitle(label);
                face = new Div(dot, badge);
                face.addClassName("avatar-wrap");
            }

            Span nameEl = new Span(m.getName());
            nameEl.addClassName("name");
            if (m.isAdmin()) {
                Span crown = new Span("👑");
                crown.addClassName("crown");
                nameEl.add(crown);
            }
            Span countEl = new Span(String.valueOf(count));
            countEl.addClassName("count");

            Div chip = new Div(face, nameEl, countEl);
            int credits = creditService.balance(m.getId());
            if (credits > 0) {
                Span creditEl = new Span("💎 " + credits);
                creditEl.addClassName("credit-chip");
                chip.add(creditEl);
            }
            chip.addClassName("member-chip");
            String trophyText = best == null ? ""
                    : ", " + T.tr("trophy.aria", T.tr(best.nameKey()));
            if (compact) {
                chip.setTitle(m.getName() + trophyText);
                chip.getElement().setAttribute("aria-label", m.getName() + " " + count + trophyText);
            }
            if (m.getId().equals(memberId)) {
                chip.addClassName("me");
                // Your own chip is the door to your avatar — the most discoverable spot
                // there is, since it's literally the picture being changed.
                chip.addClickListener(e ->
                        new AvatarPickerDialog(service, m, this::refresh).open());
            }
            leaderboard.add(chip);
        }
    }

    private void renderTasks(boolean admin) {
        Home home = service.findHome(homeCode).orElse(null);
        boolean rotating = home != null && home.getDivisionStyle() == DivisionStyle.ROTATING;
        // One snapshot for both the chips and the grid, so they can never disagree.
        List<TaskView> all = service.taskViews(homeCode, memberId, SessionContext.timeZone());
        // One query for this member's armed reminders, never one per card: board render is pinned
        // at one statement per chore (BoardRenderCostTest) and this has to stay a constant.
        // Deliberately here rather than inside taskViews — that is the hottest read in the app and
        // its cost is a tested invariant, and a per-member badge has no business in a per-home
        // projection.
        armedReminders.clear();
        if (pushSender.isEnabled() && !viewer.isDevice()) {
            snoozes.forMember(memberId)
                    .forEach(r -> armedReminders.put(r.getTaskId(), r.getDueAt()));
        }

        List<Filter> chips = availableChips(all);
        if (chips.size() < 2) {
            chips = List.of(Filter.ALL); // nothing worth choosing between; the bar stays hidden
        }
        if (!chips.contains(filter)) {
            filter = Filter.ALL; // the bucket vanished, or the bar is hidden entirely
        }
        List<TaskView> shown = select(all, rotating);
        if (shown.isEmpty() && filter != Filter.ALL) {
            // Never leave the member staring at a blank board wondering what they broke.
            // Fires in practice when the last chore on today's list gets completed.
            filter = Filter.ALL;
            shown = select(all, rotating);
        }

        renderFilters(chips, admin);

        // The long-press drag (board-drag.js) is an admin's gesture: board order is the home's
        // setting (SPEC §4.2b), and a child shuffling the family's board would be a surprise for
        // everyone else's phone.
        if (admin) {
            board.addClassName("draggable");
        } else {
            board.removeClassName("draggable");
        }

        // Grouping happens strictly AFTER selection, so the "never render an empty board" reset
        // above still reasons over the flat list and is unaffected by any of this.
        List<ChoreGroup> groups = service.groupsOf(homeCode);
        Map<Long, List<TaskView>> buckets = new LinkedHashMap<>();
        groups.forEach(g -> buckets.put(g.getId(), new ArrayList<>()));
        List<TaskView> ungrouped = new ArrayList<>();
        for (TaskView v : shown) {
            Long gid = v.task().getGroupId();
            List<TaskView> bucket = gid == null ? null : buckets.get(gid);
            (bucket != null ? bucket : ungrouped).add(v);
        }

        // Neither tile is a chore, so under a narrowed lens they would just dilute the answer —
        // but the default TODAY lens must not hide them, or a fresh board loses "Other help"
        // and the admin's "Add chore". "All" is always the first chip, so both stay one tap away.
        List<Component> extras = new ArrayList<>();
        if (filter == Filter.ALL || filter == Filter.TODAY) {
            if (home == null || home.isAllowOtherHelp()) {
                extras.add(otherHelpCard(admin));
            }
            if (admin) {
                extras.add(addCard());
            }
        }

        board.removeAll();
        // Headings appear only once at least one group section is being rendered. A home with no
        // groups — every home at upgrade time — therefore renders a single unheaded grid, exactly
        // the board it had before groups existed.
        boolean anyGroupShown = buckets.values().stream().anyMatch(b -> !b.isEmpty());
        for (ChoreGroup g : groups) {
            List<TaskView> bucket = buckets.get(g.getId());
            if (!bucket.isEmpty()) { // an empty group is not a heading over nothing
                board.add(groupHeading(g.display()),
                        gridOf(bucket, rotating, admin, List.of(), g.getId()));
            }
        }
        if (!ungrouped.isEmpty() || !extras.isEmpty()) {
            // "Other chores" over nothing but the 🙋/＋ tiles would be a lie — they are not chores.
            if (anyGroupShown && !ungrouped.isEmpty()) {
                board.add(groupHeading(T.tr("board.group.ungrouped")));
            }
            board.add(gridOf(ungrouped, rotating, admin, extras, null));
        }
    }

    private Div groupHeading(String text) {
        Div h = new Div();
        h.setText(text);
        h.addClassName("group-heading");
        return h;
    }

    private Div gridOf(List<TaskView> views, boolean rotating, boolean admin,
                       List<Component> extras, Long groupId) {
        Div grid = new Div();
        grid.addClassName("task-grid");
        // Which section a dropped card lands in; empty for the ungrouped grid.
        grid.getElement().setAttribute("data-group-id", groupId == null ? "" : String.valueOf(groupId));
        views.forEach(v -> grid.add(taskCard(v, rotating, admin)));
        extras.forEach(grid::add);
        return grid;
    }

    /** The views the current lens shows. */
    private List<TaskView> select(List<TaskView> all, boolean rotating) {
        Predicate<TaskView> keep = matches(filter);
        List<TaskView> out = new ArrayList<>();
        for (TaskView v : all) {
            // Under enforced rotation the member's own card is the only completable one; hiding it
            // behind a lens would turn the board into a dead end.
            if (keep.test(v) || (rotating && v.assignedToMe(memberId))) {
                out.add(v);
            }
        }
        return out;
    }

    private Predicate<TaskView> matches(Filter f) {
        return switch (f) {
            case ALL -> v -> true;
            // "Is this on today's list?" — interval elapsed (or anytime) and in season. A chore
            // outside its hours (dog walk at noon) still belongs to today, so it stays listed,
            // locked; a chore booked by someone else or streak-capped likewise. Off-season
            // chores have their own chip and would only be noise here.
            case TODAY -> v -> v.lockReason() != LockReason.NOT_DUE
                    && v.lockReason() != LockReason.OUT_OF_SEASON;
            case OFF_SEASON -> v -> v.lockReason() == LockReason.OUT_OF_SEASON;
            default -> v -> Cadence.of(v.task().getIntervalDays()) == f.cadence;
        };
    }

    /**
     * Which chips are worth showing. Empty buckets are left out, and a chip that would select
     * everything is noise rather than a choice.
     */
    private List<Filter> availableChips(List<TaskView> all) {
        Map<Filter, Integer> counts = new LinkedHashMap<>();
        for (Filter f : Filter.values()) {
            int n = 0;
            for (TaskView v : all) {
                if (matches(f).test(v)) {
                    n++;
                }
            }
            counts.put(f, n);
        }

        List<Filter> cadenceChips = new ArrayList<>();
        for (Filter f : Filter.values()) {
            if (f.cadence != null && counts.get(f) > 0) {
                cadenceChips.add(f);
            }
        }

        List<Filter> chips = new ArrayList<>();
        chips.add(Filter.ALL);
        if (counts.get(Filter.TODAY) > 0 && counts.get(Filter.TODAY) < all.size()) {
            chips.add(Filter.TODAY);
        }
        // A single non-empty cadence bucket is just "All" wearing a different label.
        if (cadenceChips.size() >= 2) {
            chips.addAll(cadenceChips);
        }
        if (counts.get(Filter.OFF_SEASON) > 0) {
            chips.add(Filter.OFF_SEASON);
        }
        return chips;
    }

    private void renderFilters(List<Filter> chips, boolean admin) {
        filterBar.removeAll();
        // "All" on its own is not a choice — but "All / Due now" is, so one real alternative
        // beside it is enough to earn the row.
        filterBar.setVisible(chips.size() >= 2);
        Div selectedChip = null;
        for (Filter f : chips) {
            Div chip = new Div();
            chip.addClassName("filter-chip");
            if (f == filter) {
                chip.addClassName("selected");
                selectedChip = chip;
            }
            chip.setText(T.tr(f.key));
            chip.addClickListener(e -> {
                filter = f;
                // Re-render this board only. Never refresh() and never bump HomeState: one
                // member's view preference must not redraw the whole family's screens.
                renderTasks(admin);
            });
            filterBar.add(chip);
        }
        if (selectedChip != null) {
            // The row scrolls sideways (.filter-bar.scroll), so bring the chosen lens into view.
            // scrollLeft on the parent, not scrollIntoView: the latter also scrolls the *page*
            // vertically, which on first render would yank a phone down to the filter row.
            selectedChip.getElement().executeJs(
                    "const p=this.parentElement; if(p){p.scrollLeft=Math.max(0,this.offsetLeft-p.offsetLeft-8);}");
        }
    }

    private Div taskCard(TaskView view, boolean rotating, boolean admin) {
        Div card = new Div();
        card.addClassName("task-card");
        // Only real chores carry the id: it is what makes a card liftable and landable-on.
        card.getElement().setAttribute("data-task-id", String.valueOf(view.task().getId()));
        if (view.lockedForMe()) {
            card.addClassName("locked");
        }
        if (rotating && view.assignedToMe(memberId)) {
            card.addClassName("mine");
        }

        // A dedicated tap area for completing, so the Book button below doesn't conflict.
        Div completeArea = new Div();
        completeArea.addClassName("complete-area");
        Div emoji = new Div();
        emoji.setText(view.task().getEmoji());
        emoji.addClassName("emoji");
        Div name = new Div();
        name.setText(view.task().getName());
        name.addClassName("name");
        completeArea.add(emoji, name);
        // What the chore pays, on the card itself: the credit value used to be visible only in the
        // Admin tab's edit dialog and in the celebration after the fact, so a member choosing what
        // to do next could not tell a 💎 5 chore from a plain one. Inside the complete-area on
        // purpose — it is information about the tap, not a second control, so tapping it logs the
        // chore like tapping the name does. Zero stays blank: a home that never uses credits sees
        // the board it always had.
        int creditValue = view.task().getCreditValue();
        if (creditValue > 0) {
            Span credit = new Span("💎 " + creditValue);
            credit.addClassName("card-credit");
            credit.getElement().setAttribute("aria-label", T.tr("board.card.credits", creditValue));
            completeArea.add(credit);
        }
        Long taskId = view.task().getId();
        completeArea.addClickListener(e -> handleComplete(taskId));
        card.add(completeArea);

        // Top-left: .streak owns top-right, and .task-card is already position: relative. It
        // cannot trigger a completion, because the card's click listener is on the inner
        // .complete-area rather than on the card — the same reason .book-btn works.
        // Only when push is actually configured, matching how the header bell is hidden: a
        // control that exists to explain a server setting the family cannot change is noise.
        if (pushSender.isEnabled() && !viewer.isDevice()) {
            Instant armed = armedReminders.get(taskId);
            Span snooze = new Span(armed == null ? "⏰" : snoozeBadge(armed));
            snooze.addClassName("snooze-chip");
            if (armed != null) {
                snooze.addClassName("armed");
            }
            snooze.getElement().setAttribute("aria-label", T.tr("snooze.aria"));
            snooze.addClickListener(e -> new SnoozeDialog(snoozes, reminders, pushSender,
                    memberId, homeCode, view.task(), () -> renderTasks(admin)).open());
            card.add(snooze);
        }

        String badge = badgeText(view, rotating);
        if (badge != null) {
            Span b = new Span(badge);
            b.addClassName("streak");
            if (!rotating && view.lockReason() == LockReason.STREAK) {
                card.addClassName("streak-hot");
            }
            card.add(b);
        }

        // Booking controls — default division only, and only for a due chore. On the shared
        // device a booking is anyone's to clear (the device is the household's), and a fresh
        // one asks who it is for.
        if (!rotating && view.due()) {
            boolean device = viewer.isDevice();
            if (device ? view.bookedById() != null : view.bookedByMe(memberId)) {
                card.add(bookButton(T.tr("board.cancelBooking"), () -> {
                    if (device) {
                        sharedDevices.cancelBooking(viewer.deviceId(), taskId);
                    } else {
                        service.cancelBooking(taskId, memberId);
                    }
                    refresh();
                }));
            } else if (view.bookedById() == null && view.lockReason() == LockReason.NONE) {
                card.add(bookButton(T.tr("board.book"), device
                        ? () -> whoDialog(view.task().getEmoji(), view.task().getName(),
                                T.tr("board.who.book"), m -> handleBook(taskId, m))
                        : () -> handleBook(taskId, null)));
            }
        }
        return card;
    }

    private static Button bookButton(String label, Runnable onClick) {
        Button button = new Button(label, e -> onClick.run());
        // Not LUMO_SMALL: at 30px tall and 6px under the tap-to-complete area, a slightly
        // low thumb completed the chore instead of booking it. Full height plus the gap
        // .book-btn adds in styles.css separates the two.
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        button.addClassName("book-btn");
        return button;
    }

    /**
     * How an armed reminder reads on the card: an absolute time, not "in 2h". The board only
     * re-renders when something in the home changes, so a relative label would be wrong within a
     * minute of being drawn and stay wrong for hours.
     */
    private String snoozeBadge(Instant dueAt) {
        ZoneId zone = SessionContext.timeZone();
        long days = ChronoUnit.DAYS.between(LocalDate.now(zone), dueAt.atZone(zone).toLocalDate());
        return days <= 0 ? "⏰ " + SnoozeDialog.localTime(dueAt) : T.tr("snooze.badge.days", days);
    }

    /** The single most relevant status line for a card. */
    private String badgeText(TaskView view, boolean rotating) {
        // Before the due check on purpose: an out-of-season yearly chore would otherwise badge
        // "in 200d", which is true and tells the member nothing useful.
        if (view.lockReason() == LockReason.OUT_OF_SEASON) {
            return T.tr("board.badge.season", Seasons.displayCompact(view.task().getSeasons()));
        }
        if (!view.due()) {
            long d = ChronoUnit.DAYS.between(LocalDate.now(), view.nextDueDate());
            return d <= 0 ? T.tr("board.badge.dueNow") : T.tr("board.badge.dueIn", d);
        }
        if (view.lockReason() == LockReason.OUTSIDE_HOURS) {
            return T.tr("board.badge.hours",
                    TimeWindows.displayCompact(view.task().getAvailableWindows()));
        }
        if (rotating) {
            if (view.assignedToMe(memberId)) {
                return T.tr("board.badge.yourTurn");
            }
            return view.assignedMemberName() != null
                    ? T.tr("board.badge.today", shortName(view.assignedMemberName())) : null;
        }
        if (view.bookedById() != null) {
            return view.bookedByMe(memberId)
                    ? T.tr("board.badge.youBooked")
                    : T.tr("board.badge.bookedBy", shortName(view.bookedByName()));
        }
        if (view.streak() > 0 && view.streakHolderName() != null) {
            String who = view.streakHolderId().equals(memberId)
                    ? T.tr("board.you") : shortName(view.streakHolderName());
            return who + " ×" + view.streak() + (view.streakAtLimit() ? " 🔒" : " 🔥");
        }
        return null;
    }

    /** Names longer than this are cut down for the card badges; the leaderboard keeps them whole. */
    static final int BADGE_NAME_MAX = 10;

    /**
     * A member's name as it fits a card badge. A badge is a 136px card's top-right corner, so
     * "Alexander-Sebastian ×3 🔒" used to wrap to three lines and land on the emoji. The first
     * word (or first half of a double-barrelled name) is what the family calls them anyway; a
     * single long word gets an ellipsis. Names up to {@link #BADGE_NAME_MAX} are untouched.
     */
    static String shortName(String name) {
        if (name == null) {
            return null;
        }
        String s = name.trim();
        if (s.length() <= BADGE_NAME_MAX) {
            return s;
        }
        String first = s.split("[\\s\\-–]+", 2)[0];
        if (!first.isEmpty() && first.length() <= BADGE_NAME_MAX) {
            return first;
        }
        return s.substring(0, BADGE_NAME_MAX - 1) + "…";
    }

    /**
     * "I helped with something else" — the way to log real help the board has no card for.
     * It sits with the chores rather than in a menu: a child who just carried the shopping
     * in won't go looking for a form.
     */
    private Div otherHelpCard(boolean admin) {
        Div card = new Div();
        card.addClassNames("task-card", "help-card");
        Div emoji = new Div();
        emoji.setText("🙋");
        emoji.addClassName("emoji");
        Div name = new Div();
        name.setText(T.tr("board.otherHelp"));
        name.addClassName("name");
        card.add(emoji, name);

        long waiting = service.pendingOtherHelpCount(homeCode, memberId);
        if (waiting > 0 && !viewer.isDevice()) {
            Span badge = new Span(T.tr("board.otherHelp.waiting", waiting));
            badge.addClassName("streak");
            card.add(badge);
        }
        if (viewer.isDevice()) {
            card.addClickListener(e -> whoDialog("🙋", T.tr("board.otherHelp"),
                    T.tr("board.who.help"), who -> openOtherHelpDialog(false, who)));
        } else {
            card.addClickListener(e -> openOtherHelpDialog(admin, null));
        }
        return card;
    }

    /**
     * Describe-what-you-did. For a member it goes to an admin to accept or decline. An admin's
     * own entry counts at once — their reading it is the approval
     * ({@code ChoreService.logOtherHelpAsAdmin}) — so they name the reward here, in the same
     * field they would otherwise see when accepting somebody else's. On the shared device it
     * is written {@code forWho}, the member just picked, and waits for an admin exactly as
     * their own entry would.
     */
    private void openOtherHelpDialog(boolean admin, Member forWho) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(forWho == null ? T.tr("board.otherHelp.title")
                : T.tr("board.otherHelp.titleFor", forWho.getName()));
        dialog.setWidth("min(90vw, 24em)");

        Span intro = new Span(T.tr(admin ? "board.otherHelp.introAdmin" : "board.otherHelp.intro"));
        intro.addClassName("sub");

        TextArea what = new TextArea(T.tr("board.otherHelp.what"));
        what.setPlaceholder(T.tr("board.otherHelp.placeholder"));
        what.setWidthFull();
        what.setMaxLength(ChoreService.MAX_HELP_LENGTH);
        what.setValueChangeMode(ValueChangeMode.EAGER);
        what.setHelperText(T.tr(admin ? "board.otherHelp.helperAdmin" : "board.otherHelp.helper"));
        what.focus();

        IntegerField credits = new IntegerField(T.tr("admin.help.accept.credits"));
        credits.setMin(0);
        credits.setMax(InputLimits.MAX_CREDITS);
        credits.setValue(0);
        credits.setStepButtonsVisible(true);
        credits.setWidthFull();
        credits.setHelperText(T.tr("admin.help.accept.credits.helper"));
        credits.setVisible(admin);

        Button send = new Button(T.tr(admin ? "board.otherHelp.log" : "board.otherHelp.send"), e -> {
            if (what.getValue() == null || what.getValue().isBlank()) {
                what.setInvalid(true);
                what.setErrorMessage(T.tr("board.otherHelp.required"));
                return;
            }
            if (admin) {
                int amount = credits.getValue() == null ? 0 : Math.max(0, credits.getValue());
                var outcome = service.logOtherHelpAsAdmin(homeCode, memberId, what.getValue(), amount);
                dialog.close();
                outcome.ifPresentOrElse(
                        o -> Celebrations.afterComplete(service, creditService, o,
                                this::undoLast, showTrophies),
                        () -> Toasts.show(T.tr("board.otherHelp.off"), 4000)
                                .addThemeVariants(NotificationVariant.LUMO_CONTRAST));
                refresh();
                return;
            }
            Optional<Completion> entry = forWho == null
                    ? service.logOtherHelp(homeCode, memberId, what.getValue())
                    : sharedDevices.logOtherHelp(viewer.deviceId(), forWho.getId(), what.getValue());
            entry.filter(c -> forWho != null)
                    .ifPresent(c -> lastFromDevice = new FromDevice(c.getId(), forWho.getId()));
            boolean logged = entry.isPresent();
            dialog.close();
            Notification n = Toasts.show(
                    logged ? T.tr("board.otherHelp.sent") : T.tr("board.otherHelp.off"),
                    4000);
            n.addThemeVariants(logged ? NotificationVariant.LUMO_SUCCESS
                    : NotificationVariant.LUMO_CONTRAST);
            refresh();
        });
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout body = new VerticalLayout(intro, what, credits);
        body.setPadding(false);
        body.setSpacing(false);
        dialog.add(body);
        dialog.getFooter().add(new Button(T.tr("common.cancel"), e -> dialog.close()), send);
        dialog.open();
    }

    private Div addCard() {
        Div card = new Div();
        card.addClassNames("task-card", "add-card");
        Div plus = new Div();
        plus.setText("＋");
        plus.addClassName("emoji");
        Div name = new Div();
        name.setText(T.tr("board.addChore"));
        name.addClassName("name");
        card.add(plus, name);
        card.addClickListener(e -> openAddDialog());
        return card;
    }

    /**
     * Tapping a card asks first, unless the home has turned that off. The cards are big and
     * side by side on a phone, so a stray tap while scrolling used to log a chore outright.
     */
    private void handleComplete(Long taskId) {
        boolean confirm = service.findHome(homeCode)
                .map(Home::isConfirmCompletion).orElse(true);
        if (!confirm && !viewer.isDevice()) {
            completeNow(taskId, null);
            return;
        }
        service.taskViews(homeCode, memberId, SessionContext.timeZone()).stream()
                .filter(v -> v.task().getId().equals(taskId))
                .findFirst()
                .ifPresent(view -> {
                    if (viewer.isDevice()) {
                        // On the shared device the pick *is* the confirmation: whoever's face is
                        // tapped did it — one tap fewer than pick-then-yes, and the confirm
                        // setting does not apply, since a board that belongs to nobody has
                        // nobody to skip the question for.
                        whoDialog(view.task().getEmoji(), view.task().getName(),
                                T.tr("board.who.did"), m -> completeNow(taskId, m));
                    } else {
                        confirmDialog(view.task().getEmoji(), view.task().getName(),
                                () -> completeNow(taskId, null));
                    }
                });
    }

    /**
     * The board's question: the chore's emoji and name, a question under them, then the
     * answers, each closing the dialog before it acts. Focus lands on the first answer, so the
     * dialog is named by its question and chore — otherwise a screen reader hears only "Alex,
     * button" with nothing to say what is being asked.
     */
    private void askDialog(String emoji, String title, String question, Component... answers) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.addClassName("celebrate-dialog");
        dialog.getElement().setAttribute("aria-label", question + " " + title);

        Div emojiEl = new Div();
        emojiEl.setText(emoji);
        emojiEl.addClassName("celebrate-emoji");
        Div titleEl = new Div();
        titleEl.setText(title);
        titleEl.addClassName("celebrate-title");
        Div questionEl = new Div();
        questionEl.setText(question);
        questionEl.addClassName("celebrate-text");

        VerticalLayout layout = new VerticalLayout(emojiEl, titleEl, questionEl);
        layout.add(answers);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setPadding(false);
        dialog.add(layout);
        // Every answer, chips included, ends the question.
        layout.getChildren().flatMap(c -> c instanceof Button ? Stream.of(c)
                : c.getChildren()).filter(Button.class::isInstance).map(Button.class::cast)
                .forEach(b -> b.addClickListener(e -> dialog.close()));
        dialog.open();
    }

    /**
     * "Who?" — the shared device's version of the confirm dialog: the family as big avatar
     * chips. The ids live in the lambdas, never in the DOM, and the service re-checks that the
     * member is in the device's home anyway.
     */
    private void whoDialog(String emoji, String title, String question, Consumer<Member> onPick) {
        Div chips = new Div();
        chips.addClassName("who-row");
        for (Member m : service.membersOf(homeCode)) {
            Button chip = new Button(m.getName(), e -> onPick.accept(m));
            Div dot = MemberAvatar.dot(m);
            // The name is the chip's label already; the face read out too says it twice.
            dot.getElement().setAttribute("aria-hidden", "true");
            chip.setPrefixComponent(dot);
            chip.addClassName("who-chip");
            chips.add(chip);
        }
        Button cancel = new Button(T.tr("common.cancel"));
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancel.setWidthFull();
        askDialog(emoji, title, question, chips, cancel);
    }

    private void confirmDialog(String emoji, String choreName, Runnable onYes) {
        Button yes = new Button(T.tr("confirm.yes"), e -> onYes.run());
        yes.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        yes.setWidthFull();
        Button no = new Button(T.tr("confirm.notYet"));
        no.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        no.setWidthFull();
        askDialog(emoji, choreName, T.tr("confirm.didYouDoIt"), yes, no);
    }

    /** The tap, as this member's — or, on the shared device, as the member picked there
     *  ({@code who}), with every §4.3 lock run for them. */
    private void completeNow(Long taskId, Member who) {
        CompleteOutcome outcome = who == null
                ? service.complete(taskId, memberId, SessionContext.timeZone())
                : sharedDevices.complete(viewer.deviceId(), taskId, who.getId(), SessionContext.timeZone());
        if (!outcome.allowed()) {
            Celebrations.showBlocked(outcome, who != null);
            refresh();
            return;
        }
        if (who != null) {
            lastFromDevice = new FromDevice(outcome.completionId(), who.getId());
        }
        // On the shared device the member picked has no shelf of theirs to jump to here.
        Celebrations.afterComplete(service, creditService, outcome, this::undoLast,
                who == null ? showTrophies : null);
        refresh();
    }

    /**
     * Takes back the member's most recent chore. Offered from the celebration dialog and
     * from a strip on the board, so it survives dismissing the dialog.
     */
    private void undoLast() {
        undoable().ifPresentOrElse(completion -> {
            if (service.undoCompletion(completion.getId(), undoMemberId())) {
                Notification n = Toasts.show(T.tr("undo.done"), 3000);
                n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }
            lastFromDevice = null;
            refresh();
        }, this::refresh);
    }

    /** Whose completion the undo strip is about: this member's, or the member the device
     *  last logged for. */
    private Long undoMemberId() {
        return viewer.isDevice() ? lastFromDevice == null ? null : lastFromDevice.memberId()
                : memberId;
    }

    /**
     * The completion the strip can take back. For a member, their newest within the window;
     * for the device, the one it remembers logging — looked up through the same query, so that
     * a completion an admin has since unmarked, or one that aged out, is gone here too.
     */
    private Optional<Completion> undoable() {
        if (!viewer.isDevice()) {
            return service.undoableCompletion(memberId);
        }
        if (lastFromDevice == null) {
            return Optional.empty();
        }
        return service.undoableCompletion(lastFromDevice.memberId())
                .filter(c -> c.getId().equals(lastFromDevice.completionId()));
    }

    /** A time-limited "that wasn't me" for the member's own last chore — or, on the shared
     *  device, for the last chore logged from it, naming who it was logged for. */
    private void renderUndo() {
        undoStrip.removeAll();
        var recent = undoable();
        if (recent.isEmpty()) {
            undoStrip.setVisible(false);
            return;
        }
        undoStrip.setVisible(true);
        Completion last = recent.get();
        String what = describe(last);
        Span text = new Span(viewer.isDevice()
                ? T.tr(last.isOtherHelp() ? "undo.someoneJustLogged" : "undo.someoneJustDid",
                        service.findMember(last.getMemberId()).map(Member::getName).orElse("?"), what)
                : T.tr(last.isOtherHelp() ? "undo.justLogged" : "undo.justDid", what));
        text.addClassName("grow");
        Button undo = new Button(T.tr("undo.action"), e -> undoLast());
        undo.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        undo.addClassName("undo-btn");

        Div strip = new Div(text, undo);
        strip.addClassName("undo-strip");
        undoStrip.add(strip);
    }

    /** How a completion reads on the board: the chore, or the member's own words. */
    private String describe(Completion c) {
        ChoreTask task = c.getTaskId() == null ? null
                : service.tasksOf(homeCode).stream()
                        .filter(t -> t.getId().equals(c.getTaskId()))
                        .findFirst().orElse(null);
        return ChoreService.describe(c, task);
    }

    /** "I'll do it", as this member — or on the shared device as the member picked there. */
    private void handleBook(Long taskId, Member who) {
        boolean booked = who == null ? service.bookChore(taskId, memberId)
                : sharedDevices.book(viewer.deviceId(), taskId, who.getId());
        if (!booked) {
            Notification n = Notification.show(T.tr("board.booked.someoneElse"),
                    3000, Notification.Position.MIDDLE);
            n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        }
        refresh();
    }

    private void openAddDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(T.tr("board.add.title"));

        TextField name = new TextField(T.tr("board.add.name"));
        name.setPlaceholder(T.tr("board.add.name.placeholder"));
        name.setMaxLength(InputLimits.TASK_NAME);
        name.setWidthFull();
        name.focus();

        TextField emoji = new TextField(T.tr("board.add.emoji"));
        emoji.setPlaceholder("🪴");
        emoji.setWidthFull();
        emoji.setMaxLength(4);

        Button add = new Button(T.tr("board.addChore"), e -> {
            if (name.isEmpty()) {
                name.setInvalid(true);
                name.setErrorMessage(T.tr("board.add.nameRequired"));
                return;
            }
            dialog.close();
            // The ＋ card only exists for admins, but this dialog outlives a demotion.
            if (service.isAdminOf(homeCode, memberId)) {
                service.addTask(homeCode, name.getValue(), emoji.getValue());
            }
            refresh();
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button(T.tr("common.cancel"), e -> dialog.close());

        VerticalLayout content = new VerticalLayout(name, emoji);
        content.setPadding(false);
        content.setSpacing(false);
        dialog.add(content);
        dialog.getFooter().add(cancel, add);
        dialog.open();
    }

}
