package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.CompletionStatus;
import com.homechores.domain.Home;
import com.homechores.domain.Member;
import com.homechores.domain.Trophy;
import com.homechores.service.ChoreService.CompleteOutcome;
import com.homechores.service.StatsService.TrophyShelf;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Trophies (SPEC §4.6) and their 💎 rewards (§4.7). */
@SpringBootTest
@Transactional
class TrophyTest {

    @Autowired ChoreService chores;
    @Autowired CreditService credits;
    @Autowired StatsService stats;
    @Autowired BackupService backup;
    @Autowired CompletionRepository completionRepo;

    /** Names each test tap's chore apart. */
    private int taps = 0;

    // ---- The ladder ---------------------------------------------------------

    @Test
    void theLadder_answersBestNextAndExactRung() {
        assertTrue(Trophy.best(4).isEmpty());
        assertEquals(Trophy.SPROUT, Trophy.best(5).orElseThrow());
        assertEquals(Trophy.BRONZE, Trophy.best(49).orElseThrow());
        assertEquals(Trophy.SILVER, Trophy.next(25).orElseThrow(), "next is strictly above");
        assertTrue(Trophy.next(1000).isEmpty(), "a legend has nothing left to aim for");
        assertEquals(Trophy.LEGEND, Trophy.reachedAt(1000).orElseThrow());
        assertTrue(Trophy.reachedAt(6).isEmpty(), "only an exact rung is reached");
        assertEquals(List.of(Trophy.SPROUT, Trophy.HELPER), Trophy.earned(24));
    }

    // ---- Reaching a trophy ----------------------------------------------------

    @Test
    void theRewardIsPaidAtTheRung_once_andNotBelowIt() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        assertTrue(credits.setTrophyReward(code, Trophy.SPROUT, 7, alex.getId()));
        approvedBefore(alex, 3);

        CompleteOutcome fourth = complete(alex);
        assertNull(fourth.trophy());
        assertEquals(0, credits.balance(alex.getId()), "nothing below the rung");

        CompleteOutcome fifth = complete(alex);
        assertEquals(Trophy.SPROUT, fifth.trophy());
        assertEquals(7, fifth.award().trophyCredits());
        assertEquals(7, credits.balance(alex.getId()));

