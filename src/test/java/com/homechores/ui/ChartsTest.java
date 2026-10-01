package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.service.StatsService.CountBar;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
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

    private static List<String> labels(Component chart) {
        return chart.getChildren()
                .filter(row -> row.hasClassName("bar-row"))
                .map(row -> ((Span) row.getChildren().findFirst().orElseThrow()).getText())
                .toList();
    }
}
