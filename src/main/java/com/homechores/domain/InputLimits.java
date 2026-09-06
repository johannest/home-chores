package com.homechores.domain;

/**
 * Length limits for user-typed text, shared by all three enforcement layers: the UI
 * fields ({@code setMaxLength} — a convenience, trivially bypassed in the browser), the
 * service methods ({@link #clip} — the actual gate), and the entity columns
 * ({@code @Column(length = …)} — the schema backstop; note {@code ddl-auto=update} never
 * narrows a column that already exists, so pre-limit databases keep their old widths and
 * rely on the service layer alone).
 *
 * <p>Oversized input is truncated rather than rejected, following the precedent set by
 * the "other help" note: nothing a family types here is worth a failed save, and a
 * flooder's lorem ipsum is cut to a harmless stub either way.
 */
public final class InputLimits {

    /** Home name — create form and admin rename. */
    public static final int HOME_NAME = 64;

    /** Member nickname — create, join, rejoin and admin rename. */
    public static final int MEMBER_NAME = 40;

    /** Chore name — member add-chore, admin editor, promote-help. */
    public static final int TASK_NAME = 60;

    /**
     * Chore group name. Shorter than a chore name on purpose: a group is a heading on a
     * ~360px board, sitting beside an emoji, and it must not wrap to a second line.
     */
    public static final int GROUP_NAME = 30;

    /**
     * Chore emoji, server-side cap. The UI caps at 4 UTF-16 units, but a single emoji
     * grapheme (skin tones, family sequences) can be longer — 16 leaves room for one
     * real emoji while still shutting out free text.
     */
    public static final int EMOJI = 16;

    /** Redeem note / credit reason. Same size as the "other help" note. */
    public static final int REASON = 200;

    /** Canonical "HH:mm-HH:mm[, …]" availability string for one chore. */
    public static final int TIME_WINDOWS = 120;

    /** Maximum number of comma-separated availability windows on one chore. */
    public static final int TIME_WINDOW_COUNT = 6;

    /** Ceiling for credit values (per chore, per approval) and spree bonus credits. */
    public static final int MAX_CREDITS = 1000;

    /** Ceiling for day counts (chore interval, spree tier length). */
    public static final int MAX_DAYS = 365;

    private InputLimits() {
    }

    /**
     * Trims, then truncates to {@code max} code units. Null-safe (returns null).
     *
     * <p>Never cuts a surrogate pair in half. Emoji, and any character outside the basic
     * multilingual plane, occupy two code units in a Java string; cutting between them
     * leaves an unpaired half that is not a character at all and renders as a replacement
     * glyph wherever it is shown. {@link #EMOJI} makes this reachable in normal use — a
     * family-sequence or skin-toned emoji is long enough to land on the boundary — and the
     * result would be stored, exported to backups and shown on the board.
     */
    public static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        int end = max;
        if (end > 0 && Character.isHighSurrogate(trimmed.charAt(end - 1))
                && Character.isLowSurrogate(trimmed.charAt(end))) {
            end--; // drop the orphaned high half rather than storing it alone
        }
        return trimmed.substring(0, end).trim();
    }
}
