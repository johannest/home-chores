package com.homechores.ui;

import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.service.BackupService;
import com.homechores.service.ChoreService;
import com.homechores.service.CreditService;
import com.homechores.service.HomeState;
import com.homechores.service.StatsService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.signals.Signal;
import java.util.Optional;

/** The signed-in home experience: header + tabbed Chores / Stats / Admin panels. */
@Route("home")
@PageTitle("FlashChores")
@JsModule("./confetti.js")
public class HomeView extends VerticalLayout implements BeforeEnterObserver {

    private enum PanelTab { CHORES, STATS, ADMIN }

    private final ChoreService service;
    private final StatsService statsService;
    private final BackupService backupService;
    private final CreditService creditService;
    private final HomeState homeState;

    private String homeCode;
    private Long memberId;

    private ChoresPanel choresPanel;
    private StatsPanel statsPanel;
    private AdminPanel adminPanel;

    private final Div content = new Div();
    private PanelTab selected = PanelTab.CHORES;

    public HomeView(ChoreService service, StatsService statsService, BackupService backupService,
                    CreditService creditService, HomeState homeState) {
        this.service = service;
        this.statsService = statsService;
        this.backupService = backupService;
        this.creditService = creditService;
        this.homeState = homeState;
        setPadding(false);
        setSpacing(false);
        setSizeFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!SessionContext.isSignedIn()) {
            event.forwardTo(LandingView.class);
            return;
        }
        this.homeCode = SessionContext.homeCode();
        this.memberId = SessionContext.memberId();

