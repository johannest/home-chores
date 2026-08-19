package com.homechores.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.Month;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The seasonal availability constraint. Mirrors {@link TimeWindows}' contract, so the two
 * interesting behaviours to pin are the canonical form and the deliberate fail-open on read.
 */
class SeasonsTest {

    private static LocalDate in(Month month) {
        return LocalDate.of(2026, month, 15);
    }

    // ---- normalize ---------------------------------------------------------

    @Test
    void blankMeansAllYearRound() {
        assertNull(Seasons.normalize((String) null));
        assertNull(Seasons.normalize(""));
        assertNull(Seasons.normalize("   "));
        assertNull(Seasons.normalize(Set.of()));
        assertNull(Seasons.normalize((Set<Season>) null));
    }

    @Test
    void normalize_upperCasesAndTrims() {
        assertEquals("WINTER", Seasons.normalize(" winter "));
    }

    /** Storage must not depend on the order the admin ticked the boxes. */
    @Test
    void normalize_emitsDeclarationOrder_regardlessOfInputOrder() {
        assertEquals("SPRING,WINTER", Seasons.normalize("WINTER, spring"));
        assertEquals("SPRING,WINTER", Seasons.normalize("spring,winter"));
        assertEquals("SPRING,AUTUMN",
                Seasons.normalize(EnumSet.of(Season.AUTUMN, Season.SPRING)));
    }

    @Test
    void normalize_collapsesDuplicates() {
        assertEquals("SUMMER", Seasons.normalize("SUMMER,summer, SUMMER"));
    }

    /** "Every season" and "no restriction" are the same thing, and need one representation. */
    @Test
    void allFourSeasons_collapseToNoRestriction() {
        assertNull(Seasons.normalize("SPRING,SUMMER,AUTUMN,WINTER"));
        assertNull(Seasons.normalize(EnumSet.allOf(Season.class)));
    }

    /** Editor input is validated; a typo should be reported, not silently stored. */
    @Test
    void normalize_rejectsAnUnknownSeason() {
        assertThrows(IllegalArgumentException.class, () -> Seasons.normalize("AUTUMN,BOGUS"));
    }

    // ---- isInSeason --------------------------------------------------------

    @Test
    void untaggedChore_isInSeasonEveryMonth() {
        for (Month m : Month.values()) {
            assertTrue(Seasons.isInSeason(null, in(m)), "null should be all-year, failed in " + m);
            assertTrue(Seasons.isInSeason("  ", in(m)), "blank should be all-year, failed in " + m);
        }
    }

    @Test
    void winterChore_appliesOnlyInWinter() {
        assertTrue(Seasons.isInSeason("WINTER", in(Month.DECEMBER)));
        assertTrue(Seasons.isInSeason("WINTER", in(Month.JANUARY)));
        assertTrue(Seasons.isInSeason("WINTER", in(Month.FEBRUARY)));
        assertFalse(Seasons.isInSeason("WINTER", in(Month.MARCH)));
        assertFalse(Seasons.isInSeason("WINTER", in(Month.JULY)));
    }

    @Test
    void twoSeasonChore_appliesInBoth() {
        String tyreChange = "SPRING,AUTUMN";
        assertTrue(Seasons.isInSeason(tyreChange, in(Month.APRIL)));
        assertTrue(Seasons.isInSeason(tyreChange, in(Month.OCTOBER)));
        assertFalse(Seasons.isInSeason(tyreChange, in(Month.JULY)));
        assertFalse(Seasons.isInSeason(tyreChange, in(Month.JANUARY)));
    }

    @Test
    void seasonsChangeOnTheFirstOfTheMonth() {
        assertTrue(Seasons.isInSeason("WINTER", LocalDate.of(2026, 2, 28)));
        assertFalse(Seasons.isInSeason("WINTER", LocalDate.of(2026, 3, 1)));
        assertTrue(Seasons.isInSeason("AUTUMN", LocalDate.of(2026, 11, 30)));
        assertFalse(Seasons.isInSeason("AUTUMN", LocalDate.of(2026, 12, 1)));
    }

    /**
     * Reading fails open, exactly as {@link TimeWindows#isWithinAny} does: a value mangled by a
     * hand-edited backup must never lock a chore away where nobody can reach it.
     */
    @Test
    void unparseableValue_failsOpen() {
        for (Month m : Month.values()) {
            assertTrue(Seasons.isInSeason("NOPE", in(m)), "should fail open, blocked in " + m);
            assertTrue(Seasons.isInSeason(",,,", in(m)));
        }
    }

    // ---- editor and display helpers ---------------------------------------

    @Test
    void asSet_roundTripsThroughNormalize() {
        assertEquals(Set.of(), Seasons.asSet(null));
        assertEquals(Set.of(), Seasons.asSet(""));
        assertEquals(EnumSet.of(Season.SPRING, Season.WINTER), Seasons.asSet("SPRING,WINTER"));
        assertEquals("SPRING,WINTER", Seasons.normalize(Seasons.asSet("SPRING,WINTER")));
    }

    @Test
    void displayCompact_isEmojiInDeclarationOrder() {
        assertEquals("", Seasons.displayCompact(null));
        assertEquals("", Seasons.displayCompact("garbage"));
        assertEquals("🌱🍂", Seasons.displayCompact("AUTUMN,SPRING"));
        assertEquals("❄️", Seasons.displayCompact("winter"));
    }

    @Test
    void everyMonthMapsToASeason() {
        assertEquals(Season.SPRING, Season.of(Month.MARCH));
        assertEquals(Season.SUMMER, Season.of(Month.JULY));
        assertEquals(Season.AUTUMN, Season.of(Month.SEPTEMBER));
        assertEquals(Season.WINTER, Season.of(Month.JANUARY));
    }
}
