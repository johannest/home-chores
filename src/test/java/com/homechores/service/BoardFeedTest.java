package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.CompletionStatus;
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
 * The board's home-wide "Done today" feed and the per-member daily celebration tier
 * (doneTodayAfter on CompleteOutcome).
 */
@SpringBootTest
@Transactional
class BoardFeedTest {

    @Autowired ChoreService service;
    @Autowired CompletionRepository completions;

    private static void backdate(CompletionRepository repo, Long completionId, Duration age) {
        Completion c = repo.findById(completionId).orElseThrow();
        c.setDoneAt(Instant.now().minus(age));
        repo.save(c);
    }

    // ---- doneTodayList --------------------------------------------------------

    @Test
    void doneTodayList_showsTodayOnly_newestFirst_withNamesResolved() {
        Member alex = service.createHome("Feed", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();

        List<ChoreTask> tasks = service.tasksOf(code);
        var first = service.complete(tasks.get(0).getId(), alex.getId());
        var second = service.complete(tasks.get(1).getId(), sam.getId());
        var old = service.complete(tasks.get(2).getId(), alex.getId());
        backdate(completions, old.completionId(), Duration.ofDays(1));

        List<ChoreService.DoneToday> rows = service.doneTodayList(code);

        assertEquals(2, rows.size(), "yesterday's completion is not today's news");
        assertEquals(second.completionId(), rows.get(0).completion().getId(), "newest first");
        assertEquals("Sam", rows.get(0).memberName());
        assertEquals("Alex", rows.get(1).memberName());
        assertTrue(rows.get(1).text().contains(tasks.get(0).getName()));
        // first is used above to keep the ordering assertion honest
        assertEquals(first.completionId(), rows.get(1).completion().getId());
    }

    @Test
    void doneTodayList_includesPending_excludesRejected_andResolvesOtherHelp() {
        Member alex = service.createHome("FeedGated", "Alex");
        String code = alex.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        home.setRequireApproval(true);
        service.saveHome(home);

        var pending = service.complete(service.tasksOf(code).get(0).getId(), alex.getId());
        var rejected = service.complete(service.tasksOf(code).get(1).getId(), alex.getId());
        service.reject(rejected.completionId(), alex.getId());
        service.logOtherHelp(code, alex.getId(), "Carried the shopping in");

        List<ChoreService.DoneToday> rows = service.doneTodayList(code);

        assertEquals(2, rows.size(), "pending + other-help; the rejected one is gone");
        assertTrue(rows.stream().anyMatch(r ->
                r.completion().getId().equals(pending.completionId())
                        && r.completion().getStatus() == CompletionStatus.PENDING));
        assertTrue(rows.stream().anyMatch(r -> r.text().contains("Carried the shopping in")),
                "other help reads as the member's own words");
    }

    // ---- doneTodayAfter (celebration tiers) -----------------------------------

    @Test
    void doneTodayAfter_countsTheDayIncludingTheNewCompletion() {
        Member alex = service.createHome("Tiers", "Alex");
        String code = alex.getHomeCode();
        List<ChoreTask> tasks = service.tasksOf(code);

        assertEquals(1, service.complete(tasks.get(0).getId(), alex.getId()).doneTodayAfter());
        assertEquals(2, service.complete(tasks.get(1).getId(), alex.getId()).doneTodayAfter());
        assertEquals(3, service.complete(tasks.get(2).getId(), alex.getId()).doneTodayAfter());
    }

    @Test
    void doneTodayAfter_ignoresYesterday() {
        Member alex = service.createHome("Fresh", "Alex");
        String code = alex.getHomeCode();
        var yesterday = service.complete(service.tasksOf(code).get(0).getId(), alex.getId());
        backdate(completions, yesterday.completionId(), Duration.ofDays(1));

        var today = service.complete(service.tasksOf(code).get(1).getId(), alex.getId());

        assertEquals(1, today.doneTodayAfter(), "a new day starts the count over");
    }

    @Test
    void pendingOutcome_reportsZero_andApproveReportsTheApprovedCount() {
        Member alex = service.createHome("Gated", "Alex");
        String code = alex.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        home.setRequireApproval(true);
        service.saveHome(home);

        var pending = service.complete(service.tasksOf(code).get(0).getId(), alex.getId());
        assertTrue(pending.pending());
        assertEquals(0, pending.doneTodayAfter(), "not counted until approved");

        var approved = service.approve(pending.completionId(), alex.getId());
        assertEquals(1, approved.doneTodayAfter(), "approval is when it starts counting");
    }
}
