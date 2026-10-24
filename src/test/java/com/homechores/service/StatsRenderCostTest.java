package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.CreditEntry;
import com.homechores.domain.CreditEntryRepository;
import com.homechores.domain.CreditType;
import com.homechores.domain.Feedback;
import com.homechores.domain.Member;
import com.homechores.service.StatsService.Period;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * How much history one Stats render loads.
 *
 * <p>A Stats tab is rebuilt on every {@code HomeState} bump, for every device parked on it, and
 * completions are never purged — so a render that walked the whole ledger would grow with the
 * years and could be made to run in a loop by anyone in the home. These pin that a lens other
 * than all-time loads the trend window only, while the all-time facts stay all-time.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class StatsRenderCostTest {

    @Autowired ChoreService chores;
    @Autowired StatsService stats;
    @Autowired CompletionRepository completions;
    @Autowired CreditEntryRepository credits;
    @Autowired EntityManagerFactory emf;
    @PersistenceContext EntityManager em;

    private static Instant daysAgo(long days) {
        return LocalDate.now().minusDays(days).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant();
    }

    /** Rows of {@code entity} that one read actually loaded, from a cleared persistence context. */
    private long loadedBy(Class<?> entity, Runnable read) {
        em.flush();
        em.clear();
        Statistics s = emf.unwrap(SessionFactory.class).getStatistics();
        s.clear();
        read.run();
        return s.getEntityStatistics(entity.getName()).getLoadCount();
    }

    private Completion done(Long taskId, Long memberId, long daysAgo, Feedback feedback) {
        ChoreService.CompleteOutcome outcome = chores.complete(taskId, memberId);
        Completion c = completions.findById(outcome.completionId()).orElseThrow();
        c.setDoneAt(daysAgo(daysAgo));
        if (feedback != null) {
            chores.setFeedback(c.getId(), feedback);
        }
        return completions.save(c);
    }

    @Test
    void aLensOtherThanAllTime_walksTheTrendWindowOnly_andStillCountsAllTime() {
        Member alex = chores.createHome("Old timers", "Alex");
        String code = alex.getHomeCode();
        ChoreTask dishes = chores.tasksOf(code).get(0);
        done(dishes.getId(), alex.getId(), 2 * 365, Feedback.LOVE); // two years ago
        done(dishes.getId(), alex.getId(), 0, null);                 // today

        assertEquals(1, loadedBy(Completion.class, () -> stats.homeStats(code, Period.WEEK, Locale.ENGLISH)),
                "the week lens loads the trend window, not the ledger");
        assertEquals(1, loadedBy(Completion.class, () -> stats.myStats(alex.getId(), code, Period.WEEK, Locale.ENGLISH)));
        assertEquals(2, loadedBy(Completion.class, () -> stats.homeStats(code, Period.ALL, Locale.ENGLISH)),
                "the all-time lens is the one that reads everything");

        StatsService.HomeStats home = stats.homeStats(code, Period.WEEK, Locale.ENGLISH);
        assertEquals(2, home.counts().allTime(), "all-time is a count, not a walk");
        assertEquals(1, home.counts().today());
        assertEquals(1, home.perMember().get(0).value(), "the bars follow the lens");
        assertEquals(1, home.feedbackByChore().get(0).split().love(),
                "feedback per chore stays all-time under a bounded lens");

        StatsService.MyStats mine = stats.myStats(alex.getId(), code, Period.WEEK, Locale.ENGLISH);
        assertEquals(2, mine.totalApproved());
        assertEquals(2, mine.counts().allTime());
        assertEquals(1, mine.inPeriod());
    }

    @Test
    void theDiamondCard_readsTheHeadlineWindow_andSumsTheRest() {
        Member alex = chores.createHome("Savers", "Alex");
        String code = alex.getHomeCode();
        CreditEntry old = credits.save(new CreditEntry(code, alex.getId(), 7, CreditType.EARNED, "then", 0));
        old.setCreatedAt(daysAgo(400));
        credits.save(old);
        credits.save(new CreditEntry(code, alex.getId(), 3, CreditType.EARNED, "now", 0));
        CreditEntry spent = credits.save(new CreditEntry(code, alex.getId(), 4, CreditType.REDEEMED, "ice cream", 0));
        spent.setCreatedAt(daysAgo(300));
        credits.save(spent);

        assertEquals(2, loadedBy(CreditEntry.class, () -> stats.diamondStats(alex.getId(), LocalDate.now())),
                "today's earning and the redemption; the old earning is only summed");
        StatsService.DiamondStats d = stats.diamondStats(alex.getId(), LocalDate.now());
        assertEquals(10, d.earned().allTime());
        assertEquals(3, d.earned().today());
        assertEquals(4, d.redeemed());
        assertEquals(6, d.balance());
        assertEquals("ice cream", d.redemptions().recent().get(0).reward());
    }
}
