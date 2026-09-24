package com.homechores.service;

import com.homechores.domain.CustomList;
import com.homechores.domain.CustomListRepository;
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
 *
 * <p>A home can also name lists of its own ({@link CustomList}). Their lines are to-dos that
 * carry a {@code listId}; every read and write here takes that id, null meaning the built-in list
 * of the given kind, so the built-in To-do never shows a custom list's lines or the other way round.
 */
@Service
public class ListItemService {

    private static final Logger log = LoggerFactory.getLogger(ListItemService.class);

    /** How long a ticked line stays on the list before it is purged. */
    public static final Duration DONE_RETENTION = Duration.ofHours(24);

    /** How long a dinner slot outlives its day before the sweep reclaims it. */
    public static final int DINNER_RETENTION_DAYS = 7;

    private final ListItemRepository items;
    private final CustomListRepository customLists;
    private final ListReminderRepository reminders;
    private final MemberRepository members;
    private final ChoreService chores;
    private final HomeState homeState;

    public ListItemService(ListItemRepository items, CustomListRepository customLists,
                           ListReminderRepository reminders, MemberRepository members,
                           ChoreService chores, HomeState homeState) {
        this.items = items;
        this.customLists = customLists;
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
        return openItems(homeCode, kind, null);
    }

    /** Open lines of one list: a custom list when {@code listId} is set, else the built-in {@code kind}. */
    public List<ListItem> openItems(String homeCode, ListKind kind, Long listId) {
        return listId == null
                ? items.findByHomeCodeAndKindAndListIdIsNullAndDoneAtIsNullOrderByCreatedAtAscIdAsc(homeCode, kind)
                : inHome(homeCode, items.findByListIdAndDoneAtIsNullOrderByCreatedAtAscIdAsc(listId));
    }

    /** Lines ticked within the retention window, most recently ticked first. */
    public List<ListItem> doneItems(String homeCode, ListKind kind) {
        return doneItems(homeCode, kind, null);
    }

    /** {@link #doneItems(String, ListKind)} for a custom list when {@code listId} is set. */
    public List<ListItem> doneItems(String homeCode, ListKind kind, Long listId) {
        Instant cutoff = Instant.now().minus(DONE_RETENTION);
        return listId == null
                ? items.findByHomeCodeAndKindAndListIdIsNullAndDoneAtAfterOrderByDoneAtDescIdDesc(
                        homeCode, kind, cutoff)
                : inHome(homeCode, items.findByListIdAndDoneAtAfterOrderByDoneAtDescIdDesc(listId, cutoff));
    }

    /** Belt and braces: a list id from another home reads as an empty list, never as its lines. */
    private static List<ListItem> inHome(String homeCode, List<ListItem> lines) {
        return lines.stream().filter(i -> homeCode.equals(i.getHomeCode())).toList();
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
        return add(homeCode, kind, null, memberId, text);
    }

    /**
     * {@link #add(String, ListKind, Long, String)} onto a custom list when {@code listId} is set;
     * the line is then a to-do whatever {@code kind} says. Returns empty if that list is gone —
     * another phone may have deleted it a moment ago.
     */
    @Transactional
    public Optional<ListItem> add(String homeCode, ListKind kind, Long listId, Long memberId, String text) {
        Member member = memberOf(homeCode, memberId);
        String clean = InputLimits.clip(text == null ? null : text.trim(), InputLimits.LIST_ITEM);
        if (clean == null || clean.isBlank()) {
            return Optional.empty();
        }
        if (listId != null && findList(homeCode, listId).isEmpty()) {
            return Optional.empty();
        }
        ListItem line = new ListItem(homeCode, listId == null ? kind : ListKind.TODO, clean, member.getId());
        line.setListId(listId);
        ListItem saved = items.save(line);
        touchAndBump(homeCode);
        return Optional.of(saved);
    }

