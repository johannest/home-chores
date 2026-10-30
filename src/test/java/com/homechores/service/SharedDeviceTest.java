package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.DivisionStyle;
import com.homechores.domain.Home;
import com.homechores.domain.ListKind;
import com.homechores.domain.Member;
import com.homechores.domain.SharedDevice;
import com.homechores.service.ChoreService.CompleteOutcome;
import com.homechores.service.ChoreService.LockReason;
import com.homechores.service.ChoreService.TaskView;
import com.homechores.service.SharedDeviceService.PairResult;
import com.homechores.service.SharedDeviceService.Paired;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * The family's shared device (§4.1.3): paired with the admin PIN, proven by its own secret,
 * never an admin, and acting only for members of its own home.
 */
@SpringBootTest
@Transactional
class SharedDeviceTest {

    @Autowired
    SharedDeviceService devices;

    @Autowired
    ChoreService service;

    @Autowired
    ListItemService lists;

    @Autowired
    BackupService backup;

    private Paired pair(String code) {
        String pin = service.findHome(code).orElseThrow().getAdminPin();
        var outcome = devices.pair(code, pin, "Kitchen iPad", "Shared device");
        assertEquals(PairResult.PAIRED, outcome.result());
        return outcome.paired();
    }

    @Test
    void pairsWithTheHomeCodeAndAdminPin_andTheSecretProvesTheDevice() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();

        Paired paired = pair(code);

