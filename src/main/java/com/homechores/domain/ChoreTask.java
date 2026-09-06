package com.homechores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** A repeatable chore that members tap when they do it (e.g. "Empty dishwasher"). */
@Entity
public class ChoreTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String homeCode;

    /** Length backstop for {@link InputLimits#TASK_NAME}; the service layer clips first.
     *  ({@code ddl-auto=update} won't narrow the column on databases that predate it.) */
    @Column(length = InputLimits.TASK_NAME)
    private String name;

    /** Emoji shown on the task button. */
    @Column(length = InputLimits.EMOJI)
    private String emoji;

    /** If &gt; 0, the chore is only due every N days (e.g. water plants every 5 days).
     *  0 means always available. */
    private int intervalDays = 0;

    /** Credits awarded for completing this chore (0 = no reward; higher for hard chores). */
    private int creditValue = 0;

    /** Daily availability windows in the member's local time, canonical
     *  "HH:mm-HH:mm[,…]" (see {@link TimeWindows}); null/blank = available all day. */
    @Column(length = InputLimits.TIME_WINDOWS)
    private String availableWindows;

    /** Seasons this chore applies to, canonical "SPRING[,SUMMER…]" (see {@link Seasons});
     *  null/blank = all year round. Where availableWindows says time of day, this says
     *  time of year. */
    private String seasons;

    /**
     * The board section this chore sits in, or null for the ungrouped section at the bottom.
     *
     * <p>A scalar id, not a JPA relationship: the domain has no associations anywhere and the
     * services do the join. A groupId whose group has since been deleted reads as ungrouped —
     * {@code ChoreService.tasksOf} falls back rather than dropping the chore, so a missed write
     * can never make a chore disappear from the board.
     */
    private Long groupId;

    /**
     * Position inside this chore's group, dense 0..n-1, ascending. Board order only; nothing
     * may derive meaning from it (see {@code ChoreService.tasksInRotationOrder}).
     *
     * <p>The column default matters: {@code ddl-auto=update} adds this to databases that already
     * have chores in them, and H2 refuses a plain {@code not null} column there (see the note on
     * {@code Home.approveRejoin}). Every existing chore therefore adopts 0, which is why the
     * board query tie-breaks on {@code createdAt} — a family that never reorders anything keeps
     * exactly the order they had.
     */
    @Column(columnDefinition = "integer not null default 0")
    private int sortOrder = 0;

    /** Member who has currently booked ("I'll do it") this chore, or null. */
    private Long bookedByMemberId;

    /** When the booking was made; it expires bookingTimeoutHours later. */
    private Instant bookedAt;

    private Instant createdAt = Instant.now();

    protected ChoreTask() {
    }

    public ChoreTask(String homeCode, String name, String emoji) {
        this.homeCode = homeCode;
        this.name = name;
        this.emoji = emoji;
    }

    public Long getId() {
        return id;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(int intervalDays) {
        this.intervalDays = intervalDays;
    }

    public int getCreditValue() {
        return creditValue;
    }

    public void setCreditValue(int creditValue) {
        this.creditValue = creditValue;
    }

    public String getAvailableWindows() {
        return availableWindows;
    }

    public void setAvailableWindows(String availableWindows) {
        this.availableWindows = availableWindows;
    }

    public String getSeasons() {
        return seasons;
    }

    public void setSeasons(String seasons) {
        this.seasons = seasons;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getBookedByMemberId() {
        return bookedByMemberId;
    }

    public void setBookedByMemberId(Long bookedByMemberId) {
        this.bookedByMemberId = bookedByMemberId;
    }

    public Instant getBookedAt() {
        return bookedAt;
    }

    public void setBookedAt(Instant bookedAt) {
        this.bookedAt = bookedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
