package com.homechores.ui;

import com.homechores.service.ChoreReminderService;
import com.homechores.service.PushReminderService;
import com.homechores.service.WebPushSender;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.timepicker.TimePicker;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Per-device opt-in for the daily "no chores logged yet" Web Push reminder. Drives the
 * browser's permission prompt and {@code pushManager} subscribe/unsubscribe with its own
 * JS (returned as a JSON string) rather than Vaadin's {@code WebPush.subscribe} helper,
 * whose built-in error handler throws a server-side exception whenever the user simply
 * dismisses or denies the permission prompt.
 *
 * <p>On iPhone/iPad, Web Push only works when the app is installed to the Home Screen
 * (iOS 16.4+) — the unsupported-hint tells the user so.
 */
class ReminderDialog extends Dialog {

    private static final LocalTime DEFAULT_TIME = LocalTime.of(19, 0);

    private final PushReminderService reminders;
    private final ChoreReminderService snoozes;
    private final WebPushSender sender;
    private final Long memberId;
    private final String homeCode;

    private final TimePicker time = new TimePicker(T.tr("reminder.time"));

    ReminderDialog(PushReminderService reminders, ChoreReminderService snoozes,
                   WebPushSender sender, Long memberId, String homeCode) {
        this.reminders = reminders;
        this.snoozes = snoozes;
        this.sender = sender;
        this.memberId = memberId;
        this.homeCode = homeCode;

        setHeaderTitle(T.tr("reminder.title"));
        setWidth("min(90vw, 24em)");

        Optional<LocalTime> saved = reminders.reminderTime(memberId);

        Paragraph intro = new Paragraph(T.tr("reminder.intro"));
        intro.addClassName("sub");

        time.setStep(Duration.ofMinutes(15));
        time.setValue(saved.orElse(DEFAULT_TIME));
        time.setWidthFull();

        Button enable = new Button(
                T.tr(saved.isPresent() ? "reminder.update" : "reminder.enable"),
                e -> subscribeAndSave());
        enable.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        enable.setWidthFull();

        Button disable = new Button(T.tr("reminder.disable"), e -> unsubscribe());
        disable.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        disable.setWidthFull();
        disable.setVisible(saved.isPresent());

        VerticalLayout body = new VerticalLayout(intro, time, enable, disable);
        body.setPadding(false);
        body.setSpacing(true);
        add(body);
        getFooter().add(new Button(T.tr("common.cancel"), e -> close()));
    }

    private void subscribeAndSave() {
        LocalTime chosen = time.getValue() == null ? DEFAULT_TIME : time.getValue();
        // The subscribe script lives in WebPushSubscribe now that the per-chore snooze needs the
        // same flow — two copies of it would drift on the first change to either.
        WebPushSubscribe.ensure(sender, reminders, memberId, homeCode,
                status -> onSubscribeResult(status, chosen));
    }

    private void onSubscribeResult(String status, LocalTime chosen) {
        switch (status) {
            case "granted" -> {
                reminders.saveReminder(memberId, chosen, UI.getCurrent().getLocale());
                close();
                toast(T.tr("reminder.saved", chosen.toString()));
            }
            case "denied" -> warn(T.tr("reminder.denied"));
            case "unsupported" -> warn(T.tr("reminder.unsupported"));
            default -> warn(T.tr("reminder.error"));
        }
    }

    private void unsubscribe() {
        WebPushSubscribe.drop(reminders, status -> {
            // Clear the reminder regardless: even if the browser had no live subscription
            // (cleared site data), the member asked for it off.
            reminders.saveReminder(memberId, null, null);
            // Unsubscribing destroys the endpoint every push would have gone to, so any pending
            // per-chore nudge could no longer be delivered. One switch, everything off — leaving
            // them armed would mean rows that can never fire, and a member who turned
            // notifications off still being notified is the more surprising of the two failures.
            snoozes.cancelAll(memberId);
            close();
            toast(T.tr("reminder.disabledToast"));
        });
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
