package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.CustomList;
import com.homechores.domain.Home;
import com.homechores.domain.HomeRepository;
import com.homechores.domain.ListItem;
import com.homechores.domain.ListKind;
import com.homechores.domain.ListReminderRepository;
import com.homechores.domain.Member;
import com.homechores.service.ListItemService.ListSlot;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The Lists tab's order and which built-in lists it shows — both set by an admin. */
@SpringBootTest
class ListOrderServiceTest {

    @Autowired ListItemService lists;
    @Autowired ListReminderService reminders;
    @Autowired ListReminderRepository reminderRepo;
    @Autowired ChoreService chores;
    @Autowired HomeRepository homes;
    @Autowired BackupService backup;

    private static List<String> tokens(List<ListSlot> slots) {
        return slots.stream().map(ListSlot::token).toList();
    }

    @Test
    void byDefault_builtInsFirst_thenOwnListsInTheOrderMade() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        CustomList a = lists.createList(code, alex.getId(), "A").orElseThrow();
        CustomList b = lists.createList(code, alex.getId(), "B").orElseThrow();

        assertEquals(List.of("GROCERY", "TODO", "DINNER", "L:" + a.getId(), "L:" + b.getId()),
                tokens(lists.listSlots(code)));
    }

    @Test
    void moveList_swapsNeighbours_andStopsAtTheEnds() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        CustomList a = lists.createList(code, alex.getId(), "A").orElseThrow();

        assertTrue(lists.moveList(code, alex.getId(), "L:" + a.getId(), -1));
        assertTrue(lists.moveList(code, alex.getId(), "L:" + a.getId(), -1));
        assertTrue(lists.moveList(code, alex.getId(), "DINNER", -1));
        assertEquals(List.of("GROCERY", "L:" + a.getId(), "DINNER", "TODO"), tokens(lists.listSlots(code)));

        assertFalse(lists.moveList(code, alex.getId(), "GROCERY", -1), "already first");
        assertFalse(lists.moveList(code, alex.getId(), "TODO", 1), "already last");
        assertFalse(lists.moveList(code, alex.getId(), "L:999999", 1), "no such list");
    }

    @Test
    void theStoredOrder_healsItself() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        CustomList a = lists.createList(code, alex.getId(), "A").orElseThrow();
        Home home = homes.findById(code).orElseThrow();
        // Hand-written: a deleted list, garbage, a missing built-in (GROCERY) and no mention of A.
        home.setListOrder("L:424242,DINNER,nonsense,TODO");
        homes.save(home);

        CustomList b = lists.createList(code, alex.getId(), "B").orElseThrow();
        assertEquals(List.of("GROCERY", "DINNER", "TODO", "L:" + a.getId(), "L:" + b.getId()),
                tokens(lists.listSlots(code)),
                "dead tokens drop, a built-in goes back to its default place, own lists go last");

        lists.deleteList(code, a.getId(), alex.getId());
        assertEquals(List.of("GROCERY", "DINNER", "TODO", "L:" + b.getId()), tokens(lists.listSlots(code)));
    }

    @Test
    void switchingABuiltInOff_hidesIt_cancelsItsReminders_andKeepsItsLines() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        ListItem milk = lists.add(code, ListKind.GROCERY, alex.getId(), "Milk").orElseThrow();
        ListItem call = lists.add(code, ListKind.TODO, alex.getId(), "Call").orElseThrow();
        reminders.schedule(milk.getId(), alex.getId(), Instant.now().plus(Duration.ofHours(2)), Locale.ENGLISH);
        reminders.schedule(call.getId(), alex.getId(), Instant.now().plus(Duration.ofHours(2)), Locale.ENGLISH);

        lists.setListEnabled(code, alex.getId(), ListKind.GROCERY, false);

        assertEquals(Set.of(ListKind.GROCERY), lists.hiddenLists(code));
        assertEquals(List.of("TODO", "DINNER"), tokens(lists.visibleSlots(code)));
        assertEquals(3, lists.listSlots(code).size(), "still in the order, just not shown");
        assertTrue(reminderRepo.findByItemId(milk.getId()).isEmpty(), "nobody is nudged about a hidden list");
        assertTrue(reminderRepo.findByItemId(call.getId()).isPresent(), "other lists keep theirs");
        assertEquals(1, lists.openItems(code, ListKind.GROCERY).size(), "the milk is kept");

        lists.setListEnabled(code, alex.getId(), ListKind.GROCERY, true);
        assertTrue(lists.hiddenLists(code).isEmpty());
        assertEquals("GROCERY", lists.visibleSlots(code).get(0).token(), "back in its place");
        assertEquals(null, homes.findById(code).orElseThrow().getHiddenLists(), "all on stores as null");
    }

    @Test
    void onlyAnAdminChangesTheLists() {
        Member alex = chores.createHome("Listy", "Alex");
        Member sam = chores.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        assertThrows(IllegalArgumentException.class,
                () -> lists.moveList(alex.getHomeCode(), sam.getId(), "TODO", -1));
        assertThrows(IllegalArgumentException.class,
                () -> lists.setListEnabled(alex.getHomeCode(), sam.getId(), ListKind.DINNER, false));
        assertTrue(lists.hiddenLists(alex.getHomeCode()).isEmpty());
    }

    @Test
    void orderAndHiddenLists_surviveABackup_withListIdsRemapped() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        CustomList a = lists.createList(code, alex.getId(), "Gifts").orElseThrow();
        lists.moveList(code, alex.getId(), "L:" + a.getId(), -1);
        lists.moveList(code, alex.getId(), "L:" + a.getId(), -1);
        lists.moveList(code, alex.getId(), "L:" + a.getId(), -1); // Gifts first
        lists.setListEnabled(code, alex.getId(), ListKind.DINNER, false);

        String json = backup.export(code);
        backup.restore(json.getBytes(StandardCharsets.UTF_8), code);

        CustomList restored = lists.customLists(code).get(0);
        assertFalse(restored.getId().equals(a.getId()), "precondition: restore issued a new id");
        assertEquals(List.of("L:" + restored.getId(), "GROCERY", "TODO", "DINNER"), tokens(lists.listSlots(code)),
                "Gifts is still first, under its new id");
        assertEquals(Set.of(ListKind.DINNER), lists.hiddenLists(code));
    }
}
