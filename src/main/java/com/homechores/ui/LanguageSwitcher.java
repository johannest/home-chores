package com.homechores.ui;

import com.homechores.i18n.Translations;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.server.VaadinSession;
import java.util.List;
import java.util.Locale;

/**
 * Compact language chooser (English / Finnish / Swedish) for the landing page. Persists via a
 * cookie. The board header offers the same three languages as rows inside {@link HeaderMenu},
 * through {@link #apply}, so the two can never disagree on how a switch is done.
 */
class LanguageSwitcher extends Select<String> {

    /** The languages offered, in menu order. */
    static final List<String> CODES = List.of("en", "fi", "sv");

    LanguageSwitcher() {
        setItems(CODES);
        setItemLabelGenerator(code -> T.tr("lang." + code));
        setValue(currentCode());
        setWidth("8.5em");
        setAriaLabel(T.tr("home.menu.language"));
        addClassName("lang-select");

        addValueChangeListener(e -> apply(e.getValue()));
    }

    /** Persists the choice for future sessions, applies it now, and reloads so every view
     *  re-renders in the new language. A no-op for the language already in use. */
    static void apply(String code) {
        if (code == null || code.equals(currentCode())) {
            return;
        }
        UI ui = UI.getCurrent();
        ui.getPage().executeJs(
                "document.cookie='lang='+$0+';path=/;max-age=31536000;SameSite=Lax'", code);
        VaadinSession.getCurrent().setLocale(localeFor(code));
        ui.getPage().reload();
    }

    static String currentCode() {
        Locale l = VaadinSession.getCurrent() != null ? VaadinSession.getCurrent().getLocale() : null;
        if (l == null) {
            return "en";
        }
        return switch (l.getLanguage()) {
            case "fi" -> "fi";
            case "sv" -> "sv";
            default -> "en";
        };
    }

    private static Locale localeFor(String code) {
        return switch (code) {
            case "fi" -> Translations.FINNISH;
            case "sv" -> Translations.SWEDISH;
            default -> Translations.ENGLISH;
        };
    }
}
