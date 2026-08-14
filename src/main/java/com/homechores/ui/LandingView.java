package com.homechores.ui;

import com.homechores.domain.Member;
import com.homechores.service.ChoreService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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

    /** Join-form code, kept as a field so a ?join= link can prefill it in beforeEnter. */
    private final ValueSignal<String> joinCode = new ValueSignal<>("");
    private Tabs tabs;
    private Tab joinTab;

    public LandingView(ChoreService service) {
        this.service = service;
        addClassName("centered-page");
        // Width only — see PrivacyView: full height + centered content clips the top edge
        // on small screens.
        setWidthFull();

        Div card = new Div();
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

        add(card, footer);
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
            SessionContext.signIn(m.getId(), m.getHomeCode());
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
            SessionContext.signIn(m.get().getId(), m.get().getHomeCode());
            getUI().ifPresent(ui -> ui.navigate(HomeView.class));
        };
        join.addClickListener(e -> submit.run());
        yourNameField.addKeyPressListener(Key.ENTER, e -> submit.run());

        Div form = new Div(codeField, yourNameField, join);
        form.getStyle().set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--lumo-space-m)").set("margin-top", "var(--lumo-space-m)");
        return form;
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
