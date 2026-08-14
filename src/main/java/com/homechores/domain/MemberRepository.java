package com.homechores.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findByHomeCodeOrderByJoinedAtAsc(String homeCode);

    long countByHomeCodeAndAdminTrue(String homeCode);

    @Transactional
    void deleteByHomeCode(String homeCode);
}
