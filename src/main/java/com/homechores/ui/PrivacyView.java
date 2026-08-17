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
 * Public privacy notice. Deliberately plain and honest about the little data the app
 * holds. Replace the [bracketed] placeholders with the operator's real details before
 * going public.
 */
@Route("privacy")
@PageTitle("Privacy — FlashChores")
public class PrivacyView extends VerticalLayout {

    public PrivacyView(HomeCleanupService cleanup) {
        addClassName("centered-page");
        // Width only: a fixed 100% height + centered flex content would push the top of a
        // long page above the viewport where it can't be scrolled to. The .centered-page
        // class keeps a min-height so short content still centers vertically.
        setWidthFull();

        Div card = new Div();
        card.addClassName("auth-card");
        card.getStyle().set("max-width", "640px").set("text-align", "left");

        H1 title = new H1("Privacy at FlashChores");
        title.addClassName("brand-title");
        title.getStyle().set("font-size", "2rem");

        Paragraph intro = new Paragraph(
                "FlashChores is a small household chore tracker. It collects as little as "
                        + "possible data, shows no ads, and does not track you across the web.");
        intro.addClassName("brand-sub");

        card.add(title, intro);
        card.add(section("Who runs this service",
                "This instance of FlashChores is operated by Johannes Tuikkala. "
                        + "For any privacy question or request, contact support@flashchores.com."));

        card.add(sectionList("What data we store", new String[]{
                "The names you type for your home and its members (a nickname is fine — see the tip on the sign-in screen).",
                "Which chores were done, by whom, and when, plus any optional feedback (😖 / 🙂 / 😍).",
                "A short home code and a 4-digit admin PIN used to manage the home.",
        }));
        card.add(section("What we do NOT store",
                "No email addresses, phone numbers, passwords, home addresses, location, or payment "
                        + "details. No analytics or advertising cookies, and no third-party trackers."));

        card.add(section("Cookies",
                "The app uses a single strictly-necessary session cookie (JSESSIONID) to keep you "
                        + "signed into your home. It is required for the service to work and is not used "
                        + "for tracking, so no cookie-consent banner is needed."));

        // The retention sentence is generated from the actual configured window, so this
        // notice can't quietly drift out of step with what the server really does.
        String retention = "Data is stored in a self-hosted database on flashchores.com. A home and "
                + "its chore history are kept until an admin deletes them (see below) — we do not "
                + "delete a family's history for being idle.";
        if (cleanup.isEnabled()) {
            retention += " The one exception: a home that was created but never actually used — no "
                    + "chores ever logged and no one else invited — is removed automatically after "
                    + cleanup.getAbandonedHomeDays() + " days, so abandoned sign-ups don't linger.";
        }
        card.add(section("Where it's stored & how long", retention));

        card.add(sectionList("Your rights", new String[]{
                "See and correct your data — a home admin can rename members and edit chores.",
                "Erase your data yourself, at any time — a home admin can remove a single member "
                        + "(with all their history) under Admin → Members, or delete the entire home "
                        + "and everything in it under Admin → Danger zone. Deletion is immediate and "
                        + "permanent; no copy is kept.",
                "Export your data — a home admin can download a full JSON backup of the home under "
                        + "Admin → Backup & restore. Do this before deleting if you want to keep it.",
                "Prefer us to do it? Email support@flashchores.com with your home code and admin "
                        + "PIN. We need both: they are the only way to tell that the request really "
                        + "comes from your household, since we hold no email addresses or accounts "
                        + "to check it against. If you've lost the PIN, write from the address you "
                        + "contacted us from before and we'll agree another way to confirm.",
                "For anything else, contact support@flashchores.com and we'll help.",
        }));

        card.add(section("Children",
                "FlashChores is meant to be set up by an adult, who adds the family members and is "
                        + "responsible for the information entered about them. Please use nicknames for "
                        + "children rather than full real names."));

        card.add(section("Security",
                "Please use FlashChores over an HTTPS connection. The home code and admin PIN are "
                        + "lightweight household conveniences, not strong secrets — share them only with "
                        + "your own household."));

        Paragraph updated = new Paragraph("Last updated: August 2026");
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
