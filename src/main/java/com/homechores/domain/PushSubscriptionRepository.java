package com.homechores.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByMemberId(Long memberId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    @Transactional
    void deleteByEndpoint(String endpoint);

    @Transactional
    void deleteByMemberId(Long memberId);

    @Transactional
    void deleteByHomeCode(String homeCode);
}
