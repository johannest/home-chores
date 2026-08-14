package com.homechores.domain;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Daily availability windows for a chore, stored canonically as
 * {@code "HH:mm-HH:mm[,HH:mm-HH:mm…]"} (e.g. the dog walk: {@code 08:00-10:00,18:00-22:00}).
 * A blank/null value means the chore is available all day. Windows are evaluated against
 * the member's local wall-clock time; the end of a range is exclusive.
 */
public final class TimeWindows {

    private TimeWindows() {
    }

    /**
     * Normalizes user input like {@code "8-10, 18:30-22"} to the canonical form
     * {@code "08:00-10:00,18:30-22:00"}. Returns null for blank input (= always available);
     * throws {@link IllegalArgumentException} for input that can't be parsed.
     */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (LocalTime[] w : parse(input)) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(fmt(w[0])).append('-').append(fmt(w[1]));
        }
        return sb.toString();
    }

    /**
     * Whether {@code now} falls inside any window. Blank windows mean "always available",
     * and unparseable data (e.g. from a hand-edited backup) fails open for the same reason:
     * a broken constraint should never lock a chore permanently.
     */
    public static boolean isWithinAny(String windows, LocalTime now) {
        if (windows == null || windows.isBlank()) {
            return true;
        }
        List<LocalTime[]> parsed;
        try {
            parsed = parse(windows);
        } catch (IllegalArgumentException e) {
            return true;
        }
        for (LocalTime[] w : parsed) {
            if (!now.isBefore(w[0]) && now.isBefore(w[1])) {
                return true;
            }
        }
        return false;
    }

    /** Short human form for badges and messages: {@code "08:00-10:00,18:00-22:00"} → {@code "8–10, 18–22"}. */
    public static String displayCompact(String windows) {
        if (windows == null || windows.isBlank()) {
            return "";
        }
        List<LocalTime[]> parsed;
        try {
            parsed = parse(windows);
        } catch (IllegalArgumentException e) {
            return windows;
        }
        StringBuilder sb = new StringBuilder();
        for (LocalTime[] w : parsed) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(compact(w[0])).append('–').append(compact(w[1]));
        }
        return sb.toString();
    }

    private static List<LocalTime[]> parse(String input) {
        List<LocalTime[]> result = new ArrayList<>();
        for (String part : input.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            String[] ends = part.split("-");
            if (ends.length != 2) {
                throw new IllegalArgumentException("Bad time range: " + part);
            }
            LocalTime start = parseTime(ends[0]);
            LocalTime end = parseTime(ends[1]);
            if (!start.isBefore(end)) {
                throw new IllegalArgumentException("Start must be before end: " + part);
            }
            result.add(new LocalTime[]{start, end});
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("No time ranges given");
        }
        return result;
    }

    private static LocalTime parseTime(String s) {
        String t = s.trim();
        try {
            int colon = t.indexOf(':');
            int hour = Integer.parseInt(colon < 0 ? t : t.substring(0, colon));
            int minute = colon < 0 ? 0 : Integer.parseInt(t.substring(colon + 1));
            if (hour == 24 && minute == 0) {
                return LocalTime.MAX; // allow "…-24" to mean "until midnight"
            }
            return LocalTime.of(hour, minute);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Bad time: " + s);
        }
    }

    private static String fmt(LocalTime t) {
        return t.equals(LocalTime.MAX) ? "24:00"
                : String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private static String compact(LocalTime t) {
        if (t.equals(LocalTime.MAX)) {
            return "24";
        }
        return t.getMinute() == 0 ? String.valueOf(t.getHour())
                : t.getHour() + ":" + String.format("%02d", t.getMinute());
    }
}
