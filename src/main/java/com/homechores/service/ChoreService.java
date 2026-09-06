package com.homechores.service;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.ChoreGroup;
import com.homechores.domain.ChoreReminderRepository;
import com.homechores.domain.ChoreGroupRepository;
import com.homechores.domain.ChoreTaskRepository;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.CompletionStatus;
import com.homechores.domain.DivisionStyle;
import com.homechores.domain.Feedback;
import com.homechores.domain.Home;
import com.homechores.domain.HomeRepository;
import com.homechores.domain.InputLimits;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import com.homechores.domain.PushSubscriptionRepository;
import com.homechores.domain.RejoinRequest;
import com.homechores.domain.RejoinRequestRepository;
import com.homechores.domain.RejoinStatus;
import com.homechores.domain.Seasons;
import com.homechores.domain.TimeWindows;
import com.homechores.i18n.Translations;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for homes, chores, completions, fairness, approvals and stats.
 */
@Service
public class ChoreService {

    private static final Logger log = LoggerFactory.getLogger(ChoreService.class);

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
    private final PushSubscriptionRepository pushSubscriptions;
    private final ChoreGroupRepository groups;
    private final ChoreReminderRepository choreReminders;
    private final HomeState homeState;
    private final CreditService creditService;
    private final Translations translations;

    /** Whether pre-secret devices may still be silently upgraded — see
     *  {@link #migrateLegacyIdentity}. On by default; an operator flips it off with an
     *  external property override once their existing devices have all visited. */
    private final boolean legacyIdentityMigration;

    /** Hours after this server first started during which legacy migration stays open;
     *  past it, a returning pre-upgrade device uses the normal (gated) rejoin flow. */
    private final long legacyMigrationWindowHours;

    /** When this service instance started — the anchor for the migration time-box. */
    private final Instant serverStart = Instant.now();

    public ChoreService(HomeRepository homes, MemberRepository members,
                        ChoreTaskRepository tasks, CompletionRepository completions,
                        RejoinRequestRepository rejoins,
                        PushSubscriptionRepository pushSubscriptions,
                        ChoreGroupRepository groups,
                        ChoreReminderRepository choreReminders, HomeState homeState,
                        CreditService creditService, Translations translations,
                        @org.springframework.beans.factory.annotation.Value(
                                "${homechores.identity.legacy-migration:true}")
                        boolean legacyIdentityMigration,
                        @org.springframework.beans.factory.annotation.Value(
                                "${homechores.identity.legacy-migration-window-hours:48}")
                        long legacyMigrationWindowHours) {
        this.homes = homes;
        this.members = members;
        this.tasks = tasks;
        this.completions = completions;
        this.rejoins = rejoins;
        this.pushSubscriptions = pushSubscriptions;
        this.groups = groups;
        this.choreReminders = choreReminders;
        this.homeState = homeState;
        this.creditService = creditService;
        this.translations = translations;
        this.legacyIdentityMigration = legacyIdentityMigration;
        this.legacyMigrationWindowHours = legacyMigrationWindowHours;
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

        Home home = new Home(code, InputLimits.clip(homeName, InputLimits.HOME_NAME), generatePin());
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
        // Central chokepoint for admin edits: the UI caps field lengths, but those caps
        // live in the browser — re-assert them (and the PIN's shape) before persisting.
        home.setName(InputLimits.clip(home.getName(), InputLimits.HOME_NAME));
        if (home.getAdminPin() == null || !home.getAdminPin().matches("\\d{4}")) {
            throw new IllegalArgumentException("admin PIN must be exactly 4 digits");
        }
        homes.save(home);
        homeState.bump(home.getCode());
    }

    /**
     * Adds a member to an existing home, bypassing the join-approval gate. Returns empty if
     * the code is unknown.
     *
     * <p>Not what the landing page calls — that is {@link #requestJoin}, which honours
     * {@link Home#isApproveJoin()}. This is the trusted path: an admin approving a join
     * request, and test fixtures.
     */
    @Transactional
    public Optional<Member> joinHome(String code, String memberName) {
        String norm = normalizeCode(code);
        if (!homes.existsById(norm)) {
            return Optional.empty();
        }
        touchHome(norm);
        return Optional.of(addMember(norm, memberName, false));
    }

    /** Outcome of a first-time join attempt; exactly one of {@code token} (PENDING) and
     *  {@code member} (SIGNED_IN) is set. */
    public record JoinOutcome(RejoinResult result, String token, Member member) {
    }

    /**
     * A stranger at the door: someone asking to join the home for the first time with just
     * the code. When the home gates joins (the default), no member is created yet — a
     * pending request is raised for an admin to decide on, so a guessed or leaked code
     * can't plant anyone on the board by itself. The member only comes into being in
     * {@link #decideRejoin} when an admin approves.
     */
    @Transactional
    public JoinOutcome requestJoin(String code, String memberName) {
        String norm = normalizeCode(code);
        Home home = homes.findById(norm).orElse(null);
        if (home == null || memberName == null || memberName.isBlank()) {
            return new JoinOutcome(RejoinResult.UNKNOWN, null, null);
        }
        if (!home.isApproveJoin()) {
            touchHome(norm);
            return new JoinOutcome(RejoinResult.SIGNED_IN, null,
                    addMember(norm, memberName, false));
        }
        String token = generateToken();
        rejoins.save(RejoinRequest.joinRequest(norm,
                InputLimits.clip(memberName, InputLimits.MEMBER_NAME), token));
        homeState.bump(norm);
        return new JoinOutcome(RejoinResult.PENDING, token, null);
    }

    /**
     * The member using this nickname, matched case-insensitively — how a returning device
     * points at itself without the app ever listing the home's members to someone who only
     * knows the code.
     */
    public Optional<Member> findMemberByName(String homeCode, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = name.trim();
        return membersOf(normalizeCode(homeCode)).stream()
                .filter(m -> m.getName().equalsIgnoreCase(wanted))
                .findFirst();
    }

