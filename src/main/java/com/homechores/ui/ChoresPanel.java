package com.homechores.ui;

import com.homechores.domain.Cadence;
import com.homechores.domain.Completion;
import com.homechores.domain.DivisionStyle;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.domain.Seasons;
import com.homechores.domain.TimeWindows;
import com.homechores.service.ChoreService;
import com.homechores.service.CreditService;
import com.homechores.service.ChoreService.CompleteOutcome;
import com.homechores.service.ChoreService.LockReason;
import com.homechores.service.ChoreService.TaskView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** The chore board: daily progress, leaderboard, and tap-to-complete chore cards. */
class ChoresPanel extends VerticalLayout {

    private final ChoreService service;
    private final CreditService creditService;
    private final String homeCode;
    private final Long memberId;

    private final Div dailyStrip = new Div();
    private final Div undoStrip = new Div();
    private final Div leaderboard = new Div();
    private final Div filterBar = new Div();
    private final Div taskGrid = new Div();

    /**
     * One narrowing lens over the board. Declaration order is the order the chips render in.
     *
     * <p>Lenses, not a partition: an off-season chore shows under both {@link #OFF_SEASON} and its
     * own cadence chip, so the counts would not add up to the number of chores. That is why the
     * chips carry no count pills.
     */
    private enum Filter {
        ALL(null, "board.filter.all"),
        DUE_NOW(null, "board.filter.dueNow"),
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
     */
    private Filter filter = Filter.ALL;

    ChoresPanel(ChoreService service, CreditService creditService, String homeCode, Long memberId) {
        this.service = service;
        this.creditService = creditService;
        this.homeCode = homeCode;
        this.memberId = memberId;
        setPadding(false);
        setSpacing(false);
        setWidthFull();

        leaderboard.addClassName("leaderboard");
        filterBar.addClassName("filter-bar");
        taskGrid.addClassName("task-grid");

        undoStrip.setVisible(false);
        // Hidden until there is something worth choosing between, so a simple home looks
        // exactly as it did before.
        filterBar.setVisible(false);
        add(dailyStrip, undoStrip, sectionLabel(T.tr("board.leaderboard")), leaderboard,
                sectionLabel(T.tr("board.tapPrompt")), filterBar, taskGrid);
    }

    private Div sectionLabel(String text) {
        Div d = new Div();
        d.setText(text);
        d.addClassName("section-label");
        return d;
    }

