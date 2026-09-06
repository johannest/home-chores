package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The push endpoint is the only URL in the app that a client chooses and the server then
 * requests, which makes it the one server-side request forgery surface. These tests are
 * the list of destinations it must never be talked into.
 */
class PushEndpointsTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "https://fcm.googleapis.com/fcm/send/abc123",
        "https://updates.push.services.mozilla.com/wpush/v2/gAAAAA",
        "https://web.push.apple.com/QLMNOP",
        "https://sfo.notify.windows.com/w/?token=xyz",
        "https://push.example/1",
    })
    void realPushEndpointsAreAccepted(String endpoint) {
        assertTrue(PushEndpoints.isWellFormed(endpoint), endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // Straight at the host itself.
        "https://127.0.0.1/anything",
        "https://127.0.0.1:9200/_cluster/settings",
        "https://localhost/admin",
        "https://[::1]/admin",
        "https://0.0.0.0/",
        // Cloud instance metadata — the classic SSRF payoff.
        "https://169.254.169.254/latest/meta-data/iam/security-credentials/",
        // Private and carrier-grade ranges by literal.
        "https://10.0.0.5/",
        "https://192.168.1.1/",
        "https://172.16.0.9/",
        "https://100.64.0.1/",
        // Names that only exist inside a network.
        "https://elasticsearch/_search",
        "https://db.internal/",
        "https://printer.local/",
        "https://gateway.lan/",
        "https://svc.home.arpa/",
        // Wrong scheme, or credentials smuggled into the authority.
        "http://fcm.googleapis.com/fcm/send/abc",
        "file:///etc/passwd",
        "gopher://fcm.googleapis.com/",
        "https://user:pass@fcm.googleapis.com/fcm/send/abc",
        // Not a URL at all.
        "javascript:alert(1)",
        "not a url",
    })
    void endpointsThatMustNeverBeRequestedAreRefused(String endpoint) {
        assertFalse(PushEndpoints.isWellFormed(endpoint), endpoint);
    }

    @Test
    void nullBlankAndOversizedAreRefused() {
        assertFalse(PushEndpoints.isWellFormed(null));
        assertFalse(PushEndpoints.isWellFormed(""));
        assertFalse(PushEndpoints.isWellFormed("   "));
        assertFalse(PushEndpoints.isWellFormed(
                "https://push.example/" + "x".repeat(PushEndpoints.MAX_LENGTH)));
    }

    /**
     * The address check is the half that names cannot do. It is only reached for values
     * that already passed {@link PushEndpoints#isWellFormed}, and a host that does not
     * resolve answers "no" — an endpoint the server cannot verify is not one to open a
     * connection to.
     */
    @Test
    void aHostThatCannotBeResolvedIsNotTreatedAsPublic() {
        // .invalid is reserved by RFC 2606 precisely so it never resolves.
        assertFalse(PushEndpoints.resolvesToPublicAddress("https://nope.invalid/wpush"));
    }

    @Test
    void aNameThatPointsAtTheLoopbackIsRefusedEvenThoughItLooksFine() {
        // localhost.localdomain-style names are caught by shape; this one needs the lookup.
        assertFalse(PushEndpoints.resolvesToPublicAddress("https://localtest.me/wpush"),
                "resolves to 127.0.0.1 — the check that shape alone cannot make");
    }

    @Test
    void theAddressCheckAlsoAppliesTheShapeRules() {
        assertFalse(PushEndpoints.resolvesToPublicAddress("http://fcm.googleapis.com/x"));
        assertFalse(PushEndpoints.resolvesToPublicAddress("https://127.0.0.1/x"));
    }
}
