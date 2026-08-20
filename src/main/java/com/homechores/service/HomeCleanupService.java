package com.homechores.service;

import com.homechores.domain.CompletionRepository;
import com.homechores.domain.Home;
import com.homechores.domain.HomeRepository;
import com.homechores.domain.MemberRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Removes homes that were created and then abandoned before anyone used them — the
 * "tapped Create, never invited anyone, never did a chore" leftovers that accumulate on a
 * public instance.
 *
 * <p>The rule is deliberately narrow. A home qualifies only when it has <em>no chore
 * history at all</em> and <em>at most one member</em>, so there is nothing of value to
 * lose: no completions, no credits, no streaks, nobody else's data. Homes that a family
 * actually used are never touched however long they lie idle, because this app has no
 * email or push channel and therefore no way to warn anyone before deleting — silently
 * destroying a child's chore history is not a trade worth making for disk space.
 *
 * <p>Disabled unless {@code homechores.retention.abandoned-home-days} is set above zero.
 */
@Service
public class HomeCleanupService {

    private static final Logger log = LoggerFactory.getLogger(HomeCleanupService.class);

    /** A home with more than one member was shared with someone — never auto-purged. */
    private static final long MAX_MEMBERS_TO_PURGE = 1;

    private final HomeRepository homes;
    private final MemberRepository members;
    private final CompletionRepository completions;
    private final ChoreService choreService;

    /** Days of inactivity before an unused, empty home is purged; 0 (default) = never. */
    private final int abandonedHomeDays;

    public HomeCleanupService(HomeRepository homes, MemberRepository members,
                             CompletionRepository completions, ChoreService choreService,
                             @Value("${homechores.retention.abandoned-home-days:0}")
                             int abandonedHomeDays) {
        this.homes = homes;
        this.members = members;
        this.completions = completions;
        this.choreService = choreService;
        this.abandonedHomeDays = abandonedHomeDays;
    }

    public boolean isEnabled() {
        return abandonedHomeDays > 0;
    }

    public int getAbandonedHomeDays() {
        return abandonedHomeDays;
    }

    /** Nightly sweep, well outside the hours a family taps chores. */
    @Scheduled(cron = "${homechores.retention.cron:0 30 3 * * *}")
    public void scheduledPurge() {
        if (!isEnabled()) {
            return;
        }
        List<String> purged = purgeAbandonedHomes();
        if (!purged.isEmpty()) {
            // Count only: a home code is the home's access credential, so it stays out of
            // log files. The offline maintenance tool is the place to inspect specifics.
            log.info("Retention: purged {} abandoned home(s) unused for {}+ days",
                    purged.size(), abandonedHomeDays);
        }
    }

    /**
     * Deletes every home that qualifies as abandoned, and returns their codes. Callable
     * directly (tests, a maintenance run) and honours the configured window; a
     * non-positive window purges nothing.
     */
    public List<String> purgeAbandonedHomes() {
        if (!isEnabled()) {
            return List.of();
        }
        return purgeAbandonedHomes(Instant.now().minus(Duration.ofDays(abandonedHomeDays)));
    }

    /** Same, with an explicit cutoff — anything last active before it is a candidate. */
    public List<String> purgeAbandonedHomes(Instant cutoff) {
        List<String> purged = new ArrayList<>();
        for (Home home : homes.findAll()) {
            if (isAbandoned(home, cutoff) && choreService.deleteHome(home.getCode())) {
                purged.add(home.getCode());
            }
        }
        return purged;
    }

    /** Whether this home is an unused leftover rather than a family's real board. */
    public boolean isAbandoned(Home home, Instant cutoff) {
        if (!home.lastActiveOrCreated().isBefore(cutoff)) {
            return false; // used recently enough
        }
        if (completions.existsByHomeCode(home.getCode())) {
            return false; // has history worth keeping, however old
        }
        return members.countByHomeCode(home.getCode()) <= MAX_MEMBERS_TO_PURGE;
    }

    /** Homes that would be purged right now, without deleting anything (for inspection). */
    public List<Home> findAbandoned(Instant cutoff) {
        return homes.findAll().stream().filter(h -> isAbandoned(h, cutoff)).toList();
    }
}
