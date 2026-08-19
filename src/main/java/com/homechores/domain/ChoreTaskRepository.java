package com.homechores.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ChoreTaskRepository extends JpaRepository<ChoreTask, Long> {

    List<ChoreTask> findByHomeCodeOrderByCreatedAtAsc(String homeCode);

    /** Every chore currently carrying a booking, expired or not — the expiry sweep's candidates. */
    List<ChoreTask> findByBookedByMemberIdIsNotNull();

    @Transactional
    void deleteByHomeCode(String homeCode);
}
