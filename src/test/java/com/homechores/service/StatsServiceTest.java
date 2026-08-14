package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Feedback;
import com.homechores.domain.Member;
import com.homechores.service.ChoreService.CompleteOutcome;
import com.homechores.service.StatsService.HomeStats;
import com.homechores.service.StatsService.MyStats;
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
}
