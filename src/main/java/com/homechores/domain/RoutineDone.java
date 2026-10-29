package com.homechores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One tick of a {@link Routine} for one day in the home's zone (§4.4f). At most one per routine
 * and day, upheld by the service rather than a unique index, for the reason {@link ChoreReminder}
 * gives. Not family history: a tick only matters on its own day, so the hourly list purge
 * reclaims rows older than a week and backups leave them out.
 *
 * <p>{@code doneByMemberId} is null when the shared device ticked it, as a list line's is.
 */
@Entity
public class RoutineDone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long routineId;

    /** Denormalised off the routine so a home's ticks are one indexed read, and its deletion
     *  needs no join. */
    private String homeCode;

    /** The day ticked, in the home's zone. Stored as {@code done_day}: {@code DAY} is a reserved
     *  word in H2 (see {@link ListItem#getDay()}). */
    @Column(name = "done_day")
    private LocalDate day;

    private Long doneByMemberId;

    private Instant doneAt = Instant.now();

    protected RoutineDone() {
    }

    public RoutineDone(Long routineId, String homeCode, LocalDate day, Long doneByMemberId) {
        this.routineId = routineId;
        this.homeCode = homeCode;
        this.day = day;
        this.doneByMemberId = doneByMemberId;
    }

    public Long getId() {
        return id;
    }

    public Long getRoutineId() {
        return routineId;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public LocalDate getDay() {
        return day;
    }

    public Long getDoneByMemberId() {
        return doneByMemberId;
    }

    public Instant getDoneAt() {
        return doneAt;
    }
}
