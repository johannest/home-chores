package com.homechores.ui;

import com.homechores.domain.Member;
import com.homechores.service.ChoreService;
import com.homechores.service.StatsService;
import com.homechores.service.StatsService.ChoreFeedback;
import com.homechores.service.StatsService.CountBar;
import com.homechores.service.StatsService.HomeStats;
import com.homechores.service.StatsService.MemberDaily;
import com.homechores.service.StatsService.MyStats;
import com.homechores.service.StatsService.Period;
import com.homechores.service.StatsService.PeriodCounts;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

/** Statistics: personal charts for everyone, plus home-wide charts for admins. */
class StatsPanel extends VerticalLayout {

    private final StatsService stats;
    private final ChoreService service;
    private final String homeCode;
    private final Long memberId;

    private final Div content = new Div();
    private boolean showHome = false;

    /**
     * The selected lens. A field, exactly like {@code ChoresPanel.filter}: it survives every
     * {@code HomeState} rebuild because {@code HomeView} holds this panel instance, and resets on
     * navigation. Changing it re-renders this panel only and <strong>never bumps HomeState</strong>
     * — which period one member is looking at is not the family's business.
     */
    private Period period = Period.WEEK;

    StatsPanel(StatsService stats, ChoreService service, String homeCode, Long memberId) {
        this.stats = stats;
        this.service = service;
        this.homeCode = homeCode;
        this.memberId = memberId;
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        content.setWidthFull();
        add(content);
    }

    void refresh() {
        content.removeAll();
        boolean admin = service.findMember(memberId).map(Member::isAdmin).orElse(false);

        if (admin) {
            Tab mine = new Tab(T.tr("stats.myTab"));
            Tab home = new Tab(T.tr("stats.homeTab"));
            Tabs tabs = new Tabs(mine, home);
            tabs.setWidthFull();
            tabs.setSelectedTab(showHome ? home : mine);
            tabs.addSelectedChangeListener(e -> {
                showHome = e.getSelectedTab() == home;
                renderBody(true);
            });
            content.add(tabs);
        } else {
            showHome = false;
        }
        renderBody(admin);
    }

    private final Div body = new Div();

    private void renderBody(boolean admin) {
        body.removeAll();
        if (body.getParent().isEmpty()) {
            body.setWidthFull();
            content.add(body);
        }
        if (showHome && admin) {
            renderHome();
        } else {
            renderMine();
        }
    }

    private void renderMine() {
        MyStats s = stats.myStats(memberId, homeCode, period, UI.getCurrent().getLocale());

        H2 total = new H2(T.tr("stats.myTotal", s.totalApproved()));
        total.getStyle().set("margin", "0 0 var(--lumo-space-s)");
        body.add(total);

        // The separator lives here, not in the properties file: Properties strips leading
        // whitespace from values, so a padded "  ✅ …" ran straight into the number.
        Paragraph today = new Paragraph(T.tr("stats.today", s.doneToday(), s.target())
                + (s.doneToday() >= s.target() ? "  " + T.tr("stats.today.reached") : ""));
        today.getStyle().set("font-weight", "600").set("margin-top", "0");
        body.add(today);

        body.add(headlineRow(s.counts()));
        body.add(periodChips());

        String lens = T.tr("stats.period." + period.name().toLowerCase(Locale.ROOT));
        body.add(Charts.card(T.tr("stats.byChore.period", lens),
                s.inPeriod() == 0 ? emptyPeriod()
                        : Charts.horizontalBars(withOtherHelp(s.byChore(), s.otherHelp()))));
        body.add(Charts.card(T.tr("stats.feelings"), Charts.feedbackBar(s.feedback())));
        body.add(Charts.card(T.tr("stats.last7"), Charts.dayTrend(s.last7())));
        body.add(Charts.card(T.tr("stats.trendWeeks"), Charts.periodTrend(s.byWeek())));
        body.add(Charts.card(T.tr("stats.trendMonths"), Charts.periodTrend(s.byMonth())));
    }

