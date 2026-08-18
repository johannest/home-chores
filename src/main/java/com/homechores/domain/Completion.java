package com.homechores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * A record that a member completed a chore at a point in time — or, when {@code taskId}
 * is null, that they helped in a way no chore covers and wrote down what they did
 * ("other help", see {@link #otherHelp}).
 */
@Entity
public class Completion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String homeCode;

    /** The chore that was done, or null for an "other help" entry. */
    private Long taskId;

    private Long memberId;

    private Instant doneAt = Instant.now();

    @Enumerated(EnumType.STRING)
    private CompletionStatus status = CompletionStatus.APPROVED;

    @Enumerated(EnumType.STRING)
    private Feedback feedback; // nullable

    private Long reviewedByMemberId; // nullable

    private Instant reviewedAt; // nullable

    /** What the member wrote for an "other help" entry; null for ordinary chores. */
    @Column(length = 300)
    private String note;

    protected Completion() {
    }

    public Completion(String homeCode, Long taskId, Long memberId, CompletionStatus status) {
        this.homeCode = homeCode;
        this.taskId = taskId;
        this.memberId = memberId;
        this.status = status;
    }

    /**
     * Help the chore list doesn't cover, described by the member in their own words. It has
     * no task, so it starts PENDING whatever the home's approval setting says: somebody has
     * to read the text before it can count for anything.
     */
    public static Completion otherHelp(String homeCode, Long memberId, String note) {
        Completion c = new Completion(homeCode, null, memberId, CompletionStatus.PENDING);
        c.note = note;
        return c;
    }

    /** True for an entry a member wrote themselves rather than a tap on a chore card. */
    public boolean isOtherHelp() {
        return taskId == null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Instant getDoneAt() {
        return doneAt;
    }

    public void setDoneAt(Instant doneAt) {
        this.doneAt = doneAt;
    }

    public CompletionStatus getStatus() {
        return status;
    }

    public void setStatus(CompletionStatus status) {
        this.status = status;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public void setFeedback(Feedback feedback) {
        this.feedback = feedback;
    }

    public Long getReviewedByMemberId() {
        return reviewedByMemberId;
    }

    public void setReviewedByMemberId(Long reviewedByMemberId) {
        this.reviewedByMemberId = reviewedByMemberId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
