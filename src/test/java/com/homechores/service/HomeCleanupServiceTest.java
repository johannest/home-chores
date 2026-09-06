package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.CompletionRepository;
import com.homechores.domain.Home;
import com.homechores.domain.HomeRepository;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retention. The empty-home tiers purge only homes abandoned before anyone used them —
 * there, the rule that matters most is the negative one: a home with any history at all
 * survives. The separate total-inactivity tier purges any home unused past its window,
 * but never without first writing the safety export that is the operator's undo.
 */
@SpringBootTest
@Transactional
class HomeCleanupServiceTest {

    @Autowired
    ChoreService service;

    @Autowired
    HomeCleanupService cleanup;

    @Autowired
    HomeRepository homes;

    @Autowired
    MemberRepository members;

    @Autowired
    CompletionRepository completions;

    @Autowired
    BackupService backupService;

    @TempDir
    Path exportDir;

    private static final Instant CUTOFF = Instant.now().minus(Duration.ofDays(30));

    /** A cleanup service with explicit windows, for exercising the configured tiers. */
    private HomeCleanupService withWindows(int abandonedDays, int emptyHours) {
        return withWindows(abandonedDays, emptyHours, 0);
    }

    private HomeCleanupService withWindows(int abandonedDays, int emptyHours, int inactiveDays) {
        return new HomeCleanupService(homes, members, completions, service, backupService,
                abandonedDays, emptyHours, inactiveDays, exportDir.toString());
    }

    /** Backdates a home's activity so it looks untouched for a long time. */
    private Home stale(String code, Duration age) {
        Home home = service.findHome(code).orElseThrow();
        home.setLastActiveAt(Instant.now().minus(age));
        service.saveHome(home);
        return home;
    }

    @Test
    void freshlyCreatedHome_recordsActivity() {
        Member alex = service.createHome("New", "Alex");
        Home home = service.findHome(alex.getHomeCode()).orElseThrow();
        assertNotNull(home.getLastActiveAt(), "creating a home counts as using it");
        assertFalse(cleanup.isAbandoned(home, CUTOFF), "not stale yet");
    }

    @Test
    void abandonedSoloHomeWithNoHistory_isPurged() {
        Member alex = service.createHome("Abandoned", "Alex");
        String code = alex.getHomeCode();
        stale(code, Duration.ofDays(60));

        List<String> purged = cleanup.purgeAbandonedHomes(CUTOFF);

        assertTrue(purged.contains(code));
        assertTrue(service.findHome(code).isEmpty());
        assertTrue(service.membersOf(code).isEmpty(), "its seeded chores and member went too");
    }

    @Test
    void homeWithAnyChoreHistory_isNeverPurged_howeverOld() {
        Member alex = service.createHome("Loved", "Alex");
        String code = alex.getHomeCode();
        service.complete(service.tasksOf(code).get(0).getId(), alex.getId());
        stale(code, Duration.ofDays(365 * 3));

        assertFalse(cleanup.isAbandoned(service.findHome(code).orElseThrow(), CUTOFF));
        assertTrue(cleanup.purgeAbandonedHomes(CUTOFF).isEmpty());
        assertTrue(service.findHome(code).isPresent(), "three years idle but it has history");
    }

    @Test
    void homeSharedWithASecondMember_isNeverPurged() {
        Member alex = service.createHome("Shared", "Alex");
        String code = alex.getHomeCode();
        service.joinHome(code, "Sam");
        stale(code, Duration.ofDays(200));

        assertFalse(cleanup.isAbandoned(service.findHome(code).orElseThrow(), CUTOFF),
                "someone was invited, so it was more than a stray tap");
        assertTrue(service.findHome(code).isPresent());
    }

    /** A rejected completion is still history — it must not count as "never used". */
    @Test
    void homeWithOnlyARejectedCompletion_isNeverPurged() {
        Member alex = service.createHome("Rejected", "Alex");
        String code = alex.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        home.setRequireApproval(true);
        service.saveHome(home);
        var outcome = service.complete(service.tasksOf(code).get(0).getId(), alex.getId());
        service.reject(outcome.completionId(), alex.getId());
        stale(code, Duration.ofDays(90));

        assertFalse(cleanup.isAbandoned(service.findHome(code).orElseThrow(), CUTOFF));
        assertTrue(service.findHome(code).isPresent());
    }

