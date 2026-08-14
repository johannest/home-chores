package com.homechores.i18n;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import jakarta.servlet.http.Cookie;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Applies a previously chosen language (stored in the {@code lang} cookie) to each new
 * session. Without a cookie, Vaadin's browser-language default (via
 * {@link Translations#getProvidedLocales()}) is used.
 */
@Component
public class LocaleInitListener implements VaadinServiceInitListener {

    static final String COOKIE = "lang";

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addSessionInitListener(sessionInit -> {
            var request = sessionInit.getRequest();
            if (request == null || request.getCookies() == null) {
                return;
            }
            for (Cookie c : request.getCookies()) {
                if (COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    Locale chosen = switch (c.getValue()) {
                        case "fi" -> Translations.FINNISH;
                        case "sv" -> Translations.SWEDISH;
                        default -> Translations.ENGLISH;
                    };
                    sessionInit.getSession().setLocale(chosen);
                    return;
                }
            }
        });
    }
}
