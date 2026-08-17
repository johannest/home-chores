package com.homechores.ui;

import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.domain.RejoinRequest;
import com.homechores.domain.RejoinStatus;
import com.homechores.service.ChoreService;
import com.homechores.service.HomeState;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import java.util.Optional;

/** Kahoot-style entry screen: create a new home or join an existing one with a code. */
@Route("")
@PageTitle("FlashChores")
public class LandingView extends VerticalLayout implements BeforeEnterObserver {

    private final ChoreService service;
    private final HomeState homeState;

    /** Join-form code, kept as a field so a ?join= link can prefill it in beforeEnter. */
    private final ValueSignal<String> joinCode = new ValueSignal<>("");
    private Tabs tabs;
    private Tab joinTab;

    private final Div card = new Div();
    private final Div restoring = new Div();
    private final Div waiting = new Div();

    /** Token of the rejoin request this device is waiting on, while the waiting card shows. */
    private String waitingToken;

    public LandingView(ChoreService service, HomeState homeState) {
        this.service = service;
        this.homeState = homeState;
        addClassName("centered-page");
        // Width only — see PrivacyView: full height + centered content clips the top edge
        // on small screens.
        setWidthFull();

        card.addClassName("auth-card");

        Div langRow = new Div(new LanguageSwitcher());
        langRow.getStyle().set("display", "flex").set("justify-content", "flex-end");

        H1 title = new H1("⚡ FlashChores");
        title.addClassName("brand-title");
        Paragraph sub = new Paragraph(T.tr("landing.tagline"));
        sub.addClassName("brand-sub");

        Tab createTab = new Tab(T.tr("landing.tab.create"));
        joinTab = new Tab(T.tr("landing.tab.join"));
        tabs = new Tabs(createTab, joinTab);
        tabs.setWidthFull();

        Div createForm = buildCreateForm();
        Div joinForm = buildJoinForm();
        joinForm.setVisible(false);

        tabs.addSelectedChangeListener(e -> {
            boolean create = e.getSelectedTab() == createTab;
            createForm.setVisible(create);
            joinForm.setVisible(!create);
        });

        card.add(langRow, title, sub, tabs, createForm, joinForm);

        buildRestoringCard();
        waiting.addClassName("auth-card");
        waiting.setVisible(false);

        Div footer = new Div();
        footer.getStyle().set("margin-top", "var(--lumo-space-m)")
                .set("display", "flex").set("flex-direction", "column")
                .set("align-items", "center").set("gap", "6px")
                .set("text-align", "center").set("font-size", "0.85rem");
        RouterLink privacy = new RouterLink(T.tr("landing.footer.privacy"), PrivacyView.class);
        privacy.getStyle().set("color", "var(--lumo-secondary-text-color)");
        footer.add(privacy,
                footerLink("https://vaadin.com", VaadinIcon.VAADIN_H,
                        T.tr("landing.footer.vaadin")),
                footerLink("https://github.com/johannest/home-chores", VaadinIcon.CODE,
                        T.tr("landing.footer.github")));

        add(restoring, card, waiting, footer);
    }

    private void buildRestoringCard() {
        restoring.addClassName("restore-overlay");
        H1 title = new H1("⚡ FlashChores");
        title.addClassName("brand-title");
        ProgressBar bar = new ProgressBar();
        bar.setIndeterminate(true);
        Paragraph text = new Paragraph(T.tr("landing.restoring"));
        text.addClassName("brand-sub");
        restoring.add(title, bar, text);
    }

