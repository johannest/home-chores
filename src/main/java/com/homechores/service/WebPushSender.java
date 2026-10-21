package com.homechores.service;

import com.homechores.domain.PushSubscription;
import com.interaso.webpush.VapidKeys;
import com.interaso.webpush.WebPush;
import com.vaadin.flow.server.webpush.WebPushMessage;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends Web Push notifications over an {@link HttpClient} this class owns, with the VAPID
 * signing and payload encryption done by {@code com.interaso:webpush} (the library behind
 * Vaadin's {@code flow-webpush}; plain {@code java.security}).
 *
 * <p>Vaadin's own {@code WebPush} wrapper is not used for the send, on purpose: it builds its
 * client and request with <em>no timeouts</em> and blocks on the exchange. The endpoint is the
 * one URL in this application a browser chooses and the server then connects to, and every
 * sweep that sends runs on the single scheduler thread — so a hostile endpoint that accepts
 * the connection and never answers would hold that thread, and with it every sweep for every
 * home, until the next restart. Here the whole exchange (DNS check, connect, request,
 * response) runs on a virtual thread the sweep waits on for at most {@code sendTimeout}
 * ({@code homechores.push.send-timeout-ms}); past that the sweep moves on and the exchange is
 * cancelled. The payload format is still Vaadin's ({@link WebPushMessage#toJson}), so the
 * generated service worker reads it unchanged.
 *
 * <p>Inert unless both VAPID keys are configured ({@code homechores.push.*}, typically from
 * {@code FLASHCHORES_VAPID_*} env vars; generate a pair once with
 * {@code npx web-push generate-vapid-keys}). The client is built lazily on first send:
 * constructing it starts a {@code java.net.http} selector thread, and the thread budget (see
 * application.properties) shouldn't pay for a feature that isn't in use.
 */
@Service
public class WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    /** How long to wait for the TCP connection alone; the overall bound is {@link #sendTimeout}. */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** How one delivery attempt ended. Only {@link #GONE} tells the caller to drop the row. */
    enum Delivery {
        /** The push service accepted it (2xx). */
        DELIVERED,
        /** The push service says the subscription no longer exists (404 or 410). */
        GONE,
        /** Not attempted: the endpoint's host does not resolve to a public address. */
        SKIPPED,
        /** Attempted and failed, or gave up at the deadline; the row is kept. */
        FAILED
    }

    private final String publicKey;
    private final String privateKey;
    private final String subject;
    private final Duration sendTimeout;

    /** One virtual thread per delivery: nothing in the thread budget is spent on waiting. */
    private final ExecutorService deliveries = Executors.newVirtualThreadPerTaskExecutor();

    private volatile HttpClient http;
    private volatile WebPush webPush;

    public WebPushSender(@Value("${homechores.push.public-key:}") String publicKey,
                        @Value("${homechores.push.private-key:}") String privateKey,
                        @Value("${homechores.push.subject:mailto:support@flashchores.com}")
                        String subject,
                        @Value("${homechores.push.send-timeout-ms:10000}") long sendTimeoutMs) {
        this.publicKey = publicKey == null ? "" : publicKey.trim();
        this.privateKey = privateKey == null ? "" : privateKey.trim();
        this.subject = subject;
        this.sendTimeout = Duration.ofMillis(sendTimeoutMs);
    }

    /** Whether the push subsystem is configured at all (both VAPID keys present). */
    public boolean isEnabled() {
        return !publicKey.isBlank() && !privateKey.isBlank();
    }

    /** The VAPID public key the browser needs for {@code pushManager.subscribe}. */
    public String publicKey() {
        return publicKey;
    }

    /** The longest one send may hold the calling sweep. */
    Duration sendTimeout() {
        return sendTimeout;
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
        String payload = new WebPushMessage(title, body).toJson();
        Delivery outcome = bounded(() -> {
            // The DNS lookup is part of the bounded work: a name whose authoritative server
            // never answers costs resolver retries, and those must not land on the sweep.
            if (!PushEndpoints.resolvesToPublicAddress(endpoint)) {
                // Could be transient (DNS down) or hostile (resolves into private space).
                // Skip the send but keep the row — pruning on a DNS blip would silently turn
                // a family's reminders off for good.
                log.warn("Skipping a push endpoint that does not resolve to a public address");
                return Delivery.SKIPPED;
            }
            return post(endpoint, sub.getP256dh(), sub.getAuth(), payload);
        });
        return outcome != Delivery.GONE;
    }

    /**
     * Runs one exchange on a virtual thread and waits for it at most {@link #sendTimeout};
     * a deadline that passes cancels the exchange and counts as {@link Delivery#FAILED}.
     * Package-private so a test can prove the bound without a push service.
     */
    Delivery bounded(Callable<Delivery> exchange) {
        Future<Delivery> future = deliveries.submit(exchange);
        try {
            return future.get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Push send gave up after {} ms (will not retry)", sendTimeout.toMillis());
            return Delivery.FAILED;
        } catch (ExecutionException e) {
            // The class, never the message: an HTTP client's message can quote the URL.
            log.warn("Push send failed (will not retry): {}", e.getCause().getClass().getSimpleName());
            return Delivery.FAILED;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return Delivery.FAILED;
        }
    }

    /**
     * The HTTP exchange itself: encrypt, sign, POST, read the status. Every phase of it carries
     * a timeout of its own ({@link #CONNECT_TIMEOUT}, then {@link #sendTimeout} on the request)
     * so that even outside {@link #bounded} it cannot wait forever. Package-private for the
     * tests, which point it at a local server.
     */
    Delivery post(String endpoint, String p256dh, String auth, String payload) throws Exception {
        WebPush push = webPush();
        byte[] body = push.getBody(payload.getBytes(StandardCharsets.UTF_8),
                decodeBase64(p256dh), decodeBase64(auth));
        Map<String, String> headers = push.getHeaders(endpoint, null, null, null);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(sendTimeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(request::setHeader);
        HttpResponse<Void> response = http().send(request.build(),
                HttpResponse.BodyHandlers.discarding());
        int status = response.statusCode();
        if (status == 404 || status == 410) {
            return Delivery.GONE;
        }
        if (status / 100 == 2) {
            return Delivery.DELIVERED;
        }
        log.warn("Push service answered {} (will not retry)", status);
        return Delivery.FAILED;
    }

    private HttpClient http() {
        HttpClient client = http;
        if (client == null) {
            synchronized (this) {
                if (http == null) {
                    http = HttpClient.newBuilder()
                            .connectTimeout(CONNECT_TIMEOUT)
                            .executor(deliveries)
                            .build();
                }
                client = http;
            }
        }
        return client;
    }

    private WebPush webPush() {
        WebPush push = webPush;
        if (push == null) {
            synchronized (this) {
                if (webPush == null) {
                    webPush = new WebPush(subject, vapidKeys());
                }
                push = webPush;
            }
        }
        return push;
    }

    /** The configured pair, in either of the two formats the key generators produce: DER
     *  (X.509 / PKCS#8) first, then the raw uncompressed-point form that
     *  {@code npx web-push generate-vapid-keys} writes. The same fallback Vaadin applies. */
    private VapidKeys vapidKeys() {
        try {
            return VapidKeys.create(publicKey, privateKey);
        } catch (Exception e) {
            if (e instanceof InvalidKeySpecException || e.getCause() instanceof InvalidKeySpecException) {
                return VapidKeys.fromUncompressedBytes(publicKey, privateKey);
            }
            throw e;
        }
    }

    /** {@code PushSubscription.toJSON()} hands the keys over base64url without padding; the
     *  standard alphabet is accepted too, in case a browser ever pads or uses it. */
    private static byte[] decodeBase64(String value) {
        String urlSafe = value.trim().replace('+', '-').replace('/', '_').replace("=", "");
        return Base64.getUrlDecoder().decode(urlSafe);
    }

    @PreDestroy
    void shutdown() {
        deliveries.shutdownNow();
    }
}
