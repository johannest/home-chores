package com.homechores.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;

/**
 * How this device looks, behind one palette-glyph button: the landing page's appearance picker.
 * The rows themselves live in {@link AppearanceItems}, which the board header shares — there they
 * sit under "Appearance" inside {@link HeaderMenu} instead of getting a button of their own, so
 * the header stays one row on a phone.
 */
class AppearanceMenu extends MenuBar {

    private final AppearanceItems items;

    AppearanceMenu() {
        addClassName("appearance-menu");
        addThemeVariants(MenuBarVariant.LUMO_SMALL, MenuBarVariant.LUMO_TERTIARY_INLINE);

        MenuItem root = addItem(VaadinIcon.PALETTE.create());
        root.setAriaLabel(T.tr("appearance.title"));
        items = new AppearanceItems(root.getSubMenu());
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        items.sync(event.getUI().getPage());
    }
}
