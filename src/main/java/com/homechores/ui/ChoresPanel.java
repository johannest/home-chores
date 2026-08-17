package com.homechores.ui;

import com.homechores.domain.DivisionStyle;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
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
import com.vaadin.flow.component.textfield.TextField;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** The chore board: daily progress, leaderboard, and tap-to-complete chore cards. */
class ChoresPanel extends VerticalLayout {

    private final ChoreService service;
    private final CreditService creditService;
    private final String homeCode;
    private final Long memberId;

    private final Div dailyStrip = new Div();
    private final Div undoStrip = new Div();
    private final Div leaderboard = new Div();
    private final Div taskGrid = new Div();

    ChoresPanel(ChoreService service, CreditService creditService, String homeCode, Long memberId) {
        this.service = service;
        this.creditService = creditService;
        this.homeCode = homeCode;
        this.memberId = memberId;
        setPadding(false);
        setSpacing(false);
        setWidthFull();

        leaderboard.addClassName("leaderboard");
        taskGrid.addClassName("task-grid");

        undoStrip.setVisible(false);
        add(dailyStrip, undoStrip, sectionLabel(T.tr("board.leaderboard")), leaderboard,
                sectionLabel(T.tr("board.tapPrompt")), taskGrid);
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
        taskGrid.removeAll();
        boolean rotating = service.findHome(homeCode)
                .map(h -> h.getDivisionStyle() == DivisionStyle.ROTATING).orElse(false);
        for (TaskView view : service.taskViews(homeCode, memberId, SessionContext.timeZone())) {
            taskGrid.add(taskCard(view, rotating));
        }
        if (admin) {
            taskGrid.add(addCard());
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
        String choreName = service.tasksOf(homeCode).stream()
                .filter(t -> t.getId().equals(recent.get().getTaskId()))
                .findFirst().map(t -> t.getEmoji() + " " + t.getName()).orElse("?");

        Span text = new Span(T.tr("undo.justDid", choreName));
        text.addClassName("grow");
        Button undo = new Button(T.tr("undo.action"), e -> undoLast());
        undo.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        Div strip = new Div(text, undo);
        strip.addClassName("undo-strip");
        undoStrip.add(strip);
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
