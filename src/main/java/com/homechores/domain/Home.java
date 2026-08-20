package com.homechores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * A household. The primary key {@code code} is the short, shareable "home id"
 * that members type in to join (Kahoot style).
 */
@Entity
public class Home {

    @Id
    @Column(length = 8)
    private String code;

    private String name;

    /** 4-digit admin PIN — the credential for (re)claiming admin rights. */
    private String adminPin;

    /** When on, completions start PENDING and must be approved by an admin. */
    private boolean requireApproval = false;

    /** How many chores each member is expected to do per day (1–3). */
    private int dailyTargetPerMember = 1;

    /** How chores are divided among members. */
    @Enumerated(EnumType.STRING)
    private DivisionStyle divisionStyle = DivisionStyle.DEFAULT;

    /** In ROTATING mode, whether members may ONLY do their assigned chore (true) or
     *  the assignment is merely a highlighted suggestion (false). */
    private boolean rotationEnforced = true;

    /** How many hours a member's booking of a chore holds before it frees up for others. */
    private int bookingTimeoutHours = 4;

    /**
     * When on, a device signing back in as an existing member (after clearing its browser
     * storage) needs an admin's approval. Defaults to on: the home code travels in join
     * links, so without the gate anyone holding one could step into any member's identity.
     * Knowing the admin PIN always bypasses the gate.
     *
     * <p>The column default matters: {@code ddl-auto=update} adds this to databases that
     * already have homes in them, and H2 refuses a plain {@code not null} column there.
     * Existing homes therefore adopt the gated behaviour.
     */
    @Column(columnDefinition = "boolean not null default true")
    private boolean approveRejoin = true;

    /**
     * When on, someone joining the home for the first time with just the code waits for an
     * admin's approval before a member is created. Defaults to on: a home code is short
     * enough to be guessed or leaked, and without the gate a guess immediately plants a
     * stranger on the family's board. See the note on {@code approveRejoin} for why the
     * column carries an explicit default.
     */
    @Column(columnDefinition = "boolean not null default true")
    private boolean approveJoin = true;

    /**
     * When on, tapping a chore asks "did you do this?" before recording it. Defaults to on
     * because the cards are large and close together on a phone, and user testing showed
     * people completing several by accident. See the note on {@code approveRejoin} for why
     * the column carries an explicit default.
     */
    @Column(columnDefinition = "boolean not null default true")
    private boolean confirmCompletion = true;

    /**
     * When on, members can log help that no chore covers ("other help") in their own words,
     * for an admin to accept or decline. On by default — real help that happens to be
     * missing from the board is exactly what a family wants to notice. See the note on
     * {@code approveRejoin} for why the column carries an explicit default.
     */
    @Column(columnDefinition = "boolean not null default true")
    private boolean allowOtherHelp = true;

    private Instant createdAt = Instant.now();

    /**
     * When a member last actually did something here — completed a chore, reviewed one, or
     * opened the board. Not touched by background push traffic, so it means "a person used
     * this home", which is what retention decisions hang on.
     *
     * <p>Deliberately nullable: homes that predate the column read as null, and
     * {@link #lastActiveOrCreated()} falls back to the creation time rather than needing a
     * DDL default (see the note on {@code approveRejoin}).
     */
    private Instant lastActiveAt;

    protected Home() {
    }

    public Home(String code, String name, String adminPin) {
        this.code = code;
        this.name = name;
        this.adminPin = adminPin;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAdminPin() {
        return adminPin;
    }

    public void setAdminPin(String adminPin) {
        this.adminPin = adminPin;
    }

    public boolean isRequireApproval() {
        return requireApproval;
    }

    public void setRequireApproval(boolean requireApproval) {
        this.requireApproval = requireApproval;
    }

    public int getDailyTargetPerMember() {
        return dailyTargetPerMember;
    }

    public void setDailyTargetPerMember(int dailyTargetPerMember) {
        this.dailyTargetPerMember = dailyTargetPerMember;
    }

    public DivisionStyle getDivisionStyle() {
        return divisionStyle;
    }

    public void setDivisionStyle(DivisionStyle divisionStyle) {
        this.divisionStyle = divisionStyle;
    }

    public boolean isRotationEnforced() {
        return rotationEnforced;
    }

    public void setRotationEnforced(boolean rotationEnforced) {
        this.rotationEnforced = rotationEnforced;
    }

    public int getBookingTimeoutHours() {
        return bookingTimeoutHours;
    }

    public void setBookingTimeoutHours(int bookingTimeoutHours) {
        this.bookingTimeoutHours = bookingTimeoutHours;
    }

    public boolean isApproveRejoin() {
        return approveRejoin;
    }

    public void setApproveRejoin(boolean approveRejoin) {
        this.approveRejoin = approveRejoin;
    }

    public boolean isApproveJoin() {
        return approveJoin;
    }

    public void setApproveJoin(boolean approveJoin) {
        this.approveJoin = approveJoin;
    }

    public boolean isConfirmCompletion() {
        return confirmCompletion;
    }

    public void setConfirmCompletion(boolean confirmCompletion) {
        this.confirmCompletion = confirmCompletion;
    }

    public boolean isAllowOtherHelp() {
        return allowOtherHelp;
    }

    public void setAllowOtherHelp(boolean allowOtherHelp) {
        this.allowOtherHelp = allowOtherHelp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    /** Last activity, falling back to creation for homes that never recorded any. */
    public Instant lastActiveOrCreated() {
        return lastActiveAt != null ? lastActiveAt : createdAt;
    }
}
