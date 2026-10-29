package com.homechores.domain;

/** Which of the home's shared lists a {@link ListItem} sits on. */
public enum ListKind {
    /** The shopping list: whoever goes to the store next ticks things off. */
    GROCERY,
    /** Family to-dos that are not recurring chores — "call the plumber", "buy a gift for Aunt Liisa". */
    TODO,
    /** The dinner plan: one free-text slot per calendar day ({@link ListItem#getDay()}), shown as a
     *  sliding week from today. Never ticked; a slot is set, changed or cleared. */
    DINNER,
    /**
     * The Routines tab (§4.4f): a place on the Lists panel, never the kind of a {@link ListItem}.
     * Its rows are {@link Routine}s. It is a constant here so the admin's list order and
     * on/off switches cover it unchanged — but no line is ever stored with it, because
     * {@code list_item.kind} may be a native H2 {@code ENUM} created before it existed (see
     * {@link CustomList}). {@link #holdsLines()} is the guard every line write checks.
     */
    ROUTINES;

    /** Whether {@link ListItem}s can be of this kind. */
    public boolean holdsLines() {
        return this != ROUTINES;
    }
}
