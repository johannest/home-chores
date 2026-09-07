package com.homechores.ui;

import com.homechores.service.HomeCleanupService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import java.util.ArrayList;
import java.util.List;

/**
 * The brief user agreement every home creator and joiner ticks a checkbox for on the
 * landing page. Kept deliberately short and plain — a family should actually read it.
 * The text lives in the message bundles ({@code terms.*}) and is shown in the visitor's
 * language; the English bundle is the authoritative wording. {@link PrivacyView} is still
 * English only.
 */
@Route("terms")
public class TermsView extends VerticalLayout implements HasDynamicTitle {

    public TermsView(HomeCleanupService cleanup) {
        addClassName("centered-page");
        // Width only — see PrivacyView: full height + centered content clips the top edge
        // on small screens.
        setWidthFull();

        Div card = new Div();
        card.addClassName("auth-card");
        card.getStyle().set("max-width", "640px").set("text-align", "left");

        H1 title = new H1(T.tr("terms.title"));
        title.addClassName("brand-title");
        title.getStyle().set("font-size", "2rem");

        Paragraph intro = new Paragraph(T.tr("terms.intro"));
        intro.addClassName("brand-sub");
        card.add(title, intro);

        card.add(section(T.tr("terms.s1.title"), retention(cleanup)));

        card.add(sectionList(T.tr("terms.s2.title"), new String[]{
                T.tr("terms.s2.item1"), T.tr("terms.s2.item2"), T.tr("terms.s2.item3"),
        }));
        card.add(section(T.tr("terms.s3.title"), T.tr("terms.s3.body")));
        card.add(section(T.tr("terms.s4.title"), T.tr("terms.s4.body")));

        Div privacyLine = new Div(new Paragraph(T.tr("terms.privacy.text")),
                new RouterLink(T.tr("terms.privacy.link"), PrivacyView.class));
        card.add(privacyLine);

        Paragraph updated = new Paragraph(T.tr("terms.updated"));
        updated.addClassName("feedback-hint");
        card.add(updated);

        RouterLink back = new RouterLink(T.tr("terms.back"), LandingView.class);
        back.getStyle().set("font-weight", "600").set("margin-top", "var(--lumo-space-m)")
                .set("display", "inline-block");
        card.add(back);

        add(card);
    }

    @Override
    public String getPageTitle() {
        return T.tr("terms.pageTitle");
    }

    /**
     * The retention sentences are generated from the actual configured windows, so the
     * agreement can't quietly drift out of step with what the server really does.
     */
    private static String retention(HomeCleanupService cleanup) {
        List<String> sentences = new ArrayList<>();
        sentences.add(T.tr("terms.retention.base"));
        if (cleanup.getEmptyHomeHours() > 0) {
            String latest = cleanup.getAbandonedHomeDays() > 0
                    ? " " + T.tr("terms.retention.latest", cleanup.getAbandonedHomeDays())
                    : "";
            sentences.add(T.tr("terms.retention.neverUsed",
                    Math.max(1, cleanup.getEmptyHomeHours() / 24), latest));
        } else if (cleanup.getAbandonedHomeDays() > 0) {
            sentences.add(T.tr("terms.retention.neverUsedDays", cleanup.getAbandonedHomeDays()));
        }
        sentences.add(cleanup.getInactiveHomeDays() > 0
                ? T.tr("terms.retention.inactive", cleanup.getInactiveHomeDays())
                : T.tr("terms.retention.neverIdle"));
        return String.join(" ", sentences);
    }

    private Div section(String heading, String body) {
        Div d = new Div();
        H2 h = new H2(heading);
        h.getStyle().set("font-size", "1.15rem").set("margin-bottom", "4px");
        Paragraph p = new Paragraph(body);
        p.getStyle().set("margin-top", "0");
        d.add(h, p);
        return d;
    }

    private Div sectionList(String heading, String[] items) {
        Div d = new Div();
        H2 h = new H2(heading);
        h.getStyle().set("font-size", "1.15rem").set("margin-bottom", "4px");
        UnorderedList ul = new UnorderedList();
        for (String item : items) {
            ul.add(new ListItem(item));
        }
        d.add(h, ul);
        return d;
    }
}
