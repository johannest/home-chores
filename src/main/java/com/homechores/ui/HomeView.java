package com.homechores.ui;

import com.homechores.domain.Home;
import com.homechores.domain.ListItem;
import com.homechores.domain.ListReminder;
import com.homechores.domain.Member;
import com.homechores.service.BackupService;
import com.homechores.service.ChoreService;
import com.homechores.service.CreditService;
import com.homechores.service.HomeState;
import com.homechores.service.ChoreReminderService;
import com.homechores.service.ListItemService;
import com.homechores.service.ListReminderService;
import com.homechores.service.PushReminderService;
import com.homechores.service.StatsService;
import com.homechores.service.WebPushSender;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
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
import java.util.List;
import java.util.Optional;

/** The signed-in home experience: header + tabbed Chores / List / Stats / Admin panels. */
@Route("home")
@PageTitle("FlashChores")
@JsModule("./confetti.js")
@JsModule("./list-swipe.js")
public class HomeView extends VerticalLayout implements BeforeEnterObserver {

    private enum PanelTab { CHORES, LIST, STATS, ADMIN }

    private final ChoreService service;
    private final StatsService statsService;
    private final BackupService backupService;
    private final CreditService creditService;
    private final HomeState homeState;
    private final ListItemService listService;
    private final ListReminderService listReminderService;
    private final PushReminderService reminderService;
    private final ChoreReminderService snoozeService;
    private final WebPushSender pushSender;

    private String homeCode;
    private Long memberId;

    private ChoresPanel choresPanel;
    private ListPanel listPanel;
    private StatsPanel statsPanel;
    private AdminPanel adminPanel;

    private final Div content = new Div();
    private PanelTab selected = PanelTab.CHORES;

    public HomeView(ChoreService service, StatsService statsService, BackupService backupService,
                    CreditService creditService, HomeState homeState, ListItemService listService,
                    ListReminderService listReminderService, PushReminderService reminderService,
                    ChoreReminderService snoozeService, WebPushSender pushSender) {
        this.service = service;
        this.statsService = statsService;
        this.backupService = backupService;
        this.creditService = creditService;
        this.homeState = homeState;
        this.listService = listService;
        this.listReminderService = listReminderService;
        this.reminderService = reminderService;
        this.snoozeService = snoozeService;
        this.pushSender = pushSender;
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
            // Removed member or wiped home — drop the stored identity too, or the device
            // would keep restoring itself into a dead session on every visit.
            DeviceIdentity.forget();
            SessionContext.signOut();
            event.forwardTo(LandingView.class);
            return;
        }
        // Opening the board is the broadest honest signal that this family still uses the
        // home; it feeds retention (see HomeCleanupService) and is throttled to hourly.
        service.touchHome(homeCode);

