package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.CounterReset;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * The leaderboard badge counter starts over on a schedule the admin picks, or on demand — and
 * nothing else does. A family that resets the badges every month must keep its all-time stats.
 */
@SpringBootTest
@Transactional
class CounterResetTest {

    @Autowired ChoreService service;
    @Autowired StatsService stats;
    @Autowired CompletionRepository completions;
    @Autowired HomeState homeState;

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private Home setPeriod(String code, CounterReset period) {
        Home h = service.findHome(code).orElseThrow();
        h.setCounterReset(period);
        service.saveHome(h);
        return h;
    }

    /** An approved completion for the member, back-dated so it lands in an earlier period. */
    private void doneAt(Member m, Long taskId, Instant when) {
        Completion c = new Completion(m.getHomeCode(), taskId, m.getId(),
                com.homechores.domain.CompletionStatus.APPROVED);
        c.setDoneAt(when);
        completions.save(c);
    }

    @Test
    void newHomesResetMonthly_andTheBadgeCountsOnlyThisMonth() {
        Member alex = service.createHome("Reset", "Alex");
        String code = alex.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        assertEquals(CounterReset.MONTHLY, home.getCounterReset(), "the documented default");
        assertNull(home.getCounterResetAt());

        Long task = service.tasksOf(code).get(0).getId();
        LocalDate today = LocalDate.now(ZONE);
        doneAt(alex, task, today.withDayOfMonth(1).atStartOfDay(ZONE).toInstant().minusSeconds(1)); // last month
        doneAt(alex, task, today.withDayOfMonth(1).atStartOfDay(ZONE).toInstant());                 // 1st, 00:00
        service.complete(service.tasksOf(code).get(1).getId(), alex.getId());                      // now

        assertEquals(2, service.badgeCount(alex.getId(), home), "this month only, from the 1st at midnight");
        assertEquals(3, service.completionCount(alex.getId()), "the all-time count is untouched");
        assertEquals(3, stats.myStats(alex.getId(), code, StatsService.Period.ALL, Locale.ENGLISH).totalApproved(),
                "statistics keep everything");
    }

    @Test
    void everyPeriodCountsFromItsOwnStart() {
        Member alex = service.createHome("Reset", "Alex");
        String code = alex.getHomeCode();
        Long task = service.tasksOf(code).get(0).getId();
        LocalDate today = LocalDate.now(ZONE);
        Instant now = Instant.now();
        Instant weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZONE).toInstant();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZONE).toInstant();
        Instant yearStart = today.withDayOfYear(1).atStartOfDay(ZONE).toInstant();

        Home never = setPeriod(code, CounterReset.NEVER);
        assertNull(ChoreService.counterSince(never, now), "NEVER: all time");
        assertEquals(weekStart, ChoreService.counterSince(setPeriod(code, CounterReset.WEEKLY), now),
                "weeks start on Monday, like the stats");
        assertEquals(monthStart, ChoreService.counterSince(setPeriod(code, CounterReset.MONTHLY), now));
        assertEquals(yearStart, ChoreService.counterSince(setPeriod(code, CounterReset.YEARLY), now));

        doneAt(alex, task, yearStart.minusSeconds(1)); // last year
        Home yearly = setPeriod(code, CounterReset.YEARLY);
        assertEquals(0, service.badgeCount(alex.getId(), yearly));
        assertEquals(1, service.badgeCount(alex.getId(), setPeriod(code, CounterReset.NEVER)));
    }

    @Test
    void resetNow_zeroesTheBadges_untilTheNextPeriodStartsAfterIt() {
        Member alex = service.createHome("Reset", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        service.complete(service.tasksOf(code).get(0).getId(), alex.getId());
        service.complete(service.tasksOf(code).get(1).getId(), sam.getId());
        Home home = setPeriod(code, CounterReset.NEVER);
        assertEquals(1, service.badgeCount(alex.getId(), home));

        double before = homeState.revision(code).peek();
        service.resetCounters(code);
        home = service.findHome(code).orElseThrow();

        assertTrue(home.getCounterResetAt() != null, "the reset moment is remembered");
        assertEquals(0, service.badgeCount(alex.getId(), home), "Alex's badge starts over");
        assertEquals(0, service.badgeCount(sam.getId(), home), "so does Sam's");
        assertEquals(1, service.completionCount(alex.getId()), "history is intact");
        assertEquals(2, stats.homeStats(code, StatsService.Period.ALL, Locale.ENGLISH).counts().allTime(),
                "and so are the home statistics");

        // Under a periodic schedule the manual reset only matters until the next period begins.
        Instant nextMonth = LocalDate.now(ZONE).withDayOfMonth(1).plusMonths(1).atStartOfDay(ZONE).toInstant();
        home.setCounterReset(CounterReset.MONTHLY);
        assertEquals(home.getCounterResetAt(), ChoreService.counterSince(home, Instant.now()),
                "the reset is more recent than this month's start, so it wins");
        assertEquals(nextMonth, ChoreService.counterSince(home, nextMonth.plusSeconds(60)),
                "next month starts fresh from the 1st, not from the old reset");
    }
}
