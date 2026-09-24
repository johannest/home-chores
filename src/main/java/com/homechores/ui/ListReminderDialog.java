package com.homechores.ui;

import com.homechores.domain.ListItem;
import com.homechores.domain.ListReminder;
import com.homechores.service.ListItemService;
import com.homechores.service.ListReminderService;
import com.homechores.service.ListReminderService.QuickOption;
import com.homechores.service.PushReminderService;
import com.homechores.service.WebPushSender;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.signals.local.ValueSignal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;

/**
 * "Remind us about this line" — the home's one reminder about one shared-list line, and, once
 * it has fired, the place to answer it.
 *
 * <p>Four quick choices as chips ({@link QuickOption}: in an hour, tomorrow 9:00, next week,
 * next month) plus a fifth that unfolds a date-and-time picker. The chips reuse
 * {@code .filter-chip}, like {@link SnoozeDialog}, so every "pick one" in the app reads the same.
 *
 * <p><strong>Fired mode.</strong> Vaadin's generated service worker opens the board on a
 * notification tap and nothing more (SPEC §4.13b), so the snooze happens here: {@code HomeView}
 * opens this dialog for an unanswered reminder, headed "⏰ Reminder" and naming the line. The
 * same chips then mean "snooze until", and two more buttons finish it — tick the line off, or
 * dismiss. Closing with the footer button answers nothing; the dialog stays away for this
 * screen but comes back for the next person who opens the board.
 */
class ListReminderDialog extends Dialog {

    private final ListReminderService reminders;
    private final ListItemService lists;
    private final PushReminderService push;
    private final WebPushSender sender;
    private final Long memberId;
    private final String homeCode;
    private final ListItem item;
    private final Runnable onChange;

    /** The reminder this dialog is answering, or null when it is arming a fresh one. */
    private final Long reminderId;

    private final Div pickRow = new Div();
    private final ValueSignal<Boolean> picking = new ValueSignal<>(false);
    private final DateTimePicker picker = new DateTimePicker();

