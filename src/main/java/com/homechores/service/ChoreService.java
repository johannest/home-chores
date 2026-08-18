package com.homechores.service;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.ChoreTaskRepository;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.CompletionStatus;
import com.homechores.domain.DivisionStyle;
import com.homechores.domain.Feedback;
import com.homechores.domain.Home;
import com.homechores.domain.HomeRepository;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import com.homechores.domain.RejoinRequest;
import com.homechores.domain.RejoinRequestRepository;
import com.homechores.domain.RejoinStatus;
import com.homechores.domain.TimeWindows;
import com.homechores.i18n.Translations;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for homes, chores, completions, fairness, approvals and stats.
 */
@Service
public class ChoreService {

    /** A member may complete the SAME chore at most this many times in a row. */
    public static final int MAX_IN_A_ROW = 3;

    /** Milestones that trigger a big celebration (personal approved-chore counts). */
    static final int[] MILESTONES = {5, 10, 25, 50, 100, 250};

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I/O/0/1
    /** Length of a shareable home code. 7 chars ≈ 34 billion combinations, so codes
     *  can't realistically be guessed/enumerated to stumble into other homes. */
    private static final int CODE_LENGTH = 7;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String[] COLORS = {
        "#10b981", "#0ea5e9", "#f59e0b", "#ef4444", "#8b5cf6",
        "#ec4899", "#14b8a6", "#f97316", "#6366f1", "#84cc16"
    };

    private final HomeRepository homes;
    private final MemberRepository members;
    private final ChoreTaskRepository tasks;
    private final CompletionRepository completions;
    private final RejoinRequestRepository rejoins;
    private final HomeState homeState;
    private final CreditService creditService;
    private final Translations translations;

    public ChoreService(HomeRepository homes, MemberRepository members,
                        ChoreTaskRepository tasks, CompletionRepository completions,
                        RejoinRequestRepository rejoins, HomeState homeState,
                        CreditService creditService, Translations translations) {
        this.homes = homes;
        this.members = members;
        this.tasks = tasks;
        this.completions = completions;
        this.rejoins = rejoins;
        this.homeState = homeState;
        this.creditService = creditService;
        this.translations = translations;
    }

    // ---- Home create / join -------------------------------------------------

    /** Creates a new home with English default chores (see the locale-aware overload). */
    @Transactional
    public Member createHome(String homeName, String memberName) {
        return createHome(homeName, memberName, Locale.ENGLISH);
    }

    /**
     * Creates a new home (with a unique code + admin PIN) and its first, admin member.
     * The default chores are seeded with names in {@code locale}.
     */
    @Transactional
    public Member createHome(String homeName, String memberName, Locale locale) {
        String code;
        do {
            code = generateCode();
        } while (homes.existsById(code));

        Home home = new Home(code, homeName.trim(), generatePin());
        home.setLastActiveAt(Instant.now());
        homes.save(home);
        seedDefaultTasks(code, locale);
        return addMember(code, memberName, true);
    }

    public Optional<Home> findHome(String code) {
        return code == null ? Optional.empty() : homes.findById(normalizeCode(code));
    }

    /** How stale {@code lastActiveAt} may get before a touch bothers to write to the DB. */
    private static final Duration TOUCH_RESOLUTION = Duration.ofHours(1);

    /**
     * Records that a person is using this home. Called when someone opens the board or
     * changes something in it — never from push traffic, so an idle phone left on a
     * charger doesn't keep a home looking alive.
     *
     * <p>Writes at most once an hour per home, and never bumps the revision signal: this
     * is bookkeeping, not state anyone's screen should react to.
     */
    @Transactional
    public void touchHome(String code) {
        homes.findById(normalizeCode(code)).ifPresent(this::touch);
    }

    /** Same, for callers that already hold the entity. */
    private void touch(Home home) {
        Instant now = Instant.now();
        Instant last = home.getLastActiveAt();
        if (last == null || last.isBefore(now.minus(TOUCH_RESOLUTION))) {
            home.setLastActiveAt(now);
            homes.save(home);
        }
    }

    @Transactional
    public void saveHome(Home home) {
        homes.save(home);
        homeState.bump(home.getCode());
    }

    /** Adds a member to an existing home. Returns empty if the code is unknown. */
    @Transactional
    public Optional<Member> joinHome(String code, String memberName) {
        String norm = normalizeCode(code);
        if (!homes.existsById(norm)) {
            return Optional.empty();
        }
        touchHome(norm);
        return Optional.of(addMember(norm, memberName, false));
    }

    private Member addMember(String homeCode, String name, boolean admin) {
        int existing = members.findByHomeCodeOrderByJoinedAtAsc(homeCode).size();
        String color = COLORS[existing % COLORS.length];
        Member m = members.save(new Member(homeCode, name.trim(), color, admin));
        homeState.bump(homeCode);
        return m;
    }

    public Optional<Member> findMember(Long id) {
        return id == null ? Optional.empty() : members.findById(id);
    }