    void refresh() {
        boolean admin = service.findMember(memberId).map(Member::isAdmin).orElse(false);
        renderDaily();
        renderUndo();
        renderLeaderboard();
        renderTasks(admin);
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
        ring.getStyle().set("background",
                "conic-gradient(#10b981 " + pct + "%, var(--lumo-contrast-10pct) " + pct + "%)");
        if (done >= target) {
            ring.addClassName("done");
            ring.getStyle().set("background", "#10b981");
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
        for (Member m : service.membersOf(homeCode)) {
            long count = service.completionCount(m.getId());

            Div dot = new Div();
            dot.addClassName("dot");
            dot.getStyle().set("background", m.getColor());
            dot.setText(initials(m.getName()));

            Span nameEl = new Span(m.getName());
            if (m.isAdmin()) {
                Span crown = new Span("👑");
                crown.addClassName("crown");
                nameEl.add(crown);
            }
            Span countEl = new Span(String.valueOf(count));
            countEl.addClassName("count");

            Div chip = new Div(dot, nameEl, countEl);
            int credits = creditService.balance(m.getId());
            if (credits > 0) {
                Span creditEl = new Span("💎 " + credits);
                creditEl.addClassName("credit-chip");
                chip.add(creditEl);
            }
            chip.addClassName("member-chip");
            if (m.getId().equals(memberId)) {
                chip.addClassName("me");
            }
            leaderboard.add(chip);
        }
    }

    private void renderTasks(boolean admin) {
        Home home = service.findHome(homeCode).orElse(null);
        boolean rotating = home != null && home.getDivisionStyle() == DivisionStyle.ROTATING;
        // One snapshot for both the chips and the grid, so they can never disagree.
        List<TaskView> all = service.taskViews(homeCode, memberId, SessionContext.timeZone());

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
            // Fires in practice when you filter to "Due now" and complete the last due chore.
            filter = Filter.ALL;
            shown = select(all, rotating);
        }

        renderFilters(chips, admin);

        taskGrid.removeAll();
        for (TaskView view : shown) {
            taskGrid.add(taskCard(view, rotating));
        }
        // Neither tile is a chore, so under a narrowed lens they would just dilute the answer.
        // "All" is always the first chip, so both stay one tap away.
        if (filter == Filter.ALL) {
            if (home == null || home.isAllowOtherHelp()) {
                taskGrid.add(otherHelpCard());
            }
            if (admin) {
                taskGrid.add(addCard());
            }
        }
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
            // "Could I tap this right now, time-wise?" — a chore booked by someone else or capped
            // by the streak rule is still due, so it stays listed.
            case DUE_NOW -> v -> v.lockReason() != LockReason.NOT_DUE
                    && v.lockReason() != LockReason.OUTSIDE_HOURS
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
        if (counts.get(Filter.DUE_NOW) > 0 && counts.get(Filter.DUE_NOW) < all.size()) {
            chips.add(Filter.DUE_NOW);
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
        for (Filter f : chips) {
            Div chip = new Div();
            chip.addClassName("filter-chip");
            if (f == filter) {
                chip.addClassName("selected");
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
    }

    private Div taskCard(TaskView view, boolean rotating) {
        Div card = new Div();
        card.addClassName("task-card");
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
        Long taskId = view.task().getId();
        completeArea.addClickListener(e -> handleComplete(taskId));
        card.add(completeArea);

        String badge = badgeText(view, rotating);
        if (badge != null) {
            Span b = new Span(badge);
            b.addClassName("streak");
            if (!rotating && view.lockReason() == LockReason.STREAK) {
                card.addClassName("streak-hot");
            }
            card.add(b);
        }

        // Booking controls — default division only, and only for a due chore.
        if (!rotating && view.due()) {
            if (view.bookedByMe(memberId)) {
                Button cancel = new Button(T.tr("board.cancelBooking"), e -> {
                    service.cancelBooking(taskId, memberId);
                    refresh();
                });
                cancel.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                cancel.addClassName("book-btn");
                card.add(cancel);
            } else if (view.bookedById() == null && view.lockReason() == LockReason.NONE) {
                Button book = new Button(T.tr("board.book"), e -> handleBook(taskId));
                book.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                book.addClassName("book-btn");
                card.add(book);
            }
        }
        return card;
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
                    ? T.tr("board.badge.today", view.assignedMemberName()) : null;
        }
        if (view.bookedById() != null) {
            return view.bookedByMe(memberId)
                    ? T.tr("board.badge.youBooked") : T.tr("board.badge.bookedBy", view.bookedByName());
        }
        if (view.streak() > 0 && view.streakHolderName() != null) {
            String who = view.streakHolderId().equals(memberId)
                    ? T.tr("board.you") : view.streakHolderName();
            return who + " ×" + view.streak()
                    + (view.streak() >= ChoreService.MAX_IN_A_ROW ? " 🔒" : " 🔥");
        }
        return null;
    }

    /**
     * "I helped with something else" — the way to log real help the board has no card for.
     * It sits with the chores rather than in a menu: a child who just carried the shopping
     * in won't go looking for a form.
     */
    private Div otherHelpCard() {
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
        if (waiting > 0) {
            Span badge = new Span(T.tr("board.otherHelp.waiting", waiting));
            badge.addClassName("streak");
            card.add(badge);
        }
        card.addClickListener(e -> openOtherHelpDialog());
        return card;
    }

    /** Describe-what-you-did, then it goes to an admin to accept or decline. */
    private void openOtherHelpDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(T.tr("board.otherHelp.title"));
        dialog.setWidth("min(90vw, 24em)");

        Span intro = new Span(T.tr("board.otherHelp.intro"));
        intro.addClassName("sub");

        TextArea what = new TextArea(T.tr("board.otherHelp.what"));
        what.setPlaceholder(T.tr("board.otherHelp.placeholder"));
        what.setWidthFull();
        what.setMaxLength(ChoreService.MAX_HELP_LENGTH);
        what.setValueChangeMode(ValueChangeMode.EAGER);
        what.setHelperText(T.tr("board.otherHelp.helper"));
        what.focus();

        Button send = new Button(T.tr("board.otherHelp.send"), e -> {
            if (what.getValue() == null || what.getValue().isBlank()) {
                what.setInvalid(true);
                what.setErrorMessage(T.tr("board.otherHelp.required"));
                return;
            }
            boolean logged = service.logOtherHelp(homeCode, memberId, what.getValue()).isPresent();
            dialog.close();
            Notification n = Notification.show(
                    logged ? T.tr("board.otherHelp.sent") : T.tr("board.otherHelp.off"),
                    4000, Notification.Position.TOP_CENTER);
            n.addThemeVariants(logged ? NotificationVariant.LUMO_SUCCESS
                    : NotificationVariant.LUMO_CONTRAST);
            refresh();
        });
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout body = new VerticalLayout(intro, what);
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
        if (!confirm) {
            completeNow(taskId);
            return;
        }
        service.taskViews(homeCode, memberId, SessionContext.timeZone()).stream()
                .filter(v -> v.task().getId().equals(taskId))
                .findFirst()
                .ifPresent(view -> confirmDialog(view.task().getEmoji(), view.task().getName(),
                        () -> completeNow(taskId)));
    }

    private void confirmDialog(String emoji, String choreName, Runnable onYes) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.addClassName("celebrate-dialog");

        Div emojiEl = new Div();
        emojiEl.setText(emoji);
        emojiEl.addClassName("celebrate-emoji");
        Div titleEl = new Div();
        titleEl.setText(choreName);
        titleEl.addClassName("celebrate-title");
        Div question = new Div();
        question.setText(T.tr("confirm.didYouDoIt"));
        question.addClassName("celebrate-text");

        Button yes = new Button(T.tr("confirm.yes"), e -> {
            dialog.close();
            onYes.run();
        });
        yes.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        yes.setWidthFull();
        Button no = new Button(T.tr("confirm.notYet"), e -> dialog.close());
        no.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        no.setWidthFull();

        VerticalLayout layout = new VerticalLayout(emojiEl, titleEl, question, yes, no);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setPadding(false);
        dialog.add(layout);
        dialog.open();
    }

