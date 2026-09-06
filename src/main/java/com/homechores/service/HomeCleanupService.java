package com.homechores.service;

import com.homechores.domain.CompletionRepository;
import com.homechores.domain.Home;
import com.homechores.domain.HomeRepository;
import com.homechores.domain.MemberRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Retention: removes homes nobody is using any more. Three windows, from narrow to broad:
 *
 * <ul>
 * <li><b>{@code empty-home-hours}</b> — the fast anti-spam tier. Purges homes that were
 * created and abandoned before anyone used them (<em>no chore history at all</em> and
 * <em>at most one member</em> — the "tapped Create, never invited anyone" leftovers).
 * Nothing of value is lost, so the window is short (72h in prod config).</li>
 * <li><b>{@code abandoned-home-days}</b> — the same empty-home rule with a long window;
 * kept as the documented upper bound for unused homes even when the fast tier is off.</li>
 * <li><b>{@code inactive-home-days}</b> — the total-inactivity tier: a home nobody has
 * opened or used for this many days is deleted <em>entirely</em>, members and chore
 * history included. Because a false positive here destroys a real family's data (a long
 * trip, a lost phone), every home that has actual history is exported to
 * {@code retention.export-dir} first — the operator's undo — and a home whose export
 * fails is skipped rather than deleted. The user agreement and privacy page state this
 * window, generated from the configured value.</li>
 * </ul>
 *
 * <p>"Activity" is {@link Home#lastActiveOrCreated()}: opening the board, joining,
 * completing or reviewing a chore — never background push traffic, so an idle phone on a
 * charger doesn't keep a home alive, and an opt-in push reminder doesn't either.
 *
 * <p>The sweep is disabled unless at least one window is above zero.
 */
@Service
public class HomeCleanupService {

    private static final Logger log = LoggerFactory.getLogger(HomeCleanupService.class);

    /** For the empty-home tiers: more than one member means it was shared with someone. */
    private static final long MAX_MEMBERS_TO_PURGE = 1;

    private final HomeRepository homes;
    private final MemberRepository members;
    private final CompletionRepository completions;
    private final ChoreService choreService;
    private final BackupService backupService;

    /** Days of inactivity before an unused, empty home is purged; 0 (default) = never. */
    private final int abandonedHomeDays;

    /** Fast tier: hours before an unused, empty home is purged; 0 = fast tier off. */
    private final int emptyHomeHours;

    /** Days of total inactivity before ANY home is purged, history and all; 0 = off. */
    private final int inactiveHomeDays;

    /** Where the pre-purge safety exports of homes with real history are written. */
    private final String exportDir;

    public HomeCleanupService(HomeRepository homes, MemberRepository members,
                             CompletionRepository completions, ChoreService choreService,
                             BackupService backupService,
                             @Value("${homechores.retention.abandoned-home-days:0}")
                             int abandonedHomeDays,
                             @Value("${homechores.retention.empty-home-hours:0}")
                             int emptyHomeHours,
                             @Value("${homechores.retention.inactive-home-days:0}")
                             int inactiveHomeDays,
                             @Value("${homechores.retention.export-dir:data/retention-exports}")
                             String exportDir) {
        this.homes = homes;
        this.members = members;
        this.completions = completions;
        this.choreService = choreService;
        this.backupService = backupService;
        this.abandonedHomeDays = abandonedHomeDays;
        this.emptyHomeHours = emptyHomeHours;
        this.inactiveHomeDays = inactiveHomeDays;
        this.exportDir = exportDir;
    }

    public boolean isEnabled() {
        return abandonedHomeDays > 0 || emptyHomeHours > 0 || inactiveHomeDays > 0;
    }

    public int getAbandonedHomeDays() {
        return abandonedHomeDays;
    }

    public int getEmptyHomeHours() {
        return emptyHomeHours;
    }

    public int getInactiveHomeDays() {
        return inactiveHomeDays;
    }

    /** Nightly sweep, well outside the hours a family taps chores. */
    @Scheduled(cron = "${homechores.retention.cron:0 30 3 * * *}")
    public void scheduledPurge() {
        if (!isEnabled()) {
            return;
        }
        List<String> purged = new ArrayList<>(purgeAbandonedHomes());
        purged.addAll(purgeInactiveHomes());
        if (!purged.isEmpty()) {
            // Count only: a home code is the home's access credential, so it stays out of
            // log files. The offline maintenance tool is the place to inspect specifics.
            log.info("Retention: purged {} home(s) past the configured window(s)",
                    purged.size());
        }
    }

    // ---- Empty-home tiers (spam / never-used) --------------------------------

    /**
     * Deletes every home that qualifies as abandoned, and returns their codes. Callable
     * directly (tests, a maintenance run) and honours the configured windows — the
     * shorter of the two effectively decides; non-positive windows purge nothing.
     */
    public List<String> purgeAbandonedHomes() {
        Instant now = Instant.now();
        Instant cutoff = null;
        if (abandonedHomeDays > 0) {
            cutoff = now.minus(Duration.ofDays(abandonedHomeDays));
        }
        if (emptyHomeHours > 0) {
            Instant fast = now.minus(Duration.ofHours(emptyHomeHours));
            cutoff = cutoff == null || fast.isAfter(cutoff) ? fast : cutoff;
        }
        return cutoff == null ? List.of() : purgeAbandonedHomes(cutoff);
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
            return false; // has history — the inactive tier decides about these, not this one
        }
        return members.countByHomeCode(home.getCode()) <= MAX_MEMBERS_TO_PURGE;
    }

    /** Homes that would be purged right now, without deleting anything (for inspection). */
    public List<Home> findAbandoned(Instant cutoff) {
        return homes.findAll().stream().filter(h -> isAbandoned(h, cutoff)).toList();
    }

    // ---- Total-inactivity tier ------------------------------------------------

    /** Purges every home inactive past the configured window; no-op when the tier is off. */
    public List<String> purgeInactiveHomes() {
        if (inactiveHomeDays <= 0) {
            return List.of();
        }
        return purgeInactiveHomes(Instant.now().minus(Duration.ofDays(inactiveHomeDays)));
    }

    /**
     * Deletes every home whose last activity is before {@code cutoff}, whatever its size
     * or history. A home with any chore history is exported to {@link #exportDir} first;
     * if that export cannot be written the home is <em>kept</em> — losing a family's
     * irreplaceable history to a full disk would be the worst possible trade.
     */
    public List<String> purgeInactiveHomes(Instant cutoff) {
        List<String> purged = new ArrayList<>();
        for (Home home : homes.findAll()) {
            if (!home.lastActiveOrCreated().isBefore(cutoff)) {
                continue;
            }
            boolean hasHistory = completions.existsByHomeCode(home.getCode());
            if (hasHistory && !exportBeforePurge(home)) {
                continue; // fail safe: no export, no delete
            }
            if (choreService.deleteHome(home.getCode())) {
                purged.add(home.getCode());
            }
        }
        return purged;
    }

    /** Homes the inactive tier would purge right now, without deleting (for inspection). */
    public List<Home> findInactive(Instant cutoff) {
        return homes.findAll().stream()
                .filter(h -> h.lastActiveOrCreated().isBefore(cutoff)).toList();
    }

    /**
     * Writes the full JSON backup of a home before the inactive tier deletes it — the
     * operator's undo for a family that comes back after a long absence (restorable with
     * the maintenance tool or the in-app restore). The file name carries the home code;
     * that is fine on the server's own disk (the erasure-export convention), it just must
     * not leak into logs.
     */
    private boolean exportBeforePurge(Home home) {
        try {
            Path dir = Path.of(exportDir);
            Files.createDirectories(dir);
            Path file = dir.resolve(home.getCode() + "-" + LocalDate.now() + ".json");
            Files.writeString(file, backupService.export(home.getCode()));
            return true;
        } catch (Exception e) {
            log.warn("Retention: safety export failed, keeping the home ({})", e.getMessage());
            return false;
        }
    }
}