    public List<Member> membersOf(String homeCode) {
        return members.findByHomeCodeOrderByJoinedAtAsc(homeCode);
    }

    /**
     * Deletes a home and everything belonging to it — members, chores, completions,
     * credits, spree tiers and rejoin requests. Irreversible; the caller is responsible
     * for confirming intent (see {@code AdminPanel}'s danger zone).
     *
     * <p>The revision is bumped last so every other device still on this home re-renders,
     * finds the home gone and shows itself out (see {@code HomeView}).
     *
     * @return false if there was no such home
     */
    @Transactional
    public boolean deleteHome(String code) {
        String norm = normalizeCode(code);
        if (!homes.existsById(norm)) {
            return false;
        }
        rejoins.deleteByHomeCode(norm);
        completions.deleteByHomeCode(norm);
        creditService.deleteForHome(norm);
        tasks.deleteByHomeCode(norm);
        members.deleteByHomeCode(norm);
        homes.deleteById(norm);
        homeState.bump(norm);
        return true;
    }

    // ---- Rejoining as an existing member ------------------------------------

    /** How a {@link #requestRejoin} attempt ended. */
    public enum RejoinResult {
        /** Sign the device straight in — the home doesn't gate rejoins, or the PIN matched. */
        SIGNED_IN,
        /** An admin has to approve first; the caller should keep the returned token. */
        PENDING,
        /** A PIN was supplied but didn't match the home's admin PIN. */
        WRONG_PIN,
        /** No such home, or that member doesn't belong to it. */
        UNKNOWN
    }

    /** Outcome of a rejoin attempt; {@code token} is set only for {@link RejoinResult#PENDING}. */
    public record Rejoin(RejoinResult result, String token) {
    }

    /**
     * Asks to sign back in as an existing member — the recovery path for a device that lost
     * its stored identity. A correct admin PIN (or a home with the approval gate off) signs
     * in immediately; otherwise a pending request is raised for an admin to decide on.
     *
     * <p>The PIN only bypasses the gate — it does not grant admin rights. The member keeps
     * whatever role their existing record already has, and the header's "Admin?" action
     * remains the way to claim admin.
     */
    @Transactional
    public Rejoin requestRejoin(String code, Long memberId, String pin) {
        String norm = normalizeCode(code);
        Home home = homes.findById(norm).orElse(null);
        Member member = memberId == null ? null : members.findById(memberId).orElse(null);
        if (home == null || member == null || !norm.equals(member.getHomeCode())) {
            return new Rejoin(RejoinResult.UNKNOWN, null);
        }
        boolean pinGiven = pin != null && !pin.isBlank();
        if (pinGiven && !pin.trim().equals(home.getAdminPin())) {
            return new Rejoin(RejoinResult.WRONG_PIN, null);
        }
        if (pinGiven || !home.isApproveRejoin()) {
            touchHome(norm);
            return new Rejoin(RejoinResult.SIGNED_IN, null);
        }
        // Only the newest device may be waiting for a given member, so an abandoned request
        // on an old phone can't be used to walk in later.
        rejoins.findByHomeCodeAndStatusOrderByRequestedAtAsc(norm, RejoinStatus.PENDING).stream()
                .filter(r -> r.getMemberId().equals(memberId))
                .forEach(rejoins::delete);
        String token = generateToken();
        rejoins.save(new RejoinRequest(norm, memberId, token));
        homeState.bump(norm);
        return new Rejoin(RejoinResult.PENDING, token);
    }

    /** Looks a rejoin request up by the secret held in the requesting browser's storage. */
    public Optional<RejoinRequest> findRejoinByToken(String deviceToken) {
        return deviceToken == null || deviceToken.isBlank()
                ? Optional.empty() : rejoins.findByDeviceToken(deviceToken);
    }

    public List<RejoinRequest> pendingRejoins(String homeCode) {
        return rejoins.findByHomeCodeAndStatusOrderByRequestedAtAsc(homeCode, RejoinStatus.PENDING);
    }

    public long pendingRejoinCount(String homeCode) {
        return rejoins.countByHomeCodeAndStatus(homeCode, RejoinStatus.PENDING);
    }

    /** Approves (or rejects) a pending rejoin request. The waiting device picks the
     *  decision up through the home's revision signal. */
    @Transactional
    public boolean decideRejoin(Long requestId, Long adminId, boolean approve) {
        RejoinRequest r = rejoins.findById(requestId).orElse(null);
        if (r == null || r.getStatus() != RejoinStatus.PENDING) {
            return false;
        }
        r.setStatus(approve ? RejoinStatus.APPROVED : RejoinStatus.REJECTED);
        r.setDecidedAt(Instant.now());
        r.setDecidedByMemberId(adminId);
        rejoins.save(r);
        homeState.bump(r.getHomeCode());
        return true;
    }

    /** Consumes an approved request so its token can't be replayed on another device. */
    @Transactional
    public void consumeRejoin(Long requestId) {
        rejoins.findById(requestId).ifPresent(rejoins::delete);
    }

