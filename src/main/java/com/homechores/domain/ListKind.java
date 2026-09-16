package com.homechores.domain;

/** Which of the home's shared lists a {@link ListItem} sits on. */
public enum ListKind {
    /** The shopping list: whoever goes to the store next ticks things off. */
    GROCERY,
    /** Family to-dos that are not recurring chores — "call the plumber", "buy a gift for Aunt Liisa". */
    TODO,
    /** The dinner plan: one free-text slot per calendar day ({@link ListItem#getDay()}), shown as a
     *  sliding week from today. Never ticked; a slot is set, changed or cleared. */
    DINNER
}
