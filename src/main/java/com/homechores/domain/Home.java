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

    private Instant createdAt = Instant.now();

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
