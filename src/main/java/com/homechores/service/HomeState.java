package com.homechores.service;

import com.vaadin.flow.signals.shared.SharedNumberSignal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Reactive, cross-session state for each home.
 *
 * <p>Holds a shared "revision" signal per home code. Any mutation bumps the revision;
 * because it's a {@link SharedNumberSignal}, every UI that reads it inside a
 * {@code Signal.effect} re-runs automatically — across browser sessions, pushed live.
 * This replaces the old manual broadcaster + {@code UI.access} plumbing.
 */
@Component
public class HomeState {

    private final Map<String, SharedNumberSignal> revisions = new ConcurrentHashMap<>();

    /** The revision signal for a home (created on first use). */
    public SharedNumberSignal revision(String homeCode) {
        return revisions.computeIfAbsent(homeCode, k -> new SharedNumberSignal());
    }

    /** Signal that something in this home changed, triggering all bound effects to re-run. */
    public void bump(String homeCode) {
        revision(homeCode).incrementBy(1);
    }
}
