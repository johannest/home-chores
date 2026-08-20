package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Home;
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
