package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionStatus;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Other help": a member describes something they did that no chore covers, and an admin
 * accepts or declines it. The properties that matter are that nothing counts before the
 * decision, and that an accepted entry behaves like any other completion afterwards.
 */
@SpringBootTest
@Transactional
class OtherHelpTest {

    @Autowired
    ChoreService service;

    @Autowired
    CreditService credits;

    @Autowired
    StatsService stats;

    @Autowired
    BackupService backup;

    @Test
    void newHomesAllowOtherHelpByDefault() {
        Member alex = service.createHome("Nest", "Alex");
        assertTrue(service.findHome(alex.getHomeCode()).orElseThrow().isAllowOtherHelp());
    }

    /** The whole point of the gate: freeform text has to be read before it counts. */
    @Test
    void helpWaitsForADecisionEvenWhenChoresNeedNoApproval() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        assertFalse(service.findHome(code).orElseThrow().isRequireApproval());

        Completion help = service.logOtherHelp(code, alex.getId(), "Carried the shopping in")
                .orElseThrow();

        assertEquals(CompletionStatus.PENDING, help.getStatus());
        assertNull(help.getTaskId(), "no chore behind it");
        assertTrue(help.isOtherHelp());
        assertEquals("Carried the shopping in", help.getNote());
        assertEquals(0, service.completionCount(alex.getId()), "nothing counts yet");
        assertEquals(0, service.doneToday(alex.getId()));
    }

    @Test
    void acceptingHelpCountsItAndCanAwardCredits() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Completion help = service.logOtherHelp(alex.getHomeCode(), sam.getId(),
                "Shovelled the neighbour's snow").orElseThrow();

        service.approve(help.getId(), alex.getId(), 5);

        assertEquals(1, service.completionCount(sam.getId()));
        assertEquals(1, service.doneToday(sam.getId()), "counts towards the daily target");
        assertEquals(5, credits.balance(sam.getId()));
        assertTrue(service.pendingOtherHelp(alex.getHomeCode()).isEmpty());
    }

    @Test
    void acceptingWithoutCreditsAwardsNone() {
        Member alex = service.createHome("Nest", "Alex");
        Completion help = service.logOtherHelp(alex.getHomeCode(), alex.getId(), "Fixed a bike")
                .orElseThrow();

        service.approve(help.getId(), alex.getId());

        assertEquals(1, service.completionCount(alex.getId()));
        assertEquals(0, credits.balance(alex.getId()));
    }

    @Test
    void decliningLeavesItUncounted() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Completion help = service.logOtherHelp(alex.getHomeCode(), sam.getId(), "Nothing really")
                .orElseThrow();

        service.reject(help.getId(), alex.getId());

        assertEquals(0, service.completionCount(sam.getId()));
        assertEquals(0, credits.balance(sam.getId()));
        assertTrue(service.pendingOtherHelp(alex.getHomeCode()).isEmpty());
    }

    /** Two queues, because the two decisions differ — but one badge count for the admin. */
    @Test
    void helpHasItsOwnQueueButSharesThePendingBadge() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Home home = service.findHome(code).orElseThrow();
        home.setRequireApproval(true);
        service.saveHome(home);

        ChoreTask chore = service.tasksOf(code).get(0);
        service.complete(chore.getId(), alex.getId());
        service.logOtherHelp(code, alex.getId(), "Walked to the shop for milk");

        assertEquals(1, service.pendingApprovals(code).size(), "chores only");
        assertEquals(1, service.pendingOtherHelp(code).size(), "help only");
        assertEquals(2, service.pendingCount(code), "the Admin tab badge counts both");
        assertEquals(1, service.pendingOtherHelpCount(code, alex.getId()));
    }

    @Test
    void blankTextAndSwitchedOffHomesRecordNothing() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();

        assertTrue(service.logOtherHelp(code, alex.getId(), "   ").isEmpty());
        assertTrue(service.logOtherHelp(code, alex.getId(), null).isEmpty());

        Home home = service.findHome(code).orElseThrow();
        home.setAllowOtherHelp(false);
        service.saveHome(home);
        assertTrue(service.logOtherHelp(code, alex.getId(), "Helped anyway").isEmpty());
        assertTrue(service.pendingOtherHelp(code).isEmpty());
    }

    @Test
    void longDescriptionsAreTrimmedToFit() {
        Member alex = service.createHome("Nest", "Alex");
        Completion help = service.logOtherHelp(alex.getHomeCode(), alex.getId(),
                "x".repeat(ChoreService.MAX_HELP_LENGTH + 50)).orElseThrow();

        assertEquals(ChoreService.MAX_HELP_LENGTH, help.getNote().length());
    }

    /** A typo noticed straight away is the member's own business, as with a mis-tapped chore. */
    @Test
    void theMemberCanTakeBackTheirOwnHelpWhileItIsStillFresh() {
        Member alex = service.createHome("Nest", "Alex");
        Member sam = service.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Completion help = service.logOtherHelp(alex.getHomeCode(), sam.getId(), "Typo").orElseThrow();

        assertFalse(service.undoCompletion(help.getId(), alex.getId()), "not somebody else's");
        assertTrue(service.undoCompletion(help.getId(), sam.getId()));
        assertTrue(service.pendingOtherHelp(alex.getHomeCode()).isEmpty());
    }

    /** Accepted help shows up in the charts, counted apart from the chores it isn't. */
    @Test
    void statsCountAcceptedHelpSeparatelyFromChores() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        service.complete(service.tasksOf(code).get(0).getId(), alex.getId());
        Completion help = service.logOtherHelp(code, alex.getId(), "Cleared the gutters")
                .orElseThrow();
        service.approve(help.getId(), alex.getId());

        StatsService.MyStats mine = stats.myStats(alex.getId(), code);
        assertEquals(2, mine.totalApproved());
        assertEquals(1, mine.byChore().size(), "help is not a chore bar");
        assertEquals(1, mine.otherHelp());

        StatsService.HomeStats home = stats.homeStats(code);
        assertEquals(1, home.otherHelp());
        assertEquals(2, home.perMember().get(0).value());
    }

    /** Help entries have no task to remap, so restore must not mistake them for orphans. */
    @Test
    void backupRoundTripKeepsHelpEntriesAndTheirText() {
        Member alex = service.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Completion pending = service.logOtherHelp(code, alex.getId(), "Painted the shed")
                .orElseThrow();
        Completion accepted = service.logOtherHelp(code, alex.getId(), "Cooked for grandma")
                .orElseThrow();
        service.approve(accepted.getId(), alex.getId(), 3);
        Home home = service.findHome(code).orElseThrow();
        home.setAllowOtherHelp(false);
        service.saveHome(home);

        String json = backup.export(code);
        backup.restore(json.getBytes(StandardCharsets.UTF_8));

        assertFalse(service.findHome(code).orElseThrow().isAllowOtherHelp(), "setting survives");
        Member restored = service.membersOf(code).get(0);
        assertEquals(1, service.completionCount(restored.getId()), "the accepted one still counts");
        List<Completion> waiting = service.pendingOtherHelp(code);
        assertEquals(1, waiting.size());
        assertEquals("Painted the shed", waiting.get(0).getNote());
        assertEquals(3, credits.balance(restored.getId()), "its credits came back too");
        assertEquals(pending.getHomeCode(), code);
    }
}