    /** Drops a device's own pending request (the "never mind" button on the waiting screen). */
    @Transactional
    public void cancelRejoin(String deviceToken) {
        findRejoinByToken(deviceToken).ifPresent(r -> {
            String homeCode = r.getHomeCode();
            rejoins.delete(r);
            homeState.bump(homeCode);
        });
    }

    // ---- Admin & member management -----------------------------------------

    /** Attempts to grant admin rights to a member by verifying the home's admin PIN. */
    @Transactional
    public boolean claimAdmin(Long memberId, String pin) {
        Member member = members.findById(memberId).orElseThrow();
        Home home = homes.findById(member.getHomeCode()).orElseThrow();
        if (pin != null && pin.trim().equals(home.getAdminPin())) {
            member.setAdmin(true);
            members.save(member);
            homeState.bump(home.getCode());
            return true;
        }
        return false;
    }

    /** Promote/demote a member. Refuses to demote the last remaining admin. */
    @Transactional
    public boolean setMemberAdmin(Long memberId, boolean admin) {
        Member member = members.findById(memberId).orElseThrow();
        if (!admin && member.isAdmin()
                && members.countByHomeCodeAndAdminTrue(member.getHomeCode()) <= 1) {
            return false; // cannot remove the last admin
        }
        member.setAdmin(admin);
        members.save(member);
        homeState.bump(member.getHomeCode());
        return true;
    }

    @Transactional
    public void renameMember(Long memberId, String name) {
        Member member = members.findById(memberId).orElseThrow();
        member.setName(name.trim());
        members.save(member);
        homeState.bump(member.getHomeCode());
    }

    /** Removes a member and their completions. Refuses to remove the last admin. */
    @Transactional
    public boolean removeMember(Long memberId) {
        Member member = members.findById(memberId).orElseThrow();
        if (member.isAdmin()
                && members.countByHomeCodeAndAdminTrue(member.getHomeCode()) <= 1) {
            return false;
        }
        String homeCode = member.getHomeCode();
        completions.deleteByMemberId(memberId);
        creditService.deleteForMember(memberId);
        rejoins.deleteByMemberId(memberId);
        members.delete(member);
        homeState.bump(homeCode);
        return true;
    }

    // ---- Tasks (CRUD) -------------------------------------------------------

    /** The starter chores every new home gets, named in the creator's language. */
    private void seedDefaultTasks(String homeCode, Locale locale) {
        Instant base = Instant.now();
        int i = 0;
        i = seed(homeCode, locale, base, i, "chore.default.emptyDishwasher", "🍽️", 0, null);
        i = seed(homeCode, locale, base, i, "chore.default.trash", "🗑️", 0, null);
        i = seed(homeCode, locale, base, i, "chore.default.vacuum", "🧹", 0, null);
        i = seed(homeCode, locale, base, i, "chore.default.cookDinner", "🍳", 0, null);
        i = seed(homeCode, locale, base, i, "chore.default.fillDishwasher", "🫧", 0, null);
        i = seed(homeCode, locale, base, i, "chore.default.hangLaundry", "🧺", 0, null);
        i = seed(homeCode, locale, base, i, "chore.default.foldLaundry", "👕", 0, null);
        i = seed(homeCode, locale, base, i, "chore.default.vestibule", "🧥", 0, null);
        i = seed(homeCode, locale, base, i, "chore.default.salad", "🥗", 0, null);
        i = seed(homeCode, locale, base, i, "chore.default.waterPlants", "🪴", 7, null);
        seed(homeCode, locale, base, i, "chore.default.dogOut", "🐕", 0, "08:00-10:00,18:00-22:00");
    }

    /** Saves one seeded chore with a strictly increasing createdAt so list order is stable. */
    private int seed(String homeCode, Locale locale, Instant base, int index,
                     String nameKey, String emoji, int intervalDays, String windows) {
        ChoreTask t = new ChoreTask(homeCode, translations.getTranslation(nameKey, locale), emoji);
        t.setIntervalDays(intervalDays);
        t.setAvailableWindows(windows);
        t.setCreatedAt(base.plusMillis(index));
        tasks.save(t);
        return index + 1;
    }

    @Transactional
    public ChoreTask addTask(String homeCode, String name, String emoji) {
        return addTask(homeCode, name, emoji, 0);
    }

    @Transactional
    public ChoreTask addTask(String homeCode, String name, String emoji, int intervalDays) {
        return addTask(homeCode, name, emoji, intervalDays, 0);
    }

    @Transactional
    public ChoreTask addTask(String homeCode, String name, String emoji, int intervalDays,
                             int creditValue) {
        return addTask(homeCode, name, emoji, intervalDays, creditValue, null);
    }

    @Transactional
    public ChoreTask addTask(String homeCode, String name, String emoji, int intervalDays,
                             int creditValue, String availableWindows) {
        ChoreTask t = new ChoreTask(homeCode, name.trim(), cleanEmoji(emoji));
        t.setIntervalDays(Math.max(0, intervalDays));
        t.setCreditValue(Math.max(0, creditValue));
        t.setAvailableWindows(availableWindows);
        t = tasks.save(t);
        homeState.bump(homeCode);
        return t;
    }

