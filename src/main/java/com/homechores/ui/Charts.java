package com.homechores.ui;

import com.homechores.service.StatsService.CountBar;
import com.homechores.service.StatsService.DayCount;
import com.homechores.service.StatsService.FeedbackSplit;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import java.time.format.TextStyle;
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

    /** Horizontal bars, each scaled to the largest value. */
    static Component horizontalBars(List<CountBar> bars) {
        Div box = new Div();
        long max = bars.stream().mapToLong(CountBar::value).max().orElse(0);
        if (bars.isEmpty() || max == 0) {
            return empty(T.tr("charts.noData"));
        }
        for (CountBar b : bars) {
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
            Div col = new Div();
            col.addClassName("trend-col");

            Div bar = new Div();
            bar.addClassName("trend-bar");
            int pct = max == 0 ? 0 : (int) (d.value() * 100 / max);
            bar.getStyle().set("height", pct + "%");
            bar.getElement().setAttribute("title", d.value() + " on " + d.date());

            Span day = new Span(d.date().getDayOfWeek()
                    .getDisplayName(TextStyle.NARROW, Locale.ENGLISH));
            day.addClassName("trend-day");

            col.add(bar, day);
            trend.add(col);
        }
        return trend;
    }

    private static Component empty(String text) {
        Paragraph p = new Paragraph(text);
        p.addClassName("feedback-hint");
        return p;
    }
}
