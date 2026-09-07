package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.i18n.Translations;
import com.homechores.service.HomeCleanupService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.testbench.unit.SpringUIUnitTest;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The user agreement reads in the visitor's language, with the real retention windows in it. */
@SpringBootTest
class TermsUiTest extends SpringUIUnitTest {

    @Autowired HomeCleanupService cleanup;

    private String pageText() {
        StringBuilder sb = new StringBuilder();
        $(H1.class).all().forEach(h -> sb.append(h.getText()).append('\n'));
        $(H2.class).all().forEach(h -> sb.append(h.getText()).append('\n'));
        $(Paragraph.class).all().forEach(p -> sb.append(p.getText()).append('\n'));
        return sb.toString();
    }

    private String open(Locale locale) {
        UI.getCurrent().setLocale(locale);
        navigate(TermsView.class);
        return pageText();
    }

    @Test
    void readsInEnglishByDefault() {
        String text = open(Locale.ENGLISH);
        assertTrue(text.contains("FlashChores user agreement"), text);
        assertTrue(text.contains("1. Unused homes are removed"), text);
        assertEquals("User agreement — FlashChores", UI.getCurrent().getInternals().getTitle());
    }

    @Test
    void readsInFinnish() {
        String text = open(Translations.FINNISH);
        assertTrue(text.contains("FlashChoresin käyttöehdot"), text);
        assertTrue(text.contains("4. Reilu käyttö"), text);
        assertEquals("Käyttöehdot — FlashChores", UI.getCurrent().getInternals().getTitle());
        assertNoEnglishLeaked(text);
    }

    @Test
    void readsInSwedish() {
        String text = open(Translations.SWEDISH);
        assertTrue(text.contains("Användarvillkor för FlashChores"), text);
        assertTrue(text.contains("4. Skälig användning"), text);
        assertEquals("Användarvillkor — FlashChores", UI.getCurrent().getInternals().getTitle());
        assertNoEnglishLeaked(text);
    }

    /** The configured windows appear as numbers in every language, and no key shows raw. */
    @Test
    void retentionWindowsAreSpelledOutFromConfiguration() {
        for (Locale locale : List.of(Locale.ENGLISH, Translations.FINNISH, Translations.SWEDISH)) {
            String text = open(locale);
            assertFalse(text.contains("terms."), "raw key leaked in " + locale + ": " + text);
            assertFalse(text.contains("{0}"), "unfilled placeholder in " + locale + ": " + text);
            if (cleanup.getInactiveHomeDays() > 0) {
                assertTrue(text.contains(" " + cleanup.getInactiveHomeDays() + " "), locale + ": " + text);
            }
            if (cleanup.getAbandonedHomeDays() > 0) {
                assertTrue(text.contains(String.valueOf(cleanup.getAbandonedHomeDays())), locale + ": " + text);
            }
        }
    }

    private static void assertNoEnglishLeaked(String text) {
        for (String english : List.of("Unused homes", "Fair use", "Last updated", "Back to FlashChores")) {
            assertFalse(text.contains(english), "untranslated: " + english);
        }
    }
}
