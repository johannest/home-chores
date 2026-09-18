package com.homechores.service;

import com.homechores.domain.ListItem;
import com.homechores.domain.ListItemRepository;
import com.homechores.domain.ListReminder;
import com.homechores.domain.ListReminderRepository;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import com.homechores.domain.PushSubscription;
import com.homechores.domain.PushSubscriptionRepository;
import com.homechores.i18n.Translations;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reminders about shared-list lines: "remind us about the plumber tomorrow morning". Anyone in
 * the home arms one from a to-do or grocery line, a Web Push naming that line goes to every
 * subscribed device in the home, and the board then offers to snooze it until somebody answers.
 *
 * <p>The third push sweep, and a sibling of {@link ChoreReminderService} rather than an extension
 * of it, for the same reason that one is a sibling of {@link PushReminderService}: the rows and
 * the rules differ, and what they share is the transport and the transaction discipline —
 * <strong>no transaction may span a blocking push send</strong> (see {@code PushReminderService}
 * for why: two connections in the pool). The three-phase shape here (short read tx → sends
 * holding nothing → short write tx) is a deliberate copy.
 *
 * <p>Two differences from the chore snooze are the whole feature:
 * <ul>
 *   <li><strong>Home-wide.</strong> A shared list is shared, so one row per line, sent to every
 *       device in the home, badged for everyone, movable by anyone. Arming and answering bump
 *       {@link HomeState} — they are the family's business.
 *   <li><strong>Fired is not finished.</strong> The sweep stamps {@code firedAt} instead of
 *       deleting the row, so the app can show "⏰ Call the plumber — snooze?" to whoever opens the
 *       board next. Vaadin's generated service worker only ever opens the board on a notification
 *       tap (SPEC §4.13b), so the snooze has to happen there. The row is retired when somebody
 *       snoozes it (back to pending), ticks the line off, dismisses it — or, unanswered, after
 *       {@link #FIRED_RETENTION}.
 * </ul>
 */
@Service
public class ListReminderService {

    private static final Logger log = LoggerFactory.getLogger(ListReminderService.class);

    /** Blocking sends per sweep — the same cap as the chore snooze, for the same shared thread. */
    static final int MAX_SENDS_PER_SWEEP = 10;

    /** A reminder sooner than this is a mistrusted button; "next month" from the 31st is 31 days,
     *  and the picker may reach further, so the ceiling is generous rather than tight. */
    static final Duration MIN_OFFSET = Duration.ofMinutes(1);
    static final Duration MAX_OFFSET = Duration.ofDays(400);

    /** How long an unanswered notification keeps coming back as a dialog. */
    static final Duration FIRED_RETENTION = Duration.ofDays(7);

    /** Where "tomorrow", "next week" and "next month" land on the clock. */
    public static final LocalTime ANCHOR = LocalTime.of(9, 0);

    /**
     * The choices worth one tap, resolved in the member's own zone at the moment they tap. The
     * wall-clock ones land on {@link #ANCHOR}: a reminder set at 23:40 that fired at 23:40 the
     * next day would be a reminder nobody wanted. {@code plusMonths} clamps the 31st to a shorter
     * month's last day, which is what "next month" means to a person.
     */
    public enum QuickOption {
        IN_1H {
            @Override
            public Instant at(ZonedDateTime now) {
                return now.plusHours(1).toInstant();
            }
        },
        TOMORROW {
            @Override
            public Instant at(ZonedDateTime now) {
                return now.toLocalDate().plusDays(1).atTime(ANCHOR).atZone(now.getZone()).toInstant();
            }
        },
        NEXT_WEEK {
            @Override
            public Instant at(ZonedDateTime now) {
                return now.toLocalDate().plusWeeks(1).atTime(ANCHOR).atZone(now.getZone()).toInstant();
            }
        },
        NEXT_MONTH {
            @Override
            public Instant at(ZonedDateTime now) {
                return now.toLocalDate().plusMonths(1).atTime(ANCHOR).atZone(now.getZone()).toInstant();
            }
        };

        public abstract Instant at(ZonedDateTime now);
    }

    private final ListReminderRepository reminders;
    private final ListItemRepository items;
    private final MemberRepository members;
    private final PushSubscriptionRepository subscriptions;
    private final WebPushSender sender;
    private final Translations translations;
    private final HomeState homeState;
    private final TransactionTemplate transactions;

    public ListReminderService(ListReminderRepository reminders, ListItemRepository items,
                               MemberRepository members, PushSubscriptionRepository subscriptions,
                               WebPushSender sender, Translations translations, HomeState homeState,
                               org.springframework.transaction.PlatformTransactionManager txManager) {
        this.reminders = reminders;
        this.items = items;
        this.members = members;
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.translations = translations;
        this.homeState = homeState;
        this.transactions = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${homechores.list-reminder.sweep-ms:60000}",
            initialDelayString = "${homechores.list-reminder.sweep-ms:60000}")
    public void scheduledSweep() {
        if (sender.isEnabled()) {
            sendDue(Instant.now());
        }
    }

    /** Reclaims notifications the family never answered; shares the list purge's cadence. */
    @Scheduled(fixedDelayString = "${homechores.list.purge-ms:3600000}",
            initialDelayString = "${homechores.list.purge-ms:3600000}")
    public void scheduledPurge() {
        purgeStaleFired(Instant.now());
    }

    /**
     * One sweep at the given moment; callable directly from tests. Returns sends made.
     *
     * <p>Every reminder it looks at leaves the pending state, whatever happened: sent, nobody
     * subscribed, or line already gone. Sent ones become unanswered (the dialog); the rest are
     * deleted. Either way the row is never reconsidered by the next sweep, which bounds the crash
     * window to one duplicate notification rather than an endless retry.
     */
    public int sendDue(Instant now) {
        int sent = 0;
        for (Long id : dueReminderIds(now)) {
            Plan plan = planFor(id);
            List<Long> dead = new ArrayList<>();
            if (plan != null) {
                for (Map.Entry<PushSubscription, String[]> e : plan.messages().entrySet()) {
                    if (sender.send(e.getKey(), e.getValue()[0], e.getValue()[1])) {
                        sent++;
                    } else {
                        dead.add(e.getKey().getId()); // gone at the push service — pruned below
                    }
                }
            }
            // Attempted to at least one device = worth a dialog, even if that device turned out
            // to be gone. Nobody to send to = nothing anyone will ever see; drop it quietly.
            settle(id, plan != null && !plan.messages().isEmpty(), now, dead);
            if (sent >= MAX_SENDS_PER_SWEEP) {
                break;
            }
        }
        return sent;
    }

    /** What one sweep sends about one reminder: per device, that device's member's words. */
    private record Plan(String homeCode, Map<PushSubscription, String[]> messages) {
    }

    private List<Long> dueReminderIds(Instant now) {
        return transactions.execute(status ->
                reminders.findTop50ByDueAtLessThanEqualAndFiredAtIsNullOrderByDueAtAsc(now).stream()
                        .map(ListReminder::getId).toList());
    }

    /**
     * Decides about one reminder inside its own short transaction. Null = delete it without
     * sending: the line is gone or already ticked off, so there is nothing useful to say.
     *
     * <p>The audience is every device in the home, each addressed in its own member's language —
     * a Finnish phone and a Swedish tablet in one home are ordinary, not an edge case.
     */
    private Plan planFor(Long reminderId) {
        return transactions.execute(status -> {
            ListReminder r = reminders.findById(reminderId).orElse(null);
            if (r == null) {
                return null; // cancelled since the working set was read
            }
            ListItem item = items.findById(r.getItemId()).orElse(null);
            if (item == null || item.isDone()) {
                return null;
            }
            Map<Long, Locale> locales = new HashMap<>();
            for (Member m : members.findByHomeCodeOrderByJoinedAtAsc(r.getHomeCode())) {
                locales.put(m.getId(), localeOf(m));
            }
            Map<PushSubscription, String[]> messages = new LinkedHashMap<>();
            for (PushSubscription sub : subscriptions.findByHomeCode(r.getHomeCode())) {
                Locale locale = locales.getOrDefault(sub.getMemberId(), Locale.ENGLISH);
                messages.put(sub, new String[] {
                        translations.getTranslation("listReminder.notification.title", locale),
                        translations.getTranslation("listReminder.notification.body", locale,
                                item.getText())});
            }
            return new Plan(r.getHomeCode(), messages);
        });
    }

    /** Marks a sent reminder unanswered (or deletes an unsendable one), prunes dead devices, and
     *  tells open boards so the dialog appears without a reload. */
    private void settle(Long reminderId, boolean sent, Instant now, List<Long> dead) {
        String homeCode = transactions.execute(status -> {
            dead.forEach(subscriptions::deleteById);
            ListReminder r = reminders.findById(reminderId).orElse(null);
            if (r == null) {
                return null;
            }
            if (sent) {
                r.setFiredAt(now);
                reminders.save(r);
            } else {
                reminders.delete(r);
            }
            return r.getHomeCode();
        });
        if (homeCode != null) {
            homeState.bump(homeCode);
        }
    }

    /** Fired more than {@link #FIRED_RETENTION} ago and never answered. Returns how many went. */
    @Transactional
    public long purgeStaleFired(Instant now) {
        long removed = reminders.deleteByFiredAtBefore(now.minus(FIRED_RETENTION));
        if (removed > 0) {
            log.debug("Purged {} unanswered list reminder(s)", removed);
        }
        return removed;
    }

    // ---- Arming, snoozing and answering, called from the list -----------------

    /**
     * Arms (or moves) the home's reminder about one line, returning when it will fire. Snoozing a
     * fired one is the same call: it goes back to pending with a new time.
     *
     * <p>The language is stamped on the member, as the chore snooze does: the sweep has no
     * session to ask, and this may be the only push feature this member ever touches.
     *
     * @throws IllegalArgumentException if the member is not in the line's home
     */
    @Transactional
    public Instant schedule(Long itemId, Long memberId, Instant dueAt, Locale locale) {
        ListItem item = items.findById(itemId).orElseThrow();
        Member member = memberOf(item.getHomeCode(), memberId);
        Instant now = Instant.now();
        Instant floor = now.plus(MIN_OFFSET);
        Instant ceiling = now.plus(MAX_OFFSET);
        Instant clamped = dueAt == null || dueAt.isBefore(floor) ? floor
                : dueAt.isAfter(ceiling) ? ceiling : dueAt;

        ListReminder r = reminders.findByItemId(itemId)
                .orElseGet(() -> new ListReminder(item.getHomeCode(), itemId, clamped, member.getId()));
        r.setDueAt(clamped);
        r.setFiredAt(null);
        r.setSetByMemberId(member.getId());
        reminders.save(r);

        if (locale != null) {
            member.setReminderLocale(locale.getLanguage());
            members.save(member);
        }
        homeState.bump(item.getHomeCode());
        log.debug("List reminder on item {} at {} (by member {})", itemId, clamped, memberId);
        return clamped;
    }

    /**
     * Cancels a pending reminder, or dismisses a fired one — the row goes either way.
     *
     * @return false if there was none
     * @throws IllegalArgumentException if the member is not in the line's home
     */
    @Transactional
    public boolean cancel(Long itemId, Long memberId) {
        Optional<ListReminder> found = reminders.findByItemId(itemId);
        if (found.isEmpty()) {
            return false;
        }
        memberOf(found.get().getHomeCode(), memberId);
        reminders.delete(found.get());
        homeState.bump(found.get().getHomeCode());
        return true;
    }

    /** Everything armed or unanswered in one home, by line id. One query per render. */
    public Map<Long, ListReminder> forHome(String homeCode) {
        Map<Long, ListReminder> out = new HashMap<>();
        for (ListReminder r : reminders.findByHomeCode(homeCode)) {
            out.putIfAbsent(r.getItemId(), r);
        }
        return out;
    }

    public Optional<ListReminder> forItem(Long itemId) {
        return reminders.findByItemId(itemId);
    }

    /** The unanswered notifications in one home, oldest first. */
    public List<ListReminder> firedForHome(String homeCode) {
        return reminders.findByHomeCodeAndFiredAtIsNotNullOrderByFiredAtAsc(homeCode);
    }

    /** The member, if they belong to the home — the same line ListItemService holds. */
    private Member memberOf(String homeCode, Long memberId) {
        Member member = memberId == null ? null : members.findById(memberId).orElse(null);
        if (member == null || !member.getHomeCode().equals(homeCode)) {
            throw new IllegalArgumentException("Member does not belong to this home");
        }
        return member;
    }

    private static Locale localeOf(Member m) {
        return m.getReminderLocale() == null ? Locale.ENGLISH
                : Locale.forLanguageTag(m.getReminderLocale());
    }
}