        CompleteOutcome sixth = complete(alex);
        assertNull(sixth.trophy());
        assertEquals(7, credits.balance(alex.getId()), "past the rung pays nothing more");
    }

    @Test
    void aTrophyWithNoReward_isStillReached_andPaysNothing() {
        Member alex = chores.createHome("Nest", "Alex");
        approvedBefore(alex, 4);

        CompleteOutcome fifth = complete(alex);

        assertEquals(Trophy.SPROUT, fifth.trophy());
        assertEquals(0, fifth.award().trophyCredits());
        assertEquals(0, credits.balance(alex.getId()));
    }

    @Test
    void undoingTheChoreThatReachedIt_takesTheTrophyAndItsDiamondsBack_andRedoingPaysAgain() {
        Member alex = chores.createHome("Nest", "Alex");
        credits.setTrophyReward(alex.getHomeCode(), Trophy.SPROUT, 7, alex.getId());
        approvedBefore(alex, 4);

        CompleteOutcome fifth = complete(alex);
        assertTrue(chores.undoCompletion(fifth.completionId(), alex.getId()));

        assertEquals(0, credits.balance(alex.getId()), "the reward went with the chore");
        assertTrue(Trophy.best(chores.completionCount(alex.getId())).isEmpty(), "and the trophy");

        CompleteOutcome again = complete(alex);
        assertEquals(Trophy.SPROUT, again.trophy());
        assertEquals(7, credits.balance(alex.getId()), "net one payout, so nothing to farm");
    }

    @Test
    void climbingBackAfterAnOlderChoreWasDeleted_doesNotPayTwice() {
        Member alex = chores.createHome("Nest", "Alex");
        credits.setTrophyReward(alex.getHomeCode(), Trophy.SPROUT, 7, alex.getId());
        List<Completion> older = approvedBefore(alex, 4);
        complete(alex);
        assertEquals(7, credits.balance(alex.getId()));

        chores.deleteCompletion(older.get(0).getId()); // the admin's "that didn't happen"
        CompleteOutcome back = complete(alex);

        assertEquals(Trophy.SPROUT, back.trophy(), "the trophy is reached again, and celebrated");
        assertEquals(0, back.award().trophyCredits(), "but its reward was already paid");
        assertEquals(7, credits.balance(alex.getId()));
    }

    @Test
    void settingAReward_doesNotPayMembersAlreadyPastTheRung() {
        Member alex = chores.createHome("Nest", "Alex");
        approvedBefore(alex, 6);

        credits.setTrophyReward(alex.getHomeCode(), Trophy.SPROUT, 7, alex.getId());
        complete(alex);

        assertEquals(0, credits.balance(alex.getId()), "not retroactive");
    }

    @Test
    void inAnApprovalHome_theTrophyWaitsForTheApproval_andIsShownOnTheNextVisit() {
        Member alex = chores.createHome("Nest", "Alex");
        Member sam = chores.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Home home = chores.findHome(alex.getHomeCode()).orElseThrow();
        home.setRequireApproval(true);
        chores.saveHome(home);
        credits.setTrophyReward(home.getCode(), Trophy.SPROUT, 7, alex.getId());
        approvedBefore(sam, 4);

        CompleteOutcome pending = complete(sam);
        assertNull(pending.trophy(), "a pending chore reaches nothing");
        assertTrue(chores.unseenTrophies(sam.getId()).isEmpty());

        CompleteOutcome approved = chores.approve(pending.completionId(), alex.getId());
        assertEquals(Trophy.SPROUT, approved.trophy());
        assertEquals(7, credits.balance(sam.getId()));
        assertEquals(List.of(Trophy.SPROUT), chores.unseenTrophies(sam.getId()),
                "Sam was not looking when the admin approved it");

        chores.markTrophiesSeen(sam.getId());
        assertTrue(chores.unseenTrophies(sam.getId()).isEmpty(), "shown once, not on every visit");
    }

    @Test
    void anAdminLoggingTheChore_leavesTheTrophyUnseenForTheMember() {
        Member alex = chores.createHome("Nest", "Alex");
        Member kid = chores.addMemberWithoutDevice(alex.getHomeCode(), "Kid", alex.getId())
                .orElseThrow();
        approvedBefore(kid, 4);

        CompleteOutcome logged = chores.completeFor(task(alex, 0), kid.getId(), alex.getId());

        assertEquals(Trophy.SPROUT, logged.trophy());
        assertEquals(List.of(Trophy.SPROUT), chores.unseenTrophies(kid.getId()));
    }

    @Test
    void theMembersOwnTap_marksItsTrophySeen() {
        Member alex = chores.createHome("Nest", "Alex");
        approvedBefore(alex, 4);

        complete(alex);

        assertTrue(chores.unseenTrophies(alex.getId()).isEmpty(),
                "the tap's own dialog showed it; the redraw must not show it again");
    }

    @Test
    void trophiesOwnedBeforeTheFeature_areAllUnseenOnce() {
        Member alex = chores.createHome("Nest", "Alex");
        approvedBefore(alex, 26); // the ledger as it stood on upgrade day, marker at its default 0

        assertEquals(List.of(Trophy.SPROUT, Trophy.HELPER, Trophy.BRONZE),
                chores.unseenTrophies(alex.getId()));
        chores.markTrophiesSeen(alex.getId());
        assertTrue(chores.unseenTrophies(alex.getId()).isEmpty());
    }

    // ---- The admin's rewards --------------------------------------------------

    @Test
    void onlyACurrentAdminOfThatHome_setsTheRewards() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        Member sam = chores.joinHome(code, "Sam").orElseThrow();
        Member stranger = chores.createHome("Elsewhere", "Eve");

        assertFalse(credits.setTrophyReward(code, Trophy.SILVER, 20, sam.getId()), "a member");
        assertFalse(credits.setTrophyReward(code, Trophy.SILVER, 20, stranger.getId()),
                "another home's admin");
        assertTrue(credits.trophyRewards(code).isEmpty());

        assertTrue(chores.setMemberAdmin(sam.getId(), true));
        assertTrue(credits.setTrophyReward(code, Trophy.SILVER, 20, sam.getId()));
        assertTrue(chores.setMemberAdmin(sam.getId(), false));
        assertFalse(credits.setTrophyReward(code, Trophy.SILVER, 30, sam.getId()),
                "a demoted admin's open screen");

        assertEquals(Map.of(Trophy.SILVER, 20), credits.trophyRewards(code));
        assertTrue(credits.trophyRewards(stranger.getHomeCode()).isEmpty(), "per home");
    }

    @Test
    void aRewardIsClamped_andZeroRemovesIt() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();

        credits.setTrophyReward(code, Trophy.GOLD, 1_000_000, alex.getId());
        assertEquals(1000, credits.trophyRewards(code).get(Trophy.GOLD));

        credits.setTrophyReward(code, Trophy.GOLD, 0, alex.getId());
        assertTrue(credits.trophyRewards(code).isEmpty());
    }

    // ---- Where trophies are read ----------------------------------------------

    @Test
    void approvedCounts_groupsTheHomeByMember_inOneQuery() {
        Member alex = chores.createHome("Nest", "Alex");
        Member sam = chores.joinHome(alex.getHomeCode(), "Sam").orElseThrow();
        Member other = chores.createHome("Elsewhere", "Eve");
        approvedBefore(alex, 3);
        approvedBefore(other, 2);
        Completion pending = new Completion(alex.getHomeCode(), task(alex, 0), sam.getId(),
                CompletionStatus.PENDING);
        completionRepo.save(pending);

        Map<Long, Long> counts = chores.approvedCounts(alex.getHomeCode());

        assertEquals(Map.of(alex.getId(), 3L), counts, "pending and other homes do not count");
    }

    @Test
    void theShelf_datesEachEarnedTrophy_andNamesTheNextWithItsReward() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        credits.setTrophyReward(code, Trophy.BRONZE, 15, alex.getId());
        List<Completion> done = approvedBefore(alex, 12);

        TrophyShelf shelf = stats.trophyShelf(alex.getId(), code);

        assertEquals(12, shelf.count());
        assertEquals(Trophy.values().length, shelf.trophies().size(), "every rung, earned or not");
        assertEquals(Trophy.BRONZE, shelf.next());
        LocalDate fifth = done.get(4).getDoneAt().atZone(ZoneId.systemDefault()).toLocalDate();
        assertEquals(fifth, shelf.trophies().get(0).earnedOn(), "the day of the fifth chore");
        assertNotNull(shelf.trophies().get(1).earnedOn());
        assertFalse(shelf.trophies().get(2).earned());
        assertEquals(15, shelf.trophies().get(2).reward());
    }

    @Test
    void theShelf_isEmptyForAMemberOfAnotherHome() {
        Member alex = chores.createHome("Nest", "Alex");
        Member eve = chores.createHome("Elsewhere", "Eve");
        approvedBefore(eve, 6);

        TrophyShelf shelf = stats.trophyShelf(eve.getId(), alex.getHomeCode());

        assertTrue(shelf.trophies().isEmpty());
        assertEquals(0, shelf.count());
    }

    // ---- Backup -----------------------------------------------------------------

    @Test
    void rewardsPaidRungsAndTheSeenMarker_surviveTheRoundTrip() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        credits.setTrophyReward(code, Trophy.SPROUT, 7, alex.getId());
        approvedBefore(alex, 4);
        complete(alex);

        backup.restore(backup.export(code).getBytes(StandardCharsets.UTF_8), code, alex.getId());
        Member back = chores.findMemberByName(code, "Alex").orElseThrow();

        assertEquals(Map.of(Trophy.SPROUT, 7), credits.trophyRewards(code));
        assertEquals(7, credits.balance(back.getId()));
        assertTrue(chores.unseenTrophies(back.getId()).isEmpty(), "no replay of a seen trophy");
        // The paid rung came back too: deleting a chore and climbing back pays nothing.
        chores.deleteCompletion(completionRepo.findByMemberIdAndStatus(back.getId(),
                CompletionStatus.APPROVED).get(0).getId());
        CompleteOutcome climbedBack = complete(back);
        assertEquals(Trophy.SPROUT, climbedBack.trophy());
        assertEquals(0, climbedBack.award().trophyCredits());
        assertEquals(7, credits.balance(back.getId()));
    }

    @Test
    void aFileFromBeforeTrophies_restoresWithItsTrophiesAlreadySeen() {
        Member alex = chores.createHome("Nest", "Alex");
        String code = alex.getHomeCode();
        approvedBefore(alex, 11);
        String json = backup.export(code)
                .replaceAll(",\\s*\"trophiesSeen\"\\s*:\\s*\\d+", "")
                .replaceAll(",\\s*\"trophyRewards\"\\s*:\\s*\\[[^\\]]*\\]", "");
        assertFalse(json.contains("trophiesSeen"), "precondition: key stripped");

        backup.restore(json.getBytes(StandardCharsets.UTF_8), code, alex.getId());
        Member back = chores.findMemberByName(code, "Alex").orElseThrow();

        assertEquals(Trophy.HELPER.rung(), back.getTrophiesSeen());
        assertTrue(chores.unseenTrophies(back.getId()).isEmpty(),
                "a restore is not the moment to celebrate a year of chores");
    }

    // ---- Helpers ----------------------------------------------------------------

    /** {@code n} approved chores done over the past hours, oldest first, without any tap. */
    private List<Completion> approvedBefore(Member m, int n) {
        Long task = task(m, 0);
        return IntStream.range(0, n).mapToObj(i -> {
            Completion c = new Completion(m.getHomeCode(), task, m.getId(), CompletionStatus.APPROVED);
            c.setDoneAt(Instant.now().minus(Duration.ofHours(2L * (n - i))));
            return completionRepo.save(c);
        }).toList();
    }

    /**
     * A real tap, on a chore of its own: the seeded ones carry intervals and the fairness limit
     * on doing one chore many times in a row, and those locks are not what is under test here.
     */
    private CompleteOutcome complete(Member m) {
        ChoreTask fresh = chores.addTask(m.getHomeCode(), "Chore " + (++taps), "🧽", 0, 0);
        CompleteOutcome o = chores.complete(fresh.getId(), m.getId());
        assertTrue(o.allowed(), "precondition: the tap is not blocked");
        return o;
    }

    private Long task(Member m, int index) {
        List<ChoreTask> tasks = chores.tasksOf(m.getHomeCode());
        return tasks.get(index).getId();
    }
}
