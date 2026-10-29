package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homechores.domain.CustomList;
import com.homechores.domain.CustomListRepository;
import com.homechores.domain.ListItem;
import com.homechores.domain.ListItemRepository;
import com.homechores.domain.ListKind;
import com.homechores.domain.ListReminder;
import com.homechores.domain.ListReminderRepository;
import com.homechores.domain.Member;
import com.homechores.domain.PushSubscription;
import com.homechores.domain.PushSubscriptionRepository;
import com.homechores.service.ListItemService.ListSlot;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Private lists (§4.4e): a member's own list only they see. Every read and every write is
 * checked for the owner, another member, an admin and the shared device, because the ids a
 * request carries are the browser's and a hidden tab is not a check.
 */
@SpringBootTest
@Transactional
class PrivateListServiceTest {

    @Autowired ChoreService chores;
    @Autowired ListItemService lists;
    @Autowired ListReminderService reminders;
    @Autowired SharedDeviceService devices;
    @Autowired BackupService backup;
    @Autowired CustomListRepository listRepo;
    @Autowired ListItemRepository itemRepo;
    @Autowired ListReminderRepository reminderRepo;
    @Autowired PushSubscriptionRepository subscriptions;

    @MockitoBean WebPushSender sender;

    private Member alex; // admin
    private Member sam;  // owner of the private list
    private String code;
    private Author device;
    private CustomList diary;
    private ListItem line;

    /** Alex (admin) and Sam share a home with a kitchen tablet; Sam keeps a private list. */
    private void home() {
        alex = chores.createHome("Nest", "Alex");
        code = alex.getHomeCode();
        sam = chores.joinHome(code, "Sam").orElseThrow();
        device = Author.device(devices.pair(code, chores.findHome(code).orElseThrow().getAdminPin(),
                "Tab", "Tab").paired().device().getId());
        diary = lists.createList(code, Author.member(sam.getId()), "Diary").orElseThrow();
        line = lists.add(code, ListKind.TODO, diary.getId(), Author.member(sam.getId()), "Book therapist")
                .orElseThrow();
        assertTrue(lists.setListPrivate(code, diary.getId(), Author.member(sam.getId()), true));
    }

    private static List<String> names(List<ListSlot> slots) {
        return slots.stream().filter(s -> !s.isBuiltIn()).map(s -> s.custom().getName()).toList();
    }

    @Test
    void onlyItsOwnerSeesAPrivateList_notAnotherMember_notAnAdmin_notTheDevice() {
        home();
        Author owner = Author.member(sam.getId());
        assertEquals(List.of("Diary"), names(lists.visibleSlots(code, owner)));
        assertEquals(1, lists.openItems(code, ListKind.TODO, diary.getId(), owner).size());

        for (Author other : List.of(Author.member(alex.getId()), device)) {
            assertTrue(names(lists.visibleSlots(code, other)).isEmpty(), "no tab for " + other);
            assertTrue(names(lists.listSlots(code, other)).isEmpty(), "nor in the admin's list card");
            assertTrue(lists.findList(code, diary.getId(), other).isEmpty());
            assertTrue(lists.openItems(code, ListKind.TODO, diary.getId(), other).isEmpty());
            assertTrue(lists.doneItems(code, ListKind.TODO, diary.getId(), other).isEmpty());
        }
        assertTrue(lists.openItems(code, ListKind.TODO).isEmpty(), "its lines are not on the shared To-do");
    }

    @Test
    void nobodyButItsOwner_writesToAPrivateList_byAnyRoute() {
        home();
        lists.setDone(line.getId(), Author.member(sam.getId()), true);
        lists.setDone(line.getId(), Author.member(sam.getId()), false);
        ListItem second = lists.add(code, ListKind.TODO, diary.getId(), Author.member(sam.getId()), "Second")
                .orElseThrow();

        for (Author other : List.of(Author.member(alex.getId()), device)) {
            assertTrue(lists.add(code, ListKind.TODO, diary.getId(), other, "Planted").isEmpty());
            assertFalse(lists.rename(line.getId(), other, "Changed"));
            assertFalse(lists.setDone(line.getId(), other, true));
            assertFalse(lists.delete(line.getId(), other));
            assertFalse(lists.placeItem(code, other, second.getId(), line.getId()));
            assertEquals(0, lists.clearDone(code, ListKind.TODO, diary.getId(), other));
            assertFalse(lists.renameList(code, diary.getId(), other, "Hijacked"));
            assertFalse(lists.deleteList(code, diary.getId(), other));
            assertFalse(lists.setListPrivate(code, diary.getId(), other, false), "nor share it");
        }
        ListItem after = itemRepo.findById(line.getId()).orElseThrow();
        assertEquals("Book therapist", after.getText());
        assertFalse(after.isDone());
        assertEquals("Diary", listRepo.findById(diary.getId()).orElseThrow().getName());
        assertEquals(2, lists.openItems(code, ListKind.TODO, diary.getId(), Author.member(sam.getId())).size());
    }

