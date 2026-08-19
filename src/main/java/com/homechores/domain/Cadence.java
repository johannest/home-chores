package com.homechores.domain;

/**
 * How often a chore comes round, as a coarse bucket derived from {@link ChoreTask#getIntervalDays()}.
 *
 * <p>The interval stays the single source of truth — this is only a lens for grouping the board, so
 * a home with a handful of rare chores can narrow it down without editing anything.
 *
 * <p>The boundaries are the geometric midpoints between the canonical values the editor writes
 * (1, 7, 30, 90, 365): √(1·7)=2.6, √(7·30)=14.5, √(30·90)=52.0, √(90·365)=181.2. Each canonical
 * value therefore sits at the centre of its bucket in log space, so the three ways people write
 * "monthly" — 28, 30, 31 — cannot straddle a cut, and no value the presets produce lands within a
 * day of a boundary. 14 falls in {@link #WEEKLY} on purpose: a fortnight is a weekly rhythm, not a
 * monthly one.
 */
public enum Cadence {

    /** No schedule at all — always tappable. */
    ANYTIME,
    DAILY,
    WEEKLY,
    MONTHLY,
    /** Every few months: the 90-day range, plus everything up to half a year. */
    MULTI_MONTH,
    YEARLY;

    /** The bucket an interval falls into. Never throws; a nonsensical interval reads as ANYTIME. */
    public static Cadence of(int intervalDays) {
        if (intervalDays <= 0) {
            return ANYTIME;
        }
        if (intervalDays <= 2) {
            return DAILY;
        }
        if (intervalDays <= 14) {
            return WEEKLY;
        }
        if (intervalDays <= 51) {
            return MONTHLY;
        }
        if (intervalDays <= 180) {
            return MULTI_MONTH;
        }
        return YEARLY;
    }
}
