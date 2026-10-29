package com.homechores.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface RoutineDoneRepository extends JpaRepository<RoutineDone, Long> {

    /** Every tick in one home on one day — one query per render, never one per routine. */
    List<RoutineDone> findByHomeCodeAndDay(String homeCode, LocalDate day);

    List<RoutineDone> findByRoutineIdAndDay(Long routineId, LocalDate day);

    boolean existsByRoutineIdAndDay(Long routineId, LocalDate day);

    @Transactional
    void deleteByRoutineId(Long routineId);

    @Transactional
    void deleteByHomeCode(String homeCode);

    /** Ticks older than the purge horizon: they only ever mattered on their own day. */
    @Transactional
    long deleteByDayBefore(LocalDate cutoff);
}
