package com.homechores.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.SerializableConsumer;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** "deviceId|homeCode|secret" for a shared family device (§4.1.3); a browser holds either
     *  this or a member identity, never both. */
    private static final String DEVICE_KEY = "flashchores.device";

    /** Local storage throws in Safari private mode, so every access is guarded. */
    private static final String READ = "try{return localStorage.getItem($0)||''}catch(e){return ''}";

    /** See {@link #probeContext}: "standalone|browser,storage-ok|storage-blocked". */
    private static final String PROBE = "try{"
            + "var s='storage-ok';"
            + "try{localStorage.setItem('flashchores.probe','1');localStorage.removeItem('flashchores.probe')}"
            + "catch(e){s='storage-blocked'}"
            + "var d=(navigator.standalone===true"
            + "||(window.matchMedia&&matchMedia('(display-mode: standalone)').matches))?'standalone':'browser';"
            + "return d+','+s}catch(e){return 'unknown'}";

    private DeviceIdentity() {
    }

    /** An id (a member's, or a shared device's) + home code + secret recovered from the browser. */
    record Stored(Long id, String homeCode, String secret) {
    }

    /**
     * What the browser held under one key: an identity, nothing, or a value no current version
     * wrote ({@code unreadable}) — a pre-secret "memberId|homeCode" pair, or something
     * hand-edited. Nothing is the ordinary first visit and is not a failure; unreadable is.
     */
    record Reading(Optional<Stored> stored, boolean unreadable) {
        static final Reading NOTHING = new Reading(Optional.empty(), false);
        static final Reading UNREADABLE = new Reading(Optional.empty(), true);
    }

    /**
     * Why a value the browser held could not sign it in. Each is told to the user on the
     * landing card and logged with the browser's context, because a phone that keeps
     * landing on the join screen is otherwise indistinguishable from one that never joined.
     */
    enum RestoreFailure {
        /** The value is not one this version writes: the pre-secret format, or tampered. */
        UNREADABLE,
        /** The member or shared device the value names is gone, or is not in that home. */
        GONE,
        /** The home itself is gone (deleted, or purged by retention). */
        HOME_GONE,
        /** The row exists but holds a newer secret: another browser signed in as it since. */
        SECRET_REPLACED
    }

    static void remember(Long memberId, String homeCode, String secret) {
        exec("try{localStorage.setItem($0,$1);localStorage.removeItem($2)}catch(e){}",
                IDENTITY_KEY, memberId + "|" + homeCode + "|" + secret, DEVICE_KEY);
    }

    static void rememberDevice(Long deviceId, String homeCode, String secret) {
        exec("try{localStorage.setItem($0,$1);localStorage.removeItem($2)}catch(e){}",
                DEVICE_KEY, deviceId + "|" + homeCode + "|" + secret, IDENTITY_KEY);
    }

    /** Clears everything this app stored — used on Leave and on a stale/invalid identity. */
    static void forget() {
        exec("try{localStorage.removeItem($0);localStorage.removeItem($1);localStorage.removeItem($2)}catch(e){}",
                IDENTITY_KEY, REJOIN_KEY, DEVICE_KEY);
    }

    static void rememberRejoinToken(String token) {
        exec("try{localStorage.setItem($0,$1)}catch(e){}", REJOIN_KEY, token);
    }

    static void forgetRejoinToken() {
        exec("try{localStorage.removeItem($0)}catch(e){}", REJOIN_KEY);
    }

    /** Reads the stored member identity; the callback gets {@link Reading#NOTHING} if there is none. */
    static void read(SerializableConsumer<Reading> callback) {
        readKey(IDENTITY_KEY, callback);
    }

    /** Reads the stored shared-device identity, in the same shape as a member's. */
    static void readDevice(SerializableConsumer<Reading> callback) {
        readKey(DEVICE_KEY, callback);
    }

    private static void readKey(String key, SerializableConsumer<Reading> callback) {
        UI ui = UI.getCurrent();
        if (ui == null) {
            callback.accept(Reading.NOTHING);
            return;
        }
        ui.getPage().executeJs(READ, key).then(String.class, raw -> callback.accept(parse(raw)));
    }

    /**
     * Asks the browser how it is running, for the log line a rejected identity leaves:
     * {@code standalone} (installed on the home screen) or {@code browser}, and whether local
     * storage can be written at all ({@code storage-blocked} is private browsing or
     * "block all cookies"). The two together separate "this phone lost its storage" from
     * "another container on this phone took the member over" (§4.1).
     */
    static void probeContext(SerializableConsumer<String> callback) {
        UI ui = UI.getCurrent();
        if (ui == null) {
            callback.accept("unknown");
            return;
        }
        ui.getPage().executeJs(PROBE).then(String.class, raw -> callback.accept(knownContext(raw)));
    }

    private static final Pattern CONTEXT = Pattern.compile("(standalone|browser),(storage-ok|storage-blocked)");

    /** The probe's answer if it is one the probe can give, else {@code unknown}: the answer is
     *  the browser's to write, and whatever it says ends up in the log. */
    static String knownContext(String raw) {
        return raw != null && CONTEXT.matcher(raw).matches() ? raw : "unknown";
    }

    /**
     * A short, non-identifying label for the log from the User-Agent header: device family,
     * OS version where the UA carries one, browser and major version, and {@code webview} for
     * an iOS page outside Safari proper (an installed app or a chat app's in-app browser) —
     * the shape the diagnosis needs, without the whole string.
     */
    static String browserLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown browser";
        }
        String ua = userAgent;
        StringBuilder label = new StringBuilder();
        if (ua.contains("iPhone")) {
            label.append("iPhone");
        } else if (ua.contains("iPad")) {
            label.append("iPad");
        } else if (ua.contains("Android")) {
            label.append("Android");
        } else if (ua.contains("Macintosh")) {
            label.append("Mac");
        } else if (ua.contains("Windows")) {
            label.append("Windows");
        } else {
            label.append("other");
        }
        String ios = group(ua, "OS (\\d+[_.]\\d+)");
        if (ios != null) {
            label.append(" iOS ").append(ios.replace('_', '.'));
        }
        String android = group(ua, "Android (\\d+)");
        if (android != null) {
            label.append(' ').append(android);
        }
        label.append(", ");
        String version;
        if ((version = group(ua, "CriOS/(\\d+)")) != null || (version = group(ua, "Chrome/(\\d+)")) != null) {
            label.append("Chrome ").append(version);
        } else if ((version = group(ua, "FxiOS/(\\d+)")) != null || (version = group(ua, "Firefox/(\\d+)")) != null) {
            label.append("Firefox ").append(version);
        } else if ((version = group(ua, "Version/(\\d+)")) != null) {
            label.append("Safari ").append(version);
        } else {
            label.append(ua.contains("AppleWebKit") ? "WebKit" : "other");
        }
        if ((ua.contains("iPhone") || ua.contains("iPad")) && !ua.contains("Safari/")) {
            label.append(", webview");
        }
        return label.toString();
    }

    private static String group(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
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

    /** Package-private for the tests: the classification is what the landing page acts on. */
    static Reading parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Reading.NOTHING;
        }
        String[] parts = raw.split("\\|", 3);
        // Two parts is the format from before device secrets existed. Its one-time migration
        // path was retired in September 2026, so such a value can no longer sign anyone in;
        // the landing view explains that, clears it, and offers the sign-back-in.
        if (parts.length < 3 || parts[1].isBlank() || parts[2].isBlank()) {
            return Reading.UNREADABLE;
        }
        try {
            return new Reading(Optional.of(new Stored(Long.valueOf(parts[0]), parts[1], parts[2])), false);
        } catch (NumberFormatException e) {
            return Reading.UNREADABLE; // storage tampered with, or written by something else
        }
    }

    private static void exec(String js, Object... params) {
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.getPage().executeJs(js, params);
        }
    }
}