    @Transactional
    public void updateTask(Long taskId, String name, String emoji, int intervalDays,
                           int creditValue, String availableWindows) {
        ChoreTask t = tasks.findById(taskId).orElseThrow();
        t.setName(name.trim());
        t.setEmoji(cleanEmoji(emoji));
        t.setIntervalDays(Math.max(0, intervalDays));
        t.setCreditValue(Math.max(0, creditValue));
        t.setAvailableWindows(availableWindows);
        tasks.save(t);
        homeState.bump(t.getHomeCode());
    }

    @Transactional
    public void deleteTask(Long taskId) {
        ChoreTask t = tasks.findById(taskId).orElseThrow();
        String homeCode = t.getHomeCode();
        completions.deleteByTaskId(taskId);
        tasks.delete(t);
        homeState.bump(homeCode);
    }

    public List<ChoreTask> tasksOf(String homeCode) {
        return tasks.findByHomeCodeOrderByCreatedAtAsc(homeCode);
    }

    /**
     * Tasks enriched, for a specific member, with everything the board needs: fairness
     * streak, booking state, interval due-date, rotation assignment, and whether (and why)
     * the chore is currently locked for that member.
     */
    public List<TaskView> taskViews(String homeCode, Long memberId) {
        return taskViews(homeCode, memberId, ZoneId.systemDefault());
    }

    /** Same, evaluating availability windows in the member's local time zone. */
    public List<TaskView> taskViews(String homeCode, Long memberId, ZoneId zone) {
        Home home = homes.findById(homeCode).orElseThrow();
        List<Member> memberList = membersOf(homeCode);
        List<ChoreTask> chores = tasksOf(homeCode);
        LocalDate today = LocalDate.now();
        LocalTime localNow = LocalTime.now(zone);
        boolean rotating = home.getDivisionStyle() == DivisionStyle.ROTATING;
        Long myAssignedChore = rotating ? rotationAssignedChoreId(home, memberId, today) : null;

        List<TaskView> result = new ArrayList<>();
        for (ChoreTask task : chores) {
            List<Completion> recent = activeCompletions(task.getId());

            int streak = 0;
            Long holderId = null;
            String holderName = null;
            if (!recent.isEmpty()) {
                holderId = recent.get(0).getMemberId();
                for (Completion c : recent) {
                    if (c.getMemberId().equals(holderId)) {
                        streak++;
                    } else {
                        break;
                    }
                }
                holderName = memberName(holderId);
            }

            Long bookerId = effectiveBookerId(task, home);
            String bookerName = bookerId == null ? null : memberName(bookerId);
            Instant bookingExpires = bookerId == null ? null : bookingExpiry(task, home);

            boolean due = isDue(task);
            LocalDate nextDue = nextDueDate(task);
            boolean inHours = TimeWindows.isWithinAny(task.getAvailableWindows(), localNow);

            Long assignedMemberId = rotating
                    ? rotationAssignedMemberId(home, task, memberList, chores, today) : null;
            String assignedName = assignedMemberId == null ? null : memberName(assignedMemberId);

            LockReason reason = computeLock(home, task, memberId, streak, holderId,
                    bookerId, due, inHours, rotating, myAssignedChore);

            result.add(new TaskView(task, recent.size(), streak, holderId, holderName,
                    bookerId, bookerName, bookingExpires, due, nextDue,
                    assignedMemberId, assignedName, reason != LockReason.NONE, reason));
        }
        return result;
    }

    private LockReason computeLock(Home home, ChoreTask task, Long memberId, int streak,
                                  Long holderId, Long bookerId, boolean due, boolean inHours,
                                  boolean rotating, Long myAssignedChore) {
        if (!due) {
            return LockReason.NOT_DUE;
        }
        if (!inHours) {
            return LockReason.OUTSIDE_HOURS;
        }
        if (rotating) {
            if (home.isRotationEnforced()
                    && (myAssignedChore == null || !myAssignedChore.equals(task.getId()))) {
                return LockReason.NOT_ASSIGNED;
            }
            return LockReason.NONE; // rotation ignores booking / streak
        }
        if (bookerId != null && !bookerId.equals(memberId)) {
            return LockReason.BOOKED;
        }
        if (streak >= MAX_IN_A_ROW && memberId != null && memberId.equals(holderId)) {
            return LockReason.STREAK;
        }
        return LockReason.NONE;
    }

    /** Non-REJECTED completions of a task, newest first. */
    private List<Completion> activeCompletions(Long taskId) {
        List<Completion> all = completions.findByTaskIdOrderByDoneAtDesc(taskId);
        all.removeIf(c -> c.getStatus() == CompletionStatus.REJECTED);
        return all;
    }

    private String memberName(Long memberId) {
        return members.findById(memberId).map(Member::getName).orElse("Someone");
    }

    // ---- Interval (every-N-days) chores ------------------------------------

