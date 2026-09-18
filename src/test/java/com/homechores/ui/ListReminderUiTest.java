package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.homechores.domain.ListItem;
import com.homechores.domain.ListKind;
import com.homechores.domain.ListReminder;
import com.homechores.domain.ListReminderRepository;
import com.homechores.domain.Member;
import com.homechores.service.ChoreService;
import com.homechores.service.ListItemService;
import com.homechores.service.ListReminderService;
import com.homechores.service.WebPushSender;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.testbench.unit.SpringUIUnitTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The 🔔 on a shared-list line, and the dialog a fired reminder brings up.
 *
 * <p>Its own class for the reason {@link SnoozeUiTest} is: it needs a {@code WebPushSender} that
 * reports itself enabled, and every other UI test runs with push unconfigured.
 */
@SpringBootTest
class ListReminderUiTest extends SpringUIUnitTest {

    @Autowired ChoreService service;
    @Autowired ListItemService lists;
    @Autowired ListReminderService reminders;
    @Autowired ListReminderRepository repo;

    @MockitoBean WebPushSender sender;

    @BeforeEach
    void pushIsConfigured() {
        when(sender.isEnabled()).thenReturn(true);
        when(sender.publicKey()).thenReturn("test-key");
    }

    private boolean usable(Component c) {
        Component cur = c;
        while (cur != null) {
            if (!cur.isVisible()) {
                return false;
            }
            cur = cur.getParent().orElse(null);
        }
        return c.getUI().isPresent();
    }

    private Member signedIn(String name) {
        Member m = service.createHome("Listy", name);
        SessionContext.signIn(m.getId(), m.getHomeCode());
        navigate(HomeView.class);
        return m;
    }

    private void openLists(int kindIndex) {
        Tabs tabs = $(Tabs.class).first();
        Tab lists = tabs.getChildren().filter(Tab.class::isInstance).map(Tab.class::cast)
                .filter(t -> t.getElement().getTextRecursively().trim().startsWith("Lists"))
                .findFirst().orElseThrow();
        tabs.setSelectedTab(lists);
        $(Tabs.class).all().stream()
                .filter(t -> t.getClassNames().contains("list-kind-tabs") && usable(t))
                .findFirst().orElseThrow().setSelectedIndex(kindIndex);
    }

    private List<Button> bells() {
        return $(Button.class).all().stream()
                .filter(b -> b.getClassNames().contains("list-remind-btn") && usable(b))
                .toList();
    }

    private List<Div> rows() {
        return $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("list-item") && usable(d))
                .toList();
    }

    private List<ListReminderDialog> dialogs() {
        return $(ListReminderDialog.class).all().stream().filter(ListReminderDialog::isOpened).toList();
    }

    @Test
    void everyOpenLineCarriesABell_doneLinesAndDinnerDoNot() {
        Member alex = signedIn("Alex");
        String code = alex.getHomeCode();
        lists.add(code, ListKind.TODO, alex.getId(), "Call the plumber");
        lists.add(code, ListKind.TODO, alex.getId(), "Fix the fence");
        ListItem done = lists.add(code, ListKind.TODO, alex.getId(), "Buy paint").orElseThrow();
        lists.setDone(done.getId(), alex.getId(), true);
        lists.add(code, ListKind.GROCERY, alex.getId(), "Milk");

        openLists(1); // To-do
        assertEquals(3, rows().size(), "two open, one ticked");
        assertEquals(2, bells().size(), "a bell on each open line, none on the ticked one");

        openLists(0); // Groceries get it too
        assertEquals(1, bells().size());

        openLists(2); // Dinner is slots, not lines
        assertTrue(bells().isEmpty());
    }

    @Test
    void withoutVapidKeys_theBellIsNotOffered() {
        when(sender.isEnabled()).thenReturn(false);
        Member alex = signedIn("Alex");
        lists.add(alex.getHomeCode(), ListKind.TODO, alex.getId(), "Call the plumber");
        openLists(1);
        assertTrue(bells().isEmpty(), "a reminder that cannot be delivered should not be offered");
    }

    @Test
    void tappingTheBell_offersTheQuickOptionsAndAPicker() {
        Member alex = signedIn("Alex");
        lists.add(alex.getHomeCode(), ListKind.TODO, alex.getId(), "Call the plumber");
        openLists(1);

        test(bells().get(0)).click();

        assertEquals(1, dialogs().size(), "one dialog");
        long chips = $(Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("filter-chip") && usable(d))
                .count();
        assertEquals(ListReminderService.QuickOption.values().length + 1, chips,
                "every quick option plus 'pick a date & time', one tap each");
    }

    @Test
    void anArmedReminder_badgesItsLine_andOnlyThatOne() {
        Member alex = service.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        ListItem plumber = lists.add(code, ListKind.TODO, alex.getId(), "Call the plumber").orElseThrow();
        lists.add(code, ListKind.TODO, alex.getId(), "Fix the fence");
        reminders.schedule(plumber.getId(), alex.getId(), Instant.now().plus(Duration.ofHours(2)),
                Locale.ENGLISH);
        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);
        openLists(1);

        List<Span> badges = $(Span.class).all().stream()
                .filter(s -> s.getClassNames().contains("list-reminder") && usable(s))
                .toList();
        assertEquals(1, badges.size(), "exactly the line with the reminder");
        assertTrue(badges.get(0).getText().startsWith("⏰ "), "an absolute time, not the bare glyph");
        assertFalse(badges.get(0).getClassNames().contains("fired"));
        assertEquals(1, bells().stream().filter(b -> b.getClassNames().contains("armed")).count());
    }

    @Test
    void aFiredReminder_opensTheDialogOnArrival_andDismissClearsIt() {
        Member alex = service.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        ListItem plumber = lists.add(code, ListKind.TODO, alex.getId(), "Call the plumber").orElseThrow();
        reminders.schedule(plumber.getId(), alex.getId(), Instant.now().plus(Duration.ofHours(2)),
                Locale.ENGLISH);
        ListReminder r = repo.findByItemId(plumber.getId()).orElseThrow();
        r.setFiredAt(Instant.now());
        repo.save(r);

        SessionContext.signIn(alex.getId(), code);
        navigate(HomeView.class);

        List<ListReminderDialog> open = dialogs();
        assertEquals(1, open.size(), "the board opens straight onto the unanswered nudge");
        assertEquals(r.getId(), open.get(0).reminderId());
        assertTrue(open.get(0).getElement().getTextRecursively().contains("Call the plumber"));

        Button dismiss = $(Button.class).all().stream()
                .filter(b -> "Dismiss".equals(b.getText()) && usable(b))
                .findFirst().orElseThrow();
        test(dismiss).click();

        assertTrue(repo.findByItemId(plumber.getId()).isEmpty(), "dismissed for the whole home");
        assertTrue(dialogs().isEmpty());
    }

    @Test
    void noFiredReminder_noDialog() {
        Member alex = signedIn("Alex");
        lists.add(alex.getHomeCode(), ListKind.TODO, alex.getId(), "Call the plumber");
        assertTrue(dialogs().isEmpty());
    }
}
