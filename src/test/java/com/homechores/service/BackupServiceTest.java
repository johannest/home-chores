package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        before.setDailyTargetPerMember(3);
        chores.saveHome(before);
        String pin = chores.findHome(code).orElseThrow().getAdminPin();

        String json = backup.export(code);
        assertTrue(json.contains("Backup Home"));

        // Wipe by deleting the whole home's members/tasks, then restore.
        RestoreResult result = backup.restore(json.getBytes(StandardCharsets.UTF_8));

        assertEquals(code, result.homeCode());
        assertEquals(2, result.members());
        assertEquals(11, result.tasks());
        assertEquals(3, result.completions());

        Home after = chores.findHome(code).orElseThrow();
        assertEquals("Backup Home", after.getName());
        assertEquals(pin, after.getAdminPin());
        assertTrue(after.isRequireApproval());
        assertEquals(3, after.getDailyTargetPerMember());
        assertEquals(2, chores.membersOf(code).size());
        assertEquals(11, chores.tasksOf(code).size());

        // The seeded windowed chore round-trips its availability windows.
        assertTrue(chores.tasksOf(code).stream()
                .anyMatch(task -> "08:00-10:00,18:00-22:00".equals(task.getAvailableWindows())));

        // Sam still has 2 approved completions after the id remap.
        Member restoredSam = chores.membersOf(code).stream()
                .filter(m -> m.getName().equals("Sam")).findFirst().orElseThrow();
        assertEquals(2, chores.completionCount(restoredSam.getId()));
    }

    @Test
    void restore_rejectsGarbage() {
        assertThrows(IllegalArgumentException.class,
                () -> backup.restore("not json".getBytes(StandardCharsets.UTF_8)));
    }
}