    /**
     * Rewrites a line's text. Blank text changes nothing (delete is the way to empty a line);
     * oversized text is clipped. Dinner slots are edited through {@link #setDinner} instead.
     * Ticked state, author and reminder are kept: fixing a typo is not a new line.
     *
     * @return false if there is no such line, it is a dinner slot, or the text is blank
     * @throws IllegalArgumentException if the member is not in the line's home
     */
    @Transactional
    public boolean rename(Long itemId, Long memberId, String text) {
        Optional<ListItem> found = items.findById(itemId);
        if (found.isEmpty()) {
            return false;
        }
        ListItem item = found.get();
        memberOf(item.getHomeCode(), memberId);
        String clean = InputLimits.clip(text == null ? null : text.trim(), InputLimits.LIST_ITEM);
        if (item.getKind() == ListKind.DINNER || clean == null || clean.isBlank()) {
            return false;
        }
        if (!clean.equals(item.getText())) {
            item.setText(clean);
            items.save(item);
            touchAndBump(item.getHomeCode());
        }
        return true;
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
        return clearDone(homeCode, kind, null);
    }

    /** {@link #clearDone(String, ListKind)} on a custom list when {@code listId} is set. */
    @Transactional
    public long clearDone(String homeCode, ListKind kind, Long listId) {
        if (listId != null && findList(homeCode, listId).isEmpty()) {
            return 0;
        }
        long removed = listId == null
                ? items.deleteByHomeCodeAndKindAndListIdIsNullAndDoneAtIsNotNull(homeCode, kind)
                : items.deleteByListIdAndDoneAtIsNotNull(listId);
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

    // ---- The home's own lists ------------------------------------------------

    /** The home's own lists, in the order they were made. */
    public List<CustomList> customLists(String homeCode) {
        return customLists.findByHomeCodeOrderByCreatedAtAscIdAsc(homeCode);
    }

    /** One of the home's own lists; empty if it is gone or belongs to another home. */
    public Optional<CustomList> findList(String homeCode, Long listId) {
        return listId == null ? Optional.empty()
                : customLists.findById(listId).filter(l -> l.getHomeCode().equals(homeCode));
    }

    /**
     * Makes a new named list. Any member may: lists are shared like everything else in a home.
     *
     * @return the list, or empty for a blank name or once the home has {@link InputLimits#CUSTOM_LISTS}
     * @throws IllegalArgumentException if the member is not in that home
     */
    @Transactional
    public Optional<CustomList> createList(String homeCode, Long memberId, String name) {
        Member member = memberOf(homeCode, memberId);
        String clean = InputLimits.clip(name == null ? null : name.trim(), InputLimits.LIST_NAME);
        if (clean == null || clean.isBlank()
                || customLists.countByHomeCode(homeCode) >= InputLimits.CUSTOM_LISTS) {
            return Optional.empty();
        }
        CustomList saved = customLists.save(new CustomList(homeCode, clean, member.getId()));
        touchAndBump(homeCode);
        return Optional.of(saved);
    }

    /**
     * Renames one of the home's own lists. Blank names change nothing.
     *
     * @return false if the list is gone or the name is blank
     * @throws IllegalArgumentException if the member is not in that home
     */
    @Transactional
    public boolean renameList(String homeCode, Long listId, Long memberId, String name) {
        memberOf(homeCode, memberId);
        Optional<CustomList> found = findList(homeCode, listId);
        String clean = InputLimits.clip(name == null ? null : name.trim(), InputLimits.LIST_NAME);
        if (found.isEmpty() || clean == null || clean.isBlank()) {
            return false;
        }
        if (!clean.equals(found.get().getName())) {
            found.get().setName(clean);
            customLists.save(found.get());
            touchAndBump(homeCode);
        }
        return true;
    }

    /**
     * Deletes one of the home's own lists with every line on it and their reminders. There is
     * no undo; the UI asks first.
     *
     * @return false if the list is already gone
     * @throws IllegalArgumentException if the member is not in that home
     */
    @Transactional
    public boolean deleteList(String homeCode, Long listId, Long memberId) {
        memberOf(homeCode, memberId);
        Optional<CustomList> found = findList(homeCode, listId);
        if (found.isEmpty()) {
            return false;
        }
        List<ListItem> lines = items.findByListId(listId);
        for (ListItem line : lines) {
            reminders.deleteByItemId(line.getId());
        }
        items.deleteAll(lines);
        customLists.delete(found.get());
        touchAndBump(homeCode);
        return true;
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
