package com.homechores.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface RoutineRepository extends JpaRepository<Routine, Long> {

    List<Routine> findByHomeCodeOrderByTimeAscNameAscIdAsc(String homeCode);

    long countByHomeCode(String homeCode);

    /** The reminder sweep's working set, bounded in the query: it shares the one scheduler thread. */
    List<Routine> findTop50ByNextFireAtLessThanEqualOrderByNextFireAtAsc(Instant now);

    List<Routine> findByForMemberId(Long memberId);

    @Transactional
    void deleteByHomeCode(String homeCode);
}