    @Test
    void recentlyUsedEmptyHome_isKept() {
        Member alex = service.createHome("Yesterday", "Alex");
        String code = alex.getHomeCode();
        stale(code, Duration.ofDays(2));

        assertFalse(cleanup.isAbandoned(service.findHome(code).orElseThrow(), CUTOFF));
        assertTrue(service.findHome(code).isPresent());
    }

    @Test
    void purgeLeavesOtherHomesAlone() {
        Member doomed = service.createHome("Doomed", "Alex");
        Member keeper = service.createHome("Keeper", "Robin");
        service.complete(service.tasksOf(keeper.getHomeCode()).get(0).getId(), keeper.getId());
        stale(doomed.getHomeCode(), Duration.ofDays(60));
        stale(keeper.getHomeCode(), Duration.ofDays(60));

        List<String> purged = cleanup.purgeAbandonedHomes(CUTOFF);

        assertEquals(List.of(doomed.getHomeCode()), purged);
        assertTrue(service.findHome(keeper.getHomeCode()).isPresent());
    }

    /** findAbandoned is the dry run an operator can look at before enabling the sweep. */
    @Test
    void findAbandoned_listsCandidatesWithoutDeleting() {
        Member alex = service.createHome("Candidate", "Alex");
        stale(alex.getHomeCode(), Duration.ofDays(60));

        List<Home> candidates = cleanup.findAbandoned(CUTOFF);

        assertTrue(candidates.stream().anyMatch(h -> h.getCode().equals(alex.getHomeCode())));
        assertTrue(service.findHome(alex.getHomeCode()).isPresent(), "dry run deletes nothing");
    }

    @Test
    void disabledByDefault_soTheScheduledSweepDoesNothing() {
        Member alex = service.createHome("Safe", "Alex");
        stale(alex.getHomeCode(), Duration.ofDays(400));

        assertFalse(cleanup.isEnabled(), "retention is opt-in");
        assertEquals(0, cleanup.getAbandonedHomeDays());
        assertEquals(0, cleanup.getEmptyHomeHours());
        assertEquals(0, cleanup.getInactiveHomeDays());
        assertTrue(cleanup.purgeAbandonedHomes().isEmpty(), "no window configured, no purge");
        assertTrue(cleanup.purgeInactiveHomes().isEmpty());
        cleanup.scheduledPurge();
        assertTrue(service.findHome(alex.getHomeCode()).isPresent());
    }

    // ---- Total-inactivity tier: inactive-home-days ------------------------------------

    @Test
    void inactiveTier_purgesAUsedHome_afterTheWindow_withASafetyExport() throws Exception {
        HomeCleanupService strict = withWindows(0, 0, 30);
        assertTrue(strict.isEnabled(), "the inactive window alone enables the sweep");

        Member alex = service.createHome("Dormant", "Alex");
        String code = alex.getHomeCode();
        service.joinHome(code, "Sam");
        service.complete(service.tasksOf(code).get(0).getId(), alex.getId());
        stale(code, Duration.ofDays(60));

        List<String> purged = strict.purgeInactiveHomes();

        assertTrue(purged.contains(code), "even a real family's home goes after total inactivity");
        assertTrue(service.findHome(code).isEmpty());
        // The undo: a full backup was written right before the delete.
        try (var files = Files.list(exportDir)) {
            var export = files.filter(f -> f.getFileName().toString().startsWith(code)).toList();
            assertEquals(1, export.size(), "a home with history is exported before deletion");
            assertTrue(Files.readString(export.get(0)).contains(code));
        }
    }

