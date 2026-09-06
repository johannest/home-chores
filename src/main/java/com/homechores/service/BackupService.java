package com.homechores.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.homechores.domain.ChoreGroup;
import com.homechores.domain.ChoreReminderRepository;
import com.homechores.domain.ChoreGroupRepository;
import com.homechores.domain.ChoreTask;
import com.homechores.domain.ChoreTaskRepository;
import com.homechores.domain.Completion;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.CompletionStatus;
import com.homechores.domain.CreditEntry;
import com.homechores.domain.CreditEntryRepository;
import com.homechores.domain.CreditType;
import com.homechores.domain.DivisionStyle;
import com.homechores.domain.Feedback;
import com.homechores.domain.Home;
import com.homechores.domain.HomeRepository;
import com.homechores.domain.InputLimits;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import com.homechores.domain.RejoinRequestRepository;
import com.homechores.domain.SpreeTier;
import com.homechores.domain.SpreeTierRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exports a single home ("the family DB") to JSON and restores it back. */
@Service
public class BackupService {

    /**
     * Informational. Nothing on the restore path branches on it: unknown keys are ignored and
     * missing ones take their defaults, so a purely additive change (chore groups, seasons,
     * avatars) is compatible in both directions and does not bump this. Bump it only when a
     * reader must actually behave differently.
     */
    public static final int VERSION = 1;

    private final HomeRepository homes;
    private final MemberRepository members;
    private final ChoreTaskRepository tasks;
    private final ChoreGroupRepository groups;
    private final ChoreReminderRepository choreReminders;
    private final CompletionRepository completions;
    private final CreditEntryRepository creditEntries;
    private final SpreeTierRepository spreeTiers;
    private final RejoinRequestRepository rejoins;
    private final HomeState homeState;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // A file written by a newer build may carry settings this one has never heard
            // of. Rejecting it outright would make a family's own backup unrestorable after
            // a rollback; skipping the unknown key restores everything this version does
            // understand. Malformed JSON still fails loudly (see restore).
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public BackupService(HomeRepository homes, MemberRepository members,
                        ChoreTaskRepository tasks, ChoreGroupRepository groups,
                        ChoreReminderRepository choreReminders,
                        CompletionRepository completions,
                        CreditEntryRepository creditEntries, SpreeTierRepository spreeTiers,
                        RejoinRequestRepository rejoins, HomeState homeState) {
        this.homes = homes;
        this.members = members;
        this.tasks = tasks;
        this.groups = groups;
        this.choreReminders = choreReminders;
        this.completions = completions;
        this.creditEntries = creditEntries;
        this.spreeTiers = spreeTiers;
        this.rejoins = rejoins;
        this.homeState = homeState;
    }

    // ---- Export -------------------------------------------------------------

