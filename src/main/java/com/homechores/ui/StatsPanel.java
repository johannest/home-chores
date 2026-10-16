package com.homechores.ui;

import com.homechores.domain.Member;
import com.homechores.service.ChoreService;
import com.homechores.service.StatsService;
import com.homechores.service.StatsService.ChoreFeedback;
import com.homechores.service.StatsService.CountBar;
import com.homechores.service.StatsService.DiamondStats;
import com.homechores.service.StatsService.HomeStats;
import com.homechores.service.StatsService.MemberChores;
import com.homechores.service.StatsService.MemberDaily;
import com.homechores.service.StatsService.MyStats;
import com.homechores.service.StatsService.Period;
import com.homechores.service.StatsService.PeriodCounts;
import com.homechores.service.StatsService.Redemption;
import com.homechores.service.StatsService.Redemptions;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;

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

    /** Which members' folds the admin has opened on the Home tab, by member id. A field for
     *  the same reason as the lens: the panel is rebuilt on every change in the home, and a fold
     *  the admin just opened must not snap shut because somebody tapped a chore. */
    private final Set<Long> memberOpen = new HashSet<>();

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

        String lens = T.tr("stats.period." + period.name().toLowerCase(Locale.ROOT));
        body.add(Charts.card(T.tr("stats.byChore.period", lens),
                s.inPeriod() == 0 ? emptyPeriod()
                        : Charts.horizontalBars(withOtherHelp(s.byChore(), s.otherHelp()))));
        if (s.diamonds().earned().allTime() > 0) {
            // Only once the member has ever earned one: a home that never uses credits would
            // otherwise see a card of four zeros over seven empty columns.
            body.add(Charts.card(T.tr("stats.diamonds"), diamondsBody(s.diamonds())));
        }
        if (s.diamonds().redeemed() > 0) {
            // Likewise only once something was redeemed. The card exists so that a member who
            // earns a lot and cashes it in is not read, from a balance near zero, as someone who
            // never earns anything: earned, redeemed and what is left sit side by side, and the
            // rewards below say what the diamonds became.
            body.add(Charts.card(T.tr("stats.redeemed"), redeemedBody(s.diamonds())));
        }
        body.add(Charts.card(T.tr("stats.feelings"), Charts.feedbackBar(s.feedback())));
        body.add(Charts.card(T.tr("stats.last7"), Charts.dayTrend(s.last7())));
        body.add(Charts.card(T.tr("stats.trendWeeks"), Charts.periodTrend(s.byWeek())));
        body.add(Charts.card(T.tr("stats.trendMonths"), Charts.periodTrend(s.byMonth())));
    }

    /**
     * Today / this week / this month / all time, side by side and always all-time facts — and
     * also the lens picker: tapping a tile selects that period for the charts below.
     *
     * <p>The counts are deliberately not filtered by the selected lens: the question the power
     * users asked — "what did I do today, this week, this month" — is a comparison, and a row
     * that only ever showed the selected period would answer one quarter of it per tap. The
     * tiles used to sit above a separate chip row with the very same four labels; users read
     * that as redundant and tried to tap the tiles, so now the tiles are the picker.
     */
    private Div headlineRow(PeriodCounts counts) {
        Div row = new Div();
        row.addClassName("stat-tiles");
        row.getElement().setAttribute("role", "group");
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
            // A plain Div, so the button semantics are spelled out by hand: focusable, announced
            // as a toggle, and operable from the keyboard like the button it looks like.
            tile.getElement().setAttribute("role", "button");
            tile.getElement().setAttribute("tabindex", "0");
            tile.getElement().setAttribute("aria-pressed", String.valueOf(p == period));
            tile.addClickListener(e -> selectPeriod(p));
            tile.getElement().addEventListener("keydown", e -> selectPeriod(p))
                    .setFilter("event.key === 'Enter' || event.key === ' '")
                    .preventDefault();
            row.add(tile);
        }
        return row;
    }

    /**
     * Re-renders this panel only. A lens is one member's view preference, so it must not bump
     * HomeState and redraw the whole family's screens.
     */
    private void selectPeriod(Period p) {
        if (p == period) {
            return;
        }
        period = p;
        renderBody(showHome);
    }

    /**
     * 💎 earned today / this week / this month / all time, then the last seven days.
     *
     * <p>The same four tiles as {@link #headlineRow}, deliberately <em>not</em> the lens picker:
     * these are plain numbers, so no button role, no focus stop and no selected state — a second
     * row of tappable tiles with the same labels would be the redundancy the chip row was removed
     * for. Earned only; the balance is on the leaderboard.
     */
    private static Div diamondsBody(DiamondStats d) {
        Div row = new Div();
        row.addClassNames("stat-tiles", "readonly");
        for (Period p : Period.values()) {
            row.add(readonlyTile(d.earned().of(p),
                    T.tr("stats.headline." + p.name().toLowerCase(Locale.ROOT))));
        }
        Div box = new Div(row);
        box.add(Charts.dayTrend(d.last7()));
        return box;
    }

    /**
     * Earned · redeemed · balance as three read-only tiles, then the latest redemptions with
     * their reward notes. All-time, never narrowed by the lens, like the earned card above it.
     */
    private static Div redeemedBody(DiamondStats d) {
        Div row = new Div();
        row.addClassNames("stat-tiles", "readonly", "three");
        row.add(readonlyTile(d.earned().allTime(), T.tr("stats.redeemed.earned")),
                readonlyTile(d.redeemed(), T.tr("stats.redeemed.spent")),
                readonlyTile(d.balance(), T.tr("stats.redeemed.balance")));
        Div box = new Div(row);
        box.add(redemptionList(d.redemptions(), false));
        return box;
    }

    /** A number over a caption in the headline tiles' shape — a plain Div with no button role,
     *  no focus stop and no selected state, because it is a fact and not a picker. */
    private static Div readonlyTile(long value, String caption) {
        Div tile = new Div();
        tile.addClassName("stat-tile");
        Span number = new Span(String.valueOf(value));
        number.addClassName("stat-value");
        Span label = new Span(caption);
        label.addClassName("stat-label");
        tile.add(number, label);
        return tile;
    }

    /**
     * The redemptions newest first: day, reward note (or "no note"), 💎 amount — and the member's
     * name in front of the note on the admin's home-wide list, where {@code named} is true. A
     * real list element, so a screen reader announces how many rewards there are. When the
     * service's cap hid older rows, the last line says how many.
     */
    private static Div redemptionList(Redemptions redemptions, boolean named) {
        UnorderedList list = new UnorderedList();
        list.addClassName("redemptions");
        LocalDate today = LocalDate.now();
        for (Redemption r : redemptions.recent()) {
            ListItem item = new ListItem();
            item.addClassName("redemption-row");
            Span on = new Span(dayLabel(r.on(), today));
            on.addClassName("redemption-date");
            String reward = r.reward() == null ? T.tr("stats.redeemed.noNote") : r.reward();
            Span what = new Span(named ? r.member().getName() + " · " + reward : reward);
            what.addClassName("redemption-reward");
            Span amount = new Span("💎 " + r.amount());
            amount.addClassName("redemption-amount");
            item.add(on, what, amount);
            list.add(item);
        }
        Div box = new Div(list);
        if (redemptions.earlier() > 0) {
            Paragraph more = new Paragraph(T.tr("stats.redeemed.earlier", redemptions.earlier()));
            more.addClassName("feedback-hint");
            box.add(more);
        }
        return box;
    }

    /** "12.10." for this year, "12.10.2025" for an earlier one — the board's own day format,
     *  with the year only where it carries information. */
    private static String dayLabel(LocalDate on, LocalDate today) {
        return DateTimeFormatter.ofPattern(on.getYear() == today.getYear() ? "d.M." : "d.M.yyyy")
                .format(on);
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

    /**
     * Every member's own "my chores" chart, one fold each, for the admin. Folded by default:
     * a family of five would otherwise stack five full bar charts on a phone before the trends.
     * The summary carries the name and the total, so the folded card is already the ranking.
     */
    private Div perMemberChores(List<MemberChores> rows) {
        Div holder = new Div();
        if (rows.isEmpty()) {
            holder.add(emptyPeriod());
            return holder;
        }
        for (MemberChores mc : rows) {
            Long id = mc.member().getId();
            Details fold = new Details(mc.member().getName() + " · " + mc.total(),
                    Charts.horizontalBars(withOtherHelp(mc.byChore(), mc.otherHelp())));
            fold.addClassName("member-chores");
            // setOpened before the listener, or the restore counts as the admin's own choice.
            fold.setOpened(memberOpen.contains(id));
            fold.addOpenedChangeListener(e -> {
                if (e.isFromClient()) {
                    if (e.isOpened()) {
                        memberOpen.add(id);
                    } else {
                        memberOpen.remove(id);
                    }
                }
            });
            holder.add(fold);
        }
        return holder;
    }

    private void renderHome() {
        HomeStats s = stats.homeStats(homeCode, period, UI.getCurrent().getLocale());

        if (s.pending() > 0) {
            Paragraph p = new Paragraph(T.tr("stats.pending", s.pending()));
            p.getStyle().set("font-weight", "600");
            body.add(p);
        }

        body.add(headlineRow(s.counts()));

        String lens = T.tr("stats.period." + period.name().toLowerCase(Locale.ROOT));
        body.add(Charts.card(T.tr("stats.perMember.period", lens),
                Charts.horizontalBars(s.perMember())));
        body.add(Charts.card(T.tr("stats.perMemberChores.period", lens),
                perMemberChores(s.perMemberChores())));
        if (!s.redemptions().isEmpty()) {
            // Who redeemed what, for the admin — the same card the member sees, with names, and
            // hidden the same way for a home that never redeems.
            body.add(Charts.card(T.tr("stats.redeemed.home"),
                    redemptionList(s.redemptions(), true)));
        }
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
        body.add(Charts.card(T.tr("stats.todayGoals"), Charts.horizontalBars(todayBars, true)));
    }
}
