package com.homechores.ui;

import com.vaadin.flow.server.VaadinSession;
import java.time.ZoneId;

/**
 * Stores which member/home the current browser session is signed in as.
 * Kept deliberately tiny — this is a lightweight, password-free "Kahoot style" login.
 */
public final class SessionContext {

    private static final String MEMBER_ID = "memberId";
    private static final String HOME_CODE = "homeCode";
    private static final String TIME_ZONE = "timeZone";
    private static final String DEVICE_SECRET = "deviceSecret";

    /**
     * How long a member's session survives without them touching the app. Short on purpose:
     * the identity lives in the phone's local storage ({@link DeviceIdentity}), so an expired
     * session costs the user nothing but frees the server of state for a phone that is only
     * lying on a table. With {@code vaadin.closeIdleSessions=true} this is measured from the
     * last real interaction — heartbeats and push traffic don't count as activity.
     */
    public static final int MEMBER_TIMEOUT_SECONDS = 300;

    /**
     * Admins get longer. Their work is filling in settings, PINs and reward tiers — forms
     * that take thought and produce no requests while they're being read, and where being
     * bounced back to the board mid-edit would be a real loss rather than a blink.
     */
    public static final int ADMIN_TIMEOUT_SECONDS = 1200;

    private SessionContext() {
    }

    /** The idle lifetime for a member, in seconds. */
    public static int timeoutSecondsFor(boolean admin) {
        return admin ? ADMIN_TIMEOUT_SECONDS : MEMBER_TIMEOUT_SECONDS;
    }

    /**
     * Applies the idle lifetime for the current session. Safe to call repeatedly — it is
     * re-applied whenever the board re-renders, so a member who has just been promoted (or
     * demoted) moves to the other lifetime without signing out and in again.
     */
    public static void applyTimeout(boolean admin) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null || session.getSession() == null) {
            return; // no HTTP session behind this one (tests, background threads)
        }
        session.getSession().setMaxInactiveInterval(timeoutSecondsFor(admin));
    }

    /** Remembers the member's browser time zone (used for chore availability windows). */
    public static void setTimeZone(ZoneId zone) {
        VaadinSession.getCurrent().setAttribute(TIME_ZONE, zone);
    }

    /** The member's browser time zone, falling back to the server's zone until known. */
    public static ZoneId timeZone() {
        ZoneId zone = (ZoneId) VaadinSession.getCurrent().getAttribute(TIME_ZONE);
        return zone != null ? zone : ZoneId.systemDefault();
    }

    public static void signIn(Long memberId, String homeCode) {
        VaadinSession s = VaadinSession.getCurrent();
        s.setAttribute(MEMBER_ID, memberId);
        s.setAttribute(HOME_CODE, homeCode);
    }

    public static void signOut() {
        VaadinSession s = VaadinSession.getCurrent();
        s.setAttribute(MEMBER_ID, null);
        s.setAttribute(HOME_CODE, null);
        s.setAttribute(DEVICE_SECRET, null);
    }

    /** Keeps the device secret for the session, so the board can re-stamp the browser's
     *  stored identity without re-issuing (see {@code HomeView#onAttach}). */
    public static void setDeviceSecret(String secret) {
        VaadinSession.getCurrent().setAttribute(DEVICE_SECRET, secret);
    }

    public static String deviceSecret() {
        return (String) VaadinSession.getCurrent().getAttribute(DEVICE_SECRET);
    }

    public static Long memberId() {
        return (Long) VaadinSession.getCurrent().getAttribute(MEMBER_ID);
    }

    public static String homeCode() {
        return (String) VaadinSession.getCurrent().getAttribute(HOME_CODE);
    }

    public static boolean isSignedIn() {
        return memberId() != null && homeCode() != null;
    }
}
