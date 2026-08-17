package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.CompletionStatus;
import com.homechores.domain.Member;
import com.homechores.service.CreditService.Award;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CreditServiceTest {

    @Autowired ChoreService chores;
    @Autowired CreditService creditService;
    @Autowired CompletionRepository completionRepo;

    @Test
    void choreCredits_areOptIn_andAwardedOnCompletion() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();

        ChoreTask free = chores.addTask(code, "Easy", "🙂", 0, 0);   // 0 credits
        ChoreTask hard = chores.addTask(code, "Hard", "💪", 0, 5);   // 5 credits

        chores.complete(free.getId(), alex.getId());
        assertEquals(0, creditService.balance(alex.getId()), "opt-in: no credits by default");

        chores.complete(hard.getId(), alex.getId());
        assertEquals(5, creditService.balance(alex.getId()), "challenging chore awards its credits");
    }

    @Test
    void spreeTier_awardedOnceForConsecutiveDays() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        creditService.addTier(code, 2, 10); // 2 days in a row -> 10 credits
        ChoreTask t = chores.tasksOf(code).get(0); // seeded, 0 credit value

        // Two approved completions: yesterday and today -> a 2-day streak.
        completionRepo.save(dated(code, t.getId(), alex.getId(), Instant.now().minus(Duration.ofDays(1))));
        completionRepo.save(dated(code, t.getId(), alex.getId(), Instant.now()));

        // null completion id: this test drives the award directly, without a real tap.
        Award award = creditService.onApprovedCompletion(t, alex.getId(), code, null);
        assertEquals(2, award.spreeDays());
        assertEquals(10, award.spreeCredits());
        assertEquals(10, creditService.balance(alex.getId()));

        // Re-evaluating in the same streak must not award again.
        Award again = creditService.onApprovedCompletion(t, alex.getId(), code, null);
        assertNull(again.spreeDays());
        assertEquals(10, creditService.balance(alex.getId()));
    }

    @Test
    void redeem_reducesBalance_andRejectsOverspend() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        ChoreTask hard = chores.addTask(code, "Hard", "💪", 0, 7);
        chores.complete(hard.getId(), alex.getId());
        assertEquals(7, creditService.balance(alex.getId()));

        assertFalse(creditService.redeem(code, alex.getId(), 100, "too much", alex.getId()));
        assertEquals(7, creditService.balance(alex.getId()));

        assertTrue(creditService.redeem(code, alex.getId(), 3, "ice cream", alex.getId()));
        assertEquals(4, creditService.balance(alex.getId()));
    }

    private static Completion dated(String code, Long taskId, Long memberId, Instant when) {
        Completion c = new Completion(code, taskId, memberId, CompletionStatus.APPROVED);
        c.setDoneAt(when);
        return c;
    }
}
