package com.homechores.ui;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.communication.IndexHtmlResponse;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-route SEO head tags. The same index.html shell is served for every route, so the
 * bits that must differ per URL — canonical, description, og:title/og:url — are injected
 * here, and everything else stays static in index.html. Vaadin Flow has no crawler
 * prerendering; this listener plus the crawlable {@code #seo-content} block in index.html
 * is the sanctioned approach (see the Vaadin docs on modifying the bootstrap page).
 *
 * <p>Only the three public routes are indexable. {@code /home} (and anything else) gets
 * {@code noindex}: a board is per-session and meaningless to a search engine, and a
 * leaked link should not put a family's board URL in an index.
 *
 * <p>{@code homechores.base-url} keeps staging/dev from emitting production canonicals —
 * with it unset, no canonical/JSON-LD is emitted at all.
 */
@Component
public class SeoIndexHtmlListener implements VaadinServiceInitListener {

    private final String baseUrl;

    public SeoIndexHtmlListener(@Value("${homechores.base-url:}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        if (baseUrl.isBlank()) {
            return;
        }
        event.addIndexHtmlRequestListener(this::modify);
    }

    private void modify(IndexHtmlResponse response) {
        String path = response.getVaadinRequest().getPathInfo();
        String normalized = path == null || path.isBlank() ? "/" : path;
        Element head = response.getDocument().head();
        switch (normalized) {
            case "/" -> {
                describe(head, baseUrl + "/", "FlashChores",
                        "FlashChores is a free, effortless chore tracker for families: "
                                + "tap a chore when you do it, keep it fair, celebrate together. "
                                + "Works on any phone, no accounts, no ads.");
                head.appendElement("script")
                        .attr("type", "application/ld+json")
                        .text(jsonLd());
            }
            case "/privacy" -> describe(head, baseUrl + "/privacy",
                    "Privacy — FlashChores",
                    "What little data FlashChores stores, where it lives, how long it is "
                            + "kept, and how to see, export or erase it.");
            case "/terms" -> describe(head, baseUrl + "/terms",
                    "User agreement — FlashChores",
                    "The short FlashChores user agreement: unused homes are removed, use "
                            + "nicknames instead of real names, and security is best-effort.");
            default -> head.appendElement("meta")
                    .attr("name", "robots").attr("content", "noindex");
        }
    }

    private void describe(Element head, String url, String title, String description) {
        head.appendElement("link").attr("rel", "canonical").attr("href", url);
        head.appendElement("meta").attr("name", "description").attr("content", description);
        head.appendElement("meta").attr("property", "og:title").attr("content", title);
        head.appendElement("meta").attr("property", "og:url").attr("content", url);
        head.appendElement("meta").attr("property", "og:description").attr("content", description);
    }

    private String jsonLd() {
        return """
                {
                  "@context": "https://schema.org",
                  "@type": "WebApplication",
                  "name": "FlashChores",
                  "url": "%s/",
                  "applicationCategory": "LifestyleApplication",
                  "operatingSystem": "Any",
                  "offers": { "@type": "Offer", "price": "0", "priceCurrency": "EUR" },
                  "inLanguage": ["en", "fi", "sv"],
                  "description": "Effortless chore tracking for families. Tap a chore when you do it, keep it fair, celebrate together."
                }""".formatted(baseUrl);
    }
}
