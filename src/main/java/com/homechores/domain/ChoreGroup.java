package com.homechores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * A named section of the chore board (e.g. "🍳 Kitchen"). Chores point at one by id.
 *
 * <p>A group is a <em>label</em>, not a container: deleting one keeps its chores and their
 * whole history, moving them down to the ungrouped section. That is why
 * {@link ChoreTask#getGroupId()} is a plain scalar and nothing cascades from here.
 */
@Entity
public class ChoreGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String homeCode;

    /** Length backstop for {@link InputLimits#GROUP_NAME}; the service layer clips first. */
    @Column(length = InputLimits.GROUP_NAME)
    private String name;

    /** Emoji shown before the name in the board heading. */
    @Column(length = InputLimits.EMOJI)
    private String emoji;

    /**
     * Position among the home's groups, dense 0..n-1, ascending.
     *
     * <p>No {@code columnDefinition} here, deliberately — unlike {@link ChoreTask#getSortOrder()}.
     * That dance is only needed when a non-null column is added to a table that <em>already has
     * rows</em>, which H2 refuses under {@code ddl-auto=update} (see {@code Home.approveRejoin}).
     * This is a brand-new table: Hibernate creates it with the column already present, and every
     * row it will ever hold is written by code that sets the field.
     */
    private int sortOrder;

    private Instant createdAt = Instant.now();

    protected ChoreGroup() {
    }

    public ChoreGroup(String homeCode, String name, String emoji) {
        this.homeCode = homeCode;
        this.name = name;
        this.emoji = emoji;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /** Emoji + name, as the board heading and the admin's group picker show it. */
    public String display() {
        return (emoji == null || emoji.isBlank()) ? name : emoji + " " + name;
    }
}
