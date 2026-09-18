package com.homechores.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ListReminderRepository extends JpaRepository<ListReminder, Long> {

    /** The sweep's working set: due and not yet sent. Bounded in the query, as the chore sweep's
     *  is — this shares the single scheduler thread. */
    List<ListReminder> findTop50ByDueAtLessThanEqualAndFiredAtIsNullOrderByDueAtAsc(Instant now);

    Optional<ListReminder> findByItemId(Long itemId);

    /** Everything armed or unanswered in one home — one query per list render, never one per line. */
    List<ListReminder> findByHomeCode(String homeCode);

    /** The unanswered ones, oldest first: what the board shows as a dialog on open. */
    List<ListReminder> findByHomeCodeAndFiredAtIsNotNullOrderByFiredAtAsc(String homeCode);

    @Transactional
    void deleteByItemId(Long itemId);

    @Transactional
    void deleteByHomeCode(String homeCode);

    /** Fired and never answered — reclaimed by the hourly purge after a week. */
    @Transactional
    long deleteByFiredAtBefore(Instant cutoff);
}