    /**
     * Today / this week / this month / all time, side by side and always all-time facts.
     *
     * <p>Deliberately not filtered by the selected lens: the question the power users asked —
     * "what did I do today, this week, this month" — is a comparison, and a row that only ever
     * showed the selected period would answer one quarter of it per tap.
     */
    private Div headlineRow(PeriodCounts counts) {
        Div row = new Div();
        row.addClassName("stat-tiles");
        for (Period p : Period.values()) {
            Div tile = new Div();
            tile.addClassName("stat-tile");
            if (p == period) {
                tile.addClassName("selected");
            }
            Span value = new Span(String.valueOf(counts.of(p)));
            value.addClassName("stat-value");
            Span label = new Span(T.tr("stats.headline." + p.name().toLowerCase(Locale.ROOT)));
            label.addClassName("stat-label");
            tile.add(value, label);
            row.add(tile);
        }
        return row;
    }

    /** The lens picker. Reuses the board's chip recipe so the two rows read the same way. */
    private Div periodChips() {
        Div bar = new Div();
        bar.addClassName("filter-bar");
        for (Period p : Period.values()) {
            Div chip = new Div();
            chip.addClassName("filter-chip");
            if (p == period) {
                chip.addClassName("selected");
            }
            chip.setText(T.tr("stats.period." + p.name().toLowerCase(Locale.ROOT)));
            chip.addClickListener(e -> {
                period = p;
                // This panel only. A lens is one member's view preference, so it must not bump
                // HomeState and redraw the whole family's screens.
                renderBody(showHome);
            });
            bar.add(chip);
        }
        return bar;
    }

    private static Paragraph emptyPeriod() {
        Paragraph p = new Paragraph(T.tr("stats.nothingInPeriod"));
        p.addClassName("feedback-hint");
        return p;
    }

    /**
     * Appends accepted other help as one more bar. The service counts it but can't name it —
     * "Other help" is UI wording, unlike a chore name, which is the family's own data.
     */
    private static List<CountBar> withOtherHelp(List<CountBar> bars, long otherHelp) {
        if (otherHelp <= 0) {
            return bars;
        }
        List<CountBar> all = new ArrayList<>(bars);
        all.add(new CountBar("🙋 " + T.tr("board.otherHelp"), otherHelp));
        return all;
    }

    private void renderHome() {
        HomeStats s = stats.homeStats(homeCode, period, UI.getCurrent().getLocale());

        if (s.pending() > 0) {
            Paragraph p = new Paragraph(T.tr("stats.pending", s.pending()));
            p.getStyle().set("font-weight", "600");
            body.add(p);
        }

        body.add(headlineRow(s.counts()));
        body.add(periodChips());

        String lens = T.tr("stats.period." + period.name().toLowerCase(Locale.ROOT));
        body.add(Charts.card(T.tr("stats.perMember.period", lens),
                Charts.horizontalBars(s.perMember())));
        body.add(Charts.card(T.tr("stats.popularity.period", lens), Charts.horizontalBars(
                withOtherHelp(s.chorePopularity(), s.otherHelp()))));

        Div fbBody = new Div();
        if (s.feedbackByChore().isEmpty()) {
            Paragraph none = new Paragraph(T.tr("charts.noFeedback"));
            none.addClassName("feedback-hint");
            fbBody.add(none);
        } else {
            for (ChoreFeedback cf : s.feedbackByChore()) {
                Div label = new Div();
                label.setText(cf.task().getEmoji() + " " + cf.task().getName());
                label.getStyle().set("font-weight", "600").set("margin-top", "var(--lumo-space-s)");
                fbBody.add(label);
                fbBody.add(Charts.feedbackBar(cf.split()));
            }
        }
        body.add(Charts.card(T.tr("stats.feedbackByChore"), fbBody));

        body.add(Charts.card(T.tr("stats.trend14"), Charts.dayTrend(s.trend14())));
        body.add(Charts.card(T.tr("stats.trendWeeks"), Charts.periodTrend(s.byWeek())));
        body.add(Charts.card(T.tr("stats.trendMonths"), Charts.periodTrend(s.byMonth())));

        List<CountBar> todayBars = new ArrayList<>();
        for (MemberDaily md : s.adherence()) {
            todayBars.add(new CountBar(
                    md.member().getName() + " (" + md.doneToday() + "/" + md.target() + ")",
                    md.doneToday()));
        }
        body.add(Charts.card(T.tr("stats.todayGoals"), Charts.horizontalBars(todayBars)));
    }
}
