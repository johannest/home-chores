package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.DivisionStyle;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * How many SQL statements one board render costs.
 *
 * <p>{@code taskViews} is the hottest read in the app: every member's board runs it, and it re-runs
 * for every connected device on every {@code HomeState} bump — so one person tapping a chore pays
 * this cost once per open board. These tests pin the count so it cannot quietly grow.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class BoardRenderCostTest {

    @Autowired ChoreService service;
    @Autowired EntityManagerFactory emf;
    @PersistenceContext EntityManager em;

    private Statistics stats() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    /**
     * SQL statements issued by one full board render.
     *
     * <p>Clears the persistence context first. Production serves each render in its own session
     * (spring.jpa.open-in-view), so entities are re-read every time; without the clear, a second
     * render inside this test's single transaction would be served from the first-level cache and
     * report a cost no real request ever pays.
     */
    private long renderCost(String homeCode, Long memberId) {
        em.flush();
        em.clear();
        Statistics s = stats();
        s.clear();
        List<ChoreService.TaskView> views = service.taskViews(homeCode, memberId);
        assertTrue(views.size() > 0, "render produced no views");
        return s.getPrepareStatementCount();
    }

    /** A ceiling, not an exact figure: it should trip on a real regression, not on one new lookup. */
    @Test
    void seededHome_staysWellUnderTwoStatementsPerChore() {
        Member alex = service.createHome("Nest", "Alex");
        long cost = renderCost(alex.getHomeCode(), alex.getId());
        System.out.println(">>> seeded home (10 anytime + 1 weekly, 0 completions): " + cost);
        assertTrue(cost <= 20, "a fresh 11-chore board should not cost " + cost + " statements");
    }

    @Test
    void howOftenAChoreRepeats_doesNotChangeWhatItCostsToRender() {
        Member alex = service.createHome("Anytime", "Alex");
        String anytimeCode = alex.getHomeCode();
        for (ChoreTask t : service.tasksOf(anytimeCode)) {
            service.deleteTask(t.getId());
        }
        for (int i = 0; i < 12; i++) {
            service.addTask(anytimeCode, "Anytime " + i, "🧹", 0);
        }
        long anytimeCost = renderCost(anytimeCode, alex.getId());

        Member sam = service.createHome("Interval", "Sam");
        String intervalCode = sam.getHomeCode();
        for (ChoreTask t : service.tasksOf(intervalCode)) {
            service.deleteTask(t.getId());
        }
        for (int i = 0; i < 12; i++) {
            service.addTask(intervalCode, "Weekly " + i, "🪴", 7);
        }
        long intervalCost = renderCost(intervalCode, sam.getId());

        System.out.println(">>> 12 anytime chores : " + anytimeCost);
        System.out.println(">>> 12 weekly chores  : " + intervalCost);
        System.out.println(">>> per-chore delta   : "
                + ((intervalCost - anytimeCost) / 12.0) + " extra statements per interval chore");

        // The invariant worth guarding: how often a chore repeats must not change what it costs to
        // draw. It used to cost 2 extra statements each, because isDue and nextDueDate re-queried
        // the completions taskViews had already loaded. The frequency presets make interval chores
        // the common case, so this is the regression that would hurt most.
        assertEquals(anytimeCost, intervalCost,
                "an interval chore must cost no more to render than an anytime one");
    }

    @Test
    void rotatingDivision_addsAConstant_notAPerChoreCost() {
        Member alex = service.createHome("Rot", "Alex");
        String code = alex.getHomeCode();
        service.joinHome(code, "Sam");
        service.joinHome(code, "Kim");
        long defaultCost = renderCost(code, alex.getId());

        Home home = service.findHome(code).orElseThrow();
        home.setDivisionStyle(DivisionStyle.ROTATING);
        service.saveHome(home);
        long rotatingCost = renderCost(code, alex.getId());

        System.out.println(">>> free-for-all, 3 members, 11 chores : " + defaultCost);
        System.out.println(">>> rotating,     3 members, 11 chores : " + rotatingCost);

        // Rotation resolves the day's assignment from lists it loads once, so it must not
        // reintroduce a per-chore query.
        assertTrue(rotatingCost - defaultCost <= 4,
                "rotation added " + (rotatingCost - defaultCost) + " statements for 11 chores");
    }

    @Test
    void costGrowsLinearlyNotQuadraticallyWithChoreCount() {
        Member alex = service.createHome("Small", "Alex");
        String small = alex.getHomeCode();
        for (ChoreTask t : service.tasksOf(small)) {
            service.deleteTask(t.getId());
        }
        for (int i = 0; i < 5; i++) {
            service.addTask(small, "C" + i, "🪴", 7);
        }
        long c5 = renderCost(small, alex.getId());

        Member sam = service.createHome("Big", "Sam");
        String big = sam.getHomeCode();
        for (ChoreTask t : service.tasksOf(big)) {
            service.deleteTask(t.getId());
        }
        for (int i = 0; i < 25; i++) {
            service.addTask(big, "C" + i, "🪴", 7);
        }
        long c25 = renderCost(big, sam.getId());

        double slope = (c25 - c5) / 20.0;
        System.out.println(">>> 5 interval chores  : " + c5);
        System.out.println(">>> 25 interval chores : " + c25);
        System.out.println(">>> slope              : " + slope + " statements per chore");

        // One query per chore for its completions, and nothing else that scales with chore count.
        assertEquals(1.0, slope, 0.001,
                "board render must stay at one statement per chore; " + slope + " means a new N+1");
    }
}
