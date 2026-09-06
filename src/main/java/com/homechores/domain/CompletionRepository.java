package com.homechores.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface CompletionRepository extends JpaRepository<Completion, Long> {

    long countByMemberIdAndStatus(Long memberId, CompletionStatus status);

    long countByHomeCodeAndStatus(String homeCode, CompletionStatus status);

    /** Most recent completions of a given task, newest first (for the fairness streak check). */
    List<Completion> findByTaskIdOrderByDoneAtDesc(Long taskId);

    /** Whether this member has an APPROVED completion of this task (for "new chore"). */
    boolean existsByMemberIdAndTaskIdAndStatus(Long memberId, Long taskId, CompletionStatus status);

    /** Pending completions for a home, newest first (admin approval queue). */
    List<Completion> findByHomeCodeAndStatusOrderByDoneAtDesc(String homeCode, CompletionStatus status);

    List<Completion> findByHomeCode(String homeCode);

    /** Newest first — the admin's recent-activity list. */
    List<Completion> findByHomeCodeOrderByDoneAtDesc(String homeCode);

    /** A member's completions since a moment, newest first (the undo window). */
    List<Completion> findByMemberIdAndDoneAtAfterOrderByDoneAtDesc(Long memberId, java.time.Instant since);

    /** Whether the member submitted anything (any status) since a moment — a PENDING
     *  submission counts, so a reminder isn't sent while one awaits approval. */
    boolean existsByMemberIdAndDoneAtAfter(Long memberId, java.time.Instant since);

    /** A home's completions in the half-open range [from, to), newest first — the board's
     *  "Done today" list (APPROVED+PENDING) and the last-week stats (APPROVED). */
    List<Completion> findByHomeCodeAndStatusInAndDoneAtGreaterThanEqualAndDoneAtLessThanOrderByDoneAtDesc(
            String homeCode, java.util.Collection<CompletionStatus> statuses,
            java.time.Instant from, java.time.Instant to);

    /** How many of a member's completions with this status landed since a moment —
     *  the "n-th chore today" celebration tier. */
    long countByMemberIdAndStatusAndDoneAtGreaterThanEqual(Long memberId,
            CompletionStatus status, java.time.Instant since);

    /** Whether a home has any chore history at all, of any status (retention check). */
    boolean existsByHomeCode(String homeCode);

    List<Completion> findByHomeCodeAndStatus(String homeCode, CompletionStatus status);

    List<Completion> findByMemberIdAndStatus(Long memberId, CompletionStatus status);

    /** Every completion of one member, whatever its status — "my stats" needs the
     *  approved ones for counts and the non-rejected ones for the feedback split, and one
     *  query for both beats fetching the whole home and filtering it down to one person. */
    List<Completion> findByMemberId(Long memberId);

    /** A member's completions with this status on or after a moment — the daily ring's
     *  "done today", which asks about one date and must not read a whole history to
     *  answer. Half-open at the top end is the caller's job (see ChoreService.doneOn). */
    List<Completion> findByMemberIdAndStatusAndDoneAtGreaterThanEqualAndDoneAtLessThan(
            Long memberId, CompletionStatus status,
            java.time.Instant from, java.time.Instant to);

    /** The newest completions of a home — the admin's correction list, which shows a
     *  page's worth and must not load a family's whole history to find it. */
    List<Completion> findByHomeCodeOrderByDoneAtDesc(String homeCode,
            org.springframework.data.domain.Pageable pageable);

    @Transactional
    void deleteByHomeCode(String homeCode);

    @Transactional
    void deleteByTaskId(Long taskId);

    @Transactional
    void deleteByMemberId(Long memberId);
}
