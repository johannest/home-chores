package com.homechores.ui;

import com.homechores.i18n.Translations;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.server.VaadinSession;
import java.util.Locale;

/** Compact language chooser (English / Finnish / Swedish). Persists via a cookie. */
class LanguageSwitcher extends Select<String> {

    LanguageSwitcher() {
        setItems("en", "fi", "sv");
        setItemLabelGenerator(code -> T.tr("lang." + code));
        setValue(currentCode());
        setWidth("8.5em");
        // Narrowed on phones (see styles.css) so the header actions beside it keep their
        // text labels — a bare key or exit icon is far less obvious than "Admin?"/"Leave".
        addClassName("lang-select");

        addValueChangeListener(e -> {
            String code = e.getValue();
            if (code == null || code.equals(currentCode())) {
                return;
            }
            UI ui = UI.getCurrent();
            // Persist the choice for future sessions, apply it now, and re-render.
            ui.getPage().executeJs(
                    "document.cookie='lang='+$0+';path=/;max-age=31536000;SameSite=Lax'", code);
            VaadinSession.getCurrent().setLocale(localeFor(code));
            ui.getPage().reload();
        });
    }

    private static String currentCode() {
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
