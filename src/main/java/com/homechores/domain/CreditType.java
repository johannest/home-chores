package com.homechores.domain;

/** Whether a credit ledger entry adds to or subtracts from a member's balance. */
public enum CreditType {
    /** Credits awarded (chore reward or spree bonus). */
    EARNED,
    /** Credits spent, logged by an admin. */
    REDEEMED
}
