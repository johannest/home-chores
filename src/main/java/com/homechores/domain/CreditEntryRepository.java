package com.homechores.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CreditEntryRepository extends JpaRepository<CreditEntry, Long> {

    List<CreditEntry> findByMemberId(Long memberId);

    /** One side of a member's ledger since a moment — the 💎 headline tiles and 7-day chart,
     *  which must not read a whole history to answer about this week. */
    List<CreditEntry> findByMemberIdAndTypeAndCreatedAtGreaterThanEqual(Long memberId,
            CreditType type, java.time.Instant since);

    /** One side of a member's ledger, newest first; the id breaks ties within a second. */
    List<CreditEntry> findByMemberIdAndTypeOrderByCreatedAtDescIdDesc(Long memberId, CreditType type);

    /** The all-time total of one side of a member's ledger, summed in the database. */
    @Query("select coalesce(sum(e.amount), 0) from CreditEntry e"
            + " where e.memberId = :memberId and e.type = :type")
    long sumAmount(@Param("memberId") Long memberId, @Param("type") CreditType type);

    /** Whether a trophy rung has already paid this member. */
    boolean existsByMemberIdAndTrophyRung(Long memberId, int trophyRung);

    List<CreditEntry> findByHomeCodeOrderByCreatedAtDesc(String homeCode);

    /** One type of row for the whole home, newest first; the id breaks ties within a second. */
    List<CreditEntry> findByHomeCodeAndTypeOrderByCreatedAtDescIdDesc(String homeCode,
                                                                      CreditType type);

    /** Credits earned by one completion — revoked when it is undone or deleted. */
    @Transactional
    void deleteByCompletionId(Long completionId);

    @Transactional
    void deleteByHomeCode(String homeCode);

    @Transactional
    void deleteByMemberId(Long memberId);
}
