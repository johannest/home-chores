package com.homechores.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A routine's schedule (§4.4f): which dates it falls on and when its next reminder is due. */
class RoutineTest {

    private static final ZoneId HELSINKI = ZoneId.of("Europe/Helsinki");
    /** A Monday. */
    private static final LocalDate MON = LocalDate.of(2026, 11, 2);

    private static Routine routine(Set<DayOfWeek> days, int everyWeeks, LocalDate anchor, LocalTime time) {
        Routine r = new Routine("HOME", 1L);
        r.setWeekdays(Routine.mask(days));
        r.setEveryWeeks(everyWeeks);
        r.setAnchorMonday(anchor);
        r.setTime(time);
        return r;
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(HELSINKI).toInstant();
    }

    @Test
    void theMask_roundTripsEveryWeekday() {
        Set<DayOfWeek> days = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY);
        Routine r = routine(days, 1, MON, null);
        assertEquals(days, r.days());
        assertEquals(0b1001001, r.getWeekdays());
    }

    @Test
    void weekly_occursOnItsWeekdaysOnly() {
        Routine r = routine(EnumSet.of(DayOfWeek.TUESDAY), 1, MON, null);
        assertTrue(r.occursOn(MON.plusDays(1)));
        assertTrue(r.occursOn(MON.plusDays(8)), "next Tuesday too");
        assertFalse(r.occursOn(MON));
        assertFalse(r.occursOn(MON.plusDays(2)));
    }

    @Test
    void everyOtherWeek_fallsOnTheAnchorsWeeksAndSkipsTheOthers_inBothDirections() {
        Routine r = routine(EnumSet.of(DayOfWeek.TUESDAY), Routine.EVERY_OTHER_WEEK, MON, null);
        assertTrue(r.occursOn(MON.plusDays(1)));
        assertFalse(r.occursOn(MON.plusDays(8)), "the week after is off");
        assertTrue(r.occursOn(MON.plusDays(15)));
        assertTrue(r.occursOn(MON.minusDays(13)), "two weeks before the anchor is on too");
        assertFalse(r.occursOn(MON.minusDays(6)));
    }

    @Test
    void theNextReminder_isLaterTodayIfStillAhead_otherwiseTheNextOccurrence() {
        Routine r = routine(EnumSet.of(DayOfWeek.TUESDAY), 1, MON, LocalTime.of(18, 0));
        LocalDate tue = MON.plusDays(1);
        assertEquals(at(tue, 18, 0), r.fireAfter(at(tue, 9, 0), HELSINKI));
        assertEquals(at(tue.plusWeeks(1), 18, 0), r.fireAfter(at(tue, 18, 0), HELSINKI),
                "exactly at the time is already past: strictly after");
        assertEquals(at(tue, 18, 0), r.fireAfter(at(MON, 23, 0), HELSINKI));
    }

    @Test
    void everyOtherWeek_remindsAcrossTheOffWeek() {
        Routine r = routine(EnumSet.of(DayOfWeek.TUESDAY), Routine.EVERY_OTHER_WEEK, MON,
                LocalTime.of(18, 0));
        LocalDate tue = MON.plusDays(1);
        assertEquals(at(tue.plusWeeks(2), 18, 0), r.fireAfter(at(tue, 19, 0), HELSINKI));
    }

    @Test
    void theTimeIsWallClockInTheHomesZone_acrossTheSpringForward() {
        // Europe/Helsinki springs forward on Sunday 2027-03-28 at 03:00 → 04:00.
        LocalDate before = LocalDate.of(2027, 3, 21);
        Routine r = routine(EnumSet.of(DayOfWeek.SUNDAY), 1, Routine.mondayOf(before),
                LocalTime.of(18, 0));
        Instant first = r.fireAfter(at(before, 19, 0), HELSINKI);
        assertEquals(LocalDateTime.of(2027, 3, 28, 18, 0), LocalDateTime.ofInstant(first, HELSINKI),
                "18:00 on the clock, not 18:00 plus or minus an hour");
        Routine inGap = routine(EnumSet.of(DayOfWeek.SUNDAY), 1, Routine.mondayOf(before),
                LocalTime.of(3, 30));
        assertEquals(LocalDateTime.of(2027, 3, 28, 4, 30),
                LocalDateTime.ofInstant(inGap.fireAfter(at(before, 19, 0), HELSINKI), HELSINKI),
                "a time the clock skips lands just after the gap");
    }

    @Test
    void noTime_meansNoReminder() {
        Routine r = routine(EnumSet.allOf(DayOfWeek.class), 1, MON, null);
        assertNull(r.fireAfter(at(MON, 9, 0), HELSINKI));
    }
}
