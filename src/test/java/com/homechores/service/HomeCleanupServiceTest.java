package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.Home;
import com.homechores.domain.Member;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retention: purging homes abandoned before anyone used them. The rule that matters most
 * is the negative one — a home with any history at all must survive indefinitely.
 */
@SpringBootTest
@Transactional
class HomeCleanupServiceTest {

    @Autowired
    ChoreService service;

    @Autowired
    HomeCleanupService cleanup;

    private static final Instant CUTOFF = Instant.now().minus(Duration.ofDays(30));

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
        assertTrue(cleanup.purgeAbandonedHomes().isEmpty(), "no window configured, no purge");
        cleanup.scheduledPurge();
        assertTrue(service.findHome(alex.getHomeCode()).isPresent());
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
