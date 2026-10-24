package com.homechores.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findByHomeCodeOrderByJoinedAtAsc(String homeCode);

    long countByHomeCodeAndAdminTrue(String homeCode);

    long countByHomeCode(String homeCode);

    /**
     * The member, if they are <em>currently</em> an admin of this home — the one check every
     * admin-only service method makes for the acting member, read fresh at action time. The
     * Admin tab exists only for admins, but a dialog opened from it outlives the redraw that
     * removes the tab, so "the UI only showed it to an admin" is not evidence of anything.
     */
    default Optional<Member> findAdmin(String homeCode, Long memberId) {
        if (homeCode == null || memberId == null) {
            return Optional.empty();
        }
        return findById(memberId)
                .filter(m -> m.isAdmin() && homeCode.equalsIgnoreCase(m.getHomeCode()));
    }

    /** Members with a chore reminder configured (the reminder sweep's working set). */
    List<Member> findByReminderTimeNotNull();

    @Transactional
    void deleteByHomeCode(String homeCode);
}
