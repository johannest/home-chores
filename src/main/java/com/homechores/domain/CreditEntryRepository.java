package com.homechores.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface CreditEntryRepository extends JpaRepository<CreditEntry, Long> {

    List<CreditEntry> findByMemberId(Long memberId);

    List<CreditEntry> findByHomeCodeOrderByCreatedAtDesc(String homeCode);

    /** Credits earned by one completion — revoked when it is undone or deleted. */
    @Transactional
    void deleteByCompletionId(Long completionId);

    @Transactional
    void deleteByHomeCode(String homeCode);

    @Transactional
    void deleteByMemberId(Long memberId);
}