        SharedDevice device = paired.device();
        assertEquals("Kitchen iPad", device.getName());
        assertEquals(code, device.getHomeCode());
        assertNotNull(device.getSecretHash());
        assertTrue(devices.verify(device.getId(), paired.secret()));
        assertFalse(devices.verify(device.getId(), "not-the-secret"));
        assertFalse(devices.verify(device.getId(), ""));
        assertEquals(1, devices.devicesOf(code).size());
        assertEquals(1, service.membersOf(code).size(), "a device is not a member");
    }

    @Test
    void wrongPin_unknownHome_andTooManyDevices_areRefused() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        String pin = service.findHome(code).orElseThrow().getAdminPin();

        assertEquals(PairResult.WRONG_PIN, devices.pair(code, "0000".equals(pin) ? "1111" : "0000",
                "x", "d").result());
        assertEquals(PairResult.UNKNOWN, devices.pair("NOPE123", pin, "x", "d").result());
        assertTrue(devices.devicesOf(code).isEmpty());

        for (int i = 0; i < SharedDevice.MAX_PER_HOME; i++) {
            assertEquals(PairResult.PAIRED, devices.pair(code, pin, "", "Shared device").result());
        }
        assertEquals(PairResult.TOO_MANY, devices.pair(code, pin, "one more", "d").result());
        assertEquals("Shared device", devices.devicesOf(code).get(0).getName(),
                "a blank name takes the default wording");
    }

    /** The PIN door is one gate for the whole home: pairing guesses lock it like claiming does. */
    @Test
    void wrongPairingGuesses_lockThePinForClaimingToo() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        String pin = service.findHome(code).orElseThrow().getAdminPin();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        for (int i = 0; i < 5; i++) {
            devices.pair(code, "0000".equals(pin) ? "1111" : "0000", "x", "d");
        }
        assertFalse(service.claimAdmin(sam.getId(), pin), "locked, even with the right PIN");
        assertEquals(PairResult.WRONG_PIN, devices.pair(code, pin, "x", "d").result());
    }

    @Test
    void unpairing_isForTheHomesAdminOnly_andStopsTheSecret() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        Member other = service.createHome("Elsewhere", "Kim");
        Paired paired = pair(code);
        Long id = paired.device().getId();

        assertThrows(IllegalArgumentException.class, () -> devices.unpair(id, sam.getId()));
        assertThrows(IllegalArgumentException.class, () -> devices.unpair(id, other.getId()));
        assertTrue(devices.verify(id, paired.secret()), "still paired");

        assertTrue(devices.unpair(id, alex.getId()));
        assertFalse(devices.verify(id, paired.secret()));
        assertTrue(devices.find(id).isEmpty());
        assertFalse(devices.unpair(id, alex.getId()), "already gone");
    }

    @Test
    void leaving_unpairsOnlyTheDeviceItself_andFreesItsSlot() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Paired kitchen = pair(code);
        Paired hall = pair(code);
        Member kim = service.createHome("Elsewhere", "Kim");

        assertFalse(devices.leave(kim.getHomeCode(), kitchen.device().getId()),
                "another home's code names no device of its own here");
        assertTrue(devices.verify(kitchen.device().getId(), kitchen.secret()), "still paired");

        assertTrue(devices.leave(code, kitchen.device().getId()));
        assertFalse(devices.verify(kitchen.device().getId(), kitchen.secret()));
        assertTrue(devices.verify(hall.device().getId(), hall.secret()), "the other device stays");
        assertFalse(devices.leave(code, kitchen.device().getId()), "already gone");

        for (int i = 1; i < SharedDevice.MAX_PER_HOME; i++) {
            pair(code);
        }
        assertEquals(SharedDevice.MAX_PER_HOME, devices.devicesOf(code).size(),
                "the slot it held is free again");
    }

    @Test
    void completesForTheMemberPicked_withEveryLockRunForThem() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member milla = service.addMemberWithoutDevice(code, "Milla", alex.getId()).orElseThrow();
        Long device = pair(code).device().getId();
        ChoreTask chore = service.tasksOf(code).get(0);

        CompleteOutcome done = devices.complete(device, chore.getId(), milla.getId(),
                java.time.ZoneId.systemDefault());
        assertTrue(done.allowed());
        assertEquals(milla.getId(), done.member().getId());
        assertEquals(1, service.completionCount(milla.getId()));

        // The streak rule is Milla's, not the device's: three in a row and the fourth is refused.
        devices.complete(device, chore.getId(), milla.getId(), java.time.ZoneId.systemDefault());
        devices.complete(device, chore.getId(), milla.getId(), java.time.ZoneId.systemDefault());
        CompleteOutcome fourth = devices.complete(device, chore.getId(), milla.getId(),
                java.time.ZoneId.systemDefault());
        assertFalse(fourth.allowed());
        assertEquals(LockReason.STREAK, fourth.blockReason());
        assertEquals(3, service.completionCount(milla.getId()));
    }

    @Test
    void refusesAMemberOrChoreOutsideTheDevicesHome() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member kim = service.createHome("Elsewhere", "Kim");
        Long device = pair(code).device().getId();
        Long theirs = service.tasksOf(kim.getHomeCode()).get(0).getId();
        Long ours = service.tasksOf(code).get(0).getId();
        Long otherDevice = pair(kim.getHomeCode()).device().getId();
        var zone = java.time.ZoneId.systemDefault();

        assertThrows(IllegalArgumentException.class,
                () -> devices.complete(device, ours, kim.getId(), zone), "another home's member");
        assertThrows(IllegalArgumentException.class,
                () -> devices.complete(device, theirs, alex.getId(), zone), "another home's chore");
        assertThrows(IllegalArgumentException.class,
                () -> devices.complete(otherDevice, ours, alex.getId(), zone), "another home's device");
        assertThrows(IllegalArgumentException.class,
                () -> devices.complete(99999L, ours, alex.getId(), zone), "no such device");
        assertThrows(IllegalArgumentException.class,
                () -> devices.book(device, ours, kim.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> devices.cancelBooking(device, theirs));
        assertThrows(IllegalArgumentException.class,
                () -> devices.logOtherHelp(device, kim.getId(), "carried the shopping"));
        assertEquals(0, service.completionCount(kim.getId()));
        assertEquals(0, service.completionCount(alex.getId()));
    }

    @Test
    void booksForTheMemberPicked_andClearsAnyonesBooking() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        Long device = pair(code).device().getId();
        Long chore = service.tasksOf(code).get(0).getId();

        assertTrue(devices.book(device, chore, sam.getId()));
        assertFalse(devices.book(device, chore, alex.getId()), "Sam holds it");
        assertEquals(sam.getId(), service.tasksOf(code).get(0).getBookedByMemberId());

        devices.cancelBooking(device, chore);
        assertEquals(null, service.tasksOf(code).get(0).getBookedByMemberId(),
                "the household's device clears a booking whoever made it");
    }

    @Test
    void otherHelpFromTheDevice_waitsForAnAdmin_likeTheMembersOwnWould() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member milla = service.addMemberWithoutDevice(code, "Milla", alex.getId()).orElseThrow();
        Long device = pair(code).device().getId();

        var logged = devices.logOtherHelp(device, milla.getId(), "carried the shopping in");
        assertTrue(logged.isPresent());
        assertEquals(milla.getId(), logged.get().getMemberId());
        assertEquals(1, service.pendingOtherHelp(code).size());
        assertEquals(0, service.completionCount(milla.getId()), "pending until accepted");
    }

    /** The device's board belongs to nobody, so only the locks about time apply to it; the
     *  cards still say whose turn it is. */
    @Test
    void theDevicesBoard_locksOnTimeOnly_andStillSaysWhoseTurn() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        ChoreTask chore = service.tasksOf(code).get(0);
        assertTrue(service.bookChore(chore.getId(), sam.getId()));
        ChoreTask weekly = service.addTask(code, "Water plants", "🪴", 7);
        service.complete(weekly.getId(), alex.getId());

        List<TaskView> views = service.taskViews(code, null);
        TaskView booked = views.stream().filter(v -> v.task().getId().equals(chore.getId()))
                .findFirst().orElseThrow();
        assertEquals(LockReason.NONE, booked.lockReason(), "Sam can still tap it on the device");
        assertEquals(sam.getId(), booked.bookedById(), "and the card says it is Sam's");
        assertFalse(booked.bookedByMe(null));
        TaskView parked = views.stream().filter(v -> v.task().getId().equals(weekly.getId()))
                .findFirst().orElseThrow();
        assertEquals(LockReason.NOT_DUE, parked.lockReason(), "time locks are everyone's");

        Home home = service.findHome(code).orElseThrow();
        home.setDivisionStyle(DivisionStyle.ROTATING);
        home.setRotationEnforced(true);
        service.saveHome(home);
        for (TaskView v : service.taskViews(code, null)) {
            // Default chores include hours-bound ones (the dog), so only the who-locks are asserted.
            assertFalse(v.lockReason() == LockReason.NOT_ASSIGNED
                    || v.lockReason() == LockReason.BOOKED || v.lockReason() == LockReason.STREAK,
                    "nobody's turn is not 'not your turn': " + v.task().getName());
            assertFalse(v.assignedToMe(null));
        }
    }

    @Test
    void listLinesFromTheDevice_haveNoAuthor_andOnlyItsOwnHomesLinesTakeIt() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Author kitchen = Author.device(pair(code).device().getId());
        var milk = lists.add(code, ListKind.GROCERY, kitchen, "Milk").orElseThrow();
        assertNull(milk.getCreatedByMemberId());
        assertTrue(lists.setDone(milk.getId(), kitchen, true));
        assertNull(lists.find(milk.getId()).orElseThrow().getDoneByMemberId());
        assertThrows(IllegalArgumentException.class,
                () -> lists.setListEnabled(code, null, ListKind.DINNER, false),
                "but never the admin's settings");

        // Another home with a tablet of its own: its lines are not the kitchen's to touch, and
        // neither are this home's lines its tablet's.
        Member pat = service.createHome("Other", "Pat");
        String other = pat.getHomeCode();
        Author theirs = Author.device(pair(other).device().getId());
        var bread = lists.add(other, ListKind.GROCERY, Author.member(pat.getId()), "Bread")
                .orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> lists.setDone(bread.getId(), kitchen, true));
        assertThrows(IllegalArgumentException.class, () -> lists.rename(bread.getId(), kitchen, "x"));
        assertThrows(IllegalArgumentException.class, () -> lists.delete(bread.getId(), kitchen));
        assertThrows(IllegalArgumentException.class,
                () -> lists.add(code, ListKind.GROCERY, theirs, "Eggs"));
        assertEquals("Bread", lists.find(bread.getId()).orElseThrow().getText());
        assertFalse(lists.find(bread.getId()).orElseThrow().isDone());
    }

    @Test
    void anUnpairedDevice_writesNoLines_evenWhileTheHomeHasAnotherOne() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Paired gone = pair(code);
        pair(code); // the one that stays
        var milk = lists.add(code, ListKind.GROCERY, Author.device(gone.device().getId()), "Milk")
                .orElseThrow();
        assertTrue(devices.unpair(gone.device().getId(), alex.getId()));

        Author unpaired = Author.device(gone.device().getId());
        assertThrows(IllegalArgumentException.class,
                () -> lists.add(code, ListKind.GROCERY, unpaired, "Eggs"));
        assertThrows(IllegalArgumentException.class, () -> lists.setDone(milk.getId(), unpaired, true));
        assertThrows(IllegalArgumentException.class, () -> lists.delete(milk.getId(), unpaired));
        assertThrows(IllegalArgumentException.class, () -> Author.member(null),
                "no member and no device is nobody");
    }

    @Test
    void devicesGoWithTheHome_andSurviveARestore() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Paired paired = pair(code);
        Long id = paired.device().getId();

        byte[] json = backup.export(code).getBytes(StandardCharsets.UTF_8);
        assertFalse(new String(json, StandardCharsets.UTF_8).contains("Kitchen iPad"),
                "a device is a fact about a browser, not about the family");
        backup.restore(json, code, service.membersOf(code).get(0).getId());
        assertTrue(devices.verify(id, paired.secret()), "still paired after the restore");

        assertTrue(service.deleteHome(code));
        assertTrue(devices.find(id).isEmpty());
    }
}
