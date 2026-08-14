package com.homechores.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** A single entry in a member's credit ledger (earned reward or admin redemption). */
@Entity
public class CreditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String homeCode;

    private Long memberId;

    private int amount;

    @Enumerated(EnumType.STRING)
    private CreditType type;

    /** Human-readable reason (chore name, "spree", or an admin's redemption note). */
    private String reason;

    /** For spree awards, the tier's day-count (used to avoid awarding it twice in one streak). */
    private int spreeTierDays = 0;

    private Instant createdAt = Instant.now();

    protected CreditEntry() {
    }

    public CreditEntry(String homeCode, Long memberId, int amount, CreditType type, String reason,
                       int spreeTierDays) {
        this.homeCode = homeCode;
        this.memberId = memberId;
        this.amount = amount;
        this.type = type;
        this.reason = reason;
        this.spreeTierDays = spreeTierDays;
    }

    public Long getId() {
        return id;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public Long getMemberId() {
        return memberId;
    }

    public int getAmount() {
        return amount;
    }

    public CreditType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public int getSpreeTierDays() {
        return spreeTierDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
