package com.homechores.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface RejoinRequestRepository extends JpaRepository<RejoinRequest, Long> {

    Optional<RejoinRequest> findByDeviceToken(String deviceToken);

    List<RejoinRequest> findByHomeCodeAndStatusOrderByRequestedAtAsc(String homeCode,
                                                                    RejoinStatus status);

    long countByHomeCodeAndStatus(String homeCode, RejoinStatus status);

    @Transactional
    void deleteByHomeCode(String homeCode);

    @Transactional
    void deleteByMemberId(Long memberId);
}