        choresPanel = new ChoresPanel(service, creditService, snoozeService, reminderService,
                pushSender, homeCode, memberId);
        listPanel = new ListPanel(listService, service, listReminderService, reminderService,
                pushSender, homeCode, memberId);
        statsPanel = new StatsPanel(statsService, service, homeCode, memberId);
        adminPanel = new AdminPanel(service, creditService, backupService, listService, homeCode,
                memberId);
        // Initial render happens from the Signal.effect registered in onAttach.
    }

    /** Drops this device's identity and returns it to the landing page. */
    private void showOut() {
        removeAll();
        DeviceIdentity.forget();
        SessionContext.signOut();
        getUI().ifPresent(ui -> {
            ui.navigate(LandingView.class);
            Notification.show(T.tr("home.gone"), 5000, Notification.Position.TOP_CENTER);
        });
    }

    private boolean isAdmin() {
        return service.findMember(memberId).map(Member::isAdmin).orElse(false);
    }

    // ---- Chrome (header + tabs) --------------------------------------------

    private void buildChrome() {
        // An admin may have deleted the home (or this member) while this device was
        // looking at it — the revision bump lands here first, so show ourselves out
        // rather than blowing up mid-render.
        Optional<Home> current = service.findHome(homeCode);
        if (current.isEmpty() || service.findMember(memberId).isEmpty()) {
            showOut();
            return;
        }
        removeAll();
        boolean admin = isAdmin();
        // Re-applied on every render, so a promotion or demotion by another admin moves this
        // device onto the other idle lifetime as soon as its board updates.
        SessionContext.applyTimeout(admin);
        Home home = current.get();

        Div page = new Div();
        page.addClassName("page-pad");
        page.add(buildHeader(home, admin));
        page.add(buildTabs(admin));
        content.setWidthFull();
        page.add(content);
        add(page);

        showSelected();
        // After the board, so the dialog lands on top of a drawn page. The sweep bumps HomeState
        // when a list reminder fires, which is what brings this here live on an open board.
        showFiredReminders();
    }

    // ---- Unanswered list reminders -----------------------------------------

    /** The dialog currently answering a fired list reminder on this screen, if any. */
    private ListReminderDialog firedDialog;

    /** Fired reminders this screen has already shown and the member closed without answering.
     *  They come back on the next visit, not on the next redraw. */
    private final java.util.Set<Long> firedSeen = new java.util.HashSet<>();

    /**
     * Shows the oldest unanswered list reminder as a dialog, once per nudge per screen. Idempotent
     * across HomeState rebuilds: a dialog already open for a still-unanswered nudge stays; one
     * whose nudge somebody else has since answered closes.
     */
    private void showFiredReminders() {
        if (!pushSender.isEnabled()) {
            return;
        }
        List<ListReminder> fired = listReminderService.firedForHome(homeCode);
        if (firedDialog != null && firedDialog.isOpened()) {
            Long showing = firedDialog.reminderId();
            if (fired.stream().anyMatch(r -> r.getId().equals(showing))) {
                return;
            }
            firedDialog.close();
        }
        for (ListReminder r : fired) {
            if (firedSeen.contains(r.getId())) {
                continue;
            }
            Optional<ListItem> item = listService.find(r.getItemId());
            if (item.isEmpty()) {
                continue; // retired by the sweep on its next pass
            }
            java.util.Map<Long, String> names = new java.util.HashMap<>();
            for (Member m : service.membersOf(homeCode)) {
                names.put(m.getId(), m.getName());
            }
            firedSeen.add(r.getId());
            firedDialog = new ListReminderDialog(listReminderService, listService, reminderService,
                    pushSender, memberId, homeCode, item.get(), r, names, () -> { });
            firedDialog.open();
            return;
        }
    }

    /**
     * Called from the browser whenever this page becomes visible again. Vaadin's generated
     * service worker answers a notification tap by focusing the board if it is already open —
     * no navigation, no redraw — so this is the hook that turns that tap into the snooze dialog.
     * One query, and nothing else: the board itself is not rebuilt.
     */
    @ClientCallable
    private void onVisible() {
        if (homeCode != null && service.findMember(memberId).isPresent()) {
            showFiredReminders();
        }
    }

    /**
     * A header action whose text label collapses away on phones, leaving just the icon.
     * The label stays in the accessible name, so the button is still announced properly.
     */
    private Button headerButton(String label, VaadinIcon icon, ButtonVariant... variants) {
        Span text = new Span(label);
        text.addClassName("btn-label");
        Button b = new Button(icon.create());
        // Button.add(Component...) is not public, so attach the label at the element level.
        b.getElement().appendChild(text.getElement());
        b.setAriaLabel(label);
        b.addThemeVariants(ButtonVariant.LUMO_SMALL);
        b.addThemeVariants(variants);
        return b;
    }

    private Component buildHeader(Home home, boolean admin) {
        H1 name = new H1(home.getName());
        name.addClassName("home-title");
        name.setTitle(home.getName()); // the full name is still reachable when truncated

        // Once the family is actually a family, the invite plumbing (code, copy, share)
        // retires into a compact menu in the right corner. A solo home keeps it front and
        // center — inviting is the one thing that board still needs to make happen.
        boolean solo = service.memberCount(homeCode) <= 1;

        Div left = new Div(name);
        left.addClassName("header-id");
        if (solo) {
            Span code = new Span(home.getCode());
            code.addClassName("code-chip");
            Button copyLink = headerButton(T.tr("home.copyLink"), VaadinIcon.LINK,
                    ButtonVariant.LUMO_CONTRAST);
            copyLink.addClickListener(e -> copyJoinLink(home));
            Button share = headerButton(T.tr("home.share"), VaadinIcon.SHARE,
                    ButtonVariant.LUMO_CONTRAST);
            share.addClickListener(e -> shareJoinLink(home));
            HorizontalLayout codeRow = new HorizontalLayout(code, copyLink, share);
            codeRow.addClassName("header-code");
            codeRow.setAlignItems(FlexComponent.Alignment.CENTER);
            left.add(codeRow);
        }
        statsService.lastWeekChoreMaster(homeCode).ifPresent(cm -> {
            Span master = new Span(T.tr("home.choreMaster", cm.member().getName()));
            master.addClassName("chore-master");
            // The pill says just "Chore master" so it fits a phone header; the full wording —
            // that it is last week's — rides along as the tooltip.
            master.getElement().setAttribute("title",
                    T.tr("home.choreMaster.title", cm.member().getName()));
            left.add(master);
        });

        // Everything occasional — reminders, appearance, language, Admin?, Leave — sits behind
        // one "⋯" button, so the header is the title plus (at most) two 40px buttons. On a phone
        // the old row of labelled buttons pushed the first chore card below the fold.
        HorizontalLayout right = new HorizontalLayout();
        right.addClassName("header-actions");
        right.setAlignItems(FlexComponent.Alignment.CENTER);
        if (!solo) {
            right.add(inviteMenu(home));
        }
        right.add(new HeaderMenu(admin, pushSender.isEnabled(),
                () -> new ReminderDialog(reminderService, snoozeService, pushSender, memberId, homeCode)
                        .open(),
                this::claimAdminDialog,
                this::leave));

        HorizontalLayout header = new HorizontalLayout(left, right);
        header.addClassName("home-header");
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.getStyle().set("margin-bottom", "var(--lumo-space-m)");
        return header;
    }

    /** Leaving is an explicit sign-out, so the device forgets who it was as well. */
    private void leave() {
        DeviceIdentity.forget();
        SessionContext.signOut();
        getUI().ifPresent(ui -> ui.navigate(LandingView.class));
    }

    private Tabs buildTabs(boolean admin) {
        Tab choresTab = new Tab(VaadinIcon.CHECK_SQUARE_O.create(), tabLabel(T.tr("home.tab.chores")));
        Tab listTab = new Tab(VaadinIcon.LIST_UL.create(), tabLabel(T.tr("home.tab.list")));
        Tab statsTab = new Tab(VaadinIcon.CHART.create(), tabLabel(T.tr("home.tab.stats")));
        Tabs tabs = new Tabs(choresTab, listTab, statsTab);
        // Full width, so that below 640px the tabs can share the row equally (icon above label,
        // see .main-tabs in styles.css). Four labelled tabs side by side need ~410px, and a phone
        // has ~360: the Admin tab was scrolling off the end behind a chevron nobody noticed.
        tabs.addClassName("main-tabs");
        tabs.setWidthFull();

        Tab adminTab = null;
        if (admin) {
            Span label = tabLabel(T.tr("home.tab.admin"));
            long pending = service.pendingCount(homeCode) + service.pendingRejoinCount(homeCode);
            if (pending > 0) {
                Span badge = new Span(String.valueOf(pending));
                badge.addClassName("nav-badge");
                label.add(badge);
            }
            adminTab = new Tab(VaadinIcon.COG.create(), label);
            tabs.add(adminTab);
        }
        if (selected == PanelTab.LIST) {
            tabs.setSelectedTab(listTab);
        } else if (selected == PanelTab.STATS) {
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
            if (t == listTab) {
                selected = PanelTab.LIST;
            } else if (t == statsTab) {
                selected = PanelTab.STATS;
            } else if (t == finalAdmin) {
                selected = PanelTab.ADMIN;
            } else {
                selected = PanelTab.CHORES;
            }
            showSelected();
            // A tab switch starts at the top. A phone that was deep in the long Chores or Admin
            // panel would otherwise land on a short panel with the tab bar and all of its content
            // scrolled out of the viewport. The scroller is <body> (styles.css gives it the
            // viewport height and overflow auto), which window.scrollTo does not move, so both
            // are reset. Only on the member's own tap: showSelected() also runs on every
            // HomeState rebuild, and a family member's action must not yank this phone's scroll.
            if (e.isFromClient()) {
                UI.getCurrent().getPage().executeJs(
                        "document.body.scrollTop = 0; document.documentElement.scrollTop = 0;");
            }
        });
        tabs.getStyle().set("margin-bottom", "var(--lumo-space-m)");
        return tabs;
    }

    private static Span tabLabel(String text) {
        Span s = new Span(text);
        s.addClassName("tab-label");
        return s;
    }

    private void showSelected() {
        content.removeAll();
        switch (selected) {
            case LIST -> {
                content.add(listPanel);
                listPanel.refresh();
            }
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
    /**
     * The collapsed invite plumbing for a home that already has company: the join code
     * (tap to copy it bare), Copy link, and Share, behind one share-glyph button. A share
     * icon rather than a generic ⋮ — everything inside is about inviting.
     */
    private MenuBar inviteMenu(Home home) {
        MenuBar invite = new MenuBar();
        invite.addThemeVariants(MenuBarVariant.LUMO_SMALL);
        invite.addClassName("invite-menu");
        MenuItem root = invite.addItem(VaadinIcon.SHARE.create());
        root.setAriaLabel(T.tr("home.invite"));
        SubMenu sub = root.getSubMenu();
        Span codeChip = new Span(home.getCode());
        codeChip.addClassName("code-menu-chip");
        sub.addItem(codeChip, e -> copyCode(home));
        sub.addItem(T.tr("home.copyLink"), e -> copyJoinLink(home));
        sub.addItem(T.tr("home.share"), e -> shareJoinLink(home));
        return invite;
    }

    /** Copies the bare home code — what you dictate to a family member across the room. */
    private void copyCode(Home home) {
        UI.getCurrent().getPage().executeJs(
                "if(navigator.clipboard){navigator.clipboard.writeText($0);}", home.getCode());
        Notification.show(T.tr("home.code.copied"), 2500, Notification.Position.TOP_CENTER);
    }

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
        // Re-stamp the device's stored identity on every visit, so it heals itself if the
        // write was lost (private mode, storage pressure) and outlives the server session.
        // Only with the secret in hand — stamping without one would corrupt a good identity.
        String secret = SessionContext.deviceSecret();
        if (secret != null) {
            DeviceIdentity.remember(memberId, homeCode, secret);
        }
        // Chore availability windows ("dog out 8-10") are evaluated in the member's local
        // time, so fetch the browser's time zone once and re-render when it arrives.
        event.getUI().getPage().retrieveExtendedClientDetails(details -> {
            String tz = details.getTimeZoneId();
            if (tz != null && !tz.isBlank()) {
                try {
                    SessionContext.setTimeZone(java.time.ZoneId.of(tz));
                    // Persist it too: the reminder sweep runs with no session to ask,
                    // and "remind me at 19:00" means 19:00 on this member's own clock.
                    reminderService.updateZone(memberId, tz);
                    buildChrome();
                } catch (Exception ignored) {
                    // unknown zone id — keep the server default
                }
            }
        });
        // See onVisible. Kept on window so a re-navigation replaces the listener instead of
        // stacking another; the isConnected check covers a listener that outlives its view.
        event.getUI().getPage().executeJs(
                "const el = $0;"
                + " if (window.__fcVisible) document.removeEventListener('visibilitychange', window.__fcVisible);"
                + " window.__fcVisible = () => { if (document.visibilityState === 'visible' && el.isConnected)"
                + "   el.$server.onVisible(); };"
                + " document.addEventListener('visibilitychange', window.__fcVisible);",
                getElement());
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
