package com.homechores.domain;

import java.time.Month;

/**
 * A time of year a chore can be limited to — shovelling snow in winter, raking leaves in autumn.
 *
 * <p>Months are the <em>northern-hemisphere meteorological</em> seasons. This app has no per-home
 * hemisphere setting and its three languages (en/fi/sv) are all northern, so the mapping is fixed
 * rather than configurable. A southern-hemisphere home would need a home-level setting; until
 * someone asks, a fixed mapping is honest and one less thing to get wrong.
 */
public enum Season {

    SPRING,
    SUMMER,
    AUTUMN,
    WINTER;

    /** The season a month belongs to. */
    public static Season of(Month month) {
        return switch (month) {
            case MARCH, APRIL, MAY -> SPRING;
            case JUNE, JULY, AUGUST -> SUMMER;
            case SEPTEMBER, OCTOBER, NOVEMBER -> AUTUMN;
            case DECEMBER, JANUARY, FEBRUARY -> WINTER;
        };
    }
}
