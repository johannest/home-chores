package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.homechores.service.StatsService.Redemption;
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

    // ---- 💎 redeemed --------------------------------------------------------------

    /** The member's own list: newest first, the admin's note as the reward, the blank note as
     *  "no note", and earned / redeemed / balance agreeing with the ledger. */
    @Test
    void diamonds_listWhatWasRedeemed_newestFirst_withTheRewardAndTheBalance() {
        Member alex = chores.createHome("Shiny", "Alex");
        String code = alex.getHomeCode();
        ChoreTask oven = chores.addTask(code, "Clean the oven", "🧽", 0, 5);
        chores.complete(oven.getId(), alex.getId());
        chores.complete(oven.getId(), alex.getId());

        assertTrue(creditService.redeem(code, alex.getId(), 3, "ice cream", alex.getId()));
        assertTrue(creditService.redeem(code, alex.getId(), 2, "  ", alex.getId()));

        DiamondStats d = stats.myStats(alex.getId(), code).diamonds();
        assertEquals(10, d.earned().allTime());
        assertEquals(5, d.redeemed());
        assertEquals(5, d.balance(), "earned minus redeemed, the leaderboard's number");
        assertEquals(creditService.balance(alex.getId()), d.balance());

        List<Redemption> list = d.redemptions().recent();
        assertEquals(2, list.size());
        assertEquals(0, d.redemptions().earlier(), "nothing hidden by the cap");
        assertEquals(2, list.get(0).amount(), "the latest redemption comes first");
        assertNull(list.get(0).reward(), "a blank note is no reward, not the word 'Redeemed'");
        assertEquals("ice cream", list.get(1).reward());
        assertEquals(alex.getId(), list.get(1).member().getId());
        assertEquals(LocalDate.now(), list.get(1).on());
    }

    /** Ten rows is a phone screen; the eleventh and twelfth are counted, not listed. */
    @Test
    void diamonds_capTheRedemptionList_andCountWhatTheCapHid() {
        Member alex = chores.createHome("Shiny", "Alex");
        String code = alex.getHomeCode();
        ChoreTask oven = chores.addTask(code, "Clean the oven", "🧽", 0, 20);
        chores.complete(oven.getId(), alex.getId());
        for (int i = 1; i <= StatsService.RECENT_REDEMPTIONS + 2; i++) {
            assertTrue(creditService.redeem(code, alex.getId(), 1, "treat " + i, alex.getId()));
        }

        DiamondStats d = stats.myStats(alex.getId(), code).diamonds();
        assertEquals(StatsService.RECENT_REDEMPTIONS, d.redemptions().recent().size());
        assertEquals(2, d.redemptions().earlier());
        assertEquals("treat 12", d.redemptions().recent().get(0).reward(), "newest first");
        assertEquals("treat 3", d.redemptions().recent().get(9).reward(),
                "treats 1 and 2 are the ones the cap hid");
        assertEquals(12, d.redeemed(), "the total still counts every one");
    }

    /** The admin's list names who redeemed what, newest first — and only this home's. */
    @Test
    void homeStats_listTheHomesRedemptions_byName_andNeverAnotherHomes() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        ChoreTask oven = chores.addTask(code, "Clean the oven", "🧽", 0, 5);
        chores.complete(oven.getId(), alex.getId());
        chores.complete(oven.getId(), sam.getId());
        assertTrue(creditService.redeem(code, sam.getId(), 4, "cinema", alex.getId()));
        assertTrue(creditService.redeem(code, alex.getId(), 1, "coffee", alex.getId()));

        Member other = chores.createHome("Elsewhere", "Kim");
        ChoreTask theirs = chores.addTask(other.getHomeCode(), "Mop", "🧹", 0, 5);
        chores.complete(theirs.getId(), other.getId());
        assertTrue(creditService.redeem(other.getHomeCode(), other.getId(), 5, "secret",
                other.getId()));

        HomeStats s = stats.homeStats(code);
        List<Redemption> list = s.redemptions().recent();
        assertEquals(List.of("Alex", "Sam"),
                list.stream().map(r -> r.member().getName()).toList(), "newest first");
        assertEquals(List.of("coffee", "cinema"), list.stream().map(Redemption::reward).toList());
        assertTrue(list.stream().noneMatch(r -> "secret".equals(r.reward())),
                "another home's redemption never appears");
        assertTrue(stats.homeStats(other.getHomeCode()).redemptions().recent().stream()
                .noneMatch(r -> "cinema".equals(r.reward())), "nor the other way round");

        Member fresh = chores.createHome("Quiet", "Pat");
        assertTrue(stats.homeStats(fresh.getHomeCode()).redemptions().isEmpty(),
                "a home that never redeems has nothing to list");
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
