package com.homechores.service;

import com.homechores.domain.Home;
import com.homechores.domain.HomeRepository;
import com.homechores.domain.InputLimits;
import com.homechores.domain.ListKind;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import com.homechores.domain.PushSubscription;
import com.homechores.domain.PushSubscriptionRepository;
import com.homechores.domain.Routine;
import com.homechores.domain.RoutineDone;
import com.homechores.domain.RoutineDoneRepository;
import com.homechores.domain.RoutineRepository;
import com.homechores.domain.SharedDeviceRepository;
import com.homechores.i18n.Translations;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Routines (§4.4f): things a home does on fixed weekdays, every week or every other week, at a
 * wall-clock time in the <em>home's</em> zone. The Lists panel's Routines tab edits them, the
 * Chores tab shows today's, and a sweep pushes a reminder at each occurrence's time.
 *
 * <p>Who may do what, checked here against the database rather than trusted from the UI:
 * <ul>
 *   <li>any member makes, edits and deletes their <em>own</em> routines, and may make one of
 *       them private;
 *   <li>a routine for the whole home or for another member is an admin's to make or change;
 *   <li>anyone in the home — the shared device included — ticks any routine, as anyone ticks
 *       a list line: it is organisation, not a score;
 *   <li>a private routine is none of that to anyone but its member: every read filters it out
 *       and every write treats it as absent, admins included.
 * </ul>
 *
 * <p>The sweep is a fourth sibling of {@link ListReminderService}, with its transaction
 * discipline copied: short read transaction, sends holding nothing, short write transaction.
 * Its working set is the routines whose {@link Routine#getNextFireAt()} has passed — normally
 * none — so it costs one empty indexed query a minute.
 */
@Service
public class RoutineService {

    private static final Logger log = LoggerFactory.getLogger(RoutineService.class);

    /** Blocking sends per sweep — the cap the other one-shot sweeps on the shared thread use. */
    static final int MAX_SENDS_PER_SWEEP = 10;

    /** An occurrence the sweep reaches later than this (server down, a backlog) is skipped
     *  silently: "time for your workout" two hours after the workout is noise. */
    static final Duration STALE_AFTER = Duration.ofMinutes(30);

    /** How long a tick is kept. Only today's matter; a week leaves room for zone changes. */
    static final int DONE_RETENTION_DAYS = 7;

    static final String DEFAULT_EMOJI = "🔁";

    /**
     * What the routine editor hands over; validated and clipped here. {@code privateToOwner} is
     * only allowed on a routine the author makes for themselves.
     */
    public record Spec(String name, String emoji, Set<DayOfWeek> days, int everyWeeks,
                       boolean startNextWeek, LocalTime time, Long forMemberId,
                       boolean privateToOwner) {
    }

    /** A routine that occurs today, and its tick if it has one. */
    public record Today(Routine routine, RoutineDone done) {

        public boolean isDone() {
            return done != null;
        }
    }

    private final RoutineRepository routines;
    private final RoutineDoneRepository done;
    private final HomeRepository homes;
    private final MemberRepository members;
    private final SharedDeviceRepository devices;
    private final PushSubscriptionRepository subscriptions;
    private final ListItemService lists;
    private final WebPushSender sender;
    private final Translations translations;
    private final HomeState homeState;
    private final TransactionTemplate transactions;

    public RoutineService(RoutineRepository routines, RoutineDoneRepository done, HomeRepository homes,
                          MemberRepository members, SharedDeviceRepository devices,
                          PushSubscriptionRepository subscriptions, ListItemService lists,
                          WebPushSender sender, Translations translations, HomeState homeState,
                          PlatformTransactionManager txManager) {
        this.routines = routines;
        this.done = done;
        this.homes = homes;
        this.members = members;
        this.devices = devices;
        this.subscriptions = subscriptions;
        this.lists = lists;
        this.sender = sender;
        this.translations = translations;
        this.homeState = homeState;
        this.transactions = new TransactionTemplate(txManager);
    }

    // ---- The home's clock ---------------------------------------------------

    public ZoneId zoneOf(String homeCode) {
        return homes.findById(homeCode).map(Home::zone).orElse(ZoneId.systemDefault());
    }

    /** "Today" for the family, whoever's phone asks. */
    public LocalDate today(String homeCode) {
        return LocalDate.now(zoneOf(homeCode));
    }

    /**
     * Takes the zone of the first board opened while the home has none — the creator's phone,
     * in practice. Never overrides one already set: a member abroad must not move the family's
     * Tuesday. An unknown id is ignored.
     */
    @Transactional
    public void adoptZone(String homeCode, String zoneId) {
        Home home = homes.findById(homeCode).orElse(null);
        if (home == null || home.getTimeZone() != null || !validZone(zoneId)) {
            return;
        }
        home.setTimeZone(zoneId);
        homes.save(home);
        reschedule(homeCode);
    }

    /**
     * An admin moves the family's clock. Every routine's next reminder is recomputed in the new
     * zone at once, so a family that moved does not get one at the old wall-clock time.
     *
     * @return false for an unknown zone id
     * @throws IllegalArgumentException unless the member is an admin of the home
     */
    @Transactional
    public boolean setZone(String homeCode, Long memberId, String zoneId) {
        Member admin = memberOf(homeCode, memberId);
        if (!admin.isAdmin()) {
            throw new IllegalArgumentException("Only an admin sets the home's time zone");
        }
        if (!validZone(zoneId)) {
            return false;
        }
        Home home = homes.findById(homeCode).orElseThrow();
        home.setTimeZone(zoneId);
        homes.save(home);
        reschedule(homeCode);
        homeState.bump(homeCode);
        return true;
    }

    private static boolean validZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return false;
        }
        try {
            ZoneId.of(zoneId);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    private void reschedule(String homeCode) {
        ZoneId zone = zoneOf(homeCode);
        Instant now = Instant.now();
        for (Routine r : routines.findByHomeCodeOrderByTimeAscNameAscIdAsc(homeCode)) {
            r.setNextFireAt(r.fireAfter(now, zone));
            routines.save(r);
        }
    }

    // ---- Reading ------------------------------------------------------------

    /**
     * The home's routines this viewer may see — every one but other members' private ones; on the
     * shared device ({@code memberId} null), every one but the private ones — by time of day
     * (untimed last) and name.
     */
    public List<Routine> routinesOf(String homeCode, Long memberId) {
        List<Routine> all = new ArrayList<>(routines.findByHomeCodeOrderByTimeAscNameAscIdAsc(homeCode));
        all.removeIf(r -> !r.isVisibleTo(memberId));
        // Untimed ("some time that day") after the timed ones, whatever the database does with nulls.
        all.sort(Comparator.comparing(Routine::getTime, Comparator.nullsLast(Comparator.naturalOrder())));
        return all;
    }

    /**
     * The routines that occur today and concern the viewer: the whole home's and their own — or,
     * for the shared device ({@code memberId} null), everybody's, since it is the board of the
     * members without a phone too. Two queries, whatever the number of routines.
     */
    public List<Today> today(String homeCode, Long memberId) {
        if (lists.hiddenLists(homeCode).contains(ListKind.ROUTINES)) {
            return List.of();
        }
        LocalDate day = today(homeCode);
        Map<Long, RoutineDone> ticks = new HashMap<>();
        for (RoutineDone d : done.findByHomeCodeAndDay(homeCode, day)) {
            ticks.putIfAbsent(d.getRoutineId(), d);
        }
        return routinesOf(homeCode, memberId).stream()
                .filter(r -> r.occursOn(day))
                .filter(r -> memberId == null || r.getForMemberId() == null
                        || r.getForMemberId().equals(memberId))
                .map(r -> new Today(r, ticks.get(r.getId())))
                .toList();
    }

    /** Today's ticks in the home, by routine id — for the Routines tab, one query per render. */
    public Map<Long, RoutineDone> doneToday(String homeCode) {
        Map<Long, RoutineDone> out = new HashMap<>();
        for (RoutineDone d : done.findByHomeCodeAndDay(homeCode, today(homeCode))) {
            out.putIfAbsent(d.getRoutineId(), d);
        }
        return out;
    }

    // ---- Writing ------------------------------------------------------------

    /**
     * Makes a routine. Empty when the name is blank, no weekday is picked, the person it is for
     * is not in the home, or the home already holds {@link InputLimits#MAX_ROUTINES_PER_HOME}.
     *
     * @throws IllegalArgumentException if the member is not in the home, or a non-admin makes
     *                                  one for the home or for somebody else
     */
    @Transactional
    public Optional<Routine> create(String homeCode, Long memberId, Spec spec) {
        Member author = memberOf(homeCode, memberId);
        mayAssign(author, spec);
        if (routines.countByHomeCode(homeCode) >= InputLimits.MAX_ROUTINES_PER_HOME) {
            return Optional.empty();
        }
        Routine r = new Routine(homeCode, memberId);
        return apply(r, spec);
    }

    /**
     * Changes a routine. Empty for the same reasons {@link #create} gives, or when the routine is
     * gone or belongs to another home.
     *
     * @throws IllegalArgumentException unless the member may edit the routine (its owner or an
     *                                  admin) and may assign it to whom the spec says
     */
    @Transactional
    public Optional<Routine> update(String homeCode, Long memberId, Long routineId, Spec spec) {
        Member author = memberOf(homeCode, memberId);
        Routine r = find(homeCode, routineId, memberId).orElse(null);
        if (r == null) {
            return Optional.empty();
        }
        mayChange(author, r);
        mayAssign(author, spec);
        return apply(r, spec);
    }

    /**
     * Deletes a routine and its ticks.
     *
     * @return false if it is gone or belongs to another home
     * @throws IllegalArgumentException unless the member is its owner or an admin
     */
    @Transactional
    public boolean delete(String homeCode, Long memberId, Long routineId) {
        Member author = memberOf(homeCode, memberId);
        Routine r = find(homeCode, routineId, memberId).orElse(null);
        if (r == null) {
            return false;
        }
        mayChange(author, r);
        done.deleteByRoutineId(r.getId());
        routines.delete(r);
        homeState.bump(homeCode);
        return true;
    }

    /**
     * Ticks (or unticks) today's occurrence. Anyone in the home may, the shared device too.
     *
     * @return false if the routine is gone, of another home, does not occur today, or was
     *         already in that state
     * @throws IllegalArgumentException if the author is not in the home
     */
    @Transactional
    public boolean setDone(String homeCode, Long routineId, Author author, boolean isDone) {
        authorOf(homeCode, author);
        Routine r = find(homeCode, routineId, author.memberId()).orElse(null);
        LocalDate day = today(homeCode);
        if (r == null || !r.occursOn(day)) {
            return false;
        }
        boolean already = done.existsByRoutineIdAndDay(r.getId(), day);
        if (isDone == already) {
            return false;
        }
        if (isDone) {
            done.save(new RoutineDone(r.getId(), homeCode, day, author.memberId()));
        } else {
            done.deleteAll(done.findByRoutineIdAndDay(r.getId(), day));
        }
        homeState.bump(homeCode);
        return true;
    }

    private Optional<Routine> apply(Routine r, Spec spec) {
        String name = InputLimits.clip(spec.name() == null ? null : spec.name().trim(), InputLimits.TASK_NAME);
        if (name == null || name.isBlank() || spec.days() == null || spec.days().isEmpty()) {
            return Optional.empty();
        }
        String homeCode = r.getHomeCode();
        if (spec.forMemberId() != null && members.findById(spec.forMemberId())
                .filter(m -> m.getHomeCode().equals(homeCode)).isEmpty()) {
            return Optional.empty();
        }
        ZoneId zone = zoneOf(homeCode);
        r.setName(name);
        r.setEmoji(spec.emoji() == null || spec.emoji().isBlank() ? DEFAULT_EMOJI
                : InputLimits.clip(spec.emoji().trim(), InputLimits.EMOJI));
        r.setWeekdays(Routine.mask(spec.days()));
        int every = spec.everyWeeks() == Routine.EVERY_OTHER_WEEK ? Routine.EVERY_OTHER_WEEK : 1;
        // The anchor only moves when the rhythm or the start is asked for again: re-saving the
        // name of an every-other-week routine must not flip which weeks it falls on.
        if (r.getId() == null || every != r.getEveryWeeks() || spec.startNextWeek()) {
            LocalDate monday = Routine.mondayOf(LocalDate.now(zone));
            r.setAnchorMonday(spec.startNextWeek() ? monday.plusWeeks(1) : monday);
        }
        r.setEveryWeeks(every);
        r.setTime(spec.time() == null ? null : spec.time().withSecond(0).withNano(0));
        r.setForMemberId(spec.forMemberId());
        r.setPrivateToOwner(spec.privateToOwner());
        r.setNextFireAt(r.fireAfter(Instant.now(), zone));
        Routine saved = routines.save(r);
        homeState.bump(homeCode);
        return Optional.of(saved);
    }

    /** The routine, if it is this home's and the viewer may see it: another member's private
     *  routine reads as absent, so its existence is not confirmed either. */
    private Optional<Routine> find(String homeCode, Long routineId, Long viewerId) {
        return routineId == null ? Optional.empty()
                : routines.findById(routineId)
                        .filter(r -> homeCode.equals(r.getHomeCode()) && r.isVisibleTo(viewerId));
    }

    private static void mayAssign(Member author, Spec spec) {
        boolean own = author.getId().equals(spec.forMemberId());
        if (!author.isAdmin() && !own) {
            throw new IllegalArgumentException("Only an admin makes routines for others or the home");
        }
        if (spec.privateToOwner() && !own) {
            throw new IllegalArgumentException("Only a routine of one's own can be private");
        }
    }

    private static void mayChange(Member author, Routine r) {
        if (!author.isAdmin() && !author.getId().equals(r.getForMemberId())) {
            throw new IllegalArgumentException("Only its owner or an admin changes a routine");
        }
    }

    private Member memberOf(String homeCode, Long memberId) {
        Member member = memberId == null ? null : members.findById(memberId).orElse(null);
        if (member == null || !member.getHomeCode().equals(homeCode)) {
            throw new IllegalArgumentException("Member does not belong to this home");
        }
        return member;
    }

    private void authorOf(String homeCode, Author author) {
        if (author.isDevice()) {
            if (devices.findById(author.sharedDeviceId())
                    .filter(d -> d.getHomeCode().equals(homeCode)).isEmpty()) {
                throw new IllegalArgumentException("Device is not paired to this home");
            }
        } else {
            memberOf(homeCode, author.memberId());
        }
    }

    // ---- The reminder sweep -------------------------------------------------

    @Scheduled(fixedDelayString = "${homechores.routine.sweep-ms:60000}",
            initialDelayString = "${homechores.routine.sweep-ms:60000}")
    public void scheduledSweep() {
        sendDue(Instant.now());
    }

    @Scheduled(fixedDelayString = "${homechores.list.purge-ms:3600000}",
            initialDelayString = "${homechores.list.purge-ms:3600000}")
    public void scheduledPurge() {
        purgeOldTicks(LocalDate.now(ZoneId.systemDefault()));
    }

    /** Deletes ticks older than {@link #DONE_RETENTION_DAYS}. Returns how many went. */
    @Transactional
    public long purgeOldTicks(LocalDate today) {
        return done.deleteByDayBefore(today.minusDays(DONE_RETENTION_DAYS));
    }

    /**
     * One sweep at the given moment; callable directly from tests. Returns sends made.
     *
     * <p>Every routine it looks at moves on to its next occurrence, whatever happened — sent,
     * already ticked, stale, nobody subscribed, push off, Routines switched off — so no row is
     * reconsidered by the next sweep and a crash costs at most one duplicate nudge.
     */
    public int sendDue(Instant now) {
        int sent = 0;
        for (Long id : dueIds(now)) {
            Plan plan = planFor(id, now);
            List<Long> dead = new ArrayList<>();
            if (plan != null && sender.isEnabled()) {
                for (Map.Entry<PushSubscription, String[]> e : plan.messages().entrySet()) {
                    if (sent >= MAX_SENDS_PER_SWEEP) {
                        break;
                    }
                    if (sender.send(e.getKey(), e.getValue()[0], e.getValue()[1])) {
                        sent++;
                    } else {
                        dead.add(e.getKey().getId());
                    }
                }
            }
            advance(id, now, dead);
            if (sent >= MAX_SENDS_PER_SWEEP) {
                break;
            }
        }
        return sent;
    }

    private record Plan(Map<PushSubscription, String[]> messages) {
    }

    private List<Long> dueIds(Instant now) {
        return transactions.execute(status ->
                routines.findTop50ByNextFireAtLessThanEqualOrderByNextFireAtAsc(now).stream()
                        .map(Routine::getId).toList());
    }

    /**
     * Who hears about one occurrence, each device in its own member's language. Null = nobody:
     * the routine is gone, the moment is stale, the tab is off, or it was ticked already (done
     * before its time is the best outcome, not a reason to nag).
     */
    private Plan planFor(Long routineId, Instant now) {
        return transactions.execute(status -> {
            Routine r = routines.findById(routineId).orElse(null);
            if (r == null || r.getNextFireAt() == null
                    || r.getNextFireAt().isBefore(now.minus(STALE_AFTER))) {
                return null;
            }
            String homeCode = r.getHomeCode();
            if (lists.hiddenLists(homeCode).contains(ListKind.ROUTINES)) {
                return null;
            }
            LocalDate day = r.getNextFireAt().atZone(zoneOf(homeCode)).toLocalDate();
            if (done.existsByRoutineIdAndDay(r.getId(), day)) {
                return null;
            }
            Map<Long, Locale> locales = new HashMap<>();
            for (Member m : members.findByHomeCodeOrderByJoinedAtAsc(homeCode)) {
                locales.put(m.getId(), m.getReminderLocale() == null ? Locale.ENGLISH
                        : Locale.forLanguageTag(m.getReminderLocale()));
            }
            List<PushSubscription> audience = r.getForMemberId() == null
                    ? subscriptions.findByHomeCode(homeCode)
                    : subscriptions.findByMemberId(r.getForMemberId());
            Map<PushSubscription, String[]> messages = new LinkedHashMap<>();
            for (PushSubscription sub : audience) {
                Locale locale = locales.getOrDefault(sub.getMemberId(), Locale.ENGLISH);
                messages.put(sub, new String[] {
                        translations.getTranslation("routine.notification.title", locale,
                                r.getEmoji(), r.getName()),
                        translations.getTranslation("routine.notification.body", locale)});
            }
            return new Plan(messages);
        });
    }

    private void advance(Long routineId, Instant now, List<Long> dead) {
        transactions.executeWithoutResult(status -> {
            dead.forEach(subscriptions::deleteById);
            routines.findById(routineId).ifPresent(r -> {
                // From now, not from the occurrence just handled: after downtime that skips the
                // missed ones in one step instead of walking through them a sweep at a time.
                Instant next = r.fireAfter(now, zoneOf(r.getHomeCode()));
                r.setNextFireAt(next);
                routines.save(r);
                log.debug("Routine {} next reminder at {}", r.getId(), next);
            });
        });
    }
}