    private Member addMember(String homeCode, String name, boolean admin) {
        int existing = members.findByHomeCodeOrderByJoinedAtAsc(homeCode).size();
        String color = COLORS[existing % COLORS.length];
        Member member = new Member(homeCode,
                InputLimits.clip(name, InputLimits.MEMBER_NAME), color, admin);
        // Every UI path that creates a member is gated on the user-agreement checkbox,
        // so creation time is consent time.
        member.setTermsAcceptedAt(Instant.now());
        Member m = members.save(member);
        homeState.bump(homeCode);
        return m;
    }

    public Optional<Member> findMember(Long id) {
        return id == null ? Optional.empty() : members.findById(id);
    }

    // ---- Device secrets -------------------------------------------------------
    //
    // A member id is a small sequential number, so "memberId|homeCode" in a browser's local
    // storage proves nothing — anyone could write another member's id there and walk in as
    // them, admin rights included. Each sign-in therefore issues a fresh 128-bit secret that
    // travels with the stored identity; restoring silently requires presenting it. Only its
    // hash is persisted. Issuing on every sign-in also means an approved rejoin cuts the
    // *previous* device's stored identity off — exactly what you want when the old device
    // was lost or the identity stolen.

    /** Issues a fresh secret for this member's device and returns it (the only time the
     *  plaintext exists server-side); any previously issued secret stops working. */
    @Transactional
    public String issueDeviceSecret(Long memberId) {
        Member member = members.findById(memberId).orElseThrow();
        String secret = generateToken();
        member.setDeviceSecretHash(sha256(secret));
        members.save(member);
        return secret;
    }

    /** Legacy-migration attempts allowed per home inside {@link #MIGRATION_ATTEMPT_WINDOW},
     *  enough for a whole family to migrate but far too few to enumerate member ids. */
    static final int MAX_MIGRATION_ATTEMPTS = 10;
    static final Duration MIGRATION_ATTEMPT_WINDOW = Duration.ofHours(1);

    private static final class AttemptWindow {
        int count;
        Instant windowStart;
    }

    private final java.util.concurrent.ConcurrentHashMap<String, AttemptWindow> migrationAttempts =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Spends one from a home's rolling migration budget; false once it is exhausted. */
    private boolean migrationAttemptAllowed(String homeCode) {
        AttemptWindow w = migrationAttempts.computeIfAbsent(homeCode, k -> new AttemptWindow());
        synchronized (w) {
            Instant now = Instant.now();
            if (w.windowStart == null || now.isAfter(w.windowStart.plus(MIGRATION_ATTEMPT_WINDOW))) {
                w.windowStart = now;
                w.count = 0;
            }
            if (w.count >= MAX_MIGRATION_ATTEMPTS) {
                return false;
            }
            w.count++;
            return true;
        }
    }

    /**
     * Upgrades a device that stored its identity before secrets existed: trust it once,
     * issue it a secret, and from then on require that secret like everyone else.
     *
     * <p>Trust-on-first-use, and deliberately bounded, because a legacy value carries
     * nothing the server can verify — so an outsider who knew a home code could otherwise
     * present {@code memberId|homeCode} and be signed in as any not-yet-migrated member.
     * Four guards keep that window small:
     * <ul>
     *   <li>the {@code legacy-migration} flag (an operator can switch it off entirely);
     *   <li>a hard time-box — migration is only open for a window after this server started;
     *   <li>a per-home rate limit, so member ids can't be enumerated against a known code;
     *   <li>eligibility limited to rows that predate this feature (null {@code createdAt}),
     *       so a freshly approved join or a restored member — which the migration must never
     *       let bypass approval — is excluded and uses the normal gated rejoin flow.
     * </ul>
     * A member is also only ever migratable while it still has no secret, so each one gets
     * exactly one free pass and issuing the secret cuts any other device off.
     *
     * @return the freshly issued secret, or empty when this identity doesn't qualify
     */
    @Transactional
    public Optional<String> migrateLegacyIdentity(Long memberId, String homeCode) {
        if (!legacyIdentityMigration || memberId == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(serverStart.plus(Duration.ofHours(legacyMigrationWindowHours)))) {
            return Optional.empty(); // past the one-time migration window
        }
        // Spend from the budget BEFORE any lookup, so a miss (wrong/guessed id) costs the
        // attacker just as much as a hit — that is what makes enumeration infeasible.
        String norm = normalizeCode(homeCode);
        if (!migrationAttemptAllowed(norm)) {
            return Optional.empty();
        }
        Member member = members.findById(memberId).orElse(null);
        if (member == null || !member.getHomeCode().equals(norm)
                || member.getDeviceSecretHash() != null
                || member.getCreatedAt() != null) {
            return Optional.empty();
        }
        return Optional.of(issueDeviceSecret(memberId));
    }

