package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.Member;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Wiping a whole home from the admin's danger zone. */
@SpringBootTest
@Transactional
class DeleteHomeTest {

    @Autowired
    ChoreService service;

    @Autowired
    CreditService credits;

    @Test
    void deleteHome_removesEverythingBelongingToIt() {
        Member alex = service.createHome("Doomed", "Alex");
        String code = alex.getHomeCode();
        Member sam = service.joinHome(code, "Sam").orElseThrow();
        List<ChoreTask> tasks = service.tasksOf(code);

        // Give the home something in every table the wipe has to reach.
        service.updateTask(tasks.get(0).getId(), "Paid chore", "💎", 0, 5, null, null);
        service.complete(tasks.get(0).getId(), alex.getId());
        service.complete(tasks.get(1).getId(), sam.getId());
        credits.addTier(code, 3, 10);
        service.requestRejoin(code, sam.getId(), null);

        assertTrue(credits.balance(alex.getId()) > 0, "precondition: credits were earned");
        assertEquals(1, service.pendingRejoins(code).size());

        assertTrue(service.deleteHome(code));

        assertTrue(service.findHome(code).isEmpty());
        assertTrue(service.membersOf(code).isEmpty());
        assertTrue(service.tasksOf(code).isEmpty());
        assertTrue(service.pendingRejoins(code).isEmpty());
        assertTrue(credits.tiersOf(code).isEmpty());
        assertTrue(credits.ledger(code).isEmpty());
        assertEquals(0, credits.balance(alex.getId()));
        assertEquals(0, service.completionCount(alex.getId()));
        assertEquals(0, service.completionCount(sam.getId()));
        assertTrue(service.findMember(alex.getId()).isEmpty(), "members are gone too");
    }

    @Test
    void deleteHome_leavesOtherHomesAlone() {
        Member doomed = service.createHome("Doomed", "Alex");
        Member keeper = service.createHome("Keeper", "Robin");
        service.complete(service.tasksOf(keeper.getHomeCode()).get(0).getId(), keeper.getId());

        assertTrue(service.deleteHome(doomed.getHomeCode()));

        assertTrue(service.findHome(keeper.getHomeCode()).isPresent());
        assertEquals(1, service.membersOf(keeper.getHomeCode()).size());
        assertEquals(11, service.tasksOf(keeper.getHomeCode()).size());
        assertEquals(1, service.completionCount(keeper.getId()));
    }

    @Test
    void deleteHome_isIdempotentAndReportsUnknownCodes() {
        Member alex = service.createHome("Doomed", "Alex");
        assertTrue(service.deleteHome(alex.getHomeCode()));
        assertFalse(service.deleteHome(alex.getHomeCode()), "already gone");
        assertFalse(service.deleteHome("ZZZZZZZ"));
    }

    @Test
    void deleteHome_acceptsALowercaseCode_likeEveryOtherLookup() {
        Member alex = service.createHome("Doomed", "Alex");
        assertTrue(service.deleteHome(alex.getHomeCode().toLowerCase()));
        assertTrue(service.findHome(alex.getHomeCode()).isEmpty());
    }

    @Test
    void deletedCode_canBeUsedByANewHome() {
        Member alex = service.createHome("Doomed", "Alex");
        String code = alex.getHomeCode();
        assertTrue(service.deleteHome(code));

        Member fresh = service.createHome("Rebuilt", "Robin");
        assertNotEquals(alex.getId(), fresh.getId());
        assertTrue(service.findHome(fresh.getHomeCode()).isPresent());
    }
}
