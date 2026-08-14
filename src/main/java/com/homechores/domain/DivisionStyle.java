package com.homechores.domain;

/** How chores are divided among members in a home. */
public enum DivisionStyle {
    /** Anyone can do any chore; fairness enforced by the max-in-a-row rule. */
    DEFAULT,
    /** Each member is assigned a rotating chore per day (round-robin). */
    ROTATING
}
