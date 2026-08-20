package com.homechores.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The per-key rolling-window allowance behind the landing-page create/join throttles. */
class RateLimiterTest {

    private static final Duration HOUR = Duration.ofHours(1);

    @Test
    void allowsUpToTheCapThenBlocksWithinTheWindow() {
        RateLimiter limiter = new RateLimiter(true);
        assertTrue(limiter.allow("k", 3, HOUR));
        assertTrue(limiter.allow("k", 3, HOUR));
        assertTrue(limiter.allow("k", 3, HOUR));
        assertFalse(limiter.allow("k", 3, HOUR), "the 4th call in the window is refused");
    }

    @Test
    void keysHaveIndependentBudgets() {
        RateLimiter limiter = new RateLimiter(true);
        assertTrue(limiter.allow("a", 1, HOUR));
        assertFalse(limiter.allow("a", 1, HOUR), "a is spent");
        assertTrue(limiter.allow("b", 1, HOUR), "b is unaffected — the cap is per key");
    }

    @Test
    void aNewWindowGrantsAFreshAllowance() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(true);
        Duration tiny = Duration.ofMillis(20);
        assertTrue(limiter.allow("k", 1, tiny));
        assertFalse(limiter.allow("k", 1, tiny));
        Thread.sleep(40); // let the window elapse
        assertTrue(limiter.allow("k", 1, tiny), "the bucket resets once the window passes");
    }

    @Test
    void disabledLimiterAlwaysAllows() {
        RateLimiter limiter = new RateLimiter(false);
        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.allow("k", 1, HOUR));
        }
    }
}
