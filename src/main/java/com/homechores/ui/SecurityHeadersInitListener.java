package com.homechores.ui;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/**
 * Adds baseline security response headers to every request Vaadin serves.
 *
 * <p><strong>{@code X-Frame-Options: DENY}</strong> is the point: it stops the board being
 * embedded in an {@code <iframe>} on a page an attacker controls. Without it, a logged-in
 * family member could be lured into click-jacking — an invisible frame of the real,
 * authenticated board overlaid with bait, so a click lands on a destructive control (delete
 * home, approve a rejoin, redeem credits). Vaadin's CSRF key doesn't help there, because the
 * click is genuine; refusing to render in a frame is what defeats it. This must be a real
 * HTTP header — {@code X-Frame-Options} (and CSP {@code frame-ancestors}) are ignored when
 * set via a {@code <meta>} tag, so an {@code AppShellConfigurator} can't do it.
 *
 * <p>{@code X-Content-Type-Options: nosniff} rides along to stop MIME sniffing.
 *
 * <p>Setting these here (rather than only at the reverse proxy) keeps the defence with the
 * app, so it holds even if the app is ever reached without the proxy in front. Setting them
 * in both places is harmless — the proxy's value simply wins on the way out.
 */
@Component
public class SecurityHeadersInitListener implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.addRequestHandler((session, request, response) -> {
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("X-Content-Type-Options", "nosniff");
            return false; // headers only — let the normal handler chain serve the request
        });
    }
}
