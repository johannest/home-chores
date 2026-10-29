package com.homechores.ui;

import com.homechores.domain.Routine;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** How a {@link Routine}'s schedule reads, on the Routines tab and the Chores tab's strip alike. */
final class RoutineFormat {

    private static final Set<DayOfWeek> WORKDAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private RoutineFormat() {
    }

    /** "Tue, Thu", "Weekdays" or "Every day", in the UI language. */
    static String days(Set<DayOfWeek> days, Locale locale) {
        if (days.size() == DayOfWeek.values().length) {
            return T.tr("routine.everyDay");
        }
        if (days.equals(WORKDAYS)) {
            return T.tr("routine.workdays");
        }
        List<String> names = new ArrayList<>();
        for (DayOfWeek d : days) {
            names.add(dayName(d, locale));
        }
        return String.join(", ", names);
    }

    /** "Tue" — capitalised, since Finnish and Swedish give lowercase. */
    static String dayName(DayOfWeek day, Locale locale) {
        String s = day.getDisplayName(TextStyle.SHORT_STANDALONE, locale);
        return s.substring(0, 1).toUpperCase(locale) + s.substring(1);
    }

    static String time(Routine r) {
        return r.getTime() == null ? null : TIME.format(r.getTime());
    }

    /**
     * "Tue, Thu · 18:00 · every other week · Johannes": the whole schedule on one line. Whose it
     * is ends the line, so the Routines tab — where everyone's are listed — says it, and a
     * routine of a member who has since gone reads "?" rather than failing.
     */
    static String schedule(Routine r, Locale locale, Map<Long, String> names) {
        List<String> parts = new ArrayList<>();
        parts.add(days(r.days(), locale));
        if (r.getTime() != null) {
            parts.add(time(r));
        }
        if (r.getEveryWeeks() == Routine.EVERY_OTHER_WEEK) {
            parts.add(T.tr("routine.everyOtherWeek"));
        }
        parts.add(r.getForMemberId() == null ? T.tr("routine.forEveryone")
                : names.getOrDefault(r.getForMemberId(), "?"));
        return String.join(" · ", parts);
    }
}
