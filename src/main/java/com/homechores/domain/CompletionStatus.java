package com.homechores.domain;

/** Lifecycle of a chore completion under the optional approval workflow. */
public enum CompletionStatus {
    /** Awaiting admin review (only when the home requires approval). */
    PENDING,
    /** Counts toward stats, leaderboard, milestones. */
    APPROVED,
    /** Rejected by an admin; retained for audit, excluded from all counts. */
    REJECTED
}
