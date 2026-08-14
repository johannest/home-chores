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

    private SessionContext() {
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
