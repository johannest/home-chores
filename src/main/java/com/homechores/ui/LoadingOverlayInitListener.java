package com.homechores.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/**
 * Makes a long wait on the server look like the app loading rather than the app ignoring you.
 *
 * <p>Vaadin's loading indicator is a 4px bar at the very top of the viewport, which on a phone
 * sits under the camera island. When a tap takes seconds — typically a session that expired in
 * the member's pocket (see {@link SessionExpiryInitListener}) on a radio that is still waking
 * up — nothing visible happens, so the member taps again or gives up. From the indicator's
 * second stage on, the page is therefore covered by the same "Getting your chores…" card the
 * landing page shows while it restores the device's identity ({@code loading-overlay.js},
 * shown by {@code styles.css}). The card has to be client-side, because it appears precisely
 * while the server is the thing not answering; this listener fixes the moment it appears and
 * gives it its text, in the UI's language.
 */
@Component
public class LoadingOverlayInitListener implements VaadinServiceInitListener {

    /**
     * How long a request may take before the card covers the page — Vaadin's second stage.
     * Short enough that an expired session's first tap is answered by it, long enough that an
     * ordinary board redraw (tens of milliseconds plus a mobile round trip) never flashes it.
     */
    static final int OVERLAY_DELAY_MS = 1500;

    static final String TEXT_KEY = "app.loading";

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(init -> configure(init.getUI()));
    }

    /** Sets the stage the card appears at and hands it its text in the UI's language. */
    static void configure(UI ui) {
        ui.getLoadingIndicatorConfiguration().setSecondDelay(OVERLAY_DELAY_MS);
        ui.getPage().executeJs(
                "const el = document.getElementById('loading-overlay-text');"
                + " if (el) el.textContent = $0;",
                ui.getTranslation(TEXT_KEY));
    }
}
