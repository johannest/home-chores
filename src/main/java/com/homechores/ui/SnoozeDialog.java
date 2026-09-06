package com.homechores.ui;

import com.homechores.domain.ChoreTask;
import com.homechores.service.ChoreReminderService;
import com.homechores.service.PushReminderService;
import com.homechores.service.WebPushSender;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * "Remind me about this one later" — a single nudge about a single chore, for the member on this
 * device.
 *
 * <p>Six offsets as tappable chips rather than a picker: the decision takes two seconds, and a
 * chip is one tap where a select plus a confirm button is three. The chips reuse
 * {@code .filter-chip}'s recipe so the two rows of choices read the same way as the lenses above
 * the board.
 *
 * <p>Cancelling lives in here rather than on the card's chip. A one-tap destructive action sitting
 * on a card the member may be trying to complete is exactly the stray-tap problem
 * {@code Home.confirmCompletion} exists to solve.
 */
class SnoozeDialog extends Dialog {

    /**
     * The offsets worth a single tap. Six is what fits two rows of chips in a 24em dialog on a
     * 360px phone without scrolling.
     *
     * <p>Relative to <em>now</em>, not to when the chore next comes due. The member is looking at
     * the board when they ask, and "in 2h" anchored on a due date would silently mean the same
     * thing as "now" for the ten seeded chores that have no interval, while meaning something
     * surprising on the one that does.
     */
    enum Offset {
        H1(Duration.ofHours(1), "snooze.in.1h"),
        H2(Duration.ofHours(2), "snooze.in.2h"),
        H4(Duration.ofHours(4), "snooze.in.4h"),
        H8(Duration.ofHours(8), "snooze.in.8h"),
        D1(Duration.ofDays(1), "snooze.in.1d"),
        W1(Duration.ofDays(7), "snooze.in.1w");

        final Duration duration;
        final String key;

        Offset(Duration duration, String key) {
            this.duration = duration;
            this.key = key;
        }
    }

    SnoozeDialog(ChoreReminderService snoozes, PushReminderService reminders,
                 WebPushSender sender, Long memberId, String homeCode, ChoreTask chore,
                 Runnable onChange) {
        setHeaderTitle(chore.getEmoji() + " " + chore.getName());
        setWidth("min(90vw, 24em)");

        Paragraph intro = new Paragraph(T.tr("snooze.intro"));
        intro.addClassName("sub");

        Div chips = new Div();
        chips.addClassName("filter-bar");
        for (Offset offset : Offset.values()) {
            Div chip = new Div();
            chip.addClassName("filter-chip");
            chip.setText(T.tr(offset.key));
            chip.addClickListener(e -> arm(snoozes, reminders, sender, memberId, homeCode,
                    chore, offset, onChange));
            chips.add(chip);
        }

        VerticalLayout body = new VerticalLayout(intro, chips);
        body.setPadding(false);
        body.setSpacing(true);

        Optional<Instant> armed = snoozes.forMemberAndTask(memberId, chore.getId())
                .map(r -> r.getDueAt());
        armed.ifPresent(dueAt -> {
            Paragraph active = new Paragraph(T.tr("snooze.active", localTime(dueAt)));
            active.getStyle().set("font-weight", "600");
            Button cancel = new Button(T.tr("snooze.cancel"), e -> {
                snoozes.cancel(memberId, chore.getId());
                close();
                toast(T.tr("snooze.cancelled"));
                onChange.run();
            });
            cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            cancel.setWidthFull();
            body.add(active, cancel);
        });

        add(body);
        getFooter().add(new Button(T.tr("common.cancel"), e -> close()));
    }

    /**
     * A member who has never enabled notifications has no subscription for the push to go to, so
     * the permission flow runs first and the reminder is stored only on {@code granted}. Arming
     * one that provably cannot be delivered would be a lie the board would then badge.
     */
    private void arm(ChoreReminderService snoozes, PushReminderService reminders,
                     WebPushSender sender, Long memberId, String homeCode, ChoreTask chore,
                     Offset offset, Runnable onChange) {
        WebPushSubscribe.ensure(sender, reminders, memberId, homeCode, status -> {
            switch (status) {
                case "granted" -> {
                    Instant at = snoozes.schedule(memberId, chore.getId(), offset.duration,
                            UI.getCurrent().getLocale());
                    close();
                    toast(T.tr("snooze.set", localTime(at)));
                    onChange.run();
                }
                // The same situations, in the same words, as the daily reminder — no new
                // translations for a problem the member experiences identically either way.
                case "denied" -> warn(T.tr("reminder.denied"));
                case "unsupported" -> warn(T.tr("reminder.unsupported"));
                default -> warn(T.tr("reminder.error")); // includes a dismissed prompt
            }
        });
    }

    /** The member's own wall clock, with the date when it is not today. */
    static String localTime(Instant when) {
        var zoned = when.atZone(SessionContext.timeZone());
        String time = DateTimeFormatter.ofPattern("HH:mm").format(zoned);
        return zoned.toLocalDate().equals(LocalDate.now(SessionContext.timeZone()))
                ? time
                : DateTimeFormatter.ofPattern("d.M.").format(zoned) + " " + time;
    }

    private void toast(String message) {
        Notification n = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void warn(String message) {
        Notification n = Notification.show(message, 6000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
