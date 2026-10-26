package com.homechores.domain;

import jakarta.persistence.Column;
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

    /** Human-readable reason (chore name, "spree", or an admin's redemption note).
     *  Length backstop for {@link InputLimits#REASON}; the service layer clips first. */
    @Column(length = InputLimits.REASON)
    private String reason;

    /** For spree awards, the tier's day-count (used to avoid awarding it twice in one streak). */
    private int spreeTierDays = 0;

    /**
     * For trophy rewards, the {@link Trophy} rung that paid it (0 otherwise) — so a rung pays a
     * member once, even when an admin's correction lets them reach it a second time. The column
     * carries a DDL default because {@code ddl-auto=update} adds it to a table that already has
     * rows, and H2 refuses a plain {@code not null} column there.
     */
    @Column(columnDefinition = "integer not null default 0")
    private int trophyRung = 0;

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

    /** A trophy's 💎 reward, tagged with the completion that reached the rung so undoing it
     *  takes the reward back. */
    public static CreditEntry trophy(String homeCode, Long memberId, int amount, int rung,
                                     Long completionId) {
        CreditEntry e = new CreditEntry(homeCode, memberId, amount, CreditType.EARNED, "trophy", 0,
                completionId);
        e.trophyRung = rung;
        return e;
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

    public int getTrophyRung() {
        return trophyRung;
    }

    public void setTrophyRung(int trophyRung) {
        this.trophyRung = trophyRung;
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
