package com.homechores.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Bucket boundaries for the board's frequency filter. Pure arithmetic, so no Spring context —
 * the point of putting {@link Cadence} in the domain is that it needs nothing.
 */
class CadenceTest {

    /** The values the frequency presets write must land where their labels promise. */
    @Test
    void presetValues_landInTheirOwnBucket() {
        assertEquals(Cadence.ANYTIME, Cadence.of(0));
        assertEquals(Cadence.DAILY, Cadence.of(1));
        assertEquals(Cadence.WEEKLY, Cadence.of(7));
        assertEquals(Cadence.WEEKLY, Cadence.of(14), "a fortnight is a weekly rhythm");
        assertEquals(Cadence.MONTHLY, Cadence.of(30));
        assertEquals(Cadence.MULTI_MONTH, Cadence.of(90));
        assertEquals(Cadence.YEARLY, Cadence.of(365));
    }

    @Test
    void boundaries_fallWhereTheyAreDocumented() {
        assertEquals(Cadence.DAILY, Cadence.of(2));
        assertEquals(Cadence.WEEKLY, Cadence.of(3));

        assertEquals(Cadence.WEEKLY, Cadence.of(14));
        assertEquals(Cadence.MONTHLY, Cadence.of(15));

        assertEquals(Cadence.MONTHLY, Cadence.of(51));
        assertEquals(Cadence.MULTI_MONTH, Cadence.of(52));

        assertEquals(Cadence.MULTI_MONTH, Cadence.of(180));
        assertEquals(Cadence.YEARLY, Cadence.of(181));
    }

    /** All three ways people write "monthly" must agree, since the boundary sits far from them. */
    @Test
    void everyWayOfWritingMonthly_agrees() {
        assertEquals(Cadence.MONTHLY, Cadence.of(28));
        assertEquals(Cadence.MONTHLY, Cadence.of(30));
        assertEquals(Cadence.MONTHLY, Cadence.of(31));
    }

    @Test
    void handTypedOddballs_landSomewhereSensible() {
        assertEquals(Cadence.WEEKLY, Cadence.of(5));
        assertEquals(Cadence.MONTHLY, Cadence.of(45));
        assertEquals(Cadence.MULTI_MONTH, Cadence.of(60));
        assertEquals(Cadence.YEARLY, Cadence.of(200));
    }

    /** The service clamps negatives, but a lens must never be the thing that throws. */
    @Test
    void negativeInterval_readsAsAnytime() {
        assertEquals(Cadence.ANYTIME, Cadence.of(-3));
        assertEquals(Cadence.ANYTIME, Cadence.of(Integer.MIN_VALUE));
    }

    /**
     * Longer intervals must never map to a shorter bucket. One assertion that catches any future
     * off-by-one in the boundary chain, whichever cut is edited.
     */
    @Test
    void bucketsIncreaseMonotonically_withTheInterval() {
        Cadence previous = Cadence.of(0);
        for (int days = 0; days <= 400; days++) {
            Cadence current = Cadence.of(days);
            assertNotNull(current, "no bucket for " + days);
            assertTrue(current.ordinal() >= previous.ordinal(),
                    "bucket went backwards at " + days + ": " + previous + " -> " + current);
            previous = current;
        }
        assertEquals(Cadence.YEARLY, previous);
    }
}
