package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ListItem;
import com.homechores.domain.ListItemRepository;
import com.homechores.domain.ListKind;
import com.homechores.domain.Member;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The shared grocery / to-do lists: anyone adds, anyone ticks, nothing becomes a chore. */
@SpringBootTest
@Transactional
class ListItemServiceTest {

    @Autowired ListItemService lists;
    @Autowired ListItemRepository repo;
    @Autowired ChoreService chores;
    @Autowired HomeState homeState;

    @Test
    void add_keepsWritingOrderPerList_andClipsLongLines() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();

        lists.add(code, ListKind.GROCERY, alex.getId(), "Milk");
        lists.add(code, ListKind.GROCERY, alex.getId(), "  Bread  ");
        lists.add(code, ListKind.TODO, alex.getId(), "Call the plumber");
        lists.add(code, ListKind.GROCERY, alex.getId(), "x".repeat(500));

        List<ListItem> groceries = lists.openItems(code, ListKind.GROCERY);
        assertEquals(List.of("Milk", "Bread", "x".repeat(160)),
                groceries.stream().map(ListItem::getText).toList(),
                "trimmed, in writing order, clipped to the limit");
        assertEquals(1, lists.openItems(code, ListKind.TODO).size(), "each list is its own");
        assertEquals(alex.getId(), groceries.get(0).getCreatedByMemberId());
    }

    @Test
    void add_ignoresBlankLines_andRefusesStrangers() {
        Member alex = chores.createHome("Listy", "Alex");
        Member other = chores.createHome("Elsewhere", "Sam");

        assertTrue(lists.add(alex.getHomeCode(), ListKind.GROCERY, alex.getId(), "   ").isEmpty());
        assertTrue(lists.openItems(alex.getHomeCode(), ListKind.GROCERY).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> lists.add(alex.getHomeCode(), ListKind.GROCERY, other.getId(), "Milk"),
                "a member of another home cannot write on this list");
    }

    @Test
    void tickingStampsWhoAndWhen_untickingClears_andNothingBecomesAChore() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        ListItem milk = lists.add(code, ListKind.GROCERY, alex.getId(), "Milk").orElseThrow();

        assertTrue(lists.setDone(milk.getId(), sam.getId(), true));
        ListItem ticked = lists.doneItems(code, ListKind.GROCERY).get(0);
        assertEquals("Milk", ticked.getText());
        assertEquals(sam.getId(), ticked.getDoneByMemberId(), "Sam ticked it");
        assertNotNull(ticked.getDoneAt());
        assertTrue(lists.openItems(code, ListKind.GROCERY).isEmpty());
        assertEquals(0, chores.completionCount(sam.getId()), "ticking a grocery is not a chore done");

        // Ticking again keeps the original ticker — two phones racing must not flip it.
        Instant firstTick = ticked.getDoneAt();
        assertTrue(lists.setDone(milk.getId(), alex.getId(), true));
        assertEquals(sam.getId(), repo.findById(milk.getId()).orElseThrow().getDoneByMemberId());
        assertEquals(firstTick, repo.findById(milk.getId()).orElseThrow().getDoneAt());

        assertTrue(lists.setDone(milk.getId(), alex.getId(), false));
        ListItem back = repo.findById(milk.getId()).orElseThrow();
        assertNull(back.getDoneAt());
        assertNull(back.getDoneByMemberId());
        assertEquals(1, lists.openItems(code, ListKind.GROCERY).size());

        assertFalse(lists.setDone(999_999L, alex.getId(), true), "no such line");
    }

    @Test
    void tickedLines_hideAfterADay_andThePurgeReclaimsThem() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        ListItem old = lists.add(code, ListKind.GROCERY, alex.getId(), "Old").orElseThrow();
        ListItem fresh = lists.add(code, ListKind.GROCERY, alex.getId(), "Fresh").orElseThrow();
        lists.setDone(old.getId(), alex.getId(), true);
        lists.setDone(fresh.getId(), alex.getId(), true);
        Instant now = Instant.now();
        old.setDoneAt(now.minus(Duration.ofHours(25)));
        fresh.setDoneAt(now.minus(Duration.ofHours(23)));
        repo.save(old);
        repo.save(fresh);

        assertEquals(List.of("Fresh"),
                lists.doneItems(code, ListKind.GROCERY).stream().map(ListItem::getText).toList(),
                "the read path already hides the day-old line");
        assertTrue(repo.findById(old.getId()).isPresent(), "but the row is still there");

        double quiet = homeState.revision(code).peek();
        assertEquals(1, lists.purgeStaleDone(now));
        assertTrue(repo.findById(old.getId()).isEmpty());
        assertTrue(repo.findById(fresh.getId()).isPresent(), "23h survives");
        assertEquals(quiet, homeState.revision(code).peek(),
                "the purge changes nothing on any screen, so it does not bump the home");
    }

    @Test
    void clearDone_emptiesOnlyThatListsTickedLines() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        ListItem milk = lists.add(code, ListKind.GROCERY, alex.getId(), "Milk").orElseThrow();
        lists.add(code, ListKind.GROCERY, alex.getId(), "Bread");
        ListItem call = lists.add(code, ListKind.TODO, alex.getId(), "Call").orElseThrow();
        lists.setDone(milk.getId(), alex.getId(), true);
        lists.setDone(call.getId(), alex.getId(), true);

        assertEquals(1, lists.clearDone(code, ListKind.GROCERY));

        assertTrue(lists.doneItems(code, ListKind.GROCERY).isEmpty());
        assertEquals(1, lists.openItems(code, ListKind.GROCERY).size(), "open bread stays");
        assertEquals(1, lists.doneItems(code, ListKind.TODO).size(), "the to-do list is untouched");
        assertEquals(0, lists.clearDone(code, ListKind.GROCERY), "nothing left to clear");
    }

    @Test
    void delete_isForAnyMemberOfTheHome_butNotForOutsiders() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        Member stranger = chores.createHome("Elsewhere", "Kim");
        ListItem milk = lists.add(code, ListKind.GROCERY, alex.getId(), "Milk").orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> lists.delete(milk.getId(), stranger.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> lists.setDone(milk.getId(), stranger.getId(), true));
        assertTrue(repo.findById(milk.getId()).isPresent());

        assertTrue(lists.delete(milk.getId(), sam.getId()), "Sam did not write it, but it is shared");
        assertTrue(repo.findById(milk.getId()).isEmpty());
        assertFalse(lists.delete(milk.getId(), sam.getId()), "already gone");
    }

    // ---- Dinner week ----------------------------------------------------------

    @Test
    void setDinner_createsThenReplacesTheSameRow_andBlankClears() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        LocalDate today = LocalDate.now();

        ListItem pasta = lists.setDinner(code, today, alex.getId(), "  Pasta  ").orElseThrow();
        assertEquals("Pasta", pasta.getText(), "trimmed");
        assertEquals(today, pasta.getDay());
        assertEquals(alex.getId(), pasta.getCreatedByMemberId());

        ListItem soup = lists.setDinner(code, today, sam.getId(), "Soup").orElseThrow();
        assertEquals(pasta.getId(), soup.getId(), "a day has one slot: the row is rewritten, not doubled");
        assertEquals(sam.getId(), soup.getCreatedByMemberId(), "and it says who set it last");
        assertEquals(1, lists.dinners(code, today, today).size());
        assertEquals("Soup", lists.dinners(code, today, today).get(today).getText());

        assertTrue(lists.setDinner(code, today, alex.getId(), "   ").isEmpty(), "blank clears");
        assertTrue(lists.dinners(code, today, today).isEmpty());
        assertTrue(repo.findById(pasta.getId()).isEmpty(), "the row is gone, not blanked");
        assertTrue(lists.setDinner(code, today, alex.getId(), "").isEmpty(), "clearing a clear day is a no-op");

        String longText = "x".repeat(500);
        assertEquals(160, lists.setDinner(code, today, alex.getId(), longText).orElseThrow().getText().length());
    }

    @Test
    void dinners_windowIsInclusive_andIgnoresDaysOutsideIt() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        LocalDate today = LocalDate.now();
        lists.setDinner(code, today.minusDays(1), alex.getId(), "Yesterday");
        lists.setDinner(code, today, alex.getId(), "Today");
        lists.setDinner(code, today.plusDays(6), alex.getId(), "Last of week");
        lists.setDinner(code, today.plusDays(7), alex.getId(), "Next week");

        Map<LocalDate, ListItem> week = lists.dinners(code, today, today.plusDays(6));

        assertEquals(List.of(today, today.plusDays(6)), List.copyOf(week.keySet()), "both ends inclusive, calendar order");
        assertTrue(lists.openItems(code, ListKind.TODO).isEmpty(), "dinners never leak into the other lists");
        assertTrue(lists.openItems(code, ListKind.GROCERY).isEmpty());
    }

    @Test
    void purge_reclaimsDinnersOlderThanAWeek_andKeepsRecentOnes() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        LocalDate today = LocalDate.now();
        ListItem old = lists.setDinner(code, today.minusDays(8), alex.getId(), "Old").orElseThrow();
        ListItem recent = lists.setDinner(code, today.minusDays(6), alex.getId(), "Recent").orElseThrow();
        ListItem planned = lists.setDinner(code, today.plusDays(3), alex.getId(), "Planned").orElseThrow();

        double quiet = homeState.revision(code).peek();
        assertEquals(1, lists.purgeStaleDone(Instant.now()));

        assertTrue(repo.findById(old.getId()).isEmpty(), "eight days gone: reclaimed");
        assertTrue(repo.findById(recent.getId()).isPresent(), "six days gone: still in the backup window");
        assertTrue(repo.findById(planned.getId()).isPresent());
        assertEquals(quiet, homeState.revision(code).peek(), "no screen changes, so no bump");
    }

    @Test
    void setDinner_refusesStrangers() {
        Member alex = chores.createHome("Listy", "Alex");
        Member stranger = chores.createHome("Elsewhere", "Kim");
        assertThrows(IllegalArgumentException.class,
                () -> lists.setDinner(alex.getHomeCode(), LocalDate.now(), stranger.getId(), "Pasta"));
    }

    /**
     * Not wrapped in the class's rollback transaction: {@link HomeState#bump} defers to
     * {@code afterCommit}, which never fires for a test that only ever rolls back (the same
     * reason {@code BookingExpiryTest} is not {@code @Transactional}).
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void everyEdit_bumpsTheHome_soOtherPhonesRedraw() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();

        double r0 = homeState.revision(code).peek();
        ListItem milk = lists.add(code, ListKind.GROCERY, alex.getId(), "Milk").orElseThrow();
        double r1 = homeState.revision(code).peek();
        assertTrue(r1 > r0, "add");
        lists.setDone(milk.getId(), alex.getId(), true);
        double r2 = homeState.revision(code).peek();
        assertTrue(r2 > r1, "tick");
        lists.clearDone(code, ListKind.GROCERY);
        double r3 = homeState.revision(code).peek();
        assertTrue(r3 > r2, "clear done");
        lists.add(code, ListKind.TODO, alex.getId(), "Call").ifPresent(
                c -> lists.delete(c.getId(), alex.getId()));
        double r4 = homeState.revision(code).peek();
        assertTrue(r4 > r3, "delete");

        LocalDate today = LocalDate.now();
        lists.setDinner(code, today, alex.getId(), "Pasta");
        double r5 = homeState.revision(code).peek();
        assertTrue(r5 > r4, "setting a dinner");
        lists.setDinner(code, today, alex.getId(), "Pasta");
        assertEquals(r5, homeState.revision(code).peek(), "saving the same text again changes nothing");
        lists.setDinner(code, today, alex.getId(), "");
        assertTrue(homeState.revision(code).peek() > r5, "clearing");
    }
}
