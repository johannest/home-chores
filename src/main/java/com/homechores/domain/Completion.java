package com.homechores.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** A record that a member completed a chore at a point in time. */
@Entity
public class Completion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String homeCode;

    private Long taskId;

    private Long memberId;

    private Instant doneAt = Instant.now();

    @Enumerated(EnumType.STRING)
    private CompletionStatus status = CompletionStatus.APPROVED;

    @Enumerated(EnumType.STRING)
    private Feedback feedback; // nullable

    private Long reviewedByMemberId; // nullable

    private Instant reviewedAt; // nullable

    protected Completion() {
    }

    public Completion(String homeCode, Long taskId, Long memberId, CompletionStatus status) {
        this.homeCode = homeCode;
        this.taskId = taskId;
        this.memberId = memberId;
        this.status = status;
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
}