        Optional<Home> home = service.findHome(homeCode);
        Optional<Member> me = service.findMember(memberId);
        if (home.isEmpty() || me.isEmpty()) {
            SessionContext.signOut();
            event.forwardTo(LandingView.class);
            return;
        }
        choresPanel = new ChoresPanel(service, creditService, homeCode, memberId);
        statsPanel = new StatsPanel(statsService, service, homeCode, memberId);
        adminPanel = new AdminPanel(service, creditService, backupService, homeCode, memberId);
        // Initial render happens from the Signal.effect registered in onAttach.
    }

    private boolean isAdmin() {
        return service.findMember(memberId).map(Member::isAdmin).orElse(false);
    }

    // ---- Chrome (header + tabs) --------------------------------------------

    private void buildChrome() {
        removeAll();
        boolean admin = isAdmin();
        Home home = service.findHome(homeCode).orElseThrow();

        Div page = new Div();
        page.addClassName("page-pad");
        page.add(buildHeader(home, admin));
        page.add(buildTabs(admin));
        content.setWidthFull();
        page.add(content);
        add(page);

        showSelected();
    }

    private Component buildHeader(Home home, boolean admin) {
        H1 name = new H1(home.getName());

        Span code = new Span(home.getCode());
        code.addClassName("code-chip");
        Button copyLink = new Button(T.tr("home.copyLink"), VaadinIcon.LINK.create());
        copyLink.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST);
        copyLink.addClickListener(e -> copyJoinLink(home));
        Button share = new Button(T.tr("home.share"), VaadinIcon.SHARE.create());
        share.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST);
        share.addClickListener(e -> shareJoinLink(home));
        HorizontalLayout codeRow = new HorizontalLayout(code, copyLink, share);
        codeRow.setAlignItems(FlexComponent.Alignment.CENTER);
        codeRow.getStyle().set("flex-wrap", "wrap");
        Div left = new Div(name, codeRow);

        HorizontalLayout right = new HorizontalLayout();
        right.setAlignItems(FlexComponent.Alignment.CENTER);
        right.add(new LanguageSwitcher());
        if (admin) {
            Span badge = new Span(T.tr("home.adminBadge"));
            badge.addClassName("admin-badge");
            right.add(badge);
        } else {
            Button claim = new Button(T.tr("home.claimAdmin"), VaadinIcon.KEY.create(),
                    e -> claimAdminDialog());
            claim.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST);
            right.add(claim);
        }
        Button leave = new Button(T.tr("home.leave"), VaadinIcon.SIGN_OUT.create());
        leave.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        leave.getStyle().set("color", "#fff");
        leave.addClickListener(e -> {
            SessionContext.signOut();
            getUI().ifPresent(ui -> ui.navigate(LandingView.class));
        });
        right.add(leave);

        HorizontalLayout header = new HorizontalLayout(left, right);
        header.addClassName("home-header");
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.getStyle().set("margin-bottom", "var(--lumo-space-m)");
        return header;
    }

    private Tabs buildTabs(boolean admin) {
        Tab choresTab = new Tab(VaadinIcon.CHECK_SQUARE_O.create(), new Span(T.tr("home.tab.chores")));
        Tab statsTab = new Tab(VaadinIcon.CHART.create(), new Span(T.tr("home.tab.stats")));
        Tabs tabs = new Tabs(choresTab, statsTab);

        Tab adminTab = null;
        if (admin) {
            Span label = new Span(T.tr("home.tab.admin"));
            long pending = service.pendingCount(homeCode);
            if (pending > 0) {
                Span badge = new Span(String.valueOf(pending));
                badge.addClassName("nav-badge");
                label.add(badge);
            }
            adminTab = new Tab(VaadinIcon.COG.create(), label);
            tabs.add(adminTab);
        }
        if (selected == PanelTab.STATS) {
            tabs.setSelectedTab(statsTab);
        } else if (selected == PanelTab.ADMIN && adminTab != null) {
            tabs.setSelectedTab(adminTab);
        } else {
            tabs.setSelectedTab(choresTab);
            selected = PanelTab.CHORES;
        }
        final Tab finalAdmin = adminTab;
        tabs.addSelectedChangeListener(e -> {
            Tab t = e.getSelectedTab();
            if (t == statsTab) {
                selected = PanelTab.STATS;
            } else if (t == finalAdmin) {
                selected = PanelTab.ADMIN;
            } else {
                selected = PanelTab.CHORES;
            }
            showSelected();
        });
        tabs.getStyle().set("margin-bottom", "var(--lumo-space-m)");
        return tabs;
    }

    private void showSelected() {
        content.removeAll();
        switch (selected) {
            case STATS -> {
                content.add(statsPanel);
                statsPanel.refresh();
            }
            case ADMIN -> {
                content.add(adminPanel);
                adminPanel.refresh();
            }
            default -> {
                content.add(choresPanel);
                choresPanel.refresh();
            }
        }
    }

    // ---- Actions ------------------------------------------------------------

    private void claimAdminDialog() {
        Dialog d = new Dialog();
        d.setHeaderTitle(T.tr("claim.title"));
        TextField pin = new TextField(T.tr("claim.pin"));
        pin.setMaxLength(4);
        pin.focus();
        Button ok = new Button(T.tr("claim.unlock"), e -> {
            if (service.claimAdmin(memberId, pin.getValue())) {
                d.close();
                Notification n = Notification.show(T.tr("claim.success"),
                        2500, Notification.Position.TOP_CENTER);
                n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                selected = PanelTab.ADMIN;
                buildChrome();
            } else {
                pin.setInvalid(true);
                pin.setErrorMessage(T.tr("claim.wrong"));
            }
        });
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.add(pin);
        d.getFooter().add(new Button("Cancel", e -> d.close()), ok);
        d.open();
    }

    /** Copies a ready-to-open join link (origin + ?join=CODE) to the clipboard. */
    private void copyJoinLink(Home home) {
        UI.getCurrent().getPage().executeJs(
                "const url=location.origin+'/?join='+$0;"
                        + "if(navigator.clipboard){navigator.clipboard.writeText(url);}",
                home.getCode());
        Notification.show(T.tr("home.link.copied"), 2500, Notification.Position.TOP_CENTER);
    }

    /** Opens the native share sheet with the join link (falls back to clipboard). */
    private void shareJoinLink(Home home) {
        String text = T.tr("home.share.text", home.getName(), home.getCode());
        // The join link goes into the text as well as the url field — some share targets
        // only take the text, and the link is the whole point of sharing.
        UI.getCurrent().getPage().executeJs(
                "const url=location.origin+'/?join='+$0, msg=$1+'\\n'+url;"
                        + "if(navigator.share){navigator.share({title:'FlashChores',text:msg,url:url}).catch(()=>{});}"
                        + "else if(navigator.clipboard){navigator.clipboard.writeText(msg);}",
                home.getCode(), text);
        Notification.show(T.tr("home.share.ready", home.getCode()),
                2500, Notification.Position.TOP_CENTER);
    }

    // ---- Reactive rendering (Vaadin Signals) --------------------------------

    @Override
    protected void onAttach(AttachEvent event) {
        if (homeCode == null) {
            return; // not signed in — beforeEnter already forwarded away
        }
        // Chore availability windows ("dog out 8-10") are evaluated in the member's local
        // time, so fetch the browser's time zone once and re-render when it arrives.
        event.getUI().getPage().retrieveExtendedClientDetails(details -> {
            String tz = details.getTimeZoneId();
            if (tz != null && !tz.isBlank()) {
                try {
                    SessionContext.setTimeZone(java.time.ZoneId.of(tz));
                    buildChrome();
                } catch (Exception ignored) {
                    // unknown zone id — keep the server default
                }
            }
        });
        // Read this home's shared revision signal inside an effect: the effect re-runs
        // (and rebuilds the UI) whenever the revision changes — from this member or any
        // other member's device, pushed live. This replaces the old broadcaster +
        // UI.access plumbing, and the effect is disposed automatically on detach.
        Signal.effect(this, () -> {
            homeState.revision(homeCode).get(); // track the revision as a dependency
            buildChrome();
        });
    }
}
