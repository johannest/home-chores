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
import com.homechores.domain.ListReminder;
import com.homechores.domain.Member;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

        lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "Milk");
        lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "  Bread  ");
        lists.add(code, ListKind.TODO, Author.member(alex.getId()), "Call the plumber");
        lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "x".repeat(500));

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

        assertTrue(lists.add(alex.getHomeCode(), ListKind.GROCERY, Author.member(alex.getId()), "   ").isEmpty());
        assertTrue(lists.openItems(alex.getHomeCode(), ListKind.GROCERY).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> lists.add(alex.getHomeCode(), ListKind.GROCERY, Author.member(other.getId()), "Milk"),
                "a member of another home cannot write on this list");
    }

    @Test
    void tickingStampsWhoAndWhen_untickingClears_andNothingBecomesAChore() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        ListItem milk = lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "Milk").orElseThrow();

        assertTrue(lists.setDone(milk.getId(), Author.member(sam.getId()), true));
        ListItem ticked = lists.doneItems(code, ListKind.GROCERY).get(0);
        assertEquals("Milk", ticked.getText());
        assertEquals(sam.getId(), ticked.getDoneByMemberId(), "Sam ticked it");
        assertNotNull(ticked.getDoneAt());
        assertTrue(lists.openItems(code, ListKind.GROCERY).isEmpty());
        assertEquals(0, chores.completionCount(sam.getId()), "ticking a grocery is not a chore done");

        // Ticking again keeps the original ticker — two phones racing must not flip it.
        Instant firstTick = ticked.getDoneAt();
        assertTrue(lists.setDone(milk.getId(), Author.member(alex.getId()), true));
        assertEquals(sam.getId(), repo.findById(milk.getId()).orElseThrow().getDoneByMemberId());
        assertEquals(firstTick, repo.findById(milk.getId()).orElseThrow().getDoneAt());

        assertTrue(lists.setDone(milk.getId(), Author.member(alex.getId()), false));
        ListItem back = repo.findById(milk.getId()).orElseThrow();
        assertNull(back.getDoneAt());
        assertNull(back.getDoneByMemberId());
        assertEquals(1, lists.openItems(code, ListKind.GROCERY).size());

        assertFalse(lists.setDone(999_999L, Author.member(alex.getId()), true), "no such line");
    }

    @Test
    void tickedLines_hideAfterADay_andThePurgeReclaimsThem() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        ListItem old = lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "Old").orElseThrow();
        ListItem fresh = lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "Fresh").orElseThrow();
        lists.setDone(old.getId(), Author.member(alex.getId()), true);
        lists.setDone(fresh.getId(), Author.member(alex.getId()), true);
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
        ListItem milk = lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "Milk").orElseThrow();
        lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "Bread");
        ListItem call = lists.add(code, ListKind.TODO, Author.member(alex.getId()), "Call").orElseThrow();
        lists.setDone(milk.getId(), Author.member(alex.getId()), true);
        lists.setDone(call.getId(), Author.member(alex.getId()), true);

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
        ListItem milk = lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "Milk").orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> lists.delete(milk.getId(), Author.member(stranger.getId())));
        assertThrows(IllegalArgumentException.class,
                () -> lists.setDone(milk.getId(), Author.member(stranger.getId()), true));
        assertTrue(repo.findById(milk.getId()).isPresent());

        assertTrue(lists.delete(milk.getId(), Author.member(sam.getId())), "Sam did not write it, but it is shared");
        assertTrue(repo.findById(milk.getId()).isEmpty());
        assertFalse(lists.delete(milk.getId(), Author.member(sam.getId())), "already gone");
    }

    // ---- Dinner week ----------------------------------------------------------

    @Test
    void setDinner_createsThenReplacesTheSameRow_andBlankClears() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        LocalDate today = LocalDate.now();

        ListItem pasta = lists.setDinner(code, today, Author.member(alex.getId()), "  Pasta  ").orElseThrow();
        assertEquals("Pasta", pasta.getText(), "trimmed");
        assertEquals(today, pasta.getDay());
        assertEquals(alex.getId(), pasta.getCreatedByMemberId());

        ListItem soup = lists.setDinner(code, today, Author.member(sam.getId()), "Soup").orElseThrow();
        assertEquals(pasta.getId(), soup.getId(), "a day has one slot: the row is rewritten, not doubled");
        assertEquals(sam.getId(), soup.getCreatedByMemberId(), "and it says who set it last");
        assertEquals(1, lists.dinners(code, today, today).size());
        assertEquals("Soup", lists.dinners(code, today, today).get(today).getText());

        assertTrue(lists.setDinner(code, today, Author.member(alex.getId()), "   ").isEmpty(), "blank clears");
        assertTrue(lists.dinners(code, today, today).isEmpty());
        assertTrue(repo.findById(pasta.getId()).isEmpty(), "the row is gone, not blanked");
        assertTrue(lists.setDinner(code, today, Author.member(alex.getId()), "").isEmpty(), "clearing a clear day is a no-op");

        String longText = "x".repeat(500);
        assertEquals(160, lists.setDinner(code, today, Author.member(alex.getId()), longText).orElseThrow().getText().length());
    }

    @Test
    void dinners_windowIsInclusive_andIgnoresDaysOutsideIt() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        LocalDate today = LocalDate.now();
        lists.setDinner(code, today.minusDays(1), Author.member(alex.getId()), "Yesterday");
        lists.setDinner(code, today, Author.member(alex.getId()), "Today");
        lists.setDinner(code, today.plusDays(7), Author.member(alex.getId()), "Same weekday next week");
        lists.setDinner(code, today.plusDays(8), Author.member(alex.getId()), "Beyond the window");

        Map<LocalDate, ListItem> week = lists.dinners(code, today, today.plusDays(7));

        assertEquals(List.of(today, today.plusDays(7)), List.copyOf(week.keySet()), "both ends inclusive, calendar order");
        assertTrue(lists.openItems(code, ListKind.TODO).isEmpty(), "dinners never leak into the other lists");
        assertTrue(lists.openItems(code, ListKind.GROCERY).isEmpty());
    }

    @Test
    void purge_reclaimsDinnersOlderThanAWeek_andKeepsRecentOnes() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        LocalDate today = LocalDate.now();
        ListItem old = lists.setDinner(code, today.minusDays(8), Author.member(alex.getId()), "Old").orElseThrow();
        ListItem recent = lists.setDinner(code, today.minusDays(6), Author.member(alex.getId()), "Recent").orElseThrow();
        ListItem planned = lists.setDinner(code, today.plusDays(3), Author.member(alex.getId()), "Planned").orElseThrow();

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
                () -> lists.setDinner(alex.getHomeCode(), LocalDate.now(), Author.member(stranger.getId()), "Pasta"));
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
        ListItem milk = lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "Milk").orElseThrow();
        double r1 = homeState.revision(code).peek();
        assertTrue(r1 > r0, "add");
        lists.setDone(milk.getId(), Author.member(alex.getId()), true);
        double r2 = homeState.revision(code).peek();
        assertTrue(r2 > r1, "tick");
        lists.clearDone(code, ListKind.GROCERY);
        double r3 = homeState.revision(code).peek();
        assertTrue(r3 > r2, "clear done");
        lists.add(code, ListKind.TODO, Author.member(alex.getId()), "Call").ifPresent(
                c -> lists.delete(c.getId(), Author.member(alex.getId())));
        double r4 = homeState.revision(code).peek();
        assertTrue(r4 > r3, "delete");

        LocalDate today = LocalDate.now();
        lists.setDinner(code, today, Author.member(alex.getId()), "Pasta");
        double r5 = homeState.revision(code).peek();
        assertTrue(r5 > r4, "setting a dinner");
        lists.setDinner(code, today, Author.member(alex.getId()), "Pasta");
        assertEquals(r5, homeState.revision(code).peek(), "saving the same text again changes nothing");
        lists.setDinner(code, today, Author.member(alex.getId()), "");
        assertTrue(homeState.revision(code).peek() > r5, "clearing");
    }

    // ---- arranging a list ---------------------------------------------------------

    private static List<String> texts(List<ListItem> lines) {
        return lines.stream().map(ListItem::getText).toList();
    }

    /** A drop lands before its neighbour or at the end, a new line still goes last, and a drop
     *  that changes nothing says so. */
    @Test
    void placeItem_reordersTheOpenLines_andANewLineStillGoesLast() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        ListItem milk = lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "Milk").orElseThrow();
        ListItem bread = lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "Bread").orElseThrow();
        ListItem eggs = lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "Eggs").orElseThrow();

        assertTrue(lists.placeItem(code, Author.member(alex.getId()), eggs.getId(), milk.getId()));
        assertEquals(List.of("Eggs", "Milk", "Bread"), texts(lists.openItems(code, ListKind.GROCERY)),
                "dropped before the milk");
        assertTrue(lists.placeItem(code, Author.member(alex.getId()), milk.getId(), null));
        assertEquals(List.of("Eggs", "Bread", "Milk"), texts(lists.openItems(code, ListKind.GROCERY)),
                "no neighbour means the end");

        assertFalse(lists.placeItem(code, Author.member(alex.getId()), milk.getId(), null), "let go where it was");
        assertFalse(lists.placeItem(code, Author.member(alex.getId()), bread.getId(), bread.getId()), "before itself");

        lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "Butter");
        assertEquals(List.of("Eggs", "Bread", "Milk", "Butter"), texts(lists.openItems(code, ListKind.GROCERY)),
                "a new line goes after the arranged ones, not to the top at 0");

        lists.setDone(eggs.getId(), Author.member(alex.getId()), true);
        lists.setDone(eggs.getId(), Author.member(alex.getId()), false);
        assertEquals(List.of("Eggs", "Bread", "Milk", "Butter"), texts(lists.openItems(code, ListKind.GROCERY)),
                "ticking and unticking keeps a line's place");
    }

    /** Every id in a drop comes from the browser: another home's, another list's, a ticked
     *  line's and a dinner slot's all write nothing, and a stranger is refused outright. */
    @Test
    void placeItem_refusesStrangers_otherHomesLines_otherLists_tickedLines_andDinners() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        ListItem milk = lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "Milk").orElseThrow();
        ListItem bread = lists.add(code, ListKind.GROCERY, Author.member(alex.getId()), "Bread").orElseThrow();
        ListItem plumber = lists.add(code, ListKind.TODO, Author.member(alex.getId()), "Call the plumber").orElseThrow();
        ListItem pasta = lists.setDinner(code, LocalDate.now(), Author.member(alex.getId()), "Pasta").orElseThrow();
        Member kim = chores.createHome("Elsewhere", "Kim");
        ListItem theirs = lists.add(kim.getHomeCode(), ListKind.GROCERY, Author.member(kim.getId()), "Theirs").orElseThrow();
        List<String> before = texts(lists.openItems(code, ListKind.GROCERY));

        assertThrows(IllegalArgumentException.class,
                () -> lists.placeItem(code, Author.member(kim.getId()), bread.getId(), milk.getId()), "not in this home");
        assertFalse(lists.placeItem(code, Author.member(alex.getId()), theirs.getId(), milk.getId()), "another home's line");
        assertFalse(lists.placeItem(code, Author.member(alex.getId()), bread.getId(), theirs.getId()),
                "another home's line as the neighbour");
        assertFalse(lists.placeItem(code, Author.member(alex.getId()), bread.getId(), plumber.getId()),
                "a neighbour on another list");
        assertFalse(lists.placeItem(code, Author.member(alex.getId()), pasta.getId(), null), "a dinner slot");
        lists.setDone(bread.getId(), Author.member(alex.getId()), true);
        assertFalse(lists.placeItem(code, Author.member(alex.getId()), bread.getId(), milk.getId()), "a ticked line");
        lists.setDone(bread.getId(), Author.member(alex.getId()), false);

        assertEquals(before, texts(lists.openItems(code, ListKind.GROCERY)), "nothing moved");
        assertEquals(List.of("Theirs"), texts(lists.openItems(kim.getHomeCode(), ListKind.GROCERY)));
        assertEquals(List.of("Call the plumber"), texts(lists.openItems(code, ListKind.TODO)));
    }

    /** Due reminders lead, far-off ones trail, and the plain lines keep the family's order
     *  between them — judged against the viewer's day. */
    @Test
    void displayOrder_putsDueRemindersFirst_farOffOnesLast_andTheRestInBetween() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        ListItem a = lists.add(code, ListKind.TODO, Author.member(alex.getId()), "A next month").orElseThrow();
        ListItem b = lists.add(code, ListKind.TODO, Author.member(alex.getId()), "B plain").orElseThrow();
        ListItem c = lists.add(code, ListKind.TODO, Author.member(alex.getId()), "C this afternoon").orElseThrow();
        ListItem d = lists.add(code, ListKind.TODO, Author.member(alex.getId()), "D plain").orElseThrow();
        ListItem e = lists.add(code, ListKind.TODO, Author.member(alex.getId()), "E fired yesterday").orElseThrow();
        ListItem f = lists.add(code, ListKind.TODO, Author.member(alex.getId()), "F tomorrow 9:00").orElseThrow();

        ZoneId utc = ZoneId.of("UTC");
        LocalDate today = LocalDate.of(2026, 10, 16);
        Instant noon = today.atTime(12, 0).atZone(utc).toInstant();
        ListReminder fired = new ListReminder(code, e.getId(), noon.minus(Duration.ofDays(1)), alex.getId());
        fired.setFiredAt(noon.minus(Duration.ofDays(1)));
        Map<Long, ListReminder> reminders = Map.of(
                a.getId(), new ListReminder(code, a.getId(), noon.plus(Duration.ofDays(30)), alex.getId()),
                c.getId(), new ListReminder(code, c.getId(), noon.plus(Duration.ofHours(3)), alex.getId()),
                e.getId(), fired,
                f.getId(), new ListReminder(code, f.getId(), noon.plus(Duration.ofHours(21)), alex.getId()));

        List<ListItem> shown = ListItemService.displayOrder(lists.openItems(code, ListKind.TODO),
                reminders, today, utc);
        assertEquals(List.of("E fired yesterday", "C this afternoon", "B plain", "D plain",
                        "F tomorrow 9:00", "A next month"), texts(shown),
                "due by tonight lead soonest first; tomorrow and next month trail soonest first");

        // Tomorrow 9:00 is "later" today and "due" tomorrow — the same list, seen the next morning.
        shown = ListItemService.displayOrder(lists.openItems(code, ListKind.TODO), reminders,
                today.plusDays(1), utc);
        assertEquals("F tomorrow 9:00", shown.get(2).getText(), "its day has come: above the plain lines");

        // The plain lines follow the family's order, not their writing order.
        assertTrue(lists.placeItem(code, Author.member(alex.getId()), d.getId(), b.getId()));
        shown = ListItemService.displayOrder(lists.openItems(code, ListKind.TODO), reminders, today, utc);
        assertEquals(List.of("D plain", "B plain"), texts(shown.subList(2, 4)));

        assertEquals(texts(lists.openItems(code, ListKind.TODO)),
                texts(ListItemService.displayOrder(lists.openItems(code, ListKind.TODO), Map.of(), today, utc)),
                "with no reminders the display order is the family's order");
    }
}
