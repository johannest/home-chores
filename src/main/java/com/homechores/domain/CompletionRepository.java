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

    List<Completion> findByHomeCodeAndStatus(String homeCode, CompletionStatus status);

    List<Completion> findByMemberIdAndStatus(Long memberId, CompletionStatus status);

    @Transactional
    void deleteByHomeCode(String homeCode);

    @Transactional
    void deleteByTaskId(Long taskId);

    @Transactional
    void deleteByMemberId(Long memberId);
}
