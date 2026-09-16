package com.homechores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One line on a home's shared list (groceries or to-dos). Anyone in the home can add, tick,
 * untick or delete a line; none of it touches chores, completions or credits — a ticked
 * grocery is not a chore done, and must not start a streak or earn a diamond.
 *
 * <p>Ticking stamps {@link #doneAt} and {@link #doneByMemberId}; unticking clears both. Ticked
 * lines are shown struck through for a day and then purged (see {@code ListItemService}).
 *
 * <p>A {@link ListKind#DINNER} row is a day's slot rather than a line: {@link #day} says which
 * day, and {@link #createdAt} / {@link #createdByMemberId} mean "last set at / by", because the
 * same row is rewritten when someone changes their mind. Dinner rows are never ticked.
 *
 * <p>Brand-new table, so no {@code columnDefinition} defaults are needed — see the note on
 * {@link ChoreGroup#getSortOrder()}.
 */
@Entity
public class ListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String homeCode;

    @Enumerated(EnumType.STRING)
    private ListKind kind;

    /** Length backstop for {@link InputLimits#LIST_ITEM}; the service layer clips first. */
    @Column(length = InputLimits.LIST_ITEM)
    private String text;

    private Instant createdAt = Instant.now();

    /** Who wrote the line. Null once that member is removed from the home. */
    private Long createdByMemberId;

    /** When the line was ticked off; null while it is still open. */
    private Instant doneAt;

    /** Who ticked it off; null while open, and null once that member is removed. */
    private Long doneByMemberId;

    /** The calendar day a DINNER slot belongs to; null for GROCERY / TODO lines. Stored as
     *  {@code plan_day}: {@code DAY} is a reserved word in H2. */
    @Column(name = "plan_day")
    private LocalDate day;

    protected ListItem() {
    }

    public ListItem(String homeCode, ListKind kind, String text, Long createdByMemberId) {
        this.homeCode = homeCode;
        this.kind = kind;
        this.text = text;
        this.createdByMemberId = createdByMemberId;
    }

    public Long getId() {
        return id;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public ListKind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
    }

    public Instant getDoneAt() {
        return doneAt;
    }

    public void setDoneAt(Instant doneAt) {
        this.doneAt = doneAt;
    }

    public Long getDoneByMemberId() {
        return doneByMemberId;
    }

    public void setDoneByMemberId(Long doneByMemberId) {
        this.doneByMemberId = doneByMemberId;
    }

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public boolean isDone() {
        return doneAt != null;
    }
}
