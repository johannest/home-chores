package com.homechores.service;

import com.vaadin.flow.signals.shared.SharedNumberSignal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    /**
     * Signal that something in this home changed, triggering all bound effects to re-run.
     *
     * <p>Bumping inside a transaction would wake the other devices too early: their effects
     * re-read the database in their own transactions, still see the pre-change state, render
     * it, and then never hear about the change again. So when a transaction is in progress
     * the bump waits for its commit. Deleting a home is where this really bites — the other
     * family members would otherwise sit on a board that no longer exists — but every
     * mutation benefits from not publishing state that hasn't been committed yet.
     */
    public void bump(String homeCode) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    revision(homeCode).incrementBy(1);
                }
            });
        } else {
            revision(homeCode).incrementBy(1);
        }
    }
}
