package com.homechores.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.SerializableConsumer;
import java.util.Optional;

/**
 * Keeps "who is this phone" in the browser's local storage rather than in a long-lived
 * server session.
 *
 * <p>{@link SessionContext} still holds the signed-in member for the duration of a
 * {@code VaadinSession}, but that session is deliberately short-lived (3 minutes for a
 * member, 15 for an admin — see {@link SessionContext#applyTimeout}) so the server holds no
 * state for phones that aren't actively using the app.
 * Local storage is what carries the identity across session timeouts, restarts and app
 * relaunches — it survives everything except the user clearing site data, which is exactly
 * what the rejoin flow in {@link LandingView} recovers from.
 */
final class DeviceIdentity {

    /** Stored as "memberId|homeCode|secret" — no JSON parsing needed on the way back.
     *  The secret (issued per sign-in, hash held server-side) is what stops a forged
     *  localStorage value from stepping into another member's identity. */
    private static final String IDENTITY_KEY = "flashchores.identity";

    /** Secret for a rejoin request this device is waiting on. */
    private static final String REJOIN_KEY = "flashchores.rejoin";

    /** Local storage throws in Safari private mode, so every access is guarded. */
    private static final String READ = "try{return localStorage.getItem($0)||''}catch(e){return ''}";

    private DeviceIdentity() {
    }

    /** A member id + home code + device secret recovered from the browser. {@code secret}
     *  is null for a value written before secrets existed (see {@link #parse}). */
    record Stored(Long memberId, String homeCode, String secret) {
    }

    static void remember(Long memberId, String homeCode, String secret) {
        exec("try{localStorage.setItem($0,$1)}catch(e){}",
                IDENTITY_KEY, memberId + "|" + homeCode + "|" + secret);
    }

    /** Clears everything this app stored — used on Leave and on a stale/invalid identity. */
    static void forget() {
        exec("try{localStorage.removeItem($0);localStorage.removeItem($1)}catch(e){}",
                IDENTITY_KEY, REJOIN_KEY);
    }

    static void rememberRejoinToken(String token) {
        exec("try{localStorage.setItem($0,$1)}catch(e){}", REJOIN_KEY, token);
    }

    static void forgetRejoinToken() {
        exec("try{localStorage.removeItem($0)}catch(e){}", REJOIN_KEY);
    }

    /** Reads the stored identity; the callback gets an empty optional if there is none. */
    static void read(SerializableConsumer<Optional<Stored>> callback) {
        UI ui = UI.getCurrent();
        if (ui == null) {
            callback.accept(Optional.empty());
            return;
        }
        ui.getPage().executeJs(READ, IDENTITY_KEY)
                .then(String.class, raw -> callback.accept(parse(raw)));
    }

    /** Reads the pending rejoin token; the callback gets an empty optional if there is none. */
    static void readRejoinToken(SerializableConsumer<Optional<String>> callback) {
        UI ui = UI.getCurrent();
        if (ui == null) {
            callback.accept(Optional.empty());
            return;
        }
        ui.getPage().executeJs(READ, REJOIN_KEY).then(String.class, raw ->
                callback.accept(raw == null || raw.isBlank() ? Optional.empty() : Optional.of(raw)));
    }

    private static Optional<Stored> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String[] parts = raw.split("\\|", 3);
        // Two parts is the pre-secret format. It surfaces with a null secret so the
        // landing view can offer it for one-time migration (see LandingView.restoreFrom);
        // it is never enough to sign in by itself.
        if (parts.length < 2 || parts[1].isBlank()
                || (parts.length == 3 && parts[2].isBlank())) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Stored(Long.valueOf(parts[0]), parts[1],
                    parts.length == 3 ? parts[2] : null));
        } catch (NumberFormatException e) {
            return Optional.empty(); // storage tampered with or written by an older version
        }
    }

    private static void exec(String js, Object... params) {
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.getPage().executeJs(js, params);
        }
    }
}
