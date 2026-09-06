package com.homechores.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findByHomeCodeOrderByJoinedAtAsc(String homeCode);

    long countByHomeCodeAndAdminTrue(String homeCode);

    long countByHomeCode(String homeCode);

    /** Members with a chore reminder configured (the reminder sweep's working set). */
    List<Member> findByReminderTimeNotNull();

    @Transactional
    void deleteByHomeCode(String homeCode);
}