    /** Date of the last time this chore was done (non-rejected), or null. */
    private LocalDate lastDoneDate(Long taskId) {
        List<Completion> recent = activeCompletions(taskId);
        return recent.isEmpty() ? null
                : recent.get(0).getDoneAt().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** The next date the chore becomes due (today if it has no interval or was never done). */
    public LocalDate nextDueDate(ChoreTask t) {
        if (t.getIntervalDays() <= 0) {
            return LocalDate.now();
        }
        LocalDate last = lastDoneDate(t.getId());
        return last == null ? LocalDate.now() : last.plusDays(t.getIntervalDays());
    }

    public boolean isDue(ChoreTask t) {
        return !LocalDate.now().isBefore(nextDueDate(t));
    }

    // ---- Booking ("I'll do it") --------------------------------------------

    /** Member holding a currently-valid (non-expired) booking on this task, or null. */
    private Long effectiveBookerId(ChoreTask t, Home home) {
        Instant expiry = bookingExpiry(t, home);
        if (expiry == null) {
            return null;
        }
        return Instant.now().isBefore(expiry) ? t.getBookedByMemberId() : null;
    }

    private Instant bookingExpiry(ChoreTask t, Home home) {
        if (t.getBookedByMemberId() == null || t.getBookedAt() == null) {
            return null;
        }
        return t.getBookedAt().plus(Duration.ofHours(Math.max(1, home.getBookingTimeoutHours())));
    }

    /** Reserve a chore for the member. Fails if someone else holds a live booking. */
    @Transactional
    public boolean bookChore(Long taskId, Long memberId) {
        ChoreTask task = tasks.findById(taskId).orElseThrow();
        Home home = homes.findById(task.getHomeCode()).orElseThrow();
        Long current = effectiveBookerId(task, home);
        if (current != null && !current.equals(memberId)) {
            return false;
        }
        task.setBookedByMemberId(memberId);
        task.setBookedAt(Instant.now());
        tasks.save(task);
        homeState.bump(task.getHomeCode());
        return true;
    }

    @Transactional
    public void cancelBooking(Long taskId, Long memberId) {
        ChoreTask task = tasks.findById(taskId).orElseThrow();
        if (memberId != null && memberId.equals(task.getBookedByMemberId())) {
            task.setBookedByMemberId(null);
            task.setBookedAt(null);
            tasks.save(task);
            homeState.bump(task.getHomeCode());
        }
    }

    // ---- Rotation division --------------------------------------------------

    /** The chore assigned to this member today under rotating division, or null. */
    public Long rotationAssignedChoreId(Home home, Long memberId, LocalDate date) {
        List<ChoreTask> chores = tasksOf(home.getCode());
        List<Member> memberList = membersOf(home.getCode());
        if (chores.isEmpty()) {
            return null;
        }
        int mi = indexOf(memberList, memberId);
        if (mi < 0) {
            return null;
        }
        int c = chores.size();
        int ci = (int) (((mi + date.toEpochDay()) % c + c) % c);
        return chores.get(ci).getId();
    }

    /** The member assigned to this chore today (inverse of the rotation), or null. */
    private Long rotationAssignedMemberId(Home home, ChoreTask task, List<Member> memberList,
                                          List<ChoreTask> chores, LocalDate date) {
        if (chores.isEmpty() || memberList.isEmpty()) {
            return null;
        }
        int c = chores.size();
        int ci = indexOfTask(chores, task.getId());
        int mi = (int) (((ci - date.toEpochDay()) % c + (long) c) % c);
        return mi < memberList.size() ? memberList.get(mi).getId() : null;
    }

    private static int indexOf(List<Member> list, Long memberId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(memberId)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfTask(List<ChoreTask> list, Long taskId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(taskId)) {
                return i;
            }
        }
        return -1;
    }

    // ---- Completing a chore -------------------------------------------------

    /**
     * Records that {@code member} did {@code task}, enforcing the "max N in a row"
     * fairness rule and the home's approval setting.
     */
    @Transactional
    public CompleteOutcome complete(Long taskId, Long memberId) {
        return complete(taskId, memberId, ZoneId.systemDefault());
    }

