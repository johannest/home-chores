package com.homechores.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ListItemRepository extends JpaRepository<ListItem, Long> {

    /** Open lines of one list, oldest first — the order people wrote them in. */
    List<ListItem> findByHomeCodeAndKindAndListIdIsNullAndDoneAtIsNullOrderByCreatedAtAscIdAsc(
            String homeCode, ListKind kind);

    /** Open lines of one of the home's own lists. */
    List<ListItem> findByListIdAndDoneAtIsNullOrderByCreatedAtAscIdAsc(Long listId);

    /** Recently ticked lines of one list, most recently ticked first. The cutoff is how the
     *  24-hour retention is applied at read time; the sweep only deletes what this already hides. */
    List<ListItem> findByHomeCodeAndKindAndListIdIsNullAndDoneAtAfterOrderByDoneAtDescIdDesc(
            String homeCode, ListKind kind, Instant cutoff);

    /** Recently ticked lines of one of the home's own lists. */
    List<ListItem> findByListIdAndDoneAtAfterOrderByDoneAtDescIdDesc(Long listId, Instant cutoff);

    /** Every line of one of the home's own lists — for deleting it with its reminders. */
    List<ListItem> findByListId(Long listId);

    /** One home's dinner slots inside a window, both ends inclusive, in calendar order. */
    List<ListItem> findByHomeCodeAndKindAndDayBetweenOrderByDayAscIdAsc(
            String homeCode, ListKind kind, LocalDate from, LocalDate to);

    /** The hourly sweep: dinner slots whose day is more than a week gone. */
    @Transactional
    long deleteByKindAndDayBefore(ListKind kind, LocalDate cutoff);

    /** Everything on every list, for backup export. */
    List<ListItem> findByHomeCodeOrderByCreatedAtAscIdAsc(String homeCode);

    /** "Clear done" on one list. */
    @Transactional
    long deleteByHomeCodeAndKindAndListIdIsNullAndDoneAtIsNotNull(String homeCode, ListKind kind);

    /** "Clear done" on one of the home's own lists. */
    @Transactional
    long deleteByListIdAndDoneAtIsNotNull(Long listId);

    /** The hourly sweep: ticked lines past their retention, across every home. */
    @Transactional
    long deleteByDoneAtBefore(Instant cutoff);

    @Transactional
    void deleteByHomeCode(String homeCode);
}
