package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionStatus;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.service.ChoreService.CompleteOutcome;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * An admin recording a chore somebody else did — the member without a phone, or the one
 * who forgot to tap. It states what happened, so it goes in whatever the locks would have
 * said about who was supposed to do it next.
 */
@SpringBootTest
@Transactional
class LogForMemberTest {

    @Autowired
    ChoreService service;

    @Autowired
    CreditService credits;

    @Test
    void logsTheChoreForTheOtherMemberNotTheAdmin() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        ChoreTask chore = service.tasksOf(alex.getHomeCode()).get(0);

        CompleteOutcome outcome = service.completeFor(chore.getId(), sam.getId(), alex.getId());

        assertTrue(outcome.allowed());
        assertEquals(sam.getId(), outcome.member().getId());
        assertEquals(1, service.completionCount(sam.getId()));
        assertEquals(0, service.completionCount(alex.getId()), "not the admin's chore");
        assertEquals(1, service.doneToday(sam.getId()), "counts towards their daily target");
    }

    /** Approval is about trusting the claim; an admin making the claim settles that. */
    @Test
    void countsImmediatelyEvenWhenTheHomeRequiresApproval() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        home.setRequireApproval(true);
        service.saveHome(home);
        Member sam = service.joinHome(code, "Sam").orElseThrow();

        service.completeFor(service.tasksOf(code).get(0).getId(), sam.getId(), alex.getId());

        assertEquals(1, service.completionCount(sam.getId()));
        assertTrue(service.pendingApprovals(code).isEmpty(), "nothing left to approve");
    }

    /** Who logged it is part of the history, so the family can see whose word it was. */
    @Test
    void recordsTheAdminAsTheReviewer() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        service.completeFor(service.tasksOf(alex.getHomeCode()).get(0).getId(),
                sam.getId(), alex.getId());

        Completion logged = service.recentCompletions(alex.getHomeCode(), 1).get(0);
        assertEquals(CompletionStatus.APPROVED, logged.getStatus());
        assertEquals(alex.getId(), logged.getReviewedByMemberId());
        assertEquals(sam.getId(), logged.getMemberId());
    }

    /** The locks steer who does what next; they can't argue with what already happened. */
    @Test
    void goesInDespiteTheFairnessStreak() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        service.joinHome(code, "Sam").orElseThrow();
        Member sam = service.membersOf(code).get(1);
        ChoreTask chore = service.tasksOf(code).get(0);
        for (int i = 0; i < ChoreService.MAX_IN_A_ROW; i++) {
            service.complete(chore.getId(), sam.getId());
        }
        assertEquals(ChoreService.LockReason.STREAK,
                service.complete(chore.getId(), sam.getId()).blockReason(),
                "sam's own tap is locked out by the streak rule");

        service.completeFor(chore.getId(), sam.getId(), alex.getId());

        assertEquals(ChoreService.MAX_IN_A_ROW + 1, service.completionCount(sam.getId()));
    }

    @Test
    void goesInBeforeAnIntervalChoreIsDueAgain() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        ChoreTask plants = service.tasksOf(code).stream()
                .filter(t -> t.getIntervalDays() > 0).findFirst().orElseThrow();
        service.complete(plants.getId(), sam.getId());
        assertTrue(service.nextDueDate(plants).isAfter(LocalDate.now()), "not due again yet");

        service.completeFor(plants.getId(), sam.getId(), alex.getId());

        assertEquals(2, service.completionCount(sam.getId()));
    }

    @Test
    void awardsTheChoresCreditsToTheMemberWhoDidIt() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        ChoreTask chore = service.tasksOf(code).get(0);
        service.updateTask(chore.getId(), chore.getName(), chore.getEmoji(), 0, 4, null);

        service.completeFor(chore.getId(), sam.getId(), alex.getId());

        assertEquals(4, credits.balance(sam.getId()));
        assertEquals(0, credits.balance(alex.getId()));
    }

    /** It clears a booking for the same reason completing normally does: it's settled. */
    @Test
    void clearsAnyBookingOnTheChore() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        ChoreTask chore = service.tasksOf(code).get(0);
        service.bookChore(chore.getId(), sam.getId());

        service.completeFor(chore.getId(), sam.getId(), alex.getId());

        assertTrue(service.taskViews(code, sam.getId()).stream()
                .filter(v -> v.task().getId().equals(chore.getId()))
                .allMatch(v -> v.bookedById() == null));
    }

    /** A logged chore is an ordinary completion, so the unmark list can take it back. */
    @Test
    void canBeUnmarkedLikeAnyOtherCompletion() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        CompleteOutcome outcome = service.completeFor(
                service.tasksOf(alex.getHomeCode()).get(0).getId(), sam.getId(), alex.getId());

        service.deleteCompletion(outcome.completionId());

        assertEquals(0, service.completionCount(sam.getId()));
    }

    @Test
    void refusesAMemberFromAnotherHome() {
        Member alex = service.createHome("Nest", "Alex");
        Member other = service.createHome("Elsewhere", "Robin");
        List<ChoreTask> chores = service.tasksOf(alex.getHomeCode());

        assertThrows(IllegalArgumentException.class,
                () -> service.completeFor(chores.get(0).getId(), other.getId(), alex.getId()));
    }
}
