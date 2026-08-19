package com.homechores.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Seasonal availability for a chore, stored canonically as {@code "SPRING[,SUMMER…]"} in
 * {@link Season} declaration order. A blank/null value means the chore applies all year round.
 *
 * <p>Deliberately shaped like {@link TimeWindows}, its sibling constraint: one String column, one
 * static helper, blank means unrestricted, and unparseable data fails open. Windows say <em>time of
 * day</em>, these say <em>time of year</em>.
 */
public final class Seasons {

    private Seasons() {
    }

    /**
     * Canonicalizes a stored or hand-typed value like {@code "winter, SPRING"} to
     * {@code "SPRING,WINTER"}. Returns null for blank input, and also for a value naming all four
     * seasons — "every season" is the same thing as "no restriction", and there must be exactly one
     * representation of it. Throws {@link IllegalArgumentException} on an unknown season name.
     */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        Set<Season> found = EnumSet.noneOf(Season.class);
        for (String part : input.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                found.add(Season.valueOf(token.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Not a season: " + token, e);
            }
        }
        return normalize(found);
    }

    /**
     * Canonicalizes a set straight from the editor. Null, empty and all-four all mean
     * "all year round" and collapse to null.
     */
    public static String normalize(Set<Season> seasons) {
        if (seasons == null || seasons.isEmpty() || seasons.size() == Season.values().length) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Season s : sorted(seasons)) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(s.name());
        }
        return sb.toString();
    }

    /**
     * Whether the chore applies on this date. Blank means all year round, and unparseable data
     * (e.g. from a hand-edited backup) fails open — exactly as
     * {@link TimeWindows#isWithinAny} does, and for the same reason: a broken constraint must never
     * lock a chore away permanently.
     */
    public static boolean isInSeason(String seasons, LocalDate date) {
        if (seasons == null || seasons.isBlank()) {
            return true;
        }
        List<Season> parsed = toList(seasons);
        if (parsed.isEmpty()) {
            return true;
        }
        return parsed.contains(Season.of(date.getMonth()));
    }

    /** The seasons named by a stored value, in declaration order; unknown names are skipped. */
    public static List<Season> toList(String seasons) {
        if (seasons == null || seasons.isBlank()) {
            return List.of();
        }
        Set<Season> found = EnumSet.noneOf(Season.class);
        for (String part : seasons.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                found.add(Season.valueOf(token.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                // Lenient on read: see isInSeason.
            }
        }
        return sorted(found);
    }

    /** Editor-friendly view of a stored value; empty for "all year round". */
    public static Set<Season> asSet(String seasons) {
        Set<Season> set = EnumSet.noneOf(Season.class);
        set.addAll(toList(seasons));
        return set;
    }

    /**
     * Short human form for a card badge: {@code "SPRING,AUTUMN"} → {@code "🌱🍂"}. Emoji rather than
     * words because the badge shares a 136px card with the chore name — and emoji need no
     * translating. Message text uses localized season names instead.
     */
    public static String displayCompact(String seasons) {
        List<Season> parsed = toList(seasons);
        if (parsed.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Season s : parsed) {
            sb.append(emoji(s));
        }
        return sb.toString();
    }

    /** The emoji for one season. */
    public static String emoji(Season season) {
        return switch (season) {
            case SPRING -> "🌱";
            case SUMMER -> "☀️";
            case AUTUMN -> "🍂";
            case WINTER -> "❄️";
        };
    }

    private static List<Season> sorted(Set<Season> seasons) {
        List<Season> list = new ArrayList<>(seasons);
        list.sort(Comparator.comparingInt(Season::ordinal));
        return list;
    }
}
