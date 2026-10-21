package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.PushSubscription;
import com.homechores.service.WebPushSender.Delivery;
import com.interaso.webpush.CryptoKt;
import com.interaso.webpush.VapidKeys;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The HTTP side of a push send, against a local server standing in for the push service.
 * The point under test is the one the security audit called a blocker: the endpoint is
 * browser-chosen, every sweep runs on the single scheduler thread, and a send must therefore
 * never wait longer than its deadline whatever the far end does.
 */
class WebPushSenderTest {

    private static final VapidKeys VAPID = VapidKeys.generate();
    private static String p256dh;
    private static String auth;

    /** A browser-side key pair, as {@code PushSubscription.toJSON()} would hand it over. */
    @BeforeAll
    static void browserKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        ECPublicKey publicKey = (ECPublicKey) generator.generateKeyPair().getPublic();
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        p256dh = url.encodeToString(CryptoKt.getUncompressedBytes(publicKey));
        byte[] secret = new byte[16];
        new SecureRandom().nextBytes(secret);
        auth = url.encodeToString(secret);
    }

    private static WebPushSender sender(long sendTimeoutMs) {
        return new WebPushSender(VAPID.getX509PublicKey(), VAPID.getPkcs8PrivateKey(),
                "mailto:test@example.com", sendTimeoutMs);
    }

    private static HttpServer pushService(HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push", handler);
        server.start();
        return server;
    }

    private static String endpointOf(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/push";
    }

    @Test
    void aPushServiceThatNeverAnswers_holdsTheSweepNoLongerThanTheDeadline() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = pushService(exchange -> {
            try {
                release.await(); // accept the connection, read nothing, answer nothing
            } catch (InterruptedException ignored) {
                // released below
            }
            exchange.close();
        });
        try {
            WebPushSender sender = sender(300);
            String endpoint = endpointOf(server);
            long started = System.nanoTime();
            Delivery outcome = sender.bounded(() -> sender.post(endpoint, p256dh, auth, "{}"));
            long waitedMs = (System.nanoTime() - started) / 1_000_000;

            assertEquals(Delivery.FAILED, outcome, "gave up, and the row is kept");
            assertTrue(waitedMs < 3_000,
                    "the sweep waited for the deadline, not for the server: " + waitedMs + " ms");
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    /** The bound holds for the exchange as a whole, DNS included, not only for the socket. */
    @Test
    void anExchangeThatOverrunsTheDeadline_isGivenUpOn() {
        WebPushSender sender = sender(200);
        long started = System.nanoTime();
        Delivery outcome = sender.bounded(() -> {
            Thread.sleep(10_000);
            return Delivery.DELIVERED;
        });
        long waitedMs = (System.nanoTime() - started) / 1_000_000;

        assertEquals(Delivery.FAILED, outcome);
        assertTrue(waitedMs < 2_000, "returned at the deadline: " + waitedMs + " ms");
    }

    @Test
    void thePushServicesAnswers_mapToDeliveredGoneAndKept() throws Exception {
        AtomicInteger status = new AtomicInteger(201);
        AtomicReference<Headers> seen = new AtomicReference<>();
        AtomicInteger bodyLength = new AtomicInteger();
        HttpServer server = pushService(exchange -> {
            seen.set(exchange.getRequestHeaders());
            bodyLength.set(exchange.getRequestBody().readAllBytes().length);
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        try {
            WebPushSender sender = sender(5_000);
            String endpoint = endpointOf(server);

            assertEquals(Delivery.DELIVERED,
                    sender.post(endpoint, p256dh, auth, "{\"title\":\"Chores\"}"));
            Headers headers = seen.get();
            assertNotNull(headers.getFirst("Authorization"), "VAPID-signed");
            assertTrue(headers.getFirst("Authorization").startsWith("vapid "));
            assertEquals("aes128gcm", headers.getFirst("Content-Encoding"), "RFC 8291 payload");
            assertNotNull(headers.getFirst("TTL"));
            assertTrue(bodyLength.get() > 0, "the encrypted payload went with it");

            status.set(410);
            assertEquals(Delivery.GONE, sender.post(endpoint, p256dh, auth, "{}"));
            status.set(404);
            assertEquals(Delivery.GONE, sender.post(endpoint, p256dh, auth, "{}"));
            status.set(500);
            assertEquals(Delivery.FAILED, sender.post(endpoint, p256dh, auth, "{}"),
                    "a transient failure keeps the row");
        } finally {
            server.stop(0);
        }
    }

    /** Only "gone" tells the caller to drop the row: a malformed endpoint is dropped at once,
     *  an endpoint the sender refuses to connect to (or cannot reach) is kept. */
    @Test
    void send_dropsOnlyWhatThePushServiceReportsGone() {
        WebPushSender sender = sender(5_000);
        assertFalse(sender.send(new PushSubscription(1L, "HOME", "https://localhost/push",
                p256dh, auth), "t", "b"), "never a push service: pruned");
        assertTrue(sender.send(new PushSubscription(1L, "HOME",
                "https://push.service.invalid/" + auth, p256dh, auth), "t", "b"),
                "unresolvable today: skipped, kept");
    }
}
