package com.homechores.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByMemberId(Long memberId);

    /** A member's devices, oldest first — the order the per-member cap evicts in. */
    List<PushSubscription> findByMemberIdOrderByCreatedAtAscIdAsc(Long memberId);

    /** Every device in a home — the audience of a home-wide list reminder. */
    List<PushSubscription> findByHomeCode(String homeCode);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    /** Scoped to the member: a device may only unsubscribe itself, never an endpoint it
     *  somehow learned of another's. */
    @Transactional
    void deleteByEndpointAndMemberId(String endpoint, Long memberId);

    @Transactional
    void deleteByMemberId(Long memberId);

    @Transactional
    void deleteByHomeCode(String homeCode);
}
