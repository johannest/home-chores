package com.homechores.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A tiny in-memory, per-key rolling-window rate limiter shared across all sessions.
 *
 * <p>Used to throttle the unauthenticated landing-page actions (creating a home, asking to
 * join) per client IP, so one source can't script thousands of them and inflate the
 * database or flood an admin's approval queue. In-memory on purpose: this is a single-node
 * app, and a restart forgetting the counters is fine.
 *
 * <p>It bounds only a single source. A botnet spreading calls across many IPs isn't stopped
 * by this (nor by a per-IP limit at the proxy) — but the retention sweep reclaims the empty
 * homes such abuse would create, and the 48h sweep clears abandoned join requests.
 */
@Component
public class RateLimiter {

    /** Cap on tracked keys, so a source rotating IPs can't grow the map without bound. */
    private static final int MAX_TRACKED_KEYS = 50_000;

    private static final class Bucket {
        int count;
        Instant resetAt;
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** Off only for tests, where every request shares one client key and would otherwise
     *  exhaust the allowance across the suite. */
    private final boolean enabled;

    public RateLimiter(@Value("${homechores.ratelimit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Records one use of {@code key} and reports whether it is within the allowance.
     *
     * @return true if at most {@code max} calls (this one included) have happened for this
     *         key inside the current {@code window}; false once the allowance is spent
     */
    public boolean allow(String key, int max, Duration window) {
        if (!enabled) {
            return true;
        }
        Instant now = Instant.now();
        if (buckets.size() > MAX_TRACKED_KEYS) {
            buckets.values().removeIf(b -> b.resetAt == null || now.isAfter(b.resetAt));
        }
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket());
        synchronized (b) {
            if (b.resetAt == null || now.isAfter(b.resetAt)) {
                b.resetAt = now.plus(window);
                b.count = 0;
            }
            if (b.count >= max) {
                return false;
            }
            b.count++;
            return true;
        }
    }
}