    /** Same, checking availability windows against the member's local time zone. */
    @Transactional
    public CompleteOutcome complete(Long taskId, Long memberId, ZoneId zone) {
        ChoreTask task = tasks.findById(taskId).orElseThrow();
        Member member = members.findById(memberId).orElseThrow();
        Home home = homes.findById(task.getHomeCode()).orElseThrow();
        boolean rotating = home.getDivisionStyle() == DivisionStyle.ROTATING;

        // 1) Interval: is the chore due yet?
        if (!isDue(task)) {
            return CompleteOutcome.blocked(LockReason.NOT_DUE, task, member);
        }
        // 1b) Availability windows: is it the right time of day?
        if (!TimeWindows.isWithinAny(task.getAvailableWindows(), LocalTime.now(zone))) {
            return CompleteOutcome.blocked(LockReason.OUTSIDE_HOURS, task, member);
        }
        // 2) Rotation: must be this member's assigned chore when enforced.
        if (rotating && home.isRotationEnforced()) {
            Long assigned = rotationAssignedChoreId(home, memberId, LocalDate.now());
            if (assigned == null || !assigned.equals(taskId)) {
                return CompleteOutcome.blocked(LockReason.NOT_ASSIGNED, task, member);
            }
        }

        int streak = 0;
        if (!rotating) {
            // 3) Booking: blocked if someone else holds a live booking.
            Long bookerId = effectiveBookerId(task, home);
            if (bookerId != null && !bookerId.equals(memberId)) {
                return CompleteOutcome.blocked(LockReason.BOOKED, task, member);
            }
            // 4) Fairness: max N of the same chore in a row.
            List<Completion> recent = activeCompletions(taskId);
            if (!recent.isEmpty() && recent.get(0).getMemberId().equals(memberId)) {
                for (Completion c : recent) {
                    if (c.getMemberId().equals(memberId)) {
                        streak++;
                    } else {
                        break;
                    }
                }
            }
            if (streak >= MAX_IN_A_ROW) {
                return CompleteOutcome.blocked(LockReason.STREAK, task, member);
            }
        }

        // Doing the chore clears any booking on it.
        if (task.getBookedByMemberId() != null) {
            task.setBookedByMemberId(null);
            task.setBookedAt(null);
            tasks.save(task);
        }

        boolean approval = home.isRequireApproval();
        CompletionStatus status = approval ? CompletionStatus.PENDING : CompletionStatus.APPROVED;

        boolean firstApprovedBefore = completions.existsByMemberIdAndTaskIdAndStatus(
                memberId, taskId, CompletionStatus.APPROVED);

        Completion saved = completions.save(new Completion(task.getHomeCode(), taskId, memberId, status));
        touch(home);
        homeState.bump(task.getHomeCode());

        if (approval) {
            return CompleteOutcome.pending(task, member, saved.getId());
        }
        CreditService.Award award = creditService.onApprovedCompletion(
                task, memberId, task.getHomeCode(), saved.getId());
        long memberTotal = completions.countByMemberIdAndStatus(memberId, CompletionStatus.APPROVED);
        return CompleteOutcome.done(task, member, memberTotal, !firstApprovedBefore,
                milestoneFor(memberTotal), rotating ? 0 : streak + 1, saved.getId(), award);
    }

    /**
     * An admin recording that somebody else did a chore — the member who has no phone of
     * their own, or who simply forgot to tap it.
     *
     * <p>Deliberately skips every lock a member's own tap goes through (interval,
     * availability hours, rotation, booking, fairness streak). Those exist to steer who
     * does what next; this is a statement about what already happened, and refusing to
     * record a chore that was demonstrably done would just be wrong.
     *
     * <p>Recorded {@code APPROVED} whatever the home's approval setting says — an admin
     * logging it <em>is</em> the approval — with the admin kept as the reviewer so the
     * history shows whose word it was. Credits and milestones follow as usual, and the
     * entry can be taken back from the admin's Recent chores list like any other.
     *
     * @param memberId the member who did the chore, not the admin doing the recording
     */
    @Transactional
    public CompleteOutcome completeFor(Long taskId, Long memberId, Long adminId) {
        ChoreTask task = tasks.findById(taskId).orElseThrow();
        Member member = members.findById(memberId).orElseThrow();
        Home home = homes.findById(task.getHomeCode()).orElseThrow();
        if (!task.getHomeCode().equals(member.getHomeCode())) {
            throw new IllegalArgumentException("Chore and member belong to different homes");
        }

        // Logging it settles the chore, so a booking on it has served its purpose.
        if (task.getBookedByMemberId() != null) {
            task.setBookedByMemberId(null);
            task.setBookedAt(null);
            tasks.save(task);
        }

        boolean firstApprovedBefore = completions.existsByMemberIdAndTaskIdAndStatus(
                memberId, taskId, CompletionStatus.APPROVED);

        Completion entry = new Completion(task.getHomeCode(), taskId, memberId,
                CompletionStatus.APPROVED);
        entry.setReviewedByMemberId(adminId);
        entry.setReviewedAt(Instant.now());
        Completion saved = completions.save(entry);
        touch(home);
        homeState.bump(task.getHomeCode());

        CreditService.Award award = creditService.onApprovedCompletion(
                task, memberId, task.getHomeCode(), saved.getId());
        long memberTotal = completions.countByMemberIdAndStatus(memberId, CompletionStatus.APPROVED);
        return CompleteOutcome.done(task, member, memberTotal, !firstApprovedBefore,
                milestoneFor(memberTotal), 0, saved.getId(), award);
    }

    // ---- "Other help" (something the chore list doesn't cover) --------------

    /** Longest description a member can write for other help — a line, not an essay. */
    public static final int MAX_HELP_LENGTH = 200;