    private Div buildCreateForm() {
        ValueSignal<String> homeName = new ValueSignal<>("");
        ValueSignal<String> yourName = new ValueSignal<>("");

        Paragraph adultNote = new Paragraph(T.tr("landing.adultNote"));
        adultNote.addClassName("feedback-hint");

        TextField homeNameField = new TextField(T.tr("landing.homeName"));
        homeNameField.setPlaceholder(T.tr("landing.homeName.placeholder"));
        homeNameField.setWidthFull();
        homeNameField.bindValue(homeName, homeName::set);

        TextField yourNameField = new TextField(T.tr("landing.yourName"));
        yourNameField.setPlaceholder(T.tr("landing.yourName.placeholder"));
        yourNameField.setWidthFull();
        yourNameField.setHelperText(T.tr("landing.nicknameTip"));
        yourNameField.bindValue(yourName, yourName::set);

        Button create = new Button(T.tr("landing.create"));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        create.setWidthFull();
        create.bindEnabled(Signal.computed(() ->
                !homeName.get().isBlank() && !yourName.get().isBlank()));

        Runnable submit = () -> {
            if (homeName.peek().isBlank() || yourName.peek().isBlank()) {
                return;
            }
            Member m = service.createHome(homeName.peek(), yourName.peek(), getLocale());
            signIn(m.getId(), m.getHomeCode());
            String pin = service.findHome(m.getHomeCode()).map(h -> h.getAdminPin()).orElse("----");
            showCreatedDialog(m.getHomeCode(), pin);
        };
        create.addClickListener(e -> submit.run());
        yourNameField.addKeyPressListener(Key.ENTER, e -> submit.run());

        Div form = new Div(adultNote, homeNameField, yourNameField, create);
        form.getStyle().set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--lumo-space-m)").set("margin-top", "var(--lumo-space-m)");
        return form;
    }

    private Div buildJoinForm() {
        ValueSignal<String> code = joinCode;
        ValueSignal<String> yourName = new ValueSignal<>("");

        TextField codeField = new TextField(T.tr("landing.homeCode"));
        codeField.setPlaceholder(T.tr("landing.homeCode.placeholder"));
        codeField.setWidthFull();
        codeField.bindValue(code, code::set);

        TextField yourNameField = new TextField(T.tr("landing.yourName"));
        yourNameField.setPlaceholder(T.tr("landing.joinName.placeholder"));
        yourNameField.setWidthFull();
        yourNameField.setHelperText(T.tr("landing.joinNicknameTip"));
        yourNameField.bindValue(yourName, yourName::set);

        Button join = new Button(T.tr("landing.join"));
        join.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        join.setWidthFull();
        join.bindEnabled(Signal.computed(() ->
                !code.get().isBlank() && !yourName.get().isBlank()));

        Runnable submit = () -> {
            if (code.peek().isBlank() || yourName.peek().isBlank()) {
                return;
            }
            Optional<Member> m = service.joinHome(code.peek(), yourName.peek());
            if (m.isEmpty()) {
                warn(T.tr("landing.join.notFound", code.peek().trim().toUpperCase()));
                return;
            }
            signIn(m.get().getId(), m.get().getHomeCode());
            getUI().ifPresent(ui -> ui.navigate(HomeView.class));
        };
        join.addClickListener(e -> submit.run());
        yourNameField.addKeyPressListener(Key.ENTER, e -> submit.run());

        // Recovery path for a phone that cleared its browser storage: sign back in as the
        // member you already are, instead of creating a second one with the same name.
        Button rejoin = new Button(T.tr("landing.rejoin.link"), VaadinIcon.USER_CHECK.create(),
                e -> openRejoinDialog(code.peek()));
        rejoin.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        rejoin.setWidthFull();
        Paragraph rejoinHint = new Paragraph(T.tr("landing.rejoin.hint"));
        rejoinHint.addClassName("feedback-hint");

        Div form = new Div(codeField, yourNameField, join, rejoinHint, rejoin);
        form.getStyle().set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--lumo-space-m)").set("margin-top", "var(--lumo-space-m)");
        return form;
    }

    // ---- Rejoining as an existing member ------------------------------------

    /** Lists the home's members so a returning device can point at itself. */
    private void openRejoinDialog(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            warn(T.tr("landing.rejoin.needCode"));
            return;
        }
        Optional<Home> home = service.findHome(rawCode);
        if (home.isEmpty()) {
            // Either a typo or a home an admin has since deleted — say so, rather than
            // asking for a code that's already filled in.
            warn(T.tr("landing.join.notFound", rawCode.trim().toUpperCase()));
            return;
        }
        Home h = home.get();

        Dialog d = new Dialog();
        d.setHeaderTitle(T.tr("landing.rejoin.title"));
        d.setWidth("min(90vw, 24em)");

        Span intro = new Span(T.tr("landing.rejoin.intro", h.getName()));
        intro.addClassName("sub");

        // The PIN is optional and only useful where the gate is on — it skips the wait.
        TextField pin = new TextField(T.tr("landing.rejoin.pin"));
        pin.setMaxLength(4);
        pin.setWidthFull();
        pin.setHelperText(T.tr("landing.rejoin.pin.helper"));
        pin.setVisible(h.isApproveRejoin());

        VerticalLayout body = new VerticalLayout(intro);
        body.setPadding(false);
        body.setSpacing(true);
        body.setWidthFull();

        for (Member m : service.membersOf(h.getCode())) {
            Div dot = new Div();
            dot.addClassName("dot");
            dot.getStyle().set("background", m.getColor());
            dot.setText(m.getName().isEmpty() ? "?" : m.getName().substring(0, 1).toUpperCase());

            Div name = new Div();
            name.setText(m.getName() + (m.isAdmin() ? " 👑" : ""));
            name.getStyle().set("font-weight", "600");
            name.addClassName("grow");

            Button pick = new Button(T.tr("landing.rejoin.thisIsMe"),
                    e -> attemptRejoin(d, pin, h, m));
            pick.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

            Div row = new Div(dot, name, pick);
            row.addClassName("list-row");
            body.add(row);
        }

        body.add(pin);
        d.add(body);
        d.getFooter().add(new Button(T.tr("common.cancel"), e -> d.close()));
        d.open();
    }

    private void attemptRejoin(Dialog dialog, TextField pin, Home home, Member member) {
        String pinValue = pin.isVisible() ? pin.getValue() : null;
        var outcome = service.requestRejoin(home.getCode(), member.getId(), pinValue);
        switch (outcome.result()) {
            case SIGNED_IN -> {
                dialog.close();
                signIn(member.getId(), home.getCode());
                getUI().ifPresent(ui -> ui.navigate(HomeView.class));
            }
            case PENDING -> {
                dialog.close();
                DeviceIdentity.rememberRejoinToken(outcome.token());
                showWaiting(outcome.token(), home.getCode(), member.getName());
            }
            case WRONG_PIN -> {
                pin.setInvalid(true);
                pin.setErrorMessage(T.tr("claim.wrong"));
            }
            case UNKNOWN -> {
                dialog.close();
                warn(T.tr("landing.rejoin.gone"));
            }
        }
    }

    /**
     * Shows the "waiting for an admin" card and watches the home's revision signal, so the
     * decision lands on this screen the moment an admin makes it — no polling, no reload.
     */
    private void showWaiting(String token, String homeCode, String memberName) {
        this.waitingToken = token;
        restoring.setVisible(false);
        card.setVisible(false);
        waiting.setVisible(true);
        waiting.removeAll();

        Div emoji = new Div();
        emoji.setText("⏳");
        emoji.addClassName("celebrate-emoji");
        Div title = new Div();
        title.setText(T.tr("landing.waiting.title"));
        title.addClassName("celebrate-title");
        Paragraph text = new Paragraph(T.tr("landing.waiting.text", memberName));
        text.addClassName("celebrate-text");
        ProgressBar bar = new ProgressBar();
        bar.setIndeterminate(true);
        Button cancel = new Button(T.tr("landing.waiting.cancel"), e -> {
            service.cancelRejoin(token);
            DeviceIdentity.forgetRejoinToken();
            waitingToken = null;
            waiting.setVisible(false);
            showCard();
        });
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        waiting.add(emoji, title, text, bar, cancel);

        Signal.effect(this, () -> {
            homeState.revision(homeCode).get(); // re-run whenever anything in the home changes
            if (waitingToken == null) {
                return;
            }
            Optional<RejoinRequest> r = service.findRejoinByToken(waitingToken);
            if (r.isEmpty()) {
                // An admin removed the member, or the home was restored from a backup.
                finishWaiting();
                warn(T.tr("landing.rejoin.gone"));
                showCard();
            } else if (r.get().getStatus() == RejoinStatus.APPROVED) {
                RejoinRequest req = r.get();
                service.consumeRejoin(req.getId());
                finishWaiting();
                signIn(req.getMemberId(), req.getHomeCode());
                getUI().ifPresent(ui -> ui.navigate(HomeView.class));
            } else if (r.get().getStatus() == RejoinStatus.REJECTED) {
                service.consumeRejoin(r.get().getId());
                finishWaiting();
                warn(T.tr("landing.waiting.rejected"));
                showCard();
            }
        });
    }

    private void finishWaiting() {
        waitingToken = null;
        DeviceIdentity.forgetRejoinToken();
        waiting.setVisible(false);
    }

    // ---- Startup: restore identity from the browser -------------------------

    @Override
    protected void onAttach(AttachEvent event) {
        if (SessionContext.isSignedIn()) {
            return; // beforeEnter already forwarded to the board
        }
        DeviceIdentity.read(stored -> {
            if (stored.isPresent() && restoreFrom(stored.get())) {
                return;
            }
            if (stored.isPresent()) {
                DeviceIdentity.forget(); // points at a member or home that no longer exists
            }
            checkPendingRejoin();
        });
    }

    /** Signs in from a stored identity, if the member and home are both still there. */
    private boolean restoreFrom(DeviceIdentity.Stored stored) {
        Optional<Member> m = service.findMember(stored.memberId());
        if (m.isEmpty() || !m.get().getHomeCode().equals(stored.homeCode())
                || service.findHome(stored.homeCode()).isEmpty()) {
            return false;
        }
        SessionContext.signIn(m.get().getId(), m.get().getHomeCode());
        getUI().ifPresent(ui -> ui.navigate(HomeView.class));
        return true;
    }

    /** Picks a rejoin request back up after a reload — including one decided while away. */
    private void checkPendingRejoin() {
        DeviceIdentity.readRejoinToken(token -> {
            Optional<RejoinRequest> r = token.flatMap(service::findRejoinByToken);
            if (r.isEmpty()) {
                DeviceIdentity.forgetRejoinToken();
                showCard();
                return;
            }
            RejoinRequest req = r.get();
            switch (req.getStatus()) {
                case APPROVED -> {
                    service.consumeRejoin(req.getId());
                    DeviceIdentity.forgetRejoinToken();
                    signIn(req.getMemberId(), req.getHomeCode());
                    getUI().ifPresent(ui -> ui.navigate(HomeView.class));
                }
                case REJECTED -> {
                    service.consumeRejoin(req.getId());
                    DeviceIdentity.forgetRejoinToken();
                    warn(T.tr("landing.waiting.rejected"));
                    showCard();
                }
                case PENDING -> showWaiting(req.getDeviceToken(), req.getHomeCode(),
                        service.findMember(req.getMemberId()).map(Member::getName).orElse("?"));
            }
        });
    }

    /** Lifts the restoring overlay — the join/create card is underneath it all along. */
    private void showCard() {
        restoring.setVisible(false);
        card.setVisible(true);
    }

    /** Signs in for this session and remembers the identity on the device itself. */
    private void signIn(Long memberId, String homeCode) {
        SessionContext.signIn(memberId, homeCode);
        DeviceIdentity.remember(memberId, homeCode);
    }

    /** A small footer row: icon + text linking to an external site (new tab). */
    private Anchor footerLink(String href, VaadinIcon icon, String text) {
        Icon i = icon.create();
        i.setSize("16px");
        Anchor a = new Anchor(href, "");
        a.setTarget("_blank");
        a.getElement().setAttribute("rel", "noopener");
        a.add(i, new Span(text));
        a.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("display", "inline-flex").set("align-items", "center").set("gap", "6px");
        return a;
    }

    private void warn(String message) {
        Notification n = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    /** Shows the new home's code and the private admin PIN once, then enters the home. */
    private void showCreatedDialog(String code, String pin) {
        com.vaadin.flow.component.dialog.Dialog d = new com.vaadin.flow.component.dialog.Dialog();
        d.setModal(true);
        d.setCloseOnEsc(false);
        d.setCloseOnOutsideClick(false);

        Div emoji = new Div();
        emoji.setText("🏠");
        emoji.addClassName("celebrate-emoji");
        Div title = new Div();
        title.setText(T.tr("created.title"));
        title.addClassName("celebrate-title");

        Paragraph share = new Paragraph(T.tr("created.shareCode"));
        share.addClassName("celebrate-text");
        Span codeChip = new Span(code);
        codeChip.addClassName("pin-box");

        Paragraph pinText = new Paragraph(T.tr("created.pinInfo"));
        pinText.addClassName("celebrate-text");
        Span pinChip = new Span(pin);
        pinChip.addClassName("pin-box");

        Button go = new Button(T.tr("created.go"), e -> {
            d.close();
            getUI().ifPresent(ui -> ui.navigate(HomeView.class));
        });
        go.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);

        VerticalLayout layout = new VerticalLayout(emoji, title, share, codeChip,
                pinText, pinChip, go);
        layout.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        layout.setPadding(true);
        d.add(layout);
        d.open();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (SessionContext.isSignedIn()) {
            event.forwardTo(HomeView.class);
            return;
        }
        // A shared join link (…/?join=CODE) preselects the Join tab and fills the code.
        event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("join", java.util.List.of()).stream().findFirst()
                .ifPresent(codeParam -> {
                    joinCode.set(codeParam.trim().toUpperCase());
                    if (tabs != null && joinTab != null) {
                        tabs.setSelectedTab(joinTab);
                    }
                });
    }
}
