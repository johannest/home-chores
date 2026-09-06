package com.homechores.service;

import com.homechores.domain.PushSubscription;
import com.vaadin.flow.server.webpush.WebPush;
import com.vaadin.flow.server.webpush.WebPushException;
import com.vaadin.flow.server.webpush.WebPushKeys;
import com.vaadin.flow.server.webpush.WebPushMessage;
import com.vaadin.flow.server.webpush.WebPushSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends Web Push notifications, wrapping Vaadin's {@link WebPush} (which in turn wraps
 * com.interaso:webpush — plain {@code java.security} + {@code java.net.http}).
 *
 * <p>Inert unless both VAPID keys are configured ({@code homechores.push.*}, typically
 * from {@code FLASHCHORES_VAPID_*} env vars; generate a pair once with
 * {@code npx web-push generate-vapid-keys}). The {@link WebPush} instance is built
 * lazily on first send: constructing it starts a {@code java.net.http} selector thread,
 * and the thread budget (see application.properties) shouldn't pay for a feature that
 * isn't in use.
 */
@Service
public class WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    private final String publicKey;
    private final String privateKey;
    private final String subject;

    private volatile WebPush webPush;

    public WebPushSender(@Value("${homechores.push.public-key:}") String publicKey,
                        @Value("${homechores.push.private-key:}") String privateKey,
                        @Value("${homechores.push.subject:mailto:support@flashchores.com}")
                        String subject) {
        this.publicKey = publicKey == null ? "" : publicKey.trim();
        this.privateKey = privateKey == null ? "" : privateKey.trim();
        this.subject = subject;
    }

    /** Whether the push subsystem is configured at all (both VAPID keys present). */
    public boolean isEnabled() {
        return !publicKey.isBlank() && !privateKey.isBlank();
    }

    /** The VAPID public key the browser needs for {@code pushManager.subscribe}. */
    public String publicKey() {
        return publicKey;
    }

    /**
     * Sends one notification. Returns false exactly when the push service reports the
     * subscription is gone (HTTP 404/410 — the user revoked it or the browser rotated
     * it), which is the caller's cue to delete the stored row. Transient failures are
     * logged and swallowed: a missed reminder is not worth retry machinery.
     */
    public boolean send(PushSubscription sub, String title, String body) {
        // The endpoint is browser-supplied, so it is re-checked here — on the one code path
        // that actually opens a socket — and not only where it was stored. Rows written
        // before the check existed pass through here too, and a name that resolved
        // somewhere harmless yesterday can point inwards today (see PushEndpoints).
        String endpoint = sub.getEndpoint();
        if (!PushEndpoints.isWellFormed(endpoint)) {
            // Never a real push service: prune it like a dead subscription.
            log.warn("Refusing a malformed push endpoint; dropping the subscription");
            return false;
        }
        if (!PushEndpoints.resolvesToPublicAddress(endpoint)) {
            // Could be transient (DNS down) or hostile (resolves into private space).
            // Skip the send but keep the row — pruning on a DNS blip would silently turn
            // a family's reminders off for good.
            log.warn("Skipping a push endpoint that does not resolve to a public address");
            return true;
        }
        try {
            webPush().sendNotification(
                    new WebPushSubscription(sub.getEndpoint(),
                            new WebPushKeys(sub.getP256dh(), sub.getAuth())),
                    new WebPushMessage(title, body));
            return true;
        } catch (WebPushException e) {
            // Vaadin's WebPush throws without a cause exactly for the gone-subscription
            // (404/410) case; everything else (network, 5xx) is wrapped with one.
            if (e.getCause() == null) {
                return false;
            }
            log.warn("Push send failed (will not retry): {}", e.getMessage());
            return true;
        }
    }

    private WebPush webPush() {
        WebPush wp = webPush;
        if (wp == null) {
            synchronized (this) {
                if (webPush == null) {
                    webPush = new WebPush(publicKey, privateKey, subject);
                }
                wp = webPush;
            }
        }
        return wp;
    }
}
