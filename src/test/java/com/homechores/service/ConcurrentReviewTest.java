package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.homechores.domain.Completion;
import com.homechores.domain.Member;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Two admins deciding the same entry at the same moment, each in a transaction of their own.
 * Deliberately not {@code @Transactional}: a race inside one shared test transaction is no
 * race, so this class commits for real and deletes its homes afterwards.
 */
@SpringBootTest
class ConcurrentReviewTest {

    private static final int ROUNDS = 20;

    @Autowired ChoreService service;
    @Autowired CreditService credits;

    private final List<String> homes = new ArrayList<>();

    @AfterEach
    void deleteHomes() {
        homes.forEach(service::deleteHome);
    }

    @Test
    void twoAdminsAcceptingTheSameHelpAtOnce_payItExactlyOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Member alex = service.createHome("Race", "Alex");
            String code = alex.getHomeCode();
            homes.add(code);
            Member sam = service.joinHome(code, "Sam").orElseThrow();
            Member pat = service.joinHome(code, "Pat").orElseThrow();
            service.setMemberAdmin(pat.getId(), true, alex.getId());
            Completion help = service.logOtherHelp(code, sam.getId(), "Fixed the sink").orElseThrow();

            CyclicBarrier together = new CyclicBarrier(2);
            CompletableFuture<Optional<?>> first = CompletableFuture.supplyAsync(() -> {
                await(together);
                return service.approve(help.getId(), alex.getId(), 5);
            });
            CompletableFuture<Optional<?>> second = CompletableFuture.supplyAsync(() -> {
                await(together);
                return service.approve(help.getId(), pat.getId(), 5);
            });

            long winners = List.of(first.get(), second.get()).stream().filter(Optional::isPresent).count();
            assertEquals(1, winners, "round " + round + ": exactly one decision wins");
            assertEquals(5, credits.balance(sam.getId()), "round " + round + ": paid once");
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
