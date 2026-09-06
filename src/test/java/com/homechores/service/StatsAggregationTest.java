package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Feedback;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.service.StatsService.CountBar;
import com.homechores.service.StatsService.HomeStats;
import com.homechores.service.StatsService.MyStats;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Characterization of the statistics aggregates, written to pin the exact shape of every
 * list {@code StatsService} returns before its inner loops are rewritten: which rows are
 * included, which statuses count, what order they come out in, and how "other help"
 * (a completion with no chore) is kept apart from chores.
 *
 * <p>{@code StatsServiceTest} covers the happy path; this covers the edges that a
 * one-pass rewrite could plausibly get wrong and nothing else would notice — a bar that
 * silently disappears when its count is zero, a REJECTED row leaking into a total, an
 * order that stops matching the chart's legend.
 */
@SpringBootTest
@Transactional
class StatsAggregationTest {

    @Autowired ChoreService chores;
    @Autowired StatsService stats;

    private static long valueOf(List<CountBar> bars, String label) {
        return bars.stream().filter(b -> b.label().equals(label)).findFirst().orElseThrow().value();
    }

    /** Alex + Sam, approval on, a spread of APPROVED / PENDING / REJECTED and other help. */
    private String busyHome() {
        Member alex = chores.createHome("Busy", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        List<ChoreTask> t = chores.tasksOf(code);

        var a1 = chores.complete(t.get(0).getId(), alex.getId());   // APPROVED
        chores.setFeedback(a1.completionId(), Feedback.LOVE);
        var s1 = chores.complete(t.get(0).getId(), sam.getId());    // APPROVED
        chores.setFeedback(s1.completionId(), Feedback.HATE);
        var s2 = chores.complete(t.get(1).getId(), sam.getId());    // -> REJECTED
        chores.reject(s2.completionId(), alex.getId());

        // Other help: always PENDING; accept one, leave one waiting.
        var accepted = chores.logOtherHelp(code, sam.getId(), "carried the shopping in")
                .orElseThrow();
        chores.approve(accepted.getId(), alex.getId(), 2);
        chores.logOtherHelp(code, alex.getId(), "still waiting on this one");
        return code;
    }

    @Test
    void homeStats_perMember_hasARowPerMemberInJoinOrder_countingApprovedOnly() {
        String code = busyHome();
        HomeStats s = stats.homeStats(code);

        assertEquals(List.of("Alex", "Sam"),
                s.perMember().stream().map(CountBar::label).toList(),
                "one bar per member, in join order");
        assertEquals(1, valueOf(s.perMember(), "Alex"), "one approved chore");
        assertEquals(2, valueOf(s.perMember(), "Sam"),
                "one approved chore plus the accepted help; the rejected one does not count");
    }

    @Test
    void homeStats_chorePopularity_keepsEveryChoreInCreationOrder_evenAtZero() {
        String code = busyHome();
        List<ChoreTask> tasks = chores.tasksOf(code);
        HomeStats s = stats.homeStats(code);

        assertEquals(tasks.size(), s.chorePopularity().size(), "a bar per chore, zeroes included");
        assertEquals(tasks.stream().map(t -> t.getEmoji() + " " + t.getName()).toList(),
                s.chorePopularity().stream().map(CountBar::label).toList(),
                "chore creation order, emoji-prefixed label");
        assertEquals(2, s.chorePopularity().get(0).value(), "first chore done by both");
        assertEquals(0, s.chorePopularity().get(1).value(), "second chore's only try was rejected");
    }

    @Test
    void homeStats_feedbackByChore_listsOnlyChoresThatGotAny_andIgnoresRejected() {
        String code = busyHome();
        HomeStats s = stats.homeStats(code);

        assertEquals(1, s.feedbackByChore().size(),
                "only the dishwasher has feedback; the rejected row's HATE is excluded");
        var only = s.feedbackByChore().get(0);
        assertEquals(chores.tasksOf(code).get(0).getId(), only.task().getId());
        assertEquals(1, only.split().love());
        assertEquals(1, only.split().hate());
        assertEquals(2, only.split().total());
    }

    @Test
    void homeStats_countsAcceptedOtherHelpApart_andPendingIncludesIt() {
        String code = busyHome();
        HomeStats s = stats.homeStats(code);

        assertEquals(1, s.otherHelp(), "one accepted help entry, counted outside the chores");
        assertEquals(1, s.pending(), "the undecided help entry is the only thing waiting");
    }

    @Test
    void homeStats_trendAndAdherence_haveFixedShapes() {
        String code = busyHome();
        HomeStats s = stats.homeStats(code);

        assertEquals(14, s.trend14().size());
        assertEquals(java.time.LocalDate.now(), s.trend14().get(13).date(), "ends today");
        assertEquals(3, s.trend14().get(13).value(), "3 approved home-wide today");

        assertEquals(2, s.adherence().size());
        var sam = s.adherence().stream().filter(a -> a.member().getName().equals("Sam"))
                .findFirst().orElseThrow();
        assertEquals(2, sam.doneToday());
        assertEquals(1, sam.target(), "the home's target rides along on every row");
    }

    @Test
    void myStats_byChore_dropsZeroes_andExcludesOtherHelpFromTheChoreBars() {
        String code = busyHome();
        Member sam = chores.membersOf(code).stream()
                .filter(m -> m.getName().equals("Sam")).findFirst().orElseThrow();

        MyStats s = stats.myStats(sam.getId(), code);

        assertEquals(2, s.totalApproved(), "one chore + one accepted help");
        assertEquals(1, s.byChore().size(), "only chores with a count of their own appear");
        assertEquals(1, s.otherHelp(), "help is reported separately for the view to label");
        assertFalse(s.byChore().stream().anyMatch(b -> b.label().contains("shopping")),
                "a member's own words never become a chore bar");
    }

    @Test
    void myStats_feedback_countsMyNonRejectedRowsOnly() {
        String code = busyHome();
        Member sam = chores.membersOf(code).stream()
                .filter(m -> m.getName().equals("Sam")).findFirst().orElseThrow();

        MyStats s = stats.myStats(sam.getId(), code);

        assertEquals(1, s.feedback().hate(), "Sam's approved HATE");
        assertEquals(0, s.feedback().love(), "Alex's LOVE is not Sam's");
        assertEquals(1, s.feedback().total(), "the rejected row is excluded");
    }

    @Test
    void myStats_last7AndTarget() {
        String code = busyHome();
        Member alex = chores.membersOf(code).get(0);
        Home home = chores.findHome(code).orElseThrow();
        home.setDailyTargetPerMember(3);
        chores.saveHome(home);

        MyStats s = stats.myStats(alex.getId(), code);

        assertEquals(7, s.last7().size());
        assertEquals(java.time.LocalDate.now(), s.last7().get(6).date(), "ends today");
        assertEquals(1, s.last7().get(6).value());
        assertEquals(1, s.doneToday());
        assertEquals(3, s.target());
    }

    /** One family's numbers must never pick up another's. */
    @Test
    void aggregatesAreScopedToOneHome() {
        String mine = busyHome();
        Member other = chores.createHome("Elsewhere", "Robin");
        chores.complete(chores.tasksOf(other.getHomeCode()).get(0).getId(), other.getId());

        HomeStats s = stats.homeStats(mine);
        assertEquals(2, s.perMember().size(), "Robin is not in this home");
        assertTrue(s.perMember().stream().noneMatch(b -> b.label().equals("Robin")));

        Member alex = chores.membersOf(mine).get(0);
        assertEquals(1, stats.myStats(alex.getId(), mine).totalApproved());
    }

    /** The chore-master badge reads the previous ISO week, so a blank one shows nothing. */
    @Test
    void choreMaster_isEmptyForAQuietPreviousWeek() {
        String code = busyHome();
        assertTrue(stats.lastWeekChoreMaster(code).isEmpty(),
                "everything in this fixture happened today");
    }
}
