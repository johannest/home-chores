package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Member;
import com.homechores.service.ChoreReminderService;
import com.homechores.service.ChoreService;
import com.homechores.service.WebPushSender;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.testbench.unit.SpringUIUnitTest;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The ⏰ affordance on a chore card.
 *
 * <p>Its own class rather than a few cases in {@code HomeUiTest}, because it needs a
 * {@code WebPushSender} that reports itself enabled — the board deliberately hides every push
 * control when VAPID is unconfigured, which is exactly the state {@code HomeUiTest} runs in.
 */
@SpringBootTest
class SnoozeUiTest extends SpringUIUnitTest {

    @Autowired ChoreService service;
    @Autowired ChoreReminderService snoozes;

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

    private List<Span> snoozeChips() {
        return $(Span.class).all().stream()
                .filter(s -> s.getClassNames().contains("snooze-chip") && usable(s))
                .toList();
    }

    private Member signedInAdmin() {
        Member alex = service.createHome("Snoozes", "Alex");
        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);
        return alex;
    }

    @Test
    void everyChoreCardCarriesTheAffordance_butTheExtraTilesDoNot() {
        Member alex = signedInAdmin();
        long chores = service.tasksOf(alex.getHomeCode()).size();

        // The board opens on the TODAY lens, so not every chore is on screen — but every card
        // that IS a chore has a chip, and the 🙋 / ＋ tiles (which are not chores) have none.
        long cards = $(com.vaadin.flow.component.html.Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("task-card") && usable(d))
                .filter(d -> !d.getClassNames().contains("help-card")
                        && !d.getClassNames().contains("add-card"))
                .count();
        assertEquals(cards, snoozeChips().size(), "one chip per chore card, and none on the tiles");
        assertTrue(cards > 0 && cards <= chores);
    }

    @Test
    void withoutVapidKeys_theAffordanceIsNotOffered() {
        when(sender.isEnabled()).thenReturn(false);
        signedInAdmin();
        assertTrue(snoozeChips().isEmpty(),
                "a reminder that cannot be delivered should not be offered");
    }

    @Test
    void tappingTheChip_opensTheDialogOfferingSixOffsets() {
        signedInAdmin();
        Span chip = snoozeChips().get(0);
        ComponentUtil.fireEvent(chip, new ClickEvent<>(chip));

        SnoozeDialog dialog = $(SnoozeDialog.class).all().stream()
                .findFirst().orElseThrow(() -> new AssertionError("No snooze dialog"));
        long offsets = $(com.vaadin.flow.component.html.Div.class).all().stream()
                .filter(d -> d.getClassNames().contains("filter-chip") && usable(d))
                .count();
        assertTrue(dialog.isOpened());
        assertTrue(offsets >= SnoozeDialog.Offset.values().length,
                "every offset is one tap away; had " + offsets);
    }

    @Test
    void anArmedReminder_badgesItsOwnCard_andOnlyThatOne() {
        // Armed before the board is first rendered, on purpose: arming deliberately does not bump
        // HomeState (a private nudge must not redraw the whole family's screens), so navigating to
        // an already-open view would not pick it up.
        Member alex = service.createHome("Snoozes", "Alex");
        ChoreTask task = service.tasksOf(alex.getHomeCode()).stream()
                .filter(t -> t.getIntervalDays() == 0).findFirst().orElseThrow();
        snoozes.schedule(alex.getId(), task.getId(), Duration.ofHours(2), Locale.ENGLISH);

        SessionContext.signIn(alex.getId(), alex.getHomeCode());
        navigate(HomeView.class);

        List<Span> armed = snoozeChips().stream()
                .filter(s -> s.getClassNames().contains("armed")).toList();
        assertEquals(1, armed.size(), "exactly the chore that was snoozed");
        assertFalse(armed.get(0).getText().equals("\u23f0"),
                "an armed chip shows when it fires, not the bare glyph");
        assertTrue(snoozeChips().size() > 1, "and the other cards keep the dormant glyph");
    }
}
