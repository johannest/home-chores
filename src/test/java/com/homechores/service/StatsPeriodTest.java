package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.CompletionStatus;
import com.homechores.domain.Member;
import com.homechores.service.StatsService.HomeStats;
import com.homechores.service.StatsService.MyStats;
import com.homechores.service.StatsService.Period;
import com.homechores.service.StatsService.PeriodBucket;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * The statistics period lens: today / this week / this month / all time, and the longer trends.
 *
 * <p>Every case here back-dates {@code doneAt} directly, because that is the only way to make a
 * "last month" row exist inside a test. {@code ChoreService.complete} always stamps the moment of
 * the tap — which is itself worth knowing, and is why nothing but a test writes that field.
 */
@SpringBootTest
@Transactional
class StatsPeriodTest {

    @Autowired ChoreService chores;
    @Autowired StatsService stats;
    @Autowired CompletionRepository completions;

    private static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    /** Logs a chore and back-dates it to {@code daysAgo}, keeping its status. */
    private Completion logOn(String code, Long memberId, int taskIndex, int daysAgo) {
        ChoreTask t = chores.tasksOf(code).get(taskIndex);
        Completion c = completions.findByTaskIdOrderByDoneAtDesc(t.getId()).stream()
                .filter(x -> x.getMemberId().equals(memberId))
                .findFirst().orElse(null);
        chores.complete(t.getId(), memberId);
        Completion saved = completions.findByTaskIdOrderByDoneAtDesc(t.getId()).stream()
                .filter(x -> !x.equals(c))
                .findFirst().orElseThrow();
        saved.setDoneAt(LocalDate.now().minusDays(daysAgo).atTime(12, 0).atZone(zone()).toInstant());
        return completions.save(saved);
    }

    private MyStats mine(Member m, Period p) {
        return stats.myStats(m.getId(), m.getHomeCode(), p, Locale.ENGLISH);
    }

    @Test
    void yesterdayCountsInTheWeekButNotInToday() {
        Member alex = chores.createHome("Periods", "Alex");
        String code = alex.getHomeCode();
        // Monday would make "yesterday" fall in last week, which is a different assertion.
        LocalDate today = LocalDate.now();
        int back = today.getDayOfWeek() == DayOfWeek.MONDAY ? 0 : 1;

        logOn(code, alex.getId(), 0, back);

        assertEquals(back == 0 ? 1 : 0, mine(alex, Period.TODAY).inPeriod());
        assertEquals(1, mine(alex, Period.WEEK).inPeriod());
        assertEquals(1, mine(alex, Period.MONTH).inPeriod());
        assertEquals(1, mine(alex, Period.ALL).inPeriod());
    }

    @Test
    void theWeekStartsOnMonday_matchingTheChoreMasterBadge() {
        Member alex = chores.createHome("Periods", "Alex");
        String code = alex.getHomeCode();
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int sinceMonday = (int) (LocalDate.now().toEpochDay() - monday.toEpochDay());

        logOn(code, alex.getId(), 0, sinceMonday);      // this Monday — inside
        logOn(code, alex.getId(), 1, sinceMonday + 1);  // the Sunday before — outside

        assertEquals(1, mine(alex, Period.WEEK).inPeriod(),
                "the week runs Monday–Sunday, like lastWeekChoreMaster");
        assertEquals(2, mine(alex, Period.ALL).inPeriod());
    }

    @Test
    void aRejectedCompletionCountsInNoPeriod() {
        Member alex = chores.createHome("Periods", "Alex");
        String code = alex.getHomeCode();
        Completion c = logOn(code, alex.getId(), 0, 0);
        c.setStatus(CompletionStatus.REJECTED);
        completions.save(c);

        for (Period p : Period.values()) {
            assertEquals(0, mine(alex, p).inPeriod(), p + " must exclude REJECTED");
        }
    }

    /**
     * A completion approved days after it was done still belongs to the day it was done —
     * {@code doneAt} is never touched by approval, and the member did the chore that day.
     */
    @Test
    void aLateApprovedCompletionBucketsByWhenItWasDone_notWhenItWasApproved() {
        Member alex = chores.createHome("Periods", "Alex");
        String code = alex.getHomeCode();
        var home = chores.findHome(code).orElseThrow();
        home.setRequireApproval(true);
        chores.saveHome(home);

        ChoreTask t = chores.tasksOf(code).get(0);
        chores.complete(t.getId(), alex.getId());
        Completion c = completions.findByTaskIdOrderByDoneAtDesc(t.getId()).get(0);
        c.setDoneAt(LocalDate.now().minusDays(40).atTime(12, 0).atZone(zone()).toInstant());
        completions.save(c);

        chores.approve(c.getId(), alex.getId()); // approved today, done 40 days ago

        assertEquals(0, mine(alex, Period.MONTH).inPeriod(),
                "approving it today must not drag it into this month");
        assertEquals(1, mine(alex, Period.ALL).inPeriod());
    }

