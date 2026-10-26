package com.homechores.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface TrophyRewardRepository extends JpaRepository<TrophyReward, Long> {

    List<TrophyReward> findByHomeCodeOrderByRungAsc(String homeCode);

    Optional<TrophyReward> findByHomeCodeAndRung(String homeCode, int rung);

    @Transactional
    void deleteByHomeCode(String homeCode);
}
