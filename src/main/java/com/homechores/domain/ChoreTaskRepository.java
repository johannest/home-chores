package com.homechores.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ChoreTaskRepository extends JpaRepository<ChoreTask, Long> {

    /**
     * Creation order — the rotation's index space ({@code ChoreService.tasksInRotationOrder})
     * and the backup export's stable order. Deliberately <em>not</em> the board order; see
     * {@link #findByHomeCodeOrderBySortOrderAscCreatedAtAscIdAsc}.
     */
    List<ChoreTask> findByHomeCodeOrderByCreatedAtAsc(String homeCode);

    /**
     * Board order within a group. {@code sortOrder} is 0 for every chore that predates the
     * column, so {@code createdAt} is what actually orders a home that has never been
     * reordered — the upgrade must be invisible. The id breaks the remaining tie between
     * chores seeded in the same millisecond.
     *
     * <p>There is deliberately no {@code …AndGroupId…} variant: Spring Data renders a null
     * argument as {@code = null}, which matches nothing, so the ungrouped bucket would come
     * back silently empty while every grouped bucket worked. Bucketing happens in Java over
     * the home's list instead — which is also what lets a dangling groupId fall back to
     * ungrouped instead of vanishing.
     */
    List<ChoreTask> findByHomeCodeOrderBySortOrderAscCreatedAtAscIdAsc(String homeCode);

    /** Every chore currently carrying a booking, expired or not — the expiry sweep's candidates. */
    List<ChoreTask> findByBookedByMemberIdIsNotNull();

    @Transactional
    void deleteByHomeCode(String homeCode);
}
