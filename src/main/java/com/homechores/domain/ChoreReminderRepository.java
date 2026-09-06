package com.homechores.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ChoreReminderRepository extends JpaRepository<ChoreReminder, Long> {

    /** The sweep's working set. Bounded in the query rather than in the loop: the sweep shares
     *  the single scheduler thread, so it must not be able to load a large list even in the
     *  pathological case. Anything left over is picked up next minute. */
    List<ChoreReminder> findTop50ByDueAtLessThanEqualOrderByDueAtAsc(Instant now);

    Optional<ChoreReminder> findByMemberIdAndTaskId(Long memberId, Long taskId);

    /** Everything this member has armed — one query per board render, never one per card
     *  (see {@code BoardRenderCostTest}). A member holds a handful at most. */
    List<ChoreReminder> findByMemberId(Long memberId);

    @Transactional
    void deleteByMemberIdAndTaskId(Long memberId, Long taskId);

    @Transactional
    void deleteByTaskId(Long taskId);

    @Transactional
    void deleteByMemberId(Long memberId);

    @Transactional
    void deleteByHomeCode(String homeCode);
}
