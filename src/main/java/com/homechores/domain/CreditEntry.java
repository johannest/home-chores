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

    /**
     * The completion that earned this credit, when it came from one. Undoing or deleting
     * that completion revokes the credit, so a mis-tap can't leave phantom 💎 behind (nor
     * be farmed by completing and undoing). Null for admin redemptions and for entries
     * restored from a backup written before this existed.
     */
    private Long completionId;

    private Instant createdAt = Instant.now();

    protected CreditEntry() {
    }

    public CreditEntry(String homeCode, Long memberId, int amount, CreditType type, String reason,
                       int spreeTierDays) {
        this(homeCode, memberId, amount, type, reason, spreeTierDays, null);
    }

    public CreditEntry(String homeCode, Long memberId, int amount, CreditType type, String reason,
                       int spreeTierDays, Long completionId) {
        this.homeCode = homeCode;
        this.memberId = memberId;
        this.amount = amount;
        this.type = type;
        this.reason = reason;
        this.spreeTierDays = spreeTierDays;
        this.completionId = completionId;
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

    public Long getCompletionId() {
        return completionId;
    }

    public void setCompletionId(Long completionId) {
        this.completionId = completionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
