package com.homechores.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalTime DEFAULT_TIME = LocalTime.of(19, 0);

    /** Inlined base64url → Uint8Array (what pushManager.subscribe wants for the key). */
    private static final String URL_B64 =
            "const urlB64 = (s) => { const pad = '='.repeat((4 - s.length % 4) % 4);"
            + " const b = (s + pad).replace(/-/g, '+').replace(/_/g, '/');"
            + " const raw = atob(b);"
            + " return Uint8Array.from([...raw].map((c) => c.charCodeAt(0))); };";

    private final PushReminderService reminders;
    private final WebPushSender sender;
    private final Long memberId;
    private final String homeCode;

    private final TimePicker time = new TimePicker(T.tr("reminder.time"));

    ReminderDialog(PushReminderService reminders, WebPushSender sender,
                   Long memberId, String homeCode) {
        this.reminders = reminders;
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
        UI ui = UI.getCurrent();
        ui.getPage().executeJs(
                "return (async () => { try {"
                + " const reg = await navigator.serviceWorker.getRegistration();"
                + " if (!reg || !reg.pushManager) return JSON.stringify({status:'unsupported'});"
                + " const perm = await Notification.requestPermission();"
                + " if (perm !== 'granted') return JSON.stringify({status: perm});"
                + " " + URL_B64
                + " const sub = await reg.pushManager.subscribe("
                + "   {userVisibleOnly: true, applicationServerKey: urlB64($0)});"
                + " return JSON.stringify({status:'granted', sub: sub.toJSON()});"
                + " } catch (err) {"
                + "   return JSON.stringify({status:'error', message: String(err)});"
                + " } })();", sender.publicKey())
                .then(String.class, json -> onSubscribeResult(json, chosen));
    }

    private void onSubscribeResult(String json, LocalTime chosen) {
        JsonNode result = parse(json);
        String status = result.path("status").asText("error");
        switch (status) {
            case "granted" -> {
                JsonNode sub = result.path("sub");
                reminders.storeSubscription(memberId, homeCode,
                        sub.path("endpoint").asText(null),
                        sub.path("keys").path("p256dh").asText(null),
                        sub.path("keys").path("auth").asText(null));
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
        UI.getCurrent().getPage().executeJs(
                "return (async () => { try {"
                + " const reg = await navigator.serviceWorker.getRegistration();"
                + " const sub = reg && reg.pushManager"
                + "   ? await reg.pushManager.getSubscription() : null;"
                + " if (!sub) return JSON.stringify({status:'none'});"
                + " const endpoint = sub.endpoint;"
                + " await sub.unsubscribe();"
                + " return JSON.stringify({status:'ok', endpoint: endpoint});"
                + " } catch (err) {"
                + "   return JSON.stringify({status:'error', message: String(err)});"
                + " } })();")
                .then(String.class, json -> {
                    JsonNode result = parse(json);
                    String endpoint = result.path("endpoint").asText(null);
                    if (endpoint != null) {
                        reminders.removeSubscription(endpoint);
                    }
                    // Clear the reminder regardless: even if the browser had no live
                    // subscription (cleared site data), the member asked for it off.
                    reminders.saveReminder(memberId, null, null);
                    close();
                    toast(T.tr("reminder.disabledToast"));
                });
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
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
