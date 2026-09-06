package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.Member;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** The 🥇 "chore master of last week" header badge (previous ISO week, Mon–Sun). */
@SpringBootTest
@Transactional
class ChoreMasterTest {

    @Autowired ChoreService service;
    @Autowired StatsService stats;
    @Autowired CompletionRepository completions;

    /** Noon on a day inside the previous ISO week (offset 0 = last Monday). */
    private static Instant lastWeek(int dayOffset) {
        LocalDate lastMonday = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        return lastMonday.plusDays(dayOffset).atTime(12, 0)
                .atZone(ZoneId.systemDefault()).toInstant();
    }

    private void completeAt(String code, Long memberId, int taskIndex, Instant when) {
        var outcome = service.complete(service.tasksOf(code).get(taskIndex).getId(), memberId);
        Completion c = completions.findById(outcome.completionId()).orElseThrow();
        c.setDoneAt(when);
        completions.save(c);
    }

    @Test
    void mostApprovedCompletionsLastWeek_wins() {
        Member alex = service.createHome("Masters", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();

        completeAt(code, alex.getId(), 0, lastWeek(0));
        completeAt(code, sam.getId(), 1, lastWeek(1));
        completeAt(code, sam.getId(), 2, lastWeek(6)); // Sunday still counts

        var master = stats.lastWeekChoreMaster(code).orElseThrow();
        assertEquals(sam.getId(), master.member().getId());
        assertEquals(2, master.count());
    }

    @Test
    void tie_goesToTheEarliestJoinedMember() {
        Member alex = service.createHome("Tied", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();

        completeAt(code, sam.getId(), 0, lastWeek(2));
        completeAt(code, alex.getId(), 1, lastWeek(3));

        var master = stats.lastWeekChoreMaster(code).orElseThrow();
        assertEquals(alex.getId(), master.member().getId(), "Alex joined first");
    }

    @Test
    void thisWeeksWork_doesNotCount_andABlankWeekHasNoMaster() {
        Member alex = service.createHome("Blank", "Alex");
        String code = alex.getHomeCode();
        service.joinHome(code, "Sam");
        service.complete(service.tasksOf(code).get(0).getId(), alex.getId()); // today

        assertTrue(stats.lastWeekChoreMaster(code).isEmpty(),
                "only the previous ISO week counts");
    }

    @Test
    void soloHome_hasNoMaster() {
        Member alex = service.createHome("Solo", "Alex");
        completeAt(alex.getHomeCode(), alex.getId(), 0, lastWeek(2));

        assertTrue(stats.lastWeekChoreMaster(alex.getHomeCode()).isEmpty(),
                "a competition of one is no competition");
    }
}
