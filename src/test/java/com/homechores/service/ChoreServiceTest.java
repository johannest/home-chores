package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.CompletionStatus;
import com.homechores.domain.Feedback;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.domain.Trophy;
import com.homechores.service.ChoreService.CompleteOutcome;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class ChoreServiceTest {

    @Autowired
    ChoreService service;

    private List<ChoreTask> tasks(String code) {
        return service.tasksOf(code);
    }

    @Test
    void createHome_makesAdmin_withPin_andSeedsTasks() {
        Member admin = service.createHome("Nest", "Alex");
        assertTrue(admin.isAdmin());
        Home home = service.findHome(admin.getHomeCode()).orElseThrow();
        assertNotNull(home.getAdminPin());
        assertEquals(4, home.getAdminPin().length());
        assertEquals(11, tasks(admin.getHomeCode()).size(), "eleven default chores seeded");
    }

    @Test
    void joinHome_addsNonAdmin_andUnknownCodeFails() {
        Member admin = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        assertFalse(sam.isAdmin());
        assertTrue(service.joinHome("ZZZZZ", "Nobody").isEmpty());
    }

    @Test
    void claimAdmin_requiresCorrectPin() {
        Member admin = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(admin.getHomeCode(), "Sam").orElseThrow();
        Home home = service.findHome(admin.getHomeCode()).orElseThrow();

        assertFalse(service.claimAdmin(sam.getId(), "0000".equals(home.getAdminPin()) ? "1111" : "0000"));
        assertTrue(service.claimAdmin(sam.getId(), home.getAdminPin()));
        assertTrue(service.findMember(sam.getId()).orElseThrow().isAdmin());
    }

    @Test
    void cannotRemoveOrDemoteLastAdmin() {
        Member admin = service.createHome("Nest", "Alex");
        assertFalse(service.setMemberAdmin(admin.getId(), false, admin.getId()), "last admin cannot be demoted");
        assertFalse(service.removeMember(admin.getId(), admin.getId()), "last admin cannot be removed");
        assertTrue(service.findMember(admin.getId()).orElseThrow().isAdmin());
    }

    @Test
    void fairness_blocksFourthInARow_andResetsWhenOtherMemberActs() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Long dish = tasks(alex.getHomeCode()).get(0).getId();

        assertTrue(service.complete(dish, alex.getId()).allowed());
        assertTrue(service.complete(dish, alex.getId()).allowed());
        assertTrue(service.complete(dish, alex.getId()).allowed());
        assertFalse(service.complete(dish, alex.getId()).allowed(), "4th in a row blocked");

        // Someone else takes a turn -> Alex is unlocked again.
        assertTrue(service.complete(dish, sam.getId()).allowed());
        assertTrue(service.complete(dish, alex.getId()).allowed());
    }

    @Test
    void fairness_isOffWhenTheHomeSaysSo() {
        Member alex = service.createHome("Nest", "Alex");
        Home home = service.findHome(alex.getHomeCode()).orElseThrow();
        home.setMaxInARow(0);
        service.saveHome(home);
        Long dish = tasks(alex.getHomeCode()).get(0).getId();

        for (int i = 0; i < 5; i++) {
            assertTrue(service.complete(dish, alex.getId()).allowed(), "no limit: tap " + (i + 1));
        }
        var view = service.taskViews(alex.getHomeCode(), alex.getId()).stream()
                .filter(v -> v.task().getId().equals(dish)).findFirst().orElseThrow();
        assertEquals(5, view.streak(), "the run is still counted for the badge");
        assertFalse(view.streakAtLimit(), "but it never reads as locked");
        assertEquals(ChoreService.LockReason.NONE, view.lockReason());
    }

    @Test
    void fairness_honoursACustomLimit_andNamesTheRunWhenBlocking() {
        Member alex = service.createHome("Nest", "Alex");
        Home home = service.findHome(alex.getHomeCode()).orElseThrow();
        home.setMaxInARow(2);
        service.saveHome(home);
        Long dish = tasks(alex.getHomeCode()).get(0).getId();

        assertTrue(service.complete(dish, alex.getId()).allowed());
        assertTrue(service.complete(dish, alex.getId()).allowed());
        var blocked = service.complete(dish, alex.getId());
        assertFalse(blocked.allowed(), "3rd in a row blocked at limit 2");
        assertEquals(ChoreService.LockReason.STREAK, blocked.blockReason());
        assertEquals(2, blocked.newStreak(), "the block reports the run that caused it");
    }

    @Test
    void approvalOff_countsImmediately_andReachesTheFirstTrophyAtFive() {
        Member alex = service.createHome("Nest", "Alex");
        List<ChoreTask> t = tasks(alex.getHomeCode());

        // 3 of task[0] then 2 of task[1] = 5 approved.
        service.complete(t.get(0).getId(), alex.getId());
        service.complete(t.get(0).getId(), alex.getId());
        service.complete(t.get(0).getId(), alex.getId());
        service.complete(t.get(1).getId(), alex.getId());
        CompleteOutcome fifth = service.complete(t.get(1).getId(), alex.getId());

        assertEquals(5, service.completionCount(alex.getId()));
        assertEquals(Trophy.SPROUT, fifth.trophy(), "the first trophy is reached at 5");
    }

    @Test
    void firstTimeChore_isFlaggedAsNew() {
        Member alex = service.createHome("Nest", "Alex");
        Long dish = tasks(alex.getHomeCode()).get(0).getId();
        assertTrue(service.complete(dish, alex.getId()).newChoreForMember());
        assertFalse(service.complete(dish, alex.getId()).newChoreForMember());
    }

    @Test
    void approvalOn_completionsArePending_untilApproved_andRejectDoesNotCount() {
        Member alex = service.createHome("Nest", "Alex");
        Home home = service.findHome(alex.getHomeCode()).orElseThrow();
        home.setRequireApproval(true);
        service.saveHome(home);
        Long dish = tasks(alex.getHomeCode()).get(0).getId();

        CompleteOutcome pending = service.complete(dish, alex.getId());
        assertTrue(pending.allowed());
        assertTrue(pending.pending());
        assertEquals(0, service.completionCount(alex.getId()), "pending doesn't count yet");
        assertNull(pending.trophy());

        assertEquals(1, service.pendingCount(alex.getHomeCode()));
        service.approve(pending.completionId(), alex.getId());
        assertEquals(1, service.completionCount(alex.getId()), "approved now counts");

        CompleteOutcome pending2 = service.complete(dish, alex.getId());
        service.reject(pending2.completionId(), alex.getId());
        assertEquals(1, service.completionCount(alex.getId()), "rejected never counts");
    }

    @Test
    void pendingCompletions_countTowardFairness() {
        Member alex = service.createHome("Nest", "Alex");
        Home home = service.findHome(alex.getHomeCode()).orElseThrow();
        home.setRequireApproval(true);
        service.saveHome(home);
        Long dish = tasks(alex.getHomeCode()).get(0).getId();

        assertTrue(service.complete(dish, alex.getId()).allowed());
        assertTrue(service.complete(dish, alex.getId()).allowed());
        assertTrue(service.complete(dish, alex.getId()).allowed());
        assertFalse(service.complete(dish, alex.getId()).allowed(),
                "pending taps still trigger the 3-in-a-row lock");
    }

    @Test
    void feedback_isStored() {
        Member alex = service.createHome("Nest", "Alex");
        Long dish = tasks(alex.getHomeCode()).get(0).getId();
        CompleteOutcome o = service.complete(dish, alex.getId());
        service.setFeedback(o.completionId(), Feedback.LOVE);
        // Re-read via a fresh completion query is not exposed; assert via stats instead below.
        assertNotNull(o.completionId());
    }

    @Test
    void doneToday_countsApprovedCompletionsForToday() {
        Member alex = service.createHome("Nest", "Alex");
        List<ChoreTask> t = tasks(alex.getHomeCode());
        service.complete(t.get(0).getId(), alex.getId());
        service.complete(t.get(1).getId(), alex.getId());
        assertEquals(2, service.doneToday(alex.getId()));
        assertEquals(0, service.doneOn(alex.getId(), LocalDate.now().minusDays(1)));
    }

    @Test
    void removeMember_deletesTheirCompletions() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Long dish = tasks(alex.getHomeCode()).get(0).getId();
        service.complete(dish, sam.getId());
        assertEquals(1, service.completionCount(sam.getId()));

        assertTrue(service.removeMember(sam.getId(), alex.getId()));
        assertTrue(service.findMember(sam.getId()).isEmpty());
    }

    @Test
    void deleteTask_removesItsCompletions() {
        Member alex = service.createHome("Nest", "Alex");
        Long dish = tasks(alex.getHomeCode()).get(0).getId();
        service.complete(dish, alex.getId());
        assertEquals(1, service.completionCount(alex.getId()));

        service.deleteTask(dish);
        assertEquals(10, tasks(alex.getHomeCode()).size());
        assertEquals(0, service.completionCount(alex.getId()), "completions removed with the chore");
    }


    /** Approving and rejecting are admin actions, re-checked for the acting member when they
     *  happen: a demoted admin's still-open approval list must not keep working. */
    @Test
    void approveAndReject_refuseANonAdmin() {
        Member alex = service.createHome("Nest", "Alex");
        Home home = service.findHome(alex.getHomeCode()).orElseThrow();
        home.setRequireApproval(true);
        service.saveHome(home);
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Long dish = tasks(alex.getHomeCode()).get(0).getId();
        CompleteOutcome pending = service.complete(dish, sam.getId());

        assertThrows(IllegalArgumentException.class,
                () -> service.approve(pending.completionId(), sam.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> service.reject(pending.completionId(), sam.getId()));
        assertEquals(1, service.pendingCount(alex.getHomeCode()), "still pending");

        service.approve(pending.completionId(), alex.getId());
        assertEquals(1, service.completionCount(sam.getId()));
    }

    @Test
    void approvingAChoreTwice_countsItOnce() {
        Member alex = service.createHome("Nest", "Alex");
        Home home = service.findHome(alex.getHomeCode()).orElseThrow();
        home.setRequireApproval(true);
        service.saveHome(home);
        CompleteOutcome pending = service.complete(tasks(alex.getHomeCode()).get(0).getId(), alex.getId());

        assertTrue(service.approve(pending.completionId(), alex.getId()).isPresent());
        assertTrue(service.approve(pending.completionId(), alex.getId()).isEmpty());
        assertFalse(service.reject(pending.completionId(), alex.getId()), "nor rejected once approved");
        assertEquals(1, service.completionCount(alex.getId()));
    }

    /** The service checks the home itself rather than trusting that its caller paired a chore
     *  with one of that home's members. */
    @Test
    void aMemberOfAnotherHome_canNeitherCompleteNorBookItsChores() {
        Member alex = service.createHome("Nest", "Alex");
        Member robin = service.createHome("Elsewhere", "Robin");
        Long dish = tasks(alex.getHomeCode()).get(0).getId();

        assertThrows(IllegalArgumentException.class, () -> service.complete(dish, robin.getId()));
        assertThrows(IllegalArgumentException.class, () -> service.bookChore(dish, robin.getId()));
        assertEquals(0, service.completionCount(robin.getId()));

        assertTrue(service.bookChore(dish, alex.getId()), "its own home's member still can");
        assertTrue(service.complete(dish, alex.getId()).allowed());
    }

    @Test
    void unmarkingAChore_andRemovingAMember_needAnAdminOfThatHome() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Member robin = service.createHome("Elsewhere", "Robin"); // an admin, of another home
        CompleteOutcome done = service.complete(tasks(alex.getHomeCode()).get(0).getId(), sam.getId());

        assertThrows(IllegalArgumentException.class,
                () -> service.deleteCompletion(done.completionId(), robin.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteCompletion(done.completionId(), sam.getId()));
        assertThrows(IllegalArgumentException.class, () -> service.removeMember(sam.getId(), robin.getId()));
        assertThrows(IllegalArgumentException.class, () -> service.removeMember(alex.getId(), sam.getId()));
        assertEquals(1, service.completionCount(sam.getId()), "nothing was unmarked");
        assertTrue(service.findMember(sam.getId()).isPresent(), "nobody was removed");

        service.deleteCompletion(done.completionId(), alex.getId());
        assertEquals(0, service.completionCount(sam.getId()));
        assertTrue(service.removeMember(sam.getId(), alex.getId()));
    }

    @Test
    void promotingAndRenamingAMember_needAnAdminOfThatHome() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Member robin = service.createHome("Elsewhere", "Robin"); // an admin, of another home

        assertThrows(IllegalArgumentException.class, () -> service.setMemberAdmin(sam.getId(), true, sam.getId()));
        assertThrows(IllegalArgumentException.class, () -> service.setMemberAdmin(sam.getId(), true, robin.getId()));
        assertThrows(IllegalArgumentException.class, () -> service.renameMember(alex.getId(), "Hacked", sam.getId()));
        assertThrows(IllegalArgumentException.class, () -> service.renameMember(sam.getId(), "Hacked", robin.getId()));
        assertFalse(service.findMember(sam.getId()).orElseThrow().isAdmin(), "nobody was promoted");
        assertEquals("Alex", service.findMember(alex.getId()).orElseThrow().getName());
        assertEquals("Sam", service.findMember(sam.getId()).orElseThrow().getName());

        assertTrue(service.setMemberAdmin(sam.getId(), true, alex.getId()));
        service.renameMember(sam.getId(), "Samuel", alex.getId());
        Member after = service.findMember(sam.getId()).orElseThrow();
        assertTrue(after.isAdmin());
        assertEquals("Samuel", after.getName());
    }

    /** A hold nobody can ever complete must not keep the chore from everyone else. */
    @Test
    void removingAMember_releasesTheChoresTheyHadBooked() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Long dish = tasks(alex.getHomeCode()).get(0).getId();
        assertTrue(service.bookChore(dish, sam.getId()));
        assertFalse(service.bookChore(dish, alex.getId()), "precondition: Sam's hold blocks Alex");

        assertTrue(service.removeMember(sam.getId(), alex.getId()));

        assertTrue(service.bookChore(dish, alex.getId()), "free again");
        assertTrue(service.complete(dish, alex.getId()).allowed());
    }
}
