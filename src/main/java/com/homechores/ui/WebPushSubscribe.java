package com.homechores.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homechores.service.PushReminderService;
import com.homechores.service.WebPushSender;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.SerializableConsumer;

/**
 * Getting this browser subscribed to Web Push, and storing what it hands back.
 *
 * <p>Hand-written JS rather than Vaadin's {@code WebPush.subscribe}, for the reason the reminder
 * dialog originally wrote it: Vaadin's built-in error handler throws a server-side exception
 * whenever the user simply dismisses or denies the permission prompt, which is not an error — it
 * is an answer. This is that script, lifted out whole now that two features need it, so the two
 * cannot drift into disagreeing about what "subscribed" means.
 *
 * <p>{@code pushManager.subscribe} returns the existing subscription when there already is one
 * for the same application key, so this is an upsert and safe to call every time.
 *
 * <p>On iPhone/iPad, Web Push only exists once the app is installed to the Home Screen
 * (iOS 16.4+); that comes back as {@code unsupported}.
 */
final class WebPushSubscribe {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Inlined base64url → Uint8Array (what pushManager.subscribe wants for the key). */
    private static final String URL_B64 =
            "const urlB64 = (s) => { const pad = '='.repeat((4 - s.length % 4) % 4);"
            + " const b = (s + pad).replace(/-/g, '+').replace(/_/g, '/');"
            + " const raw = atob(b);"
            + " return Uint8Array.from([...raw].map((c) => c.charCodeAt(0))); };";

    private WebPushSubscribe() {
    }

    /**
     * Subscribes if needed and stores the subscription, then reports one of
     * {@code granted} / {@code denied} / {@code default} / {@code unsupported} / {@code error}.
     * The storing happens here, so a caller never has to know what a {@code p256dh} is.
     */
    static void ensure(WebPushSender sender, PushReminderService reminders, Long memberId,
                       String homeCode, SerializableConsumer<String> onResult) {
        UI.getCurrent().getPage().executeJs(
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
                .then(String.class, json -> {
                    JsonNode result = parse(json);
                    String status = result.path("status").asText("error");
                    if ("granted".equals(status)) {
                        JsonNode sub = result.path("sub");
                        reminders.storeSubscription(memberId, homeCode,
                                sub.path("endpoint").asText(null),
                                sub.path("keys").path("p256dh").asText(null),
                                sub.path("keys").path("auth").asText(null));
                    }
                    onResult.accept(status);
                });
    }

    /** Unsubscribes this browser and forgets the row. Reports {@code ok} / {@code none} /
     *  {@code error}, with the endpoint already removed server-side when there was one. */
    static void drop(PushReminderService reminders, SerializableConsumer<String> onResult) {
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
                    onResult.accept(result.path("status").asText("error"));
                });
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }
}
