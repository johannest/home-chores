package com.homechores.ui;

/**
 * An id read back from the browser — a drop event's detail, never a server-rendered id captured
 * in a lambda. Parsing is all this does: whether the id belongs to the session's home is the
 * service's check, made again for every drop (SPEC §4.2b).
 */
final class ClientId {

    private ClientId() {
    }

    /** The id, or null for a blank or unparseable string (a drop at the end sends blank). */
    static Long parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
