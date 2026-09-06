package com.homechores.ui;

import com.homechores.service.HomeCleanupService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

/**
 * The brief user agreement every home creator and joiner ticks a checkbox for on the
 * landing page. Kept deliberately short and plain — a family should actually read it.
 * Like {@link PrivacyView}, the legal text is maintained in English only; the UI around
 * it (checkbox label, links) is translated.
 */
@Route("terms")
@PageTitle("User agreement — FlashChores")
public class TermsView extends VerticalLayout {

    public TermsView(HomeCleanupService cleanup) {
        addClassName("centered-page");
        // Width only — see PrivacyView: full height + centered content clips the top edge
        // on small screens.
        setWidthFull();

        Div card = new Div();
        card.addClassName("auth-card");
        card.getStyle().set("max-width", "640px").set("text-align", "left");

        H1 title = new H1("FlashChores user agreement");
        title.addClassName("brand-title");
        title.getStyle().set("font-size", "2rem");

        Paragraph intro = new Paragraph(
                "By creating or joining a home you agree to the following. It is short — "
                        + "please do read it.");
        intro.addClassName("brand-sub");
        card.add(title, intro);

        // The retention sentences are generated from the actual configured windows, so the
        // agreement can't quietly drift out of step with what the server really does.
        StringBuilder retention = new StringBuilder(
                "FlashChores is meant for ongoing family use, not for storage. Opening the "
                        + "app, joining, or logging a chore all count as using a home.");
        if (cleanup.getEmptyHomeHours() > 0) {
            retention.append(" A home that is never taken into use — nobody else joined and "
                    + "no chores were ever logged — is deleted automatically after about "
                    + Math.max(1, cleanup.getEmptyHomeHours() / 24) + " day(s)");
            if (cleanup.getAbandonedHomeDays() > 0) {
                retention.append(" (after " + cleanup.getAbandonedHomeDays()
                        + " days at the latest)");
            }
            retention.append(".");
        } else if (cleanup.getAbandonedHomeDays() > 0) {
            retention.append(" A home that is never taken into use — nobody else joined and "
                    + "no chores were ever logged — is deleted automatically after "
                    + cleanup.getAbandonedHomeDays() + " days.");
        }
        if (cleanup.getInactiveHomeDays() > 0) {
            retention.append(" A home that nobody in the family has opened or used for "
                    + cleanup.getInactiveHomeDays() + " days is also deleted — members, "
                    + "chores and history included. If your family comes back after a "
                    + "longer break and the home is gone, contact us (see the privacy "
                    + "page): removed homes are backed up right before deletion and can "
                    + "usually be restored.");
        } else {
            retention.append(" Homes a family actually uses are never deleted for being idle.");
        }
        card.add(section("1. Unused homes are removed", retention.toString()));

        card.add(sectionList("2. Don't enter real personal details", new String[]{
                "Do not use real first names or surnames anywhere in the app — not for the "
                        + "home, not for its members, not in chore names or notes.",
                "Use nicknames instead: “Our Nest”, “Mom”, “Dad”, "
                        + "“The Kid” — the app works exactly as well with them.",
                "Never enter sensitive personal data of any kind (addresses, birthdays, "
                        + "health details, and so on). The app never asks for any of it.",
        }));

        card.add(section("3. Security, on a best-effort basis",
                "We do our best to keep FlashChores safe and the data in it protected. Still, "
                        + "no online service can promise perfect security. By using the app you "
                        + "accept that the service is provided “as is”, and that we are "
                        + "not legally liable for damage caused by a third party gaining unlawful "
                        + "access to the data. This is also why rule 2 matters: a board that only "
                        + "ever held nicknames has nothing sensitive to lose."));

        card.add(section("4. Fair use",
                "The service is free for households. Don't use it to store unrelated data, "
                        + "flood it with automated sign-ups, or interfere with other homes."));

        Paragraph privacy = new Paragraph("How data is handled, stored and deleted is described "
                + "in the privacy notice.");
        Div privacyLine = new Div(privacy,
                new RouterLink("Privacy at FlashChores", PrivacyView.class));
        card.add(privacyLine);

        Paragraph updated = new Paragraph("Last updated: September 2026");
        updated.addClassName("feedback-hint");
        card.add(updated);

        RouterLink back = new RouterLink("← Back to FlashChores", LandingView.class);
        back.getStyle().set("font-weight", "600").set("margin-top", "var(--lumo-space-m)")
                .set("display", "inline-block");
        card.add(back);

        add(card);
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
