package com.homechores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * A list a home named itself ("Hardware store", "Gift ideas"), shown as its own tab beside
 * groceries, to-dos and dinners. It is a <em>container</em>, unlike {@link ChoreGroup}: its lines
 * are {@link ListItem}s of kind {@link ListKind#TODO} carrying {@link ListItem#getListId()}, and
 * deleting the list deletes them.
 *
 * <p>Why not a new {@link ListKind} constant: Hibernate may have created {@code list_item.kind}
 * as a native H2 {@code ENUM}, which {@code ddl-auto=update} never widens — a new constant would
 * fail every insert on an existing database. A custom list's lines behave exactly like to-dos
 * (ticked, reminded, purged a day after ticking), so they are stored as to-dos.
 *
 * <p>Brand-new table, so no {@code columnDefinition} defaults are needed — see the note on
 * {@link ChoreGroup#getSortOrder()}.
 */
@Entity
public class CustomList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String homeCode;

    /** Length backstop for {@link InputLimits#LIST_NAME}; the service layer clips first. */
    @Column(length = InputLimits.LIST_NAME)
    private String name;

    private Instant createdAt = Instant.now();

    /** Who made it. Null once that member is removed from the home. */
    private Long createdByMemberId;

    protected CustomList() {
    }

    public CustomList(String homeCode, String name, Long createdByMemberId) {
        this.homeCode = homeCode;
        this.name = name;
        this.createdByMemberId = createdByMemberId;
    }

    public Long getId() {
        return id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }
}
