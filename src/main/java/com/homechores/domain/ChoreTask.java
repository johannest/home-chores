package com.homechores.domain;

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

    private String name;

    /** Emoji shown on the task button. */
    private String emoji;

    /** If &gt; 0, the chore is only due every N days (e.g. water plants every 5 days).
     *  0 means always available. */
    private int intervalDays = 0;

    /** Credits awarded for completing this chore (0 = no reward; higher for hard chores). */
    private int creditValue = 0;

    /** Daily availability windows in the member's local time, canonical
     *  "HH:mm-HH:mm[,…]" (see {@link TimeWindows}); null/blank = available all day. */
    private String availableWindows;

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