    public String export(String homeCode) {
        Home home = homes.findById(homeCode).orElseThrow();
        Backup b = new Backup();
        b.version = VERSION;
        b.home = new HomeDto(home.getCode(), home.getName(), home.getAdminPin(),
                home.isRequireApproval(), home.getDailyTargetPerMember(), home.getDivisionStyle(),
                home.isRotationEnforced(), home.getBookingTimeoutHours(), home.isApproveRejoin(),
                home.isApproveJoin(), home.isConfirmCompletion(), home.isAllowOtherHelp(),
                home.getCreatedAt());
        for (Member m : members.findByHomeCodeOrderByJoinedAtAsc(homeCode)) {
            b.members.add(new MemberDto(m.getId(), m.getName(), m.getColor(), m.isAdmin(),
                    m.getJoinedAt(), m.getAvatar()));
        }
        for (ChoreGroup g : groups.findByHomeCodeOrderBySortOrderAscIdAsc(homeCode)) {
            b.groups.add(new GroupDto(g.getId(), g.getName(), g.getEmoji(), g.getSortOrder()));
        }
        // Creation order, not board order: the file's row order never was the board's, and
        // sortOrder now carries the arrangement explicitly.
        for (ChoreTask t : tasks.findByHomeCodeOrderByCreatedAtAsc(homeCode)) {
            b.tasks.add(new TaskDto(t.getId(), t.getName(), t.getEmoji(), t.getIntervalDays(),
                    t.getCreditValue(), t.getAvailableWindows(), t.getSeasons(), t.getCreatedAt(),
                    t.getGroupId(), t.getSortOrder()));
        }
        for (Completion c : completions.findByHomeCode(homeCode)) {
            b.completions.add(new CompletionDto(c.getId(), c.getTaskId(), c.getMemberId(),
                    c.getDoneAt(), c.getStatus(), c.getFeedback(),
                    c.getReviewedByMemberId(), c.getReviewedAt(), c.getNote()));
        }
        for (SpreeTier t : spreeTiers.findByHomeCodeOrderByDaysAsc(homeCode)) {
            b.spreeTiers.add(new SpreeTierDto(t.getDays(), t.getCredits()));
        }
        for (CreditEntry e : creditEntries.findByHomeCodeOrderByCreatedAtDesc(homeCode)) {
            b.credits.add(new CreditDto(e.getMemberId(), e.getAmount(), e.getType(),
                    e.getReason(), e.getSpreeTierDays(), e.getCompletionId(), e.getCreatedAt()));
        }
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(b);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize backup", e);
        }
    }

    // ---- Restore ------------------------------------------------------------

    /**
     * Replaces {@code ownHomeCode}'s data with the uploaded file. Returns a short summary.
     *
     * <p>{@code ownHomeCode} is the home the acting admin is signed into, and the file's
     * own {@code home.code} must match it. Without that check an admin of one home could
     * upload a backup naming a different family's code and overwrite (or hijack) that
     * home — restore is a full wipe-and-replace, so it must never touch another home.
     */
    @Transactional
    public RestoreResult restore(byte[] json, String ownHomeCode) {
        Backup b = parse(json);
        String code = b.home.code.trim().toUpperCase();
        String own = ownHomeCode == null ? "" : ownHomeCode.trim().toUpperCase();
        if (!code.equals(own)) {
            throw new IllegalArgumentException("This backup is for a different home (" + code
                    + "). You can only restore your own home's backup.");
        }
        return apply(b, code);
    }

    /**
     * Restores whatever home the file names, without requiring an admin to be signed into
     * it — the operator's undo, reachable only from the offline maintenance CLI.
     *
     * <p>The in-app path above deliberately refuses a file naming another home, because
     * there the caller is one family's admin and restore is a full wipe-and-replace. That
     * check cannot also serve the case it was never about: a home the retention sweep has
     * already deleted has no admin left to sign in as, so the very backup written to be
     * its undo would be unrestorable. §4.11.2 and the privacy notice both promise that
     * undo, and this is what keeps the promise. Its safety comes from where it lives — a
     * process the operator starts on the host, with the service stopped — not from a code
     * comparison.
     *
     * @return the summary, plus whether an existing home was overwritten
     */
    @Transactional
    public RestoreResult restoreAnyHome(byte[] json) {
        Backup b = parse(json);
        return apply(b, b.home.code.trim().toUpperCase());
    }

    /** Whether a home with the code this file names is currently in the database. */
    public String homeCodeIn(byte[] json) {
        return parse(json).home.code.trim().toUpperCase();
    }

    private Backup parse(byte[] json) {
        Backup b;
        try {
            b = mapper.readValue(json, Backup.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Not a valid backup file: " + e.getMessage());
        }
        if (b == null || b.home == null || b.home.code == null || b.home.code.isBlank()) {
            throw new IllegalArgumentException("Backup file is missing home data.");
        }
        return b;
    }

    private RestoreResult apply(Backup b, String code) {
        // Upsert the home. Every string in the file is user-supplied (a backup can be
        // hand-edited before upload), so lengths and formats are re-asserted here just
        // like at the interactive entry points.
        Home home = homes.findById(code).orElseGet(() -> new Home(code, b.home.name, "0000"));
        home.setName(InputLimits.clip(b.home.name, InputLimits.HOME_NAME));
        if (b.home.adminPin != null && b.home.adminPin.matches("\\d{4}")) {
            home.setAdminPin(b.home.adminPin);
        } // else: keep the home's current PIN rather than storing arbitrary text
        home.setRequireApproval(b.home.requireApproval);
        home.setDailyTargetPerMember(clampTarget(b.home.dailyTargetPerMember));
        home.setDivisionStyle(b.home.divisionStyle == null ? DivisionStyle.DEFAULT : b.home.divisionStyle);
        home.setRotationEnforced(b.home.rotationEnforced);
        home.setApproveRejoin(b.home.approveRejoin == null || b.home.approveRejoin);
        home.setApproveJoin(b.home.approveJoin == null || b.home.approveJoin);
        home.setConfirmCompletion(b.home.confirmCompletion == null || b.home.confirmCompletion);
        home.setAllowOtherHelp(b.home.allowOtherHelp == null || b.home.allowOtherHelp);
        if (b.home.bookingTimeoutHours > 0) {
            home.setBookingTimeoutHours(b.home.bookingTimeoutHours);
        }
        if (b.home.createdAt != null) {
            home.setCreatedAt(b.home.createdAt);
        }
        // Restoring is someone using this home, right now. Without this the home would carry
        // only the backup's (possibly months-old) createdAt as its activity, and a home
        // brought back from the retention export would be swept away again the same night —
        // the undo undone. See HomeCleanupService and Home.lastActiveOrCreated.
        home.setLastActiveAt(Instant.now());
        homes.save(home);

        // Wipe current data for this home. Rejoin requests go too: their member ids are
        // about to be remapped, so any survivor would point at the wrong person.
        rejoins.deleteByHomeCode(code);
        // Pending chore reminders go with them, for the reason the rejoin requests do: restore
        // remaps every member and task id, so a survivor would nudge the wrong person about the
        // wrong chore. They are excluded from the export for the same reason PushSubscription is
        // — a pending, device-targeted notification is not family history.
        choreReminders.deleteByHomeCode(code);
        completions.deleteByHomeCode(code);
        creditEntries.deleteByHomeCode(code);
        spreeTiers.deleteByHomeCode(code);
        tasks.deleteByHomeCode(code);
        groups.deleteByHomeCode(code);
        members.deleteByHomeCode(code);

        // Recreate members and tasks, remapping their (identity-generated) ids.
        Map<Long, Long> memberIdMap = new HashMap<>();
        int memberIndex = 0;
        for (MemberDto m : b.members) {
            String name = InputLimits.clip(m.name, InputLimits.MEMBER_NAME);
            Member entity = new Member(code,
                    name == null || name.isBlank() ? "Member" : name,
                    safeColor(m.color, memberIndex++), m.admin);
            // Whitelist, same reasoning as safeColor: a backup is hand-editable.
            entity.setAvatar(com.homechores.domain.Avatars.sanitize(m.avatar));
            if (m.joinedAt != null) {
                entity.setJoinedAt(m.joinedAt);
            }
            Member saved = members.save(entity);
            memberIdMap.put(m.id, saved.getId());
        }
        // Groups first: tasks point at them, and restore never trusts the ids in the file.
        Map<Long, Long> groupIdMap = new HashMap<>();
        for (GroupDto g : b.groups) {
            ChoreGroup entity = new ChoreGroup(code,
                    InputLimits.clip(g.name, InputLimits.GROUP_NAME),
                    InputLimits.clip(g.emoji, InputLimits.EMOJI));
            entity.setSortOrder(Math.max(0, g.sortOrder));
            groupIdMap.put(g.id, groups.save(entity).getId());
        }

        Map<Long, Long> taskIdMap = new HashMap<>();
        for (TaskDto t : b.tasks) {
            ChoreTask entity = new ChoreTask(code,
                    InputLimits.clip(t.name, InputLimits.TASK_NAME),
                    InputLimits.clip(t.emoji, InputLimits.EMOJI));
            entity.setIntervalDays(ChoreService.clampDays(t.intervalDays));
            entity.setCreditValue(ChoreService.clampCredits(t.creditValue));
            entity.setAvailableWindows(InputLimits.clip(
                    ChoreService.clipWindows(t.availableWindows), InputLimits.TIME_WINDOWS));
            // Content stored raw: a backup written before seasons existed has no key, which
            // deserializes to null — and null already means "all year round". Garbage is
            // neutralised on read by Seasons.isInSeason failing open; only the length is capped.
            entity.setSeasons(InputLimits.clip(t.seasons, 64));
            if (t.createdAt != null) {
                entity.setCreatedAt(t.createdAt);
            }
            // A groupId that doesn't resolve makes the chore ungrouped rather than dropping it —
            // deliberately the opposite of the completion rule below. A completion pointing at a
            // vanished chore has no meaning left; a chore that lost its label is still a chore the
            // family does. A backup is hand-editable, so both cases are reachable.
            entity.setGroupId(t.groupId == null ? null : groupIdMap.get(t.groupId));
            entity.setSortOrder(Math.max(0, t.sortOrder));
            ChoreTask saved = tasks.save(entity);
            taskIdMap.put(t.id, saved.getId());
        }
        for (SpreeTierDto st : b.spreeTiers) {
            if (st.days > 0 && st.credits > 0) {
                spreeTiers.save(new SpreeTier(code,
                        ChoreService.clampDays(st.days), ChoreService.clampCredits(st.credits)));
            }
        }
        int restoredCompletions = 0;
        Map<Long, Long> completionIdMap = new HashMap<>();
        for (CompletionDto c : b.completions) {
            // A null taskId is an "other help" entry, not an orphan: it never had a chore,
            // and its own note is what it says. Only a task that no longer resolves is one.
            Long newTask = c.taskId == null ? null : taskIdMap.get(c.taskId);
            Long newMember = memberIdMap.get(c.memberId);
            if (newMember == null || (c.taskId != null && newTask == null)) {
                continue; // orphaned record — skip
            }
            Completion entity = new Completion(code, newTask, newMember,
                    c.status == null ? CompletionStatus.APPROVED : c.status);
            if (c.doneAt != null) {
                entity.setDoneAt(c.doneAt);
            }
            entity.setFeedback(c.feedback);
            entity.setNote(InputLimits.clip(c.note, ChoreService.MAX_HELP_LENGTH));
            entity.setReviewedByMemberId(memberIdMap.get(c.reviewedByMemberId));
            entity.setReviewedAt(c.reviewedAt);
            Completion savedCompletion = completions.save(entity);
            completionIdMap.put(c.id, savedCompletion.getId());
            restoredCompletions++;
        }
        for (CreditDto cr : b.credits) {
            Long newMember = memberIdMap.get(cr.memberId);
            if (newMember == null) {
                continue;
            }
            CreditEntry entity = new CreditEntry(code, newMember, Math.max(0, cr.amount),
                    cr.type == null ? CreditType.EARNED : cr.type,
                    InputLimits.clip(cr.reason, InputLimits.REASON), cr.spreeTierDays,
                    completionIdMap.get(cr.completionId));
            if (cr.createdAt != null) {
                entity.setCreatedAt(cr.createdAt);
            }
            creditEntries.save(entity);
        }
        homeState.bump(code);
        return new RestoreResult(code, b.members.size(), b.tasks.size(), restoredCompletions);
    }

    private static int clampTarget(int t) {
        return Math.max(1, Math.min(3, t));
    }

    /** Restored avatar colors must look like a CSS hex color, or the member gets a fresh
     *  one from the palette — a backup is not a way to smuggle arbitrary CSS in. */
    private static String safeColor(String color, int index) {
        if (color != null && color.matches("#[0-9a-fA-F]{6}")) {
            return color;
        }
        return FALLBACK_COLORS[index % FALLBACK_COLORS.length];
    }

    private static final String[] FALLBACK_COLORS = {
        "#10b981", "#0ea5e9", "#f59e0b", "#ef4444", "#8b5cf6",
        "#ec4899", "#14b8a6", "#f97316", "#6366f1", "#84cc16"
    };

    public record RestoreResult(String homeCode, int members, int tasks, int completions) {
    }

    // ---- JSON shapes (public, mutable for Jackson) --------------------------
    //
    // Deliberately excluded from export: Member.deviceSecretHash and termsAcceptedAt
    // (server-side facts, not family data) and everything about push reminders
    // (PushSubscription rows, Member.reminderTime/zoneId/reminderLocale) — subscriptions
    // are device credentials that would dangle after restore anyway, since restore mints
    // new member ids.

    public static class Backup {
        public int version = VERSION;
        public HomeDto home;
        public List<MemberDto> members = new ArrayList<>();
        public List<GroupDto> groups = new ArrayList<>();
        public List<TaskDto> tasks = new ArrayList<>();
        public List<CompletionDto> completions = new ArrayList<>();
        public List<SpreeTierDto> spreeTiers = new ArrayList<>();
        public List<CreditDto> credits = new ArrayList<>();
    }

    /** The boxed booleans are boxed so backups written before those settings existed
     *  restore with their default (on) rather than Jackson's {@code false}. */
    public record HomeDto(String code, String name, String adminPin, boolean requireApproval,
                          int dailyTargetPerMember, DivisionStyle divisionStyle,
                          boolean rotationEnforced, int bookingTimeoutHours,
                          Boolean approveRejoin, Boolean approveJoin, Boolean confirmCompletion,
                          Boolean allowOtherHelp, Instant createdAt) {
    }

    /** {@code avatar} is absent in pre-avatar backups and deserializes to null — fine. */
    public record MemberDto(Long id, String name, String color, boolean admin, Instant joinedAt,
                            String avatar) {
    }

    /** Absent in pre-groups backups: Jackson leaves the array empty, and every task's groupId
     *  then deserializes to null — which already means "ungrouped". */
    public record GroupDto(Long id, String name, String emoji, int sortOrder) {
    }

    /** {@code groupId} is boxed because null is a real value (ungrouped). {@code sortOrder} is a
     *  primitive on purpose, unlike {@code HomeDto}'s boxed booleans: there, Jackson's {@code
     *  false} default would be the wrong answer for a missing key, whereas here its {@code 0}
     *  default means exactly the right thing — "legacy order", which createdAt then resolves. */
    public record TaskDto(Long id, String name, String emoji, int intervalDays, int creditValue,
                          String availableWindows, String seasons, Instant createdAt,
                          Long groupId, int sortOrder) {
    }

    public record SpreeTierDto(int days, int credits) {
    }

    public record CreditDto(Long memberId, int amount, CreditType type, String reason,
                            int spreeTierDays, Long completionId, Instant createdAt) {
    }

    /** {@code taskId} is null (and {@code note} set) for an "other help" entry. */
    public record CompletionDto(Long id, Long taskId, Long memberId, Instant doneAt,
                                CompletionStatus status, Feedback feedback,
                                Long reviewedByMemberId, Instant reviewedAt, String note) {
    }
}
