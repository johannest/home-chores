package com.homechores.ui;

import com.homechores.domain.Member;
import com.homechores.service.ChoreService;
import com.homechores.service.StatsService;
import com.homechores.service.StatsService.ChoreFeedback;
import com.homechores.service.StatsService.CountBar;
import com.homechores.service.StatsService.HomeStats;
import com.homechores.service.StatsService.MemberDaily;
import com.homechores.service.StatsService.MyStats;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import java.util.ArrayList;
import java.util.List;

/** Statistics: personal charts for everyone, plus home-wide charts for admins. */
class StatsPanel extends VerticalLayout {

    private final StatsService stats;
    private final ChoreService service;
    private final String homeCode;
    private final Long memberId;

    private final Div content = new Div();
    private boolean showHome = false;

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
        MyStats s = stats.myStats(memberId, homeCode);

        H2 total = new H2(T.tr("stats.myTotal", s.totalApproved()));
        total.getStyle().set("margin", "0 0 var(--lumo-space-s)");
        body.add(total);

        // The separator lives here, not in the properties file: Properties strips leading
        // whitespace from values, so a padded "  ✅ …" ran straight into the number.
        Paragraph today = new Paragraph(T.tr("stats.today", s.doneToday(), s.target())
                + (s.doneToday() >= s.target() ? "  " + T.tr("stats.today.reached") : ""));
        today.getStyle().set("font-weight", "600").set("margin-top", "0");
        body.add(today);

        body.add(Charts.card(T.tr("stats.byChore"), Charts.horizontalBars(withOtherHelp(
                s.byChore(), s.otherHelp()))));
        body.add(Charts.card(T.tr("stats.feelings"), Charts.feedbackBar(s.feedback())));
        body.add(Charts.card(T.tr("stats.last7"), Charts.dayTrend(s.last7())));
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
        HomeStats s = stats.homeStats(homeCode);

        if (s.pending() > 0) {
            Paragraph p = new Paragraph(T.tr("stats.pending", s.pending()));
            p.getStyle().set("font-weight", "600");
            body.add(p);
        }

        body.add(Charts.card(T.tr("stats.perMember"), Charts.horizontalBars(s.perMember())));
        body.add(Charts.card(T.tr("stats.popularity"), Charts.horizontalBars(withOtherHelp(
                s.chorePopularity(), s.otherHelp()))));

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

        List<CountBar> todayBars = new ArrayList<>();
        for (MemberDaily md : s.adherence()) {
            todayBars.add(new CountBar(
                    md.member().getName() + " (" + md.doneToday() + "/" + md.target() + ")",
                    md.doneToday()));
        }
        body.add(Charts.card(T.tr("stats.todayGoals"), Charts.horizontalBars(todayBars)));
    }
}
