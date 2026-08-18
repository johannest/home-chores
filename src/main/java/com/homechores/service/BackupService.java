package com.homechores.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

    public static final int VERSION = 1;

    private final HomeRepository homes;
    private final MemberRepository members;
    private final ChoreTaskRepository tasks;
    private final CompletionRepository completions;
    private final CreditEntryRepository creditEntries;
    private final SpreeTierRepository spreeTiers;
    private final RejoinRequestRepository rejoins;
    private final HomeState homeState;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public BackupService(HomeRepository homes, MemberRepository members,
                        ChoreTaskRepository tasks, CompletionRepository completions,
                        CreditEntryRepository creditEntries, SpreeTierRepository spreeTiers,
                        RejoinRequestRepository rejoins, HomeState homeState) {
        this.homes = homes;
        this.members = members;
        this.tasks = tasks;
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
                home.isConfirmCompletion(), home.isAllowOtherHelp(), home.getCreatedAt());
        for (Member m : members.findByHomeCodeOrderByJoinedAtAsc(homeCode)) {
            b.members.add(new MemberDto(m.getId(), m.getName(), m.getColor(), m.isAdmin(), m.getJoinedAt()));
        }
        for (ChoreTask t : tasks.findByHomeCodeOrderByCreatedAtAsc(homeCode)) {
            b.tasks.add(new TaskDto(t.getId(), t.getName(), t.getEmoji(), t.getIntervalDays(),
                    t.getCreditValue(), t.getAvailableWindows(), t.getCreatedAt()));
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

    /** Replaces the backup's home with the file contents. Returns a short summary. */
    @Transactional
    public RestoreResult restore(byte[] json) {
        Backup b;
        try {
            b = mapper.readValue(json, Backup.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Not a valid backup file: " + e.getMessage());
        }
        if (b == null || b.home == null || b.home.code == null || b.home.code.isBlank()) {
            throw new IllegalArgumentException("Backup file is missing home data.");
        }
        String code = b.home.code.trim().toUpperCase();

        // Upsert the home.
        Home home = homes.findById(code).orElseGet(() -> new Home(code, b.home.name, b.home.adminPin));
        home.setName(b.home.name);
        home.setAdminPin(b.home.adminPin);
        home.setRequireApproval(b.home.requireApproval);
        home.setDailyTargetPerMember(clampTarget(b.home.dailyTargetPerMember));
        home.setDivisionStyle(b.home.divisionStyle == null ? DivisionStyle.DEFAULT : b.home.divisionStyle);
        home.setRotationEnforced(b.home.rotationEnforced);
        home.setApproveRejoin(b.home.approveRejoin == null || b.home.approveRejoin);
        home.setConfirmCompletion(b.home.confirmCompletion == null || b.home.confirmCompletion);
        home.setAllowOtherHelp(b.home.allowOtherHelp == null || b.home.allowOtherHelp);
        if (b.home.bookingTimeoutHours > 0) {
            home.setBookingTimeoutHours(b.home.bookingTimeoutHours);
        }
        if (b.home.createdAt != null) {
            home.setCreatedAt(b.home.createdAt);
        }
        homes.save(home);

        // Wipe current data for this home. Rejoin requests go too: their member ids are
        // about to be remapped, so any survivor would point at the wrong person.
        rejoins.deleteByHomeCode(code);
        completions.deleteByHomeCode(code);
        creditEntries.deleteByHomeCode(code);
        spreeTiers.deleteByHomeCode(code);
        tasks.deleteByHomeCode(code);
        members.deleteByHomeCode(code);

        // Recreate members and tasks, remapping their (identity-generated) ids.
        Map<Long, Long> memberIdMap = new HashMap<>();
        for (MemberDto m : b.members) {
            Member entity = new Member(code, m.name, m.color, m.admin);
            if (m.joinedAt != null) {
                entity.setJoinedAt(m.joinedAt);
            }
            Member saved = members.save(entity);
            memberIdMap.put(m.id, saved.getId());
        }
        Map<Long, Long> taskIdMap = new HashMap<>();
        for (TaskDto t : b.tasks) {
            ChoreTask entity = new ChoreTask(code, t.name, t.emoji);
            entity.setIntervalDays(Math.max(0, t.intervalDays));
            entity.setCreditValue(Math.max(0, t.creditValue));
            entity.setAvailableWindows(t.availableWindows);
            if (t.createdAt != null) {
                entity.setCreatedAt(t.createdAt);
            }
            ChoreTask saved = tasks.save(entity);
            taskIdMap.put(t.id, saved.getId());
        }
        for (SpreeTierDto st : b.spreeTiers) {
            spreeTiers.save(new SpreeTier(code, st.days, st.credits));
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
            entity.setNote(c.note);
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
            CreditEntry entity = new CreditEntry(code, newMember, cr.amount,
                    cr.type == null ? CreditType.EARNED : cr.type, cr.reason, cr.spreeTierDays,
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

    public record RestoreResult(String homeCode, int members, int tasks, int completions) {
    }

    // ---- JSON shapes (public, mutable for Jackson) --------------------------

    public static class Backup {
        public int version = VERSION;
        public HomeDto home;
        public List<MemberDto> members = new ArrayList<>();
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
                          Boolean approveRejoin, Boolean confirmCompletion,
                          Boolean allowOtherHelp, Instant createdAt) {
    }

    public record MemberDto(Long id, String name, String color, boolean admin, Instant joinedAt) {
    }

    public record TaskDto(Long id, String name, String emoji, int intervalDays, int creditValue,
                          String availableWindows, Instant createdAt) {
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
