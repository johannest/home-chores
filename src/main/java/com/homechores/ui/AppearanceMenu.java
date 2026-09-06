package com.homechores.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How this device looks: colour scheme (auto / light / dark) and palette (green / pink / blue /
 * grey), behind one palette-glyph button.
 *
 * <p>A menu rather than two selects, because of where it lives. The board header already carries a
 * language select, a bell, "Admin?" and "Leave", and below 640px it wraps them onto their own
 * full-width row with the spacing deliberately trimmed to keep them off a third one. A second
 * 7.5em select would spend that margin and more; this spends none, because it <em>replaces</em>
 * the theme select outright — the header gets narrower while gaining a setting. It also lets the
 * palettes be shown as actual coloured circles, which is how people pick a colour.
 *
 * <p>Both choices are per device and per browser: nothing here touches the home, and the server
 * never learns them. Applying and persisting happen entirely in the browser
 * ({@code window.__applyTheme} / {@code window.__applyPalette} in {@code index.html}, which also
 * re-apply before Vaadin loads so there is no flash of the wrong one). This class asks the browser
 * what is stored when it attaches for one reason only: to put the check marks in the right places.
 */
class AppearanceMenu extends MenuBar {

    private static final List<String> SCHEMES = List.of("auto", "light", "dark");
    private static final List<String> PALETTES = List.of("green", "pink", "blue", "gray");

    private final Map<String, MenuItem> schemeItems = new LinkedHashMap<>();
    private final Map<String, MenuItem> paletteItems = new LinkedHashMap<>();

    AppearanceMenu() {
        addClassName("appearance-menu");
        addThemeVariants(MenuBarVariant.LUMO_SMALL, MenuBarVariant.LUMO_TERTIARY_INLINE);

        MenuItem root = addItem(VaadinIcon.PALETTE.create());
        root.setAriaLabel(T.tr("appearance.title"));
        SubMenu sub = root.getSubMenu();

        sub.addComponent(sectionLabel(T.tr("appearance.scheme")));
        for (String mode : SCHEMES) {
            schemeItems.put(mode, checkable(sub, T.tr("theme." + mode), null,
                    () -> select(schemeItems, mode, "window.__applyTheme($0)", mode)));
        }

        sub.addComponent(new Hr());
        sub.addComponent(sectionLabel(T.tr("appearance.palette")));
        for (String palette : PALETTES) {
            paletteItems.put(palette, checkable(sub, T.tr("palette." + palette), dot(palette),
                    () -> select(paletteItems, palette, "window.__applyPalette($0)", palette)));
        }

        setValue(schemeItems, "auto");
        setValue(paletteItems, "green");
    }

    /** A non-interactive heading inside the menu. */
    private static Span sectionLabel(String text) {
        Span s = new Span(text);
        s.addClassName("menu-section-label");
        return s;
    }

    /**
     * One checkable row. The group behaves like radio buttons because {@link #select} clears its
     * siblings — {@code MenuBar} has no radio-group notion of its own. {@code setKeepOpen} lets a
     * member try all four palettes without reopening the menu each time.
     */
    private MenuItem checkable(SubMenu sub, String label, Component leading, Runnable action) {
        MenuItem item = sub.addItem("");
        if (leading != null) {
            item.add(leading);
        }
        item.add(new Span(label));
        item.setCheckable(true);
        item.setKeepOpen(true);
        item.addClickListener(e -> action.run());
        return item;
    }

    private void select(Map<String, MenuItem> group, String chosen, String js, String arg) {
        setValue(group, chosen);
        UI.getCurrent().getPage().executeJs(js, arg);
    }

    private static void setValue(Map<String, MenuItem> group, String chosen) {
        group.forEach((key, item) -> item.setChecked(key.equals(chosen)));
    }

    /** The colour chip beside a palette's name. The hues live in styles.css next to the palette
     *  blocks they mirror, so Java never carries a colour. */
    private static Span dot(String palette) {
        Span s = new Span();
        s.addClassName("palette-dot");
        s.getElement().setAttribute("data-palette", palette);
        return s;
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        // One round trip for both axes; the guarded read matches DeviceIdentity.
        event.getUI().getPage().executeJs(
                        "try { return (localStorage.getItem('flashchores.theme') || 'auto') + '|'"
                        + " + (localStorage.getItem('flashchores.palette') || 'green'); }"
                        + " catch (e) { return 'auto|green'; }")
                .then(String.class, stored -> {
                    String[] parts = (stored == null ? "auto|green" : stored).split("\\|", 2);
                    if (SCHEMES.contains(parts[0])) {
                        setValue(schemeItems, parts[0]);
                    }
                    if (parts.length > 1 && PALETTES.contains(parts[1])) {
                        setValue(paletteItems, parts[1]);
                    }
                });
    }
}
