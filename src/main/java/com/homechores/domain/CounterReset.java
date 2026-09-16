package com.homechores.domain;

/**
 * How often the leaderboard counter in a member's badge starts over. Only that badge: the
 * statistics, milestones, credits and the chore-master pick keep counting every completion the
 * member ever made. A family that wants "who did most this month" sets MONTHLY (the default);
 * one that likes an all-time score sets NEVER.
 */
public enum CounterReset {
    NEVER, WEEKLY, MONTHLY, YEARLY
}
