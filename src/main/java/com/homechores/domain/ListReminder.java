package com.homechores.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * The home's one reminder about one shared-list line — "remind us about the plumber tomorrow
 * morning".
 *
 * <p>Home-wide, not per member, unlike {@link ChoreReminder}: a shared list is shared, so the
 * push goes to every subscribed device in the home, everyone sees the badge, and anyone may
 * move or cancel it. At most one per line, upserted in the service ("tomorrow" then "actually,
 * next week" moves it). Enforced there rather than with a unique index, for the reason
 * {@link ChoreReminder} gives.
 *
 * <p>Two lives, not one. Until {@link #firedAt} is set the row is a pending notification: the
 * sweep sends it once and stamps the moment. After that it is an <em>unanswered</em> one: the app
 * shows it as a dialog to whoever opens the board next, offering the same snooze choices, until
 * somebody snoozes (which clears {@code firedAt} and moves {@code dueAt}), ticks the line off, or
 * dismisses it. A notification the family never answers is purged after a week.
 *
 * <p>{@code dueAt} is an absolute instant, so no zone travels with it; "tomorrow 9:00" is
 * resolved in the setter's zone before it gets here. {@code homeCode} is denormalised off the line
 * so the home-deletion cascade needs no join. {@code setByMemberId} is who asked, for the badge —
 * nullable, and left dangling when that member leaves, like {@link ListItem#getCreatedByMemberId()}.
 */
@Entity
public class ListReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String homeCode;

    private Long itemId;

    /** When the push should go out (or, once fired, when it was due). */
    private Instant dueAt;

    /** When the push went out; null while still pending. */
    private Instant firedAt;

    private Long setByMemberId;

    private Instant createdAt = Instant.now();

    protected ListReminder() {
    }

    public ListReminder(String homeCode, Long itemId, Instant dueAt, Long setByMemberId) {
        this.homeCode = homeCode;
        this.itemId = itemId;
        this.dueAt = dueAt;
        this.setByMemberId = setByMemberId;
    }

    public Long getId() {
        return id;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public Long getItemId() {
        return itemId;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Instant getFiredAt() {
        return firedAt;
    }

    public void setFiredAt(Instant firedAt) {
        this.firedAt = firedAt;
    }

    public boolean isFired() {
        return firedAt != null;
    }

    public Long getSetByMemberId() {
        return setByMemberId;
    }

    public void setSetByMemberId(Long setByMemberId) {
        this.setByMemberId = setByMemberId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