    /**
     * Logs help that no chore covers: the member writes what they did and an admin accepts
     * or declines it (see {@link #approve(Long, Long, int)} / {@link #reject}).
     *
     * <p>Always PENDING, even in a home that doesn't require approval for chores. The text
     * is freeform and there is no chore behind it, so somebody has to read it before it
     * counts towards anyone's totals.
     *
     * @return empty if the home has the feature switched off or the description is blank
     */
    @Transactional
    public Optional<Completion> logOtherHelp(String homeCode, Long memberId, String description) {
        Home home = homes.findById(normalizeCode(homeCode)).orElseThrow();
        if (!home.isAllowOtherHelp()) {
            return Optional.empty();
        }
        String text = description == null ? "" : description.trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (text.length() > MAX_HELP_LENGTH) {
            text = text.substring(0, MAX_HELP_LENGTH);
        }
        Completion saved = completions.save(
                Completion.otherHelp(home.getCode(), memberId, text));
        touch(home);
        homeState.bump(home.getCode());
        return Optional.of(saved);
    }

    /** Other-help entries waiting for a decision, newest first (the admin's list). */
    public List<Completion> pendingOtherHelp(String homeCode) {
        return completions
                .findByHomeCodeAndStatusOrderByDoneAtDesc(homeCode, CompletionStatus.PENDING)
                .stream().filter(Completion::isOtherHelp).toList();
    }

    /** How many of this member's own help entries are still waiting — shown on their card. */
    public long pendingOtherHelpCount(String homeCode, Long memberId) {
        return pendingOtherHelp(homeCode).stream()
                .filter(c -> c.getMemberId().equals(memberId)).count();
    }

    @Transactional
    public void setFeedback(Long completionId, Feedback feedback) {
        Completion c = completions.findById(completionId).orElseThrow();
        c.setFeedback(feedback);
        completions.save(c);
        homeState.bump(c.getHomeCode());
    }

    // ---- Approvals ----------------------------------------------------------

    /** Pending chore completions. Other help has its own list, since it is decided
     *  differently (a reward to name, and maybe a new chore to add). */
    public List<Completion> pendingApprovals(String homeCode) {
        return completions
                .findByHomeCodeAndStatusOrderByDoneAtDesc(homeCode, CompletionStatus.PENDING)
                .stream().filter(c -> !c.isOtherHelp()).toList();
    }

    public long pendingCount(String homeCode) {
        return completions.countByHomeCodeAndStatus(homeCode, CompletionStatus.PENDING);
    }

    /** Approves a pending completion; returns the celebration outcome for that member. */
    @Transactional
    public CompleteOutcome approve(Long completionId, Long adminId) {
        return approve(completionId, adminId, 0);
    }

    /**
     * Approves a pending completion, or accepts an other-help entry. {@code credits} is only
     * used for other help — a chore carries its own credit value, but help written by hand
     * has nothing to read a reward off, so the admin sets it when they accept.
     */
    @Transactional
    public CompleteOutcome approve(Long completionId, Long adminId, int credits) {
        Completion c = completions.findById(completionId).orElseThrow();
        ChoreTask task = c.isOtherHelp() ? null : tasks.findById(c.getTaskId()).orElseThrow();
        Member member = members.findById(c.getMemberId()).orElseThrow();

        boolean firstApprovedBefore = task != null && completions.existsByMemberIdAndTaskIdAndStatus(
                c.getMemberId(), c.getTaskId(), CompletionStatus.APPROVED);

        c.setStatus(CompletionStatus.APPROVED);
        c.setReviewedByMemberId(adminId);
        c.setReviewedAt(java.time.Instant.now());
        completions.save(c);
        touchHome(c.getHomeCode());
        homeState.bump(c.getHomeCode());

        CreditService.Award award = task != null
                ? creditService.onApprovedCompletion(task, c.getMemberId(), c.getHomeCode(), c.getId())
                : creditService.onApprovedHelp(c.getHomeCode(), c.getMemberId(), c.getId(),
                        credits, c.getNote());
        long memberTotal = completions.countByMemberIdAndStatus(c.getMemberId(), CompletionStatus.APPROVED);
        return CompleteOutcome.done(task, member, memberTotal, task != null && !firstApprovedBefore,
                milestoneFor(memberTotal), 0, c.getId(), award);
    }

    @Transactional
    public void reject(Long completionId, Long adminId) {
        Completion c = completions.findById(completionId).orElseThrow();
        c.setStatus(CompletionStatus.REJECTED);
        c.setReviewedByMemberId(adminId);
        c.setReviewedAt(java.time.Instant.now());
        completions.save(c);
        homeState.bump(c.getHomeCode());
    }

    /**
     * Removes a completion outright — the admin's "that didn't happen" correction. Any
     * credits it earned go with it, so an undone chore can't leave phantom 💎 behind.
     */
    @Transactional
    public void deleteCompletion(Long completionId) {
        Completion c = completions.findById(completionId).orElseThrow();
        String homeCode = c.getHomeCode();
        creditService.deleteForCompletion(completionId);
        completions.delete(c);
        homeState.bump(homeCode);
    }

    /** How long a member may take back a chore they logged by mistake. */
    public static final Duration UNDO_WINDOW = Duration.ofMinutes(10);

