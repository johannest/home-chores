package com.homechores.service;

import com.homechores.domain.InputLimits;
import com.homechores.domain.ListItem;
import com.homechores.domain.ListItemRepository;
import com.homechores.domain.ListKind;
import com.homechores.domain.ListReminderRepository;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The home's shared grocery, to-do and dinner lists. Deliberately outside {@link ChoreService}: a line
 * here is not a chore. Ticking one creates no {@code Completion}, earns no credits, starts no
 * streak and needs no approval — whoever is at the store ticks the milk off, and that is all.
 *
 * <p>Ticked lines stay visible, struck through, for {@link #DONE_RETENTION} and then vanish.
 * That retention is applied twice, on purpose: the read path filters by {@code doneAt}, so a
 * line ages out of every board on its next render with no write at all, and an hourly sweep
 * reclaims the rows afterwards. Purging on read was rejected because the read runs inside every
 * open device's render effect — one write per device per revision bump, for bookkeeping.
 *
 * <p>The dinner list is a different shape: one slot per calendar day, set / changed / cleared
 * rather than added and ticked (see {@link #setDinner}). The panel shows a sliding week from
 * today; a day that has passed drops off the view and its row is reclaimed by the same sweep
 * {@link #DINNER_RETENTION_DAYS} later, so a backup still holds last week's meals.
 */
@Service
public class ListItemService {

    private static final Logger log = LoggerFactory.getLogger(ListItemService.class);

    /** How long a ticked line stays on the list before it is purged. */
    public static final Duration DONE_RETENTION = Duration.ofHours(24);

    /** How long a dinner slot outlives its day before the sweep reclaims it. */
    public static final int DINNER_RETENTION_DAYS = 7;

    private final ListItemRepository items;
    private final ListReminderRepository reminders;
    private final MemberRepository members;
    private final ChoreService chores;
    private final HomeState homeState;

    public ListItemService(ListItemRepository items, ListReminderRepository reminders,
                           MemberRepository members, ChoreService chores, HomeState homeState) {
        this.items = items;
        this.reminders = reminders;
        this.members = members;
        this.chores = chores;
        this.homeState = homeState;
    }

    /** One line by id, whichever list it is on. */
    public Optional<ListItem> find(Long itemId) {
        return items.findById(itemId);
    }

    /** Open lines of one list, in the order they were written. */
    public List<ListItem> openItems(String homeCode, ListKind kind) {
        return items.findByHomeCodeAndKindAndDoneAtIsNullOrderByCreatedAtAscIdAsc(homeCode, kind);
    }

    /** Lines ticked within the retention window, most recently ticked first. */
    public List<ListItem> doneItems(String homeCode, ListKind kind) {
        return items.findByHomeCodeAndKindAndDoneAtAfterOrderByDoneAtDescIdDesc(
                homeCode, kind, Instant.now().minus(DONE_RETENTION));
    }

    /** Dinner slots per day in {@code [from, to]}, calendar-ordered; days with nothing set are absent. */
    public Map<LocalDate, ListItem> dinners(String homeCode, LocalDate from, LocalDate to) {
        Map<LocalDate, ListItem> out = new TreeMap<>();
        for (ListItem i : items.findByHomeCodeAndKindAndDayBetweenOrderByDayAscIdAsc(
                homeCode, ListKind.DINNER, from, to)) {
            out.putIfAbsent(i.getDay(), i); // the oldest row wins if a race ever made two
        }
        return out;
    }

    /**
     * Sets, changes or clears the dinner for one day — an upsert, because a day has one slot.
     * Blank text clears the slot. Saving the text a slot already holds changes nothing and does
     * not bump the home. Two phones saving the same day: last write wins, the same rule as two
     * phones editing anything else here.
     *
     * @return the slot, or empty when the day is (now) clear
     * @throws IllegalArgumentException if the member is not in that home
     */
    @Transactional
    public Optional<ListItem> setDinner(String homeCode, LocalDate day, Long memberId, String text) {
        Objects.requireNonNull(day, "day");
        memberOf(homeCode, memberId);
        String clean = InputLimits.clip(text == null ? null : text.trim(), InputLimits.LIST_ITEM);
        List<ListItem> existing = items.findByHomeCodeAndKindAndDayBetweenOrderByDayAscIdAsc(
                homeCode, ListKind.DINNER, day, day);

        if (clean == null || clean.isBlank()) {
            if (existing.isEmpty()) {
                return Optional.empty();
            }
            items.deleteAll(existing);
            touchAndBump(homeCode);
            return Optional.empty();
        }

        // A racing insert can leave two rows for one day; the first (oldest) one is the slot and
        // the rest are folded away here.
        List<ListItem> extras = existing.size() > 1 ? existing.subList(1, existing.size()) : List.of();
        if (!existing.isEmpty() && clean.equals(existing.get(0).getText())) {
            if (!extras.isEmpty()) {
                items.deleteAll(extras);
                touchAndBump(homeCode);
            }
            return Optional.of(existing.get(0));
        }
        ListItem slot;
        if (existing.isEmpty()) {
            slot = new ListItem(homeCode, ListKind.DINNER, clean, memberId);
            slot.setDay(day);
        } else {
            slot = existing.get(0);
            slot.setText(clean);
            slot.setCreatedByMemberId(memberId);
            slot.setCreatedAt(Instant.now());
        }
        if (!extras.isEmpty()) {
            items.deleteAll(extras);
        }
        slot = items.save(slot);
        touchAndBump(homeCode);
        return Optional.of(slot);
    }

    /**
     * Adds a line. Blank text adds nothing and returns empty; oversized text is clipped, following
     * the app-wide truncate-don't-reject rule (see {@link InputLimits}).
     *
     * @throws IllegalArgumentException if the member is not in that home
     */
    @Transactional
    public Optional<ListItem> add(String homeCode, ListKind kind, Long memberId, String text) {
        Member member = memberOf(homeCode, memberId);
        String clean = InputLimits.clip(text == null ? null : text.trim(), InputLimits.LIST_ITEM);
        if (clean == null || clean.isBlank()) {
            return Optional.empty();
        }
        ListItem saved = items.save(new ListItem(homeCode, kind, clean, member.getId()));
        touchAndBump(homeCode);
        return Optional.of(saved);
    }

    /**
     * Ticks a line off (or back on). Idempotent: ticking an already ticked line keeps the original
     * ticker and time, so two phones racing to the same line do not flip it back and forth.
     *
     * @return false if there is no such line
     * @throws IllegalArgumentException if the member is not in the line's home
     */
    @Transactional
    public boolean setDone(Long itemId, Long memberId, boolean done) {
        Optional<ListItem> found = items.findById(itemId);
        if (found.isEmpty()) {
            return false;
        }
        ListItem item = found.get();
        memberOf(item.getHomeCode(), memberId);
        if (done == item.isDone()) {
            return true;
        }
        item.setDoneAt(done ? Instant.now() : null);
        item.setDoneByMemberId(done ? memberId : null);
        items.save(item);
        if (done) {
            // Doing it settles it: a reminder about a ticked line — pending or already fired and
            // waiting for an answer — has nothing left to say. Unticking does not bring it back.
            reminders.deleteByItemId(item.getId());
        }
        touchAndBump(item.getHomeCode());
        return true;
    }

    /**
     * Removes a line outright. Any member of the home may do this — a shared list is shared.
     *
     * @return false if there is no such line
     * @throws IllegalArgumentException if the member is not in the line's home
     */
    @Transactional
    public boolean delete(Long itemId, Long memberId) {
        Optional<ListItem> found = items.findById(itemId);
        if (found.isEmpty()) {
            return false;
        }
        ListItem item = found.get();
        memberOf(item.getHomeCode(), memberId);
        reminders.deleteByItemId(item.getId());
        items.delete(item);
        touchAndBump(item.getHomeCode());
        return true;
    }

    /** Removes every ticked line from one list. Returns how many went. */
    @Transactional
    public long clearDone(String homeCode, ListKind kind) {
        long removed = items.deleteByHomeCodeAndKindAndDoneAtIsNotNull(homeCode, kind);
        if (removed > 0) {
            touchAndBump(homeCode);
        }
        return removed;
    }

    /**
     * Deletes lines ticked longer ago than {@link #DONE_RETENTION} and dinner slots whose day is
     * more than {@link #DINNER_RETENTION_DAYS} gone, across every home. Does not bump any home's
     * revision: the read path stopped showing those rows long before, so nothing on any screen
     * changes. The dinner cutoff uses the server date; the panel's window uses the member's zone,
     * and a week of margin covers any pair of zones on Earth.
     */
    @Transactional
    public long purgeStaleDone(Instant now) {
        long removed = items.deleteByDoneAtBefore(now.minus(DONE_RETENTION));
        LocalDate cutoff = LocalDate.ofInstant(now, ZoneId.systemDefault()).minusDays(DINNER_RETENTION_DAYS);
        removed += items.deleteByKindAndDayBefore(ListKind.DINNER, cutoff);
        if (removed > 0) {
            log.debug("Purged {} stale list row(s)", removed);
        }
        return removed;
    }

    @Scheduled(fixedDelayString = "${homechores.list.purge-ms:3600000}",
            initialDelayString = "${homechores.list.purge-ms:3600000}")
    public void scheduledPurge() {
        purgeStaleDone(Instant.now());
    }

    /** The member, if they belong to the home — the same line ChoreReminderService holds. */
    private Member memberOf(String homeCode, Long memberId) {
        Member member = memberId == null ? null : members.findById(memberId).orElse(null);
        if (member == null || !member.getHomeCode().equals(homeCode)) {
            throw new IllegalArgumentException("Member does not belong to this home");
        }
        return member;
    }

    /** A list edit is a person using the home (retention cares) and a change every device shows. */
    private void touchAndBump(String homeCode) {
        chores.touchHome(homeCode);
        homeState.bump(homeCode);
    }
}