    @Test
    void onlyTheMaker_makesAListPrivate_onlyTheOwnerSharesIt_andTheDeviceDoesNeither() {
        home();
        CustomList shared = lists.createList(code, Author.member(alex.getId()), "Gifts").orElseThrow();
        assertFalse(lists.setListPrivate(code, shared.getId(), Author.member(sam.getId()), true),
                "somebody else's list is not mine to hide");
        assertFalse(lists.setListPrivate(code, shared.getId(), device, true));
        CustomList devicesOwn = lists.createList(code, device, "Tablet list").orElseThrow();
        assertFalse(lists.setListPrivate(code, devicesOwn.getId(), device, true), "the device owns nothing");

        assertTrue(lists.setListPrivate(code, diary.getId(), Author.member(sam.getId()), false));
        assertEquals(List.of("Diary", "Gifts", "Tablet list"),
                names(lists.visibleSlots(code, Author.member(alex.getId()))), "shared: everyone sees it");
    }

    @Test
    void anAdminMovingLists_stepsOverAPrivateListTheyCannotSee() {
        home();
        CustomList gifts = lists.createList(code, Author.member(alex.getId()), "Gifts").orElseThrow();
        // Home order: … ROUTINES, Diary (private), Gifts. To Alex: … ROUTINES, Gifts.
        assertTrue(lists.moveList(code, alex.getId(), "L:" + gifts.getId(), -1));
        List<String> alexSees = lists.listSlots(code, Author.member(alex.getId())).stream()
                .map(ListSlot::token).toList();
        assertEquals("L:" + gifts.getId(), alexSees.get(alexSees.size() - 2),
                "one visible step: Gifts is now before Routines for the admin");
        assertEquals(List.of("GROCERY", "TODO", "DINNER", "L:" + gifts.getId(), "L:" + diary.getId(), "ROUTINES"),
                lists.listSlots(code, Author.member(sam.getId())).stream().map(ListSlot::token).toList(),
                "and Sam's list keeps its own place, fifth, as before");
    }

    @Test
    void aPrivateLinesReminder_reachesOnlyItsOwner_andNobodyElseCanSetOrSeeIt() {
        when(sender.isEnabled()).thenReturn(true);
        when(sender.send(any(), anyString(), anyString())).thenReturn(true);
        home();
        for (Member m : List.of(alex, sam)) {
            subscriptions.save(new PushSubscription(m.getId(), code,
                    "https://fcm.googleapis.com/fcm/send/" + m.getId(), "p256dh", "auth"));
        }
        Instant later = Instant.now().plus(Duration.ofHours(2));
        assertThrows(IllegalArgumentException.class,
                () -> reminders.schedule(line.getId(), alex.getId(), later, Locale.ENGLISH));

        reminders.schedule(line.getId(), sam.getId(), later, Locale.ENGLISH);
        ListReminder r = reminderRepo.findByItemId(line.getId()).orElseThrow();
        r.setDueAt(Instant.now().minusSeconds(60));
        reminderRepo.save(r);
        assertEquals(1, reminders.sendDue(Instant.now()), "Sam's one device, not Alex's");
        verify(sender, times(1)).send(any(), anyString(), anyString());

        assertEquals(1, reminders.firedFor(code, sam.getId()).size());
        assertTrue(reminders.firedFor(code, alex.getId()).isEmpty(), "Alex's board shows no dialog about it");
        assertThrows(IllegalArgumentException.class, () -> reminders.cancel(line.getId(), alex.getId()));
    }

    @Test
    void aRemovedMember_takesTheirPrivateListsAlong_theirSharedOnesStay() {
        home();
        CustomList shared = lists.createList(code, Author.member(sam.getId()), "Shared by Sam").orElseThrow();
        reminders.schedule(line.getId(), sam.getId(), Instant.now().plus(Duration.ofHours(2)), Locale.ENGLISH);

        assertTrue(chores.removeMember(sam.getId()));
        assertTrue(listRepo.findById(diary.getId()).isEmpty());
        assertTrue(itemRepo.findById(line.getId()).isEmpty());
        assertTrue(reminderRepo.findByItemId(line.getId()).isEmpty());
        assertTrue(listRepo.findById(shared.getId()).isPresent());
    }

    @Test
    void aBackupHoldsPrivateLists_andRestoreKeepsThemPrivate_orDropsThemWithTheirLines() {
        home();
        String json = backup.export(code);
        assertTrue(json.contains("Book therapist"), "the family's backup is the whole record (§4.15)");

        backup.restore(json.getBytes(StandardCharsets.UTF_8), code, alex.getId());
        Member newSam = chores.membersOf(code).stream().filter(m -> m.getName().equals("Sam")).findFirst().orElseThrow();
        Member newAlex = chores.membersOf(code).stream().filter(m -> m.getName().equals("Alex")).findFirst().orElseThrow();
        assertEquals(List.of("Diary"), names(lists.visibleSlots(code, Author.member(newSam.getId()))));
        assertTrue(names(lists.visibleSlots(code, Author.member(newAlex.getId()))).isEmpty());

        // An owner who is not in the file: the list and its lines go — never onto the shared To-do.
        String orphaned = backup.export(code).replace("\"ownerMemberId\" : " + newSam.getId(),
                "\"ownerMemberId\" : 999999");
        assertTrue(orphaned.contains("999999"), "the edit took");
        backup.restore(orphaned.getBytes(StandardCharsets.UTF_8), code, newAlex.getId());
        assertTrue(lists.customLists(code).isEmpty());
        assertTrue(lists.openItems(code, ListKind.TODO).isEmpty());
    }
}
