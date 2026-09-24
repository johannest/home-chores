package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.CustomList;
import com.homechores.domain.InputLimits;
import com.homechores.domain.ListItem;
import com.homechores.domain.ListKind;
import com.homechores.domain.ListReminderRepository;
import com.homechores.domain.Member;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** A home's own named lists, and editing a line's text. */
@SpringBootTest
class CustomListServiceTest {

    @Autowired ListItemService lists;
    @Autowired ListReminderService reminders;
    @Autowired ListReminderRepository reminderRepo;
    @Autowired ChoreService chores;

    @Test
    void createList_trimsAndClips_refusesBlank_andStopsAtTheLimit() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();

        CustomList hw = lists.createList(code, alex.getId(), "  Hardware store  ").orElseThrow();
        assertEquals("Hardware store", hw.getName());
        assertEquals("x".repeat(InputLimits.LIST_NAME),
                lists.createList(code, alex.getId(), "x".repeat(99)).orElseThrow().getName());
        assertTrue(lists.createList(code, alex.getId(), "   ").isEmpty());

        for (int i = lists.customLists(code).size(); i < InputLimits.CUSTOM_LISTS; i++) {
            assertTrue(lists.createList(code, alex.getId(), "List " + i).isPresent());
        }
        assertTrue(lists.createList(code, alex.getId(), "One too many").isEmpty());
        assertEquals(InputLimits.CUSTOM_LISTS, lists.customLists(code).size());
        assertEquals("Hardware store", lists.customLists(code).get(0).getName(), "creation order");
    }

    @Test
    void aCustomListsLines_stayOffTheBuiltInToDo_andTheOtherWayRound() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        CustomList gifts = lists.createList(code, alex.getId(), "Gifts").orElseThrow();
        CustomList hw = lists.createList(code, alex.getId(), "Hardware").orElseThrow();

        lists.add(code, ListKind.TODO, alex.getId(), "Call the plumber");
        ListItem book = lists.add(code, ListKind.GROCERY, gifts.getId(), alex.getId(), "Book").orElseThrow();
        lists.add(code, ListKind.TODO, hw.getId(), alex.getId(), "Screws");

        assertEquals(ListKind.TODO, book.getKind(), "a custom list's lines are to-dos");
        assertEquals(List.of("Call the plumber"),
                lists.openItems(code, ListKind.TODO).stream().map(ListItem::getText).toList());
        assertEquals(List.of("Book"),
                lists.openItems(code, ListKind.TODO, gifts.getId()).stream().map(ListItem::getText).toList());
        assertEquals(List.of("Screws"),
                lists.openItems(code, ListKind.TODO, hw.getId()).stream().map(ListItem::getText).toList());

        lists.setDone(book.getId(), alex.getId(), true);
        assertEquals(1, lists.doneItems(code, ListKind.TODO, gifts.getId()).size());
        assertTrue(lists.doneItems(code, ListKind.TODO).isEmpty(), "the built-in To-do's done list is its own");
        assertEquals(0, lists.clearDone(code, ListKind.TODO), "clearing To-do leaves the gift list alone");
        assertEquals(1, lists.clearDone(code, ListKind.TODO, gifts.getId()));
    }

    @Test
    void aListFromAnotherHome_isNeitherReadableNorWritable() {
        Member alex = chores.createHome("Listy", "Alex");
        Member sam = chores.createHome("Elsewhere", "Sam");
        CustomList samsList = lists.createList(sam.getHomeCode(), sam.getId(), "Private").orElseThrow();
        lists.add(sam.getHomeCode(), ListKind.TODO, samsList.getId(), sam.getId(), "Secret");

        assertTrue(lists.openItems(alex.getHomeCode(), ListKind.TODO, samsList.getId()).isEmpty());
        assertTrue(lists.add(alex.getHomeCode(), ListKind.TODO, samsList.getId(), alex.getId(), "x").isEmpty());
        assertFalse(lists.renameList(alex.getHomeCode(), samsList.getId(), alex.getId(), "Mine"));
        assertFalse(lists.deleteList(alex.getHomeCode(), samsList.getId(), alex.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> lists.createList(alex.getHomeCode(), sam.getId(), "Intruder"));
    }

    @Test
    void renameList_changesTheName_blankChangesNothing() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        CustomList l = lists.createList(code, alex.getId(), "Gifts").orElseThrow();

        assertTrue(lists.renameList(code, l.getId(), alex.getId(), " Birthday gifts "));
        assertEquals("Birthday gifts", lists.findList(code, l.getId()).orElseThrow().getName());
        assertFalse(lists.renameList(code, l.getId(), alex.getId(), "  "));
        assertEquals("Birthday gifts", lists.findList(code, l.getId()).orElseThrow().getName());
    }

    @Test
    void deleteList_takesItsLinesAndTheirReminders_andNothingElse() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        CustomList l = lists.createList(code, alex.getId(), "Gifts").orElseThrow();
        ListItem book = lists.add(code, ListKind.TODO, l.getId(), alex.getId(), "Book").orElseThrow();
        ListItem keep = lists.add(code, ListKind.TODO, alex.getId(), "Call the plumber").orElseThrow();
        reminders.schedule(book.getId(), alex.getId(), Instant.now().plus(Duration.ofHours(2)), Locale.ENGLISH);
        reminders.schedule(keep.getId(), alex.getId(), Instant.now().plus(Duration.ofHours(2)), Locale.ENGLISH);

        assertTrue(lists.deleteList(code, l.getId(), alex.getId()));

        assertTrue(lists.customLists(code).isEmpty());
        assertTrue(lists.find(book.getId()).isEmpty(), "the list's lines go with it");
        assertTrue(reminderRepo.findByItemId(book.getId()).isEmpty(), "and so do their reminders");
        assertTrue(lists.find(keep.getId()).isPresent(), "the built-in To-do is untouched");
        assertTrue(reminderRepo.findByItemId(keep.getId()).isPresent());
        assertFalse(lists.deleteList(code, l.getId(), alex.getId()), "already gone");
    }

    @Test
    void deleteHome_takesItsCustomListsToo() {
        Member alex = chores.createHome("Listy", "Alex");
        String code = alex.getHomeCode();
        lists.createList(code, alex.getId(), "Gifts");
        assertTrue(chores.deleteHome(code));
        assertTrue(lists.customLists(code).isEmpty());
    }

    @Test
    void rename_rewritesTheText_keepingTickAndAuthor() {
        Member alex = chores.createHome("Listy", "Alex");
        Member sam = chores.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        String code = alex.getHomeCode();
        ListItem milk = lists.add(code, ListKind.GROCERY, alex.getId(), "Mlik").orElseThrow();
        lists.setDone(milk.getId(), alex.getId(), true);

        assertTrue(lists.rename(milk.getId(), sam.getId(), "  Milk  "));
        ListItem after = lists.find(milk.getId()).orElseThrow();
        assertEquals("Milk", after.getText());
        assertTrue(after.isDone(), "fixing a typo is not unticking");
        assertEquals(alex.getId(), after.getCreatedByMemberId(), "nor a new author");

        assertTrue(lists.rename(milk.getId(), sam.getId(), "y".repeat(500)));
        assertEquals(InputLimits.LIST_ITEM, lists.find(milk.getId()).orElseThrow().getText().length());
        assertFalse(lists.rename(milk.getId(), sam.getId(), "   "), "blank is not an edit");
        assertFalse(lists.rename(-1L, sam.getId(), "x"), "no such line");
    }

    @Test
    void rename_refusesDinnerSlotsAndStrangers() {
        Member alex = chores.createHome("Listy", "Alex");
        Member other = chores.createHome("Elsewhere", "Sam");
        String code = alex.getHomeCode();
        ListItem dinner = lists.setDinner(code, java.time.LocalDate.now(), alex.getId(), "Pasta").orElseThrow();
        ListItem milk = lists.add(code, ListKind.GROCERY, alex.getId(), "Milk").orElseThrow();

        assertFalse(lists.rename(dinner.getId(), alex.getId(), "Soup"), "dinners are set, not renamed");
        assertThrows(IllegalArgumentException.class, () -> lists.rename(milk.getId(), other.getId(), "Mine"));
        assertEquals("Milk", lists.find(milk.getId()).orElseThrow().getText());
    }
}
