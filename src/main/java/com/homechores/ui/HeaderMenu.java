package com.homechores.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;

/**
 * The board header's one "⋯" menu: reminders, appearance, language, "Admin?" and "Leave".
 *
 * <p>These used to be a row of their own buttons under the home name, which on a phone made the
 * header three to five rows tall before the first chore was in sight. Every one of them is used
 * once in a while, never while doing chores, so they fold behind a single 40px button and the
 * header shrinks to the title plus this. Inviting stays outside (see {@code HomeView.inviteMenu}):
 * it is the one action a fresh family reaches for repeatedly.
 *
 * <p>Appearance and language are nested sub menus rather than flat rows, which keeps the first
 * level to at most six entries — a menu that needs scrolling on a phone has stopped being a
 * shortcut.
 */
class HeaderMenu extends MenuBar {

    private final AppearanceItems appearance;

    HeaderMenu(boolean admin, boolean pushEnabled, Runnable onReminders, Runnable onClaimAdmin,
               Runnable onLeave) {
        addClassName("header-menu");
        addThemeVariants(MenuBarVariant.LUMO_SMALL);

        MenuItem root = addItem(VaadinIcon.ELLIPSIS_DOTS_V.create());
        root.setAriaLabel(T.tr("home.menu"));
        SubMenu sub = root.getSubMenu();

        if (admin) {
            // The role marker that used to be a pill in the header. Not a choice, just a fact.
            sub.addComponent(AppearanceItems.sectionLabel(T.tr("home.adminBadge")));
        }
        if (pushEnabled) {
            sub.addItem(row(VaadinIcon.BELL, T.tr("reminder.button")), e -> onReminders.run());
        }

        MenuItem look = sub.addItem(row(VaadinIcon.PALETTE, T.tr("appearance.title")));
        appearance = new AppearanceItems(look.getSubMenu());

        MenuItem language = sub.addItem(row(VaadinIcon.GLOBE, T.tr("home.menu.language")));
        String current = LanguageSwitcher.currentCode();
        for (String code : LanguageSwitcher.CODES) {
            MenuItem item = language.getSubMenu().addItem(T.tr("lang." + code),
                    e -> LanguageSwitcher.apply(code));
            item.setCheckable(true);
            item.setChecked(code.equals(current));
        }

        sub.addComponent(new Hr());
        if (!admin) {
            sub.addItem(row(VaadinIcon.KEY, T.tr("home.claimAdmin")), e -> onClaimAdmin.run());
        }
        sub.addItem(row(VaadinIcon.SIGN_OUT, T.tr("home.leave")), e -> onLeave.run());
    }

    /** Icon plus text, so the rows scan as fast as the buttons they replace. */
    private static Component row(VaadinIcon icon, String text) {
        Icon i = icon.create();
        i.addClassName("menu-row-icon");
        Span s = new Span(i, new Span(text));
        s.addClassName("menu-row");
        return s;
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        appearance.sync(event.getUI().getPage());
    }
}
