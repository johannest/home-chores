package com.homechores.ui;

import com.vaadin.flow.component.notification.Notification;

/**
 * Where the app's toasts appear. At the bottom, not the top: index.html ships
 * {@code viewport-fit=cover}, and a toast at the top of a phone sat behind the camera island.
 * The bottom is also where the thumb is for a toast's Undo, and styles.css keeps the container
 * clear of the home indicator and of the on-screen keyboard ({@code keyboard-inset.js}). The two {@code MIDDLE} toasts (why a chore cannot be done now,
 * a chore someone else just booked) answer a tap that did nothing and keep their own position.
 */
final class Toasts {

    static final Notification.Position POSITION = Notification.Position.BOTTOM_CENTER;

    private Toasts() {
    }

    static Notification show(String text, int durationMs) {
        return Notification.show(text, durationMs, POSITION);
    }
}
