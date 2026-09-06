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
                "Only if you turn on the optional chore reminder: your chosen reminder time, "
                        + "your device's timezone, and the push-notification address and keys your "
                        + "browser issues for it. These are deleted when you turn the reminder off, "
                        + "when the member is removed, or when the home is deleted — and they are "
                        + "never included in backup exports.",
                "Only if you ask to be reminded about one particular chore later (\"remind me in "
                        + "two hours\"): which chore, and when. That note is deleted the moment the "
                        + "reminder is sent, and also if the chore, the member or the home is "
                        + "deleted or you turn notifications off. It is never included in backup "
                        + "exports either.",
        }));
        card.add(section("What we do NOT store",
                "No email addresses, phone numbers, passwords, home addresses, location, or payment "
                        + "details. No analytics or advertising cookies, and no third-party trackers."));

        // Honesty about the layer below the app: FlashChores logs no client data itself
        // (server.tomcat.accesslog.enabled=false, no request-level logging), but the
        // hosting platform keeps the ordinary web-server records any site needs to stay
        // up and secure. Saying so here keeps the "what we do NOT store" claim above true
        // rather than absolute.
        card.add(section("Server logs and hosting statistics",
                "FlashChores itself stores no client information such as IP addresses, and it "
                        + "keeps no request logs of its own. The hosting service the site runs on "
                        + "does keep the normal web-server records that any website needs — access "
                        + "logs and aggregate visitor statistics (AWStats-style: hit counts, "
                        + "referrers, browsers, error rates) — which are used only to keep the site "
                        + "working and to spot abuse or attacks. These records live with the "
                        + "hosting infrastructure, are kept only for a short period, and are never "
                        + "correlated with a member, a home, a home code or anything you do in the "
                        + "app: we do not link them to the data described above."));

        card.add(section("Cookies",
                "The app uses a single strictly-necessary session cookie (JSESSIONID) to keep you "
                        + "signed into your home. It is required for the service to work and is not used "
                        + "for tracking, so no cookie-consent banner is needed."));

        // The retention sentences are generated from the actual configured windows, so this
        // notice can't quietly drift out of step with what the server really does.
        String retention = "Data is stored in a self-hosted database on flashchores.com.";
        if (cleanup.getEmptyHomeHours() > 0 || cleanup.getAbandonedHomeDays() > 0) {
            retention += " A home that was created but never actually used — no chores ever "
                    + "logged and no one else invited — is removed automatically";
            if (cleanup.getEmptyHomeHours() > 0) {
                retention += " after about " + Math.max(1, cleanup.getEmptyHomeHours() / 24)
                        + " day(s)";
                if (cleanup.getAbandonedHomeDays() > 0) {
                    retention += " (and after " + cleanup.getAbandonedHomeDays()
                            + " days at the latest)";
                }
            } else {
                retention += " after " + cleanup.getAbandonedHomeDays() + " days";
            }
            retention += ", so abandoned sign-ups don't linger.";
        }
        if (cleanup.getInactiveHomeDays() > 0) {
            retention += " A home that nobody has opened or used for "
                    + cleanup.getInactiveHomeDays() + " days is deleted too, chore history "
                    + "included. Right before that deletion the home is exported to an "
                    + "operator-held backup, so a family returning from a long break can ask "
                    + "for it to be restored (contact address below).";
        } else {
            retention += " Beyond that, a home and its chore history are kept until an admin "
                    + "deletes them (see below) — we do not delete a family's history for "
                    + "being idle.";
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

        RouterLink terms = new RouterLink("User agreement", TermsView.class);
        terms.getStyle().set("display", "inline-block");
        card.add(terms);

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