    /** Whether this secret is the one last issued to the member's device. */
    public boolean verifyDeviceSecret(Long memberId, String secret) {
        if (memberId == null || secret == null || secret.isBlank()) {
            return false;
        }
        return members.findById(memberId)
                .map(Member::getDeviceSecretHash)
                .map(hash -> java.security.MessageDigest.isEqual(
                        hash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        sha256(secret).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .orElse(false);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // mandated by the JLS
        }
    }

    public List<Member> membersOf(String homeCode) {
        return members.findByHomeCodeOrderByJoinedAtAsc(homeCode);
    }

    /**
     * Deletes a home and everything belonging to it — members, chores, chore groups,
     * completions, credits, spree tiers, rejoin requests and pending chore reminders. Irreversible; the caller is responsible
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
        pushSubscriptions.deleteByHomeCode(norm);
        choreReminders.deleteByHomeCode(norm);
        completions.deleteByHomeCode(norm);
        creditService.deleteForHome(norm);
        tasks.deleteByHomeCode(norm);
        groups.deleteByHomeCode(norm);
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
        if (pinGiven && !checkPin(norm, home.getAdminPin(), pin)) {
            return new Rejoin(RejoinResult.WRONG_PIN, null);
        }
        if (pinGiven || !home.isApproveRejoin()) {
            // Signing in settles the matter — a pending request this member abandoned on
            // another attempt must not stay approvable in the admin's queue.
            dropPendingRejoins(norm, memberId);
            touchHome(norm);
            return new Rejoin(RejoinResult.SIGNED_IN, null);
        }
        // Only the newest device may be waiting for a given member, so an abandoned request
        // on an old phone can't be used to walk in later.
        dropPendingRejoins(norm, memberId);
        String token = generateToken();
        rejoins.save(new RejoinRequest(norm, memberId, token));
        homeState.bump(norm);
        return new Rejoin(RejoinResult.PENDING, token);
    }

    /** Removes this member's waiting requests. (memberId is on the left of the equals: a
     *  pending first-time join request has no member id.) */
    private void dropPendingRejoins(String homeCode, Long memberId) {
        rejoins.findByHomeCodeAndStatusOrderByRequestedAtAsc(homeCode, RejoinStatus.PENDING)
                .stream()
                .filter(r -> memberId.equals(r.getMemberId()))
                .forEach(rejoins::delete);
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

    /** Approves (or rejects) a pending rejoin or first-time join request. Approving a join
     *  is the moment the member is actually created. The waiting device picks the decision
     *  up through the home's revision signal. */
    @Transactional
    public boolean decideRejoin(Long requestId, Long adminId, boolean approve) {
        RejoinRequest r = rejoins.findById(requestId).orElse(null);
        if (r == null || r.getStatus() != RejoinStatus.PENDING) {
            return false;
        }
        if (approve && r.isJoin()) {
            r.setMemberId(addMember(r.getHomeCode(), r.getRequestedName(), false).getId());
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

    /** How old a join/rejoin request may get before the sweep removes it. */
    public static final Duration REJOIN_MAX_AGE = Duration.ofHours(48);

    /**
     * Deletes join/rejoin requests past {@link #REJOIN_MAX_AGE}, whatever their status.
     * Pending ones are abandoned attempts that would otherwise sit in the admin's queue
     * (and keep a stranger's requested nickname in the database) forever; an APPROVED one
     * whose device never came back is worse — a live sign-in token nobody is watching.
     */
    @Scheduled(fixedDelayString = "${homechores.rejoin.sweep-ms:3600000}",
               initialDelayString = "${homechores.rejoin.sweep-ms:3600000}")
    @Transactional
    public int expireStaleRejoins() {
        List<RejoinRequest> stale =
                rejoins.findByRequestedAtBefore(Instant.now().minus(REJOIN_MAX_AGE));
        if (stale.isEmpty()) {
            return 0;
        }
        Set<String> affectedHomes = new LinkedHashSet<>();
        for (RejoinRequest r : stale) {
            rejoins.delete(r);
            if (r.getStatus() == RejoinStatus.PENDING) {
                affectedHomes.add(r.getHomeCode()); // its row in an open admin queue
            }
        }
        affectedHomes.forEach(homeState::bump);
        log.info("Expired {} stale join/rejoin request(s)", stale.size());
        return stale.size();
    }

    // ---- Admin PIN brute-force gate ------------------------------------------
    //
    // The PIN is 4 digits: 10,000 possibilities, trivially enumerable without a brake.
    // Both places that check it (claiming admin, skipping the rejoin gate) go through this
    // per-home counter — a handful of typos costs nothing, a scripted sweep hits the wall
    // after five tries. In-memory on purpose: this is a single-node app, and a restart
    // forgetting the counters is fine.

    /** Wrong-PIN attempts allowed per home before the PIN check locks. */
    static final int MAX_PIN_FAILURES = 5;
    /** How long the PIN check stays locked after too many failures. */
    static final Duration PIN_LOCKOUT = Duration.ofMinutes(15);

    private static final class PinGate {
        int failures;
        Instant lockedUntil;
    }

    private final java.util.concurrent.ConcurrentHashMap<String, PinGate> pinGates =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Whether this home's PIN may even be checked right now. */
    private boolean pinAttemptAllowed(String homeCode) {
        PinGate g = pinGates.get(homeCode);
        if (g == null) {
            return true;
        }
        synchronized (g) {
            return g.lockedUntil == null || Instant.now().isAfter(g.lockedUntil);
        }
    }

    private void recordPinFailure(String homeCode) {
        PinGate g = pinGates.computeIfAbsent(homeCode, k -> new PinGate());
        synchronized (g) {
            if (++g.failures >= MAX_PIN_FAILURES) {
                g.lockedUntil = Instant.now().plus(PIN_LOCKOUT);
                g.failures = 0;
            }
        }
    }

    private void clearPinFailures(String homeCode) {
        pinGates.remove(homeCode);
    }

    /** One guarded PIN check: false for a wrong PIN or a locked gate, and each wrong
     *  answer moves the gate closer to locking. */
    private boolean checkPin(String homeCode, String expected, String given) {
        if (!pinAttemptAllowed(homeCode)) {
            return false;
        }
        if (given == null || !given.trim().equals(expected)) {
            recordPinFailure(homeCode);
            return false;
        }
        clearPinFailures(homeCode);
        return true;
    }

    // ---- Admin & member management -----------------------------------------

    /** Attempts to grant admin rights to a member by verifying the home's admin PIN. */
    @Transactional
    public boolean claimAdmin(Long memberId, String pin) {
        Member member = members.findById(memberId).orElseThrow();
        Home home = homes.findById(member.getHomeCode()).orElseThrow();
        if (checkPin(home.getCode(), home.getAdminPin(), pin)) {
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
        member.setName(InputLimits.clip(name, InputLimits.MEMBER_NAME));
        members.save(member);
        homeState.bump(member.getHomeCode());
    }

    /** Sets (or with null/garbage clears) a member's avatar from the fixed catalog. */
    @Transactional
    public void setAvatar(Long memberId, String avatarId) {
        Member member = members.findById(memberId).orElseThrow();
        member.setAvatar(com.homechores.domain.Avatars.sanitize(avatarId));
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
        pushSubscriptions.deleteByMemberId(memberId);
        choreReminders.deleteByMemberId(memberId);
        members.delete(member);
        homeState.bump(homeCode);
        return true;
    }

    // ---- Tasks (CRUD) -------------------------------------------------------

    /** The starter chores every new home gets, named in the creator's language. */
    private void seedDefaultTasks(String homeCode, Locale locale) {
        Instant base = Instant.now();
        int i = 0;
        i = seed(homeCode, locale, base, i, "chore.default.emptyDishwasher", "🍽️", 0, null, null);
        i = seed(homeCode, locale, base, i, "chore.default.trash", "🗑️", 0, null, null);
        i = seed(homeCode, locale, base, i, "chore.default.vacuum", "🧹", 0, null, null);
        i = seed(homeCode, locale, base, i, "chore.default.cookDinner", "🍳", 0, null, null);
        i = seed(homeCode, locale, base, i, "chore.default.fillDishwasher", "🫧", 0, null, null);
        i = seed(homeCode, locale, base, i, "chore.default.hangLaundry", "🧺", 0, null, null);
        i = seed(homeCode, locale, base, i, "chore.default.foldLaundry", "👕", 0, null, null);
        i = seed(homeCode, locale, base, i, "chore.default.vestibule", "🧥", 0, null, null);
        i = seed(homeCode, locale, base, i, "chore.default.salad", "🥗", 0, null, null);
        i = seed(homeCode, locale, base, i, "chore.default.waterPlants", "🪴", 7, null, null);
        seed(homeCode, locale, base, i, "chore.default.dogOut", "🐕", 0, "08:00-10:00,18:00-22:00", null);
    }

    /** Saves one seeded chore with a strictly increasing createdAt so list order is stable. */
    private int seed(String homeCode, Locale locale, Instant base, int index,
                     String nameKey, String emoji, int intervalDays, String windows,
                     String seasons) {
        ChoreTask t = new ChoreTask(homeCode, translations.getTranslation(nameKey, locale), emoji);
        t.setIntervalDays(intervalDays);
        t.setAvailableWindows(windows);
        t.setSeasons(seasons);
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
        return addTask(homeCode, name, emoji, intervalDays, creditValue, availableWindows, null);
    }

    @Transactional
    public ChoreTask addTask(String homeCode, String name, String emoji, int intervalDays,
                             int creditValue, String availableWindows, String seasons) {
        return addTask(homeCode, name, emoji, intervalDays, creditValue, availableWindows,
                seasons, null);
    }

    /** The full form. {@code groupId} null puts the chore in the ungrouped section, which is
     *  where every chore added from the board's ＋ card lands. */
    @Transactional
    public ChoreTask addTask(String homeCode, String name, String emoji, int intervalDays,
                             int creditValue, String availableWindows, String seasons,
                             Long groupId) {
        ChoreTask t = new ChoreTask(homeCode,
                InputLimits.clip(name, InputLimits.TASK_NAME), cleanEmoji(emoji));
        t.setIntervalDays(clampDays(intervalDays));
        t.setCreditValue(clampCredits(creditValue));
        t.setAvailableWindows(clipWindows(availableWindows));
        t.setSeasons(seasons);
        // Appends rather than renumbering: on a legacy home where every chore still sits at 0 this
        // writes a larger number than its neighbours, and the chore still renders last because
        // createdAt is the tiebreaker. Correct by construction, no forced migration.
        applyGroup(t, groupId);
        t = tasks.save(t);
        homeState.bump(homeCode);
        return t;
    }

    /**
     * Replaces every editable field of a chore. There is deliberately no shorter overload: a
     * full-replace method that silently nulled a field the caller forgot would quietly delete data.
     */
    @Transactional
    public void updateTask(Long taskId, String name, String emoji, int intervalDays,
                           int creditValue, String availableWindows, String seasons) {
        ChoreTask existing = tasks.findById(taskId).orElseThrow();
        updateTask(taskId, name, emoji, intervalDays, creditValue, availableWindows, seasons,
                existing.getGroupId());
    }

    /** The full form, group included — once the editor has a group picker, group membership is
     *  an editable field like any other. Changing it appends the chore to the target group, so
     *  one dialog save stays one write and one redraw on every connected phone. */
    @Transactional
    public void updateTask(Long taskId, String name, String emoji, int intervalDays,
                           int creditValue, String availableWindows, String seasons,
                           Long groupId) {
        ChoreTask t = tasks.findById(taskId).orElseThrow();
        t.setName(InputLimits.clip(name, InputLimits.TASK_NAME));
        t.setEmoji(cleanEmoji(emoji));
        t.setIntervalDays(clampDays(intervalDays));
        t.setCreditValue(clampCredits(creditValue));
        t.setAvailableWindows(clipWindows(availableWindows));
        t.setSeasons(seasons);
        applyGroup(t, groupId);
        tasks.save(t);
        homeState.bump(t.getHomeCode());
    }

    @Transactional
    public void deleteTask(Long taskId) {
        ChoreTask t = tasks.findById(taskId).orElseThrow();
        String homeCode = t.getHomeCode();
        completions.deleteByTaskId(taskId);
        choreReminders.deleteByTaskId(taskId);
        tasks.delete(t);
        homeState.bump(homeCode);
    }

    /**
     * The home's chores in BOARD order: group by group in the admin's group order, and within
     * each group by position, then creation time. Ungrouped chores come last — which is also
     * where a chore lands when its group is deleted, or when it is added from the board's ＋ card.
     *
     * <p><strong>Cosmetic order only.</strong> Nothing may derive meaning from a chore's position
     * in this list; an admin changes it with two taps. In particular the daily rotation must never
     * index it — that is what {@link #tasksInRotationOrder} exists for.
     *
     * <p>Buckets in Java rather than in a derived query, for two reasons: a null groupId cannot be
     * matched by a generated {@code = null} predicate, and a groupId pointing at a group that no
     * longer exists has to fall back to "ungrouped" rather than dropping the chore off the board.
     * The extra statement is one constant group query per call, not one per chore.
     */
    public List<ChoreTask> tasksOf(String homeCode) {
        List<ChoreTask> ordered = tasks.findByHomeCodeOrderBySortOrderAscCreatedAtAscIdAsc(homeCode);
        List<ChoreGroup> groupList = groupsOf(homeCode);
        if (groupList.isEmpty()) {
            return ordered; // the overwhelmingly common case: no grouping, nothing to interleave
        }
        Map<Long, List<ChoreTask>> buckets = new LinkedHashMap<>();
        groupList.forEach(g -> buckets.put(g.getId(), new ArrayList<>()));
        List<ChoreTask> ungrouped = new ArrayList<>();
        for (ChoreTask t : ordered) {
            List<ChoreTask> bucket = t.getGroupId() == null ? null : buckets.get(t.getGroupId());
            (bucket != null ? bucket : ungrouped).add(t);
        }
        List<ChoreTask> result = new ArrayList<>(ordered.size());
        buckets.values().forEach(result::addAll);
        result.addAll(ungrouped);
        return result;
    }

    // ---- Chore groups and board order ---------------------------------------

    /** The home's chore groups, in the order the admin arranged them. */
    public List<ChoreGroup> groupsOf(String homeCode) {
        return groups.findByHomeCodeOrderBySortOrderAscIdAsc(homeCode);
    }

    public Optional<ChoreGroup> findGroup(Long groupId) {
        return groups.findById(groupId);
    }

    @Transactional
    public ChoreGroup addGroup(String homeCode, String name, String emoji) {
        ChoreGroup g = new ChoreGroup(homeCode,
                InputLimits.clip(name, InputLimits.GROUP_NAME), groupEmoji(emoji));
        g.setSortOrder(groupsOf(homeCode).size()); // append
        ChoreGroup saved = groups.save(g);
        homeState.bump(homeCode);
        return saved;
    }

    @Transactional
    public void updateGroup(Long groupId, String name, String emoji) {
        ChoreGroup g = groups.findById(groupId).orElseThrow();
        g.setName(InputLimits.clip(name, InputLimits.GROUP_NAME));
        g.setEmoji(groupEmoji(emoji));
        groups.save(g);
        homeState.bump(g.getHomeCode());
    }

    /**
     * Removes a group and keeps every chore that was in it.
     *
     * <p>A group is a label, not a container. Deleting a label must never take a family's chores
     * — and their whole completion history — with it; that is what {@link #deleteTask} is for, and
     * it asks first. The chores become ungrouped and drop to the bottom of the board in their
     * existing relative order.
     */
    @Transactional
    public void deleteGroup(Long groupId) {
        ChoreGroup g = groups.findById(groupId).orElseThrow();
        String homeCode = g.getHomeCode();
        for (ChoreTask t : tasks.findByHomeCodeOrderByCreatedAtAsc(homeCode)) {
            if (groupId.equals(t.getGroupId())) {
                t.setGroupId(null);
                tasks.save(t);
            }
        }
        groups.delete(g);
        renumberGroups(homeCode);
        homeState.bump(homeCode);
    }

    /** Moves a group one place up (-1) or down (+1). False when it is already at that end. */
    @Transactional
    public boolean moveGroup(Long groupId, int direction) {
        ChoreGroup g = groups.findById(groupId).orElseThrow();
        String homeCode = g.getHomeCode();
        renumberGroups(homeCode);
        List<ChoreGroup> all = groupsOf(homeCode);
        int i = indexOfGroup(all, groupId);
        int j = i + Integer.signum(direction);
        if (i < 0 || j < 0 || j >= all.size()) {
            return false;
        }
        swapOrder(all.get(i), all.get(j));
        groups.saveAll(List.of(all.get(i), all.get(j)));
        homeState.bump(homeCode);
        return true;
    }

    /**
     * Moves a chore one place up (-1) or down (+1) <em>within its own group</em>. False when it is
     * already at that end — which is what lets the Admin tab disable the button rather than offer
     * a tap that does nothing.
     */
    @Transactional
    public boolean moveChore(Long taskId, int direction) {
        ChoreTask t = tasks.findById(taskId).orElseThrow();
        String homeCode = t.getHomeCode();
        renumberChores(homeCode, t.getGroupId());
        List<ChoreTask> bucket = bucketOf(homeCode, t.getGroupId());
        int i = indexOfTaskIn(bucket, taskId);
        int j = i + Integer.signum(direction);
        if (i < 0 || j < 0 || j >= bucket.size()) {
            return false;
        }
        ChoreTask a = bucket.get(i);
        ChoreTask b = bucket.get(j);
        int tmp = a.getSortOrder();
        a.setSortOrder(b.getSortOrder());
        b.setSortOrder(tmp);
        tasks.saveAll(List.of(a, b));
        homeState.bump(homeCode);
        return true;
    }

    /** Moves a chore into another group (null = ungrouped), appending it to that group's end. */
    @Transactional
    public void setChoreGroup(Long taskId, Long groupId) {
        ChoreTask t = tasks.findById(taskId).orElseThrow();
        applyGroup(t, groupId);
        tasks.save(t);
        homeState.bump(t.getHomeCode());
    }

    /**
     * Puts a chore in a group and appends it to that group's end, without saving or bumping —
     * the shared half of {@link #setChoreGroup} and the group field of {@code updateTask}, so a
     * dialog save stays one write and one board redraw for every connected phone.
     *
     * <p>A groupId belonging to another home is ignored rather than stored: the picker can only
     * offer this home's groups, so it means a tampered request, and silently ungrouping is a
     * smaller surprise than throwing at the family's admin.
     */
    private void applyGroup(ChoreTask t, Long groupId) {
        Long target = null;
        if (groupId != null) {
            target = groups.findById(groupId)
                    .filter(g -> g.getHomeCode().equals(t.getHomeCode()))
                    .map(ChoreGroup::getId).orElse(null);
        }
        if (java.util.Objects.equals(target, t.getGroupId())) {
            return; // unchanged — leave the position alone
        }
        t.setGroupId(target);
        t.setSortOrder(bucketOf(t.getHomeCode(), target).size()); // append to the new bucket
    }

    /** The chores in one bucket, in board order. A null groupId means the ungrouped bucket, and
     *  a groupId whose group is gone falls in there too — see {@link #tasksOf}. */
    private List<ChoreTask> bucketOf(String homeCode, Long groupId) {
        Set<Long> live = new java.util.HashSet<>();
        groupsOf(homeCode).forEach(g -> live.add(g.getId()));
        List<ChoreTask> out = new ArrayList<>();
        for (ChoreTask t : tasks.findByHomeCodeOrderBySortOrderAscCreatedAtAscIdAsc(homeCode)) {
            Long effective = (t.getGroupId() != null && live.contains(t.getGroupId()))
                    ? t.getGroupId() : null;
            if (java.util.Objects.equals(effective, groupId)) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * Rewrites one bucket's positions to a dense 0..n-1 in its current display order.
     *
     * <p>Dense rather than gapped-by-tens. Gaps buy nothing at family scale — a dozen chores means
     * rewriting a bucket is a handful of updates — while a gap scheme needs a "ran out of room,
     * compact everything" path that in a home this size would never run, and therefore never be
     * right. Dense also makes a move a plain swap, makes the invariant assertable (0,1,2,…), and
     * quietly normalizes the legacy rows: every chore predating the column sits at 0, and the
     * first move in a bucket converges it on the order {@code createdAt} was already showing.
     *
     * <p>Deliberately <em>not</em> called from {@code deleteTask}, {@code addTask} or the vacated
     * side of a group move. A gap changes no visible order, and the next move renumbers anyway.
     */
    private void renumberChores(String homeCode, Long groupId) {
        List<ChoreTask> bucket = bucketOf(homeCode, groupId);
        for (int i = 0; i < bucket.size(); i++) {
            if (bucket.get(i).getSortOrder() != i) {
                bucket.get(i).setSortOrder(i);
                tasks.save(bucket.get(i));
            }
        }
    }

    private void renumberGroups(String homeCode) {
        List<ChoreGroup> all = groupsOf(homeCode);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getSortOrder() != i) {
                all.get(i).setSortOrder(i);
                groups.save(all.get(i));
            }
        }
    }

    private static void swapOrder(ChoreGroup a, ChoreGroup b) {
        int tmp = a.getSortOrder();
        a.setSortOrder(b.getSortOrder());
        b.setSortOrder(tmp);
    }

    private static int indexOfGroup(List<ChoreGroup> list, Long groupId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(groupId)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfTaskIn(List<ChoreTask> list, Long taskId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(taskId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The home's chores in creation order — the rotation's index space, and nothing else.
     *
     * <p>Deliberately immune to every reordering gesture in the Admin tab. §4.5 assigns member
     * <em>m</em> the chore at index {@code (m + epochDay) mod n}; if that index space followed the
     * board, an admin nudging a card up would reassign the whole family's day, and under enforced
     * rotation would lock someone out of the chore they were about to do with no message that
     * explains why. Arranging the board is a layout preference and must not have a fairness effect.
     */
    public List<ChoreTask> tasksInRotationOrder(String homeCode) {
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
        // A second, differently ordered view of the same chores, on purpose: indexing the board
        // order here would make an admin's ↑/↓ reshuffle who is assigned what today.
        List<ChoreTask> rotationOrder = rotating ? tasksInRotationOrder(homeCode) : List.of();
        Long myAssignedChore = rotating
                ? assignedChoreId(rotationOrder, memberList, memberId, today) : null;

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

            boolean due = isDue(task, recent);
            LocalDate nextDue = nextDueDate(task, recent);
            boolean inHours = TimeWindows.isWithinAny(task.getAvailableWindows(), localNow);
            boolean inSeason = Seasons.isInSeason(task.getSeasons(), today);

            Long assignedMemberId = rotating
                    ? assignedMemberId(rotationOrder, memberList, task.getId(), today) : null;
            String assignedName = assignedMemberId == null ? null : memberName(assignedMemberId);

            LockReason reason = computeLock(home, task, memberId, streak, holderId,
                    bookerId, due, inHours, inSeason, rotating, myAssignedChore);

            result.add(new TaskView(task, recent.size(), streak, holderId, holderName,
                    bookerId, bookerName, bookingExpires, due, nextDue,
                    assignedMemberId, assignedName, reason != LockReason.NONE, reason));
        }
        return result;
    }

    private LockReason computeLock(Home home, ChoreTask task, Long memberId, int streak,
                                  Long holderId, Long bookerId, boolean due, boolean inHours,
                                  boolean inSeason, boolean rotating, Long myAssignedChore) {
        // Season comes first on purpose: a snow-shovelling chore in July is out of scope, not
        // "not due yet". Checked later, a winter chore on a yearly interval would badge
        // "in 200 days" — true, and useless.
        if (!inSeason) {
            return LockReason.OUT_OF_SEASON;
        }
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

    /** Date of the last time this chore was done, from an already-fetched list, or null. */
    private LocalDate lastDoneDate(List<Completion> recent) {
        return recent.isEmpty() ? null
                : recent.get(0).getDoneAt().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** The next date the chore becomes due (today if it has no interval or was never done). */
    public LocalDate nextDueDate(ChoreTask t) {
        // No interval means always due, and answering that needs no history at all — so don't
        // pay for a query to find out.
        if (t.getIntervalDays() <= 0) {
            return LocalDate.now();
        }
        return nextDueDate(t, activeCompletions(t.getId()));
    }

    public boolean isDue(ChoreTask t) {
        return !LocalDate.now().isBefore(nextDueDate(t));
    }

    /*
     * Both due-date questions are also asked from taskViews, which has already loaded the task's
     * completions for the fairness streak. These overloads take that list so a board render costs
     * one query per chore instead of three: measured 3 statements per interval chore before, 1
     * after — a 25-chore weekly board went from 78 statements to 28 (see BoardRenderCostTest).
     * It matters because taskViews re-runs for every connected device on every HomeState bump.
     */

    private LocalDate nextDueDate(ChoreTask t, List<Completion> recent) {
        if (t.getIntervalDays() <= 0) {
            return LocalDate.now();
        }
        LocalDate last = lastDoneDate(recent);
        return last == null ? LocalDate.now() : last.plusDays(t.getIntervalDays());
    }

    private boolean isDue(ChoreTask t, List<Completion> recent) {
        return !LocalDate.now().isBefore(nextDueDate(t, recent));
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

    /**
     * Clears bookings whose hold has lapsed, and returns how many were freed.
     *
     * <p>Expiry itself is derived at read time by {@link #effectiveBookerId} — a lapsed
     * booking never blocks anyone even before this runs. What the sweep adds is
     * <em>visibility</em>: boards only re-render when {@link HomeState} is bumped, and a
     * booking quietly crossing its expiry instant is not a mutation, so an open board would
     * otherwise keep showing "🔖 Alex" and a locked card until some unrelated change or a
     * page reload. Writing the release back also stops dead bookedBy/bookedAt columns from
     * accumulating in the database and riding along through backups.
     *
     * <p>Both paths share {@link #bookingExpiry}, so the write can never disagree with the
     * read-time derivation.
     */
    @Scheduled(fixedDelayString = "${homechores.booking.sweep-ms:60000}",
               initialDelayString = "${homechores.booking.sweep-ms:60000}")
    @Transactional
    public int releaseExpiredBookings() {
        Instant now = Instant.now();
        List<ChoreTask> released = new ArrayList<>();
        Set<String> affectedHomes = new LinkedHashSet<>();

        for (ChoreTask task : tasks.findByBookedByMemberIdIsNotNull()) {
            Home home = homes.findById(task.getHomeCode()).orElse(null);
            // An orphaned task (its home is gone) can never be un-booked by anyone: free it.
            if (home != null) {
                Instant expiry = bookingExpiry(task, home);
                if (expiry != null && now.isBefore(expiry)) {
                    continue; // still a live hold
                }
            }
            task.setBookedByMemberId(null);
            task.setBookedAt(null);
            released.add(task);
            affectedHomes.add(task.getHomeCode());
        }

        if (released.isEmpty()) {
            return 0;
        }
        tasks.saveAll(released);
        // One bump per home, not per task — each bump re-renders every board in that home.
        affectedHomes.forEach(homeState::bump);
        log.info("Released {} expired booking(s) across {} home(s)",
                released.size(), affectedHomes.size());
        return released.size();
    }

    // ---- Rotation division --------------------------------------------------

    /**
     * The chore assigned to this member today under rotating division, or null.
     *
     * <p>Loads the rotation order itself, for callers that do not already hold it. Anything that
     * has the lists in hand should call {@link #assignedChoreId} directly rather than paying for
     * two more queries — see {@code taskViews}.
     */
    public Long rotationAssignedChoreId(Home home, Long memberId, LocalDate date) {
        return assignedChoreId(tasksInRotationOrder(home.getCode()),
                membersOf(home.getCode()), memberId, date);
    }

    /**
     * The rotation itself, over lists the caller already holds. Both entry points funnel through
     * here so the board's "⭐ Your turn" badge and the enforcement gate in {@code complete} cannot
     * drift apart — they were two separate index computations before, over two separate reads.
     *
     * <p>{@code rotationOrder} must be {@link #tasksInRotationOrder}, never {@link #tasksOf}.
     */
    private static Long assignedChoreId(List<ChoreTask> rotationOrder, List<Member> memberList,
                                        Long memberId, LocalDate date) {
        if (rotationOrder.isEmpty()) {
            return null;
        }
        int mi = indexOf(memberList, memberId);
        if (mi < 0) {
            return null;
        }
        int c = rotationOrder.size();
        int ci = (int) (((mi + date.toEpochDay()) % c + c) % c);
        return rotationOrder.get(ci).getId();
    }

    /** The member assigned to this chore today (inverse of the rotation), or null. */
    private static Long assignedMemberId(List<ChoreTask> rotationOrder, List<Member> memberList,
                                         Long taskId, LocalDate date) {
        if (rotationOrder.isEmpty() || memberList.isEmpty()) {
            return null;
        }
        int c = rotationOrder.size();
        int ci = rotationIndexOf(rotationOrder, taskId);
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

    /** A chore's position in the rotation order. The only place a chore becomes an integer —
     *  named for the list it means, so nobody hands it the board order by accident. */
    private static int rotationIndexOf(List<ChoreTask> list, Long taskId) {
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

        // 0) Season: does this chore apply at all right now? Uses the server date rather than
        // the member's zone, on purpose — hours are member-local (see the windows check below),
        // but a season is a month-level notion and a travelling member must not slip into a
        // different one.
        if (!Seasons.isInSeason(task.getSeasons(), LocalDate.now())) {
            return CompleteOutcome.blocked(LockReason.OUT_OF_SEASON, task, member);
        }
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
        // ...and any nudge this member had asked for about it. A notification ninety minutes
        // later naming a chore they already did is the one way that feature can be worse than
        // nothing. Only their own: a reminder belongs to the member who armed it, and somebody
        // else's is retired by the sweep's own due-check instead of a fan-out write here.
        choreReminders.deleteByMemberIdAndTaskId(memberId, taskId);

        boolean approval = home.isRequireApproval();
        CompletionStatus status = approval ? CompletionStatus.PENDING : CompletionStatus.APPROVED;

        boolean alreadyDoneThisChore = completions.existsByMemberIdAndTaskIdAndStatus(
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
        return CompleteOutcome.done(task, member, memberTotal, !alreadyDoneThisChore,
                milestoneFor(memberTotal), rotating ? 0 : streak + 1, saved.getId(), award,
                doneTodayCount(memberId));
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
        // An admin logging it for someone IS that member doing it, so their nudge goes too.
        choreReminders.deleteByMemberIdAndTaskId(memberId, taskId);

        boolean alreadyDoneThisChore = completions.existsByMemberIdAndTaskIdAndStatus(
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
        return CompleteOutcome.done(task, member, memberTotal, !alreadyDoneThisChore,
                milestoneFor(memberTotal), 0, saved.getId(), award,
                doneTodayCount(memberId));
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

        boolean alreadyDoneThisChore = task != null && completions.existsByMemberIdAndTaskIdAndStatus(
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
        return CompleteOutcome.done(task, member, memberTotal, task != null && !alreadyDoneThisChore,
                milestoneFor(memberTotal), 0, c.getId(), award,
                doneTodayCount(c.getMemberId()));
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

    /**
     * Recent completions in a home, newest first — the admin's correction list.
     *
     * <p>The limit goes to the database rather than to a stream over the result: this list
     * shows fifteen rows, and reading a family's entire chore history to throw all but
     * fifteen of it away is a cost that grows for as long as they use the app.
     */
    public List<Completion> recentCompletions(String homeCode, int limit) {
        return completions.findByHomeCodeOrderByDoneAtDesc(homeCode,
                org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit)));
    }

    /** One row of the board's "Done today" list, prefetched so rendering is O(1) per row. */
    public record DoneToday(Completion completion, String memberName, String text) {
    }

    /**
     * Today's completions in a home (server date, same "today" as the daily ring), newest
     * first. APPROVED and PENDING are both listed — in a require-approval home the list
     * would otherwise stay empty all day; the UI marks pending rows ⏳. REJECTED excluded.
     */
    public List<DoneToday> doneTodayList(String homeCode) {
        String norm = normalizeCode(homeCode);
        ZoneId zone = ZoneId.systemDefault();
        Instant from = LocalDate.now().atStartOfDay(zone).toInstant();
        Instant to = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant();
        List<Completion> rows = completions
                .findByHomeCodeAndStatusInAndDoneAtGreaterThanEqualAndDoneAtLessThanOrderByDoneAtDesc(
                        norm, List.of(CompletionStatus.APPROVED, CompletionStatus.PENDING),
                        from, to);
        var taskById = new java.util.HashMap<Long, ChoreTask>();
        tasksOf(norm).forEach(t -> taskById.put(t.getId(), t));
        var nameById = new java.util.HashMap<Long, String>();
        membersOf(norm).forEach(m -> nameById.put(m.getId(), m.getName()));
        return rows.stream()
                .map(c -> new DoneToday(c,
                        nameById.getOrDefault(c.getMemberId(), "?"),
                        describe(c, c.getTaskId() == null ? null : taskById.get(c.getTaskId()))))
                .toList();
    }

    // ---- Counts & daily target ---------------------------------------------

    /** How many members the home has — one count query, cheap enough for every render. */
    public long memberCount(String homeCode) {
        return members.countByHomeCode(normalizeCode(homeCode));
    }

    /** Number of APPROVED completions by this member (leaderboard count). */
    public long completionCount(Long memberId) {
        return completions.countByMemberIdAndStatus(memberId, CompletionStatus.APPROVED);
    }

    /**
     * APPROVED completions by this member with doneAt on the given local date.
     *
     * <p>Asked once per board render for the daily ring, so it asks the database for that
     * one day rather than loading every chore the member has ever done and filtering by
     * date in memory — the same shape {@link #doneTodayCount} already used on the
     * completion hot path.
     */
    public long doneOn(Long memberId, LocalDate date) {
        ZoneId zone = ZoneId.systemDefault();
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        return completions.findByMemberIdAndStatusAndDoneAtGreaterThanEqualAndDoneAtLessThan(
                memberId, CompletionStatus.APPROVED, from, to).size();
    }

    public long doneToday(Long memberId) {
        return doneOn(memberId, LocalDate.now());
    }

    /** Same count via one indexed query — for the completion hot path (celebration tiers). */
    private long doneTodayCount(Long memberId) {
        Instant midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        return completions.countByMemberIdAndStatusAndDoneAtGreaterThanEqual(
                memberId, CompletionStatus.APPROVED, midnight);
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

    /** How a completion reads in a list: the chore's emoji+name, or the helper's own words. */
    public static String describe(Completion c, ChoreTask task) {
        if (c.isOtherHelp()) {
            return "🙋 " + c.getNote();
        }
        return task == null ? "?" : task.getEmoji() + " " + task.getName();
    }

    /**
     * A group's emoji, where blank stays blank — unlike {@link #cleanEmoji}, which substitutes ✅.
     * A chore is a tappable card and needs a glyph on it; a group is a line of text above a grid,
     * and inventing a checkmark for an admin who left the field empty would put a tick over
     * chores nobody has done.
     */
    private static String groupEmoji(String emoji) {
        return emoji == null || emoji.isBlank() ? null : InputLimits.clip(emoji, InputLimits.EMOJI);
    }

    private static String cleanEmoji(String emoji) {
        if (emoji == null || emoji.isBlank()) {
            return "✅";
        }
        // The UI caps the field at 4 UTF-16 units, but that cap lives in the browser; 16
        // still fits any single emoji grapheme while shutting out smuggled free text.
        return InputLimits.clip(emoji, InputLimits.EMOJI);
    }

    /** Interval / tier day counts: never negative, never past a year. */
    static int clampDays(int days) {
        return Math.clamp(days, 0, InputLimits.MAX_DAYS);
    }

    /** Credit values: never negative, never absurd. */
    static int clampCredits(int credits) {
        return Math.clamp(credits, 0, InputLimits.MAX_CREDITS);
    }

    /**
     * Bounds a canonical availability string: at most {@link InputLimits#TIME_WINDOW_COUNT}
     * windows survive (enough windows would otherwise normalize past the column width).
     */
    static String clipWindows(String availableWindows) {
        if (availableWindows == null || availableWindows.isBlank()) {
            return availableWindows;
        }
        String[] parts = availableWindows.split(",");
        if (parts.length <= InputLimits.TIME_WINDOW_COUNT) {
            return availableWindows;
        }
        return String.join(",",
                java.util.Arrays.copyOf(parts, InputLimits.TIME_WINDOW_COUNT));
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
    public enum LockReason {
        NONE, STREAK, BOOKED, NOT_DUE, NOT_ASSIGNED, OUTSIDE_HOURS, OUT_OF_SEASON
    }

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
            int spreeCredits,
            /** The member's APPROVED completions today (server date) including this one;
             *  0 = not applicable (blocked, or pending — not counted until approved). */
            long doneTodayAfter) {

        static CompleteOutcome blocked(LockReason reason, ChoreTask task, Member member) {
            return new CompleteOutcome(false, false, reason, task, member, 0, false, null, 0, null,
                    0, null, 0, 0);
        }

        static CompleteOutcome pending(ChoreTask task, Member member, Long completionId) {
            return new CompleteOutcome(true, true, LockReason.NONE, task, member, 0, false, null, 0,
                    completionId, 0, null, 0, 0);
        }

        static CompleteOutcome done(ChoreTask task, Member member, long total, boolean newChore,
                                    Integer milestone, int newStreak, Long completionId,
                                    CreditService.Award award, long doneTodayAfter) {
            return new CompleteOutcome(true, false, LockReason.NONE, task, member, total, newChore,
                    milestone, newStreak, completionId,
                    award.choreCredits(), award.spreeDays(), award.spreeCredits(), doneTodayAfter);
        }
    }
}