    private void completeNow(Long taskId) {
        CompleteOutcome outcome = service.complete(taskId, memberId, SessionContext.timeZone());
        if (!outcome.allowed()) {
            Celebrations.showBlocked(outcome);
            refresh();
            return;
        }
        Celebrations.afterComplete(service, outcome, this::undoLast);
        refresh();
    }

    /**
     * Takes back the member's most recent chore. Offered from the celebration dialog and
     * from a strip on the board, so it survives dismissing the dialog.
     */
    private void undoLast() {
        service.undoableCompletion(memberId).ifPresentOrElse(completion -> {
            if (service.undoCompletion(completion.getId(), memberId)) {
                Notification n = Notification.show(T.tr("undo.done"),
                        3000, Notification.Position.TOP_CENTER);
                n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }
            refresh();
        }, this::refresh);
    }

    /** A time-limited "that wasn't me" for the member's own last chore. */
    private void renderUndo() {
        undoStrip.removeAll();
        var recent = service.undoableCompletion(memberId);
        if (recent.isEmpty()) {
            undoStrip.setVisible(false);
            return;
        }
        undoStrip.setVisible(true);
        Completion last = recent.get();
        Span text = new Span(T.tr(last.isOtherHelp() ? "undo.justLogged" : "undo.justDid",
                describe(last)));
        text.addClassName("grow");
        Button undo = new Button(T.tr("undo.action"), e -> undoLast());
        undo.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        Div strip = new Div(text, undo);
        strip.addClassName("undo-strip");
        undoStrip.add(strip);
    }

    /** How a completion reads on the board: the chore, or the member's own words. */
    private String describe(Completion c) {
        if (c.isOtherHelp()) {
            return "🙋 " + c.getNote();
        }
        return service.tasksOf(homeCode).stream()
                .filter(t -> t.getId().equals(c.getTaskId()))
                .findFirst().map(t -> t.getEmoji() + " " + t.getName()).orElse("?");
    }

    private void handleBook(Long taskId) {
        if (!service.bookChore(taskId, memberId)) {
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
            service.addTask(homeCode, name.getValue(), emoji.getValue());
            dialog.close();
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

    private static String initials(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "?";
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        }
        return trimmed.substring(0, Math.min(2, trimmed.length())).toUpperCase();
    }
}