    ListReminderDialog(ListReminderService reminders, ListItemService lists, PushReminderService push,
                       WebPushSender sender, Long memberId, String homeCode, ListItem item,
                       ListReminder existing, Map<Long, String> names, Runnable onChange) {
        this.reminders = reminders;
        this.lists = lists;
        this.push = push;
        this.sender = sender;
        this.memberId = memberId;
        this.homeCode = homeCode;
        this.item = item;
        this.onChange = onChange;
        this.reminderId = existing == null ? null : existing.getId();
        boolean fired = existing != null && existing.isFired();

        setWidth("min(90vw, 24em)");
        addClassName("list-reminder-dialog");
        if (fired) {
            addClassName("fired");
        }

        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(true);

        if (fired) {
            setHeaderTitle(T.tr("listReminder.fired.title"));
            Paragraph what = new Paragraph(item.getText());
            what.addClassName("list-reminder-what");
            Paragraph intro = new Paragraph(T.tr("listReminder.fired.intro"));
            intro.addClassName("sub");
            body.add(what, intro);
        } else {
            setHeaderTitle(item.getText());
            Paragraph intro = new Paragraph(T.tr("listReminder.intro"));
            intro.addClassName("sub");
            body.add(intro);
        }

        Div chips = new Div();
        chips.addClassName("filter-bar");
        for (QuickOption option : QuickOption.values()) {
            Div chip = new Div();
            chip.addClassName("filter-chip");
            chip.setText(T.tr("listReminder." + option.name()));
            chip.addClickListener(e -> arm(option.at(ZonedDateTime.now(SessionContext.timeZone()))));
            chips.add(chip);
        }
        Div pick = new Div();
        pick.addClassName("filter-chip");
        pick.addClassName("pick");
        pick.setText(T.tr("listReminder.pick"));
        pick.addClickListener(e -> {
            picking.update(open -> !open);
            if (picking.peek()) {
                picker.focus();
            }
        });
        chips.add(pick);
        body.add(chips);

        // The picker starts on the reminder's current time when there is one, so changing it is
        // a nudge of the time rather than typing it all again; otherwise tomorrow 9:00 in the
        // member's own zone — a real, plausible value, so one tap on "Set" is never a mistake.
        ZoneId zone = SessionContext.timeZone();
        boolean editing = existing != null && !fired && existing.getDueAt().isAfter(Instant.now());
        UI ui = UI.getCurrent();
        picker.setLabel(T.tr("listReminder.pickLabel"));
        picker.setLocale(ui == null ? Locale.ENGLISH : ui.getLocale());
        picker.setStep(Duration.ofMinutes(15));
        picker.setMin(LocalDateTime.now(zone));
        picker.setValue(editing
                ? LocalDateTime.ofInstant(existing.getDueAt(), zone).truncatedTo(ChronoUnit.MINUTES)
                : LocalDate.now(zone).plusDays(1).atTime(ListReminderService.ANCHOR));
        picker.setWidthFull();
        Button set = new Button(T.tr(editing ? "listReminder.updateButton" : "listReminder.setButton"), e -> {
            LocalDateTime chosen = picker.getValue();
            if (chosen == null) {
                picker.setInvalid(true);
                return;
            }
            arm(chosen.atZone(SessionContext.timeZone()).toInstant());
        });
        set.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        set.setWidthFull();
        pickRow.addClassName("list-reminder-pick");
        pickRow.add(picker, set);
        // Editing an armed reminder opens straight onto its time: that is what the tap was for.
        // One signal drives both the unfolded row and the chip's selected look.
        picking.set(editing);
        pickRow.bindVisible(picking);
        pick.bindClassName("selected", picking);
        body.add(pickRow);

        if (fired) {
            Button done = new Button(T.tr("listReminder.fired.done"), e -> {
                lists.setDone(item.getId(), memberId, true); // also drops the reminder
                close();
                toast(T.tr("listReminder.fired.doneToast", item.getText()));
                onChange.run();
            });
            done.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            done.setWidthFull();
            Button dismiss = new Button(T.tr("listReminder.fired.dismiss"), e -> {
                reminders.cancel(item.getId(), memberId);
                close();
                toast(T.tr("listReminder.dismissed"));
                onChange.run();
            });
            dismiss.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            dismiss.setWidthFull();
            body.add(done, dismiss);
        } else if (existing != null) {
            String who = names.getOrDefault(existing.getSetByMemberId(), "?");
            Paragraph active = new Paragraph(T.tr("listReminder.active",
                    SnoozeDialog.localTime(existing.getDueAt()), who));
            active.getStyle().set("font-weight", "600");
            Button cancel = new Button(T.tr("listReminder.cancel"), e -> {
                reminders.cancel(item.getId(), memberId);
                close();
                toast(T.tr("listReminder.cancelled"));
                onChange.run();
            });
            cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            cancel.setWidthFull();
            body.add(active, cancel);
        }

        add(body);
        getFooter().add(new Button(T.tr("common.cancel"), e -> close()));
    }

    /** The reminder being answered, or null. {@code HomeView} uses it to keep one dialog per nudge. */
    Long reminderId() {
        return reminderId;
    }

    /**
     * The push goes to every device in the home, but this device runs the permission flow first,
     * as the chore snooze does: a member who asks to be reminded should be among those who are.
     * Stored only on {@code granted} — a home where nobody can receive it would otherwise badge a
     * reminder that can never fire.
     */
    private void arm(Instant at) {
        WebPushSubscribe.ensure(sender, push, memberId, homeCode, status -> {
            switch (status) {
                case "granted" -> {
                    Instant when = reminders.schedule(item.getId(), memberId, at,
                            UI.getCurrent().getLocale());
                    close();
                    toast(T.tr("listReminder.set", SnoozeDialog.localTime(when)));
                    onChange.run();
                }
                case "denied" -> warn(T.tr("reminder.denied"));
                case "unsupported" -> warn(T.tr("reminder.unsupported"));
                default -> warn(T.tr("reminder.error")); // includes a dismissed prompt
            }
        });
    }

    /** How a reminder reads on its line: the absolute time today, the date otherwise, or "in Nd"
     *  when it is more than a day out — the same rule as the chore card. */
    static String badge(ListReminder r) {
        ZoneId zone = SessionContext.timeZone();
        Instant dueAt = r.getDueAt();
        long days = ChronoUnit.DAYS.between(LocalDate.now(zone), dueAt.atZone(zone).toLocalDate());
        return days <= 1 ? "⏰ " + SnoozeDialog.localTime(dueAt) : T.tr("listReminder.badge.days", days);
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