    /**
     * The whole point of the safety export: a family comes back after the sweep took their
     * board, and the operator can put it back. Deleting and then restoring is the cycle
     * §4.11.2 and the privacy notice both promise, so it is tested as one thing rather
     * than as an export that is merely written and never read.
     */
    @Test
    void aPurgedHomeCanBeRestoredFromItsSafetyExport() throws Exception {
        HomeCleanupService strict = withWindows(0, 0, 30);
        Member alex = service.createHome("Comes Back", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        service.complete(service.tasksOf(code).get(0).getId(), alex.getId());
        service.complete(service.tasksOf(code).get(1).getId(), sam.getId());
        Home before = service.findHome(code).orElseThrow();
        before.setDailyTargetPerMember(3);
        before.setApproveJoin(false);
        service.saveHome(before);
        String pin = service.findHome(code).orElseThrow().getAdminPin();
        int choreCount = service.tasksOf(code).size();
        stale(code, Duration.ofDays(60));

        assertTrue(strict.purgeInactiveHomes().contains(code));
        assertTrue(service.findHome(code).isEmpty(), "gone, along with its admin");

        // The operator's undo: the export is a restorable backup, not just evidence.
        Path export;
        try (var files = Files.list(exportDir)) {
            export = files.filter(f -> f.getFileName().toString().startsWith(code))
                    .findFirst().orElseThrow();
        }
        var result = backupService.restoreAnyHome(Files.readAllBytes(export));

        assertEquals(code, result.homeCode());
        Home after = service.findHome(code).orElseThrow();
        assertEquals("Comes Back", after.getName());
        assertEquals(pin, after.getAdminPin(), "the family's own PIN, so they can get back in");
        assertEquals(3, after.getDailyTargetPerMember());
        assertFalse(after.isApproveJoin(), "and the settings they had chosen");
        assertEquals(2, service.membersOf(code).size());
        assertEquals(choreCount, service.tasksOf(code).size());
        assertEquals(1, service.completionCount(
                service.membersOf(code).stream().filter(m -> m.getName().equals("Sam"))
                        .findFirst().orElseThrow().getId()),
                "Sam's history came back with them");
        assertTrue(service.membersOf(code).stream().anyMatch(Member::isAdmin),
                "someone can administer the home again");
    }

    /**
     * A home the operator has just put back must survive the next nightly sweep. Its
     * createdAt comes from the backup and is by definition old, so activity has to be
     * stamped on restore — otherwise the undo is undone a few hours later.
     */
    @Test
    void aRestoredHomeIsNotPurgedAgainOnTheNextSweep() throws Exception {
        HomeCleanupService strict = withWindows(0, 0, 30);
        Member alex = service.createHome("Second Chance", "Alex");
        String code = alex.getHomeCode();
        service.complete(service.tasksOf(code).get(0).getId(), alex.getId());
        stale(code, Duration.ofDays(60));
        strict.purgeInactiveHomes();

        Path export;
        try (var files = Files.list(exportDir)) {
            export = files.filter(f -> f.getFileName().toString().startsWith(code))
                    .findFirst().orElseThrow();
        }
        backupService.restoreAnyHome(Files.readAllBytes(export));

        assertTrue(strict.purgeInactiveHomes().isEmpty(), "the sweep leaves it alone now");
        assertTrue(service.findHome(code).isPresent());
    }

    @Test
    void inactiveTier_keepsARecentlyUsedHome() {
        HomeCleanupService strict = withWindows(0, 0, 30);
        Member alex = service.createHome("Lively", "Alex");
        service.complete(service.tasksOf(alex.getHomeCode()).get(0).getId(), alex.getId());
        stale(alex.getHomeCode(), Duration.ofDays(10));

        assertTrue(strict.purgeInactiveHomes().isEmpty());
        assertTrue(service.findHome(alex.getHomeCode()).isPresent());
    }

    @Test
    void inactiveTier_purgesAnEmptyHomeWithoutBotheringToExportIt() throws Exception {
        HomeCleanupService strict = withWindows(0, 0, 30);
        Member alex = service.createHome("EmptyOld", "Alex");
        String code = alex.getHomeCode();
        stale(code, Duration.ofDays(60));

        assertTrue(strict.purgeInactiveHomes().contains(code));
        try (var files = Files.list(exportDir)) {
            assertTrue(files.findAny().isEmpty(), "nothing of value, nothing to export");
        }
    }

    @Test
    void inactiveTier_keepsTheHomeWhenTheSafetyExportCannotBeWritten() throws Exception {
        // An unwritable export dir (a file where the directory should be) forces the
        // fail-safe path: no export, no delete.
        Path blocked = exportDir.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        HomeCleanupService strict = new HomeCleanupService(homes, members, completions,
                service, backupService, 0, 0, 30, blocked.toString());

        Member alex = service.createHome("Protected", "Alex");
        service.complete(service.tasksOf(alex.getHomeCode()).get(0).getId(), alex.getId());
        stale(alex.getHomeCode(), Duration.ofDays(60));

        assertTrue(strict.purgeInactiveHomes().isEmpty(), "no export, no delete");
        assertTrue(service.findHome(alex.getHomeCode()).isPresent());
    }

    @Test
    void findInactive_listsCandidatesWithoutDeleting() {
        HomeCleanupService strict = withWindows(0, 0, 30);
        Member alex = service.createHome("DryRun", "Alex");
        service.complete(service.tasksOf(alex.getHomeCode()).get(0).getId(), alex.getId());
        stale(alex.getHomeCode(), Duration.ofDays(60));

        assertTrue(strict.findInactive(CUTOFF).stream()
                .anyMatch(h -> h.getCode().equals(alex.getHomeCode())));
        assertTrue(service.findHome(alex.getHomeCode()).isPresent(), "dry run deletes nothing");
    }

    // ---- Fast tier: empty-home-hours ------------------------------------------------

    @Test
    void fastTier_purgesAnEmptyHomeAfterTheConfiguredHours() {
        HomeCleanupService fast = withWindows(0, 72);
        assertTrue(fast.isEnabled(), "the hours window alone enables the sweep");

        Member alex = service.createHome("DriveBy", "Alex");
        String code = alex.getHomeCode();
        stale(code, Duration.ofHours(96));

        assertTrue(fast.purgeAbandonedHomes().contains(code));
        assertTrue(service.findHome(code).isEmpty());
    }

    @Test
    void fastTier_keepsAnEmptyHomeYoungerThanTheWindow() {
        HomeCleanupService fast = withWindows(0, 72);
        Member alex = service.createHome("StillNew", "Alex");
        stale(alex.getHomeCode(), Duration.ofHours(24));

        assertTrue(fast.purgeAbandonedHomes().isEmpty());
        assertTrue(service.findHome(alex.getHomeCode()).isPresent());
    }

    @Test
    void fastTier_neverTouchesAHomeWithHistoryOrASecondMember() {
        HomeCleanupService fast = withWindows(0, 72);

        Member robin = service.createHome("Used", "Robin");
        service.complete(service.tasksOf(robin.getHomeCode()).get(0).getId(), robin.getId());
        stale(robin.getHomeCode(), Duration.ofDays(30));

        Member alex = service.createHome("Shared", "Alex");
        service.joinHome(alex.getHomeCode(), "Sam");
        stale(alex.getHomeCode(), Duration.ofDays(30));

        assertTrue(fast.purgeAbandonedHomes().isEmpty());
        assertTrue(service.findHome(robin.getHomeCode()).isPresent());
        assertTrue(service.findHome(alex.getHomeCode()).isPresent());
    }

    @Test
    void bothWindowsConfigured_theShorterOneDecides() {
        HomeCleanupService both = withWindows(30, 72);
        Member alex = service.createHome("Tiered", "Alex");
        String code = alex.getHomeCode();
        stale(code, Duration.ofDays(5)); // past 72h, well short of 30 days

        assertTrue(both.purgeAbandonedHomes().contains(code));
    }

    @Test
    void activityIsRecordedWhenAMemberUsesTheHome() {
        Member alex = service.createHome("Active", "Alex");
        String code = alex.getHomeCode();
        stale(code, Duration.ofDays(60));
        Instant before = service.findHome(code).orElseThrow().getLastActiveAt();

        service.touchHome(code);

        Instant after = service.findHome(code).orElseThrow().getLastActiveAt();
        assertTrue(after.isAfter(before), "touching a stale home moves it forward");
        assertFalse(cleanup.isAbandoned(service.findHome(code).orElseThrow(), CUTOFF));
    }

    @Test
    void touchIsThrottled_soEveryPageOpenIsNotAWrite() {
        Member alex = service.createHome("Throttled", "Alex");
        String code = alex.getHomeCode();
        Instant first = service.findHome(code).orElseThrow().getLastActiveAt();

        service.touchHome(code);

        assertEquals(first, service.findHome(code).orElseThrow().getLastActiveAt(),
                "a second touch within the hour leaves the timestamp alone");
    }

    @Test
    void completingAChore_countsAsActivity() {
        Member alex = service.createHome("Chores", "Alex");
        String code = alex.getHomeCode();
        stale(code, Duration.ofDays(60));

        service.complete(service.tasksOf(code).get(0).getId(), alex.getId());

        assertFalse(cleanup.isAbandoned(service.findHome(code).orElseThrow(), CUTOFF));
    }

    @Test
    void joiningAHome_countsAsActivity() {
        Member alex = service.createHome("Joining", "Alex");
        String code = alex.getHomeCode();
        stale(code, Duration.ofDays(60));

        service.joinHome(code, "Sam");

        Instant last = service.findHome(code).orElseThrow().getLastActiveAt();
        assertTrue(last.isAfter(Instant.now().minus(Duration.ofMinutes(5))));
    }
}
