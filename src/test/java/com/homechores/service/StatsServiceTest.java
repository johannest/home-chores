package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.CreditEntry;
import com.homechores.domain.CreditEntryRepository;
import com.homechores.domain.Feedback;
import com.homechores.domain.Member;
import com.homechores.service.ChoreService.CompleteOutcome;
import com.homechores.service.StatsService.DiamondStats;
import com.homechores.service.StatsService.HomeStats;
import com.homechores.service.StatsService.MemberChores;
import com.homechores.service.StatsService.MyStats;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StatsServiceTest {

    @Autowired
    ChoreService chores;
    @Autowired
    StatsService stats;
    @Autowired
    CreditEntryRepository credits;
    @Autowired
    CreditService creditService;

    @Test
    void myStats_reflectsCompletionsAndFeedback() {
        Member alex = chores.createHome("Nest", "Alex");
        List<ChoreTask> t = chores.tasksOf(alex.getHomeCode());

        CompleteOutcome a = chores.complete(t.get(0).getId(), alex.getId());
        chores.setFeedback(a.completionId(), Feedback.LOVE);
        chores.complete(t.get(1).getId(), alex.getId());

        MyStats s = stats.myStats(alex.getId(), alex.getHomeCode());
        assertEquals(2, s.totalApproved());
        assertEquals(1, s.feedback().love());
        assertEquals(2, s.byChore().size(), "two distinct chores done");
        assertEquals(7, s.last7().size());
        assertEquals(2, s.doneToday());
    }

    @Test
    void homeStats_aggregatesPerMemberAndPopularityAndPending() {
        Member alex = chores.createHome("Nest", "Alex");
        Member sam = chores.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        List<ChoreTask> t = chores.tasksOf(alex.getHomeCode());

        chores.complete(t.get(0).getId(), alex.getId());
        chores.complete(t.get(0).getId(), sam.getId());
        chores.complete(t.get(1).getId(), sam.getId());

        HomeStats s = stats.homeStats(alex.getHomeCode());
        assertEquals(2, s.perMember().size());
        // Sam did 2, Alex did 1
        long samCount = s.perMember().stream().filter(b -> b.label().equals("Sam"))
                .findFirst().orElseThrow().value();
        assertEquals(2, samCount);
        assertEquals(14, s.trend14().size());
        assertEquals(0, s.pending());
        assertTrue(s.chorePopularity().stream().anyMatch(b -> b.value() == 2),
                "the dishwasher was done twice");
    }

    // ---- 💎 earned ------------------------------------------------------------

    @Test
    void diamonds_countWhatTheMemberEarned_andFollowAnUndo() {
        Member alex = chores.createHome("Shiny", "Alex");
        String code = alex.getHomeCode();
        ChoreTask oven = chores.addTask(code, "Clean the oven", "🧽", 0, 2);
        ChoreTask plain = chores.tasksOf(code).get(0);

        CompleteOutcome paid = chores.complete(oven.getId(), alex.getId());
        chores.complete(plain.getId(), alex.getId());

        DiamondStats d = stats.myStats(alex.getId(), code).diamonds();
        assertEquals(2, d.earned().today(), "the oven pays two, the plain chore nothing");
        assertEquals(2, d.earned().thisWeek());
        assertEquals(2, d.earned().thisMonth());
        assertEquals(2, d.earned().allTime());
        assertEquals(7, d.last7().size());
        assertEquals(2, d.last7().get(6).value(), "the last column is today");

        assertTrue(chores.undoCompletion(paid.completionId(), alex.getId()));
        assertEquals(0, stats.myStats(alex.getId(), code).diamonds().earned().allTime(),
                "undoing the chore takes its diamonds back from the chart too");
    }

    /** Spending is the admin's line in the ledger; the chart answers "what did I earn". */
    @Test
    void diamonds_ignoreRedemptions_andBucketYesterdayOutOfToday() {
        Member alex = chores.createHome("Shiny", "Alex");
        String code = alex.getHomeCode();
        ChoreTask oven = chores.addTask(code, "Clean the oven", "🧽", 0, 5);
        chores.complete(oven.getId(), alex.getId());

        // Back-date the award to yesterday noon: only a test can write createdAt, which is the
        // point — nothing in the app moves a credit between days.
        LocalDate today = LocalDate.now();
        for (CreditEntry e : credits.findByMemberId(alex.getId())) {
            e.setCreatedAt(today.minusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant());
            credits.save(e);
        }
        creditService.redeem(code, alex.getId(), 3, "ice cream", alex.getId());

        DiamondStats d = stats.diamondStats(alex.getId(), today);
        assertEquals(0, d.earned().today(), "yesterday's award is not today's");
        assertEquals(5, d.earned().allTime(), "and the redemption did not shrink what was earned");
        assertEquals(5, d.last7().get(5).value(), "yesterday is the second-to-last column");
        assertEquals(2, creditService.balance(alex.getId()), "while the balance did go down");
    }

    // ---- each member's own chores, for the admin -------------------------------

    @Test
    void homeStats_carryEachMembersOwnChoreBars_inJoinOrder_skippingTheIdle() {
        Member alex = chores.createHome("Nest", "Alex");
        Member sam = chores.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        chores.joinHome(alex.getHomeCode(), "Kim").orElseThrow(); // does nothing
        List<ChoreTask> t = chores.tasksOf(alex.getHomeCode());

        chores.complete(t.get(0).getId(), alex.getId());
        chores.complete(t.get(0).getId(), sam.getId());
        chores.complete(t.get(1).getId(), sam.getId());
        chores.logOtherHelpAsAdmin(alex.getHomeCode(), alex.getId(), "Carried the shopping", 0);

        List<MemberChores> rows = stats.homeStats(alex.getHomeCode()).perMemberChores();
        assertEquals(List.of("Alex", "Sam"),
                rows.stream().map(r -> r.member().getName()).toList(),
                "join order, and Kim — who did nothing — has no fold");

        MemberChores alexRow = rows.get(0);
        assertEquals(1, alexRow.byChore().size(), "one chore type");
        assertEquals(1, alexRow.otherHelp(), "plus the help the admin logged");
        assertEquals(2, alexRow.total(), "the total counts both");

        MemberChores samRow = rows.get(1);
        assertEquals(2, samRow.byChore().size(), "two distinct chores");
        assertEquals(t.get(0).getEmoji() + " " + t.get(0).getName(), samRow.byChore().get(0).label(),
                "bars in board order, like the member's own chart");
        assertEquals(0, samRow.otherHelp());
        assertEquals(2, samRow.total());
    }
}
