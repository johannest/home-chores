package com.homechores.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The three message bundles must carry the same keys.
 *
 * <p>They are edited by hand, three files at a time, and a missed key fails nothing at runtime —
 * {@code Translations} returns the key itself, so a Finnish user simply reads "admin.moveUp" on a
 * button. That is a typo-shaped bug with no stack trace and, until now, no test behind it.
 */
class MessageParityTest {

    private static final List<String> BUNDLES =
            List.of("messages.properties", "messages_fi.properties", "messages_sv.properties");

    private static Properties load(String name) {
        Properties p = new Properties();
        try (InputStream in = MessageParityTest.class.getClassLoader().getResourceAsStream(name)) {
            assertTrue(in != null, name + " is on the classpath");
            p.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p;
    }

    @Test
    void everyBundleCarriesTheSameKeys() {
        Set<String> english = new TreeSet<>(load("messages.properties").stringPropertyNames());
        for (String bundle : BUNDLES.subList(1, BUNDLES.size())) {
            Set<String> other = new TreeSet<>(load(bundle).stringPropertyNames());
            Set<String> missing = new TreeSet<>(english);
            missing.removeAll(other);
            Set<String> extra = new TreeSet<>(other);
            extra.removeAll(english);
            assertEquals(Set.of(), missing, bundle + " is missing keys");
            assertEquals(Set.of(), extra, bundle + " has keys English does not");
        }
    }

    /**
     * A value containing {@code {0}} goes through {@link MessageFormat}, where a lone apostrophe
     * quotes the placeholder instead of printing — "you'll get {0}" renders as "youll get {0}".
     * Finnish and Swedish reach for apostrophes more readily than English, so this is a real trap
     * and the bundle header warns about it. Only parameterized values are checked; elsewhere an
     * apostrophe is just an apostrophe.
     */
    @Test
    void parameterizedValuesDoNotCarryALoneApostrophe() {
        for (String bundle : BUNDLES) {
            Properties p = load(bundle);
            Set<String> offenders = new LinkedHashSet<>();
            for (String key : p.stringPropertyNames()) {
                String value = p.getProperty(key);
                if (value.contains("{") && value.replace("''", "").contains("'")) {
                    offenders.add(bundle + ": " + key + " = " + value);
                }
            }
            assertEquals(Set.of(), offenders,
                    "double the apostrophe in a MessageFormat value, or it quotes the placeholder");
        }
    }
}
