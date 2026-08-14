package com.homechores.i18n;

import com.vaadin.flow.i18n.I18NProvider;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.springframework.stereotype.Component;

/**
 * Loads UI translations from {@code messages[_xx].properties} bundles.
 * Supported languages: English (default), Finnish, Swedish. Because
 * {@link #getProvidedLocales()} lists them, Vaadin picks the best match for the
 * visitor's browser automatically as the initial locale.
 */
@Component
public class Translations implements I18NProvider {

    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale FINNISH = Locale.of("fi");
    public static final Locale SWEDISH = Locale.of("sv");

    // English first so it's the fallback when the browser language is unsupported.
    private static final List<Locale> LOCALES = List.of(ENGLISH, FINNISH, SWEDISH);

    @Override
    public List<Locale> getProvidedLocales() {
        return LOCALES;
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        if (key == null) {
            return "";
        }
        ResourceBundle bundle = ResourceBundle.getBundle("messages", locale == null ? ENGLISH : locale);
        String value;
        try {
            value = bundle.getString(key);
        } catch (MissingResourceException e) {
            return key; // show the key so a missing translation is obvious
        }
        return params.length == 0 ? value : MessageFormat.format(value, params);
    }
}
