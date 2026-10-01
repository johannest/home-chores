package com.homechores.ui;

import com.homechores.service.StatsService.CountBar;
import com.homechores.service.StatsService.DayCount;
import com.homechores.service.StatsService.FeedbackSplit;
import com.homechores.service.StatsService.PeriodBucket;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Tiny dependency-free charts built from styled divs (no licensed components). */
final class Charts {

    private Charts() {
    }

    static Div card(String title, Component body) {
        Div card = new Div();
        card.addClassName("chart-card");
        Div t = new Div();
        t.setText(title);
        t.addClassName("chart-title");
        card.add(t, body);
        return card;
    }

    /**
     * Horizontal bars ranked longest first, each scaled to the largest value. Zero bars are
     * left out: a ranking is read top-down for who or what did the most, and a tail of empty
     * tracks only pushes the trend cards below it further down a phone screen.
     */
    static Component horizontalBars(List<CountBar> bars) {
        return horizontalBars(bars, false);
    }

    /**
     * Horizontal bars ranked longest first. With {@code keepZeros} the empty bars stay, for
     * cards where a zero is the message — "today's goals" lists every member, and a member
     * on 0/3 belongs on it more than anyone.
     */
    static Component horizontalBars(List<CountBar> bars, boolean keepZeros) {
        Div box = new Div();
        // Stable sort: ties keep the service's board order, so equal bars don't shuffle
        // between renders.
        List<CountBar> ranked = bars.stream()
                .filter(b -> keepZeros || b.value() > 0)
                .sorted(Comparator.comparingLong(CountBar::value).reversed())
                .toList();
        long max = ranked.stream().mapToLong(CountBar::value).max().orElse(0);
        if (ranked.isEmpty() || max == 0) {
            return empty(T.tr("charts.noData"));
        }
        for (CountBar b : ranked) {
            Div row = new Div();
            row.addClassName("bar-row");

            Span label = new Span(b.label());
            label.addClassName("bar-label");

            Div track = new Div();
            track.addClassName("bar-track");
            Div fill = new Div();
            fill.addClassName("bar-fill");
            fill.getStyle().set("width", (max == 0 ? 0 : (b.value() * 100 / max)) + "%");
            track.add(fill);

            Span value = new Span(String.valueOf(b.value()));
            value.addClassName("bar-value");

            row.add(label, track, value);
            box.add(row);
        }
        return box;
    }

    /** A single hate/ok/love segmented bar with a legend. */
    static Component feedbackBar(FeedbackSplit s) {
        if (s.total() == 0) {
            return empty(T.tr("charts.noFeedback"));
        }
        Div box = new Div();
        Div track = new Div();
        track.addClassName("seg-track");
        track.add(seg("seg-hate", s.hate(), s.total()));
        track.add(seg("seg-ok", s.ok(), s.total()));
        track.add(seg("seg-love", s.love(), s.total()));

        Paragraph legend = new Paragraph(
                "😖 " + s.hate() + "   🙂 " + s.ok() + "   😍 " + s.love());
        legend.addClassName("feedback-hint");
        box.add(track, legend);
        return box;
    }

    private static Div seg(String cls, long value, long total) {
        Div d = new Div();
        d.addClassName(cls);
        d.getStyle().set("width", (total == 0 ? 0 : (value * 100 / total)) + "%");
        return d;
    }

    /** Vertical columns for a day-by-day trend. */
    static Component dayTrend(List<DayCount> days) {
        long max = days.stream().mapToLong(DayCount::value).max().orElse(0);
        Div trend = new Div();
        trend.addClassName("trend");
        for (DayCount d : days) {
            // The weekday initial was hardcoded to English, which put "M T W T F S S" over a
            // Finnish and a Swedish board too. Same for the tooltip, which was assembled with a
            // bare " on " between the count and the date.
            trend.add(column(d.value(), max,
                    d.date().getDayOfWeek().getDisplayName(TextStyle.NARROW, locale()),
                    T.tr("charts.tooltip", d.value(), d.date())));
        }
        return trend;
    }

    /**
     * Vertical columns for a week- or month-grained trend. Same recipe as {@link #dayTrend}; the
     * labels arrive already formatted, because the service is where the locale is known.
     *
     * <p>Twelve columns is the cap ({@code StatsService.TREND_WEEKS}), which is what a ~360px
     * phone column fits: the page must never scroll sideways, so a longer trend has to thin its
     * labels rather than widen itself.
     */
    static Component periodTrend(List<PeriodBucket> buckets) {
        long max = buckets.stream().mapToLong(PeriodBucket::value).max().orElse(0);
        Div trend = new Div();
        trend.addClassName("trend");
        trend.addClassName("trend-wide");
        for (PeriodBucket b : buckets) {
            trend.add(column(b.value(), max, b.label(),
                    T.tr("charts.tooltip", b.value(), b.caption())));
        }
        return trend;
    }

    private static Div column(long value, long max, String label, String tooltip) {
        Div col = new Div();
        col.addClassName("trend-col");

        // The bar and its count live in a plot box of their own, so the bar's percentage is
        // measured against the room that is actually left for it. Measured against the whole
        // column (as it was), a bar above ~65% collided with the count and the caption and
        // flexbox shrank it to fit, so 65 and 98 drew the same height while 52 still fell short.
        Div plot = new Div();
        plot.addClassName("trend-plot");

        Div bar = new Div();
        bar.addClassName("trend-bar");
        bar.getStyle().set("--trend-pct",
                max == 0 ? "0" : String.format(Locale.ROOT, "%.4f", (double) value / max));
        bar.getElement().setAttribute("title", tooltip);

        Span caption = new Span(label);
        caption.addClassName("trend-day");

        // The tooltip is hover-only, and a phone has no hover: without a printed count the
        // "past 7 days" card shows bars and nothing else. Zero stays blank so the quiet days do
        // not read as a row of noughts.
        if (value > 0) {
            Span count = new Span(String.valueOf(value));
            count.addClassName("trend-count");
            plot.add(count);
        }
        plot.add(bar);
        col.add(plot, caption);
        return col;
    }

    /** The UI's locale, guarded like {@link T#tr} — there is no UI in a unit test. */
    private static Locale locale() {
        UI ui = UI.getCurrent();
        return ui == null ? Locale.ENGLISH : ui.getLocale();
    }

    private static Component empty(String text) {
        Paragraph p = new Paragraph(text);
        p.addClassName("feedback-hint");
        return p;
    }
}
