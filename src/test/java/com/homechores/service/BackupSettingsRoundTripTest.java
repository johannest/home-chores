package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.DivisionStyle;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every per-home setting must survive a backup round-trip.
 *
 * <p>{@code BackupServiceTest} checks the data (members, chores, completions) and three of
 * the settings. This one exists because a setting that is simply absent from
 * {@code BackupService.HomeDto} fails silently: the restore resets it to the field default
 * and nobody notices until a family's home comes back with a gate they had turned off.
 * Asserting the whole set is what makes adding a setting and forgetting the DTO a test
 * failure rather than a support ticket.
 */
@SpringBootTest
@Transactional
class BackupSettingsRoundTripTest {

    @Autowired ChoreService chores;
    @Autowired BackupService backup;

    /** Flips every setting away from its default, so a dropped field cannot pass by luck. */
    private String setUpHomeWithEverythingNonDefault(String code) {
        Home h = chores.findHome(code).orElseThrow();
        h.setName("Renamed House");
        h.setRequireApproval(true);        // default false
        h.setApproveRejoin(false);         // default true
        h.setApproveJoin(false);           // default true
        h.setConfirmCompletion(false);     // default true
        h.setAllowOtherHelp(false);        // default true
        h.setRotationEnforced(false);      // default true
        h.setDivisionStyle(DivisionStyle.ROTATING); // default DEFAULT
        h.setDailyTargetPerMember(3);      // default 1
        h.setBookingTimeoutHours(12);      // default 4
        chores.saveHome(h);
        return h.getAdminPin();
    }

    @Test
    void everyHomeSettingSurvivesTheRoundTrip() {
        Member alex = chores.createHome("Settings", "Alex");
        String code = alex.getHomeCode();
        String pin = setUpHomeWithEverythingNonDefault(code);

        String json = backup.export(code);
        backup.restore(json.getBytes(StandardCharsets.UTF_8), code);

        Home after = chores.findHome(code).orElseThrow();
        assertEquals("Renamed House", after.getName());
        assertEquals(pin, after.getAdminPin());
        assertTrue(after.isRequireApproval(), "requireApproval");
        assertFalse(after.isApproveRejoin(), "approveRejoin");
        assertFalse(after.isApproveJoin(), "approveJoin — the join gate is a setting too");
        assertFalse(after.isConfirmCompletion(), "confirmCompletion");
        assertFalse(after.isAllowOtherHelp(), "allowOtherHelp");
        assertFalse(after.isRotationEnforced(), "rotationEnforced");
        assertEquals(DivisionStyle.ROTATING, after.getDivisionStyle(), "divisionStyle");
        assertEquals(3, after.getDailyTargetPerMember(), "dailyTargetPerMember");
        assertEquals(12, after.getBookingTimeoutHours(), "bookingTimeoutHours");
    }

    /**
     * A backup written before a setting existed has no such key, and must restore with the
     * documented default rather than Jackson's {@code false} — the same rule the boxed
     * Booleans in {@code HomeDto} exist for. Stripping the keys simulates the old file.
     */
    @Test
    void aBackupWrittenBeforeASettingExisted_restoresWithItsDefault() {
        Member alex = chores.createHome("Old File", "Alex");
        String code = alex.getHomeCode();
        setUpHomeWithEverythingNonDefault(code);

        String json = backup.export(code)
                .replaceAll("\\s*\"approveRejoin\"\\s*:\\s*(true|false|null)\\s*,", "")
                .replaceAll("\\s*\"approveJoin\"\\s*:\\s*(true|false|null)\\s*,", "")
                .replaceAll("\\s*\"confirmCompletion\"\\s*:\\s*(true|false|null)\\s*,", "")
                .replaceAll("\\s*\"allowOtherHelp\"\\s*:\\s*(true|false|null)\\s*,", "");

        backup.restore(json.getBytes(StandardCharsets.UTF_8), code);

        Home after = chores.findHome(code).orElseThrow();
        assertTrue(after.isApproveRejoin(), "absent rejoin gate means gated, not open");
        assertTrue(after.isApproveJoin(), "absent join gate means gated, not open");
        assertTrue(after.isConfirmCompletion());
        assertTrue(after.isAllowOtherHelp());
    }

    /**
     * The operator's undo path: the home is gone (retention purged it, or an admin deleted
     * it) and the backup has to bring the whole thing back from nothing. This takes the
     * {@code new Home(...)} branch in restore, where every setting comes from the file
     * alone — there is no surviving row left to inherit anything from.
     */
    @Test
    void aHomeThatNoLongerExists_isFullyRebuiltFromItsBackup() {
        Member alex = chores.createHome("Gone", "Alex");
        String code = alex.getHomeCode();
        chores.joinHome(code, "Sam");
        chores.complete(chores.tasksOf(code).get(0).getId(), alex.getId());
        String pin = setUpHomeWithEverythingNonDefault(code);
        String json = backup.export(code);

        assertTrue(chores.deleteHome(code), "the home is really gone");
        assertTrue(chores.findHome(code).isEmpty());

        var result = backup.restore(json.getBytes(StandardCharsets.UTF_8), code);

        assertEquals(2, result.members());
        assertEquals(1, result.completions());
        Home after = chores.findHome(code).orElseThrow();
        assertEquals("Renamed House", after.getName());
        assertEquals(pin, after.getAdminPin(), "the family's PIN comes back, not 0000");
        assertTrue(after.isRequireApproval());
        assertFalse(after.isApproveRejoin(), "approveRejoin");
        assertFalse(after.isApproveJoin(), "approveJoin");
        assertFalse(after.isConfirmCompletion(), "confirmCompletion");
        assertFalse(after.isAllowOtherHelp(), "allowOtherHelp");
        assertEquals(DivisionStyle.ROTATING, after.getDivisionStyle());
        assertEquals(12, after.getBookingTimeoutHours());
    }

    /**
     * A home brought back from a backup must not be swept away again the same night.
     * Its {@code createdAt} comes from the file and can be months old, so without a fresh
     * {@code lastActiveAt} the retention sweep would see it as untouched since creation.
     */
    @Test
    void aRestoredHomeCountsAsActiveRightNow() {
        Member alex = chores.createHome("Revived", "Alex");
        String code = alex.getHomeCode();
        Home old = chores.findHome(code).orElseThrow();
        old.setCreatedAt(java.time.Instant.now().minus(java.time.Duration.ofDays(400)));
        chores.saveHome(old);
        String json = backup.export(code);
        chores.deleteHome(code);

        backup.restore(json.getBytes(StandardCharsets.UTF_8), code);

        Home after = chores.findHome(code).orElseThrow();
        assertTrue(after.lastActiveOrCreated()
                        .isAfter(java.time.Instant.now().minus(java.time.Duration.ofMinutes(5))),
                "restoring a home is someone using it");
    }
}
