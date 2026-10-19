package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.i18n.Translations;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.internal.PendingJavaScriptInvocation;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The client-side "Getting your chores…" card gets its text from the server, in the UI's
 * language, and appears at the loading indicator's second stage.
 */
@SpringBootTest
class LoadingOverlayUiTest extends HomeChoresUiTest {

    /** The text the UI last sent to the card, or null if it sent none. */
    private static String overlayTextSentBy(UI ui) {
        List<PendingJavaScriptInvocation> pending = ui.getInternals().dumpPendingJavaScriptInvocations();
        return pending.stream()
                .filter(p -> p.getInvocation().getExpression().contains("loading-overlay-text"))
                .map(p -> (String) p.getInvocation().getParameters().get(0))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    @Test
    void cardTextIsSentInTheUisLanguage() {
        UI ui = UI.getCurrent();
        ui.setLocale(Translations.FINNISH);
        LoadingOverlayInitListener.configure(ui);
        assertEquals("Haetaan kotitöitäsi…", overlayTextSentBy(ui));

        ui.setLocale(Locale.ENGLISH);
        LoadingOverlayInitListener.configure(ui);
        assertEquals("Getting your chores…", overlayTextSentBy(ui));
    }

    @Test
    void cardSharesTheLandingPagesRestoreText() {
        UI ui = UI.getCurrent();
        ui.setLocale(Translations.SWEDISH);
        LoadingOverlayInitListener.configure(ui);
        String sent = overlayTextSentBy(ui); // dumping drains the queue, so read it once
        // The landing page's restore card reads the same key, so the two can never drift apart.
        assertEquals(ui.getTranslation(LoadingOverlayInitListener.TEXT_KEY), sent);
        assertEquals("Hämtar dina sysslor…", sent);
    }

    @Test
    void cardAppearsAtTheSecondStageAfterOneAndAHalfSeconds() {
        UI ui = UI.getCurrent();
        LoadingOverlayInitListener.configure(ui);
        assertEquals(1500, ui.getLoadingIndicatorConfiguration().getSecondDelay());
        assertTrue(ui.getLoadingIndicatorConfiguration().getFirstDelay() < 1500,
                "the thin bar still comes first");
    }

    @Test
    void everyNewUiIsConfiguredByTheInitListener() {
        // The init listener ran for this UI when the test environment created it.
        UI ui = UI.getCurrent();
        assertEquals(1500, ui.getLoadingIndicatorConfiguration().getSecondDelay());
    }
}
