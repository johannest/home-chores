package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreGroup;
import com.homechores.domain.ChoreTask;
import com.homechores.domain.Home;
import com.homechores.domain.ListItem;
import com.homechores.domain.ListKind;
import com.homechores.domain.Member;
import com.homechores.service.BackupService.RestoreResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BackupServiceTest {

    @Autowired
    ChoreService chores;
    @Autowired
    StatsService stats;
    @Autowired
    BackupService backup;
    @Autowired
    ListItemService lists;

    /** A home's own lists ride along too, their lines landing back on them rather than on To-do. */
    @Test
    void customLists_surviveTheRoundTrip_withTheirLines() {
        Member alex = chores.createHome("Listful", "Alex");
        String code = alex.getHomeCode();
        var gifts = lists.createList(code, alex.getId(), "Gifts").orElseThrow();
        lists.add(code, ListKind.TODO, gifts.getId(), alex.getId(), "Book");
        lists.add(code, ListKind.TODO, alex.getId(), "Call the plumber");

        String json = backup.export(code);
        assertTrue(json.contains("\"customLists\""));
        backup.restore(json.getBytes(StandardCharsets.UTF_8), code);

        var restored = lists.customLists(code);
        assertEquals(List.of("Gifts"), restored.stream().map(com.homechores.domain.CustomList::getName).toList());
        assertEquals(List.of("Book"), lists.openItems(code, ListKind.TODO, restored.get(0).getId())
                .stream().map(ListItem::getText).toList(), "the line is back on its list");
        assertEquals(List.of("Call the plumber"), lists.openItems(code, ListKind.TODO)
                .stream().map(ListItem::getText).toList(), "and not on the built-in To-do");

        // A file whose list vanished (hand-edited): the line falls back to the built-in To-do.
        String orphaned = json.replaceAll(",\\s*\"customLists\"\\s*:\\s*\\[[^\\]]*\\]", "");
        assertFalse(orphaned.contains("customLists"), "precondition: key stripped");
        backup.restore(orphaned.getBytes(StandardCharsets.UTF_8), code);
        assertTrue(lists.customLists(code).isEmpty());
        assertEquals(2, lists.openItems(code, ListKind.TODO).size(), "Book lands on To-do");
    }

    /** The shared lists are family data, so they ride along — ticked state and ticker included. */
    @Test
    void sharedLists_surviveTheRoundTrip_withTheTickerRemapped() {
        Member alex = chores.createHome("Listful", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        lists.add(code, ListKind.GROCERY, alex.getId(), "Milk");
        ListItem bread = lists.add(code, ListKind.GROCERY, sam.getId(), "Bread").orElseThrow();
        lists.setDone(bread.getId(), sam.getId(), true);
        lists.add(code, ListKind.TODO, alex.getId(), "Call the plumber");
        java.time.LocalDate today = java.time.LocalDate.now();
        lists.setDinner(code, today, sam.getId(), "Pasta");

        String json = backup.export(code);
        assertTrue(json.contains("\"listItems\""));
        assertTrue(json.contains("Bread"));
        assertTrue(json.contains("\"day\" : \"" + today + "\""), "the dinner's day travels as an ISO date");

        backup.restore(json.getBytes(StandardCharsets.UTF_8), code);

        assertEquals(List.of("Milk"),
                lists.openItems(code, ListKind.GROCERY).stream().map(ListItem::getText).toList());
        List<ListItem> done = lists.doneItems(code, ListKind.GROCERY);
        assertEquals(1, done.size());
        assertEquals("Bread", done.get(0).getText());
        Member restoredSam = chores.findMemberByName(code, "Sam").orElseThrow();
        assertEquals(restoredSam.getId(), done.get(0).getDoneByMemberId(),
                "the ticker points at the restored Sam, not the old id");
        assertEquals(1, lists.openItems(code, ListKind.TODO).size());
        ListItem dinner = lists.dinners(code, today, today).get(today);
        assertEquals("Pasta", dinner.getText(), "the dinner slot is back on its day");
        assertEquals(restoredSam.getId(), dinner.getCreatedByMemberId(), "set by the restored Sam");

        // A file from before the lists existed has no such key: restore leaves the lists empty.
        String old = json.replaceAll(",\\s*\"listItems\"\\s*:\\s*\\[[^\\]]*\\]", "");
        assertFalse(old.contains("listItems"), "precondition: key stripped");
        backup.restore(old.getBytes(StandardCharsets.UTF_8), code);
        assertTrue(lists.openItems(code, ListKind.GROCERY).isEmpty());
        assertTrue(lists.doneItems(code, ListKind.GROCERY).isEmpty());
        assertTrue(lists.dinners(code, today, today).isEmpty());
        assertEquals(2, chores.membersOf(code).size(), "everything else still restores");
    }

    @Test
    void exportThenRestore_roundTripsData() {
        Member alex = chores.createHome("Backup Home", "Alex");
        Member sam = chores.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        String code = alex.getHomeCode();
        List<ChoreTask> t = chores.tasksOf(code);
        chores.complete(t.get(0).getId(), alex.getId());
        chores.complete(t.get(1).getId(), sam.getId());
        chores.complete(t.get(2).getId(), sam.getId());

        Home before = chores.findHome(code).orElseThrow();
        before.setRequireApproval(true);
        before.setApproveRejoin(false);
        before.setDailyTargetPerMember(3);
        chores.saveHome(before);
        String pin = chores.findHome(code).orElseThrow().getAdminPin();

        // Tag one chore so seasons ride along with the windows through the round-trip.
        ChoreTask tagged = chores.tasksOf(code).get(0);
        chores.updateTask(tagged.getId(), tagged.getName(), tagged.getEmoji(),
                tagged.getIntervalDays(), tagged.getCreditValue(), null, "SPRING,SUMMER");

        String json = backup.export(code);
        assertTrue(json.contains("Backup Home"));

        // Wipe by deleting the whole home's members/tasks, then restore.
        RestoreResult result = backup.restore(json.getBytes(StandardCharsets.UTF_8), code);

        assertEquals(code, result.homeCode());
        assertEquals(2, result.members());
        assertEquals(11, result.tasks());
        assertEquals(3, result.completions());

        Home after = chores.findHome(code).orElseThrow();
        assertEquals("Backup Home", after.getName());
        assertEquals(pin, after.getAdminPin());
        assertTrue(after.isRequireApproval());
        assertFalse(after.isApproveRejoin());
        assertEquals(3, after.getDailyTargetPerMember());
        assertEquals(2, chores.membersOf(code).size());
        assertEquals(11, chores.tasksOf(code).size());

        // The seeded windowed chore round-trips its availability windows.
        assertTrue(chores.tasksOf(code).stream()
                .anyMatch(task -> "08:00-10:00,18:00-22:00".equals(task.getAvailableWindows())));
        // ...and the seasonal tag survives alongside it.
        assertTrue(chores.tasksOf(code).stream()
                .anyMatch(task -> "SPRING,SUMMER".equals(task.getSeasons())),
                "seasonal tag round-trips");

        // Sam still has 2 approved completions after the id remap.
        Member restoredSam = chores.membersOf(code).stream()
                .filter(m -> m.getName().equals("Sam")).findFirst().orElseThrow();
        assertEquals(2, chores.completionCount(restoredSam.getId()));
    }

    @Test
    void restore_rejectsGarbage() {
        assertThrows(IllegalArgumentException.class,
                () -> backup.restore("not json".getBytes(StandardCharsets.UTF_8), "XXXXXXX"));
    }

    /** A backup may only be restored into the home it came from — never over another family. */
    @Test
    void restore_rejectsBackupForADifferentHome() {
        Member mine = chores.createHome("Mine", "Alex");
        Member victim = chores.createHome("Victim", "Vera");
        String myCode = mine.getHomeCode();
        String victimCode = victim.getHomeCode();
        String victimBackup = backup.export(victimCode);

        // Admin of "Mine" uploads a file that names the victim's home code.
        assertThrows(IllegalArgumentException.class,
                () -> backup.restore(victimBackup.getBytes(StandardCharsets.UTF_8), myCode));

        // The victim home is completely untouched.
        assertEquals("Victim", chores.findHome(victimCode).orElseThrow().getName());
        assertEquals(1, chores.membersOf(victimCode).size());
    }

    @Test
    void groupsAndBoardOrder_roundTrip() {
        Member alex = chores.createHome("Grouped", "Alex");
        String code = alex.getHomeCode();
        ChoreGroup kitchen = chores.addGroup(code, "Kitchen", "🍳");
        chores.addGroup(code, "Laundry", "🧺");
        List<ChoreTask> t = chores.tasksOf(code);
        String firstName = t.get(2).getName();
        chores.setChoreGroup(t.get(2).getId(), kitchen.getId());
        chores.setChoreGroup(t.get(0).getId(), kitchen.getId());

        String json = backup.export(code);
        backup.restore(json.getBytes(StandardCharsets.UTF_8), code);

        assertEquals(List.of("Kitchen", "Laundry"),
                chores.groupsOf(code).stream().map(ChoreGroup::getName).toList());
        // Ids are remapped, so the assertion has to go through the RESTORED group, never the old id.
        Long restoredKitchen = chores.groupsOf(code).get(0).getId();
        List<ChoreTask> board = chores.tasksOf(code);
        assertEquals(firstName, board.get(0).getName(), "board order survives the round trip");
        assertEquals(restoredKitchen, board.get(0).getGroupId());
    }

    /**
     * A backup written before groups existed carries neither the array nor the per-chore key.
     * Both take their defaults — an empty group list and a null groupId — which already mean
     * "this family never grouped anything".
     */
    @Test
    void legacyBackupWithoutGroups_restoresEverythingUngrouped() {
        Member alex = chores.createHome("Old", "Alex");
        String code = alex.getHomeCode();
        chores.addGroup(code, "Kitchen", "🍳");
        chores.setChoreGroup(chores.tasksOf(code).get(0).getId(), chores.groupsOf(code).get(0).getId());

        String json = backup.export(code)
                .replaceAll("\\s*\"groups\"\\s*:\\s*\\[[^]]*],", "")
                .replaceAll("\\s*\"groupId\"\\s*:\\s*(null|\\d+)\\s*,", "");
        assertFalse(json.contains("\"groupId\""), "the key really is gone from the payload");

        backup.restore(json.getBytes(StandardCharsets.UTF_8), code);

        assertTrue(chores.groupsOf(code).isEmpty());
        assertTrue(chores.tasksOf(code).stream().allMatch(t -> t.getGroupId() == null));
        assertEquals(11, chores.tasksOf(code).size(), "and no chore was lost with the label");
    }

    /** A hand-edited file can point a chore at a group that isn't in it. The chore is a chore
     *  either way — it must land ungrouped rather than vanish. */
    @Test
    void aDanglingGroupIdInABackup_restoresTheChoreAsUngrouped() {
        Member alex = chores.createHome("Odd", "Alex");
        String code = alex.getHomeCode();
        String json = backup.export(code)
                .replaceFirst("\"groupId\"\\s*:\\s*null", "\"groupId\":999999");

        backup.restore(json.getBytes(StandardCharsets.UTF_8), code);

        assertEquals(11, chores.tasksOf(code).size());
        assertTrue(chores.tasksOf(code).stream().allMatch(t -> t.getGroupId() == null));
    }

    /**
     * A backup written before seasons existed has no such key. Jackson only rejects *unknown*
     * properties, so a missing one must deserialize to null — which already means "all year round".
     */
    @Test
    void legacyBackupWithoutSeasons_restoresAsAllYearRound() {
        Member alex = chores.createHome("Old Backup", "Alex");
        String code = alex.getHomeCode();
        String json = backup.export(code).replaceAll("\\s*\"seasons\"\\s*:\\s*(null|\"[^\"]*\")\\s*,", "");
        assertFalse(json.contains("\"seasons\""), "the key really is gone from the payload");

        backup.restore(json.getBytes(StandardCharsets.UTF_8), code);

        List<ChoreTask> restored = chores.tasksOf(code);
        assertEquals(11, restored.size());
        assertTrue(restored.stream().allMatch(t -> t.getSeasons() == null));
        assertTrue(chores.complete(restored.get(0).getId(), chores.membersOf(code).get(0).getId())
                .allowed(), "no tag means doable today");
    }
}