    @Test
    void headlineCountsAreAlwaysAllTimeFacts_whateverLensIsSelected() {
        Member alex = chores.createHome("Periods", "Alex");
        String code = alex.getHomeCode();
        logOn(code, alex.getId(), 0, 0);
        logOn(code, alex.getId(), 1, 40);

        for (Period p : Period.values()) {
            var counts = mine(alex, p).counts();
            assertEquals(1, counts.today(), "today, under lens " + p);
            assertEquals(2, counts.allTime(), "all time, under lens " + p);
        }
    }

    @Test
    void trendsAreZeroFilledAndEndWithTheCurrentBucket() {
        Member alex = chores.createHome("Periods", "Alex");
        MyStats s = mine(alex, Period.ALL);

        assertEquals(StatsService.TREND_WEEKS, s.byWeek().size());
        assertEquals(StatsService.TREND_MONTHS, s.byMonth().size());
        assertEquals(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                s.byWeek().get(s.byWeek().size() - 1).start());
        assertEquals(LocalDate.now().withDayOfMonth(1),
                s.byMonth().get(s.byMonth().size() - 1).start());
        assertTrue(s.byWeek().stream().allMatch(b -> b.value() == 0),
                "an empty history is zero-filled, not absent");
        assertTrue(s.byWeek().stream().noneMatch(b -> b.label() == null));
    }

    @Test
    void aChoreDoneLastMonthLandsInItsOwnMonthColumn() {
        Member alex = chores.createHome("Periods", "Alex");
        String code = alex.getHomeCode();
        logOn(code, alex.getId(), 0, 40);

        List<PeriodBucket> months = mine(alex, Period.ALL).byMonth();
        LocalDate target = LocalDate.now().minusDays(40).withDayOfMonth(1);
        assertEquals(1, months.stream().filter(b -> b.start().equals(target))
                .findFirst().orElseThrow().value());
        assertEquals(0, months.get(months.size() - 1).value(), "and not in the current month");
    }

    /**
     * The axis labels have to fit a ~25px column on a phone, in every language. Finnish is the
     * case that broke it: {@code TextStyle.SHORT} gives "marrask.", half again wider than its
     * column, and because the page sets {@code overflow-x: hidden} it would be clipped rather
     * than revealed (SPEC §4.15.1). Hence digits on the axis and the readable form in the
     * tooltip — asserted here so a future "nicer labels" change has to notice.
     */
    @Test
    void trendAxisLabelsStayNarrowInEveryLanguage() {
        Member alex = chores.createHome("Periods", "Alex");
        for (Locale locale : List.of(Locale.ENGLISH, Locale.of("fi"), Locale.of("sv"))) {
            MyStats s = stats.myStats(alex.getId(), alex.getHomeCode(), Period.ALL, locale);
            for (PeriodBucket b : s.byMonth()) {
                assertTrue(b.label().length() <= 2,
                        "month axis label \"" + b.label() + "\" (" + locale + ") must fit its column");
                assertTrue(b.caption().length() > 2,
                        "but the tooltip keeps the readable form");
            }
            for (PeriodBucket b : s.byWeek()) {
                assertTrue(b.label().length() <= 5,
                        "week axis label \"" + b.label() + "\" (" + locale + ") must fit its column");
            }
        }
    }

    @Test
    void homeStatsFollowTheLensForBars_butNotForTheHeadline() {
        Member alex = chores.createHome("Periods", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        logOn(code, alex.getId(), 0, 0);
        logOn(code, sam.getId(), 1, 40);

        HomeStats today = stats.homeStats(code, Period.TODAY, Locale.ENGLISH);
        assertEquals(1, today.perMember().stream().mapToLong(b -> b.value()).sum(),
                "only today's chore is in the bars");
        assertEquals(2, today.counts().allTime(), "but the headline still knows about both");

        HomeStats all = stats.homeStats(code, Period.ALL, Locale.ENGLISH);
        assertEquals(2, all.perMember().stream().mapToLong(b -> b.value()).sum());
    }
}
