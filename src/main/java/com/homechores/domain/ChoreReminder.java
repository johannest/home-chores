package com.homechores.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * One member's one-shot nudge about one chore — "remind me about the dishwasher in two hours".
 *
 * <p>Unlike {@link Member#getReminderTime()}, which is a standing daily schedule, this row
 * <em>is</em> the reminder: it exists from the moment it is asked for until it fires, and is then
 * deleted. Nothing recurs, so there is no stamp to keep and nothing to make idempotent beyond the
 * row's own existence.
 *
 * <p>At most one per member per chore, upserted in the service: the card shows a single ⏰, and
 * "in 2h" followed by "actually, tomorrow" must move the reminder rather than arm a second one.
 * Enforced there rather than with a unique index, because {@code ddl-auto=update} adding a unique
 * constraint to a table that already has rows is exactly the kind of thing that bites.
 *
 * <p>{@code dueAt} is an absolute instant, not a wall-clock time — which is why this entity needs
 * no zone and no locale of its own, and cannot be wrong across a DST boundary or a flight.
 * {@code homeCode} is denormalised off the task for the same reason {@link PushSubscription}
 * carries one: it is what lets the home-deletion cascade find these rows without a join.
 */
@Entity
public class ChoreReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private String homeCode;

    private Long taskId;

    /** When the push should go out. Absolute, so no zone travels with it. */
    private Instant dueAt;

    private Instant createdAt = Instant.now();

    protected ChoreReminder() {
    }

    public ChoreReminder(Long memberId, String homeCode, Long taskId, Instant dueAt) {
        this.memberId = memberId;
        this.homeCode = homeCode;
        this.taskId = taskId;
        this.dueAt = dueAt;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
