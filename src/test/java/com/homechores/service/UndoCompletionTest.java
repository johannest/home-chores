package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.CompletionStatus;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.service.ChoreService.CompleteOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Taking back a chore logged by accident — the member's own short-window undo, and the
 * admin's unmark. The property that matters most is that credits go back with it.
 */
@SpringBootTest
@Transactional
class UndoCompletionTest {

    @Autowired
    ChoreService service;

    @Autowired
    CreditService credits;

    @Autowired
    CompletionRepository completions;

    /** Backdates a completion so it falls outside the member's undo window. */
    private void backdate(Long completionId, Duration age) {
        Completion c = completions.findById(completionId).orElseThrow();
        c.setDoneAt(Instant.now().minus(age));
        completions.save(c);
    }

    private ChoreTask paidChore(String code, int creditValue) {
        ChoreTask task = service.tasksOf(code).get(0);
        service.updateTask(task.getId(), task.getName(), task.getEmoji(), 0, creditValue, null);
        return service.tasksOf(code).get(0);
    }

    @Test
    void newHomesAskForConfirmationByDefault() {
        Member alex = service.createHome("Nest", "Alex");
        assertTrue(service.findHome(alex.getHomeCode()).orElseThrow().isConfirmCompletion());
    }

    @Test
    void memberCanUndoTheirOwnRecentChore() {
        Member alex = service.createHome("Nest", "Alex");
        CompleteOutcome done = service.complete(service.tasksOf(alex.getHomeCode()).get(0).getId(),
                alex.getId());
        assertEquals(1, service.completionCount(alex.getId()));

        assertTrue(service.undoCompletion(done.completionId(), alex.getId()));

        assertEquals(0, service.completionCount(alex.getId()));
        assertTrue(completions.findById(done.completionId()).isEmpty());
    }

    @Test
    void undoTakesBackTheCreditsItEarned() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        ChoreTask paid = paidChore(code, 5);

        CompleteOutcome done = service.complete(paid.getId(), alex.getId());
        assertEquals(5, credits.balance(alex.getId()), "precondition: the chore paid out");

        assertTrue(service.undoCompletion(done.completionId(), alex.getId()));

        assertEquals(0, credits.balance(alex.getId()),
                "an undone chore must not leave credits behind, or it could be farmed");
    }

    @Test
    void undoTakesBackASpreeBonusToo() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        credits.addTier(code, 1, 20); // a one-day spree, awarded on the first chore of a day

        CompleteOutcome done = service.complete(service.tasksOf(code).get(0).getId(), alex.getId());
        assertTrue(credits.balance(alex.getId()) >= 20, "precondition: spree paid out");

        assertTrue(service.undoCompletion(done.completionId(), alex.getId()));
        assertEquals(0, credits.balance(alex.getId()));
    }

    @Test
    void cannotUndoSomeoneElsesChore() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        CompleteOutcome done = service.complete(service.tasksOf(alex.getHomeCode()).get(0).getId(),
                alex.getId());

        assertFalse(service.undoCompletion(done.completionId(), sam.getId()));
        assertEquals(1, service.completionCount(alex.getId()), "Alex's chore still stands");
    }

    @Test
    void cannotUndoOnceTheWindowHasPassed() {
        Member alex = service.createHome("Nest", "Alex");
        CompleteOutcome done = service.complete(service.tasksOf(alex.getHomeCode()).get(0).getId(),
                alex.getId());
        backdate(done.completionId(), ChoreService.UNDO_WINDOW.plusMinutes(1));

        assertFalse(service.undoCompletion(done.completionId(), alex.getId()),
                "beyond the window it is an admin correction, not a self-service undo");
        assertEquals(1, service.completionCount(alex.getId()));
    }

    @Test
    void undoableCompletion_offersOnlyTheMembersOwnRecentOne() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        List<ChoreTask> tasks = service.tasksOf(alex.getHomeCode());

        assertTrue(service.undoableCompletion(alex.getId()).isEmpty(), "nothing done yet");

        service.complete(tasks.get(0).getId(), sam.getId());
        assertTrue(service.undoableCompletion(alex.getId()).isEmpty(), "Sam's chore is not Alex's");

        CompleteOutcome mine = service.complete(tasks.get(1).getId(), alex.getId());
        assertEquals(mine.completionId(),
                service.undoableCompletion(alex.getId()).orElseThrow().getId());

        backdate(mine.completionId(), ChoreService.UNDO_WINDOW.plusMinutes(1));
        assertTrue(service.undoableCompletion(alex.getId()).isEmpty(), "window closed");
    }

    /** The admin's escape hatch: unmark anything, however old, credits included. */
    @Test
    void adminCanUnmarkAnOldApprovedChore() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        ChoreTask paid = paidChore(code, 3);
        CompleteOutcome done = service.complete(paid.getId(), alex.getId());
        backdate(done.completionId(), Duration.ofDays(4));

        service.deleteCompletion(done.completionId());

        assertEquals(0, service.completionCount(alex.getId()));
        assertEquals(0, credits.balance(alex.getId()));
    }

    @Test
    void unmarkingAPendingChoreLeavesNothingBehind() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        home.setRequireApproval(true);
        service.saveHome(home);

        CompleteOutcome done = service.complete(service.tasksOf(code).get(0).getId(), alex.getId());
        assertEquals(1, service.pendingApprovals(code).size());

        service.deleteCompletion(done.completionId());
        assertTrue(service.pendingApprovals(code).isEmpty());
        assertEquals(0, credits.balance(alex.getId()));
    }

    @Test
    void undoingFreesTheChoreForTheFairnessRuleAgain() {
        Member alex = service.createHome("Nest", "Alex");
        Long taskId = service.tasksOf(alex.getHomeCode()).get(0).getId();
        for (int i = 0; i < ChoreService.MAX_IN_A_ROW; i++) {
            service.complete(taskId, alex.getId());
        }
        assertFalse(service.complete(taskId, alex.getId()).allowed(), "locked out by the streak");

        Completion last = service.undoableCompletion(alex.getId()).orElseThrow();
        assertTrue(service.undoCompletion(last.getId(), alex.getId()));

        assertTrue(service.complete(taskId, alex.getId()).allowed(),
                "one fewer in the run, so the chore is available again");
    }

    @Test
    void recentCompletions_listsNewestFirstAcrossMembers() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        List<ChoreTask> tasks = service.tasksOf(alex.getHomeCode());
        CompleteOutcome first = service.complete(tasks.get(0).getId(), alex.getId());
        backdate(first.completionId(), Duration.ofHours(2));
        CompleteOutcome second = service.complete(tasks.get(1).getId(), sam.getId());

        List<Completion> recent = service.recentCompletions(alex.getHomeCode(), 15);

        assertEquals(2, recent.size());
        assertEquals(second.completionId(), recent.get(0).getId(), "newest first");
        assertEquals(first.completionId(), recent.get(1).getId());
    }

    @Test
    void recentCompletions_includesRejectedOnesSoTheyCanBeTidiedAway() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        home.setRequireApproval(true);
        service.saveHome(home);
        CompleteOutcome done = service.complete(service.tasksOf(code).get(0).getId(), alex.getId());
        service.reject(done.completionId(), alex.getId());

        List<Completion> recent = service.recentCompletions(code, 15);
        assertEquals(1, recent.size());
        assertEquals(CompletionStatus.REJECTED, recent.get(0).getStatus());
    }
}