    /**
     * A member taking back their own accidental tap. Deliberately narrow: only your own
     * completion, and only while it is recent — beyond that it is an admin correction, so
     * nobody can quietly rewrite last week's leaderboard.
     *
     * @return false if it isn't yours, or the window has passed
     */
    @Transactional
    public boolean undoCompletion(Long completionId, Long memberId) {
        Completion c = completions.findById(completionId).orElse(null);
        if (c == null || !c.getMemberId().equals(memberId) || !isUndoable(c)) {
            return false;
        }
        deleteCompletion(completionId);
        return true;
    }

    private boolean isUndoable(Completion c) {
        return c.getDoneAt().isAfter(Instant.now().minus(UNDO_WINDOW));
    }

    /**
     * This member's most recent completion while it can still be taken back, so the board
     * can offer an undo even after the celebration dialog has been dismissed.
     */
    public Optional<Completion> undoableCompletion(Long memberId) {
        Instant since = Instant.now().minus(UNDO_WINDOW);
        return completions.findByMemberIdAndDoneAtAfterOrderByDoneAtDesc(memberId, since)
                .stream().findFirst();
    }

    /** Recent completions in a home, newest first — the admin's correction list. */
    public List<Completion> recentCompletions(String homeCode, int limit) {
        return completions.findByHomeCodeOrderByDoneAtDesc(homeCode).stream()
                .limit(limit).toList();
    }

    // ---- Counts & daily target ---------------------------------------------

    /** Number of APPROVED completions by this member (leaderboard count). */
    public long completionCount(Long memberId) {
        return completions.countByMemberIdAndStatus(memberId, CompletionStatus.APPROVED);
    }

    /** APPROVED completions by this member with doneAt on the given local date. */
    public long doneOn(Long memberId, LocalDate date) {
        ZoneId zone = ZoneId.systemDefault();
        return completions.findByMemberIdAndStatus(memberId, CompletionStatus.APPROVED).stream()
                .filter(c -> c.getDoneAt().atZone(zone).toLocalDate().equals(date))
                .count();
    }

    public long doneToday(Long memberId) {
        return doneOn(memberId, LocalDate.now());
    }

    // ---- Helpers ------------------------------------------------------------

    private static Integer milestoneFor(long total) {
        for (int m : MILESTONES) {
            if (total == m) {
                return m;
            }
        }
        return null;
    }

    private static String cleanEmoji(String emoji) {
        return (emoji == null || emoji.isBlank()) ? "✅" : emoji.trim();
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String generatePin() {
        return String.format("%04d", RANDOM.nextInt(10000));
    }

    /** A 128-bit secret for a rejoining device — long enough that it can't be guessed. */
    private static String generateToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    // ---- DTOs ---------------------------------------------------------------

    /** Why a chore can't be completed right now by a given member (NONE = it can). */
    public enum LockReason { NONE, STREAK, BOOKED, NOT_DUE, NOT_ASSIGNED, OUTSIDE_HOURS }

    /** A task enriched for one member: streak, booking, interval, rotation and lock state. */
    public record TaskView(
            ChoreTask task,
            long totalDone,
            int streak,
            Long streakHolderId,
            String streakHolderName,
            Long bookedById,
            String bookedByName,
            Instant bookingExpiresAt,
            boolean due,
            LocalDate nextDueDate,
            Long assignedMemberId,
            String assignedMemberName,
            boolean lockedForMe,
            LockReason lockReason) {

        public boolean bookedByMe(Long memberId) {
            return bookedById != null && bookedById.equals(memberId);
        }

        public boolean assignedToMe(Long memberId) {
            return assignedMemberId != null && assignedMemberId.equals(memberId);
        }
    }

    /** The result of attempting to complete (or approve) a chore. {@code task} is null when
     *  what was accepted was other help rather than a chore. */
    public record CompleteOutcome(
            boolean allowed,
            boolean pending,
            LockReason blockReason,
            ChoreTask task,
            Member member,
            long memberTotal,
            boolean newChoreForMember,
            Integer milestone,
            int newStreak,
            Long completionId,
            int creditsAwarded,
            Integer spreeDays,
            int spreeCredits) {

        static CompleteOutcome blocked(LockReason reason, ChoreTask task, Member member) {
            return new CompleteOutcome(false, false, reason, task, member, 0, false, null, 0, null,
                    0, null, 0);
        }

        static CompleteOutcome pending(ChoreTask task, Member member, Long completionId) {
            return new CompleteOutcome(true, true, LockReason.NONE, task, member, 0, false, null, 0,
                    completionId, 0, null, 0);
        }

        static CompleteOutcome done(ChoreTask task, Member member, long total, boolean newChore,
                                    Integer milestone, int newStreak, Long completionId,
                                    CreditService.Award award) {
            return new CompleteOutcome(true, false, LockReason.NONE, task, member, total, newChore,
                    milestone, newStreak, completionId,
                    award.choreCredits(), award.spreeDays(), award.spreeCredits());
        }
    }
}
