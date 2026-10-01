package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.service.StatsService.CountBar;
import com.homechores.service.StatsService.PeriodBucket;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The horizontal bar chart ranks its rows and hides the empty ones. */
class ChartsTest {

    private static final List<CountBar> BOARD_ORDER = List.of(
            new CountBar("🧹 Vacuum", 2),
            new CountBar("🍽️ Dishes", 7),
            new CountBar("🐕 Dog", 0),
            new CountBar("🗑️ Trash", 7),
            new CountBar("🛏️ Beds", 4));

    @Test
    void ranksLongestFirstAndDropsZeroBars() {
        assertEquals(List.of("🍽️ Dishes", "🗑️ Trash", "🛏️ Beds", "🧹 Vacuum"),
                labels(Charts.horizontalBars(BOARD_ORDER)),
                "longest first; ties keep board order; the zero bar is gone");
    }

    @Test
    void keepsZeroBarsWhenAskedTo() {
        assertEquals(List.of("🍽️ Dishes", "🗑️ Trash", "🛏️ Beds", "🧹 Vacuum", "🐕 Dog"),
                labels(Charts.horizontalBars(BOARD_ORDER, true)));
    }

    @Test
    void allZeroIsNoData() {
        Component chart = Charts.horizontalBars(List.of(new CountBar("🐕 Dog", 0)));
        assertTrue(labels(chart).isEmpty(), "no bar rows for an all-zero chart");
    }

    /**
     * The bar scales as a fraction of its own plot box, not as a percentage of the column. As
     * a column percentage, every bar above ~65% collided with the count and the caption and
     * flexbox shrank it to the same height: 65 and 98 drew alike while 52 stayed shorter.
     */
    @Test
    void trendBarsScaleAgainstTheLargestBucket() {
        LocalDate d = LocalDate.of(2026, 9, 7);
        Component trend = Charts.periodTrend(List.of(
                new PeriodBucket(d, "7.9", "7.9.–13.9.", 65),
                new PeriodBucket(d.plusWeeks(1), "14.9", "14.9.–20.9.", 98),
                new PeriodBucket(d.plusWeeks(2), "21.9", "21.9.–27.9.", 52),
                new PeriodBucket(d.plusWeeks(3), "28.9", "28.9.–4.10.", 0)));
        assertEquals(List.of("0.6633", "1.0000", "0.5306", "0.0000"), pcts(trend));
    }

    @Test
    void emptyTrendDrawsNoBars() {
        Component trend = Charts.periodTrend(List.of(
                new PeriodBucket(LocalDate.of(2026, 9, 7), "7.9", "7.9.–13.9.", 0)));
        assertEquals(List.of("0"), pcts(trend), "no division by a zero maximum");
    }

    /** The bar fraction of each column, in order. */
    private static List<String> pcts(Component trend) {
        return trend.getChildren()
                .filter(col -> col.hasClassName("trend-col"))
                .map(col -> col.getChildren()
                        .filter(c -> c.hasClassName("trend-plot")).findFirst().orElseThrow())
                .map(plot -> plot.getChildren()
                        .filter(c -> c.hasClassName("trend-bar")).findFirst().orElseThrow())
                .map(bar -> bar.getStyle().get("--trend-pct"))
                .toList();
    }

    private static List<String> labels(Component chart) {
        return chart.getChildren()
                .filter(row -> row.hasClassName("bar-row"))
                .map(row -> ((Span) row.getChildren().findFirst().orElseThrow()).getText())
                .toList();
    }
}
